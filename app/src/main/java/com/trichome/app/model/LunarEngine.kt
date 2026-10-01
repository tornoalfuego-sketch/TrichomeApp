package com.trichome.app.model

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor

/**
 * The eight named points of the synodic (lunar) cycle, in the order they occur.
 *
 * [labelEs] and [iconKey] are the only UI-facing members. The engine itself never
 * touches a resource or a drawable — it hands the layer above a string and a key,
 * so the phase table stays testable on the JVM and the Spanish wording stays in
 * one place.
 *
 * @param labelEs Spanish display label, matching `strings.xml` wording.
 * @param iconKey stable snake_case key the UI resolves to an icon or a resource;
 *   it never changes, so a rename of [labelEs] cannot orphan an icon.
 */
enum class LunarPhase(val labelEs: String, val iconKey: String) {
    NEW_MOON("Luna nueva", "lunar_new_moon"),
    WAXING_CRESCENT("Luna creciente", "lunar_waxing_crescent"),
    FIRST_QUARTER("Cuarto creciente", "lunar_first_quarter"),
    WAXING_GIBBOUS("Gibosa creciente", "lunar_waxing_gibbous"),
    FULL_MOON("Luna llena", "lunar_full_moon"),
    WANING_GIBBOUS("Gibosa menguante", "lunar_waning_gibbous"),
    LAST_QUARTER("Cuarto menguante", "lunar_last_quarter"),
    WANING_CRESCENT("Luna menguante", "lunar_waning_crescent")
}

/** Which half of the cycle an instant sits in. The grow tips depend on it. */
enum class LunarTrend { WAXING, WANING }

/**
 * Grow-care advice for one phase, as data.
 *
 * The text is Spanish because it is UI copy; the type is English because it is
 * domain state. It lives here, not in a composable, for two reasons: the phase
 * table is then unit-testable without a device, and a rewrite of a sentence can
 * never accidentally change which phase it belongs to.
 *
 * @param headlineEs the phase shown next to the advice.
 * @param adviceEs what a grower is traditionally told to do at this point.
 */
data class LunarTip(
    val phase: LunarPhase,
    val headlineEs: String,
    val adviceEs: String
)

/** Everything the calendar badge needs for one instant. */
data class LunarSnapshot(
    val epochMillis: Long,
    val phase: LunarPhase,
    /** 0 at new moon, 0.5 at full moon, approaching 1 just before the next new. */
    val cycleFraction: Float,
    /** Fraction of the disc lit, 0f..1f. */
    val illumination: Float,
    val trend: LunarTrend,
    val tip: LunarTip
) {
    val isWaxing: Boolean get() = trend == LunarTrend.WAXING
    val isWaning: Boolean get() = trend == LunarTrend.WANING
}

/**
 * Lunar phase engine — pure astronomy, no Android, no clock, no network.
 *
 * ## What this is and is not
 *
 * This is a *mean synodic month* model: the cycle is treated as a perfectly
 * uniform 29.530588853 days. Real lunation length varies by roughly ±0.35 days
 * (the new-moon-to-new-moon interval swings between about 29.27 and 29.83 days)
 * because the Moon's orbit is elliptical and perturbed by the Sun, and the phase
 * angle is not a linear function of time. The model therefore drifts from the
 * true sky by a few hours over a few years.
 *
 * That is an accepted trade for an offline app: the alternative is a table of
 * precomputed ephemeris instants, which would be a maintenance liability, would
 * need a data source this app deliberately does not have, and would put an
 * unlabelled lookup table in the middle of a domain layer whose other engines are
 * all formulas. What the model *does* give is exactness where it matters here —
 * the phase a grower sees is always a consistent eighth of the cycle, and the
 * same instant always produces the same answer.
 *
 * ## The formula
 *
 * ```
 *   P(t)   = floorMod(t - NEW_MOON_REFERENCE, SYNODIC_MONTH_MILLIS)
 *   f(t)   = P(t) / SYNODIC_MONTH_MILLIS                      0..1 through the cycle
 *   phase  = the eighth of the cycle that P(t) falls in         see [phaseAt]
 *   k(t)   = (1 - cos(2 * PI * f(t))) / 2                      illuminated fraction
 *   trend  = WAXING if f < 0.5 else WANING
 * ```
 *
 * `f = 0` is new moon, `f = 0.5` is full moon. Phase boundaries are therefore
 * the eighths `0, 1/8, 2/8, ... 7/8`, evaluated as **integer millis** so that a
 * caller testing the exact instant of a quarter gets the exact phase back with
 * no floating-point tie-breaking.
 *
 * ## Timezone independence
 *
 * The engine takes `epochMillis` — an absolute instant since the Unix epoch — and
 * performs integer arithmetic only. No `LocalDate`, no `ZoneId`, no
 * `TimeZone.getDefault()`, no `System.currentTimeMillis()` default argument. A
 * synodic cycle is a property of the Earth-Moon-Sun system, so the phase of an
 * instant cannot depend on where the observer happens to be standing: the same
 * 947182440000L is a new moon for a phone in Buenos Aires and for a phone in
 * Auckland. Callers that need a local calendar day must format it themselves;
 * the phase is already correct when they do.
 */
