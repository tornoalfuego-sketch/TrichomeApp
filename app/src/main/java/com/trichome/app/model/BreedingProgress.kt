package com.trichome.app.model

/**
 * The breeding progression, as persisted.
 *
 * **Not** the terpene progression and **not** the Room-backed grow-event
 * progression. All three count things and all three could be called "progress",
 * so they are kept in separate stores with separate keys: a terpene streak and a
 * breeding medal are different achievements that happen to share a word, and
 * reading one out of the other would make one activity silently revoke the
 * other's rewards.
 *
 * Medals are stored as their ids rather than as objects so a build that drops a
 * tier still reads what is already banked; unknown ids are ignored rather than
 * guessed at, which is what keeps a downgrade from crashing or from
 * reinterpreting one medal as another.
 */
data class BreedingProgress(
    /** Chapter ids with every tier banked. */
    val completedChapters: Set<String> = emptySet(),
    /** Medal ids, `chapterId:tierId`. Additive; never rewritten by a worse score. */
    val earnedMedals: Set<String> = emptySet(),
    /** Whole quiz attempts, including failed ones. */
    val quizAttempts: Int = 0,
    /** Correct answers across all attempts. */
    val correctAnswers: Int = 0
) {
    /** Medals of a chapter, best last. Unreadable ids are dropped. */
    fun medalsEarnedIn(chapterId: String): List<BreedingMedal> =
        earnedMedals.mapNotNull(::parseBreedingMedalId)
            .filter { it.chapterId == chapterId }
            .sortedBy { it.tier.ordinal }

    /** The highest tier banked on a chapter, or null if none. */
    fun bestTierIn(chapterId: String): BreedingMedalTier? =
        medalsEarnedIn(chapterId).lastOrNull()?.tier

    /** Every readable medal, grouped by chapter. */
    val chapterMedals: Map<String, List<BreedingMedal>>
        get() = earnedMedals.mapNotNull(::parseBreedingMedalId)
            .groupBy({ it.chapterId }, { it })

    /** How many medals are actually readable; unparseable ids are not counted. */
    val totalMedals: Int get() = earnedMedals.count { parseBreedingMedalId(it) != null }
}
