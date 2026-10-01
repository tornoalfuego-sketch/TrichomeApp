package com.trichome.app.model

import kotlin.math.floor

/**
 * Rewards for the Séquito module.
 *
 * ## Why there is no XP table here
 *
 * The app already has one progression system: the `achievements` table, whose
 * `xpReward` the app sums into the player's total. A second ledger inside this
 * module would be a second source of truth for the same number, and the two
 * would drift. So the only reward the Séquito content can grant is an
 * [EntourageAchievement], and the table does the summing.
 *
 * The unlock condition is the module's own: a finished quiz run with at least
 * [QUIZ_THRESHOLD] of the rounds correct. The threshold is a fraction of the
 * rounds rather than a fixed count, so a shorter shipped quiz does not
 * silently make the badge impossible.
 *
 * [labelEs] is named for what it is rather than `name`, which `Enum` already
 * owns: the value here is Spanish display text, while `Enum.name` is the
 * constant's own identifier.
 */
enum class EntourageAchievement(
    val labelEs: String,
    val description: String,
    val icon: String,
    val xpReward: Int
) {
    /**
     * The single badge the module ships.
     *
     * A perfect run is not required: the quiz asks about mechanisms that are
     * genuinely pre-clinical, and demanding 10/10 would reward guessing the
     * ones nobody can reason out.
     */
    ENTOURAGE_MASTER(
        labelEs = "Maestro del Efecto Séquito",
        description = "Acierta 8 de 10 preguntas sobre modulación terpénica",
        icon = "🧬",
        xpReward = 300
    );

    companion object {
        /** Rounds a full run of the shipped quiz has. */
        const val QUIZ_ROUNDS = 10

        /** Correct answers needed, as a fraction of the rounds played. */
        const val QUIZ_THRESHOLD = 0.8f

        /**
         * Whether a quiz run unlocks this achievement.
         *
         * A run that was never finished cannot unlock it, so abandoning the
         * quiz at the last round is not the same as completing it.
         *
         * @param finished true when the run reached its terminal round.
         */
        fun isEarned(score: Int, rounds: Int, finished: Boolean): Boolean {
            if (!finished || rounds < 1) return false
            return score >= thresholdFor(rounds)
        }

        /** How many correct answers the badge needs for a run of [rounds]. */
        fun thresholdFor(rounds: Int): Int =
            floor(rounds.coerceAtLeast(1) * QUIZ_THRESHOLD).toInt().coerceAtLeast(1)
    }
}