object LunarEngine {

    private const val MILLIS_PER_DAY = 86_400_000L

    /**
     * Mean synodic month in milliseconds: 29.530588853 days, the standard mean
     * lunar period (Meeus, *Astronomical Algorithms*, ch. 49).
     */
    const val SYNODIC_MONTH_MILLIS: Long = 2_551_442_877L

    /**
     * Instant of a known new moon: 2000-01-06T18:14:00Z.
     *
     * ## Why this reference
     *
     * It is the same instant as **JD 2451550.1**, the J2000.0 new moon used as the
     * epoch of the standard lunar phase series. Three properties make it the right
     * anchor rather than any other published new moon:
     *
     *  1. It is *defined* in the mean-drift model used by every published phase
     *     series, so anchoring on it makes this engine's `f = 0` agree with the
     *     reference ephemeris at the epoch instead of being one synodic month off.
     *  2. It is far enough in the past that every phone's clock is after it, so
     *     `floorMod` never has to reason about a negative cycle count for any
     *     plausible current date.
     *  3. It is a round J2000 epoch, so a reader can check it against any
     *     ephemeris without hunting for it.
     *
     * Anchoring *before* 1970 is deliberately avoided: `t - reference` would go
     * negative for pre-1970 inputs and the phase would silently depend on how a
     * language floors a division.
     */
    const val NEW_MOON_REFERENCE_MILLIS: Long = 947_182_440_000L

    /**
     * One eighth of the synodic month in millis (318930359 ms ≈ 3.691 days).
     *
     * Integer division leaves a 5 ms remainder per month, which is 4.6 seconds of
     * drift after a full synodic cycle — far below the ±0.35 day inherent
     * variation of the real lunation, and it buys exactness at the quarter
     * instants, which is what the tests pin.
     */
    const val QUARTER_MILLIS: Long = SYNODIC_MONTH_MILLIS / 8

    /**
     * The UI must label this as guidance, not as a measured input. Spanish
     * because it is shown to the grower; the type and this KDoc are English.
     */
    const val DISCLAIMER_ES: String =
        "Consejo orientativo basado en el ciclo lunar. No es una medición: " +
            "la app es 100 % local y no consulta ningún sensor."

    /**
     * Position inside the current cycle, in millis from the start of the cycle
     * (0 at a new moon). Always in `[0, SYNODIC_MONTH_MILLIS)`, including for
     * instants before the reference: `Math.floorMod` returns a non-negative
     * remainder, so a pre-2000 timestamp is one ordinary cycle back rather than
     * a negative fraction.
     */
    fun cyclePositionMillis(epochMillis: Long): Long =
        Math.floorMod(epochMillis - NEW_MOON_REFERENCE_MILLIS, SYNODIC_MONTH_MILLIS)

    /**
     * How far through the cycle [epochMillis] is, `0f..1f`.
     *
     * 0 is a new moon, 0.5 a full moon. Not a percentage of the *visible* cycle:
     * the value keeps rising through the waning half, which is what makes
     * [trend] a simple comparison against 0.5.
     */
    fun cycleFraction(epochMillis: Long): Float =
        cyclePositionMillis(epochMillis).toFloat() / SYNODIC_MONTH_MILLIS.toFloat()

