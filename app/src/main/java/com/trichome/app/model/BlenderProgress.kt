package com.trichome.app.model

import kotlin.math.roundToInt

/**
 * The Master Blender's progression, written into the **existing** `achievements`
 * table.
 *
 * ## There is no second progression system here, on purpose
 *
 * The app already has one: the `achievements` table, whose `xpReward` column is
 * summed into the player's total, and [Gamification.levelFromXp], which turns
 * that total into a level. A blender-specific counter, a blender-specific level
 * ladder and a blender-specific "runs played" table would each be a second
 * source of truth for a number the app already owns, and the two would drift the
 * first time a journal entry moved the player up a level.
 *
 * So every decision here is a *derivation* from those two facts:
 *
 *  - the XP a run pays is a function of the run's own score and size;
 *  - the level is [Gamification.levelFromXp] applied to the player's total,
 *    **not** a blender-level ladder;
 *  - the history is the set of rows the blender has written, one per level it has
 *    been the thing that pushed the player to.
 *
 * ## Why XP is paid on a level crossing, and not on every run
 *
 * `Achievement.id` is autoincrementing and there is no unique index on `name`, so
 * the name is the only identity a row has — and `AchievementDao`'s
 * `insertAchievementIfAbsent` enforces it in SQL. A row named after the *run*
 * would therefore be written once and never again, and a row named after the
 * *level* is written exactly once, at the crossing. Since XP is paid by writing a
 * row, XP that were paid on every run would need a new name every run, which is
 * the duplicate-row defect F4 recorded having already shipped once (+600 XP for
 * one badge).
 *
 * So: **a run pays when it moves the player to a new level, and the row it
 * writes is that level.** A run that does not move the level reports its score
 * on screen and pays nothing, which is also the honest game design — repeating
 * the same comparison is not progress.
 *
 * ## The row text is derived, never a literal
 *
 * [descriptionFor] builds the sentence from the level, the percentage, the
 * distance and the compound count — the four things the run actually produced.
 * There is no constant beside it to fall out of date, for the reason F1 removed
 * the hardcoded "Acierta 8 de 10" and F4 removed `QUIZ_ROUNDS`: the sentence
 * lives in the database forever and the reader has no way to check it.
 */
object BlenderProgress {

    /** Prefix every row this object writes carries. */
    const val ROW_PREFIX_ES: String = "Master Blender: "

    /** The glyph stored on the row. Emoji because the column is text, not a vector. */
    const val ROW_ICON: String = "⚗️"

    /**
     * XP per whole percentage point of a run.
     *
     * A perfect run over the full [TerpeneBlender.DEFAULT_SLIDERS] compounds
     * therefore pays 100 XP — two "journal event" registrations' worth, which is
     * the right order of magnitude for a run that takes a minute and reads eight
     * sliders. The constant is a *rate*, not a total: the total is always
     * `XP_PER_PERCENT * percent * difficulty`, and no code path restates it.
     */
    const val XP_PER_PERCENT: Int = 1

    /**
     * The least a scored run pays.
     *
     * Non-zero because a scored run produced a comparison against the
     * reference — that is work — and the run's own percentage is already the
     * dominant term. What this floor must never become is a payment for a mix
     * that was not scored: [NO_REFERENCE] and `NO_SELECTION` pay nothing at all,
     * because there is no reading behind them.
     */
    const val MIN_SCORED_XP: Int = 5

    /** "Master Blender · nivel 3" — the [ROW_PREFIX_ES] every row is named under. */
    fun rowName(level: Int): String = "$ROW_PREFIX_ES${levelNameEs(level)}"

    /** "nivel 3", built from the level rather than typed per level. */
    fun levelNameEs(level: Int): String = "nivel $level"

    /**
     * The XP a run pays, or zero when there is nothing to pay for.
     *
     * Difficulty scales by the number of compounds compared, because the odds of
     * two random mixes agreeing by chance fall as that number grows — a 90 % match
     * over two compounds is a much weaker claim than a 90 % match over eight, and
     * paying them the same would price the harder one as the easier.
     *
     * @param percent the run's displayed score, 0..100.
     * @param compoundsCompared how many compounds the reading covers.
     */
    fun xpForRun(percent: Int, compoundsCompared: Int): Int {
        if (percent <= 0 || compoundsCompared <= 0) return 0
        val difficulty = compoundsCompared.toFloat() / TerpeneBlender.DEFAULT_SLIDERS
        val raw = percent * XP_PER_PERCENT * difficulty
        return raw.roundToInt().coerceAtLeast(MIN_SCORED_XP)
    }

    /**
     * What a run pays, as the rows it writes.
     *
     * Empty when the run cannot be scored, and empty when it does not move the
     * player to a new level. Both are silent, not errors: a player who mixes the
     * same thing twice has done something legitimate and earned nothing, and the
     * screen already told them the score.
     *
     * @param precision the run's unrounded reading.
     * @param totalXpBefore the player's total **before** this run. Read from the
     *   same table the app sums, not from a blender-local counter.
     * @return zero or one reward. Never more: a run crosses exactly one level
     *   boundary or none, so two rows for one run would be a counting error.
     */
    fun rewardsForRun(
        precision: BlendPrecision,
        totalXpBefore: Int,
        percent: Int = -1
    ): List<EntourageReward> {
        if (precision.isEmpty) return emptyList()

        val score = if (percent >= 0) percent else (precision.similarity * 100f).roundToInt()
        val xp = xpForRun(score, precision.compoundsCompared)
        if (xp <= 0) return emptyList()

        val levelBefore = Gamification.levelFromXp(totalXpBefore)
        val levelAfter = Gamification.levelFromXp(totalXpBefore + xp)
        // `levelFromXp` is monotonic, so this is a boundary rather than a
        // comparison: a run that does not cross one is worth zero rows.
        if (levelAfter <= levelBefore) return emptyList()

        return listOf(
            EntourageReward(
                nameEs = rowName(levelAfter),
                descriptionEs = descriptionFor(levelAfter, score, precision),
                icon = ROW_ICON,
                xpReward = xp
            )
        )
    }

