package com.trichome.app.model

import kotlin.math.roundToInt

/**
 * Progression rules for the terpenes encyclopedia.
 *
 * Pure and deterministic so the reward curve can be unit-tested without a
 * device. Experience is awarded for *discovering* a terpene (opening its full
 * card), not for merely scrolling the list, so exploring the encyclopedia is the
 * only way to level up.
 */
object TerpeneProgression {

    /** Experience for opening the detail card of a new terpene. */
    const val XP_PER_DISCOVERY = 20

    /** Extra experience the first time a family is completed. */
    const val XP_PER_FAMILY = 120

    /** Experience for a correct trivia answer. */
    const val XP_PER_QUIZ_CORRECT = 15

    /**
     * Level for a given amount of experience.
     *
     * The curve is quadratic on purpose: the first levels arrive quickly so the
     * feature feels responsive, then each level costs progressively more.
     */
    fun levelFor(xp: Int): Int {
        if (xp < 0) return 1
        var level = 1
        var remaining = xp
        while (remaining >= costFor(level)) {
            remaining -= costFor(level)
            level++
            if (level > 999) break
        }
        return level
    }

    /** Experience needed to go from [level] to the next one. */
    fun costFor(level: Int): Int = 100 + (level - 1) * 50

    /** Experience accumulated inside the current level. */
    fun progressInLevel(xp: Int): Int {
        var remaining = xp.coerceAtLeast(0)
        var level = 1
        while (level < 999 && remaining >= costFor(level)) {
            remaining -= costFor(level)
            level++
        }
        return remaining
    }

    /** Completion of the current level, 0f..1f, for the progress bar. */
    fun levelProgress(xp: Int): Float {
        val cost = costFor(levelFor(xp))
        return (progressInLevel(xp).toFloat() / cost).coerceIn(0f, 1f)
    }

    /** Total experience required to reach [level] from zero. */
    fun totalXpFor(level: Int): Int {
        var total = 0
        for (l in 1 until level) total += costFor(l)
        return total
    }

    /**
     * Badges unlocked by the current state of the encyclopedia.
     *
     * @param discoveredCount how many terpenes have been opened
     * @param familiesCompleted how many chemical families are fully discovered
     * @param favorites how many are marked as favourites
     * @param streak consecutive days of activity
     * @param quizzesCorrect correct trivia answers
     */
    fun badges(
        discoveredCount: Int,
        familiesCompleted: Int = 0,
        favorites: Int = 0,
        streak: Int = 0,
        quizzesCorrect: Int = 0
    ): List<Badge> = buildList {
        add(Badge("🔎", "Primervistazo", "Descubre tu primer terpeno", discoveredCount >= 1, 1))
        add(Badge("🧪", "Analista", "Descubre 10 terpenos", discoveredCount >= 10, 2))
        add(Badge("🧬", "Químico", "Descubre 40 terpenos", discoveredCount >= 40, 3))
        add(Badge("🏆", "Maestro de la Biblia", "Descubre los 150 terpenos", discoveredCount >= 150, 5))
        add(Badge("🌿", "Familia Completa", "Completa una familia química", familiesCompleted >= 1, 3))
        add(Badge("⭐", "Coleccionista", "Marca 5 terpenos como favoritos", favorites >= 5, 2))
        add(Badge("🔥", "Racha Constante", "Actívate 3 días seguidos", streak >= 3, 2))
        add(Badge("🎓", "Olfato Entrenado", "Acierta 20 preguntas", quizzesCorrect >= 20, 4))
    }

    /** Experience needed to reach the next level, for the "next reward" hint. */
    fun xpToNextLevel(xp: Int): Int =
        (totalXpFor(levelFor(xp) + 1) - xp).coerceAtLeast(0)

    /** Rank title shown next to the level. */
    fun rankTitle(level: Int): String = when {
        level >= 20 -> "Doctor en Terpenologia"
        level >= 15 -> "Investigador Senior"
        level >= 10 -> "Quimico de Cannabinoides"
        level >= 7 -> "Analista de Perfiles"
        level >= 5 -> "Tecnico de Extraccion"
        level >= 3 -> "Curioso Novato"
        else -> "Aprendiz"
    }

    /** Rounds to a whole percentage for display. */
    fun percent(value: Float): Int = (value * 100).roundToInt()
}

/** A single unlockable achievement in the encyclopedia. */
data class Badge(
    val emoji: String,
    val name: String,
    val description: String,
    val unlocked: Boolean,
    /** Number of the level tier the badge belongs to, for sorting. */
    val tier: Int
)
