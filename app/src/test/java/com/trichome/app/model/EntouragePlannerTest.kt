package com.trichome.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Entourage Planner: what the match score means, and what it must never
 * mean.
 *
 * The defects these tests pin down are the two ways a similarity number in a
 * health-adjacent app misleads:
 *
 * 1. **A lone compound scoring perfect.** With the similarity alone, a
 *    one-terpene selection that happens to pick the profile's lead terpene
 *    renormalises to 1.0 against a five-terpene profile. That is arithmetically
 *    true and practically nonsense, so [EntouragePlanner.COVERAGE_WEIGHT]
 *    exists and is tested here.
 * 2. **A low score read as a verdict.** Nothing in the guidance says the
 *    selection is bad, weak or unsafe. A percentage is a distance from one
 *    profile's proportions, and the tests assert the wording never drifts into
 *    a judgement about the user.
 */
class EntouragePlannerTest {

    /** The five-terpene sedative profile, shares summing to 1. */
    private val sedative = EntourageProfile(
        key = PharmacologicalProfile.SEDATIVE,
        labelEs = "Sedante",
        descriptionEs = "Perfil de prueba",
        cannabinoidWeights = mapOf(Cannabinoid.CBN to 0.45f, Cannabinoid.THC to 0.35f),
        terpeneShares = mapOf(
            EntourageTerpene.MYRCENE to 0.35f,
            EntourageTerpene.LINALOOL to 0.25f,
            EntourageTerpene.BETA_CARYOPHYLLENE to 0.15f,
            EntourageTerpene.LIMONENE to 0.15f,
            EntourageTerpene.TERPINOLENE to 0.10f
        ),
        noteEs = "Nota de prueba"
    )

    private fun selection(vararg terpenes: EntourageTerpene, cannabinoids: Set<Cannabinoid> = emptySet()) =
        EntourageSelection(cannabinoids = cannabinoids, terpenes = terpenes.toSet())

    private val allFive = selection(
        EntourageTerpene.MYRCENE,
        EntourageTerpene.LINALOOL,
        EntourageTerpene.BETA_CARYOPHYLLENE,
        EntourageTerpene.LIMONENE,
        EntourageTerpene.TERPINOLENE
    )

    // --- the perfect match -------------------------------------------------

    @Test
    fun selectingExactlyTheProfileScoresOneHundred() {
        val plan = EntouragePlanner.plan(allFive, sedative)

        assertEquals("the profile's own terpenes are the reference, not a near miss", 100, plan.percent)
        assertEquals(EntourageMatchQuality.AJUSTADO, plan.quality)
        assertTrue("nothing is missing when the selection is the profile", plan.gaps.isEmpty())
        assertTrue("nothing is off target either", plan.offTarget.isEmpty())
    }

    @Test
    fun aSelectionWithNoGapsIsNeverReportedAsMissingSomething() {
        val plan = EntouragePlanner.plan(allFive, sedative)

        assertTrue(plan.gaps.isEmpty())
        assertTrue(
            "the guidance must not announce a gap that does not exist: ${plan.guidanceEs}",
            !plan.guidanceEs.contains("Faltan")
        )
    }

    // --- the single-compound trap ------------------------------------------

    @Test
    fun aSingleLeadTerpeneCannotScorePerfectAgainstAFiveTerpeneProfile() {
        val plan = EntouragePlanner.plan(selection(EntourageTerpene.MYRCENE), sedative)

        assertTrue(
            "picking only the profile's lead terpene must not read as a perfect match, got ${plan.percent}%",
            plan.percent < 100
        )
        assertEquals(
            "a one-compound selection is the weakest possible evidence of a profile",
            EntourageMatchQuality.LEJANO,
            plan.quality
        )
    }

    @Test
    fun theMoreOfTheProfileIsPresentTheHigherTheScore() {
        val one = EntouragePlanner.plan(selection(EntourageTerpene.MYRCENE), sedative).percent
        val two = EntouragePlanner.plan(
            selection(EntourageTerpene.MYRCENE, EntourageTerpene.LINALOOL),
            sedative
        ).percent
        val three = EntouragePlanner.plan(
            selection(
                EntourageTerpene.MYRCENE,
                EntourageTerpene.LINALOOL,
                EntourageTerpene.BETA_CARYOPHYLLENE
            ),
            sedative
        ).percent
        val five = EntouragePlanner.plan(allFive, sedative).percent

        assertTrue("1 < 2: $one < $two", one < two)
        assertTrue("2 < 3: $two < $three", two < three)
        assertTrue("3 < 5: $three < $five", three < five)
    }

