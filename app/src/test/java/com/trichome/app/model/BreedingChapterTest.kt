package com.trichome.app.model

import com.trichome.app.data.repository.BreedingGeneration
import com.trichome.app.data.repository.BreedingTechnique
import com.trichome.app.data.repository.BreedingTerm
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract for the breeding theory chapters.
 *
 * Three things are locked down here, and they are the three ways this feature
 * could quietly lie to the user:
 *
 * 1. **Provenance.** A chapter either quotes `assets/data/breeding.json` or it is
 *    explicitly [BreedingContentSource.Authored]. There is no third state in which
 *    text appears in the app with no record of where it came from. The tests read
 *    the real `breeding.json` off disk, so a hand-typed "translation" of a
 *    generation cannot pass.
 * 2. **The genetics matches the simulator.** The authored chapter states ratios as
 *    [BreedingRatioClaim] data and every claim is checked against
 *    [Punnett.square], so the prose cannot drift from the maths the app ships.
 * 3. **Progress is additive.** A medal is banked, never recomputed from a
 *    shrinking score, so a worse second attempt cannot take one away.
 */
class BreedingChapterTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class BreedingAsset(
        val version: Int = 1,
        val generations: List<BreedingGeneration> = emptyList(),
        val techniques: List<BreedingTechnique> = emptyList(),
        val glossary: List<BreedingTerm> = emptyList()
    )

    private fun asset(): BreedingAsset {
        val candidates = listOf(
            File("src/main/assets/data/breeding.json"),
            File("app/src/main/assets/data/breeding.json")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Could not locate breeding.json. Looked in: " + candidates.joinToString { it.absolutePath })
        return json.decodeFromString(file.readText(Charsets.UTF_8))
    }

    private fun catalog() = BreedingChapters.catalog(
        generations = asset().generations,
        techniques = asset().techniques,
        glossary = asset().glossary
    )

    /* ── 1. Provenance ────────────────────────────────────────────────────── */

    @Test
    fun theCatalogIsNotEmptyAndEveryChapterIsUsable() {
        val chapters = catalog()
        assertTrue("the theory tab must ship chapters", chapters.isNotEmpty())
        chapters.forEach { chapter ->
            assertTrue("${chapter.id} has no id", chapter.id.isNotBlank())
            assertTrue("${chapter.id} has no title", chapter.titleEs.isNotBlank())
            assertTrue("${chapter.id} has no summary", chapter.summaryEs.isNotBlank())
            assertTrue("${chapter.id} has no body", chapter.bodyEs.isNotEmpty())
            chapter.bodyEs.forEach { assertTrue("${chapter.id} has an empty paragraph", it.isNotBlank()) }
            assertTrue("${chapter.id} ships no quiz", chapter.questions.isNotEmpty())
            chapter.questions.forEach { q ->
                assertTrue("${chapter.id}: a question has no prompt", q.promptEs.isNotBlank())
                assertTrue(
                    "${chapter.id}: a question has fewer than $MIN_OPTIONS_PER_QUESTION options",
                    q.optionsEs.size >= MIN_OPTIONS_PER_QUESTION
                )
                assertTrue(
                    "${chapter.id}: the correct index ${q.correctIndex} is outside " +
                        "0..${q.optionsEs.size - 1}",
                    q.correctIndex in q.optionsEs.indices
                )
                assertTrue("${chapter.id}: a question has no explanation", q.explanationEs.isNotBlank())
                assertEquals(
                    "${chapter.id}: duplicate options",
                    q.optionsEs.size,
                    q.optionsEs.distinct().size
                )
                q.optionsEs.forEach {
                    assertTrue("${chapter.id}: an option is blank", it.isNotBlank())
                }
            }
        }
    }

    @Test
    fun everyChapterIdIsUnique() {
        val ids = catalog().map { it.id }
        assertEquals("two chapters share an id", ids.size, ids.distinct().size)
    }

    @Test
    fun aChapterEitherQuotesTheAssetOrSaysItWasAuthored() {
        catalog().forEach { chapter ->
            when (val source = chapter.source) {
                is BreedingContentSource.FromAsset -> assertTrue(
                    "${chapter.id} claims the asset but names no entry",
                    source.refs.isNotEmpty()
                )
                // A mixed chapter has two obligations, not one: it must name the
                // entries it quotes *and* say what the expansion adds. Either half
                // alone would let authored prose be read as library text, which is
                // the thing this whole mechanism exists to prevent.
                is BreedingContentSource.Mixed -> {
                    assertTrue(
                        "${chapter.id} mixes but names no asset entry",
                        source.refs.isNotEmpty()
                    )
                    assertTrue(
                        "${chapter.id} mixes without saying what the expansion adds",
                        source.addedForApp.isNotBlank()
                    )
                }
                is BreedingContentSource.Authored -> assertTrue(
                    "${chapter.id} is authored without saying why",
                    source.reason.isNotBlank()
                )
            }
        }
    }

    @Test
    fun everyAssetEntryAChapterNamesReallyExistsInTheJson() {
        val data = asset()
        val known = buildSet {
            data.generations.forEach { add(BreedingAssetRef(BreedingContentCollection.GENERATIONS, it.id)) }
            data.techniques.forEach { add(BreedingAssetRef(BreedingContentCollection.TECHNIQUES, it.id)) }
            data.glossary.forEach { add(BreedingAssetRef(BreedingContentCollection.GLOSSARY, it.term)) }
        }
        assertTrue("the fixture must actually contain generations", known.isNotEmpty())

        catalog().forEach { chapter ->
            val source = chapter.source as? BreedingContentSource.FromAsset ?: return@forEach
            source.refs.forEach { ref ->
                assertTrue(
                    "${chapter.id} names ${ref.key}, which is not in breeding.json",
                    ref in known
                )
            }
        }
    }

    @Test
    fun theBodyOfAnAssetChapterIsTheAssetTextNotAParaphrase() {
        // The strongest provenance claim available without a human content review:
        // every paragraph of an asset chapter is either an asset string verbatim or
        // "<asset string> — <asset string>".
        val data = asset()
        val assetStrings = buildSet {
            data.generations.forEach { g ->
                add(g.label); add(g.titleEs); add(g.descriptionEs)
                addAll(g.stepsEs); addAll(g.prosEs); addAll(g.consEs)
            }
            data.techniques.forEach { t ->
                add(t.labelEs); add(t.descriptionEs)
                t.methodsEs.forEach { add(it.name); add(it.detailEs) }
            }
            data.glossary.forEach { add(it.term); add(it.definitionEs) }
        }
        assertTrue("the asset strings must not be empty", assetStrings.isNotEmpty())

        val assetChapters = catalog().filter { it.source is BreedingContentSource.FromAsset }
        assertTrue("some chapters must quote the asset", assetChapters.isNotEmpty())

        assetChapters.forEach { chapter ->
            chapter.bodyEs.forEach { paragraph ->
                val halves = paragraph.split(" — ")
                halves.forEach { half ->
                    assertTrue(
                        "${chapter.id} quotes text that is not in breeding.json: \"$half\"",
                        half in assetStrings
                    )
                }
            }
        }
    }

    @Test
    fun theAssetChaptersQuoteMoreThanJustTheirOwnTitle() {
        // Guards against a chapter satisfying the provenance test with a single
        // paragraph while its declared refs go unread.
        val data = asset()
        catalog()
            .filter { it.source is BreedingContentSource.FromAsset }
            .forEach { chapter ->
                if (chapter.id == "generaciones") {
                    assertTrue(
                        "the generations chapter must quote the loaded asset",
                        chapter.bodyEs.any { data.generations.any { g -> it.contains(g.descriptionEs) } }
                    )
                }
                if (chapter.id == "glosario") {
                    assertEquals(
                        "the glossary chapter must be one paragraph per term",
                        data.glossary.size,
                        chapter.bodyEs.size
                    )
                }
            }
    }

    @Test
    fun theThreeSubjectsTheRequestNamedAreAllPresent() {
        // Mendel, feminisation with STS, and phenotype selection.
        val ids = catalog().map { it.id }.toSet()
        listOf("mendel", "feminizacion", "fenotipica").forEach { id ->
            assertTrue("the chapter requested by the brief is missing: $id", id in ids)
        }
    }

    @Test
    fun theChaptersTheAssetCannotCoverAreMarkedAsAuthored() {
        val chapters = catalog().associateBy { it.id }
        // breeding.json has no Mendel entry and no ethylene mechanism, so both
        // chapters have to say the text was written for the app.
        listOf("mendel", "feminizacion", "fenotipica").forEach { id ->
            val source = chapters[id]!!.source
            assertTrue(
                "$id is authored by construction and must be marked as such, was $source",
                source is BreedingContentSource.Authored
            )
        }
    }

    @Test
    fun theProvenanceBadgeNamesTheSourceForBothKindsOfChapter() {
        catalog().forEach { chapter ->
            assertTrue(
                "${chapter.id} shows no provenance badge",
                chapter.provenanceLabelEs.isNotBlank()
            )
            if (chapter.source is BreedingContentSource.Authored) {
                assertTrue(
                    "${chapter.id} badge does not carry the reason",
                    chapter.provenanceLabelEs.contains(
                        (chapter.source as BreedingContentSource.Authored).reason
                    )
                )
            }
        }
    }

    @Test
    fun aChapterBuiltFromAnEmptyAssetRendersWithoutInventingAQuote() {
        // If the asset ever fails to load, the tab must still render. The authored
        // chapters are compiled in; the asset-backed ones come out empty rather
        // than being filled with a plausible-looking invention.
        val chapters = BreedingChapters.catalog(emptyList(), emptyList(), emptyList())

        chapters.forEach { chapter ->
            if (chapter.source is BreedingContentSource.FromAsset) {
                assertTrue(
                    "${chapter.id} claims the asset but the asset is empty",
                    chapter.bodyEs.isEmpty()
                )
            } else {
                assertTrue(
                    "${chapter.id} is authored and must keep its text",
                    chapter.bodyEs.isNotEmpty()
                )
            }
        }
        assertEquals("every chapter still ships its quiz", chapters.size * BreedingChapters.QUIZ_QUESTIONS,
            chapters.sumOf { it.questions.size })
    }

    /* ── 2. The genetics in the text matches the simulator ────────────────── */

    @Test
    fun everyRatioTheTextClaimsIsTheRatioTheSimulatorProduces() {
        val delta = 1e-9
        catalog().flatMap { it.ratioClaims }.forEach { claim ->
            val result = Punnett.square(claim.firstParent, claim.secondParent)
            assertTrue(
                "${claim.labelEs} must compute, got $result",
                result is PunnettResult.Computed
            )
            val square = (result as PunnettResult.Computed).square
            assertEquals(
                "${claim.labelEs}: dominant phenotype",
                claim.dominantPhenotypeProbability,
                square.dominantPhenotypeProbability,
                delta
            )
            assertEquals(
                "${claim.labelEs}: recessive phenotype",
                claim.recessivePhenotypeProbability,
                square.recessivePhenotypeProbability,
                delta
            )
            assertEquals(
                "${claim.labelEs}: carrier",
                claim.carrierProbability,
                square.carrierProbability,
                delta
            )
        }
    }

    @Test
    fun everyRatioClaimNamesACrossTheModelCanActuallyCompute() {
        catalog().flatMap { it.ratioClaims }.forEach { claim ->
            listOf(claim.firstParent, claim.secondParent).forEach { parent ->
                assertTrue(
                    "${claim.labelEs} uses \"$parent\", which the parser rejects",
                    Genotype.parse(parent) is GenotypeParse.Ok
                )
            }
        }
    }

    @Test
    fun theAuthoredChapterActuallyMakesRatioClaims() {
        val claims = catalog().first { it.id == "mendel" }.ratioClaims
        assertTrue(
            "the Mendel chapter teaches the ratios, so it has to state them as " +
                "checkable claims rather than prose nobody can verify",
            claims.size >= 3
        )
    }

    @Test
    fun theAuthoredChapterCoversAllFiveCanonicalCrosses() {
        val pairs = catalog().first { it.id == "mendel" }.ratioClaims
            .map { it.firstParent to it.secondParent }
            .toSet()
        listOf(
            "AA" to "AA", "AA" to "Aa", "Aa" to "Aa", "Aa" to "aa", "aa" to "aa"
        ).forEach { cross ->
            assertTrue("the Mendel chapter does not state $cross", cross in pairs)
        }
    }

    @Test
    fun theMendelChapterStatesTheClassicThreeToOneInItsOwnWords() {
        val mendel = catalog().first { it.id == "mendel" }
        assertTrue(
            "the classic F2 phenotype ratio must appear in the text",
            mendel.bodyEs.any { it.contains("3:1") } || mendel.keyPointsEs.any { it.contains("3:1") }
        )
        assertTrue(
            "the 1:2:1 genotype ratio must appear too",
            mendel.bodyEs.any { it.contains("1:2:1") } || mendel.keyPointsEs.any { it.contains("1:2:1") }
        )
    }

    /* ── 3. The unlock rule ───────────────────────────────────────────────── */

    @Test
    fun everyChapterIsOpenSoReadingAboutStsIsNeverGatedBehindAMendelQuiz() {
        val chapters = catalog()
        assertTrue("the all-open policy must actually be on", BreedingChapters.ALL_CHAPTERS_OPEN)
        chapters.forEach { chapter ->
            assertTrue(
                "${chapter.id} must be readable without finishing ${chapters.first().id}",
                BreedingChapters.isUnlocked(chapter.id, chapters)
            )
        }
    }

    @Test
    fun anIdThatIsNotInTheCatalogIsNotUnlocked() {
        // Defensive: a typo in a deep link must not open a chapter that does not
        // exist, even under the all-open policy.
        assertFalse(BreedingChapters.isUnlocked("no-such-chapter", catalog()))
        assertFalse(BreedingChapters.isUnlocked("", catalog()))
        assertFalse(BreedingChapters.isUnlocked("mendel", emptyList()))
    }

    @Test
    fun theOpenPolicyDoesNotDependOnTheOrderOfTheChapters() {
        val chapters = catalog()
        val shuffled = chapters.shuffled(kotlin.random.Random(seed = 7))
        assertEquals(
            chapters.map { BreedingChapters.isUnlocked(it.id, chapters) },
            shuffled.map { BreedingChapters.isUnlocked(it.id, shuffled) }
        )
    }

    @Test
    fun theChapterOrderIsStableAndThePrimerComesFirst() {
        assertEquals(catalog().map { it.id }, catalog().map { it.id })
        assertEquals("the Mendel primer opens the theory tab", "mendel", catalog().first().id)
    }

    /* ── 4. Quiz scoring ─────────────────────────────────────────────────── */

    private fun chapter() = catalog().first { it.id == "mendel" }

    /** Scores, failing loudly if the scorer rejected an input that should be valid. */
    private fun scored(chapter: BreedingChapter, answers: List<Int>): BreedingQuizOutcome =
        when (val result = scoreBreedingQuiz(chapter, answers)) {
            is BreedingQuizResult.Scored -> result.outcome
            is BreedingQuizResult.Invalid -> error("scoring rejected a valid quiz: ${result.reason}")
        }

    @Test
    fun everyCorrectAnswerScoresEveryQuestion() {
        val chapter = chapter()
        // The correct option per question, not `indices`: an answer list is the
        // option the user picked, which is the question's own correctIndex.
        val outcome = scored(chapter, chapter.questions.map { it.correctIndex })

        assertEquals(chapter.questions.size, outcome.correct)
        assertEquals(chapter.questions.size, outcome.total)
        assertEquals(BreedingMedalTier.GOLD, outcome.bestTier)
    }

    @Test
    fun everyWrongAnswerScoresNothing() {
        val chapter = chapter()
        val answers = chapter.questions.map { (it.correctIndex + 1) % it.optionsEs.size }

        val outcome = scored(chapter, answers)

        assertEquals(0, outcome.correct)
        assertNull("a zero earns nothing", outcome.bestTier)
        assertTrue("a zero earns no medal", outcome.earned.isEmpty())
    }

    @Test
    fun scoringIsIndependentOfHowManyQuestionsAChapterAsks() {
        val chapter = chapter()
        val correctAnswers = chapter.questions.map { it.correctIndex }
        val perfect = scored(chapter, correctAnswers)
        assertEquals(chapter.questions.size, perfect.correct)
        assertEquals(100, perfect.percentCorrect())

        // The score is a function of the answers and the question count, nothing
        // else: a chapter whose questions all carry the same answer pattern scores
        // the same, whatever the text says.
        val partial = chapter.questions.mapIndexed { index, question ->
            if (index == 0) question.correctIndex
            else (question.correctIndex + 1) % question.optionsEs.size
        }
        val outcome = scored(chapter, partial)
        assertEquals(1, outcome.correct)
        assertEquals(chapter.questions.size, outcome.total)
        assertEquals(25, outcome.percentCorrect())
    }

    @Test
    fun aPartialAnswerListIsRejectedRatherThanScoredAsAFailure() {
        val chapter = chapter()
        val result = scoreBreedingQuiz(chapter, chapter.questions.indices.drop(1).toList())
        assertTrue("a partial answer list is a bug, not a wrong answer", result is BreedingQuizResult.Invalid)
        assertTrue((result as BreedingQuizResult.Invalid).reason.isNotBlank())
    }

    @Test
    fun anEmptyAnswerListForANonEmptyQuizIsRejected() {
        val chapter = chapter()
        assertTrue(scoreBreedingQuiz(chapter, emptyList()) is BreedingQuizResult.Invalid)
    }

    @Test
    fun anOutOfRangeAnswerIsRejectedWithTheOffendingIndex() {
        val chapter = chapter()
        val answers = MutableList(chapter.questions.size) { 0 }
        answers[1] = 99
        val result = scoreBreedingQuiz(chapter, answers)
        assertTrue(result is BreedingQuizResult.Invalid)
        assertTrue(
            "the reason names the 1-based question position: ${(result as BreedingQuizResult.Invalid).reason}",
            result.reason.contains("2")
        )
    }

    @Test
    fun aNegativeAnswerIsRejected() {
        val chapter = chapter()
        val answers = MutableList(chapter.questions.size) { 0 }
        answers[0] = -1
        assertTrue(scoreBreedingQuiz(chapter, answers) is BreedingQuizResult.Invalid)
    }

    @Test
    fun aChapterWithNoQuestionsIsRejectedInsteadOfScoringAsAPerfectRun() {
        val empty = chapter().copy(questions = emptyList())
        assertTrue(scoreBreedingQuiz(empty, emptyList()) is BreedingQuizResult.Invalid)
    }

    @Test
    fun aReplayedScoreIsIdenticalWhichIsWhatMakesTheUnionWriteIdempotent() {
        val chapter = chapter()
        val answers = chapter.questions.map { it.correctIndex }
        assertEquals(
            scored(chapter, answers),
            scored(chapter, answers)
        )
    }

    /* ── 5. Medal thresholds ──────────────────────────────────────────────── */

    @Test
    fun theThresholdTableIsHalfThreeQuartersAndAllOfThem() {
        // 1 of 4 is a fail; 2 of 4 is bronze; 3 of 4 is silver; 4 of 4 is gold.
        assertNull("1 of 4 is below bronze", medalTierFor(1, 4))
        assertEquals(BreedingMedalTier.BRONZE, medalTierFor(2, 4))
        assertEquals(BreedingMedalTier.BRONZE, medalTierFor(2, 3))
        assertEquals(BreedingMedalTier.SILVER, medalTierFor(3, 4))
        assertEquals(BreedingMedalTier.GOLD, medalTierFor(4, 4))
        assertEquals(BreedingMedalTier.BRONZE, medalTierFor(1, 2))
        assertEquals(BreedingMedalTier.GOLD, medalTierFor(100, 100))
    }

    @Test
    fun theTierFractionsAreExactlyAHalfThreeQuartersAndOne() {
        assertEquals(0.5, BreedingMedalTier.BRONZE.minimumFraction, 0.0)
        assertEquals(0.75, BreedingMedalTier.SILVER.minimumFraction, 0.0)
        assertEquals(1.0, BreedingMedalTier.GOLD.minimumFraction, 0.0)
    }

    @Test
    fun aMedalNeedsExactlyHalfTheQuestionsForBronzeAndAllOfThemForGold() {
        assertEquals(2, requiredCorrectFor(BreedingMedalTier.BRONZE, 4))
        assertEquals(3, requiredCorrectFor(BreedingMedalTier.SILVER, 4))
        assertEquals(4, requiredCorrectFor(BreedingMedalTier.GOLD, 4))
        // Rounded up, so a half pass never needs more than half the questions.
        assertEquals(2, requiredCorrectFor(BreedingMedalTier.BRONZE, 3))
        assertEquals(3, requiredCorrectFor(BreedingMedalTier.SILVER, 3))
        assertEquals(1, requiredCorrectFor(BreedingMedalTier.BRONZE, 1))
        assertEquals(1, requiredCorrectFor(BreedingMedalTier.GOLD, 1))
    }

    @Test
    fun aZeroQuestionQuizNeverAwardsAnything() {
        BreedingMedalTier.entries.forEach { tier ->
            assertEquals(0, requiredCorrectFor(tier, 0))
        }
        assertNull(medalTierFor(0, 0))
        assertNull(medalTierFor(5, 0))
    }

    @Test
    fun moreCorrectAnswersThanQuestionsCannotClimbAboveGold() {
        assertEquals(BreedingMedalTier.GOLD, medalTierFor(50, 4))
    }

    @Test
    fun aNegativeScoreIsTreatedAsZero() {
        assertNull("a negative score is not a pass", medalTierFor(-3, 4))
        // A negative question count is not a quiz; the threshold collapses to zero
        // rather than to a negative "needed" figure.
        assertEquals(0, requiredCorrectFor(BreedingMedalTier.BRONZE, -4))
        assertTrue(medalsEarnedBy("mendel", -3, 4).isEmpty())
    }

    @Test
    fun everyTierOfAHigherBarAlsoClearsTheLowerBar() {
        listOf(1, 2, 3, 4, 6, 9).forEach { count ->
            BreedingMedalTier.entries.zipWithNext().forEach { (lower, higher) ->
                assertTrue(
                    "$count questions: ${higher.name} (${requiredCorrectFor(higher, count)}) must be at " +
                        "least ${lower.name} (${requiredCorrectFor(lower, count)})",
                    requiredCorrectFor(higher, count) >= requiredCorrectFor(lower, count)
                )
            }
        }
    }

    @Test
    fun theTiersAreStrictlyOrderedOnAnEightQuestionQuiz() {
        val bars = BreedingMedalTier.entries.map { requiredCorrectFor(it, 8) }
        assertEquals(listOf(4, 6, 8), bars)
    }

    @Test
    fun everyTierLabelRendersTheSpanishPercentageRule() {
        BreedingMedalTier.entries.forEach { tier ->
            assertTrue(
                "${tier.labelWithFractionEs} does not use \"n %\"",
                tier.labelWithFractionEs.contains(" % ")
            )
        }
    }

    /* ── 6. Medals are banked, never revoked ──────────────────────────────── */

    @Test
    fun theTierLadderHasNoGaps() {
        listOf(2, 3, 4, 4).forEach { total ->
            (0..total).forEach { correct ->
                val earned = medalsEarnedBy("mendel", correct, total).map { it.tier }
                val expected = BreedingMedalTier.entries.takeWhile { earned.contains(it) }
                assertEquals(
                    "the ladder for $correct of $total must be contiguous, got $earned",
                    expected,
                    earned
                )
            }
        }
    }

    @Test
    fun aPartialPassBanksOnlyWhatItCrossed() {
        val earned = medalsEarnedBy("mendel", 2, 4).map { it.tier }.toSet()
        assertEquals(setOf(BreedingMedalTier.BRONZE), earned)
    }

    @Test
    fun everyEarnedMedalBelongsToTheChapterThatWasQuizzed() {
        val outcome = scored(chapter(), chapter().questions.map { it.correctIndex })
        assertTrue("a perfect run must earn something", outcome.earned.isNotEmpty())
        outcome.earned.forEach { medal ->
            assertEquals(chapter().id, medal.chapterId)
        }
    }

    @Test
    fun aMedalIdRoundTripsThroughItsPersistedForm() {
        BreedingMedalTier.entries.forEach { tier ->
            val medal = BreedingMedal("mendel", tier)
            assertEquals(medal, parseBreedingMedalId(medal.id))
        }
    }

    @Test
    fun anUnreadableMedalIdIsRejectedRatherThanGuessedAt() {
        assertNull(parseBreedingMedalId(""))
        assertNull(parseBreedingMedalId("mendel"))
        assertNull(parseBreedingMedalId("mendel:platinum"))
        assertNull(parseBreedingMedalId("mendel:oro:extra"))
        assertNull(parseBreedingMedalId(":oro"))
    }

    @Test
    fun twoChaptersCannotShareAMedalId() {
        val ids = catalog().flatMap { chapter ->
            BreedingMedalTier.entries.map { BreedingMedal(chapter.id, it).id }
        }
        assertEquals("medal ids collide", ids.size, ids.distinct().size)
    }

    /* ── 7. The quizzes themselves ────────────────────────────────────────── */

    @Test
    fun everyChapterOffersTheSameNumberOfQuestionsSoTheRatiosAreComparable() {
        val sizes = catalog().map { it.questions.size }.toSet()
        assertEquals("chapters ask a different number of questions: $sizes", 1, sizes.size)
        assertEquals(BreedingChapters.QUIZ_QUESTIONS, sizes.first())
    }

    @Test
    fun theQuizOptionOrderIsNotLearnableFromTheIndex() {
        // A quiz whose correct answer is always option 0 teaches nothing.
        val indexes = catalog().flatMap { it.questions.map { it.correctIndex } }
        assertTrue(
            "the correct option is always at the same position: $indexes",
            indexes.distinct().size >= 2
        )
    }

    @Test
    fun noQuestionRepeatsItselfWithinItsOwnChapter() {
        catalog().forEach { chapter ->
            val prompts = chapter.questions.map { it.promptEs }
            assertEquals(
                "${chapter.id} repeats a question",
                prompts.size,
                prompts.distinct().size
            )
        }
    }

    @Test
    fun theOutcomeCarriesTheTotalsTheProgressScreenDisplays() {
        val chapter = chapter()
        val outcome = scored(chapter, chapter.questions.map { it.correctIndex })
        assertNotNull(outcome.bestTier)
        assertEquals(100, outcome.percentCorrect())

        val zero = chapter.questions.map { (it.correctIndex + 1) % it.optionsEs.size }
        assertEquals(0, scored(chapter, zero).percentCorrect())
    }
}


