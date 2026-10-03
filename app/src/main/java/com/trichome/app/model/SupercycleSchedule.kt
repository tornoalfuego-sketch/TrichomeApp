package com.trichome.app.model

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * The photoperiod of one supercycle, resolved onto the **real** 24-hour day, plus
 * every string the anchor picker and the heatmap print.
 *
 * ## Why this file exists
 *
 * The supercycle is 26 h or 32 h long and the day is 24 h. Those two numbers do not
 * divide into each other, so a strip of 24 cells laid out against the wall clock
 * **cannot** be a picture of the cycle: at 18/6 the light window slides 2 h later on
 * the clock every superday, and at 16/8 it slides 8 h. A heatmap drawn as 24 equal
 * cells and read as "these are the plant's hours" is a claim the engine never made.
 *
 * So the model does two things the composable must not:
 *
 *  1. **Asks the engine, per hour.** [hourState] calls
 *     [SuperCycleEngine.calculateSuperCycle] at the start and at the end of every hour
 *     of the reference day. The strip therefore cannot disagree with the superday and
 *     the phase percentage on the card above it: there is one implementation of "which
 *     phase is this instant in", and the heatmap is not a second one.
 *  2. **States the drift out loud.** [driftValueEs], [driftLabelEs] and
 *     [driftExplanationEs] say how far the cycle runs past the day and that the picture
 *     is of *today*, not of a repeating pattern. A graphic that implies a 24 h period is
 *     a lie told with a picture, so the sentence that denies it is part of the content
 *     rather than a comment above it.
 *
 * ## Why the geometry is here and not in the composable
 *
 * Compose has no unit-test runtime in this project — neither Robolectric nor
 * `compose-ui-test` is on the `test` classpath — and four bug classes have already
 * shipped from decisions a JVM test could not reach; one of them was a `weight(0f)`
 * that threw at compose time and took every terpene page down with it. Which hour is
 * lit, where the phase boundaries fall, and where the grid's two rows break are all
 * decisions. They are decided here, and `SupercycleHeatmap.kt` is left with layout.
 *
 * ## Two rows of twelve, and why
 *
 * Twenty-four cells across a phone is 13 dp each at this screen's width: the lit/dark
 * distinction becomes a guess, and the hour ticks collide into noise. Twelve per row
 * gives roughly 26 dp — wide enough to read as filled or empty at a glance and to put a
 * printed tick under every third column — and the whole day stays on screen with no
 * gesture, which is the only way a strip can be compared against a clock.
 *
 * A horizontal scroll was the other candidate and was rejected: it hides half the day
 * behind a discoverability problem and adds a second scroll axis to a screen that
 * already owns the vertical one.
 *
 * ## The four states, and why four
 *
 * [HeatmapHourState.PENDING] exists because the anchor is now the grower's choice. An
 * hour that ends before the anchor has not happened yet, and colouring it "dark" would
 * claim something about a period the cycle does not cover. An hour the phase boundary
 * falls inside is [HeatmapHourState.TRANSITION]: part of it is light and part is not,
 * and no single fill says that. On a 26 h cycle a boundary lands inside an hour on most
 * superdays, so the third state is the common case and not a refinement.
 *
 * ## Spanish here, not in the composable
 *
 * Every label, legend entry and per-hour description is assembled here.
 * `TerpeneDetailProcessingTest` and `EntourageF5StructureTest` already forbid a
 * non-blank string literal inside a composable, and `SupercycleHeatmapStructureTest`
 * extends that to this pair of files. Weekday abbreviations come from [DIAS_SEMANA_ES]
 * rather than from a `DateTimeFormatter` pattern: the JVM and Android CLDR data disagree
 * about `oct.` versus `oct`, and copy that changes with the platform is copy no test can
 * assert.
 */
object SupercycleScheduleBuilder {

    private const val MILLIS_PER_HOUR = 3_600_000L
    private const val MILLIS_PER_DAY = 86_400_000L
    private const val HOURS_PER_DAY = 24

    /** Cells per row. Two rows of twelve rather than one row of twenty-four. */
    const val COLUMNS = 12

    /** Columns between printed hour ticks, so four labels land on each row. */
    const val TICK_EVERY = 3

    private val DIAS_SEMANA_ES = listOf("dom", "lun", "mar", "mié", "jue", "vie", "sáb")

    private const val TITLE_ES = "🕐 Mapa de 24 horas"
    private const val NOW_MARKER_ES = "▲ ahora"

