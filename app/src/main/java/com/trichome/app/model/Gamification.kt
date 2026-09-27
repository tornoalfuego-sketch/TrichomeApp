package com.trichome.app.model

import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

/**
 * Gamification utilities: XP, levels and registration streaks.
 */
object Gamification {

    /**
     * XP per grow event type (journal registration rewards).
     */
    fun xpForEvent(eventType: EventType): Int = when (eventType) {
        EventType.IRRIGATION -> 10
        EventType.HEIGHT -> 10
        EventType.TEMPERATURE_HUMIDITY -> 10
        EventType.TRICHOME_CHECK -> 20
        EventType.TRAINING -> 15
        EventType.PRUNING -> 15
        EventType.DEFOLIATION -> 15
        EventType.FLUSHING -> 15
        EventType.FERTILIZATION -> 15
        EventType.TRANSPLANT -> 20
        EventType.LAMP_DISTANCE -> 10
        EventType.VPD -> 10
        EventType.PEST_CONTROL -> 25
        EventType.DIAGNOSIS -> 25
        EventType.BREEDING -> 25
        EventType.HARVEST -> 50
    }

    fun levelFromXp(totalXp: Int): Int = floor((totalXp / 100.0).pow(0.65)).toInt() + 1

    fun xpIntoLevel(totalXp: Int): Int = totalXp - xpForPreviousLevels(levelFromXp(totalXp))

    /** Total XP required to reach [level] (sum of all previous thresholds). */
    fun xpForPreviousLevelsTotal(level: Int): Int = xpForPreviousLevels(level.coerceAtLeast(1))

    private fun xpForPreviousLevels(level: Int): Int {
        var acc = 0
        for (l in 1 until level) acc += 100 * l
        return acc
    }

    /**
     * Current streak (consecutive days with at least one registration).
     * @param daysWithRegistrations unique days (epochDay) that had entries, any order.
     */
    fun currentStreak(daysWithRegistrations: Set<Long>, todayEpochDay: Long): Int {
        if (daysWithRegistrations.isEmpty()) return 0
        var streak = 0
        var day = todayEpochDay
        // Today not yet registered keeps the streak alive only if yesterday is part of it.
        while (daysWithRegistrations.contains(day)) {
            streak++
            day--
        }
        // Allow "today missing but yesterday present" => streak survives (pending check-in).
        if (streak == 0 && daysWithRegistrations.contains(todayEpochDay - 1)) {
            streak = 1
            day = todayEpochDay - 2
            while (daysWithRegistrations.contains(day)) {
                streak++
                day--
            }
        }
        return streak
    }
}