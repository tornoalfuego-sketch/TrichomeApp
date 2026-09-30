package com.trichome.app.model

import com.trichome.app.data.repository.Terpene
import kotlin.random.Random

/** One question built from the catalog: identify the terpene from its aroma. */
data class TerpeneQuizQuestion(
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

/**
 * Every state the trivia minigame can be in.
 *
 * The states exist so the "answered" moment is a state of its own instead of a
 * side effect of tapping. Previously the tap both recorded the answer and
 * bumped the round counter, so the very next recomposition already held question
 * N+1: the explanation rendered above a question the player had not read yet and
 * the correct-option highlight was already gone. Splitting [Revealed] out makes
 * the answered question the one the UI is guaranteed to be showing.
 */
sealed interface TerpeneQuizState {

    /** The catalog cannot support a question; [reason] is player-facing text. */
    data class Unavailable(val reason: String) : TerpeneQuizState

    /** A question is on screen and nothing has been picked yet. */
    data class Asking(
        val question: TerpeneQuizQuestion,
        /** 1-based index of the round being asked. */
        val round: Int,
        val totalRounds: Int,
        val score: Int
    ) : TerpeneQuizState

    /**
     * The player picked and the round is being explained.
     *
     * [question] is the question that was asked — never the next one — and both
     * [chosenIndex] and [question].correctIndex are still readable, so the UI can
     * show the right answer and the player's pick side by side.
     */
    data class Revealed(
        val question: TerpeneQuizQuestion,
        val chosenIndex: Int,
        val round: Int,
        val totalRounds: Int,
        val score: Int
    ) : TerpeneQuizState {
        /** Whether the player hit the correct option this round. */
        val isCorrect: Boolean get() = chosenIndex == question.correctIndex

        /** True when this was the last round, so the UI can offer "Ver resultado". */
        val isLastRound: Boolean get() = round >= totalRounds
    }

    /** Every round is consumed. Carries the score, so it always renders a body. */
    data class Finished(val score: Int, val totalRounds: Int) : TerpeneQuizState
}

/**
 * The trivia lifecycle, as a pure state machine.
 *
 * No Compose state, no `LaunchedEffect`, no deferred flag. A transition is a
 * function of the current state, so the whole game — including the terminal
 * round — is assertable on the JVM.
 *
 * Transitions:
 * ```
 *   (start)  ──▶ Unavailable        pool too small to build a question
 *            ──▶ Asking
 *   Asking   ──answer(i)──▶ Revealed        tap records, does not advance
 *   Revealed ──next()─────▶ Asking          explicit "Siguiente"
 *                         ─▶ Finished        explicit, and only after the last round
 *   Finished ──answer/next─▶ Finished        terminal states are stable
 *   any      ──restart()───▶ Asking
 * ```
 * Any transition that is not listed for the current state is rejected and the
 * machine returns the same instance, so a double tap or a stale button cannot
 * corrupt the game.
 */
class TerpeneQuiz(
    pool: List<Terpene>,
    val totalRounds: Int = DEFAULT_ROUNDS,
    private val random: Random = Random.Default
) {

    /**
     * Only terpenes that carry an aroma description can be answered, otherwise
     * the question would be unanswerable, and four of them are needed for
     * distractors.
     */
    private val answerable: List<Terpene> =
        pool.filter { it.aroma.isNotBlank() && it.name.isNotBlank() }

    /** The current state. Read-only; every change goes through a transition. */
    var state: TerpeneQuizState = initialState()
        private set

    /**
     * Records the option at [chosenIndex] as the player's pick.
     *
     * Does **not** advance: the reveal stays on the question that was asked
     * until [next] is called. Out-of-range indices and states that are not
     * [TerpeneQuizState.Asking] are rejected.
     */
    fun answer(chosenIndex: Int): TerpeneQuizState {
        val current = state as? TerpeneQuizState.Asking ?: return state
        if (chosenIndex !in current.question.options.indices) return state
        state = TerpeneQuizState.Revealed(
            question = current.question,
            chosenIndex = chosenIndex,
            round = current.round,
            totalRounds = current.totalRounds,
            score = current.score + if (chosenIndex == current.question.correctIndex) 1 else 0
        )
        return state
    }

    /**
     * Advances past the reveal.
     *
     * Rejected while a question is still unanswered, so the game cannot be
     * skipped. The last round lands on [TerpeneQuizState.Finished] directly,
     * synchronously: there is no frame in which the question is null and the
     * dialog body is empty.
     */
    fun next(): TerpeneQuizState {
        val current = state as? TerpeneQuizState.Revealed ?: return state
        state = if (current.isLastRound) {
            TerpeneQuizState.Finished(score = current.score, totalRounds = current.totalRounds)
        } else {
            TerpeneQuizState.Asking(
                question = buildQuestion(answerable, random),
                round = current.round + 1,
                totalRounds = current.totalRounds,
                score = current.score
            )
        }
        return state
    }

    /** Starts a fresh run on round one with a zero score. */
    fun restart(): TerpeneQuizState {
        state = initialState()
        return state
    }

    private fun initialState(): TerpeneQuizState = when {
        totalRounds < 1 -> TerpeneQuizState.Unavailable("La trivia no tiene rondas configuradas.")
        answerable.size < MIN_OPTIONS -> TerpeneQuizState.Unavailable(
            "No hay suficientes terpenos cargados para armar el juego."
        )
        else -> TerpeneQuizState.Asking(
            question = buildQuestion(answerable, random),
            round = 1,
            totalRounds = totalRounds,
            score = 0
        )
    }

    companion object {
        /** Rounds in a standard run. */
        const val DEFAULT_ROUNDS = 5

        /** One correct option plus [OPTIONS_PER_QUESTION] - 1 distractors. */
        const val MIN_OPTIONS = 4

        /** Options offered per question: one correct plus three distractors. */
        const val OPTIONS_PER_QUESTION = 4
    }
}

/**
 * Builds one question: "which terpene smells like X?".
 *
 * Distractors are picked from other answerable terpenes so they are plausible,
 * and the correct option's position is randomised to avoid a learnable pattern.
 * [random] is injected so the shuffle is reproducible in tests.
 *
 * The aroma goes *in the prompt*. It used to say "¿Qué terpeno tiene este aroma?"
 * and then never show one: the text only appeared in [explanation], after the
 * answer was already locked, so the question could not be answered by reading it.
 * The player was picking between four names with nothing to choose from.
 */
internal fun buildQuestion(pool: List<Terpene>, random: Random): TerpeneQuizQuestion {
    val correct = pool[random.nextInt(pool.size)]
    val distractors = pool.asSequence()
        .filter { it.id != correct.id }
        .shuffled(random)
        .take(TerpeneQuiz.OPTIONS_PER_QUESTION - 1)
        .toList()
    val options = (distractors + correct).shuffled(random)
    return TerpeneQuizQuestion(
        prompt = "¿Qué terpeno huele a esto: «${correct.aroma.trim()}»?",
        options = options.map { it.name },
        correctIndex = options.indexOfFirst { it.id == correct.id },
        explanation = "${correct.name}: ${correct.aroma}" +
            (if (correct.formula.isNotBlank()) " (${correct.formula})" else "")
    )
}
