package com.trichome.app.model

import com.trichome.app.ui.components.LunarPanelLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F11: the lunar engine, reachable for an arbitrary date.
 *
 * ## Why a date selector needs its own arithmetic tests
 *
 * [LunarEngine] is already covered for "today". What it never had was a way to
 * *choose* an instant, so nothing tested whether stepping a day forward actually
 * moves the cycle forward, whether the offset is clamped or silently wrapped, or
 * whether the drift statement the screen now prints is derived from the numbers
 * it claims to come from. Those are the assertions below.
 *
 * ## What these tests cannot cover
 *
 * Nothing here says the phase matches the real sky. The engine's own KDoc records
 * that it is a mean synodic month rather than an ephemeris, and the honest claim
 * this file can make is the one the drift copy makes: the arithmetic is
 * self-consistent and the error bound is derived. A test that asserted "the moon
 * is a waxing gibbous on this date" would be asserting against an ephemeris this
 * app deliberately does not ship.
 */
class LunarTimelineTest {

    /** The J2000.0 reference the engine is anchored on, so the tests are readable. */
    private val reference = LunarEngine.NEW_MOON_REFERENCE_MILLIS

    /** An arbitrary present, far from any quarter instant. */
    private val present = reference + 812_345_678L

    /* ── The offset moves the instant and the cycle ──────────────────────── */

    @Test
    fun aZeroOffsetIsTheReferenceInstantItself() {
        assertEquals(present, LunarTimeline.readingAt(present, 0).epochMillis)
    }

    @Test
    fun oneDayForwardIsExactlyOneDayLater() {
        val day = 86_400_000L

        assertEquals(
            present + day,
            LunarTimeline.readingAt(present, 1).epochMillis
        )
        assertEquals(
            present - day,
            LunarTimeline.readingAt(present, -1).epochMillis
        )
    }

    @Test
    fun theCycleAdvancesAsTheDaysDo() {
        // Not "the countdown falls by a day per day": the moment a step crosses a
        // phase boundary the wait jumps to the *next* one, so the series is a saw
        // tooth and asserting a constant difference would be asserting the
        // boundaries do not exist. What has to hold is the shape of the saw: every
        // step either shortens the wait by exactly a day, or lengthens it because
        // a boundary went by — and never anything else.
        val remaining = (0..60).map { offset ->
            val reading = LunarTimeline.readingAt(present, offset)
            val wait = reading.nextPhaseMillis - reading.epochMillis

            assertTrue(
                "the wait at offset $offset is not positive",
                wait > 0
            )
            assertTrue(
                "the wait at offset $offset is longer than a lunation",
                wait <= LunarEngine.SYNODIC_MONTH_MILLIS
            )
            wait
        }

        remaining.zipWithNext().forEach { (before, after) ->
            val delta = before - after
            val crossedNothing = delta == 86_400_000L
            val crossedABoundary = after > before

            assertTrue(
                "adding a day changed the wait by $delta, which is neither a day " +
                    "nor a boundary crossing",
                crossedNothing || crossedABoundary
            )
        }

        assertTrue(
            "sixty days has to have crossed several boundaries",
            remaining.zipWithNext().count { it.second > it.first } >= 2
        )
    }

    @Test
    fun steppingAwayAndBackReturnsTheSameReading() {
        val out = LunarTimeline.readingAt(present, 137)
        val again = LunarTimeline.readingAt(present, 137)

        assertEquals(out.epochMillis, again.epochMillis)
        assertEquals(out.snapshot.phase, again.snapshot.phase)
        assertEquals(out.snapshot.illumination, again.snapshot.illumination, 0f)
        assertEquals(out.daysUntilNextPhase, again.daysUntilNextPhase)
    }

    @Test
    fun aReadingNeverDependsOnHowItWasReached() {
        // The selector and the direct entry point are two doors into the same
        // function. If they disagreed, a grower who used the stepper and one who
        // opened on the same day would be shown two different moons.
        assertEquals(
            LunarTimeline.readingAt(present, 0).snapshot.phase,
            LunarTimeline.readingNow(present).snapshot.phase
        )
        assertEquals(
            LunarTimeline.readingAt(present, 0).epochMillis,
            LunarTimeline.readingNow(present).epochMillis
        )
    }