    private val LEGEND_ES: List<HeatmapLegendEntry> = listOf(
        HeatmapLegendEntry(HeatmapHourState.LIT, "Luz"),
        HeatmapLegendEntry(HeatmapHourState.DARK, "Oscuridad"),
        HeatmapLegendEntry(HeatmapHourState.TRANSITION, "Cambia dentro de la hora"),
        HeatmapLegendEntry(HeatmapHourState.PENDING, "Fuera del ciclo")
    )

    /**
     * Resolves the grid, the drift figures and the copy for one supercycle.
     *
     * @param cycleStartAt the anchor. Zero or negative means the cycle has not started
     *   and every cell is [HeatmapHourState.PENDING].
     * @param nowMillis the instant the grid is resolved for, passed in rather than read
     *   so the whole thing is deterministic on the JVM.
     * @param zone the zone whose midnight defines "the reference day". A grower at
     *   UTC-3 whose cycle starts at 23:00 has a day that does not begin at 00:00 UTC,
     *   and hardcoding `ZoneOffset.UTC` here would put their light window in the wrong
     *   column.
     */
    fun scheduleFor(
        cycleStartAt: Long,
        lightHours: Int,
        darkHours: Int,
        nowMillis: Long,
        zone: ZoneId
    ): SupercycleSchedule {
        val totalCycleHours = lightHours + darkHours
        val isRenderable = cycleStartAt > 0L && totalCycleHours > 0

        val dayStart = startOfDay(nowMillis, zone)
        val cells = (0 until HOURS_PER_DAY).map { hour ->
            val cellStart = dayStart + hour * MILLIS_PER_HOUR
            val cellEnd = cellStart + MILLIS_PER_HOUR
            val state = hourState(cellStart, cellEnd, cycleStartAt, lightHours, darkHours)
            HeatmapHour(
                hourOfDay = hour,
                state = state,
                isNow = nowMillis in cellStart until cellEnd,
                descriptionEs = hourDescription(
                    hour = hour,
                    state = state,
                    isBeforeAnchor = cellEnd <= cycleStartAt,
                    isNow = nowMillis in cellStart until cellEnd
                )
            )
        }

        val result = SuperCycleEngine.calculateSuperCycle(
            nowTimestamp = nowMillis,
            cycleStartAt = cycleStartAt,
            lightHours = lightHours,
            darkHours = darkHours
        )

        // 26 h -> +2, 32 h -> +8, 24 h -> 0, 18 h -> -6. Arithmetic on the engine's own
        // total, and the number the whole picture has to admit to.
        val drift = totalCycleHours - HOURS_PER_DAY

        return SupercycleSchedule(
            rows = listOf(
                HeatmapRow(
                    cells = cells.take(COLUMNS),
                    ticks = ticksFor(0),
                    rangeEs = "${hhmm(0)}–${hhmm(COLUMNS * 60)}"
                ),
                HeatmapRow(
                    cells = cells.drop(COLUMNS),
                    ticks = ticksFor(COLUMNS),
                    rangeEs = "${hhmm(COLUMNS * 60)}–${hhmm(HOURS_PER_DAY * 60)}"
                )
            ),
            hours = cells,
            lightHours = lightHours,
            darkHours = darkHours,
            totalCycleHours = totalCycleHours,
            driftHours = drift,
            superday = result.superday,
            titleEs = TITLE_ES,
            photoperiodLabelEs = "$lightHours h de luz · $darkHours h de oscuridad",
            phaseLabelEs = phaseLabelEs(result.phase),
            phaseDetailEs = phaseDetailEs(result),
            nowMarkerLabelEs = NOW_MARKER_ES,
            driftValueEs = driftValueEs(drift),
            driftLabelEs = driftLabelEs(drift),
            driftExplanationEs = driftExplanationEs(totalCycleHours, drift),
            legend = LEGEND_ES,
            isRenderable = isRenderable
        )
    }

