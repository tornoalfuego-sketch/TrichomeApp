package com.trichome.app.model

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The lunar engine, reachable for an arbitrary date instead of only for right now.
 *
 * ## What F2's engine already answered, and what was missing
 *
 * [LunarEngine] takes `epochMillis` and answers for it correctly — it has no
 * `now` default and no clock. What it did not ship was a way for a grower to
 * *choose* an instant, so every call site passed `System.currentTimeMillis()`
 * and the eight phases, however well computed, were only ever reachable as
 * "tonight". This file is the selector: a signed day offset, a bounded range, and
 * one [LunarReading] per instant.
 *
 * ## The drift is stated, not hidden
 *
 * The engine's own KDoc records that it is a **mean sidereal month, not an
 * ephemeris**, and that it therefore drifts hours over years. A date selector
 * makes that drift *reachable in a way the calendar never did*: a grower who
 * steps two years forward is exactly the user the model is worst for, and a
 * screen that answered "cuarto creciente, 14 mar 2028" without saying so would
 * be overstating it. So:
 *
 *  - the reading carries [LunarReading.driftEs], which names what is measured
 *    (the real lunation range) and what is calculated (the phase), and marks the
 *    error bound with `≈`;
 *  - the error bound itself is **derived** from the two lunation bounds rather
 *    than typed, so it cannot drift away from the numbers it came from;
 *  - the per-phase tip is rendered unconditionally on the reading, because the
 *    tip is the only part of this feature that is useful.
 *
 * Nothing about the arithmetic changed. This file does not touch
 * [LunarEngine.SYNODIC_MONTH_MILLIS], [LunarEngine.QUARTER_MILLIS] or
 * [LunarEngine.NEW_MOON_REFERENCE_MILLIS], and a selector that quietly
 * "improved" the constant would be a second source of truth for the phase.
 *
 * ## Timezone independence
 *
 * Same rule as the engine: the offset is counted in **UTC days** from the
 * reference instant, never in local calendar days. A phase is a property of the
 * Earth-Moon-Sun system, so "one day later" has to mean the same instant plus
 * 86 400 000 ms on a phone in Buenos Aires and on one in Auckland. Formatting
 * the result into a local date is the caller's job — [LunarDateFormatter] does it
 * and nothing else does.
 */
data class LunarReading(
    /** The instant this reading is about, verbatim from the caller's offset. */
    val epochMillis: Long,
    /** Negative in the past, zero at the reference, positive in the future. */
    val offsetDays: Int,
    /** Spanish label for the date: "Hoy", "Ayer", or "14 mar 2028". */
    val dateLabelEs: String,
    /** Everything [LunarEngine.snapshot] computed for [epochMillis]. */
    val snapshot: LunarSnapshot,
    /** The phase the cycle enters after [epochMillis]. */
    val nextPhase: LunarPhase,
    /** Exact instant [nextPhase] begins, on the engine's own arithmetic. */
    val nextPhaseMillis: Long,
    /** Whole days rounded up until [nextPhase], 0 when it begins right now. */
    val daysUntilNextPhase: Int,
    /** Whole percent of the disc lit, e.g. "62 %". */
    val illuminationEs: String,
    /** "creciente" or "menguante". */
    val trendLabelEs: String,
    /** The per-phase tip headline. Always shown; never behind a disclosure. */
    val tipHeadlineEs: String,
    /** The per-phase tip body. Always shown; never behind a disclosure. */
    val tipAdviceEs: String,
    /** What the model computes, named as a calculation. */
    val calculatedLabelEs: String,
    /** What the real lunation bounds are, named as measurements. */
    val measuredLabelEs: String,
    /** The drift statement, `≈`-marked. See [LunarTimeline.DRIFT_ES]. */
    val driftEs: String
)

/**
 * The date selector's copy and arithmetic.
 *
 * Everything the panel prints lives here for the reason `LunarCardCopy` exists:
 * Compose has no unit-test runtime on this project's `test` classpath, so a
 * sentence written inside a composable is a sentence no JVM test can hold to the
 * honesty rules this module runs on.
 */
object LunarTimeline {

    private const val MILLIS_PER_DAY = 86_400_000L

    /**
     * How far either side of the reference the selector goes.
     *
     * ±365 days, and the bound is part of the answer rather than a limitation to
     * apologise for: beyond roughly a year the mean-month error is no longer an
     * "off by an afternoon" footnote, it is the difference between the right
     * phase and the wrong one. A grower asking about next week's moon is the use
     * case; a grower asking about 2038 is not, and pretending otherwise with a
     * wider range would be exactly the overstatement [DRIFT_ES] exists to
     * prevent.
     */
    const val OFFSET_RANGE_MIN_DAYS: Int = -365

