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
 * a second, drifting copy of the asset's question count.
 *
 * **F5 removed that constant, and the no-argument [description] with it.** F5
 * added questions to the asset, and the moment it did, `QUIZ_ROUNDS = 10` beside a
 * sixteen-question file was exactly the drift F1's own KDoc warned about: a
 * hand-maintained copy of a number the asset already holds. There is deliberately
 * **no** zero-argument reading left to reach for, on the same grounds F4 gave for
 * `RESIN_ENGINEER` — every caller is handed the count it actually played.
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
     * The quiz badge, unlocked by a good run.
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
    ),

    /**
     * F5's badge: the Lab, unlocked by having played every shipped case.
     *
     * ## What it honestly claims
     *
     * It claims a **verdict in every case the asset ships**, which is a fact the
     * `achievements` table already records: `EntourageRewards.forLabVerdict`
     * writes one row per case, named `Séquito: <título del caso>`, and writes
     * nothing at all for [LabVerdict.INEFICAZ]. So the condition reads a set of
     * names that is already persisted rather than inventing a second
     * "have I opened this" ledger — which F4 refused to create for the resin
     * badge and which would be a second source of truth for the same fact.
     *
     * The wording is deliberately **not** "resuelve": a case answered with
     * [LabVerdict.RIESGO] also pays, so the row's existence proves a verdict was
     * obtained, not that it was the best one. The sentence says what it can prove.
     *
     * It cannot exist before F5's own case ships. Before this phase the Lab had
     * three cases; the count is derived from the parsed case list for the same
     * reason [descriptionFor] takes its count as an argument.
     */
    TERPENE_ALCHEMIST(
        labelEs = "Alquimista de Terpenos",
        icon = "⚖️",
        xpReward = 400
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

    /**
     * F5: the terpene alchemist's description, derived from the shipped case list.
     *
     * Same discipline as the two above, for the same reason: the condition is a
     * **count of the cases the asset ships**, so a literal beside it would drift
     * the first time a case is added or removed — and it would drift in the one
     * place the player cannot check it, because the row is written once into the
     * `achievements` table and lives there forever.
     *
     * Deliberately distinct in name from [descriptionFor] and
     * [descriptionForProcessing] for the reason F4 gave: two of the three take
     * different data, so a shared signature would let a caller holding the wrong
     * badge compile and write the wrong sentence into the row.
     *
     * @param shippedCases how many cases the parsed asset resolves. Zero still
     *   gets a sentence; no caller renders an empty one.
     */
    fun descriptionForCases(shippedCases: Int): String =
        "Obtén un veredicto en los $shippedCases casos del Laboratorio"

    companion object {
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
