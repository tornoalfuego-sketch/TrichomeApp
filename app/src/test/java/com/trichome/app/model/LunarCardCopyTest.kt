package com.trichome.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The calendar's lunar bar: what it says, and that it says anything.
 *
 * ## What this covers
 *
 * The bar's copy is decided in [LunarCardCopy] rather than in a composable because
 * `compose-ui-test` lives only in `androidTest`, which is device-only. That split is
 * only worth anything if the tests hold the part that can be silently wrong:
 *
 *  - **every phase produces non-blank advice.** A phase name on its own teaches
 *    nothing, so [LunarEngine.tipFor] is the feature; a rewrite of a sentence that
 *    emptied it, or a wiring bug that dropped the tip, would still compile and still
 *    render a finished-looking card.
 *  - **the toggle is operable and states its action**, and its label differs when
 *    collapsed and expanded.
 *  - **a glyph is resolved for every `iconKey` the engine can emit**, so a rename of
 *    `labelEs` cannot orphan one.
 *
 * ## What this does NOT cover
 *
 * No assertion that the panel animates, that the bar does not push the calendar off
 * screen, or that the button is reachable by touch or by screen reader. Those are
 * composition and gesture behaviour and need `androidTest` on a device.
 */
class LunarCardCopyTest {

    /** One instant per phase, taken at the phase's own start instant. */
    private fun instantFor(phase: LunarPhase): Long =
        LunarEngine.NEW_MOON_REFERENCE_MILLIS + LunarEngine.phaseStartOffsetMillis(phase)

    @Test
    fun everyPhaseCarriesNonBlankAdvice() {
        LunarPhase.entries.forEach { phase ->
            val content = LunarCardCopy.contentOf(LunarEngine.snapshot(instantFor(phase)))

            assertTrue(
                "${phase.name} has no advice sentence: a phase name alone teaches nothing",
                content.adviceEs.isNotBlank()
            )
            assertTrue(
                "${phase.name} has no tip headline",
                content.tipHeadlineEs.isNotBlank()
            )
            assertTrue(
                "${phase.name} has no phase label",
                content.phaseLabelEs.isNotBlank()
            )
            assertEquals(
                "the tip's own phase must agree with the snapshot for ${phase.name}",
                phase,
                LunarEngine.tipFor(phase).phase
            )
        }
    }

    @Test
    fun everyAdviceSentenceIsDistinctFromEveryOther() {
        // Two phases sharing advice is a copy-paste bug that reads fine and teaches
        // the wrong thing; asserting non-blankness would not catch it.
        val advice = LunarPhase.entries.map { LunarEngine.tipFor(it).adviceEs }
        assertEquals(
            "two phases share the same advice sentence",
            advice.size,
            advice.distinct().size
        )
        val headlines = LunarPhase.entries.map { LunarEngine.tipFor(it).headlineEs }
        assertEquals(
            "two phases share the same tip headline",
            headlines.size,
            headlines.distinct().size
        )
    }

    @Test
    fun theAdviceIsTheEnginesOwnSentenceAndNotARewordedCopy() {
        // The card must render the engine's tip verbatim, not a paraphrase assembled
        // in the UI: the phase table and its advice are only safe from drifting apart
        // while there is exactly one copy of the sentence.
        LunarPhase.entries.forEach { phase ->
            val snapshot = LunarEngine.snapshot(instantFor(phase))
            val content = LunarCardCopy.contentOf(snapshot)
            assertEquals(snapshot.tip.adviceEs, content.adviceEs)
            assertEquals(snapshot.tip.headlineEs, content.tipHeadlineEs)
        }
    }

    @Test
    fun everyPhaseHasAGlyph() {
        LunarPhase.entries.forEach { phase ->
            val glyph = LunarCardCopy.glyphFor(phase.iconKey)
            assertNotEquals(
                "${phase.name} (${phase.iconKey}) resolved to the fallback glyph",
                LunarCardCopy.FALLBACK_GLYPH,
                glyph
            )
            assertTrue("${phase.name} resolved to a blank glyph", glyph.isNotBlank())
        }
    }

    @Test
    fun anUnknownIconKeyDegradesInsteadOfFailing() {
        assertEquals(LunarCardCopy.FALLBACK_GLYPH, LunarCardCopy.glyphFor("lunar_does_not_exist"))
        assertEquals(LunarCardCopy.FALLBACK_GLYPH, LunarCardCopy.glyphFor(""))
    }

