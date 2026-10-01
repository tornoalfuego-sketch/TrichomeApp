package com.trichome.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Lifecycle of the Séquito quiz, and the reward it grants.
 *
 * The quiz is a state machine for the same reason [TerpeneQuiz] is: a tap
 * records an answer and does not advance, so the explanation always renders
 * above the question the player actually read.
 */
class EntourageQuizTest {

    private fun question(id: String, correct: Int = 0) = EntourageQuizQuestion(
        id = id,
        promptEs = "Prompt de $id",
        optionsEs = listOf("A", "B", "C", "D"),
        correctIndex = correct,
        explanationEs = "Explicación de $id"
    )

    private fun quiz(size: Int = 3, seed: Int = 7) =
        EntourageQuiz((1..size).map { question("q$it") }, random = Random(seed))

    private fun asking(q: EntourageQuiz) = q.state as EntourageQuizState.Asking

    // --- the shipped order is never what the player sees -------------------

    @Test
    fun theAnswerIsNotAlwaysInTheFirstSlot() {
        // The asset ships every correct answer first, so the state machine
        // shuffles. Without the shuffle the quiz is a pattern, not a test of
        // whether the player read the question.
        val seen = (0..99).map { seed ->
            (quiz(size = 1, seed = seed).state as EntourageQuizState.Asking).question.correctIndex
        }

        assertTrue(
            "the correct option must move around",
            seen.toSet().size > 1
        )
    }

    @Test
    fun shufflingNeverChangesWhichOptionIsCorrect() {
        val shipped = question("q", correct = 2)

        repeat(50) { seed ->
            val shown = EntourageQuiz(listOf(shipped), random = Random(seed))
                .state as EntourageQuizState.Asking
            val question = shown.question

            assertEquals("options must be a permutation", 4, question.optionsEs.size)
            assertEquals("options must stay distinct", 4, question.optionsEs.toSet().size)
            assertEquals(
                "seed=$seed: the answer moved to slot ${question.correctIndex} but names '${question.optionsEs[question.correctIndex]}'",
                shipped.optionsEs[shipped.correctIndex],
                question.optionsEs[question.correctIndex]
            )
        }
    }

    @Test
    fun theSameSeedGivesTheSameQuestion() {
        assertEquals(
            "the shuffle is injected, so a seed is reproducible",
            asking(quiz(seed = 31)).question,
            asking(quiz(seed = 31)).question
        )
    }

    @Test
    fun differentSeedsCanPutTheAnswerInDifferentSlots() {
        val slots = (0..99).map { seed ->
            asking(quiz(size = 1, seed = seed)).question.correctIndex
        }

        assertEquals(
            "every slot has to be reachable, or the shuffle is not shuffling",
            setOf(0, 1, 2, 3),
            slots.toSet()
        )
    }

    // --- the lifecycle ------------------------------------------------------

    @Test
    fun answeringDoesNotAdvanceTheRound() {
        val q = quiz()
        val onScreen = asking(q).question

        val reveal = q.answer(onScreen.correctIndex) as EntourageQuizState.Revealed

        assertEquals(1, reveal.round)
        assertEquals("the options must not be swapped under the player", onScreen.optionsEs, reveal.question.optionsEs)
    }

    @Test
    fun theRevealDescribesTheQuestionThatWasAsked() {
        val q = quiz()
        val onScreen = asking(q).question

        val reveal = q.answer(onScreen.correctIndex) as EntourageQuizState.Revealed

        assertSame(onScreen, reveal.question)
        assertTrue(reveal.question.explanationEs.isNotBlank())
    }

    @Test
    fun aSecondTapOnAnAnsweredRoundIsIgnored() {
        val q = quiz()

        val first = q.answer(1)

        assertSame("a revealed round must not be answerable twice", first, q.answer(2))
    }

    @Test
    fun nextIsRejectedWhileAQuestionIsUnanswered() {
        val q = quiz()
        val before = q.state

        assertSame("advancing without answering would skip a question", before, q.next())
    }

    @Test
    fun anOutOfRangePickIsIgnored() {
        val q = quiz()
        val before = q.state

        assertSame(before, q.answer(99))
        assertSame(before, q.answer(-1))
    }

    @Test
    fun aCorrectAnswerScoresAndAWrongOneDoesNot() {
        val correct = quiz(seed = 11)
        val right = correct.answer(asking(correct).question.correctIndex) as EntourageQuizState.Revealed
        val wrongIndex = (0..3).first { it != right.question.correctIndex }
        val wrongQuiz = quiz(seed = 11)
        val wrong = wrongQuiz.answer(wrongIndex) as EntourageQuizState.Revealed

        assertTrue(right.isCorrect)
        assertEquals(1, right.score)
        assertFalse(wrong.isCorrect)
        assertEquals(0, wrong.score)
    }

