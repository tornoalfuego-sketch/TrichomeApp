package com.trichome.app

import com.trichome.app.model.EventType
import com.trichome.app.model.Gamification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** XP thresholds, level curve and registration streak logic. */
class GamificationTest {

    @Test
    fun `xp for harvest is the highest single event`() {
        val harvest = Gamification.xpForEvent(EventType.HARVEST)
        EventType.entries.forEach { other ->
            if (other != EventType.HARVEST) {
                assertTrue("$other should give less XP than harvest", harvest >= Gamification.xpForEvent(other))
            }
        }
        assertEquals(50, harvest)
    }

    @Test
    fun `level one at zero xp`() {
        assertEquals(1, Gamification.levelFromXp(0))
        assertEquals(1, Gamification.levelFromXp(99))
    }

    @Test
    fun `level grows with xp`() {
        assertEquals(2, Gamification.levelFromXp(100))
        assertTrue(Gamification.levelFromXp(10_000) > Gamification.levelFromXp(1_000))
    }

    @Test
    fun `xp into level never exceeds threshold`() {
        listOf(0, 100, 250, 1000, 5000).forEach { xp ->
            val level = Gamification.levelFromXp(xp)
            val into = Gamification.xpIntoLevel(xp)
            val thresholds = Gamification.xpForPreviousLevelsTotal(level + 1) - Gamification.xpForPreviousLevelsTotal(level)
            assertTrue("$xp xp: $into should be < $thresholds", into < thresholds)
        }
    }

    @Test
    fun `streak counts consecutive days with registrations`() {
        val today = 20_000L
        val days = setOf(today - 2, today - 1, today)
        assertEquals(3, Gamification.currentStreak(days, today))
    }

    @Test
    fun `streak survives a missing today when yesterday was active`() {
        val today = 20_000L
        val days = setOf(today - 3, today - 2, today - 1)
        assertEquals(3, Gamification.currentStreak(days, today))
    }

    @Test
    fun `streak breaks on a gap`() {
        val today = 20_000L
        val days = setOf(today - 5, today - 4, today - 1)
        assertEquals(1, Gamification.currentStreak(days, today))
    }

    @Test
    fun `empty registrations give zero streak`() {
        assertEquals(0, Gamification.currentStreak(emptySet(), 20_000L))
    }
}