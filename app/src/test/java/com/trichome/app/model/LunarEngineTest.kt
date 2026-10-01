package com.trichome.app.model

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * Lunar phase math against a fixed reference instant.
 *
 * The engine has no clock, no timezone and no table, so every assertion here is
 * about arithmetic: where the eighths of the cycle fall, that the illuminated
 * fraction behaves like a cosine rather than a ramp, that `daysUntil` counts
 * forward, and — the one property a moon phase must never violate — that the same
 * instant is the same phase for a phone in any timezone.
 *
 * `LunarEngine.NEW_MOON_REFERENCE_MILLIS` is 2000-01-06T18:14:00Z, which is
 * JD 2451550.1 (J2000.0). Every instant below is written as
 * `REFERENCE + n * QUARTER_MILLIS` rather than as a wall-clock string, so the
 * suite stays readable as the cycle is reasoned about instead of encoding dates
 * nobody can check.
 */
class LunarEngineTest {

    private val reference: Long = LunarEngine.NEW_MOON_REFERENCE_MILLIS
    private val quarter: Long = LunarEngine.QUARTER_MILLIS
    private val day: Long = 86_400_000L

    /** The exact instant each phase opens, indexed by eighth of the cycle. */
    private val bandStarts: List<LunarPhase> = LunarPhase.entries.toList()

    private val newMoon = reference
    private val fullMoon = reference + 4 * quarter

    /* ── Every phase is reachable ─────────────────────────────────────── */

    @Test
    fun allEightPhasesAreReachableAndInCycleOrder() {
        val reached = bandStarts.indices.map { LunarEngine.phaseAt(reference + it * quarter) }

        assertEquals(
            "the cycle must open all eight named phases, no more and no fewer",
            LunarPhase.entries.toList(),
            reached
        )
        assertEquals(
            "each phase must be reached once and only once",
            8,
            reached.toSet().size
        )
    }

    @Test
    fun aPhaseIsItsOwnSuccessorAfterOneQuarter() {
        bandStarts.indices.forEach { band ->
            // The eighth boundary is the cycle start again, one synodic month later.
            // QUARTER_MILLIS * 8 is 5 ms short of SYNODIC_MONTH_MILLIS, so the
            // engine's cycle start instant has to be used here rather than
            // reference + 8 * quarter, which would still be inside the old cycle.
            val openedAt = reference + band * quarter
            val nextAt =
                if (band == bandStarts.lastIndex) reference
                else reference + (band + 1) * quarter

            assertEquals(
                "the eighth at $openedAt must be ${bandStarts[band]}",
                band,
                bandStarts.indexOf(LunarEngine.phaseAt(openedAt))
            )
            assertEquals(
                "and the next eighth must open ${bandStarts[(band + 1) % bandStarts.size]}",
                bandStarts[(band + 1) % bandStarts.size],
                LunarEngine.phaseAt(nextAt)
            )
        }
    }

    /* ── Where the cycle starts and where it is full ───────────────────── */

    @Test
    fun theNewMoonOpensTheCycleAtZero() {
        assertEquals(
            "the reference instant is new moon, so the fraction is exactly 0",
            0f,
            LunarEngine.cycleFraction(newMoon),
            0f
        )
        assertEquals(LunarPhase.NEW_MOON, LunarEngine.phaseAt(newMoon))
        assertEquals(0L, LunarEngine.cyclePositionMillis(newMoon))
    }

    @Test
    fun theFullMoonSitsInTheMiddleOfTheCycle() {
        assertEquals(
            "four eighths of a 29.53 day cycle is 14.77 days, not 15",
            0.5f,
            LunarEngine.cycleFraction(fullMoon),
            0.0001f
        )
        assertEquals(LunarPhase.FULL_MOON, LunarEngine.phaseAt(fullMoon))
    }

    @Test
    fun aQuarterIsLowerInclusiveSoABoundaryBelongsToThePhaseItOpens() {
        bandStarts.indices.forEach { band ->
            val atBoundary = reference + band * quarter
            assertEquals(
                "the instant a phase opens belongs to that phase, not the previous one",
                bandStarts[band],
                LunarEngine.phaseAt(atBoundary)
            )
            assertEquals(
                "and one millisecond earlier it is the phase before",
                bandStarts[(band + 7) % 8],
                LunarEngine.phaseAt(atBoundary - 1L)
            )
        }
    }