    @Test
    fun theSelectorIsDeterministic() {
        repeat(3) {
            assertEquals(
                LunarTimeline.readingAt(present, 42).epochMillis,
                LunarTimeline.readingAt(present, 42).epochMillis
            )
        }
    }

    /* ── The range is bounded, and says which bound it clamped to ─────────── */

    @Test
    fun anOffsetOutsideTheRangeIsClampedAndTheReadingReportsTheClampedValue() {
        val beyond = LunarTimeline.OFFSET_RANGE_MAX_DAYS + 500

        val reading = LunarTimeline.readingAt(present, beyond)

        assertEquals(
            "the number on screen and the instant that was computed must agree",
            LunarTimeline.OFFSET_RANGE_MAX_DAYS,
            reading.offsetDays
        )
        assertEquals(
            present + LunarTimeline.OFFSET_RANGE_MAX_DAYS * 86_400_000L,
            reading.epochMillis
        )
    }

    @Test
    fun anOffsetBeforeTheRangeIsClampedToTheFirstDayToo() {
        val reading = LunarTimeline.readingAt(present, LunarTimeline.OFFSET_RANGE_MIN_DAYS - 500)

        assertEquals(LunarTimeline.OFFSET_RANGE_MIN_DAYS, reading.offsetDays)
    }

    @Test
    fun theStepperSaysWhenItCannotStepFurther() {
        assertTrue(
            "at the first day there is nothing earlier to go to",
            !LunarTimeline.canStepFurther(LunarTimeline.OFFSET_RANGE_MIN_DAYS, back = true)
        )
        assertTrue(
            "and it can still go forward",
            LunarTimeline.canStepFurther(LunarTimeline.OFFSET_RANGE_MIN_DAYS, back = false)
        )
        assertTrue(
            "at the last day there is nothing later to go to",
            !LunarTimeline.canStepFurther(LunarTimeline.OFFSET_RANGE_MAX_DAYS, back = false)
        )
        assertTrue(
            "and it can still go back",
            LunarTimeline.canStepFurther(LunarTimeline.OFFSET_RANGE_MAX_DAYS, back = true)
        )
        assertTrue("zero can go both ways", LunarTimeline.canStepFurther(0, back = true))
        assertTrue(LunarTimeline.canStepFurther(0, back = false))
    }

    @Test
    fun theRangeIsExactlyAYearEachWay() {
        // A named constant rather than a magic number, because the range is part
        // of the answer: beyond roughly a year the mean-month error stops being an
        // afternoon's footnote.
        assertEquals(365, LunarTimeline.OFFSET_RANGE_MAX_DAYS)
        assertEquals(-365, LunarTimeline.OFFSET_RANGE_MIN_DAYS)
    }

    /* ── The drift is derived, not typed ──────────────────────────────────── */

    @Test
    fun theDriftBoundIsHalfTheMeasuredLunationRange() {
        val expected = (LunarTimeline.MAX_LUNATION_DAYS - LunarTimeline.MIN_LUNATION_DAYS) / 2.0 * 24.0

        assertEquals(
            "the bound has to follow the two measured numbers it is derived from",
            expected,
            LunarTimeline.DRIFT_BOUND_HOURS,
            1e-9
        )
    }

    @Test
    fun theDriftStatementMarksTheBoundAsAnApproximation() {
        val reading = LunarTimeline.readingAt(present, 0)

        assertTrue(
            "the drift sentence must carry the ≈ marker; it reads: \"${reading.driftEs}\"",
            reading.driftEs.contains("≈")
        )
        assertTrue(
            "and it must print the derived bound, not a rounded literal",
            reading.driftEs.contains(LunarTimeline.DRIFT_BOUND_ES)
        )
    }

