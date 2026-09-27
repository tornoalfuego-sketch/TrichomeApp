package com.trichome.app.model

/**
 * Super-cycle engine — pure photoperiod math.
 *
 * Formulas (per spec):
 *   T = lightHours + darkHours           (total cycle period, hours)
 *   Superday = floor((now - start) / T) + 1
 *   Current phase: LIGHT if (elapsed mod T) < lightHours else DARK
 */
object SuperCycleEngine {

    private const val MILLIS_PER_HOUR = 3_600_000.0
    private const val MILLIS_PER_DAY = 86_400_000.0

    fun calculateSuperCycle(
        nowTimestamp: Long = System.currentTimeMillis(),
        cycleStartAt: Long = nowTimestamp,
        lightHours: Int,
        darkHours: Int
    ): SuperCycleResult {
        val totalCycleHours = lightHours + darkHours
        if (totalCycleHours <= 0 || lightHours < 0 || darkHours < 0 || cycleStartAt <= 0) {
            return SuperCycleResult(
                superday = 0, phase = Phase.OFF, phaseProgress = 0f, phasePercentage = 0f,
                hoursRemainingInPhase = 0, calendarDaysElapsed = 0, totalCycleHours = totalCycleHours
            )
        }

        val elapsedMillis = (nowTimestamp - cycleStartAt).coerceAtLeast(0L)
        val elapsedHours = elapsedMillis / MILLIS_PER_HOUR

        val completedCycles = (elapsedHours / totalCycleHours).toInt()
        val superday = completedCycles + 1

        val currentCycleElapsed = elapsedHours - completedCycles * totalCycleHours
        val isLightPhase = currentCycleElapsed < lightHours

        val phase = if (isLightPhase) Phase.LIGHT else Phase.DARK
        val phaseLength = if (isLightPhase) lightHours else darkHours

        // Elapsed time strictly inside the current phase (phase starts at 0),
        // so 1ms into the dark phase gives ~0 progress, not 1.
        val elapsedInPhase = if (isLightPhase) currentCycleElapsed else (currentCycleElapsed - lightHours)

        val phaseProgress = if (phaseLength > 0) (elapsedInPhase / phaseLength).toFloat() else 1f
        val clampedProgress = phaseProgress.coerceIn(0f, 1f)

        val hoursRemaining = (phaseLength - elapsedInPhase).let {
            if (it < 0) 0 else it.toInt()
        }

        val phasePercentage = (1f - clampedProgress) * 100f
        val calendarDaysElapsed = (elapsedMillis / MILLIS_PER_DAY).toInt()

        return SuperCycleResult(
            superday = superday,
            phase = phase,
            phaseProgress = clampedProgress,
            phasePercentage = phasePercentage,
            hoursRemainingInPhase = hoursRemaining,
            calendarDaysElapsed = calendarDaysElapsed,
            totalCycleHours = totalCycleHours
        )
    }

    fun getPresetName(lightHours: Int, darkHours: Int): String = when {
        lightHours == 18 && darkHours == 6 -> "18/6"
        lightHours == 12 && darkHours == 12 -> "12/12"
        lightHours == 24 && darkHours == 0 -> "24/0"
        else -> "custom"
    }
}

enum class Phase { LIGHT, DARK, OFF }

data class SuperCycleResult(
    val superday: Int,
    val phase: Phase,
    val phaseProgress: Float,        // 0..1 within current phase
    val phasePercentage: Float,      // % remaining of current phase
    val hoursRemainingInPhase: Int,
    val calendarDaysElapsed: Int,
    val totalCycleHours: Int
) {
    val isLight get() = phase == Phase.LIGHT
    val isDark get() = phase == Phase.DARK
}