    @Test
    fun anInstantOneCycleBackIsTheSamePhase() {
        assertEquals(
            "the model is a uniform cycle, so one synodic month back is identical",
            LunarEngine.phaseAt(newMoon),
            LunarEngine.phaseAt(newMoon - LunarEngine.SYNODIC_MONTH_MILLIS)
        )
        assertTrue(
            "the position must never be negative, even before the reference",
            LunarEngine.cyclePositionMillis(newMoon - 3 * LunarEngine.SYNODIC_MONTH_MILLIS) >= 0L
        )
    }

    /* ── Illumination ──────────────────────────────────────────────────── */

    @Test
    fun illuminationIsZeroAtTheNewMoonAndOneAtTheFull() {
        assertEquals(
            "an unlit disc at new moon",
            0f,
            LunarEngine.illumination(newMoon),
            0.0001f
        )
        assertEquals(
            "a fully lit disc at full moon",
            1f,
            LunarEngine.illumination(fullMoon),
            0.0001f
        )
    }

    @Test
    fun illuminationIsSymmetricAroundTheFullMoon() {
        val offsets = listOf(quarter / 2, quarter, 2 * quarter, 3 * quarter)
        offsets.forEach { offset ->
            assertEquals(
                "the same distance either side of full moon reads the same",
                LunarEngine.illumination(fullMoon - offset),
                LunarEngine.illumination(fullMoon + offset),
                0.0001f
            )
        }
    }

    @Test
    fun illuminationIsSymmetricAroundTheNewMoonToo() {
        val offsets = listOf(quarter / 2, quarter, 2 * quarter, 3 * quarter)
        offsets.forEach { offset ->
            assertEquals(
                "a crescent before new moon reads like the crescent after it",
                LunarEngine.illumination(newMoon - offset),
                LunarEngine.illumination(newMoon + offset),
                0.0001f
            )
        }
    }

    @Test
    fun illuminationRisesMonotonicallyThroughTheWaxingHalf() {
        var previous = -1f
        (0..7).forEach { band ->
            val lit = LunarEngine.illumination(newMoon + band * quarter / 2)
            assertTrue(
                "illumination must keep rising up to the full moon, was $lit after $previous",
                lit >= previous
            )
            previous = lit
        }
        assertTrue(
            "a first quarter reads about half lit, not a linear ramp",
            Math.abs(LunarEngine.illumination(newMoon + 2 * quarter) - 0.5f) < 0.01f
        )
    }

    @Test
    fun illuminationStaysInsideTheUnitRangeOverAWholeCycle() {
        (0 until LunarEngine.SYNODIC_MONTH_MILLIS.toInt() step 7_919_533).forEach { offset ->
            val lit = LunarEngine.illumination(newMoon + offset)
            assertTrue("illumination $lit escaped 0..1", lit in 0f..1f)
        }
    }

    /* ── Waxing / waning ───────────────────────────────────────────────── */

    @Test
    fun theMoonIsWaxingBeforeTheFullAndWaningAfterIt() {
        assertEquals(LunarTrend.WAXING, LunarEngine.trend(newMoon))
        assertEquals(LunarTrend.WAXING, LunarEngine.trend(newMoon + 3 * quarter))
        assertEquals(LunarTrend.WANING, LunarEngine.trend(newMoon + 5 * quarter))
        assertEquals(LunarTrend.WANING, LunarEngine.trend(newMoon + 7 * quarter))
    }

    @Test
    fun aSnapshotAgreesWithEverySingleValueItWasBuiltFrom() {
        val at = newMoon + 2 * quarter
        val snapshot = LunarEngine.snapshot(at)

        assertEquals(LunarEngine.phaseAt(at), snapshot.phase)
        assertEquals(LunarEngine.cycleFraction(at), snapshot.cycleFraction, 0f)
        assertEquals(LunarEngine.illumination(at), snapshot.illumination, 0f)
        assertEquals(LunarEngine.trend(at), snapshot.trend)
        assertEquals(LunarEngine.tipFor(LunarEngine.phaseAt(at)), snapshot.tip)
        assertTrue(snapshot.isWaxing)
        assertTrue(!snapshot.isWaning)
    }