    @Test
    fun theDriftStatementSaysWhichNumbersAreMeasuredAndWhichAreCalculated() {
        val reading = LunarTimeline.readingAt(present, 0)

        assertTrue(
            "the measured lunation range must be labelled as measured",
            reading.measuredLabelEs.startsWith("Medido")
        )
        assertTrue(
            "and the phase as calculated",
            reading.calculatedLabelEs.startsWith("Calculado")
        )
        assertTrue(
            "the drift sentence must say the model is not an ephemeris, or a " +
                "mean-month phase reads like a table of instants",
            reading.driftEs.contains("efeméride")
        )
    }

    @Test
    fun theDriftBoundIsRenderedWithASpanishDecimalComma() {
        assertEquals(
            "6,7",
            LunarTimeline.DRIFT_BOUND_ES
        )
    }

    @Test
    fun theEngineConstantsWereNotTouchedToSuitTheSelector() {
        // The selector is additive on purpose. A mean month, an eighth of it and
        // the J2000 reference are the engine's, and a selector that quietly
        // "improved" them would be a second source of truth for the phase.
        assertEquals(2_551_442_877L, LunarEngine.SYNODIC_MONTH_MILLIS)
        assertEquals(947_182_440_000L, LunarEngine.NEW_MOON_REFERENCE_MILLIS)
        assertEquals(LunarEngine.SYNODIC_MONTH_MILLIS / 8, LunarEngine.QUARTER_MILLIS)
    }

    /* ── The next phase, from the engine's own eighths ───────────────────── */

    @Test
    fun theNextPhaseIsTheOneTheEngineOpensNext() {
        // Pinned at the reference itself, where the phase is new moon and the
        // next boundary is exactly one eighth of the month away.
        val (phase, instant) = LunarTimeline.nextPhaseChange(reference)

        assertEquals(LunarPhase.WAXING_CRESCENT, phase)
        assertEquals(reference + LunarEngine.QUARTER_MILLIS, instant)
    }

    @Test
    fun theNextBoundaryIsAlwaysInTheFuture() {
        listOf(0L, 1L, LunarEngine.QUARTER_MILLIS, LunarEngine.QUARTER_MILLIS - 1,
            LunarEngine.SYNODIC_MONTH_MILLIS - 1).forEach { position ->
            val instant = reference + position
            val (_, next) = LunarTimeline.nextPhaseChange(instant)

            assertTrue(
                "at position $position the next boundary is not in the future",
                next > instant
            )
            assertTrue(
                "and it is no more than a full month away",
                next - instant <= LunarEngine.SYNODIC_MONTH_MILLIS
            )
        }
    }

    @Test
    fun theBoundaryTheSelectorAnnouncesIsTheOneTheEngineReportsJustAfterIt() {
        // The two have to be the same fact. If they were computed separately, a
        // reading could name one phase and the day would open on another.
        val instant = reference + 3_000_000_000L
        val (_, boundary) = LunarTimeline.nextPhaseChange(instant)

        assertEquals(
            "the selector's boundary and the engine's phase one millisecond later " +
                "disagree",
            LunarTimeline.nextPhaseChange(instant).first,
            LunarEngine.phaseAt(boundary)
        )
        assertNotEquals(
            "and it has to be a boundary, not the phase already in progress",
            LunarEngine.phaseAt(instant),
            LunarEngine.phaseAt(boundary)
        )
    }

    /* ── The copy ────────────────────────────────────────────────────────── */

    @Test
    fun everyPhaseCarriesATipAndTheReadingAlwaysCarriesIt() {
        // The tip is the point of the panel, so a reading with a blank one is a
        // phase table rather than a feature.
        for (offset in listOf(-365, -100, -1, 0, 1, 100, 365)) {
            val reading = LunarTimeline.readingAt(present, offset)

            assertTrue("offset $offset has no tip headline", reading.tipHeadlineEs.isNotBlank())
            assertTrue("offset $offset has no advice", reading.tipAdviceEs.isNotBlank())
        }
    }

