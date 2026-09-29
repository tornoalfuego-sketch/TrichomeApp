package com.trichome.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Progression curve, badge unlock rules and the shipped catalog's integrity. */
class TerpeneProgressionTest {

    @Test
    fun levelNeverDecreasesAsExperienceGrows() {
        var previous = 0
        for (xp in 0..4000 step 20) {
            val level = TerpeneProgression.levelFor(xp)
            assertTrue("level dropped at xp=$xp ($level < $previous)", level >= previous)
            previous = level
        }
    }

    @Test
    fun levelProgressStaysWithinZeroAndOne() {
        for (xp in listOf(0, 1, 19, 20, 137, 1500, 99_999)) {
            val p = TerpeneProgression.levelProgress(xp)
            assertTrue("levelProgress($xp) out of range: $p", p in 0f..1f)
        }
    }

    @Test
    fun nextLevelCostGrowsWithLevel() {
        var previous = -1
        for (level in 1..25) {
            val cost = TerpeneProgression.costFor(level)
            assertTrue("costFor($level)=$cost should exceed $previous", cost > previous)
            previous = cost
        }
    }

    @Test
    fun rankTitlesFormOrderedBands() {
        val titles = (1..12).map { TerpeneProgression.rankTitle(it) }

        titles.forEach { assertTrue("rank title must not be blank", it.isNotBlank()) }
        // Titles are named bands that span consecutive levels, so the contract
        // is a step function, not a title per level: a band may repeat, but it
        // must never come back once it has been left.
        assertTrue("expected several distinct rank bands, got ${titles.toSet()}", titles.toSet().size >= 3)

        val seenBands = mutableSetOf<String>()
        val leftBands = mutableSetOf<String>()
        titles.forEachIndexed { i, title ->
            if (title in leftBands) {
                throw AssertionError(
                    "level ${i + 1} returns to band '$title', which was already left behind"
                )
            }
            if (i > 0 && titles[i - 1] != title) {
                leftBands.add(titles[i - 1])
            }
            seenBands.add(title)
        }
    }

    @Test
    fun discoveryAwardsExperienceThatAccumulatesIntoProgress() {
        // A single discovery is meant to fill a slice of the first level, not to
        // promote immediately, so the contract is progress, not a level jump.
        val firstLevel = TerpeneProgression.levelFor(0)
        val afterOne = TerpeneProgression.levelFor(TerpeneProgression.XP_PER_DISCOVERY)

        assertEquals("the first level should still be level 1", firstLevel, afterOne)
        assertTrue(
            "a discovery must move the bar, progress was ${TerpeneProgression.levelProgress(TerpeneProgression.XP_PER_DISCOVERY)}",
            TerpeneProgression.levelProgress(TerpeneProgression.XP_PER_DISCOVERY) > 0f
        )
        assertTrue(
            "experience must be worth something",
            TerpeneProgression.xpToNextLevel(0) > 0
        )
    }

    @Test
    fun badgesUnlockWithProgressAndStayLockedWithoutIt() {
        val none = TerpeneProgression.badges(BadgeCounters())
        val many = TerpeneProgression.badges(
            BadgeCounters(
                discoveredCount = 40,
                familiesCompleted = 5,
                favorites = 3,
                bestStreak = 7,
                quizzesCorrect = 12
            )
        )

        val unlockedAtStart = none.count { it.unlocked }
        val unlockedLater = many.count { it.unlocked }

        assertTrue("no progress should unlock only the starter badge", unlockedAtStart in 0..1)
        assertTrue("real progress should unlock more badges", unlockedLater > unlockedAtStart)
        assertNotEquals(many.size, 0)
    }
}
