package com.trichome.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Entourage Lab: a clinical case answered by a selection.
 *
 * The case the plan describes is insomnia with high THC tolerance but
 * tachycardia at high doses. That is the whole design constraint: the patient
 * can take a lot, but not a lot of *THC*, so the efficacy has to come from
 * somewhere else. These tests assert that the scoring produces that answer
 * rather than the obvious one, and that the verdicts stay ordered.
 */
class EntourageLabTest {

    /** The shipped sedative profile: myrcene, linalool, caryophyllene, limonene, terpinolene. */
    private val sedativeProfile = EntourageProfile(
        key = PharmacologicalProfile.SEDATIVE,
        labelEs = "Sedante",
        descriptionEs = "Perfil de prueba",
        cannabinoidWeights = mapOf(Cannabinoid.CBN to 0.45f, Cannabinoid.THC to 0.35f, Cannabinoid.CBD to 0.20f),
        terpeneShares = mapOf(
            EntourageTerpene.MYRCENE to 0.35f,
            EntourageTerpene.LINALOOL to 0.25f,
            EntourageTerpene.BETA_CARYOPHYLLENE to 0.15f,
            EntourageTerpene.LIMONENE to 0.15f,
            EntourageTerpene.TERPINOLENE to 0.10f
        ),
        noteEs = "Perfil de prueba, no una indicación médica.",
        evidence = ProfileEvidence.MIXTO
    )

    private val profiles = listOf(sedativeProfile)

    /** The plan's case: insomnia, high THC tolerance, tachycardia ceiling. */
    private val insomnia = EntourageCase(
        id = "insomnio_alta_tolerancia",
        titleEs = "Insomnio con tolerancia alta a THC",
        briefEs = "Taquicardia en dosis altas de THC.",
        goal = PharmacologicalProfile.SEDATIVE,
        maxCannabinoidShare = mapOf(Cannabinoid.THC to 0.20f),
        ceilings = mapOf(
            LabAxis.ANXIETY to 0.30f,
            LabAxis.TACHYCARDIA to 0.25f,
            LabAxis.SEDATION to 1.00f,
            LabAxis.COGNITIVE to 0.80f
        ),
        explanationEs = "Menos THC, más terpena."
    )

    private val fullTerpenes = sedativeProfile.terpeneShares.keys

    private fun solve(
        case: EntourageCase = insomnia,
        cannabinoids: Set<Cannabinoid> = emptySet(),
        terpenes: Set<EntourageTerpene> = emptySet(),
        shares: Map<Cannabinoid, Float> = emptyMap()
    ) = EntourageLab.solve(
        case,
        EntourageSelection(cannabinoids = cannabinoids, terpenes = terpenes, cannabinoidShares = shares),
        profiles
    )

    // --- the case the plan describes ---------------------------------------

    @Test
    fun aMaximalThcProfileIsTheWrongAnswerToTheInsomniaCase() {
        val result = solve(
            cannabinoids = setOf(Cannabinoid.THC),
            terpenes = fullTerpenes,
            shares = mapOf(Cannabinoid.THC to 1f)
        )

        assertTrue(
            "the most THC possible is the answer the case has to rule out, got ${result.verdict}",
            result.verdict != LabVerdict.OPTIMO
        )
        assertTrue(
            "and the reason has to name the ceiling that was crossed",
            result.notesEs.any { it.contains("taquicardia") }
        )
    }

    @Test
    fun aTerpeneLedSedativeProfileInsideTheCeilingsIsTheOptimum() {
        val result = solve(
            cannabinoids = setOf(Cannabinoid.CBD, Cannabinoid.CBN),
            terpenes = fullTerpenes
        )

        assertEquals(LabVerdict.OPTIMO, result.verdict)
        assertTrue(
            "no ceiling may be crossed for the case to be solved: ${result.readings}",
            result.readings.none { it.crosses }
        )
        assertTrue(
            "and the sedation axis has to be the loaded one",
            result.readings.single { it.axis == LabAxis.SEDATION }.load >
                result.readings.single { it.axis == LabAxis.ANXIETY }.load
        )
    }

    @Test
    fun theSameProfileWithOnlyThcIsScoredOnItsCeilingsNotItsEfficacy() {
        val terps = fullTerpenes
        val withCbd = solve(cannabinoids = setOf(Cannabinoid.CBD), terpenes = terps)
        val withThc = solve(
            cannabinoids = setOf(Cannabinoid.THC),
            terpenes = terps,
            shares = mapOf(Cannabinoid.THC to 1f)
        )

        assertEquals(
            "the terpene set is identical, so efficacy is identical",
            withCbd.efficacy,
            withThc.efficacy
        )
        assertEquals(LabVerdict.OPTIMO, withCbd.verdict)
        assertEquals(
            "and yet the THC one has to be ruled out by the case's ceilings",
            LabVerdict.RIESGO,
            withThc.verdict
        )
    }