    /**
     * The phase [epochMillis] falls in.
     *
     * Boundaries are the eighths of the cycle, lower-inclusive and
     * upper-exclusive, so an instant exactly on a boundary belongs to the phase it
     * opens.
     */
    fun phaseAt(epochMillis: Long): LunarPhase = when (cyclePositionMillis(epochMillis)) {
        in 0 until QUARTER_MILLIS -> LunarPhase.NEW_MOON
        in QUARTER_MILLIS until 2 * QUARTER_MILLIS -> LunarPhase.WAXING_CRESCENT
        in 2 * QUARTER_MILLIS until 3 * QUARTER_MILLIS -> LunarPhase.FIRST_QUARTER
        in 3 * QUARTER_MILLIS until 4 * QUARTER_MILLIS -> LunarPhase.WAXING_GIBBOUS
        in 4 * QUARTER_MILLIS until 5 * QUARTER_MILLIS -> LunarPhase.FULL_MOON
        in 5 * QUARTER_MILLIS until 6 * QUARTER_MILLIS -> LunarPhase.WANING_GIBBOUS
        in 6 * QUARTER_MILLIS until 7 * QUARTER_MILLIS -> LunarPhase.LAST_QUARTER
        else -> LunarPhase.WANING_CRESCENT
    }

    /** Waxing up to and including the approach to full; waning from full onwards. */
    fun trend(epochMillis: Long): LunarTrend =
        if (cyclePositionMillis(epochMillis) < 4 * QUARTER_MILLIS) LunarTrend.WAXING
        else LunarTrend.WANING

    /** Convenience over [trend]. `f = 0.5` itself is the moment the moon turns. */
    fun isWaxing(epochMillis: Long): Boolean = trend(epochMillis) == LunarTrend.WAXING

    /**
     * Fraction of the lunar disc that is lit, `0f..1f`.
     *
     * ```
     *   k = (1 - cos(2 * PI * f)) / 2
     * ```
     * The illumination of a sphere seen from a distance is exactly the projected
     * fraction of its lit hemisphere, so it is this — not a linear ramp — and it
     * is why a crescent reads near zero while a half moon reads exactly 0.5.
     *
     * Properties the UI relies on: `k(0) = 0` (new moon, unlit), `k(0.5) = 1`
     * (full moon), `k` is symmetric about `f = 0` and about `f = 0.5`, and `k` rises
     * monotonically over the waxing half.
     */
    fun illumination(epochMillis: Long): Float {
        val fraction = cycleFraction(epochMillis).toDouble()
        val value = (1.0 - cos(2.0 * PI * fraction)) / 2.0
        return value.toFloat().coerceIn(0f, 1f)
    }

    /**
     * Millis from the start of the current cycle to the instant [phase] begins.
     *
     * Exhaustive `when` on purpose: a ninth phase is a build break here rather
     * than a phase that silently resolves to `NEW_MOON`.
     */
    fun phaseStartOffsetMillis(phase: LunarPhase): Long = when (phase) {
        LunarPhase.NEW_MOON -> 0L
        LunarPhase.WAXING_CRESCENT -> QUARTER_MILLIS
        LunarPhase.FIRST_QUARTER -> 2 * QUARTER_MILLIS
        LunarPhase.WAXING_GIBBOUS -> 3 * QUARTER_MILLIS
        LunarPhase.FULL_MOON -> 4 * QUARTER_MILLIS
        LunarPhase.WANING_GIBBOUS -> 5 * QUARTER_MILLIS
        LunarPhase.LAST_QUARTER -> 6 * QUARTER_MILLIS
        LunarPhase.WANING_CRESCENT -> 7 * QUARTER_MILLIS
    }

