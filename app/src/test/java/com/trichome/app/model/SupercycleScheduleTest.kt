package com.trichome.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The photoperiod grid and the anchor picker, on the JVM.
 *
 * ## Why this file is mostly about honesty
 *
 * The cycle is 26 h or 32 h and the day is 24 h, so a fixed wall-clock strip cannot
 * show a repeating daily pattern. Every test below that could have papered over that —
 * by asserting only the lit count, or only that "something changed" — instead asserts
 * the specific consequence: how far the drift is, what the card says about it, and that
 * the grid agrees with the engine hour by hour.
 *
 * ## What is NOT covered here
 *
 * That the cells are laid out in two rows of twelve, that they are coloured from the
 * scheme, and that nothing scrolls twice. Those are shape, and shape is what
 * `SupercycleHeatmapStructureTest` reads the source for. Compose has no unit-test runtime
 * in this project, so there is no way to assert a `weight(1f)` from a test — the
 * `weight(0f)` that once crashed every terpene page proves it.
 */
class SupercycleScheduleTest {

    private val madrid: ZoneId = ZoneId.of("Europe/Madrid")
    private val newYork: ZoneId = ZoneId.of("America/New_York")
    private val losAngeles: ZoneId = ZoneId.of("America/Los_Angeles")
    private val utc: ZoneId = ZoneOffset.UTC

    private val june15: LocalDate = LocalDate.of(2026, 6, 15)
    private val hour: Long = 3_600_000L
    private val day: Long = 86_400_000L

    /** 2026-06-15 08:00 UTC. Mid-June, so no DST edge anywhere near it. */
    private val summerMorning: Long = instantAt(june15, 8, 0)

    /** An instant in [zone], so the fixtures read as local times rather than arithmetic. */
    private fun instantAt(date: LocalDate, hourOfDay: Int, minute: Int = 0, zone: ZoneId = utc): Long =
        LocalDateTime.of(date, LocalTime.of(hourOfDay, minute)).atZone(zone).toInstant().toEpochMilli()

    private fun stateAt(schedule: SupercycleSchedule, hourOfDay: Int): HeatmapHourState? =
        schedule.stateAt(hourOfDay)

    /* ── The geometry ────────────────────────────────────────────────────── */

    @Test
    fun thereAreTwentyFourHoursInTwoRowsOfTwelve() {
        val schedule = SupercycleScheduleBuilder.scheduleFor(
            cycleStartAt = summerMorning - 3 * day,
            lightHours = 18,
            darkHours = 6,
            nowMillis = summerMorning,
            zone = utc
        )

        assertEquals(24, schedule.hours.size)
        assertEquals(2, schedule.rows.size)
        assertEquals(SupercycleScheduleBuilder.COLUMNS, schedule.rows[0].cells.size)
        assertEquals(SupercycleScheduleBuilder.COLUMNS, schedule.rows[1].cells.size)
        // 0..23 in order, so "the third column of row two" is 14:00 and not a guess.
        assertEquals((0..23).toList(), schedule.hours.map { it.hourOfDay })
    }

    @Test
    fun theTicksShareTheCellsColumnsSoALabelCannotLandUnderTheWrongHour() {
        // The bug this prevents: a separate four-item tick row positions itself against
        // its own arrangement, and "00" ends up printed under 01.
        val schedule = SupercycleScheduleBuilder.scheduleFor(
            cycleStartAt = summerMorning,
            lightHours = 18,
            darkHours = 6,
            nowMillis = summerMorning,
            zone = utc
        )

        val ticks = schedule.rows.flatMap { it.ticks }
        assertEquals(24, ticks.size)
        assertEquals("00:00", ticks[0])
        assertEquals("03:00", ticks[3])
        assertNull("only every third column carries a label", ticks[1])
        assertEquals("12:00", ticks[12])
        assertEquals("21:00", ticks[21])
        // Column eleven carries no label: the day does not tick at 23:00, and printing
        // one there would suggest a resolution the grid does not have.
        assertNull(ticks[23])
    }