    @Test
    fun theShareCeilingIsEnforcedNotJustTheAxisCeilings() {
        val withinCeiling = solve(
            cannabinoids = setOf(Cannabinoid.THC, Cannabinoid.CBD),
            terpenes = fullTerpenes,
            shares = mapOf(Cannabinoid.THC to 0.20f, Cannabinoid.CBD to 0.80f)
        )
        val overCeiling = solve(
            cannabinoids = setOf(Cannabinoid.THC, Cannabinoid.CBD),
            terpenes = fullTerpenes,
            shares = mapOf(Cannabinoid.THC to 0.60f, Cannabinoid.CBD to 0.40f)
        )

        assertTrue(
            "a 20% THC share is inside the case's limit",
            withinCeiling.verdict != LabVerdict.RIESGO || !withinCeiling.notesEs.any { it.contains("aporte de THC") }
        )
        assertEquals(LabVerdict.RIESGO, overCeiling.verdict)
        assertTrue(
            "and the note has to name the cannabinoid and both numbers",
            overCeiling.notesEs.any { note ->
                note.contains("THC") && note.contains("60%") && note.contains("20%")
            }
        )
    }

    @Test
    fun aShareExactlyAtTheCeilingIsInsideIt() {
        val atCeiling = solve(
            cannabinoids = setOf(Cannabinoid.THC, Cannabinoid.CBD),
            terpenes = fullTerpenes,
            shares = mapOf(Cannabinoid.THC to 0.20f, Cannabinoid.CBD to 0.80f)
        )

        assertTrue(
            "a ceiling is a maximum, not something to be under",
            !atCeiling.notesEs.any { it.contains("aporte de THC") }
        )
    }

    @Test
    fun aShareOfTheCannabinoidTheCaseLimitsIsStillCheckedWhenItIsAlone() {
        val result = solve(
            cannabinoids = setOf(Cannabinoid.THC),
            terpenes = fullTerpenes,
            shares = mapOf(Cannabinoid.THC to 1f)
        )

        assertTrue(result.notesEs.any { it.contains("aporte de THC") })
    }

    // --- forbidden compounds ------------------------------------------------

    @Test
    fun aForbiddenCannabinoidDisqualifiesTheSelectionWhateverTheTerpenesDo() {
        val focusCase = insomnia.copy(
            id = "bloque_trabajo",
            goal = PharmacologicalProfile.SEDATIVE,
            forbiddenCannabinoids = setOf(Cannabinoid.CBN)
        )
        val result = solve(
            case = focusCase,
            cannabinoids = setOf(Cannabinoid.CBN, Cannabinoid.CBD),
            terpenes = fullTerpenes
        )

        assertEquals(LabVerdict.INEFICAZ, result.verdict)
        assertEquals(0, result.efficacy)
        assertTrue(
            "the note names the compound that is out",
            result.notesEs.any { it.contains("CBN") }
        )
    }

    @Test
    fun aForbiddenCompoundIsNotRescuedByAPerfectTerpeneProfile() {
        val focusCase = insomnia.copy(forbiddenCannabinoids = setOf(Cannabinoid.CBN))
        val perfectTerpenes = fullTerpenes
        val allowed = solve(cannabinoids = setOf(Cannabinoid.CBD), terpenes = perfectTerpenes)
        val forbidden = solve(
            case = focusCase,
            cannabinoids = setOf(Cannabinoid.CBN),
            terpenes = perfectTerpenes
        )

        assertEquals(100, allowed.efficacy)
        assertEquals("the terpene set was perfect and it does not matter", 0, forbidden.efficacy)
    }

    // --- insufficient efficacy ---------------------------------------------

    @Test
    fun aProfileWithNoRelationToTheGoalIsIneffective() {
        val result = solve(
            cannabinoids = setOf(Cannabinoid.CBD),
            terpenes = setOf(EntourageTerpene.CAMPHENE, EntourageTerpene.OCIMENE)
        )

        assertEquals(LabVerdict.INEFICAZ, result.verdict)
    }