    /**
     * The row's sentence, built from the run that earned it.
     *
     * Four derived numbers and no adjective: the level reached, the percentage,
     * the distance, and how many compounds the reading covers. The distance is
     * marked `≈` because it is calculated from vectors the player set by hand,
     * and the compound count is on screen because the same percentage means
     * different things over two compounds and over eight.
     *
     * Deliberately says nothing about whether the mix was good. A run that
     * matched the reference in two compounds and a run that matched it in eight
     * both wrote a row, and both rows read as what they are: a distance that was
     * recorded.
     */
    fun descriptionFor(level: Int, percent: Int, precision: BlendPrecision): String =
        "Alcanzaste el ${levelNameEs(level)} con una coincidencia del $percent % " +
            "(distancia ${precision.distanceEs}) sobre " +
            "${precision.compoundsCompared} " +
            if (precision.compoundsCompared == 1) "compuesto" else "compuestos"

    /**
     * The rewards not yet paid.
     *
     * A **pre-filter, not the guarantee.** It saves a pointless insert on a
     * recomposition; the guarantee that two coroutines cannot both write the row
     * is `AchievementDao.insertAchievementIfAbsent`'s `WHERE NOT EXISTS`, in SQL.
     * F4 recorded what a caller-side check is worth: a hopeful version of the
     * same guarantee, and four duplicate badges shipped while one existed.
     *
     * @param awardedNames every name already in the `achievements` table.
     */
    fun pending(rewards: List<EntourageReward>, awardedNames: Set<String>): List<EntourageReward> =
        rewards.filter { it.nameEs !in awardedNames }

    /**
     * The history line: the levels the blender has rows for, ascending.
     *
     * Read off the rows themselves rather than from a stored list, so the history
     * on screen cannot disagree with the table that holds it. A player with no
     * rows gets [NO_HISTORY_ES] rather than an empty line.
     */
    fun historyEs(achievementNames: Set<String>): String {
        val levels = achievementNames
            .filter { it.startsWith(ROW_PREFIX_ES) }
            .mapNotNull { name ->
                name.removePrefix(ROW_PREFIX_ES).trim().removePrefix("nivel ").toIntOrNull()
            }
            .distinct()
            .sorted()

        if (levels.isEmpty()) return NO_HISTORY_ES
        return levels.joinToString(" · ") { levelNameEs(it) }
    }

    /** What the history reads before the player has earned a row. */
    const val NO_HISTORY_ES: String = "Todavía no hay niveles registrados."

    /** Headline over the history line. */
    const val HISTORY_HEADING_ES: String = "Historial de niveles"

    /** Headline over the precision readout. */
    const val PRECISION_HEADING_ES: String = "Precisión de la coincidencia"

    /** Row label for the distance. */
    const val DISTANCE_LABEL_ES: String = "Distancia a la referencia"

    /** Row label for the similarity. */
    const val SIMILARITY_LABEL_ES: String = "Similitud"

    /** Row label for the player's level. */
    const val LEVEL_LABEL_ES: String = "Nivel"

    /** Row label for the XP this run pays. */
    const val XP_LABEL_ES: String = "XP de este intento"

    /**
     * What the precision panel shows before there is a reading.
     *
     * A blank `≈ —` under a heading called "Precisión" would read as a computed
     * value of nothing; this says there is nothing to compute yet.
     */
    const val PRECISION_EMPTY_ES: String =
        "Mueve al menos un compuesto para leer la distancia."

    /**
     * The XP row's value.
     *
     * Says what the run *earned*, never what it *paid*, and the difference is the
     * whole point of [recordLabelEs]: a run that scores and does not move the
     * player to a new level has earned XP that no row carries, so printing a
     * bare total next to a disabled "Registrar nivel" button would read as a
     * payment the app refused to make.
     */
    fun xpLabelEs(xp: Int): String = when {
        xp <= 0 -> "0 XP · sin lectura"
        else -> "+$xp XP · ganados"
    }

    /**
     * The record button's label, which names what the press does.
     *
     * Two labels rather than one so the control announces its own state: a
     * screen-reader user hears "Registrar nivel" whether or not there is anything
     * to record, and a sighted user can tell a disabled control from an enabled
     * one without inferring it from a greyed icon.
     */
    fun recordLabelEs(enabled: Boolean): String =
        if (enabled) "Registrar nivel" else "Nivel ya registrado"

    /**
     * The line above the XP readout.
     *
     * Says the level is the app's own, because a player who registers a journal
     * event and sees their level move has just been told something false by a
     * screen that implied the blender moved it.
     */
    const val LEVEL_NOTE_ES: String =
        "El nivel es el de la app: suma el registro en bitácora y este mezclador."
}