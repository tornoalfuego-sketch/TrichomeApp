package com.trichome.app.model

import com.trichome.app.data.repository.Terpene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Lifecycle of the trivia minigame.
 *
 * The defect these tests pin down: the dialog used to bump its round counter in
 * the same click handler that recorded the answer, so the next recomposition
 * already held question N+1. The explanation was therefore rendered above a
 * question the player had not read yet, describing an answer whose highlight had
 * already been replaced. The lifecycle is a pure state machine now, so the whole
 * thing is assertable without a device.
 */
class TerpeneQuizTest {

    private fun terpene(id: String, aroma: String = "Terroso y afrutado") =
        Terpene(id = id, name = id.replaceFirstChar { it.uppercase() }, aroma = aroma)

    private fun pool(size: Int = 8) = (1..size).map { terpene("t$it") }

    private fun asking(quiz: TerpeneQuiz) = quiz.state as TerpeneQuizState.Asking

    private fun revealed(quiz: TerpeneQuiz) = quiz.state as TerpeneQuizState.Revealed

    // --- the defect --------------------------------------------------------

    @Test
    fun revealingKeepsTheQuestionThatWasJustAnswered() {
        val quiz = TerpeneQuiz(pool(), random = Random(7))
        val onScreen = asking(quiz).question

        val after = quiz.answer(onScreen.correctIndex)

        val reveal = after as TerpeneQuizState.Revealed
        assertSame(
            "the reveal must describe the question the player actually saw, not the next one",
            onScreen,
            reveal.question
        )
    }

    @Test
    fun answeringDoesNotAdvanceToTheNextQuestion() {
        val quiz = TerpeneQuiz(pool(), random = Random(11))
        val onScreen = asking(quiz).question

        val reveal = revealed(quiz.apply { answer(onScreen.correctIndex) })

        assertEquals("answering must stay on the same round", 1, reveal.round)
        assertEquals("the options must not be swapped under the player", onScreen.options, reveal.question.options)
    }

    @Test
    fun aSecondTapOnAnAnsweredRoundIsIgnored() {
        val quiz = TerpeneQuiz(pool(), random = Random(13))
        val first = quiz.answer(1)

        val second = quiz.answer(2)

        assertSame("a revealed round must not be answerable again", first, second)
    }

    @Test
    fun nextIsRejectedWhileAQuestionIsStillUnanswered() {
        val quiz = TerpeneQuiz(pool(), random = Random(17))
        val before = quiz.state

        val after = quiz.next()

        assertSame("advancing without answering would skip a question", before, after)
    }

    // --- explicit advancement ----------------------------------------------

    @Test
    fun nextMovesToAFreshQuestionAndKeepsTheScore() {
        val quiz = TerpeneQuiz(pool(), random = Random(19))
        quiz.answer(asking(quiz).question.correctIndex)

        val advanced = quiz.next()

        val second = advanced as TerpeneQuizState.Asking
        assertEquals("the second round must be on screen", 2, second.round)
        assertEquals("a correct answer must carry into the next round", 1, second.score)
    }

    @Test
    fun aWrongAnswerDoesNotAddToTheScore() {
        val quiz = TerpeneQuiz(pool(), random = Random(23))
        val question = asking(quiz).question
        val wrongIndex = (0 until question.options.size).first { it != question.correctIndex }

        val reveal = quiz.answer(wrongIndex) as TerpeneQuizState.Revealed

        assertEquals("a wrong answer must not score", 0, reveal.score)
    }

    // --- right / wrong is observable per round -----------------------------

    @Test
    fun aWrongAnswerIsDistinguishableFromACorrectOne() {
        val pool = pool()
        val question = asking(TerpeneQuiz(pool, random = Random(29))).question
        val wrongIndex = (0 until question.options.size).first { it != question.correctIndex }

        val right = TerpeneQuiz(pool, random = Random(29)).let { quiz ->
            quiz.answer(question.correctIndex) as TerpeneQuizState.Revealed
        }
        val wrong = TerpeneQuiz(pool, random = Random(29)).let { quiz ->
            quiz.answer(wrongIndex) as TerpeneQuizState.Revealed
        }

        assertTrue("the correct pick must be marked correct", right.isCorrect)
        assertFalse("the wrong pick must not be marked correct", wrong.isCorrect)
        assertTrue("the reveal must still name the right option", right.question.correctIndex in 0 until right.question.options.size)
        assertTrue("the reveal must still name the chosen option", wrong.chosenIndex in 0 until wrong.question.options.size)
    }

    @Test
    fun theRevealedRoundCarriesBothTheCorrectAndTheChosenOption() {
        val quiz = TerpeneQuiz(pool(), random = Random(31))
        val question = asking(quiz).question
        val wrongIndex = (0 until question.options.size).first { it != question.correctIndex }

        val reveal = quiz.answer(wrongIndex) as TerpeneQuizState.Revealed

        assertEquals("the wrong pick is remembered", wrongIndex, reveal.chosenIndex)
        assertEquals("the right answer is still available to show", question.correctIndex, reveal.question.correctIndex)
        assertTrue(
            "the explanation must be revealed with the question",
            reveal.question.explanation.isNotBlank()
        )
    }

