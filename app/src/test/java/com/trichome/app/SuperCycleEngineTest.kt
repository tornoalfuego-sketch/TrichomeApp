package com.trichome.app

import com.trichome.app.model.Phase
import com.trichome.app.model.SuperCycleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Super-cycle math: T = light + dark, superday is 1-based, phase PHASE switch
 * at lightHours boundary, phaseProgress within phase.
 */
class SuperCycleEngineTest {

    private val hour = 3_600_000L

    @Test
    fun `cycle start lands in light phase on day 1`() {
        val start = 1_700_000_000_000L
        val result = SuperCycleEngine.calculateSuperCycle(
            nowTimestamp = start,
            cycleStartAt = start,
            lightHours = 18,
            darkHours = 6
        )
        assertEquals(1, result.superday)
        assertEquals(Phase.LIGHT, result.phase)
        assertEquals(0f, result.phaseProgress, 0.001f)
        assertEquals(100f, result.phasePercentage, 0.001f)
        assertEquals(18, result.hoursRemainingInPhase)
        assertEquals(24, result.totalCycleHours)
    }

    @Test
    fun `dark phase begins after lightHours`() {
        val start = 1_700_000_000_000L
        val now = start + 18 * hour + 1L
        val result = SuperCycleEngine.calculateSuperCycle(
            nowTimestamp = now,
            cycleStartAt = start,
            lightHours = 18,
            darkHours = 6
        )
        assertEquals(1, result.superday)
        assertEquals(Phase.DARK, result.phase)
        // 1 millis into the 6h dark phase
        assertEquals(0f, result.phaseProgress, 0.001f)
    }

    @Test
    fun `superday increments every full cycle`() {
        val start = 1_700_000_000_000L
        val now = start + 24 * hour // exactly one full 18/6 cycle
        val result = SuperCycleEngine.calculateSuperCycle(
            nowTimestamp = now,
            cycleStartAt = start,
            lightHours = 18,
            darkHours = 6
        )
        assertEquals(2, result.superday)
        assertEquals(Phase.LIGHT, result.phase)
    }

    @Test
    fun `12-12 after eleven hours is still light`() {
        val start = 1_700_000_000_000L
        val result = SuperCycleEngine.calculateSuperCycle(
            nowTimestamp = start + 11 * hour,
            cycleStartAt = start,
            lightHours = 12,
            darkHours = 12
        )
        assertEquals(Phase.LIGHT, result.phase)
        assertTrue(result.hoursRemainingInPhase in 1..12)
    }

    @Test
    fun `24-0 never enters dark phase`() {
        val start = 1_700_000_000_000L
        val result = SuperCycleEngine.calculateSuperCycle(
            nowTimestamp = start + 100 * hour,
            cycleStartAt = start,
            lightHours = 24,
            darkHours = 0
        )
        assertEquals(Phase.LIGHT, result.phase)
        assertEquals(24, result.totalCycleHours)
    }

    @Test
    fun `invalid config returns OFF phase`() {
        val result = SuperCycleEngine.calculateSuperCycle(
            cycleStartAt = 1_700_000_000_000L,
            lightHours = 0,
            darkHours = 0
        )
        assertEquals(Phase.OFF, result.phase)
        assertEquals(0, result.superday)
    }

    @Test
    fun `preset name detection`() {
        assertEquals("18/6", SuperCycleEngine.getPresetName(18, 6))
        assertEquals("12/12", SuperCycleEngine.getPresetName(12, 12))
        assertEquals("24/0", SuperCycleEngine.getPresetName(24, 0))
        assertEquals("custom", SuperCycleEngine.getPresetName(20, 4))
    }
}