    /**
     * Whole days rounded **up** until [phase] begins again, 0 when it begins
     * exactly now.
     *
     * ```
     *   delta  = floorMod(start(phase) - t, SYNODIC_MONTH_MILLIS)
     *   answer = ceil(delta / 86_400_000)
     * ```
     * `floorMod` is the forward distance to the next occurrence: for an instant
     * just *after* a boundary it returns nearly a full month (the phase next comes
     * round again), and for an instant well before one it returns the distance to
     * that upcoming boundary. Rounding up rather than down is deliberate — "0 days"
     * must mean "it is happening now", not "somewhere inside today".
     *
     * Because a lunation is 29.53 days, this returns 30 for the second day after a
     * new moon: the next new moon is 28.5 days away and 28 would understate it.
     */
    fun daysUntil(phase: LunarPhase, epochMillis: Long): Int {
        val deltaMillis = Math.floorMod(
            phaseStartOffsetMillis(phase) - cyclePositionMillis(epochMillis),
            SYNODIC_MONTH_MILLIS
        )
        return ceil(deltaMillis / MILLIS_PER_DAY.toDouble()).toInt()
    }

    /**
     * The grow-care advice for [phase].
     *
     * Exhaustive `when` again: the phase table and the copy cannot drift apart,
     * and a new phase forces its advice to be written here.
     */
    fun tipFor(phase: LunarPhase): LunarTip = when (phase) {
        LunarPhase.NEW_MOON -> LunarTip(
            phase = phase,
            headlineEs = "Luna nueva · germinación",
            adviceEs = "Ventana de siembra y germinación: la tierra húmeda y el " +
                "calor parejo favorecen la aparición de la plántula."
        )
        LunarPhase.WAXING_CRESCENT -> LunarTip(
            phase = phase,
            headlineEs = "Luna creciente · enraizado",
            adviceEs = "Raíces activándose: ventana de siembra. Trasplanta con " +
                "cuidado y no manipules las raíces."
        )
        LunarPhase.FIRST_QUARTER -> LunarTip(
            phase = phase,
            headlineEs = "Cuarto creciente · estimulación",
            adviceEs = "Buen momento para la poda de estimulación y los trainings: " +
                "la planta empuja hacia arriba y tolera bien la manipulación."
        )
        LunarPhase.WAXING_GIBBOUS -> LunarTip(
            phase = phase,
            headlineEs = "Gibosa creciente · vegetativo",
            adviceEs = "Desarrollo vegetativo: consolida el cepollón y fija la planta en " +
                "su lugar definitivo de la carpa antes de la floración."
        )
        LunarPhase.FULL_MOON -> LunarTip(
            phase = phase,
            headlineEs = "Luna llena · floración",
            adviceEs = "Pico de floración: revisa tricomas, ajusta la " +
                "nutrición al pico y, si toca, cosecha de resina."
        )
        LunarPhase.WANING_GIBBOUS -> LunarTip(
            phase = phase,
            headlineEs = "Gibosa menguante · lavado",
            adviceEs = "Lavado y curado: baja el calcio y el magnesio, revisa " +
                "el color de los tricomas y prepara la cosecha."
        )
        LunarPhase.LAST_QUARTER -> LunarTip(
            phase = phase,
            headlineEs = "Cuarto menguante · limpieza",
            adviceEs = "Poda de limpieza y despinjado: retira hojas secas y " +
                "la manicura que el hongo busca."
        )
        LunarPhase.WANING_CRESCENT -> LunarTip(
            phase = phase,
            headlineEs = "Luna menguante · cosecha",
            adviceEs = "Cosecha, curado y revisión de los pies: buen momento para dejar " +
                "descansar la planta tras el esfuerzo de la floración."
        )
    }

    /** Every phase with its advice, in cycle order, for the calendar strip. */
    val tips: List<LunarTip> = LunarPhase.entries.map { tipFor(it) }

    /** Everything the UI needs for [epochMillis], computed once. */
    fun snapshot(epochMillis: Long): LunarSnapshot {
        val phase = phaseAt(epochMillis)
        return LunarSnapshot(
            epochMillis = epochMillis,
            phase = phase,
            cycleFraction = cycleFraction(epochMillis),
            illumination = illumination(epochMillis),
            trend = trend(epochMillis),
            tip = tipFor(phase)
        )
    }
}