    /**
     * The consequence of choosing [cycleStartAt]: which superday it implies, and which
     * human day that superday began on.
     *
     * A wrong anchor does not throw. It silently renumbers every superday from that
     * instant on, which is why the picker shows this while the grower is still choosing
     * instead of reporting the outcome afterwards.
     */
    fun anchorPreviewFor(
        cycleStartAt: Long,
        lightHours: Int,
        darkHours: Int,
        nowMillis: Long,
        zone: ZoneId
    ): SupercycleAnchorPreview {
        val result = SuperCycleEngine.calculateSuperCycle(
            nowTimestamp = nowMillis,
            cycleStartAt = cycleStartAt,
            lightHours = lightHours,
            darkHours = darkHours
        )
        // Where the reported superday began. Derived from the engine's own superday and
        // total rather than re-run, so the two cannot drift apart.
        val superdayStartedAt = cycleStartAt +
            (result.superday - 1).coerceAtLeast(0) * result.totalCycleHours * MILLIS_PER_HOUR

        return SupercycleAnchorPreview(
            anchorLabelEs = instantEs(cycleStartAt, zone),
            superdayLabelEs = "Ahora: superdía ${result.superday}",
            superdayStartLabelEs = "Ese superdía empezó el ${instantEs(superdayStartedAt, zone)}",
            offsetLabelEs = offsetLabelEs(superdayStartedAt, zone),
            phaseNowLabelEs = phaseNowLabelEs(result),
            warningEs = warningEs(cycleStartAt, nowMillis)
        )
    }

    /* ── The hour grid ───────────────────────────────────────────────────── */

    /**
     * The phase of the hour starting at [start].
     *
     * Sampled at both ends and compared, so an hour the boundary falls inside is
     * [HeatmapHourState.TRANSITION] rather than being rounded to whichever end happened
     * to be read first.
     */
    private fun hourState(
        start: Long,
        end: Long,
        cycleStartAt: Long,
        lightHours: Int,
        darkHours: Int
    ): HeatmapHourState {
        if (cycleStartAt <= 0L) return HeatmapHourState.PENDING
        // Wholly before the anchor: the cycle has not reached this hour yet.
        if (end <= cycleStartAt) return HeatmapHourState.PENDING
        // The anchor falls strictly inside this hour, so part of the hour is outside the
        // cycle and part of it is not. Painting it with the phase it holds for most of
        // its length would claim the cycle covers an hour it has not started yet. The
        // engine clamps negative elapsed time to zero, which is right for arithmetic and
        // wrong for a picture.
        if (start < cycleStartAt) return HeatmapHourState.TRANSITION

        val first = phaseStateAt(start, cycleStartAt, lightHours, darkHours)
        val last = phaseStateAt(end - 1, cycleStartAt, lightHours, darkHours)
        return if (first == last) first else HeatmapHourState.TRANSITION
    }

    /** One instant, answered by the engine. Never re-derived here. */
    private fun phaseStateAt(
        instant: Long,
        cycleStartAt: Long,
        lightHours: Int,
        darkHours: Int
    ): HeatmapHourState =
        when (
            SuperCycleEngine.calculateSuperCycle(
                nowTimestamp = instant,
                cycleStartAt = cycleStartAt,
                lightHours = lightHours,
                darkHours = darkHours
            ).phase
        ) {
            Phase.LIGHT -> HeatmapHourState.LIT
            Phase.DARK -> HeatmapHourState.DARK
            // The engine refuses a non-positive anchor or a zero-length cycle. There is
            // no phase to draw, so the hour is pending rather than guessed at.
            Phase.OFF -> HeatmapHourState.PENDING
        }