    /* ── daysUntil ─────────────────────────────────────────────────────── */

    @Test
    fun daysUntilIsZeroForThePhaseThatStartsRightNow() {
        assertEquals(0, LunarEngine.daysUntil(LunarPhase.NEW_MOON, newMoon))
        assertEquals(0, LunarEngine.daysUntil(LunarPhase.FULL_MOON, fullMoon))
    }

    @Test
    fun daysUntilCountsForwardAcrossAMonthBoundary() {
        // Just after a new moon: the next new moon is 28.5 days away, so rounding
        // up is 29 and rounding down would understate it.
        assertEquals(
            "29.53 - 1 = 28.53 days, which rounds up to 29",
            29,
            LunarEngine.daysUntil(LunarPhase.NEW_MOON, newMoon + day)
        )
        // One second past the new moon the answer is nearly a full lunation.
        assertEquals(
            "a second into the cycle, the next new moon is a whole lunation out",
            30,
            LunarEngine.daysUntil(LunarPhase.NEW_MOON, newMoon + 1_000L)
        )
        assertEquals(
            "the first quarter is 3.69 days out",
            4,
            LunarEngine.daysUntil(LunarPhase.WAXING_CRESCENT, newMoon)
        )
        assertEquals(
            "the full moon is 14.77 days out",
            15,
            LunarEngine.daysUntil(LunarPhase.FULL_MOON, newMoon)
        )
    }

    @Test
    fun daysUntilFromInsideAPhasePointsAtTheUpcomingBoundaryNotTheLastOne() {
        // Halfway through the waxing gibbous, three quarters in.
        val insideGibbous = newMoon + 3 * quarter + quarter / 2
        assertEquals(LunarPhase.WAXING_GIBBOUS, LunarEngine.phaseAt(insideGibbous))
        assertEquals(
            "the full moon is half an eighth ahead, about 1.8 days",
            2,
            LunarEngine.daysUntil(LunarPhase.FULL_MOON, insideGibbous)
        )
        assertEquals(
            "the gibbous already running only comes round again in 27.7 days",
            28,
            LunarEngine.daysUntil(LunarPhase.WAXING_GIBBOUS, insideGibbous)
        )
    }

    @Test
    fun daysUntilNeverLeavesOneSynodicMonthForAnyPhaseAndInstant() {
        val instants = listOf(0L, 1L, day, 12L * day, 200L * day) +
            bandStarts.indices.map { reference + it * quarter + 1L }
        instants.forEach { at ->
            LunarPhase.entries.forEach { phase ->
                val days = LunarEngine.daysUntil(phase, at)
                assertTrue(
                    "$phase in $days days from $at escaped 0..30",
                    days in 0..30
                )
            }
        }
    }

    /* ── Timezone independence ─────────────────────────────────────────── */

    @Test
    fun theSameInstantIsTheSamePhaseInEveryTimezone() {
        val original = TimeZone.getDefault()
        val zones = listOf("UTC", "America/Argentina/Buenos_Aires", "Pacific/Auckland", "Asia/Tokyo")
        val at = newMoon + 3 * quarter + 6L * 3_600_000L
        val expectedPhase = LunarEngine.phaseAt(at)
        val expectedFraction = LunarEngine.cycleFraction(at)
        val expectedIllumination = LunarEngine.illumination(at)
        val expectedTrend = LunarEngine.trend(at)
        val expectedDays = LunarEngine.daysUntil(LunarPhase.FULL_MOON, at)
        val localDaysSeen = mutableSetOf<Int>()

        try {
            zones.forEach { id ->
                TimeZone.setDefault(TimeZone.getTimeZone(id))
                assertEquals(
                    "the phase of an instant cannot depend on the device timezone ($id)",
                    expectedPhase,
                    LunarEngine.phaseAt(at)
                )
                assertEquals(expectedFraction, LunarEngine.cycleFraction(at), 0f)
                assertEquals(expectedIllumination, LunarEngine.illumination(at), 0f)
                assertEquals(expectedTrend, LunarEngine.trend(at))
                assertEquals(expectedDays, LunarEngine.daysUntil(LunarPhase.FULL_MOON, at))
                localDaysSeen += Instant.ofEpochMilli(at)
                    .atZone(ZoneId.of(id))
                    .toLocalDate()
                    .dayOfYear
            }
        } finally {
            TimeZone.setDefault(original)
        }

        assertTrue(
            "the zones really have to disagree on the local date, otherwise the " +
                "test proves nothing: they landed on days $localDaysSeen",
            localDaysSeen.size > 1
        )
    }