    // --- terminal state ----------------------------------------------------

    @Test
    fun theLastRoundFinishesWithNoEmptyFrame() {
        val totalRounds = 3
        val quiz = TerpeneQuiz(pool(), totalRounds = totalRounds, random = Random(37))

        repeat(totalRounds) { round ->
            quiz.answer(asking(quiz).question.correctIndex)
            val after = quiz.next()
            if (round < totalRounds - 1) {
                assertTrue("round ${round + 1} must continue the game", after is TerpeneQuizState.Asking)
            } else {
                // Synchronously finished: no deferred flag flip, so the dialog
                // never renders one frame with a null question and no body.
                val finished = after as TerpeneQuizState.Finished
                assertEquals(totalRounds, finished.score)
                assertEquals(totalRounds, finished.totalRounds)
            }
        }
    }

    @Test
    fun everyReachableStateRendersSomething() {
        val totalRounds = 3
        val quiz = TerpeneQuiz(pool(), totalRounds = totalRounds, random = Random(41))
        val seen = mutableListOf<TerpeneQuizState>()

        seen += quiz.state
        repeat(totalRounds) {
            quiz.answer(asking(quiz).question.correctIndex)
            seen += quiz.state
            quiz.next().also { next -> seen += next }
        }

        assertTrue(
            "the walk must reach the terminal state last, ended on ${seen.last()::class.simpleName}",
            seen.last() is TerpeneQuizState.Finished
        )
        seen.forEach { state ->
            when (state) {
                is TerpeneQuizState.Asking -> assertTrue(
                    "an asking state must carry a prompt and options",
                    state.question.prompt.isNotBlank() && state.question.options.isNotEmpty()
                )
                is TerpeneQuizState.Revealed -> assertTrue(
                    "a revealed state must carry its explanation",
                    state.question.explanation.isNotBlank()
                )
                is TerpeneQuizState.Finished -> assertTrue(
                    "the finished state must carry the score",
                    state.totalRounds > 0
                )
                is TerpeneQuizState.Unavailable -> assertTrue(
                    "an unavailable state must say why",
                    state.reason.isNotBlank()
                )
            }
        }
    }

    @Test
    fun advancingPastTheEndStaysFinished() {
        val quiz = TerpeneQuiz(pool(), totalRounds = 1, random = Random(43))
        quiz.answer(asking(quiz).question.correctIndex)
        val finished = quiz.next()

        assertSame("the terminal state must be stable", finished, quiz.next())
        assertSame("the terminal state must be stable", finished, quiz.answer(0))
    }

    @Test
    fun restartReturnsToTheFirstRoundWithAZeroScore() {
        val quiz = TerpeneQuiz(pool(), totalRounds = 2, random = Random(47))
        quiz.answer(asking(quiz).question.correctIndex)
        quiz.next()
        quiz.answer(asking(quiz).question.correctIndex)
        quiz.next()

        val restarted = quiz.restart() as TerpeneQuizState.Asking

        assertEquals("a restart returns to round one", 1, restarted.round)
        assertEquals("a restart clears the score", 0, restarted.score)
    }

    // --- degenerate input --------------------------------------------------

    @Test
    fun aPoolTooSmallToBuildAQuestionIsReportedInsteadOfCrashing() {
        val quiz = TerpeneQuiz(listOf(terpene("solo")), random = Random(53))

        val state = quiz.state as TerpeneQuizState.Unavailable
        assertNotNull(state.reason)
        assertSame("an unavailable quiz has nothing to answer", state, quiz.answer(0))
        assertSame("an unavailable quiz has nothing to advance", state, quiz.next())
    }

    @Test
    fun terpenesWithoutAnAromaAreNotUsedAsAnswers() {
        val pool = pool(6) + Terpene(id = "vacio", name = "Vacio", aroma = "")

        val state = TerpeneQuiz(pool, random = Random(59)).state as TerpeneQuizState.Asking

        state.question.options.forEach { option ->
            assertTrue("a blank aroma cannot be answered: '$option'", option != "Vacio")
        }
    }

    // --- question construction --------------------------------------------

    @Test
    fun everyQuestionIsAnswerable() {
        repeat(50) { seed ->
            val state = TerpeneQuiz(pool(), random = Random(seed)).state as TerpeneQuizState.Asking
            val question = state.question

            assertEquals("four options per question", 4, question.options.size)
            assertTrue(
                "the correct option must exist, seed=$seed",
                question.correctIndex in question.options.indices
            )
            assertTrue("options must be distinct, seed=$seed", question.options.toSet().size == 4)
        }
    }
}