    @Test
    fun eachRowNamesTheSpanItCovers() {
        // Two rows of twelve cannot be misread as two twelve-hour periods if each row
        // says what it is. Without this, a grower reads the first row as the morning.
        val schedule = SupercycleScheduleBuilder.scheduleFor(
            cycleStartAt = summerMorning,
            lightHours = 18,
            darkHours = 6,
            nowMillis = summerMorning,
            zone = utc
        )

        assertEquals("00:00–12:00", schedule.rows[0].rangeEs)
        assertEquals("12:00–24:00", schedule.rows[1].rangeEs)
    }

    /* ── The cells answer the engine, not a second implementation ────────── */

    @Test
    fun everyCellAgreesWithTheEngineAboutThatInstant() {
        val anchor = summerMorning - 5 * day + 3 * hour
        val light = 18
        val dark = 6

        val schedule = SupercycleScheduleBuilder.scheduleFor(
            cycleStartAt = anchor,
            lightHours = light,
            darkHours = dark,
            nowMillis = summerMorning,
            zone = utc
        )

        // Read the engine directly at the middle of each hour and require the grid to
        // agree. A heatmap that recomputed the phase differently would pass every
        // lit-count assertion and still disagree with the card above it.
        schedule.hours.forEach { cell ->
            val middle = instantAt(june15, cell.hourOfDay) + hour / 2
            val engine = SuperCycleEngine.calculateSuperCycle(
                nowTimestamp = middle,
                cycleStartAt = anchor,
                lightHours = light,
                darkHours = dark
            )
            val expected = when (engine.phase) {
                Phase.LIGHT -> HeatmapHourState.LIT
                Phase.DARK -> HeatmapHourState.DARK
                Phase.OFF -> HeatmapHourState.PENDING
            }
            // A cell the boundary falls inside is TRANSITION by design: it contains both
            // phases, so the engine can only match one of them.
            if (cell.state != HeatmapHourState.TRANSITION) {
                assertEquals(
                    "hour ${cell.hourOfDay}: grid says ${cell.state}, engine says $expected",
                    expected,
                    cell.state
                )
            }
        }
    }

    @Test
    fun anEighteenSixCycleIsLitForEighteenHoursOfTheDay() {
        // 18/6 = 24 h exactly, so the wall-clock day really is one whole cycle and the
        // count is exact. This is the case where the strip can honestly be read as a
        // repeating daily pattern.
        val schedule = SupercycleScheduleBuilder.scheduleFor(
            cycleStartAt = instantAt(june15, 0),
            lightHours = 18,
            darkHours = 6,
            nowMillis = instantAt(june15, 10),
            zone = utc
        )

        assertEquals(24, schedule.totalCycleHours)
        assertEquals(0, schedule.driftHours)
        assertFalse(schedule.runsPastTheDay)
        assertEquals(18, schedule.litHourCount)
        assertEquals(HeatmapHourState.LIT, stateAt(schedule, 17))
        assertEquals(HeatmapHourState.DARK, stateAt(schedule, 18))
    }

    @Test
    fun theDayIsMarkedExactlyOnceAsNow() {
        val schedule = SupercycleScheduleBuilder.scheduleFor(
            cycleStartAt = summerMorning - day,
            lightHours = 18,
            darkHours = 6,
            nowMillis = summerMorning,
            zone = utc
        )

        assertEquals(
            "a 24-cell grid with no 'now' marker, or with two, is not telling the grower " +
                "which hour they are looking at",
            1,
            schedule.hours.count { it.isNow }
        )
        assertEquals(8, schedule.hours.first { it.isNow }.hourOfDay)
        assertTrue(
            "and the marked hour must say so out loud for a screen reader",
            schedule.hours.first { it.isNow }.descriptionEs.contains("ahora")
        )
    }

    /* ── The boundary: the honest-failure case ───────────────────────────── */

