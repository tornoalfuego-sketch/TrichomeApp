package com.trichome.app.model

import com.trichome.app.data.repository.EntourageBible
import com.trichome.app.data.repository.EntourageContent
import com.trichome.app.data.repository.toContent
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F5 — the second scoring mode of the Lab, at the model level.
 *
 * ## What this file is really asserting
 *
 * That the two modes are two questions. Most of the tests here are a variation of
 * one property: **the same input cannot be scored into the same answer by two
 * different questions**. The strongest form is
 * [theTwoModesCannotProduceTheSameVerdictForTheSameInput]; the subtler one is
 * [theSameRouteAndMaterialReachesDifferentVerdictsUnderTheTwoGoals], which exists
 * because the *two goals inside the handling mode* were quietly interchangeable
 * until that test asked whether they could disagree.
 *
 * ## What is not tested here
 *
 * That the rows are persisted, that the screen renders the verdict, or that a
 * route is picked with a finger. Those need Room and a device. What is asserted
 * is the part that can go wrong silently: the verdict mapping, the evidence
 * framing, and the one thing a JVM test can genuinely do and a screen cannot —
 * check that the hand-declared compound classes still match the shipped
 * processing notes they were read from.
 */
class EntourageHandlingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun asset(): EntourageBible {
        val file = listOf(
            File("src/main/assets/data/entourage_data.json"),
            File("app/src/main/assets/data/entourage_data.json")
        ).first { it.isFile }
        return json.decodeFromString<EntourageBible>(String(file.readBytes(), Charsets.UTF_8))
    }

    private fun content(): EntourageContent = asset().toContent()

    private fun handlingSource(): String = listOf(
        File("src/main/java/com/trichome/app/model/EntourageHandling.kt"),
        File("app/src/main/java/com/trichome/app/model/EntourageHandling.kt")
    ).first { it.isFile }
        .readText(Charsets.UTF_8)
        .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
        .replace(Regex("""//[^\n]*"""), " ")

    private fun handlingCase() = content().cases.first { it.mode == LabMode.HANDLING }

    private fun pharmacologicalCase() = content().cases
        .first { it.mode == LabMode.PHARMACOLOGICAL }

    private fun selectionOf(vararg terpenes: EntourageTerpene) =
        EntourageSelection(cannabinoids = emptySet(), terpenes = terpenes.toSet())

    /* ── The two modes are two questions ─────────────────────────────────── */

    /**
     * The property the whole phase is built on.
     *
     * Three independent halves, because any one of them alone could pass while the
     * modes had been merged:
     *
     *  1. **The inputs are disjoint.** A pharmacological case carries cannabinoid
     *     shares and side-effect ceilings; a handling case carries neither. There
     *     is no cannabinoid to put into a handling answer.
     *  2. **The outputs are disjoint.** A handling result has no [LabAxis] reading
     *     and no efficacy, so no number a screen would label "how much" can be
     *     produced by this mode.
     *  3. **One verdict is unreachable.** [LabVerdict.RIESGO] means "it worked and
     *     broke a ceiling". There is no ceiling here, so it cannot be produced —
     *     swept over every route, every goal and every one- and two-compound
     *     material the enums can express.
     */
    @Test
    fun theTwoModesCannotProduceTheSameVerdictForTheSameInput() {
        val shipped = content()
        val handling = handlingCase()
        val pharmacological = pharmacologicalCase()

        // 1. inputs
        assertTrue(
            "the handling case carries no cannabinoid to be scored on",
            handling.maxCannabinoidShare.isEmpty() &&
                handling.forbiddenCannabinoids.isEmpty() &&
                handling.ceilings.isEmpty() &&
                handling.goal == null
        )
        assertTrue(
            "and the pharmacological case carries a cannabinoid profile to be scored on",
            pharmacological.goal != null && pharmacological.maxCannabinoidShare.isNotEmpty()
        )

        // 2. outputs
        val selection = selectionOf(*handling.handlingCompounds.toTypedArray())
        val handlingResult = EntourageLab.solve(
            handling,
            selection,
            shipped.profiles,
            ProcessingMethod.LIVE_ROSIN
        )
        val pharmacologicalResult = EntourageLab.solve(
            pharmacological,
            selection,
            shipped.profiles
        )

        assertEquals(LabMode.HANDLING, handlingResult.mode)
        assertEquals(LabMode.PHARMACOLOGICAL, pharmacologicalResult.mode)
        assertTrue(
            "a handling result has no side-effect axis: there is no patient",
            handlingResult.readings.isEmpty()
        )
        assertTrue(
            "and no efficacy: there is no cannabinoid profile to be a distance from",
            handlingResult.efficacy == 0
        )
        assertTrue(
            "while the pharmacological result still has both",
            pharmacologicalResult.readings.isNotEmpty() && pharmacologicalResult.efficacy > 0
        )

        // 3. RIESGO is unreachable in handling mode
        var reached: LabVerdict? = null
        HandlingGoal.entries.forEach { goal ->
            ProcessingMethod.entries.forEach { route ->
                EntourageTerpene.entries.forEach { first ->
                    EntourageTerpene.entries.forEach { second ->
                        val result = EntourageHandling.solve(
                            handling.copy(
                                handlingGoal = goal,
                                handlingForbiddenRoutes = emptySet()
                            ),
                            route,
                            selectionOf(first, second)
                        )
                        if (result.verdict == LabVerdict.RIESGO) reached = result.verdict
                    }
                }
            }
        }
        assertNull(
            "RISEGO must be unreachable in handling mode: it means a ceiling was " +
                "crossed and there is no ceiling",
            reached
        )
    }

    /**
     * The two goals inside the handling mode have to be two questions too.
     *
     * This test found a real defect. The first implementation scored coverage over
     * the whole selection and let the goal add a note; a solvent route over a
     * mixed material then came out [LabVerdict.VIABLE] under *both* goals, so
     * [HandlingGoal] was a label rather than a question. The goal now decides
     * which compounds count, which is what makes the two answers differ.
     */
    @Test
    fun theSameRouteAndMaterialReachesDifferentVerdictsUnderTheTwoGoals() {
        val shipped = content()
        val handling = handlingCase()
        // A material with one volatile compound and one retained one: the exact
        // split F4's comparison describes.
        val mixed = selectionOf(EntourageTerpene.LIMONENE, EntourageTerpene.BETA_CARYOPHYLLENE)

        val liveUnderBothGoals = HandlingGoal.entries.map { goal ->
            EntourageLab.solve(
                handling.copy(handlingGoal = goal),
                mixed,
                shipped.profiles,
                ProcessingMethod.LIVE_ROSIN
            )
        }
        assertEquals(
            "live rosin keeps both classes, so it serves either goal",
            listOf(LabVerdict.OPTIMO, LabVerdict.OPTIMO),
            liveUnderBothGoals.map { it.verdict }
        )

        val solvent = HandlingGoal.entries.map { goal ->
            EntourageLab.solve(
                handling.copy(handlingGoal = goal),
                mixed,
                shipped.profiles,
                ProcessingMethod.SOLVENT_EXTRACTION
            )
        }
        assertEquals(
            "the solvent route keeps the heavy compound and loses the volatile " +
                "one: under the volatile goal that is an outright failure, and " +
                "under the whole-profile goal it is a partial success",
            listOf(LabVerdict.INEFICAZ, LabVerdict.VIABLE),
            solvent.map { it.verdict }
        )
        assertNotEquals(
            "so the two goals are not the same question asked twice",
            solvent[0].verdict,
            solvent[1].verdict
        )
    }

    @Test
    fun aGoalTheMaterialHasNoCompoundForIsUnscorableRatherThanSilentlyMet() {
        val shipped = content()
        val heavyOnly = selectionOf(EntourageTerpene.BETA_CARYOPHYLLENE)

        val result = EntourageLab.solve(
            handlingCase().copy(handlingGoal = HandlingGoal.VOLATILE_FRACTION),
            heavyOnly,
            shipped.profiles,
            ProcessingMethod.LIVE_ROSIN
        )

        assertEquals(
            "keeping a heavy compound is not evidence that the volatile " +
                "fraction was kept",
            LabVerdict.INEFICAZ,
            result.verdict
        )
        assertTrue(
            "and the note has to say the material holds none of that class",
            result.notesEs.any { "clase" in it }
        )
    }

    /* ── The verdict mapping ─────────────────────────────────────────────── */

    @Test
    fun noRouteMeansNoVerdict() {
        val result = EntourageHandling.solve(
            handlingCase(),
            route = null,
            selection = selectionOf(EntourageTerpene.LIMONENE)
        )

        assertEquals(LabVerdict.INEFICAZ, result.verdict)
        assertTrue(result.readings.isEmpty())
        assertTrue(
            "and it has to say why, rather than reporting a zero",
            result.notesEs.any { it.isNotBlank() }
        )
    }

    @Test
    fun anEmptySelectionIsNotASubmission() {
        assertEquals(
            LabVerdict.INEFICAZ,
            EntourageHandling.solve(
                handlingCase(),
                ProcessingMethod.LIVE_ROSIN,
                selectionOf()
            ).verdict
        )
    }

    @Test
    fun aRouteTheCaseRulesOutIsUnscorable() {
        val handling = handlingCase()
        val forbidden = handling.handlingForbiddenRoutes.first()
        val result = EntourageHandling.solve(
            handling,
            forbidden,
            selectionOf(EntourageTerpene.LIMONENE)
        )

        assertEquals(LabVerdict.INEFICAZ, result.verdict)
        assertTrue(
            "the note has to name the route the player picked",
            result.notesEs.any { forbidden.labelEs in it }
        )
    }

    @Test
    fun aCompoundTheCatalogueDoesNotDocumentIsUnscorable() {
        // Not reachable with the shipped enum, because
        // `everyModuleCompoundIsInExactlyOneClass` proves all ten are classified.
        // That is the better world, so this asserts the *contract* rather than
        // fabricating a compound to break it: the null branch has to exist, has to
        // be what a missing classification returns, and has to be what the solver
        // filters on. A build that widened the enum without adding a note would
        // fall out of all three.
        val source = handlingSource()

        assertTrue(
            "isVolatileFraction has to return Boolean?, so an unclassified " +
                "compound is null rather than silently heavy",
            Regex("""fun isVolatileFraction\(terpene: EntourageTerpene\): Boolean\?""")
                .containsMatchIn(source)
        )
        assertTrue(
            "and the solver has to drop on the null rather than on `== false`",
            source.contains("HandlingCompounds.isVolatileFraction(it) == null")
        )
        assertTrue(
            "and it has to name the compound in the note rather than just say no",
            source.contains("""undocumented.joinToString(""")
        )
    }

    @Test
    fun fullCoverageIsOptimalPartialCoverageIsViableAndNoCoverageIsUseless() {
        // The whole-profile goal, because it is the one under which a *partial*
        // material is a question at all. Under the volatile goal a heavy-only
        // selection is not a partial success but an unscorable one — that is
        // `aGoalTheMaterialHasNoCompoundForIsUnscorableRatherThanSilentlyMet`.
        val case = handlingCase().copy(handlingGoal = HandlingGoal.WHOLE_PROFILE)

        assertEquals(
            LabVerdict.OPTIMO,
            EntourageHandling.solve(
                case,
                ProcessingMethod.LIVE_ROSIN,
                selectionOf(EntourageTerpene.LIMONENE, EntourageTerpene.BETA_CARYOPHYLLENE)
            ).verdict
        )

        val volatileOnly = EntourageHandling.solve(
            case,
            ProcessingMethod.SOLVENT_EXTRACTION,
            selectionOf(EntourageTerpene.LIMONENE)
        )
        assertEquals(
            "the solvent route keeps nothing of the volatile class",
            LabVerdict.INEFICAZ,
            volatileOnly.verdict
        )

        val heavyOnly = EntourageHandling.solve(
            case,
            ProcessingMethod.SOLVENT_EXTRACTION,
            selectionOf(EntourageTerpene.BETA_CARYOPHYLLENE)
        )
        assertEquals(
            "and the same route over a material it keeps outright is optimal: " +
                "the whole-profile goal is satisfied by keeping all of it",
            LabVerdict.OPTIMO,
            heavyOnly.verdict
        )

        assertEquals(
            LabVerdict.INEFICAZ,
            EntourageHandling.solve(
                case,
                ProcessingMethod.DECARBOXYLATION,
                selectionOf(EntourageTerpene.LIMONENE, EntourageTerpene.BETA_CARYOPHYLLENE)
            ).verdict
        )

        val mixed = EntourageHandling.solve(
            case,
            ProcessingMethod.SOLVENT_EXTRACTION,
            selectionOf(EntourageTerpene.LIMONENE, EntourageTerpene.BETA_CARYOPHYLLENE)
        )
        assertEquals(
            "and a route that keeps some of a mixed material is the partial case",
            LabVerdict.VIABLE,
            mixed.verdict
        )
    }

    @Test
    fun coverageCountsTheCompoundsTheRouteKeepsAndNeverAnythingElse() {
        val goal = handlingCase().copy(handlingGoal = HandlingGoal.WHOLE_PROFILE)
        val all = listOf(
            EntourageTerpene.LIMONENE,
            EntourageTerpene.BETA_CARYOPHYLLENE,
            EntourageTerpene.HUMULENE,
            EntourageTerpene.MYRCENE
        )
        val underSolvent = EntourageHandling.solve(
            goal,
            ProcessingMethod.SOLVENT_EXTRACTION,
            selectionOf(*all.toTypedArray())
        )

        assertEquals(
            "two of four survive a solvent separation",
            50,
            EntourageHandling.coveragePercent(underSolvent.handlingReadings)
        )
        assertEquals(LabVerdict.VIABLE, underSolvent.verdict)

        val underLive = EntourageHandling.solve(
            goal,
            ProcessingMethod.LIVE_ROSIN,
            selectionOf(*all.toTypedArray())
        )
        assertEquals(
            100,
            EntourageHandling.coveragePercent(underLive.handlingReadings)
        )
    }

    @Test
    fun coverageOfNothingIsZeroRatherThanADivisionByZero() {
        assertEquals(0, EntourageHandling.coveragePercent(emptyList()))
    }

    /* ── The declared classes still match the shipped notes ───────────────── */

    /**
     * The drift detector for the two hand-declared compound sets.
     *
     * [HandlingCompounds] is the weakest data in this repository and its KDoc says
     * so. This test is what makes the declaration defensible: it re-derives both
     * sets from `entourage_data.json` by reading each compound's shipped
     * `SOLVENT_EXTRACTION` note, so editing a note underneath the constants fails
     * here instead of silently leaving the Lab contradicting the page.
     *
     * It is a keyword check on prose, which is the right shape for a *drift
     * detector* and a terrible shape for a source of truth — which is exactly why
     * the constants are declared and this only watches them.
     */
    @Test
    fun theDeclaredCompoundSetsAgreeWithTheShippedProcessingNotes() {
        val shipped = content()
        val loses = Regex("""se va en buena parte""")
        val keeps = Regex("""de los últimos en irse|aguanta más que los monoterpenos""")

        val derivedVolatile = mutableSetOf<EntourageTerpene>()
        val derivedRetained = mutableSetOf<EntourageTerpene>()
        shipped.processing.forEach { entry ->
            val note = entry.methods
                .firstOrNull { it.method == ProcessingMethod.SOLVENT_EXTRACTION }
                ?.detailEs
                ?: return@forEach
            when {
                loses.containsMatchIn(note) -> derivedVolatile += entry.terpene
                keeps.containsMatchIn(note) -> derivedRetained += entry.terpene
            }
        }

        assertEquals(
            "the volatile set has to match what the shipped solvent notes say. " +
                "Derived: $derivedVolatile",
            derivedVolatile,
            HandlingCompounds.VOLATILE_FRACTION
        )
        assertEquals(
            "the retained set has to match what the shipped solvent notes say. " +
                "Derived: $derivedRetained",
            derivedRetained,
            HandlingCompounds.RETAINED_IN_SEPARATION
        )
    }

    @Test
    fun everyModuleCompoundIsInExactlyOneClass() {
        assertEquals(
            "the two sets have to partition the library, or a compound would be " +
                "in both or in neither",
            EntourageTerpene.entries.toSet(),
            HandlingCompounds.known
        )
        assertTrue(
            "and they may not overlap",
            HandlingCompounds.VOLATILE_FRACTION.intersect(
                HandlingCompounds.RETAINED_IN_SEPARATION
            ).isEmpty()
        )
        assertTrue(
            "both classes have to be non-empty or the mode scores one material",
            HandlingCompounds.VOLATILE_FRACTION.isNotEmpty() &&
                HandlingCompounds.RETAINED_IN_SEPARATION.isNotEmpty()
        )
    }

    @Test
    fun anUnknownCompoundIsNotSilentlyTreatedAsAHeavyOne() {
        // The null-vs-false decision. Every compound the enum has is classified
        // today, so the observable half is the partition (asserted above) plus the
        // declared return type (asserted beside it). What is left is to pin the
        // answers the shipped notes disagree on, so a refactor that flipped a set's
        // meaning could not pass.
        assertEquals(
            "limoneno's shipped note says it goes in good part during the separation",
            true,
            HandlingCompounds.isVolatileFraction(EntourageTerpene.LIMONENE)
        )
        assertEquals(
            "cariofileno's shipped note says it is among the last to go",
            false,
            HandlingCompounds.isVolatileFraction(EntourageTerpene.BETA_CARYOPHYLLENE)
        )
        assertEquals(
            "and humulene's says it beats the monoterpenes without claiming it " +
                "beats caryophyllene, so it belongs with the retained set",
            false,
            HandlingCompounds.isVolatileFraction(EntourageTerpene.HUMULENE)
        )
    }

    /* ── The guide table is total and carries its evidence ───────────────── */

    @Test
    fun everyRouteAndClassPairHasAGuideWithItsBasis() {
        ProcessingMethod.entries.forEach { route ->
            listOf(true, false).forEach { volatile ->
                val guide = HandlingGuides.guideFor(route, volatile)
                assertEquals(route, guide.route)
                assertTrue(
                    "a guide with no direction is a label with nothing behind it",
                    guide.whatEs.isNotBlank()
                )
                assertTrue(
                    "and a direction with no basis is a bare instruction",
                    guide.basisEs.isNotBlank()
                )
                assertEquals(
                    "the guide has to agree with the lookup it is reached through",
                    guide.outcome,
                    HandlingGuides.outcomeOf(route, volatile)
                )
            }
        }
    }

    @Test
    fun everyCellInTheTableIsReachableSoNoRouteKeepsAStaleAnswer() {
        val reachable = ProcessingMethod.entries.flatMap { route ->
            listOf(true, false).map { volatile -> route to HandlingGuides.outcomeOf(route, volatile) }
        }.toSet()
        val authored = HandlingGuides.all.map { it.route to it.outcome }.toSet()

        assertEquals(
            "a cell the table answers but does not explain would be a verdict " +
                "with no sentence behind it",
            authored,
            reachable
        )
    }

    /**
     * F4's three refusals, inherited whole.
     *
     * Asserted over the model's own copy rather than observed by absence in the
     * asset: a temperature, a duration or a quantity in a handling sentence is an
     * instruction, and this mode has no way to be honest with one.
     */
    @Test
    fun theAuthoredCopyCarriesNoTemperatureNoDurationAndNoQuantity() {
        val copy = HandlingGuides.all.flatMap { listOf(it.whatEs, it.basisEs) }

        assertTrue(
            "a temperature in a handling sentence would be a setpoint",
            copy.none { Regex("""\d+\s*°C""").containsMatchIn(it) }
        )
        assertTrue(
            "and a duration would be a schedule",
            copy.none {
                Regex("""\d+\s*(min|minutos|horas|días|dias|semanas)""").containsMatchIn(it)
            }
        )
        assertTrue(
            "and a quantity would be a yield",
            copy.none { Regex("""\d+\s*(%|g|mg|ml|mL|kg)""").containsMatchIn(it) }
        )
    }

    @Test
    fun everySentenceTheseScreensPrintPassesTheLanguageGuard() {
        val copy = HandlingGuides.all.flatMap { listOf(it.whatEs, it.basisEs) } +
            LabMode.entries.map { it.labelEs } +
            HandlingGoal.entries.map { it.labelEs } +
            HandlingOutcome.entries.map { it.labelEs }

        val offenders = copy
            .flatMap { text -> EntourageLanguage.violations(text).map { "$it in: $text" } }
            .toList()

        assertTrue("the copy must be clean: $offenders", offenders.isEmpty())
        listOf("cura", "daño", "peligro", "insegur", "tóxico").forEach { banned ->
            assertTrue(
                "and the F1/F4 banned list still applies here, found '$banned'",
                copy.none { banned in it.lowercase() }
            )
        }
    }

    /* ── The shipped case ─────────────────────────────────────────────────── */

    @Test
    fun theShippedHandlingCaseDeclaresEverythingItIsScoredOn() {
        val case = handlingCase()

        assertNotNull("a goal", case.handlingGoal)
        assertTrue("a material", case.handlingCompounds.isNotEmpty())
        assertNotNull("an evidence level", case.handlingEvidence)
        assertTrue("a basis", case.handlingBasisEs.isNotBlank())
        assertTrue("an explanation", case.explanationEs.isNotBlank())
        assertTrue(
            "every compound of the material has to be one the mode can score",
            case.handlingCompounds.all { HandlingCompounds.isVolatileFraction(it) != null }
        )
        assertTrue(
            "a route it rules out, or the case carries no constraint at all",
            case.handlingForbiddenRoutes.isNotEmpty()
        )
    }

    @Test
    fun everyRouteTheCaseAllowsIsDifferentFromEveryRouteItForbids() {
        val case = handlingCase()
        val material = selectionOf(*case.handlingCompounds.toTypedArray())

        ProcessingMethod.entries.forEach { route ->
            val result = EntourageHandling.solve(case, route, material)
            if (route in case.handlingForbiddenRoutes) {
                assertEquals(
                    "$route is ruled out and has to come back unscored",
                    LabVerdict.INEFICAZ,
                    result.verdict
                )
            } else {
                assertFalse(
                    "$route is allowed, so it must not report the case as undocumented",
                    result.notesEs.any { "no documenta" in it }
                )
            }
        }
    }

    @Test
    fun theShippedCaseIsSolvableByAtLeastOneRoute() {
        val case = handlingCase()
        val material = selectionOf(*case.handlingCompounds.toTypedArray())

        val best = ProcessingMethod.entries
            .map { EntourageHandling.solve(case, it, material) }
            .minBy { it.verdict.ordinal }

        assertNotEquals(
            "no route in the enum serves this case, so it is unsolvable content",
            LabVerdict.INEFICAZ,
            best.verdict
        )
    }

    @Test
    fun theCaseTitlesAreDistinctSoTheBadgeCanFindItsOwnRows() {
        val titles = content().cases.map { it.titleEs }
        assertEquals(
            "the case row name is built from the title, so a duplicated title " +
                "would make the third badge unreachable",
            titles.size,
            titles.toSet().size
        )
    }
}