    @Test
    fun aPartialProfileIsViableRatherThanIneffective() {
        val result = solve(
            cannabinoids = setOf(Cannabinoid.CBD),
            terpenes = setOf(EntourageTerpene.MYRCENE, EntourageTerpene.LINALOOL)
        )

        assertTrue(
            "two of the profile's lead terpenes is not nothing, got ${result.verdict} at ${result.efficacy}%",
            result.verdict == LabVerdict.VIABLE || result.verdict == LabVerdict.OPTIMO
        )
    }

    @Test
    fun nothingSelectedDoesNothing() {
        val result = solve()

        assertEquals(LabVerdict.INEFICAZ, result.verdict)
        assertEquals(0, result.efficacy)
    }

    // --- the readings -------------------------------------------------------

    @Test
    fun everyAxisIsReportedSoTheUiCannotSilentlyDropOne() {
        val result = solve(cannabinoids = setOf(Cannabinoid.CBD), terpenes = fullTerpenes)

        assertEquals(
            "all four axes have to be on screen, not only the ones that failed",
            LabAxis.entries.toSet(),
            result.readings.map { it.axis }.toSet()
        )
    }

    @Test
    fun aCrossingIsVisibleAsAMarginNotJustABoolean() {
        val result = solve(
            cannabinoids = setOf(Cannabinoid.THC),
            terpenes = fullTerpenes,
            shares = mapOf(Cannabinoid.THC to 1f)
        )
        val tachycardia = result.readings.single { it.axis == LabAxis.TACHYCARDIA }

        assertTrue("this is the case's whole point, the ceiling has to be crossed", tachycardia.crosses)
        assertTrue(
            "and the load has to be above the ceiling, not just flagged: " +
                "${tachycardia.load} vs ${tachycardia.ceiling}",
            tachycardia.load > tachycardia.ceiling
        )
    }

    @Test
    fun addingAThcFreeTerpeneDoesNotReduceTheLoadOfAThcHeavyOne() {
        // Terpenes contribute absolutely, not as a share of the terpene set, so
        // adding one cannot make another look lighter.
        val alone = solve(cannabinoids = setOf(Cannabinoid.THC), terpenes = setOf(EntourageTerpene.MYRCENE))
        val withMore = solve(
            cannabinoids = setOf(Cannabinoid.THC),
            terpenes = setOf(EntourageTerpene.MYRCENE, EntourageTerpene.LIMONENE)
        )
        val sedationAlone = alone.readings.single { it.axis == LabAxis.SEDATION }.load
        val sedationMore = withMore.readings.single { it.axis == LabAxis.SEDATION }.load

        assertTrue(
            "adding a terpene can only add load: $sedationAlone -> $sedationMore",
            sedationMore > sedationAlone
        )
    }

    @Test
    fun thcLoadsEveryAxisHarderThanCbd() {
        LabAxis.entries.forEach { axis ->
            assertTrue(
                "THC must load $axis harder than CBD",
                LabWeights.cannabinoidLoad(Cannabinoid.THC, axis) >
                    LabWeights.cannabinoidLoad(Cannabinoid.CBD, axis)
            )
        }
    }

    @Test
    fun cbnIsTheHeaviestSedativeLoadAndThcTheHeaviestAnxietyLoad() {
        assertEquals(
            Cannabinoid.entries.maxByOrNull { LabWeights.cannabinoidLoad(it, LabAxis.SEDATION) },
            Cannabinoid.CBN
        )
        assertEquals(
            Cannabinoid.entries.maxByOrNull { LabWeights.cannabinoidLoad(it, LabAxis.ANXIETY) },
            Cannabinoid.THC
        )
    }

    @Test
    fun myrceneAndLinaloolAreTheHeaviestSedativeTerpenes() {
        val heaviest = EntourageTerpene.entries
            .maxByOrNull { LabWeights.terpeneLoad(it, LabAxis.SEDATION) }

        assertEquals(EntourageTerpene.MYRCENE, heaviest)
    }

    @Test
    fun everyCannabinoidHasADefinedLoadOnEveryAxis() {
        // The enum and the weight table are exhaustive `when`s, so a new
        // cannabinoid cannot fall through. This asserts the table is total
        // rather than sparse, which the compiler does not check.
        Cannabinoid.entries.forEach { cannabinoid ->
            LabAxis.entries.forEach { axis ->
                val load = LabWeights.cannabinoidLoad(cannabinoid, axis)
                assertTrue("$cannabinoid on $axis must be a fraction, was $load", load in 0f..1f)
            }
        }
    }

    @Test
    fun everyTerpeneHasADefinedLoadOnEveryAxis() {
        EntourageTerpene.entries.forEach { terpene ->
            LabAxis.entries.forEach { axis ->
                val load = LabWeights.terpeneLoad(terpene, axis)
                assertTrue("$terpene on $axis must be a fraction, was $load", load in 0f..1f)
            }
        }
    }