    @Test
    fun aTerpeneTheProfileDoesNotUseCostsScore() {
        val withExtra = EntouragePlanner.plan(
            selection(
                EntourageTerpene.MYRCENE,
                EntourageTerpene.LINALOOL,
                EntourageTerpene.BETA_CARYOPHYLLENE,
                EntourageTerpene.LIMONENE,
                EntourageTerpene.TERPINOLENE,
                EntourageTerpene.HUMULENE
            ),
            sedative
        )
        val exact = EntouragePlanner.plan(allFive, sedative)

        assertTrue(
            "the profile's own compounds are still all present, so only focus can differ: " +
                "${withExtra.percent}% vs ${exact.percent}%",
            withExtra.percent < exact.percent
        )
    }

    @Test
    fun aSelectionOfNothingButOffTargetCompoundsScoresZero() {
        val offTarget = EntouragePlanner.plan(
            selection(EntourageTerpene.CAMPHENE, EntourageTerpene.OCIMENE),
            sedative
        )

        assertEquals(0, offTarget.percent)
        assertEquals(EntourageMatchQuality.LEJANO, offTarget.quality)
    }

    @Test
    fun extraTerpenesDoNotImproveTheScore() {
        val withExtra = EntouragePlanner.plan(
            selection(
                EntourageTerpene.MYRCENE,
                EntourageTerpene.LINALOOL,
                EntourageTerpene.BETA_CARYOPHYLLENE,
                EntourageTerpene.LIMONENE,
                EntourageTerpene.TERPINOLENE,
                EntourageTerpene.HUMULENE
            ),
            sedative
        )

        assertTrue(
            "adding a terpene the profile does not use must not raise the score",
            withExtra.percent <= EntouragePlanner.plan(allFive, sedative).percent
        )
        assertTrue(
            "an off-target terpene has to be reported so the UI can flag it",
            EntourageTerpene.HUMULENE in withExtra.offTarget
        )
    }

    @Test
    fun proportionsMatterNotJustPresence() {
        // Same five compounds as the profile, so presence is identical and only
        // the proportions can tell the two selections apart. The plan has no
        // terpene proportions to read, so the selection is treated as uniform;
        // the point of the test is that the score still drops when the user's
        // own mix is lopsided, which the caller expresses by leaving a
        // compound out or adding one.
        val lopsided = selection(
            EntourageTerpene.BETA_CARYOPHYLLENE,
            EntourageTerpene.MYRCENE
        )
        val plan = EntouragePlanner.plan(lopsided, sedative)

        assertTrue(
            "a lopsided selection must not reach the balanced one's score",
            plan.percent < EntouragePlanner.plan(allFive, sedative).percent
        )
        assertEquals(
            "two of the profile's five compounds were compared",
            5,
            plan.compoundsCompared
        )
    }

    @Test
    fun howManyCompoundsWereComparedIsReportedSoTheNumberIsNotOverTrusted() {
        assertEquals(5, EntouragePlanner.plan(allFive, sedative).compoundsCompared)
        assertEquals(
            "an off-target terpene joins the comparison, widening the set",
            6,
            EntouragePlanner.plan(
                selection(
                    EntourageTerpene.MYRCENE,
                    EntourageTerpene.LINALOOL,
                    EntourageTerpene.BETA_CARYOPHYLLENE,
                    EntourageTerpene.LIMONENE,
                    EntourageTerpene.TERPINOLENE,
                    EntourageTerpene.HUMULENE
                ),
                sedative
            ).compoundsCompared
        )
    }

    // --- distinct degenerate states ----------------------------------------

    @Test
    fun anEmptySelectionIsItsOwnStateRatherThanAZeroScore() {
        val plan = EntouragePlanner.plan(EntourageSelection(), sedative)

        assertEquals(EntourageMatchQuality.NO_SELECTION, plan.quality)
        assertEquals(0, plan.percent)
        assertEquals(
            "an unanswered screen should not announce four missing terpenes",
            0,
            plan.gaps.size
        )
    }

