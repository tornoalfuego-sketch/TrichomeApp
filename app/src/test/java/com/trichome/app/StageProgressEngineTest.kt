package com.trichome.app

import com.trichome.app.model.StageProgressEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Stage progress + grow-day semantics. Key contract: created today is
 * **Day 1** (off-by-one fixed), days in current stage are 1-based, and
 * remaining days never go negative.
 */
class StageProgressEngineTest {

    private val day = 86_400_000L

    @Test
    fun `created today is day 1`() {
        val start = 1_700_000_000_000L
        assertEquals(1, StageProgressEngine.daysInGrow(start, start))
    }

    @Test
    fun `one day later is day 2`() {
        val start = 1_700_000_000_000L
        assertEquals(2, StageProgressEngine.daysInGrow(start, start + day))
    }

    @Test
    fun `almost 24h is still day 1`() {
        val start = 1_700_000_000_000L
        assertEquals(1, StageProgressEngine.daysInGrow(start, start + day - 1))
    }

    @Test
    fun `zero or negative start defaults to day 1`() {
        assertEquals(1, StageProgressEngine.daysInGrow(0L, 1_700_000_000_000L))
    }

    @Test
    fun `empty protocol returns null`() {
        assertNull(
            StageProgressEngine.calculateProgress(
                blocks = emptyList(),
                startTimestamp = 1_700_000_000_000L
            )
        )
    }

    @Test
    fun `first block day one is not finished`() {
        val blocks = listOf(
            StageProgressEngine.StageBlock("Germinación", 7),
            StageProgressEngine.StageBlock("Vegetativa", 35)
        )
        val result = StageProgressEngine.calculateProgress(
            blocks = blocks,
            startTimestamp = 1_700_000_000_000L,
            nowTimestamp = 1_700_000_000_000L
        )
        assertNotNull(result)
        result!!
        assertEquals(0, result.blockIndex)
        assertEquals("Germinación", result.stageName)
        assertEquals(1, result.daysInCurrentStage)
        assertEquals(6, result.daysRemainingInStage)
        assertEquals(42, result.totalCycleDays)
        assertEquals(false, result.isFinished)
    }

    @Test
    fun `second block starts after first completes`() {
        val blocks = listOf(
            StageProgressEngine.StageBlock("Germinación", 7),
            StageProgressEngine.StageBlock("Vegetativa", 35)
        )
        val start = 1_700_000_000_000L
        val result = StageProgressEngine.calculateProgress(
            blocks = blocks,
            startTimestamp = start,
            nowTimestamp = start + 7 * day
        )
        assertNotNull(result)
        result!!
        assertEquals(1, result.blockIndex)
        assertEquals("Vegetativa", result.stageName)
        assertEquals(1, result.daysInCurrentStage)
        assertEquals(34, result.daysRemainingInStage)
    }

    @Test
    fun `finished when elapsed passes total`() {
        val blocks = listOf(
            StageProgressEngine.StageBlock("Vegetativa", 30),
            StageProgressEngine.StageBlock("Floración", 60)
        )
        val start = 1_700_000_000_000L
        val result = StageProgressEngine.calculateProgress(
            blocks = blocks,
            startTimestamp = start,
            nowTimestamp = start + 90 * day
        )
        assertNotNull(result)
        result!!
        assertEquals(true, result.isFinished)
        assertEquals(0, result.daysRemainingInStage)
    }

    @Test
    fun `next occurrences respect interval and count`() {
        val first = 1_700_000_000_000L
        val occurrences = StageProgressEngine.nextOccurrences(first, 7, 3)
        assertEquals(3, occurrences.size)
        assertEquals(first, occurrences[0])
        assertEquals(first + 7 * day, occurrences[1])
        assertEquals(first + 14 * day, occurrences[2])
    }
}