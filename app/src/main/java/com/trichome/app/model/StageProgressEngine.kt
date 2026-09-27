package com.trichome.app.model

import kotlin.math.floor

/**
 * Stage-progress engine: maps a plant's chronological position inside a
 * protocol made of ordered blocks (stages) to human metrics:
 * days elapsed in the current stage, days remaining, total expected cycle days.
 */
object StageProgressEngine {

    data class StageBlock(
        val stageName: String,
        val durationDays: Int
    )

    data class StageProgress(
        val blockIndex: Int,
        val stageName: String,
        val daysInCurrentStage: Int,     // 1-based: entering today counts as day 1
        val daysRemainingInStage: Int,
        val totalCycleDays: Int,
        val overallDaysElapsed: Int,
        val overallProgress: Float,      // 0..1 across the whole protocol
        val isFinished: Boolean
    )

    /**
     * @param startTimestamp plant grow start (millis)
     * @param nowTimestamp reference "now" (millis)
     */
    fun calculateProgress(
        blocks: List<StageBlock>,
        startTimestamp: Long,
        nowTimestamp: Long = System.currentTimeMillis()
    ): StageProgress? {
        if (blocks.isEmpty()) return null

        val totalDays = blocks.sumOf { it.durationDays.coerceAtLeast(0) }
        if (totalDays <= 0) return null

        val elapsedMillis = (nowTimestamp - startTimestamp).coerceAtLeast(0L)
        val elapsedDays = floor(elapsedMillis / 86_400_000.0).toInt()

        // Position: consumed days
        var consumed = 0
        blocks.forEachIndexed { index, block ->
            val duration = block.durationDays.coerceAtLeast(0)
            if (elapsedDays < consumed + duration || index == blocks.size - 1) {
                // 1-based stage day: entering today counts as Day 1. Remaining
                // is what's left *after* today, so day 1 of 7 => 6 remaining.
                val daysInStage =
                    if (duration == 0) 1 else (elapsedDays - consumed).coerceIn(0, duration - 1) + 1
                val remaining = (duration - daysInStage).coerceAtLeast(0)
                val overallProgress = if (totalDays > 0) {
                    (elapsedDays.toFloat() / totalDays).coerceIn(0f, 1f)
                } else 0f
                return StageProgress(
                    blockIndex = index,
                    stageName = block.stageName,
                    daysInCurrentStage = daysInStage,
                    daysRemainingInStage = remaining,
                    totalCycleDays = totalDays,
                    overallDaysElapsed = elapsedDays,
                    overallProgress = overallProgress,
                    isFinished = elapsedDays >= totalDays
                )
            }
            consumed += duration
        }
        return null
    }

    /**
     * Days on grow, 1-based (created today = Day 1). Semantic fixed for
     * the off-by-one bug reported on PlantDetailScreen.
     */
    fun daysInGrow(startTimestamp: Long, nowTimestamp: Long = System.currentTimeMillis()): Int {
        if (startTimestamp <= 0L) return 1
        val elapsed = (nowTimestamp - startTimestamp).coerceAtLeast(0L)
        return (elapsed / 86_400_000).toInt() + 1
    }

    /** Next N scheduled dates for a recurring reminder starting at [firstRun]. */
    fun nextOccurrences(firstRun: Long, intervalDays: Int, count: Int = 5): List<Long> {
        if (intervalDays <= 0) return emptyList()
        return (0 until count).map { firstRun + it.toLong() * intervalDays * 86_400_000L }
    }
}