    /** The far end of the range: [OFFSET_RANGE_MIN_DAYS] forwards. */
    const val OFFSET_RANGE_MAX_DAYS: Int = 365

    /** How far either side of the reference the selector goes.
     *
     * A `val` rather than a `const val`: `IntRange` is not a primitive type, so
     * a `const` of it does not compile, and the two bounds below are the
     * primitives worth naming anyway.
     */
    val OFFSET_RANGE_DAYS: IntRange = OFFSET_RANGE_MIN_DAYS..OFFSET_RANGE_MAX_DAYS

    /**
     * Shortest interval between two successive new moons, in days.
     *
     * A **measurement**, not a parameter of the model: the real lunation length
     * varies because the Moon's orbit is elliptical and perturbed by the Sun. It
     * is here so the error bound below can be derived from published numbers
     * rather than asserted as a round figure that drifts away from them.
     */
    const val MIN_LUNATION_DAYS: Double = 29.27

    /**
     * Longest interval between two successive new moons, in days.
     *
     * The other end of the same measurement. [MIN_LUNATION_DAYS] and this are the
     * only two astronomy facts this file needs, and they are what
     * [DRIFT_BOUND_HOURS] is derived from.
     */
    const val MAX_LUNATION_DAYS: Double = 29.83

    /**
     * The error bound a phase boundary can carry against the real sky, in hours.
     *
     * **Derived, never typed.** It is half the span between
     * [MIN_LUNATION_DAYS] and [MAX_LUNATION_DAYS]: the model places every
     * boundary at a fixed mean month, so the worst it can be is out by half the
     * range the real interval actually occupies. `29.83 - 29.27 = 0.56` days,
     * half of which is `0.28` days, or `6.72` hours.
     *
     * A **bound, not an error**, and not a growing one: it is what a single
     * boundary can be wrong by, and it is what the `≈` in [DRIFT_ES] marks. A
     * per-instant error would require an ephemeris this app deliberately does not
     * ship — see [LunarEngine]'s KDoc for why the table of precomputed instants
     * was refused.
     */
    val DRIFT_BOUND_HOURS: Double =
        (MAX_LUNATION_DAYS - MIN_LUNATION_DAYS) / 2.0 * 24.0

    /** [DRIFT_BOUND_HOURS] rounded to one decimal, Spanish decimal comma. */
    val DRIFT_BOUND_ES: String = formatHoursEs(DRIFT_BOUND_HOURS)

    /** "Medido: ..." — the real lunation range, which is a measurement. */
    val MEASURED_LABEL_ES: String =
        "Medido: la lunación real varía entre $MIN_LUNATION_DAYS y $MAX_LUNATION_DAYS días."

    /** "Calculado: ..." — what the engine produces, named as a calculation. */
    val CALCULATED_LABEL_ES: String =
        "Calculado: la fase y la iluminación salen de un mes sinódico medio de " +
            "29,530589 días."

    /**
     * The drift statement, `≈`-marked.
     *
     * Every clause here is load-bearing. It says the phase is calculated, it
     * quotes the measured range the bound comes from, it marks the bound as an
     * approximation of the model's error rather than a measurement of it, and it
     * says the app fetches nothing. A number that reads like a measurement and
     * is not is the defect class this whole module refuses, so the `≈` is not
     * decoration — `EstimatedClimate`'s marker and this one are the same rule.
     *
     * Not a `const val`: it interpolates [DRIFT_BOUND_HOURS], which is derived
     * arithmetic rather than a constant, and a `const` would force that bound to
     * be restated as a literal — the exact drift this file exists to prevent.
     */
    val DRIFT_ES: String =
        "La fase es un cálculo con un mes sinódico medio, no una efeméride. El " +
            "cálculo puede diferir del cielo real en ≈ $DRIFT_BOUND_ES h " +
            "según el día del ciclo: el modelo coloca cada cambio de fase a " +
            "intervalos fijos y el intervalo real varía. La app es local y no " +
            "consulta ningún servicio de efemérides."

    /** "Hoy" — the zero offset. */
    const val TODAY_ES: String = "Hoy"

    /** "Ayer" — offset -1. */
    const val YESTERDAY_ES: String = "Ayer"

    /** "Mañana" — offset +1. */
    const val TOMORROW_ES: String = "Mañana"

    /** The panel's title. */
    const val TITLE_ES: String = "Fase lunar en otra fecha"

    /** The calendar button that opens the panel. */
    const val OPEN_ES: String = "Elegir otra fecha"

    /** The stepper's content description when it moves back a day. */
    const val PREVIOUS_DAY_ES: String = "Ver la fase del día anterior"