    @Test
    fun theTrendIsNamedInSpanish() {
        assertEquals("creciente", LunarCardCopy.trendLabelEs(LunarTrend.WAXING))
        assertEquals("menguante", LunarCardCopy.trendLabelEs(LunarTrend.WANING))
    }

    @Test
    fun illuminationRendersAsAWholePercent() {
        assertEquals("0 %", LunarCardCopy.illuminationEs(0f))
        assertEquals("100 %", LunarCardCopy.illuminationEs(1f))
        assertEquals("50 %", LunarCardCopy.illuminationEs(0.5f))
        // Rounded, not truncated: 0.623 must not advertise "62 %".
        assertEquals("62 %", LunarCardCopy.illuminationEs(0.623f))
        assertEquals("63 %", LunarCardCopy.illuminationEs(0.626f))
    }

    @Test
    fun illuminationIsClampedSoAnOutOfRangeValueCannotBePrinted() {
        assertEquals("100 %", LunarCardCopy.illuminationEs(1.4f))
        assertEquals("0 %", LunarCardCopy.illuminationEs(-0.2f))
    }

    @Test
    fun theToggleNamesItsActionAndSaysItDiffersWhenExpanded() {
        val snapshot = LunarEngine.snapshot(instantFor(LunarPhase.FULL_MOON))

        val collapsed = LunarCardCopy.contentOf(snapshot, expanded = false)
        val expanded = LunarCardCopy.contentOf(snapshot, expanded = true)

        // "Operable, not hover-only" is only meaningful if the label tells a
        // screen-reader user what the press will do.
        assertTrue(
            "the collapsed label must name the action and what it reveals",
            collapsed.toggleDescriptionEs.contains(LunarCardCopy.EXPAND_PREFIX_ES) &&
                collapsed.toggleDescriptionEs.contains(LunarPhase.FULL_MOON.labelEs)
        )
        assertTrue(
            "the expanded label must say it hides the panel",
            expanded.toggleDescriptionEs.contains(LunarCardCopy.COLLAPSE_PREFIX_ES)
        )
        assertNotEquals(
            "a control whose label is identical in both states cannot be announced " +
                "as a disclosure",
            collapsed.toggleDescriptionEs,
            expanded.toggleDescriptionEs
        )
    }

    @Test
    fun theVisibleCopyDoesNotChangeWithTheExpandedState() {
        // Only the description may differ. If the phase label or the advice tracked the
        // toggle, the visible text would disagree with what a screen reader announced.
        val snapshot = LunarEngine.snapshot(instantFor(LunarPhase.WAXING_GIBBOUS))
        val collapsed = LunarCardCopy.contentOf(snapshot, expanded = false)
        val expanded = LunarCardCopy.contentOf(snapshot, expanded = true)

        assertEquals(collapsed.copy(toggleDescriptionEs = ""), expanded.copy(toggleDescriptionEs = ""))
    }

    @Test
    fun theDisclaimerIsCarried() {
        val content = LunarCardCopy.contentOf(LunarEngine.snapshot(0L))
        assertEquals(LunarEngine.DISCLAIMER_ES, content.disclaimerEs)
        assertTrue(
            "the disclaimer must say it is not a measurement",
            content.disclaimerEs.contains("No es una medición")
        )
    }

    @Test
    fun theCardStaysConsistentWithTheSnapshotAtRealInstants() {
        // Not the phase boundaries — those are the engine's own test. This walks a full
        // cycle so the wiring is checked where the two halves meet, which is where an
        // off-by-one in the copy would hide.
        var t = 1_700_000_000_000L
        repeat(24) {
            val snapshot = LunarEngine.snapshot(t)
            val content = LunarCardCopy.contentOf(snapshot)
            assertEquals(snapshot.phase.labelEs, content.phaseLabelEs)
            assertEquals(snapshot.phase.iconKey, content.iconKey)
            assertEquals(
                LunarCardCopy.trendLabelEs(snapshot.trend),
                content.trendLabelEs
            )
            assertEquals(
                LunarCardCopy.illuminationEs(snapshot.illumination),
                content.illuminationEs
            )
            t += LunarEngine.SYNODIC_MONTH_MILLIS / 12
        }
    }
}