    @Test
    fun anHourThePhaseBoundaryFallsInsideIsMarkedAsTransition() {
        // Anchor at 02:17 on a 26 h cycle, so the light/dark boundary lands at 20:17 —
        // the middle of an hour rather than on its edge. Without a transition state the
        // cell would be rounded to whichever end was read first, and the hour's own
        // answer a coin flip.
        val schedule = SupercycleScheduleBuilder.scheduleFor(
            cycleStartAt = instantAt(june15, 2, 17),
            lightHours = 18,
            darkHours = 8,
            nowMillis = instantAt(june15, 12),
            zone = utc
        )

        assertEquals(26, schedule.totalCycleHours)
        assertEquals(HeatmapHourState.TRANSITION, stateAt(schedule, 20))
        // Its neighbours are not: the boundary is inside hour 20 and nowhere else.
        assertEquals(HeatmapHourState.LIT, stateAt(schedule, 19))
        assertEquals(HeatmapHourState.DARK, stateAt(schedule, 21))
    }

    @Test
    fun aTwentySixHourCycleSaysItIsNotADailyPattern() {
        val schedule = SupercycleScheduleBuilder.scheduleFor(
            cycleStartAt = summerMorning - day,
            lightHours = 18,
            darkHours = 8,
            nowMillis = summerMorning,
            zone = utc
        )

        assertEquals(26, schedule.totalCycleHours)
        assertEquals(2, schedule.driftHours)
        assertTrue(schedule.runsPastTheDay)
        assertEquals("+2 h", schedule.driftValueEs)

        val text = schedule.driftExplanationEs
        assertTrue("it has to name the real length: $text", text.contains("26 h"))
        assertTrue(
            "and deny the repetition: $text",
            text.contains("no un patrón que se repita mañana")
        )
    }

    @Test
    fun aThirtyTwoHourCycleReportsEightHoursOfDrift() {
        val schedule = SupercycleScheduleBuilder.scheduleFor(
            cycleStartAt = summerMorning,
            lightHours = 24,
            darkHours = 8,
            nowMillis = summerMorning,
            zone = utc
        )

        assertEquals(32, schedule.totalCycleHours)
        assertEquals(8, schedule.driftHours)
        assertEquals("+8 h", schedule.driftValueEs)
    }

    @Test
    fun aTwentyFourHourCycleIsToldItDoesRepeat() {
        // The opposite branch. Printing "this does not repeat" for a 24 h cycle would be
        // the same class of lie in the other direction, and it would teach the grower to
        // skip the sentence that matters on every other photoperiod.
        val schedule = SupercycleScheduleBuilder.scheduleFor(
            cycleStartAt = summerMorning,
            lightHours = 18,
            darkHours = 6,
            nowMillis = summerMorning,
            zone = utc
        )

        assertEquals(0, schedule.driftHours)
        assertEquals("0 h", schedule.driftValueEs)
        assertEquals("de desfase por día", schedule.driftLabelEs)
        assertTrue(
            "a 24 h cycle genuinely repeats, and the card has to say so: " +
                schedule.driftExplanationEs,
            schedule.driftExplanationEs.contains("sí se repite")
        )
    }

    @Test
    fun aCycleShorterThanADayReportsItsDriftAsShortfall() {
        val schedule = SupercycleScheduleBuilder.scheduleFor(
            cycleStartAt = summerMorning,
            lightHours = 12,
            darkHours = 6,
            nowMillis = summerMorning,
            zone = utc
        )

        assertEquals(-6, schedule.driftHours)
        assertEquals("6 h menos", schedule.driftValueEs)
        assertTrue(schedule.driftExplanationEs.contains("se adelanta 6 h"))
    }

    /* ── Hours before the anchor ─────────────────────────────────────────── */