    /** The stepper's content description when it moves forward a day. */
    const val NEXT_DAY_ES: String = "Ver la fase del día siguiente"

    /** The stepper's content description when it returns to the present. */
    const val BACK_TO_TODAY_ES: String = "Volver a la fecha de hoy"

    /** Label for the offset the selector is sitting on, e.g. "-12 días". */
    const val OFFSET_PREFIX_ES: String = "días"

    /** "próxima fase", used as the head of the countdown line. */
    const val NEXT_PHASE_ES: String = "Próxima fase"

    /**
     * The countdown line, e.g. `"Próxima fase: Luna llena · en 3 días"`.
     *
     * `days` is [LunarEngine.daysUntil]'s own value, rounded **up**, so `0` means
     * the phase begins inside the next day rather than "right now" — which is the
     * honest reading of a ceiling-rounded countdown, and the reason the wording
     * says "en" rather than "dentro de exactamente".
     */
    fun nextPhaseLineEs(nextPhaseLabelEs: String, days: Int): String {
        val amount = when {
            days <= 0 -> TODAY_ES.lowercase()
            days == 1 -> "1 $OFFSET_PREFIX_ES"
            else -> "$days $OFFSET_PREFIX_ES"
        }
        return "$NEXT_PHASE_ES: $nextPhaseLabelEs · en $amount"
    }

    /** The engine's own disclaimer, re-exported so the panel reads one object. */
    val DISCLAIMER_ES: String = LunarEngine.DISCLAIMER_ES

    /**
     * The reading for [offsetDays] whole UTC days from [referenceMillis].
     *
     * @param referenceMillis the "present" instant. The caller passes
     *   `System.currentTimeMillis()`; nothing here reads a clock, for the same
     *   reason [LunarEngine] has no clock.
     * @param offsetDays signed day offset. **Out of [OFFSET_RANGE_DAYS] is
     *   coerced**, and [LunarReading.offsetDays] reports the clamped value, so
     *   the number on screen and the instant that was computed cannot disagree.
     */
    fun readingAt(referenceMillis: Long, offsetDays: Int): LunarReading {
        val clamped = offsetDays.coerceIn(OFFSET_RANGE_DAYS.first, OFFSET_RANGE_DAYS.last)
        val instant = referenceMillis + clamped * MILLIS_PER_DAY
        val snapshot = LunarEngine.snapshot(instant)
        val next = nextPhaseChange(instant)

        return LunarReading(
            epochMillis = instant,
            offsetDays = clamped,
            dateLabelEs = dateLabelEs(referenceMillis, instant, clamped),
            snapshot = snapshot,
            nextPhase = next.first,
            nextPhaseMillis = next.second,
            daysUntilNextPhase = LunarEngine.daysUntil(next.first, instant),
            illuminationEs = LunarCardCopy.illuminationEs(snapshot.illumination),
            trendLabelEs = LunarCardCopy.trendLabelEs(snapshot.trend),
            tipHeadlineEs = snapshot.tip.headlineEs,
            tipAdviceEs = snapshot.tip.adviceEs,
            calculatedLabelEs = CALCULATED_LABEL_ES,
            measuredLabelEs = MEASURED_LABEL_ES,
            driftEs = DRIFT_ES
        )
    }

    /**
     * The phase the cycle enters after [epochMillis], and the exact instant it
     * begins.
     *
     * Derived from the engine's own eighths rather than re-derived here: the
     * boundary is `phaseStartOffsetMillis`, so the instant this returns and the
     * phase [LunarEngine.phaseAt] reports one millisecond later are the same
     * fact read twice, and a selector that computed its own boundaries would be
     * a second source of truth for the phase.
     *
     * The offset is taken with `floorMod` over the synodic month, which is what
     * makes it correct at the wrap: at a new moon the next opening is a whole
     * month away, not negative. An earlier version subtracted the raw position
     * and produced a boundary *behind* the instant asked about, which is the
     * half of the bug a test pinned.
     */
    fun nextPhaseChange(epochMillis: Long): Pair<LunarPhase, Long> {
        val position = LunarEngine.cyclePositionMillis(epochMillis)
        val next = LunarPhase.entries.firstOrNull { phase ->
            LunarEngine.phaseStartOffsetMillis(phase) > position
        } ?: LunarPhase.NEW_MOON

        val offset = Math.floorMod(
            LunarEngine.phaseStartOffsetMillis(next) - position,
            LunarEngine.SYNODIC_MONTH_MILLIS
        )
        // A strict `>` match guarantees a non-zero offset, so this cannot be zero
        // in practice. It is here so a future phase added with an offset of zero
        // cannot make the countdown read "in 0 days" forever.
        val delta = if (offset == 0L) LunarEngine.SYNODIC_MONTH_MILLIS else offset
        return next to (epochMillis + delta)
    }