    @Test
    fun theScoreCarriesIntoTheNextRound() {
        val q = quiz(size = 3)
        q.answer(asking(q).question.correctIndex)

        val second = q.next() as EntourageQuizState.Asking

        assertEquals(2, second.round)
        assertEquals(1, second.score)
        assertEquals(3, second.totalRounds)
    }

    @Test
    fun theLastRoundFinishesWithNoEmptyFrame() {
        val total = 4
        val q = quiz(size = total)

        repeat(total) { index ->
            q.answer(asking(q).question.correctIndex)
            val after = q.next()
            if (index < total - 1) {
                assertTrue("round ${index + 1} continues", after is EntourageQuizState.Asking)
            } else {
                val finished = after as EntourageQuizState.Finished
                assertEquals(total, finished.score)
                assertEquals(total, finished.totalRounds)
            }
        }
    }

    @Test
    fun aFinishedRunIsTerminal() {
        val q = quiz(size = 1)
        q.answer(asking(q).question.correctIndex)
        val finished = q.next()

        assertSame(finished, q.next())
        assertSame(finished, q.answer(0))
    }

    @Test
    fun restartReturnsToRoundOneWithAZeroScore() {
        val q = quiz(size = 2)
        q.answer(asking(q).question.correctIndex)
        q.next()

        val restarted = q.restart() as EntourageQuizState.Asking

        assertEquals(1, restarted.round)
        assertEquals(0, restarted.score)
    }

    @Test
    fun noQuestionsIsReportedRatherThanCrashing() {
        val q = EntourageQuiz(emptyList())

        val state = q.state as EntourageQuizState.Unavailable
        assertTrue(state.reason.isNotBlank())
        assertSame(state, q.answer(0))
        assertSame(state, q.next())
    }

    @Test
    fun everyReachableStateCarriesSomethingToRender() {
        val q = quiz(size = 3)
        val seen = listOf(q.state, q.answer(asking(q).question.correctIndex), q.next())

        seen.forEach { state ->
            when (state) {
                is EntourageQuizState.Asking -> assertTrue(
                    state.question.promptEs.isNotBlank() && state.question.optionsEs.isNotEmpty()
                )
                is EntourageQuizState.Revealed -> assertTrue(state.question.explanationEs.isNotBlank())
                is EntourageQuizState.Finished -> assertTrue(state.totalRounds > 0)
                is EntourageQuizState.Unavailable -> assertTrue(state.reason.isNotBlank())
            }
        }
    }

    // --- the reward ---------------------------------------------------------

    @Test
    fun eightOutOfTenEarnsTheBadge() {
        assertTrue(
            "the shipped description promises 8 of 10, so 8 must earn it",
            EntourageAchievement.isEarned(score = 8, rounds = 10, finished = true)
        )
    }

    @Test
    fun sevenOutOfTenDoesNotEarnTheBadge() {
        assertFalse(
            EntourageAchievement.isEarned(score = 7, rounds = 10, finished = true)
        )
    }

    @Test
    fun anUnfinishedRunCannotEarnTheBadge() {
        assertFalse(
            "quitting at round ten is not the same as finishing",
            EntourageAchievement.isEarned(score = 10, rounds = 10, finished = false)
        )
    }

    @Test
    fun theThresholdIsAFractionOfTheRoundsNotAFixedCount() {
        // A shorter shipped quiz must not make the badge impossible, and a
        // longer one must not hand it out for one lucky answer.
        assertEquals(1, EntourageAchievement.thresholdFor(rounds = 2))
        assertEquals(4, EntourageAchievement.thresholdFor(rounds = 5))
        assertEquals(8, EntourageAchievement.thresholdFor(rounds = 10))
        assertEquals(16, EntourageAchievement.thresholdFor(rounds = 20))
    }

    @Test
    fun theThresholdAlwaysLeavesARunWinnable() {
        (1..30).forEach { rounds ->
            val threshold = EntourageAchievement.thresholdFor(rounds)
            assertTrue(
                "a run of $rounds needs $threshold correct, which is not reachable",
                threshold in 1..rounds
            )
        }
    }

    @Test
    fun aRunWithNoRoundsEarnsNothing() {
        assertFalse(EntourageAchievement.isEarned(score = 10, rounds = 0, finished = true))
    }

    @Test
    fun theBadgeIsWorthExperienceWorthEarning() {
        val badge = EntourageAchievement.ENTOURAGE_MASTER

        assertTrue("a badge with no XP is not a reward", badge.xpReward > 0)
        assertTrue(badge.labelEs.isNotBlank())
        assertTrue(badge.description.isNotBlank())
        assertTrue(badge.icon.isNotBlank())
        assertEquals(
            "the shipped description has to match the threshold the code enforces",
            "Acierta ${EntourageAchievement.thresholdFor(EntourageAchievement.QUIZ_ROUNDS)} " +
                "de ${EntourageAchievement.QUIZ_ROUNDS} preguntas sobre modulación terpénica",
            badge.description
        )
    }
}