    @Test
    fun hoursBeforeTheAnchorAreOutsideTheCycleNotDark() {
        // The anchor is now the grower's choice, so this case is reachable for the first
        // time. Painting a pre-anchor hour "dark" would assert that the lights were off
        // during a period the cycle does not describe at all.
        // Half past twelve, so the anchor falls *inside* hour 12 rather than on its edge:
        // an anchor at exactly 12:00 would leave that hour wholly inside the cycle.
        val anchor = instantAt(june15, 12, 30)
        val schedule = SupercycleScheduleBuilder.scheduleFor(
            cycleStartAt = anchor,
            lightHours = 18,
            darkHours = 8,
            nowMillis = anchor,
            zone = utc
        )

        assertEquals(HeatmapHourState.PENDING, stateAt(schedule, 0))
        assertEquals(HeatmapHourState.PENDING, stateAt(schedule, 11))
        // The hour the anchor falls inside is both: partly not the cycle yet.
        assertEquals(HeatmapHourState.TRANSITION, stateAt(schedule, 12))
        // From 13:00 the cycle is running, and the light phase simply runs off the end of
        // the day — 18 h of light starting at 12:30 leaves the day with eleven lit hours
        // and continues on tomorrow's grid. That is the picture being one day, not the
        // cycle, and it is why the card states the drift.
        assertEquals(HeatmapHourState.LIT, stateAt(schedule, 13))
        assertEquals(HeatmapHourState.LIT, stateAt(schedule, 23))
        assertEquals(11, schedule.litHourCount)
        assertTrue("and the card says so", schedule.driftExplanationEs.contains("no un patrón"))
    }

    @Test
    fun anHourWhoseOnlyIntersectionIsTheAnchorItselfIsFullyInTheCycle() {
        // The mirror of the previous test, so the straddle rule cannot be over-applied: an
        // anchor at exactly 12:00 makes hour 12 wholly part of the cycle.
        val schedule = SupercycleScheduleBuilder.scheduleFor(
            cycleStartAt = instantAt(june15, 12, 0),
            lightHours = 18,
            darkHours = 8,
            nowMillis = instantAt(june15, 12, 0),
            zone = utc
        )

        assertEquals(HeatmapHourState.PENDING, stateAt(schedule, 11))
        assertEquals(HeatmapHourState.LIT, stateAt(schedule, 12))
    }

    @Test
    fun aMissingAnchorMakesTheGridUnrenderableRatherThanAllDark() {
        val schedule = SupercycleScheduleBuilder.scheduleFor(
            cycleStartAt = 0L,
            lightHours = 18,
            darkHours = 6,
            nowMillis = summerMorning,
            zone = utc
        )

        assertFalse(
            "twenty-four identical cells would read as 'always dark', which is a claim " +
                "the engine refused to make",
            schedule.isRenderable
        )
        assertTrue(schedule.hours.all { it.state == HeatmapHourState.PENDING })
    }

    @Test
    fun aZeroLengthCycleIsUnrenderable() {
        val schedule = SupercycleScheduleBuilder.scheduleFor(
            cycleStartAt = summerMorning,
            lightHours = 0,
            darkHours = 0,
            nowMillis = summerMorning,
            zone = utc
        )

        assertFalse(schedule.isRenderable)
    }

    /* ── The zone is not optional ─────────────────────────────────────────── */

    @Test
    fun theDayIsTheGrowersDayNotUtc() {
        // 22:00 UTC on the 14th is 00:00 on the 15th in Madrid. Resolving the grid against
        // UTC would put the grower's midnight in the wrong column and shift the whole
        // picture by two hours.
        val instant = instantAt(LocalDate.of(2026, 6, 14), 22)

        val inUtc = SupercycleScheduleBuilder.scheduleFor(
            cycleStartAt = instant - hour,
            lightHours = 18,
            darkHours = 6,
            nowMillis = instant,
            zone = utc
        )
        val inMadrid = SupercycleScheduleBuilder.scheduleFor(
            cycleStartAt = instant - hour,
            lightHours = 18,
            darkHours = 6,
            nowMillis = instant,
            zone = madrid
        )

        // The anchor is 21:00 UTC, which is 23:00 in Madrid. In UTC the day is the 14th and
        // the grower's instant is hour 22 of it; in Madrid it is already the 15th and the
        // same instant is hour 00. One column, two answers, and the one that would be
        // wrong is the one a UTC-defaulted grid would draw.
        assertEquals(22, inUtc.hours.first { it.isNow }.hourOfDay)
        assertEquals(0, inMadrid.hours.first { it.isNow }.hourOfDay)
        // The day does not wrap: UTC's day is the 14th, Madrid's is the 15th.
        assertEquals(HeatmapHourState.PENDING, stateAt(inUtc, 0))
        assertEquals(HeatmapHourState.LIT, stateAt(inMadrid, 1))
        assertFalse(stateAt(inUtc, 23) == HeatmapHourState.PENDING)
    }