    private fun startOfDay(millis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * Four labels per row, the rest null.
     *
     * The ticks occupy the same twelve columns as the cells so both rows share one
     * geometry: a separate four-item row would put its labels wherever its own
     * arrangement put them, which is how a "00" ends up over 01.
     */
    private fun ticksFor(rowStartHour: Int): List<String?> =
        List(COLUMNS) { column ->
            // Minutes, not hours: `hhmm` takes a time of day, and passing the hour here
            // printed "00:03" for three o'clock.
            if (column % TICK_EVERY == 0) hhmm((rowStartHour + column) * 60) else null
        }

    /* ── Spanish copy ────────────────────────────────────────────────────── */

    private fun phaseLabelEs(phase: Phase): String = when (phase) {
        Phase.LIGHT -> "Fase de luz"
        Phase.DARK -> "Fase de oscuridad"
        Phase.OFF -> "Sin ciclo"
    }

    private fun phaseDetailEs(result: SuperCycleResult): String = when (result.phase) {
        Phase.LIGHT -> "Quedan ${result.hoursRemainingInPhase} h de luz"
        Phase.DARK -> "Quedan ${result.hoursRemainingInPhase} h de oscuridad"
        Phase.OFF -> "El ciclo todavía no ha empezado"
    }

    private fun phaseNowLabelEs(result: SuperCycleResult): String = when (result.phase) {
        Phase.LIGHT -> "Ahora: fase de luz · superdía ${result.superday} · quedan " +
            "${result.hoursRemainingInPhase} h de luz"
        Phase.DARK -> "Ahora: fase de oscuridad · superdía ${result.superday} · quedan " +
            "${result.hoursRemainingInPhase} h de oscuridad"
        Phase.OFF -> "Ese inicio aún no ha llegado"
    }

    /**
     * What a screen reader hears for one cell.
     *
     * The "now" marker is folded in here rather than only drawn: a cell marked by a
     * 3 dp rule is invisible to a screen-reader user, and the hour the card is about is
     * the one hour on it that matters.
     */
    private fun hourDescription(
        hour: Int,
        state: HeatmapHourState,
        isBeforeAnchor: Boolean,
        isNow: Boolean
    ): String {
        val span = "${hhmm(hour * 60)}–${hhmm((hour + 1) * 60)}"
        val stateText = when (state) {
            HeatmapHourState.LIT -> "luz"
            HeatmapHourState.DARK -> "oscuridad"
            HeatmapHourState.TRANSITION -> "el cambio de fase cae dentro de esta hora"
            HeatmapHourState.PENDING ->
                if (isBeforeAnchor) "el ciclo todavía no ha llegado aquí"
                else "sin ciclo definido"
        }
        return if (isNow) "$span, $stateText, ahora" else "$span, $stateText"
    }

    private fun driftValueEs(drift: Int): String = when {
        drift > 0 -> "+$drift h"
        drift < 0 -> "${-drift} h menos"
        else -> "0 h"
    }

    private fun driftLabelEs(drift: Int): String =
        if (drift == 0) "de desfase por día" else "de desfase por superdía"

    /**
     * The sentence that stops the picture from lying.
     *
     * Two cases, and the split is the reason this is not a fixed caveat: a 24 h cycle
     * really *is* a daily repeating pattern and the grid can honestly be read as one,
     * while a 26 h cycle cannot. Printing the same disclaimer for both would teach the
     * grower to skip it, which is the failure [ClimateCardContent] guards against on the
     * estimate card.
     */
    private fun driftExplanationEs(totalCycleHours: Int, drift: Int): String = when {
        drift > 0 ->
            "El superciclo dura $totalCycleHours h, no 24. Cada superdía la franja de " +
                "luz se desplaza $drift h respecto al reloj, así que este mapa es de hoy " +
                "y no un patrón que se repita mañana."
        drift < 0 ->
            "El superciclo dura $totalCycleHours h, menos que un día. Cada superdía la " +
                "franja de luz se adelanta ${-drift} h en el reloj, así que este mapa es " +
                "de hoy y no un patrón que se repita mañana."
        else ->
            "El superciclo dura 24 h justas: la franja de luz cae en las mismas horas " +
                "del reloj todos los días y este mapa sí se repite."
    }

    private fun warningEs(cycleStartAt: Long, nowMillis: Long): String? = when {
        cycleStartAt <= 0L -> "Elige una fecha y una hora de inicio."
        cycleStartAt > nowMillis ->
            "Ese inicio está en el futuro. Hasta que llegue, el superciclo no ha " +
                "empezado y todo el mapa aparece como fuera del ciclo."
        nowMillis - cycleStartAt > MILLIS_PER_DAY * 400 ->
            "Ese inicio fue hace más de un año: el número de superdía será enorme y " +
                "casi ningún registro caerá dentro del ciclo."
        else -> null
    }

    /**
     * The superday boundary expressed against midnight.
     *
     * The number a grower actually asks about — "does my dark period start at night?" —
     * against their own clock rather than as an offset from an abstract instant.
     */
    private fun offsetLabelEs(superdayStartedAt: Long, zone: ZoneId): String {
        val local = Instant.ofEpochMilli(superdayStartedAt).atZone(zone).toLocalTime()
        val minutesOfDay = local.hour * 60 + local.minute
        val hours = (minutesOfDay / 60) % HOURS_PER_DAY
        val minutes = minutesOfDay % 60
        val tail = if (minutes == 0) {
            "$hours h después de medianoche"
        } else {
            "$hours h y $minutes min después de medianoche"
        }
        return "Ese superdía empieza a las ${hhmm(minutesOfDay)} de tu reloj, $tail."
    }

    /** `sáb 04/10/2026 · 08:00`, in the grower's own zone. */
    private fun instantEs(millis: Long, zone: ZoneId): String {
        val local = Instant.ofEpochMilli(millis).atZone(zone)
        val day = local.toLocalDate()
        val weekday = DIAS_SEMANA_ES[(day.dayOfWeek.value - 1).coerceIn(0, 6)]
        return String.format(
            Locale.US,
            "%s %02d/%02d/%04d · %02d:%02d",
            weekday,
            day.dayOfMonth,
            day.monthValue,
            day.year,
            local.hour,
            local.minute
        )
    }

    private fun hhmm(minutesOfDay: Int): String =
        String.format(Locale.US, "%02d:%02d", (minutesOfDay / 60) % (HOURS_PER_DAY + 1), minutesOfDay % 60)
}

/**
 * What the picker prints for the instant the grower is looking at, before they commit
 * to it.
 *
 * The warning is a field rather than a dropped sentence so a bad anchor cannot be
 * confirmed quietly: [canConfirm] is false while one is set, and the dialog disables its
 * confirm button from it.
 */
data class SupercycleAnchorPreview(
    /** The chosen instant, formatted. */
    val anchorLabelEs: String,
    /** The superday that instant puts us in right now. */
    val superdayLabelEs: String,
    /** The human date that superday began on — the day the grower recognises. */
    val superdayStartLabelEs: String,
    /** The same boundary expressed against midnight. */
    val offsetLabelEs: String,
    /** The live phase read against this anchor. */
    val phaseNowLabelEs: String,
    /** Why this anchor is a problem, or null when it is fine. */
    val warningEs: String?
) {
    /** False for a missing or future anchor: the picker must not confirm it. */
    val canConfirm: Boolean get() = warningEs == null
}

/** One hour cell. The composable draws [state] and nothing else. */
data class HeatmapHour(
    val hourOfDay: Int,
    val state: HeatmapHourState,
    /** True for the hour containing the instant the grid was resolved for. */
    val isNow: Boolean,
    /** Spoken description, so the grid is not a picture with no reading. */
    val descriptionEs: String
)

/**
 * The four fills the grid can show.
 *
 * [PENDING] is not a fifth colour question, it is the reason the grid cannot be two
 * colours: once the anchor is the grower's choice, an hour before the anchor has not
 * happened, and painting it "dark" would assert something about a period the cycle does
 * not describe.
 */
enum class HeatmapHourState {
    LIT,
    DARK,
    TRANSITION,
    PENDING
}

/**
 * One row of cells plus the tick labels printed under it, sharing its geometry.
 *
 * [rangeEs] names the wall-clock span the row covers, so "two rows of twelve" cannot be
 * misread as two twelve-hour periods. It is what the composable prints above the row and
 * it is built here because the span is arithmetic on [COLUMNS].
 */
data class HeatmapRow(
    val cells: List<HeatmapHour>,
    /** Twelve entries, four of them labels and eight null. Never shorter. */
    val ticks: List<String?>,
    /** e.g. "12:00–24:00" on the second row. */
    val rangeEs: String
)

/** One legend swatch and its Spanish name. */
data class HeatmapLegendEntry(
    val state: HeatmapHourState,
    val labelEs: String
)

/**
 * Everything the heatmap prints.
 *
 * [isRenderable] travels with the data rather than being re-checked at the call site: a
 * zero-length cycle has no phases, so the card cannot be drawn from it, and the
 * composable returns instead of painting twenty-four identical cells that read as
 * "always dark".
 */
data class SupercycleSchedule(
    val rows: List<HeatmapRow>,
    /** All twenty-four cells in hour order, for tests and for the accessibility text. */
    val hours: List<HeatmapHour>,
    val lightHours: Int,
    val darkHours: Int,
    val totalCycleHours: Int,
    /** `totalCycleHours - 24`. Positive means the cycle runs past the day. */
    val driftHours: Int,
    val superday: Int,
    val titleEs: String,
    val photoperiodLabelEs: String,
    val phaseLabelEs: String,
    val phaseDetailEs: String,
    val nowMarkerLabelEs: String,
    val driftValueEs: String,
    val driftLabelEs: String,
    val driftExplanationEs: String,
    val legend: List<HeatmapLegendEntry>,
    val isRenderable: Boolean
) {
    /** True when the cycle is longer than the day it is drawn against. */
    val runsPastTheDay: Boolean get() = driftHours > 0

    /** The hour state at [hourOfDay], or null when the hour is outside 0..23. */
    fun stateAt(hourOfDay: Int): HeatmapHourState? =
        hours.firstOrNull { it.hourOfDay == hourOfDay }?.state

    /** Whole hours currently in the lit phase, counted off the grid itself. */
    val litHourCount: Int get() = hours.count { it.state == HeatmapHourState.LIT }
}

/** Which half of the anchor picker is on screen. */
enum class SupercycleAnchorStep { DATE, TIME }

/**
 * Every string the anchor picker prints, and the conversions it needs.
 *
 * ## The UTC trap
 *
 * `DatePicker` reports its selection as the **UTC** midnight of the chosen day, not as
 * the instant that day began locally. Passing that value straight to
 * `LocalDateTime` and converting with the local zone is the bug this object exists to
 * prevent: for a grower at UTC-3 it lands the anchor on the previous evening and
 * renumbers every superday from there.
 *
 * The conversion is asymmetric and therefore testable. [toPickerDateMillis] throws the
 * local time of day away on purpose; [fromPickerDateMillis] reattaches the wall-clock
 * time in [zone]. `SupercycleScheduleTest` asserts that
 * `fromPickerDateMillis(toPickerDateMillis(x), m, zone) == x` for an `x` whose
 * wall-clock time is `m`, at three offsets including one with a DST transition.
 */
object SupercycleAnchorPicker {

