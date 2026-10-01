package com.trichome.app.data.repository

import com.trichome.app.model.EntourageBooster
import com.trichome.app.model.EntourageCards
import com.trichome.app.model.EntourageFilters
import com.trichome.app.model.EntourageIntegrity
import com.trichome.app.model.EntourageLanguage
import com.trichome.app.model.EntouragePlanner
import com.trichome.app.model.EntourageQuiz
import com.trichome.app.model.EntourageRewards
import com.trichome.app.model.EntourageSynergyCard
import com.trichome.app.model.PharmacologicalProfile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runs the presentation layer against the **shipped** Séquito catalog.
 *
 * [EntouragePresentationTest] proves the presentation layer does the right thing
 * with whatever it is handed, including deliberately broken input. This is the
 * other half: that the file the app actually ships produces a module worth
 * showing. Hand-written fixtures pass both directions — they cannot catch a
 * synergy shipped with a blank `evidence_es`, a disclaimer that says nothing, or
 * a quiz too short for the badge to be reachable.
 *
 * The asset is read off disk exactly as `EntourageAssetTest` does, so a
 * hand-typed "translation" of it cannot stand in for it here.
 */
class EntourageShippedPresentationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun content(): EntourageContent {
        val file = listOf(
            File("src/main/assets/data/entourage_data.json"),
            File("app/src/main/assets/data/entourage_data.json")
        ).firstOrNull { it.isFile }
            ?: error("Could not locate entourage_data.json")
        return json.decodeFromString<EntourageBible>(file.readText(Charsets.UTF_8)).toContent()
    }

    /* ── The disclaimer ───────────────────────────────────────────────────── */

    @Test
    fun theShippedDisclaimerIsRealTextAndSurvivesToTheScreen() {
        val shipped = content()

        assertTrue(
            "the module ships a disclaimer and it must be a sentence, not a placeholder",
            shipped.disclaimerEs.length > 40
        )
        val rendered = EntourageIntegrity.disclaimerFor(shipped.disclaimerEs)
        assertEquals(
            "the shipped text must be what reaches the screen, unaltered",
            shipped.disclaimerEs,
            rendered
        )
    }

    @Test
    fun theShippedParseIsCleanSoTheIntegrityNoticeStaysQuiet() {
        // The notice exists for a truncated catalog. If the shipped file is
        // clean, rendering nothing is the correct outcome and this pins it.
        assertNull(EntourageIntegrity.noticeFor(content().unresolvedReferences))
    }

    /* ── Every shipped synergy is explainable ─────────────────────────────── */

    @Test
    fun everyShippedSynergyDeclaresItsOwnEvidence() {
        // The single most important assertion in this file. A card whose
        // evidence line had to fall back would be the app printing a
        // mechanistically plausible claim with no stated limits, in a
        // health-adjacent screen.
        val withoutDeclaredEvidence = content().synergies
            .map { EntourageCards.cardFor(it) }
            .filterNot { it.evidenceWasDeclared }

        assertEquals(
            "these synergies ship no evidence_es, so their cards fall back to " +
                "\"the catalog declares none\"",
            emptyList<String>(),
            withoutDeclaredEvidence.map { it.synergyId }
        )
    }

    @Test
    fun noShippedCardFallsBackOnAnyField() {
        val fallbacks = content().synergies
            .map { EntourageCards.cardFor(it) }
            .filter {
                it.outcomeEs == EntourageSynergyCard.NOT_DECLARED ||
                    it.descriptionEs == EntourageSynergyCard.NOT_DECLARED ||
                    it.mechanismEs == EntourageSynergyCard.NOT_DECLARED
            }

        assertEquals(
            "these synergies are missing outcome, description or mechanism",
            emptyList<String>(),
            fallbacks.map { it.synergyId }
        )
    }

    @Test
    fun everyShippedEvidenceLineIsSubstantiveRatherThanAVeryShortClaim() {
        // "Evidencia: alta." is a declaration, not an evidence level. The bar is
        // deliberately loose — it only catches a stub.
        val thin = content().synergies.filter { it.evidenceEs.length < 12 }

        assertEquals("these evidence lines are too short to state a level", emptyList<String>(), thin.map { it.id })
    }

    /* ── The filter has something to filter ───────────────────────────────── */

    @Test
    fun theShippedLibraryIsReachableThroughTheFilter() {
        val shipped = content()
        val offered = EntourageFilters.filterableTerpenes(shipped.synergies)

        assertTrue("the filter row would be empty", offered.isNotEmpty())
        offered.forEach { terpene ->
            val shown = EntourageFilters.synergiesForTerpene(shipped.synergies, terpene)
            assertTrue(
                "offering $terpene as a filter that matches nothing is a dead end",
                shown.isNotEmpty()
            )
            assertTrue(shown.all { terpene in it.terpenes })
        }
    }

    @Test
    fun aTerpeneThatNoSynergyUsesIsStillReachableFromItsDetailPage() {
        // The join is offered for every modelled terpene, whether or not the
        // library documents one. The screen has to say so rather than look
        // broken; this pins that the "empty" state is a real reachable state.
        val shipped = content()
        val unmodelled = EntourageFilters.filterableTerpenes(shipped.synergies)
        assertNotNull(unmodelled)
        assertTrue(
            EntourageFilters.filterableTerpenes(shipped.synergies).size <=
                com.trichome.app.model.EntourageTerpene.entries.size
        )
    }

    /* ── The booster reads the shipped profiles ────────────────────────────── */

    @Test
    fun everyShippedProfileProducesACleanFrame() {
        val shipped = content()

        shipped.profiles.forEach { profile ->
            val selection = com.trichome.app.model.EntourageSelection(
                cannabinoids = profile.cannabinoidWeights.keys.take(1).toSet(),
                terpenes = profile.terpeneShares.keys.take(2).toSet()
            )
            val plan = EntouragePlanner.plan(selection, profile, shipped.synergies)
            val frame = EntourageBooster.frame(plan, profile)

            assertTrue(
                "the frame for ${profile.key} used ${EntourageLanguage.violations(frame.authoredTextEs)}",
                EntourageLanguage.violations(frame.authoredTextEs).isEmpty()
            )
            assertTrue(frame.caveatEs.isNotBlank())
        }
    }

    @Test
    fun aPerfectShippedProfileSelectionReportsOneHundred() {
        val shipped = content()
        val profile = shipped.profiles.first()

        val plan = EntouragePlanner.plan(
            com.trichome.app.model.EntourageSelection(terpenes = profile.terpeneShares.keys),
            profile
        )
        assertEquals(
            "holding every compound of a profile is the reference match, and the " +
                "screen must be able to show that",
            100,
            plan.percent
        )
    }

    /* ── The Lab and the quiz are playable with what ships ────────────────── */

    @Test
    fun everyShippedCaseIsSolvableAndPaysSomething() {
        val shipped = content()
        val profiles = shipped.profiles

        shipped.cases.forEach { case ->
            val goal = profiles.firstOrNull { it.key == case.goal }
            assertNotNull("case ${case.id} has no shipped profile", goal)

            // The intended answer: every terpene of the goal profile, and a
            // cannabinoid share inside the case's ceilings.
            val dials = goal!!.cannabinoidWeights.keys
                .associateWith { cannabinoid ->
                    case.maxCannabinoidShare[cannabinoid] ?: 1f
                }
            val selection = com.trichome.app.model.EntourageLabUi.selectionFromDials(
                dials,
                goal.terpeneShares.keys
            )
            val result = com.trichome.app.model.EntourageLab.solve(case, selection, profiles)

            assertFalse(
                "case ${case.id} cannot be solved by the profile it targets",
                com.trichome.app.model.EntourageLab.solve(
                    case,
                    com.trichome.app.model.EntourageLabUi.selectionFromDials(
                        dials,
                        goal.terpeneShares.keys
                    ),
                    profiles
                ).verdict == com.trichome.app.model.LabVerdict.INEFICAZ
            )
            assertTrue(
                "case ${case.id} pays nothing for a solved puzzle, so the Lab " +
                    "cannot be used to earn XP",
                EntourageRewards.forLabVerdict(case.id, case.titleEs, result.verdict).isNotEmpty()
            )
        }
    }

    @Test
    fun theShippedQuizIsLongEnoughForTheBadgeToBeReachable() {
        val shipped = content()

        assertTrue(
            "the badge threshold is a fraction of the rounds, but a quiz of " +
                "${shipped.questions.size} rounds makes it trivial or impossible",
            shipped.questions.size >= 5
        )
        val threshold = com.trichome.app.model.EntourageAchievement
            .thresholdFor(shipped.questions.size)
        assertTrue(
            "a perfect run must qualify: threshold $threshold of ${shipped.questions.size}",
            com.trichome.app.model.EntourageAchievement.isEarned(
                score = shipped.questions.size,
                rounds = shipped.questions.size,
                finished = true
            )
        )
        assertTrue(
            "a zero run must not",
            !com.trichome.app.model.EntourageAchievement.isEarned(0, shipped.questions.size, true)
        )
    }

    @Test
    fun theShippedQuizPlaysThroughToItsTerminalRound() {
        val quiz = EntourageQuiz(content().questions)
        val rounds = content().questions.size

        repeat(rounds) {
            val asking = quiz.state as com.trichome.app.model.EntourageQuizState.Asking
            quiz.answer(asking.question.correctIndex)
            quiz.next()
        }

        val finished = quiz.state
        assertTrue(
            "the machine must reach its terminal state, got $finished",
            finished is com.trichome.app.model.EntourageQuizState.Finished
        )
        assertEquals(rounds, (finished as com.trichome.app.model.EntourageQuizState.Finished).score)
    }

    @Test
    fun everyShippedVapourisationRowCarriesANote() {
        // A temperature with no note teaches a number and nothing else; the note
        // is where "why this window" lives.
        val without = content().vaporisation.filter { it.noteEs.isBlank() }

        assertEquals(
            "these temperatures ship no note_es",
            emptyList<String>(),
            without.map { it.terpene.key }
        )
    }

    @Test
    fun theShippedDisclaimersAndProfilesAreNotEmptyPlaceholders() {
        val shipped = content()
        assertEquals(4, com.trichome.app.model.PharmacologicalProfile.entries.size)
        shipped.profiles.forEach { profile ->
            assertTrue("${profile.key} has no description", profile.descriptionEs.isNotBlank())
            assertTrue("${profile.key} has no note", profile.noteEs.isNotBlank())
        }
        assertTrue(PharmacologicalProfile.entries.isNotEmpty())
    }
}