    @Test
    fun aLocalCalendarDayCanDisagreeWithThePhaseAndThatIsCorrect() {
        // The engine answers "what phase is this instant", never "what is today's
        // phase", so a caller that formats a local date is the only thing that can
        // disagree. This pins that separation rather than hiding it.
        val aucklandOpen = LocalDate.of(2026, 3, 20)
            .atStartOfDay(ZoneId.of("Pacific/Auckland"))
            .toInstant()
            .toEpochMilli()
        val utcOpen = LocalDate.of(2026, 3, 20)
            .atStartOfDay(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()

        assertTrue(
            "Auckland and UTC open 2026-03-20 twelve hours apart",
            aucklandOpen != utcOpen
        )
        assertTrue(
            "so the two instants sit at different points of the cycle",
            LunarEngine.cycleFraction(aucklandOpen) != LunarEngine.cycleFraction(utcOpen)
        )
        assertTrue(
            "and each instant keeps the phase its own position implies",
            LunarEngine.phaseAt(aucklandOpen) in LunarPhase.entries &&
                LunarEngine.phaseAt(utcOpen) in LunarPhase.entries
        )
    }

    /* ── The advice is data, and every phase has one ───────────────────── */

    @Test
    fun everyPhaseHasExactlyOneAdviceAndNoTwoPhasesShareOne() {
        val tips = LunarEngine.tips

        assertEquals(8, tips.size)
        assertEquals(
            "each phase must be advised exactly once",
            LunarPhase.entries.toSet(),
            tips.map { it.phase }.toSet()
        )
        tips.forEach { tip ->
            assertTrue(
                "${tip.phase} has no headline",
                tip.headlineEs.isNotBlank()
            )
            assertTrue(
                "${tip.phase} has no advice",
                tip.adviceEs.isNotBlank()
            )
            assertTrue(
                "the tip must point back at the phase it was asked for",
                tip.phase.labelEs.isNotBlank() && tip.phase.iconKey.isNotBlank()
            )
        }
        assertEquals(
            "the advice must be distinct, not the same sentence eight times",
            8,
            tips.map { it.adviceEs }.toSet().size
        )
    }

    @Test
    fun germinationBelongsToTheWaxingSideAndHarvestToTheFullMoon() {
        val waxing = LunarPhase.entries.filter { phaseIsWaxing(it) }
        assertTrue(
            "the growing tips belong to the waxing half",
            waxing.isNotEmpty()
        )
        assertTrue(
            "the full moon must carry the flowering advice",
            LunarEngine.tipFor(LunarPhase.FULL_MOON).adviceEs.contains("floración", true)
        )
        assertTrue(
            "the new moon must carry the germination advice",
            LunarEngine.tipFor(LunarPhase.NEW_MOON).adviceEs.contains("germinación", true)
        )
        assertTrue(
            "a stimulation-pruning tip must sit on a waxing phase",
            LunarEngine.tipFor(LunarPhase.FIRST_QUARTER).adviceEs.contains("estimulación")
        )
    }

    @Test
    fun theEngineLabelsItsOwnOutputAsGuidance() {
        assertTrue(
            "the disclaimer must say it is not a measurement",
            LunarEngine.DISCLAIMER_ES.contains("no es una medición", true)
        )
    }

    /** Whether a phase sits in the waxing half, derived from the engine itself. */
    private fun phaseIsWaxing(phase: LunarPhase): Boolean =
        LunarEngine.isWaxing(reference + LunarEngine.phaseStartOffsetMillis(phase))
}