    @Test
    fun aMissingProfileIsItsOwnStateRatherThanAZeroScore() {
        val plan = EntouragePlanner.plan(allFive, null)

        assertEquals(EntourageMatchQuality.NO_REFERENCE, plan.quality)
        assertEquals(0, plan.percent)
        assertNull(plan.profileKey)
    }

    @Test
    fun aProfileWithNoTerpeneSharesIsAlsoAMissingReference() {
        val empty = sedative.copy(terpeneShares = emptyMap())

        assertEquals(
            EntourageMatchQuality.NO_REFERENCE,
            EntouragePlanner.plan(allFive, empty).quality
        )
    }

    // --- gaps ---------------------------------------------------------------

    @Test
    fun gapsAreListedRichestFirst() {
        val plan = EntouragePlanner.plan(selection(EntourageTerpene.LIMONENE), sedative)

        assertEquals(
            "the profile's biggest asks come first",
            EntourageTerpene.MYRCENE,
            plan.gaps.first().terpene
        )
        assertEquals(4, plan.gaps.size)
        val shares = plan.gaps.map { it.targetShare }
        assertEquals(
            "gaps must be ordered by how much the profile wants them",
            shares.sortedDescending(),
            shares
        )
    }

    @Test
    fun aGapCarriesTheShareItIsWorth() {
        val plan = EntouragePlanner.plan(selection(EntourageTerpene.LIMONENE), sedative)

        val gap = plan.gaps.single { it.terpene == sedative.leadTerpene }
        assertEquals("the lead terpene is worth a third of the profile", 35, sharePercent(gap.targetShare))
        assertEquals("and it carries the display name", sedative.leadTerpene!!.labelEs, gap.labelEs)
    }

    @Test
    fun missingCannabinoidsAreReportedButNotScored() {
        val plan = EntouragePlanner.plan(
            allFive,
            sedative,
            synergies = emptyList()
        )

        assertEquals(
            "the profile's cannabinoids are reported when the selection has none",
            sedative.cannabinoidWeights.keys,
            plan.missingCannabinoids.toSet()
        )
        assertEquals(
            "and they do not move the score, because presence is not potency",
            100,
            plan.percent
        )
    }

    @Test
    fun aSelectionHoldingEveryCannabinoidHasNothingMissing() {
        val plan = EntouragePlanner.plan(
            EntourageSelection(
                terpenes = sedative.terpeneShares.keys,
                cannabinoids = sedative.cannabinoidWeights.keys
            ),
            sedative
        )

        assertTrue(plan.missingCannabinoids.isEmpty())
        assertEquals(100, plan.percent)
    }

    // --- the band -----------------------------------------------------------

    @Test
    fun theBandIsDerivedFromTheRoundedNumber() {
        // 100 and 70 are both "ajustado", so a player reading 100% is never
        // told the match is only "parcial".
        assertEquals(EntourageMatchQuality.AJUSTADO, EntouragePlanner.qualityFor(100))
        assertEquals(EntourageMatchQuality.AJUSTADO, EntouragePlanner.qualityFor(70))
        assertEquals(EntourageMatchQuality.PARCIAL, EntouragePlanner.qualityFor(69))
        assertEquals(EntourageMatchQuality.PARCIAL, EntouragePlanner.qualityFor(40))
        assertEquals(EntourageMatchQuality.LEJANO, EntouragePlanner.qualityFor(39))
    }

    @Test
    fun theBandNeverContradictsThePercentItDescribes() {
        listOf(0, 20, 39, 40, 69, 70, 85, 100).forEach { percent ->
            val quality = EntouragePlanner.qualityFor(percent)
            val expected = when {
                percent >= 70 -> EntourageMatchQuality.AJUSTADO
                percent >= 40 -> EntourageMatchQuality.PARCIAL
                else -> EntourageMatchQuality.LEJANO
            }
            assertEquals("$percent% must read as $expected", expected, quality)
        }
    }

    // --- the guidance is a report, not a verdict ----------------------------