    private const val MILLIS_PER_DAY = 86_400_000L

    const val TITLE_ES = "🗓️ Inicio del superciclo"

    /** One sentence: what the field is, and why it is worth getting right. */
    const val INTRO_ES =
        "Es el instante desde el que se cuenta el superdía. Cambiarlo renumera " +
            "todos los superdías desde esa fecha."

    const val DATE_STEP_ES = "Fecha de inicio"

    const val TIME_STEP_ES = "Hora de inicio"

    const val PICK_TIME_ES = "Elegir la hora"

    const val PICK_DATE_ES = "Elegir la fecha"

    const val CONFIRM_ES = "Usar este inicio"

    /** Closes the dialog without writing anything. The stored anchor is untouched. */
    const val CANCEL_ES = "Cancelar"

    /** Content description for the control that opens the dialog. */
    const val OPEN_ES = "Cambiar el inicio del superciclo"

    const val CONSEQUENCE_ES = "Esto es lo que implicaría"

    /** Minutes on the time step. A supercycle is whole-hour, so seconds are not offered. */
    fun minutesOf(millis: Long, zone: ZoneId): Int {
        val local = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        return local.hour * 60 + local.minute
    }

    /** The `DatePicker` selection for a local instant, as UTC midnight. */
    fun toPickerDateMillis(millis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /**
     * The local instant a picker selection plus a wall-clock time stands for.
     *
     * @param pickerDateMillis a UTC midnight as returned by `DatePicker`.
     * @param minutesOfDay wall-clock minutes, clamped into 0..1439.
     */
    fun fromPickerDateMillis(
        pickerDateMillis: Long,
        minutesOfDay: Int,
        zone: ZoneId
    ): Long = Instant.ofEpochMilli(pickerDateMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .atTime(LocalTime.ofSecondOfDay(minutesOfDay.coerceIn(0, 1439) * 60L))
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    /** Whole days between two instants. Negative when [toMillis] is the earlier one. */
    fun daysBetween(fromMillis: Long, toMillis: Long): Int =
        ((toMillis - fromMillis) / MILLIS_PER_DAY).toInt()

    /**
     * How far the stored anchor sits from today, in the grower's own words.
     *
     * Printed beside the anchor on the form so the value is readable without opening the
     * dialog — "hoy" against "hace 37 días" is what tells a grower whether this row was
     * written when they think it was.
     */
    fun daysFromNowLabelEs(anchorMillis: Long, nowMillis: Long): String {
        if (anchorMillis <= 0L) return "Todavía sin inicio guardado"
        val days = daysBetween(anchorMillis, nowMillis)
        return when {
            days == 0 -> "El inicio es hoy"
            days == 1 -> "El inicio fue ayer"
            days > 1 -> "El inicio fue hace $days días"
            days == -1 -> "El inicio es mañana"
            else -> "El inicio es dentro de ${-days} días"
        }
    }
}