    /* ── The legend ──────────────────────────────────────────────────────── */

    @Test
    fun everyStateTheGridCanShowIsNamedInTheLegend() {
        val schedule = SupercycleScheduleBuilder.scheduleFor(
            cycleStartAt = summerMorning,
            lightHours = 18,
            darkHours = 6,
            nowMillis = summerMorning,
            zone = utc
        )

        val named = schedule.legend.map { it.state }.toSet()
        assertEquals(HeatmapHourState.entries.toSet(), named)
        assertTrue(
            "a legend with a blank entry explains nothing",
            schedule.legend.all { it.labelEs.isNotBlank() }
        )
    }

    /* ── The anchor preview ──────────────────────────────────────────────── */

    @Test
    fun thePreviewNamesTheSuperdayAndTheHumanDayItBeganOn() {
        val preview = SupercycleScheduleBuilder.anchorPreviewFor(
            cycleStartAt = summerMorning - 4 * day - hour,
            lightHours = 18,
            darkHours = 8,
            nowMillis = summerMorning,
            zone = utc
        )

        assertTrue(preview.superdayLabelEs.startsWith("Ahora: superdía "))
        // A 26 h cycle started four days and one hour ago puts us in superday 4, which
        // began on the 14th — not on the day the anchor was set, and not on today. The
        // whole reason the preview exists is that this date is not guessable.
        assertTrue(
            "the grower recognises a calendar date, not a millisecond: " +
                preview.superdayStartLabelEs,
            preview.superdayStartLabelEs.contains("14/06/2026")
        )
        assertTrue(preview.offsetLabelEs.contains("medianoche"))
        assertNull("a four-day-old anchor is fine", preview.warningEs)
        assertTrue(preview.canConfirm)
    }

    @Test
    fun thePreviewFollowsTheEngineRatherThanRecomputingTheSuperday() {
        val anchor = summerMorning - 3 * day - 2 * hour
        val preview = SupercycleScheduleBuilder.anchorPreviewFor(
            cycleStartAt = anchor,
            lightHours = 18,
            darkHours = 6,
            nowMillis = summerMorning,
            zone = utc
        )
        val engine = SuperCycleEngine.calculateSuperCycle(
            nowTimestamp = summerMorning,
            cycleStartAt = anchor,
            lightHours = 18,
            darkHours = 6
        )

        assertTrue(
            "the preview has to print the engine's superday: " + preview.superdayLabelEs,
            preview.superdayLabelEs.endsWith(engine.superday.toString())
        )
    }

    @Test
    fun aFutureAnchorCannotBeConfirmed() {
        // The engine clamps negative elapsed time to zero, so a future anchor would
        // render the whole day as the first light phase — a day of sunlight the cycle
        // does not describe. Refusing beats warning.
        val preview = SupercycleScheduleBuilder.anchorPreviewFor(
            cycleStartAt = summerMorning + 3 * day,
            lightHours = 18,
            darkHours = 6,
            nowMillis = summerMorning,
            zone = utc
        )

        assertFalse(preview.canConfirm)
        assertNotNull(preview.warningEs)
        assertTrue(preview.warningEs!!.contains("futuro"))
    }

    @Test
    fun anAnchorOlderThanAYearIsFlagged() {
        val preview = SupercycleScheduleBuilder.anchorPreviewFor(
            cycleStartAt = summerMorning - 500 * day,
            lightHours = 18,
            darkHours = 6,
            nowMillis = summerMorning,
            zone = utc
        )

        assertFalse(preview.canConfirm)
        assertTrue(preview.warningEs!!.contains("más de un año"))
    }

    @Test
    fun anAnchorOfExactlyAYearIsNotFlagged() {
        // The boundary case: the "more than a year" rule has to be strictly greater than,
        // or a perfectly ordinary 400-day-old cycle gets blocked.
        val preview = SupercycleScheduleBuilder.anchorPreviewFor(
            cycleStartAt = summerMorning - 400 * day,
            lightHours = 18,
            darkHours = 6,
            nowMillis = summerMorning,
            zone = utc
        )

        assertTrue(preview.canConfirm)
    }