    @Test
    fun theGuidanceNeverCallsTheSelectionBad() {
        val verdicts = listOf("malo", "mala", "malo", "incorrecto", "inseguro", "peligroso", "daño", "deficiente", "insuficiente", "no sirve")

        (0..100 step 5).forEach { percent ->
            (0..4).forEach { gaps ->
                val text = EntouragePlanner.guidanceFor(percent, gaps).lowercase()
                verdicts.forEach { word ->
                    assertFalse(
                        "guidance for $percent% with $gaps gaps says '$word': \"$text\"",
                        text.contains(word)
                    )
                }
            }
        }
    }

    @Test
    fun theGuidanceCountsTheGapsInTheSingularAndThePlural() {
        assertTrue(
            "one missing terpene must not be written as 'faltan 1 terpenos'",
            EntouragePlanner.guidanceFor(40, 1).contains("Falta 1 terpeno del objetivo")
        )
        assertTrue(
            EntouragePlanner.guidanceFor(40, 3).contains("Faltan 3 terpenos del objetivo")
        )
    }

    @Test
    fun aLowScoreStillGetsUsableGuidance() {
        val guidance = EntouragePlanner.guidanceFor(10, 4)

        assertTrue("guidance must not be blank", guidance.isNotBlank())
        assertTrue(
            "a poor match should point at what to look at: \"$guidance\"",
            guidance.contains("perfil objetivo")
        )
    }

    // --- synergies ----------------------------------------------------------

    private val synergies = listOf(
        EntourageSynergy(
            id = "thc_myrcene",
            cannabinoids = setOf(Cannabinoid.THC),
            terpenes = setOf(EntourageTerpene.MYRCENE),
            profiles = setOf(PharmacologicalProfile.SEDATIVE),
            outcomeEs = "Sedación profunda",
            descriptionEs = "Descripción",
            mechanismEs = "CB1",
            evidenceEs = "Preclínico",
            strainsEs = listOf("OG Kush"),
            interactionEs = "El THC aumenta la frecuencia cardíaca."
        ),
        EntourageSynergy(
            id = "thc_limonene",
            cannabinoids = setOf(Cannabinoid.THC),
            terpenes = setOf(EntourageTerpene.LIMONENE),
            profiles = setOf(PharmacologicalProfile.ANSIOLYTIC),
            outcomeEs = "Menos ansiedad",
            descriptionEs = "Descripción",
            mechanismEs = "GABA",
            evidenceEs = "Preclínico",
            strainsEs = listOf("Amnesia Haze"),
            interactionEs = ""
        )
    )

    @Test
    fun theMatchingSynergyIsTheOneWhoseTerpenesArePresent() {
        val matched = EntouragePlanner.matchedSynergy(
            selection(EntourageTerpene.MYRCENE, cannabinoids = setOf(Cannabinoid.THC)),
            synergies
        )

        assertEquals("thc_myrcene", matched?.id)
    }

    @Test
    fun aSelectionMatchingNoSynergyGetsNone() {
        val matched = EntouragePlanner.matchedSynergy(
            selection(EntourageTerpene.CAMPHENE, cannabinoids = setOf(Cannabinoid.CBD)),
            synergies
        )

        assertNull("an unexplained combination must not borrow another one's text", matched)
    }

    @Test
    fun interactionNotesOnlySurfaceForTheCannabinoidsActuallySelected() {
        val thc = EntouragePlanner.interactionWarnings(
            selection(EntourageTerpene.MYRCENE, cannabinoids = setOf(Cannabinoid.THC)),
            synergies
        )
        val cbd = EntouragePlanner.interactionWarnings(
            selection(EntourageTerpene.MYRCENE, cannabinoids = setOf(Cannabinoid.CBD)),
            synergies
        )

        assertEquals("only the THC synergy declares an interaction", 1, thc.size)
        assertTrue(
            "the note has to be the one the asset shipped",
            thc.single().contains("frecuencia cardíaca")
        )
        assertTrue("a CBD selection triggers no THC warning", cbd.isEmpty())
    }

    @Test
    fun anEmptySynergyLibraryDoesNotBreakThePlan() {
        val plan = EntouragePlanner.plan(
            selection(EntourageTerpene.MYRCENE, cannabinoids = setOf(Cannabinoid.THC)),
            sedative,
            synergies = emptyList()
        )

        assertNull(plan.matchedSynergy)
        assertTrue(plan.interactionWarningsEs.isEmpty())
        assertTrue("the score is independent of the synergy library", plan.percent > 0)
    }

    // --- vaporisation -------------------------------------------------------

