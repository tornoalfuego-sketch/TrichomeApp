package com.trichome.app.model

import kotlin.math.roundToInt

/**
 * Progression rules for the terpenes encyclopedia.
 *
 * Pure and deterministic so the reward curve can be unit-tested without a
 * device. Experience is awarded for *discovering* a terpene (opening its full
 * card), not for merely scrolling the list, so exploring the encyclopedia is the
 * only way to level up.
 *
 * The total is computed by [totalXp] from a [TerpeneXpSources] snapshot. It used
 * to be written out inline in `TerpenesViewModel` as a two-term expression, and
 * `XP_PER_FAMILY` was declared here but never added to anything — a family
 * completion moved the badge and nothing else. One function, one definition.
 */
object TerpeneProgression {

    /** Experience for opening the detail card of a new terpene. */
    const val XP_PER_DISCOVERY = 20

    /**
     * Extra experience the first time a family is completed.
     *
     * Derived from state rather than incremented on the event: the discovered
     * set only ever grows, so in practice a family pays out exactly once. Worth
     * more than a handful of discoveries, because completing a family means
     * reading every entry in it.
     */
    const val XP_PER_FAMILY = 120

    /** Experience for a correct trivia answer. */
    const val XP_PER_QUIZ_CORRECT = 15

    /**
     * Experience for finishing a whole trivia run, correct answers or not.
     *
     * A perfect run earns 5 x [XP_PER_QUIZ_CORRECT] = 75 from its answers, so a
     * flat 40 keeps accuracy the better route while still paying for showing
     * up. A participation bonus has to be worth less than a perfect game or it
     * rewards guessing.
     */
    const val XP_PER_QUIZ_COMPLETED = 40

    /**
     * Total experience for every award the encyclopedia can grant.
     *
     * ```
     * xp = XP_PER_DISCOVERY     * discovered
     *    + XP_PER_FAMILY        * familiesCompleted
     *    + XP_PER_QUIZ_CORRECT  * quizCorrect
     *    + XP_PER_QUIZ_COMPLETED* quizzesCompleted
     * ```
     *
     * Counters are coerced at zero: DataStore is user-writable storage and a
     * negative or corrupt value must not be able to drain experience.
     */
    fun totalXp(sources: TerpeneXpSources): Int =
        XP_PER_DISCOVERY * sources.discovered.coerceAtLeast(0) +
            XP_PER_FAMILY * sources.familiesCompleted.coerceAtLeast(0) +
            XP_PER_QUIZ_CORRECT * sources.quizCorrect.coerceAtLeast(0) +
            XP_PER_QUIZ_COMPLETED * sources.quizzesCompleted.coerceAtLeast(0)

    /**
     * How many chemical families the grower has fully discovered.
     *
     * "Complete" means every catalogued member of the family is in [discovered]
     * — a partial threshold would be an invented number with nothing behind it,
     * and the badge says "completa una familia". Entries with a blank family are
     * skipped: there is no family to complete. Ids absent from [catalog] count
     * for nothing, so a stale discovered id cannot fake a completion.
     */
    fun completedFamilies(catalog: List<TerpeneFamilyMember>, discovered: Set<String>): Int =
        catalog.asSequence()
            .filter { it.family.isNotBlank() }
            .groupBy { it.family }
            .count { (_, members) -> members.all { it.id in discovered } }

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
     * A badge is unlocked when its requirement is met **or** when its id is
     * already in [earned], the set persisted in the profile. That second clause
     * is what makes badges accumulate: un-starring a terpene, or a gap in the
     * streak, used to silently take a medal away again. `bestStreak` rather than
     * the live streak is what the streak badge reads, for the same reason.
     *
     * @param earned badge ids already banked in the profile.
     */
    fun badges(
        counters: BadgeCounters,
        earned: Set<String> = emptySet()
    ): List<Badge> = buildList {
        fun add(id: TerpeneBadge, met: Boolean) {
            val unlocked = met || id.id in earned
            add(
                Badge(
                    id = id.id,
                    emoji = id.emoji,
                    name = id.label,
                    description = id.description,
                    unlocked = unlocked,
                    tier = id.tier
                )
            )
        }
        add(TerpeneBadge.FIRST_SIGHT, counters.discoveredCount >= 1)
        add(TerpeneBadge.ANALYST, counters.discoveredCount >= 10)
        add(TerpeneBadge.CHEMIST, counters.discoveredCount >= 40)
        add(TerpeneBadge.BIBLE_MASTER, counters.discoveredCount >= 150)
        add(TerpeneBadge.FAMILY_COMPLETE, counters.familiesCompleted >= 1)
        add(TerpeneBadge.COLLECTOR, counters.favorites >= 5)
        add(TerpeneBadge.STREAK, counters.bestStreak >= 3)
        add(TerpeneBadge.TRAINED_NOSE, counters.quizzesCorrect >= 20)
    }