    /* ── The DatePicker UTC trap ─────────────────────────────────────────── */

    @Test
    fun thePickerRoundTripsThroughUtcMidnightWithoutLosingTheLocalDay() {
        // `DatePicker` reports UTC midnight of the chosen day. Feeding that straight to
        // LocalDateTime lands the anchor on the previous evening at UTC-5 and renumbers
        // every superday from there.
        val instant = instantAt(june15, 8, 0, newYork)

        val rebuilt = SupercycleAnchorPicker.fromPickerDateMillis(
            pickerDateMillis = SupercycleAnchorPicker.toPickerDateMillis(instant, newYork),
            minutesOfDay = SupercycleAnchorPicker.minutesOf(instant, newYork),
            zone = newYork
        )

        assertEquals("the round trip has to land on the same instant", instant, rebuilt)
    }

    @Test
    fun thePickerRoundTripsAcrossADaylightSavingTransition() {
        // The week Europe/Madrid springs forward, 2026-03-29. A conversion that assumed
        // every day is 86 400 000 ms lands an hour out on one side of it.
        val instant = instantAt(LocalDate.of(2026, 3, 29), 2, 30, madrid)

        val rebuilt = SupercycleAnchorPicker.fromPickerDateMillis(
            pickerDateMillis = SupercycleAnchorPicker.toPickerDateMillis(instant, madrid),
            minutesOfDay = SupercycleAnchorPicker.minutesOf(instant, madrid),
            zone = madrid
        )

        assertEquals(instant, rebuilt)
    }

    @Test
    fun thePickerDateIsTheLocalDateNotTheUtcOne() {
        // 22:00 on the 14th in Los Angeles is 05:00 UTC on the 15th: same picker millis,
        // two local days. Reading the picker value as UTC would offer the wrong day.
        val instant = instantAt(LocalDate.of(2026, 6, 14), 22, 0, losAngeles)

        assertEquals(
            "the local day is the 14th",
            LocalDate.of(2026, 6, 14),
            Instant.ofEpochMilli(instant).atZone(losAngeles).toLocalDate()
        )
        assertEquals(
            "while the UTC day is already the 15th — which is the whole trap",
            LocalDate.of(2026, 6, 15),
            Instant.ofEpochMilli(instant).atZone(utc).toLocalDate()
        )
        assertEquals(
            LocalDate.of(2026, 6, 14),
            Instant.ofEpochMilli(SupercycleAnchorPicker.toPickerDateMillis(instant, losAngeles))
                .atZone(ZoneOffset.UTC).toLocalDate()
        )
    }

    @Test
    fun thePickerClampsAnImpossibleTimeOfDay() {
        // A TimePicker cannot emit 1500 minutes, but a clamped state is cheaper than a
        // conversion that throws at midnight on a bad round trip.
        val value = SupercycleAnchorPicker.fromPickerDateMillis(
            pickerDateMillis = SupercycleAnchorPicker.toPickerDateMillis(summerMorning, utc),
            minutesOfDay = 1500,
            zone = utc
        )

        assertEquals(
            LocalDateTime.of(june15, LocalTime.of(23, 59)),
            Instant.ofEpochMilli(value).atZone(utc).toLocalDateTime()
        )
    }

    @Test
    fun daysFromNowIsSpelledOutRatherThanSigned() {
        assertEquals(
            "El inicio fue ayer",
            SupercycleAnchorPicker.daysFromNowLabelEs(summerMorning - day, summerMorning)
        )
        assertEquals(
            "El inicio fue hace 37 días",
            SupercycleAnchorPicker.daysFromNowLabelEs(summerMorning - 37 * day, summerMorning)
        )
        assertEquals(
            "El inicio es hoy",
            SupercycleAnchorPicker.daysFromNowLabelEs(summerMorning, summerMorning)
        )
        assertEquals(
            "Todavía sin inicio guardado",
            SupercycleAnchorPicker.daysFromNowLabelEs(0L, summerMorning)
        )
    }
}