    /**
     * The reading for [epochMillis] itself, with no offset applied.
     *
     * Convenience over [readingAt] for the "present" case. It does **not** read
     * a clock: the caller passes the instant, so a test can pin a reading to a
     * known new moon.
     */
    fun readingNow(epochMillis: Long): LunarReading = readingAt(epochMillis, 0)

    /**
     * True when [offsetDays] is outside the range the selector offers.
     *
     * The stepper uses it to disable the button, so the control says what it
     * will do rather than silently doing nothing at the edge.
     */
    fun canStepFurther(offsetDays: Int, back: Boolean): Boolean =
        if (back) offsetDays > OFFSET_RANGE_DAYS.first else offsetDays < OFFSET_RANGE_DAYS.last

    /**
     * The label for the offset, e.g. `"0 días"`, `"−12 días"`, `"+3 días"`.
     *
     * Signed with an explicit sign for anything but zero: a bare `12` on a
     * control whose two ends both increment by one is a number the reader has to
     * work out, and a `−` that renders as a hyphen is the F1 mojibake defect in
     * a different font.
     */
    fun offsetLabelEs(offsetDays: Int): String = when {
        offsetDays == 0 -> "0 $OFFSET_PREFIX_ES"
        offsetDays > 0 -> "+$offsetDays $OFFSET_PREFIX_ES"
        else -> "−${abs(offsetDays)} $OFFSET_PREFIX_ES"
    }

    /**
     * The date label, resolved without a timezone.
     *
     * "Hoy" / "Ayer" / "Mañana" when the offset is within a day of the
     * reference, and otherwise the UTC civil date. A local calendar date is
     * deliberately *not* used: the phase is a property of the Earth-Moon-Sun
     * system, and the day it is drawn on is a property of where the phone is
     * standing. [LunarDateFormatter.localDateEs] is where the second of those
     * lives, and it is a separate call so the two cannot be confused.
     */
    private fun dateLabelEs(referenceMillis: Long, instant: Long, offsetDays: Int): String {
        val delta = instant - referenceMillis
        return when {
            delta == 0L -> TODAY_ES
            delta == -MILLIS_PER_DAY -> YESTERDAY_ES
            delta == MILLIS_PER_DAY -> TOMORROW_ES
            else -> "${LunarDateFormatter.utcDateEs(instant)} · ${offsetLabelEs(offsetDays)}"
        }
    }

    /** One decimal place, Spanish decimal comma. The bound is always `≈`. */
    private fun formatHoursEs(hours: Double): String {
        val tenths = (hours * 10.0).roundToInt()
        return "${tenths / 10},${abs(tenths % 10)}"
    }
}

/**
 * The one place a lunar instant becomes a calendar date.
 *
 * [LunarTimeline] renders UTC on purpose; this is where a phone's own zone is
 * applied, and keeping it in its own object is what stops a *phase* from
 * silently acquiring a timezone. A screen that wants "what day is that, here"
 * calls [localDateEs]; a screen that wants "which phase is that instant" calls
 * [LunarEngine.phaseAt] and neither of them touches the other's rule.
 */
object LunarDateFormatter {

    /**
     * The UTC civil date of [epochMillis], `dd mmm yyyy`.
     *
     * UTC and not the system default on purpose — see the object KDoc. The month
     * is the three-letter Spanish abbreviation, which is unambiguous in this
     * locale and needs no resource lookup, so this stays a pure JVM object.
     */
    fun utcDateEs(epochMillis: Long): String {
        val local = java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneOffset.UTC)
            .toLocalDate()
        return "${two(local.dayOfMonth)} ${MONTH_ABBREVIATIONS_ES[local.monthValue - 1]} ${local.year}"
    }

    /**
     * The local civil date of [epochMillis], `dd mmm yyyy`.
     *
     * The phone's own zone, for the label that answers "what day is that for
     * me". Never used to compute a phase.
     */
    fun localDateEs(epochMillis: Long, zone: java.time.ZoneId): String {
        val local = java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        return "${two(local.dayOfMonth)} ${MONTH_ABBREVIATIONS_ES[local.monthValue - 1]} ${local.year}"
    }

    /** Whole days between two instants, floored toward negative infinity. */
    fun daysBetween(fromMillis: Long, toMillis: Long): Int =
        floor((toMillis - fromMillis) / 86_400_000.0).toInt()

    private fun two(day: Int): String = if (day < 10) "0$day" else "$day"

    private val MONTH_ABBREVIATIONS_ES = listOf(
        "ene", "feb", "mar", "abr", "may", "jun",
        "jul", "ago", "sep", "oct", "nov", "dic"
    )
}