    /**
     * The ids of every badge the player holds, for persisting.
     *
     * Pass the result back as `earned` on the next call so a medal is never
     * revoked by unrelated state moving backwards.
     */
    fun unlockedBadgeIds(
        counters: BadgeCounters,
        earned: Set<String> = emptySet()
    ): Set<String> = badges(counters, earned).filter { it.unlocked }.map { it.id }.toSet()

    /** Experience needed to reach the next level, for the "next reward" hint. */
    fun xpToNextLevel(xp: Int): Int =
        (totalXpFor(levelFor(xp) + 1) - xp).coerceAtLeast(0)

    /** Rank title shown next to the level. */
    fun rankTitle(level: Int): String = when {
        level >= 20 -> "Doctor en Terpenología"
        level >= 15 -> "Investigador Senior"
        level >= 10 -> "Químico de Cannabinoides"
        level >= 7 -> "Analista de Perfiles"
        level >= 5 -> "Técnico de Extracción"
        level >= 3 -> "Curioso Novato"
        else -> "Aprendiz"
    }

    /** Rounds to a whole percentage for display. */
    fun percent(value: Float): Int = (value * 100).roundToInt()
}

/**
 * Every counter the XP ledger and the badges read.
 *
 * A snapshot rather than a pile of parameters, so adding an XP event is a new
 * field here and one new term in [TerpeneProgression.totalXp] — not a fourth
 * argument threaded through a ViewModel.
 */
data class TerpeneXpSources(
    /** Terpenes whose detail card has been opened at least once. */
    val discovered: Int = 0,
    /** Chemical families with every catalogued member discovered. */
    val familiesCompleted: Int = 0,
    /** Correct trivia answers, across all runs. */
    val quizCorrect: Int = 0,
    /** Whole trivia runs played to the last round. */
    val quizzesCompleted: Int = 0
)

/**
 * The only thing [TerpeneProgression.completedFamilies] needs to know about a
 * catalogued terpene: which family it belongs to.
 *
 * Keeping the shape this narrow leaves the reward rules free of any dependency
 * on the data layer, so they stay testable with a three-line fake catalog
 * instead of 158 parsed JSON records.
 */
data class TerpeneFamilyMember(val id: String, val family: String)

/**
 * The raw progress a badge requirement is measured against.
 *
 * [bestStreak] is the longest streak ever reached, not today's streak: the live
 * streak resets to one on a missed day, and a badge must not be revoked for
 * taking a day off.
 */
data class BadgeCounters(
    val discoveredCount: Int = 0,
    val familiesCompleted: Int = 0,
    val favorites: Int = 0,
    val bestStreak: Int = 0,
    val quizzesCorrect: Int = 0
)

/**
 * The shipped badge set.
 *
 * [id] is the persistence key: it is written into the profile, so it must stay
 * stable for the life of the save even if [label] is reworded.
 */
enum class TerpeneBadge(
    val id: String,
    val emoji: String,
    val label: String,
    val description: String,
    val tier: Int
) {
    FIRST_SIGHT("first_sight", "🔎", "Primervistazo", "Descubre tu primer terpeno", 1),
    ANALYST("analyst", "🧪", "Analista", "Descubre 10 terpenos", 2),
    CHEMIST("chemist", "🧬", "Químico", "Descubre 40 terpenos", 3),
    BIBLE_MASTER("bible_master", "🏆", "Maestro de la Biblia", "Descubre los 150 terpenos", 5),
    FAMILY_COMPLETE("family_complete", "🌿", "Familia Completa", "Completa una familia química", 3),
    COLLECTOR("collector", "⭐", "Coleccionista", "Marca 5 terpenos como favoritos", 2),
    STREAK("streak", "🔥", "Racha Constante", "Actívate 3 días seguidos", 2),
    TRAINED_NOSE("trained_nose", "🎓", "Olfato Entrenado", "Acierta 20 preguntas", 4)
}

/** A single unlockable achievement in the encyclopedia. */
data class Badge(
    /** Stable persistence key; see [TerpeneBadge]. */
    val id: String,
    val emoji: String,
    val name: String,
    val description: String,
    val unlocked: Boolean,
    /** Number of the level tier the badge belongs to, for sorting. */
    val tier: Int
)