    // --- the case library ---------------------------------------------------

    @Test
    fun aCaseWithNoShippedProfileIsReportedAsUnscorableRatherThanScored() {
        val result = EntourageLab.solve(insomnia, EntourageSelection(), profiles = emptyList())

        assertEquals(LabVerdict.INEFICAZ, result.verdict)
        assertEquals(0, result.efficacy)
        assertTrue(
            "the reason has to be in the notes, not swallowed",
            result.notesEs.any { it.contains("perfil") }
        )
    }

    @Test
    fun aCaseWithNoCeilingForAnAxisIsNotTreatedAsZeroTolerance() {
        // An omitted axis means the case does not constrain it. Defaulting to a
        // zero ceiling would make a typo fail every selection invisibly, so the
        // omission is a no-limit and the reading is reported as 100%.
        val openCase = insomnia.copy(ceilings = insomnia.ceilings - LabAxis.COGNITIVE)
        val result = solve(
            case = openCase,
            cannabinoids = setOf(Cannabinoid.CBD),
            terpenes = fullTerpenes
        )

        val cognitive = result.readings.single { it.axis == LabAxis.COGNITIVE }
        assertEquals("an undeclared axis is unconstrained", 100, sharePercent(cognitive.ceiling))
        assertFalse("so nothing can cross it", cognitive.crosses)
    }

    @Test
    fun toleratedShareDefaultsToNoLimitRatherThanToZero() {
        assertEquals(
            1f,
            EntourageLab.toleratedShare(insomnia, Cannabinoid.CBD),
            1e-6f
        )
        assertEquals(
            0.20f,
            EntourageLab.toleratedShare(insomnia, Cannabinoid.THC),
            1e-6f
        )
    }

    @Test
    fun aCaseAdvertisesItsShareCeilingsAsPercentages() {
        val ceilings = insomnia.shareCeilings()

        assertEquals(1, ceilings.size)
        assertEquals(Cannabinoid.THC, ceilings.single().first)
        assertEquals(20, ceilings.single().second)
    }

    // --- determinism --------------------------------------------------------

    @Test
    fun theSameAnswerAlwaysScoresTheSame() {
        val answers = listOf(
            solve(cannabinoids = setOf(Cannabinoid.CBD), terpenes = fullTerpenes),
            solve(cannabinoids = setOf(Cannabinoid.CBD), terpenes = fullTerpenes),
            solve(cannabinoids = setOf(Cannabinoid.CBD), terpenes = fullTerpenes)
        )

        assertEquals(1, answers.map { it.verdict }.toSet().size)
        assertEquals(1, answers.map { it.efficacy }.toSet().size)
    }

    @Test
    fun theResultCarriesTheCaseIdSoTheUiCannotMixUpTwoCases() {
        val other = insomnia.copy(id = "otro_caso")
        val first = solve()
        val second = solve(case = other)

        assertEquals("insomnio_alta_tolerancia", first.caseId)
        assertEquals("otro_caso", second.caseId)
    }

    @Test
    fun everySelectionProducesSomethingToRead() {
        listOf(
            solve(),
            solve(cannabinoids = setOf(Cannabinoid.CBD), terpenes = fullTerpenes),
            solve(
                cannabinoids = setOf(Cannabinoid.THC),
                terpenes = fullTerpenes,
                shares = mapOf(Cannabinoid.THC to 1f)
            ),
            solve(cannabinoids = setOf(Cannabinoid.CBD), terpenes = setOf(EntourageTerpene.CAMPHENE))
        ).forEach { result ->
            assertTrue(
                "every result must carry notes to show, got $result",
                result.notesEs.isNotEmpty()
            )
            assertTrue(result.notesEs.none { it.isBlank() })
        }
    }

    @Test
    fun aCrossingNeverProducesAnEmptyNoteList() {
        val result = solve(
            cannabinoids = setOf(Cannabinoid.THC),
            terpenes = fullTerpenes,
            shares = mapOf(Cannabinoid.THC to 1f)
        )

        assertTrue(
            "a ruling-out has to say which ceiling killed it",
            result.notesEs.any { it.contains("techo") || it.contains("aporte") }
        )
    }

    @Test
    fun theEfficiencyFloorIsBelowTheOptimumThresholdSoTheVerdictsAreOrdered() {
        assertTrue(
            "otherwise VIABLE could never exist",
            EntourageLab.EFFICACY_FLOOR < EntourageLab.EFFICACY_EXCELLENT
        )
    }
}