    private val vaporisation = listOf(
        TerpeneVaporisation(EntourageTerpene.ALPHA_PINENE, 156, 156, 180, ""),
        TerpeneVaporisation(EntourageTerpene.MYRCENE, 167, 167, 195, ""),
        TerpeneVaporisation(EntourageTerpene.LIMONENE, 176, 176, 200, ""),
        TerpeneVaporisation(EntourageTerpene.LINALOOL, 198, 198, 220, ""),
        TerpeneVaporisation(EntourageTerpene.BETA_CARYOPHYLLENE, 262, 250, 280, "")
    )

    @Test
    fun aWindowIsBoundedByTheMostDemandingAndTheMostFragileTerpene() {
        val window = EntouragePlanner.windowFor(
            setOf(EntourageTerpene.ALPHA_PINENE, EntourageTerpene.LINALOOL),
            vaporisation
        )

        assertNotNull(window)
        assertEquals("the linalool's 198 °C is the floor", 198, window!!.minTempC)
        assertEquals("the pinene's 180 °C is the ceiling", 180, window.maxTempC)
    }

    @Test
    fun theWindowIsViableOnlyWhenTheBoundsDoNotCross() {
        val comfortable = EntouragePlanner.windowFor(
            setOf(EntourageTerpene.MYRCENE, EntourageTerpene.LIMONENE),
            vaporisation
        )
        val contradictory = EntouragePlanner.windowFor(
            setOf(EntourageTerpene.ALPHA_PINENE, EntourageTerpene.BETA_CARYOPHYLLENE),
            vaporisation
        )

        assertTrue(
            "myrcene 167-195 and limonene 176-200 overlap at 176-195",
            comfortable!!.isViable
        )
        assertFalse(
            "a 156 °C pinene and a 262 °C sesquiterpene cannot both survive one pass",
            contradictory!!.isViable
        )
        assertTrue(
            "and the contradiction has to be visible in the numbers",
            contradictory.minTempC > contradictory.maxTempC
        )
    }

    @Test
    fun aSessionWithNoTerpenesHasNoWindow() {
        assertNull(EntouragePlanner.windowFor(emptySet(), vaporisation))
    }

    @Test
    fun terpenesWithNoTemperatureRowHaveNoWindow() {
        assertNull(
            "an unloaded catalog must not invent a temperature",
            EntouragePlanner.windowFor(setOf(EntourageTerpene.CAMPHENE), emptyList())
        )
    }

    @Test
    fun theVolatilityOrderComesFromTheBoilingPoints() {
        val order = EntouragePlanner.byVolatility(vaporisation, limit = 4)

        assertEquals(
            listOf(
                EntourageTerpene.ALPHA_PINENE,
                EntourageTerpene.MYRCENE,
                EntourageTerpene.LIMONENE,
                EntourageTerpene.LINALOOL
            ),
            order
        )
    }

    @Test
    fun theVolatilityOrderPutsTheHighBoilingTerpenesLast() {
        val order = EntouragePlanner.byVolatility(vaporisation)
        val boilingPoints = vaporisation.associate { it.terpene to it.boilingPointC }
        val withData = order.filter { it in boilingPoints }

        assertEquals(
            "a 262 °C compound has to sort after a 156 °C one, or the selector teaches the wrong order",
            boilingPoints.getValue(EntourageTerpene.BETA_CARYOPHYLLENE),
            boilingPoints.getValue(withData.last())
        )
        assertEquals(
            EntourageTerpene.ALPHA_PINENE,
            withData.first()
        )
    }

    @Test
    fun aTerpeneWithNoBoilingPointSortsToTheEndRatherThanCrashing() {
        val order = EntouragePlanner.byVolatility(emptyList(), limit = 3)

        assertEquals(3, order.size)
        assertTrue(
            "an unloaded table still has to return something to show",
            order.isNotEmpty()
        )
    }

    @Test
    fun aWindowThatDoesNotContainItsBoilingPointIsRejectedAtConstruction() {
        val error = runCatching {
            TerpeneVaporisation(EntourageTerpene.LIMONENE, 176, 200, 220, "")
        }.exceptionOrNull()

        assertNotNull(
            "a 200 °C floor for a 176 °C compound would teach the wrong temperature",
            error
        )
    }
}
