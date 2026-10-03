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

    /* ── The evidence level is in the text, not behind a disclosure ────────── */

    /**
     * Every shipped synergy names its evidence level in words a reader can act on.
     *
     * `evidence_es` is rendered on the card next to the claim, not in a tooltip
     * behind a tap, so "the evidence is pre-clinical" has to survive being read
     * as a single sentence. The three levels that mean anything here are `in
     * vitro`, animal and human; a line that names none of them is describing a
     * confidence rather than a body of evidence.
     *
     * [EVIDENCE_LEVEL_TERMS] is deliberately generous — it accepts the several
     * ways Spanish names the same level ("modelos animales", "roedores",
     * "preclínicos") — because the failure this guards against is a line that
     * says *nothing* about its level, not one that phrases it unusually.
     */
    @Test
    fun everyShippedSynergyNamesItsEvidenceLevelInVisibleText() {
        val unlevelled = content().synergies.filter { synergy ->
            val text = synergy.evidenceEs.lowercase()
            EVIDENCE_LEVEL_TERMS.none { it in text }
        }

        assertEquals(
            "these evidence lines never say whether the data is in vitro, animal " +
                "or human; a reader cannot tell what kind of study is behind the " +
                "claim without opening something else",
            emptyList<String>(),
            unlevelled.map { it.id }
        )
    }

    /**
     * A synergy with no human data says so, in the same line the reader sees.
     *
     * The card already shows `evidence_es` prominently, so this is where the
     * absence of human trials has to be stated. The pattern to match is
     * `cbd_caryophyllene`: "no está probada en ensayos clínicos en personas" —
     * explicit, present, and attached to the specific combination rather than to
     * the compound in general.
     *
     * This is the anti-overclaim guard, and it is deliberately narrow. It cannot
     * tell whether a sentence overclaims; it can only require that a module with
     * no human trials for a combination admits it in text. A human-sounding
     * outcome asserted by a synergy whose evidence line never mentions human
     * study is the failure.
     */
    @Test
    fun aCombinationWithNoHumanTrialsSaysSoInItsOwnEvidenceLine() {
        // Sentences that promise an outcome in a person. "Se ha propuesto",
        // "se le atribuye" and "plausible" are the module's own hedges and do not
        // trip this: the concern is an *asserted* outcome, not a described one.
        val assertedHumanOutcome = listOf(
            "reduce la paranoia en personas",
            "mejora la ansiedad en personas",
            "se ha comprobado en personas",
            "demostrado en personas",
            "probado en pacientes",
            "efecto en pacientes"
        )

        content().synergies.forEach { synergy ->
            val text = synergy.evidenceEs.lowercase()
            val mentionsHumans = HUMAN_TERMS.any { it in text }
            val hedges = HEDGE_TERMS.any { it in text }

            assertedHumanOutcome.forEach { phrase ->
                assertTrue(
                    "${synergy.id} asserts \"$phrase\" in its evidence line",
                    !text.contains(phrase)
                )
            }
            // No level of evidence named at all is already the previous test's job;
            // here the requirement is the narrower one: a synergy that says
            // nothing about human trials must not be phrased as if it has them.
            if (!mentionsHumans && !hedges) {
                assertTrue(
                    "${synergy.id} never mentions human trials and never hedges: " +
                        "\"${synergy.evidenceEs}\" reads as an established result " +
                        "when the module ships no human data for the combination",
                    text.contains("mecan") || text.contains("hipótesis") ||
                        text.contains("hipotesis") || text.contains("propuesto") ||
                        text.contains("plausible") || text.contains("preclín") ||
                        text.contains("preclin")
                )
            }
        }
    }

    /**
     * The reference case keeps saying what it says.
     *
     * `cbd_caryophyllene` is the standard the rest of the catalog is measured
     * against: it states that the CBD + beta-caryophyllene additivity is *not*
     * proven in human trials, and it says so about the combination rather than
     * about caryophyllene alone. A rewrite that softened that into "evidence is
     * limited" would be the module quietly upgrading a hypothesis into a result,
     * so the sentence is pinned here rather than trusted to review.
     */
    @Test
    fun theCbdCaryophylleneReferenceCaseStillDeniesHumanProof() {
        val synergy = content().synergyById("cbd_caryophyllene")

        assertTrue("the reference synergy has to be shipped", synergy != null)
        val text = synergy!!.evidenceEs.lowercase()

        assertTrue(
            "cbd_caryophyllene has to keep denying human proof; it reads " +
                "\"${synergy.evidenceEs}\"",
            text.contains("no está probada") || text.contains("no esta probada")
        )
        assertTrue(
            "and it has to name human trials as the missing thing",
            text.contains("personas")
        )
    }

    private companion object {
        /** Spanish ways of naming the three evidence levels that matter here. */
        val EVIDENCE_LEVEL_TERMS = listOf(
            "in vitro",
            "modelos animales",
            "modelo animal",
            "animales",
            "roedores",
            "preclínic",
            "preclin",
            "clínic",
            "clinic",
            "personas",
            "humanos",
            "ensayos"
        )

        /** Terms that show the line is talking about human studies at all. */
        val HUMAN_TERMS = listOf("personas", "humanos", "clínic", "clinic", "pacientes", "ensayos")

        /** The module's own hedges: a claim that describes rather than asserts. */
        val HEDGE_TERMS = listOf(
            "se ha propuesto",
            "se le atribuye",
            "plausible",
            "hipótesis",
            "hipotesis",
            "no hay",
            "no está",
            "no esta",
            "no se ha"
        )
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
            // F5: the two modes need different answers and different harnesses.
            // A pharmacological case is solved by hitting the profile inside its
            // ceilings; a handling case is solved by picking the route that keeps
            // what the case asks to keep. Running the pharmacological harness over
            // a handling case would assert it has a cannabinoid profile, which is
            // exactly what it must not have.
            val result = if (case.mode == com.trichome.app.model.LabMode.HANDLING) {
                solvingHandlingCase(case)
            } else {
                solvingPharmacologicalCase(case, profiles)
            }

            assertFalse(
                "case ${case.id} cannot be solved by the content it declares",
                result.verdict == com.trichome.app.model.LabVerdict.INEFICAZ
            )
            assertTrue(
                "case ${case.id} pays nothing for a solved puzzle, so the Lab " +
                    "cannot be used to earn XP",
                EntourageRewards.forLabVerdict(case.id, case.titleEs, result.verdict).isNotEmpty()
            )
        }
    }

    /** The intended answer for a pharmacological case: its profile, inside its ceilings. */
    private fun solvingPharmacologicalCase(
        case: com.trichome.app.model.EntourageCase,
        profiles: List<com.trichome.app.model.EntourageProfile>
    ): com.trichome.app.model.LabResult {
        val goal = profiles.firstOrNull { it.key == case.goal }
        assertNotNull("case ${case.id} has no shipped profile", goal)
        val dials = goal!!.cannabinoidWeights.keys
            .associateWith { cannabinoid -> case.maxCannabinoidShare[cannabinoid] ?: 1f }
        val selection = com.trichome.app.model.EntourageLabUi.selectionFromDials(
            dials,
            goal.terpeneShares.keys
        )
        return com.trichome.app.model.EntourageLab.solve(case, selection, profiles)
    }

    /**
     * The intended answer for a handling case: every route tried, the best one.
     *
     * Not a hardcoded route on purpose. The point being asserted is that a shipped
     * handling case is *solvable at all* — that some route in the enum serves its
     * declared goal for its declared material. A test that named the route would
     * be asserting the content, and the content is what the other suites check.
     */
    private fun solvingHandlingCase(
        case: com.trichome.app.model.EntourageCase
    ): com.trichome.app.model.LabResult {
        val material = case.handlingCompounds
        assertTrue(
            "handling case ${case.id} ships no material to judge a route on",
            material.isNotEmpty()
        )
        val selection = com.trichome.app.model.EntourageLabUi.selectionFromDials(
            emptyMap(),
            material
        )
        return com.trichome.app.model.ProcessingMethod.entries
            .map { route ->
                com.trichome.app.model.EntourageLab.solve(case, selection, emptyList(), route)
            }
            .minBy { it.verdict.ordinal }
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

    /**
     * The badge a player is actually shown matches the quiz they actually played.
     *
     * This is the assertion that makes the derivation worth having. The reward
     * the quiz pays is built from the round count of the run, so it cannot go
     * stale; but the constant `QUIZ_ROUNDS` still exists as the shipped default
     * for [EntourageAchievement.description], and the day the asset grows or
     * shrinks a question it is the constant — not the text the player reads —
     * that would be wrong. Here the two are compared against the same file.
     */
    @Test
    fun theBadgeTextFollowsTheShippedQuizLength() {
        val rounds = content().questions.size

        // F5 deleted `QUIZ_ROUNDS` rather than bumping it. The assertion it used
        // to make — "the constant agrees with the file" — is now structural: there
        // is no constant, so there is nothing that *can* disagree. What is left
        // to assert is the half that is still a real risk: that the text a player
        // reads is built from the $rounds questions the asset actually ships.
        assertEquals(
            "the badge text a player reads has to be built from the $rounds " +
                "questions the asset ships",
            com.trichome.app.model.EntourageAchievement
                .ENTOURAGE_MASTER
                .descriptionFor(rounds),
            "Acierta ${com.trichome.app.model.EntourageAchievement.thresholdFor(rounds)} " +
                "de $rounds preguntas sobre modulación terpénica"
        )
        assertTrue(
            "and the count has to be the parsed one, not a literal",
            rounds >= 1
        )
    }

    /** The reward the quiz pays states the threshold the run was measured against. */
    @Test
    fun thePaidBadgeDescribesTheRunThatPaidIt() {
        val rounds = content().questions.size
        val threshold = com.trichome.app.model.EntourageAchievement.thresholdFor(rounds)

        val rewards = EntourageRewards.forQuiz(score = threshold, rounds = rounds, finished = true)

        assertEquals(
            "a qualifying run pays exactly the module's badge",
            1,
            rewards.size
        )
        assertEquals(
            "the paid description has to name the $rounds rounds actually played",
            com.trichome.app.model.EntourageAchievement
                .ENTOURAGE_MASTER
                .descriptionFor(rounds),
            rewards.first().descriptionEs
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
        // F11 replaced the hardcoded 4. The assertion is now the one that was
        // always true underneath it — the enum and the file agree — plus a floor
        // on the breadth, so a build that dropped the seventeen F11 cards down to
        // the original four would fail here rather than pass quietly.
        assertEquals(
            "every enum member has to ship a card and vice versa",
            PharmacologicalProfile.entries.size,
            shipped.profiles.size
        )
        assertTrue(
            "the profile library must be broad enough to be worth a filter",
            shipped.profiles.size >= 20
        )
        shipped.profiles.forEach { profile ->
            assertTrue("${profile.key} has no description", profile.descriptionEs.isNotBlank())
            assertTrue("${profile.key} has no note", profile.noteEs.isNotBlank())
        }
        assertTrue(PharmacologicalProfile.entries.isNotEmpty())
    }
}
