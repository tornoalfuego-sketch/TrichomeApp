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
 * ## Why the badge text is a function and not a string
 *
 * The unlock condition is a fraction of the rounds played, so both the threshold
 * and the sentence that advertises it have to move with the quiz length. The
 * text used to be a literal — "Acierta 8 de 10 preguntas" — sitting next to a
 * `thresholdFor` that derived the same 8 from `floor(rounds * 0.8)`. Those
 * agreed only while the asset shipped exactly ten questions. Add an eleventh and
 * the badge demands nine while its own description still promises eight; remove
 * two and it demands six of eight while promising eight of ten. Either way the
 * player is told the wrong thing about the only reward in the module.
 *
 * So [descriptionFor] is the single place the sentence is built, from the round
 * count the caller actually played, and no `QUIZ_ROUNDS` constant survives to be
 * a second, drifting copy of the asset's question count. `description` remains as
 * the shipped-quiz reading of the same function.
 *
 * [labelEs] is named for what it is rather than `name`, which `Enum` already
 * owns: the value here is Spanish display text, while `Enum.name` is the
 * constant's own identifier.
 */
enum class EntourageAchievement(
    val labelEs: String,
    val icon: String,
    val xpReward: Int
) {
    /**
     * The single badge the module ships.
     *
     * A perfect run is not required: the quiz asks about mechanisms that are
     * genuinely pre-clinical, and demanding every answer would reward guessing
     * the ones nobody can reason out.
     */
    ENTOURAGE_MASTER(
        labelEs = "Maestro del Efecto Séquito",
        icon = "🧬",
        xpReward = 300
    ),

    /**
     * F4's badge: the processing dimension, unlocked by the resin engineer work.
     *
     * It cannot exist before F4's content does — the condition counts the
     * compounds `entourage_data.json` documents in its `processing` block, so a
     * build without that block would award a badge claiming content that is not
     * there.
     *
     * It writes into the **existing** `achievements` table through
     * `EntourageReward.toAchievementRow` and is deduplicated by name through
     * `EntourageRewards.pending`, exactly like the quiz badge. No second
     * progression system, no new column, no migration.
     */
    RESIN_ENGINEER(
        labelEs = "Ingeniero de Resina",
        icon = "⚗️",
        xpReward = 300
    );

    /**
     * The quiz badge's description for a run of [rounds] rounds.
     *
     * [rounds] is the length the player actually played, which is the parsed
     * asset's question count — not a constant written next to the asset. A run of
     * fewer than one round still gets a sentence, because `thresholdFor` already
     * coerces to at least one and the two must not disagree.
     */
    fun descriptionFor(rounds: Int): String =
        "Acierta ${thresholdFor(rounds)} de $rounds preguntas sobre modulación terpénica"

    /** Description for a full run of the shipped quiz. */
    val description: String get() = descriptionFor(QUIZ_ROUNDS)

    /**
     * F4: the resin engineer's description, derived from the shipped block.
     *
     * The same discipline as [descriptionFor], and for the same reason. This
     * badge's condition is a **count of the compounds the asset documents**, so a
     * literal sentence beside it would drift the first time a compound is added to
     * or removed from `entourage_data.json` — and it would drift in the one place
     * the player cannot check it, because the achievement row is written once and
     * then lives in the `achievements` table forever.
     *
     * It is named differently from [descriptionFor] on purpose. Two functions of the
     * same name and one parameter type would be a silent hazard: a caller holding
     * the wrong badge would still compile and would put a quiz sentence into the
     * resin row. Distinct names make the data source legible at every call site.
     *
     * @param documentedCompounds how many entries the shipped `processing` block
     *   resolves to. Zero still gets a sentence; no caller renders an empty one.
     */
    fun descriptionForProcessing(documentedCompounds: Int): String =
        "Lee el procesado de los $documentedCompounds compuestos que el catálogo documenta"

    companion object {
        /**
         * Rounds a full run of the shipped quiz has.
         *
         * Kept only as the argument for [description]'s reading and asserted
         * against the asset by
         * `EntourageAssetTest.theBadgeTextFollowsTheShippedQuizLength` — which
         * is the whole point: the constant cannot drift from the file without a
         * test failing, and the sentence the player reads is built from
         * [descriptionFor] either way.
         */
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