    @Test
    fun everyPhaseTheSelectorCanLandOnHasItsOwnAdvice() {
        // Every phase, reached by stepping: the eight are a fact about the cycle,
        // so a selector that can step a year must be able to land on all of them.
        val reached = (0..LunarTimeline.OFFSET_RANGE_MAX_DAYS)
            .map { LunarTimeline.readingAt(present, it).snapshot.phase }
            .toSet()

        assertEquals(
            "a year of daily steps has to pass through the whole cycle, so every " +
                "phase's tip is reachable",
            LunarPhase.entries.toSet(),
            reached
        )
        LunarPhase.entries.forEach { phase ->
            val tip = LunarEngine.tipFor(phase)
            assertTrue("$phase has no headline", tip.headlineEs.isNotBlank())
            assertTrue("$phase has no advice", tip.adviceEs.isNotBlank())
        }
    }

    @Test
    fun theReadingCarriesTheEnginesOwnDisclaimer() {
        assertEquals(
            "the panel has to repeat the disclaimer the calendar bar already shows, " +
                "so a grower arriving from the dialog has read it too",
            LunarEngine.DISCLAIMER_ES,
            LunarTimeline.DISCLAIMER_ES
        )
        assertTrue(LunarTimeline.DISCLAIMER_ES.isNotBlank())
    }

    @Test
    fun theDateLabelNamesTodayYesterdayAndTomorrowAndOtherwiseTheDate() {
        assertEquals(LunarTimeline.TODAY_ES, LunarTimeline.readingAt(present, 0).dateLabelEs)
        assertEquals(LunarTimeline.YESTERDAY_ES, LunarTimeline.readingAt(present, -1).dateLabelEs)
        assertEquals(LunarTimeline.TOMORROW_ES, LunarTimeline.readingAt(present, 1).dateLabelEs)

        val further = LunarTimeline.readingAt(present, 40).dateLabelEs
        assertTrue("\"$further\" does not name the date", further.contains("días"))
    }

    @Test
    fun theOffsetLabelSignsEveryNonZeroOffset() {
        assertEquals("0 días", LunarTimeline.offsetLabelEs(0))
        assertEquals("+3 días", LunarTimeline.offsetLabelEs(3))
        // A real minus sign, not a hyphen: the mojibake and the typography defects
        // this project has already fixed both lived in that character.
        assertEquals("−12 días", LunarTimeline.offsetLabelEs(-12))
    }

    @Test
    fun theCountdownWordingMatchesTheNumber() {
        assertTrue(
            LunarTimeline.nextPhaseLineEs("Luna llena", 0).contains(LunarTimeline.TODAY_ES.lowercase())
        )
        assertTrue(LunarTimeline.nextPhaseLineEs("Luna llena", 1).contains("1 días"))
        assertTrue(LunarTimeline.nextPhaseLineEs("Luna llena", 4).contains("4 días"))
    }

    @Test
    fun theDateFormatterIsZoneExplicit() {
        val instant = reference
        val utc = LunarDateFormatter.utcDateEs(instant)
        val madrid = LunarDateFormatter.localDateEs(instant, java.time.ZoneId.of("Europe/Madrid"))

        assertEquals("06 ene 2000", utc)
        assertTrue(
            "a local date has to be produced, or the panel cannot tell the grower " +
                "what day that is for them",
            madrid.isNotBlank()
        )
        assertEquals(
            0,
            LunarDateFormatter.daysBetween(instant, instant)
        )
        assertEquals(30, LunarDateFormatter.daysBetween(instant, instant + 30L * 86_400_000L))
    }

    @Test
    fun theReadingNowIsTheZeroOffsetReading() {
        assertEquals(
            LunarTimeline.readingAt(present, 0).epochMillis,
            LunarTimeline.readingNow(present).epochMillis
        )
    }

    @Test
    fun theBodyCapIsNamedAndUsable() {
        // Extracted so it is a number rather than a `LocalConfiguration` read. A
        // dialog body that is not capped pushes its confirm button off a small
        // screen, and the panel carries a stepper, a card, a tip and a drift
        // paragraph.
        assertTrue(
            "the body cap has to leave room for the dialog's own chrome, got " +
                "${LunarPanelLayout.MAX_BODY_HEIGHT_DP}dp",
            LunarPanelLayout.MAX_BODY_HEIGHT_DP in 200..900
        )
    }
}