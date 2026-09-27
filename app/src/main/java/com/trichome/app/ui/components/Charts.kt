package com.trichome.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Native Canvas line chart for grow metrics (pH, EC, temp, humidity, height).
 * Draws a shaded polyline through the series with a light grid; no external
 * charting library involved.
 */
@Composable
fun NativeLineChart(
    points: List<Pair<Long, Float>>,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    showDots: Boolean = true
) {
    val p = points.sortedBy { it.first }

    if (p.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(150.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text("Sin datos suficientes", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val minY = p.minOf { it.second }
    val maxY = p.maxOf { it.second }
    val range = (maxY - minY).takeIf { it > 0f } ?: 1f

    Canvas(modifier = modifier.fillMaxWidth().height(170.dp)) {
        val w = size.width
        val h = size.height

        // Grid (4 horizontal lines)
        val gridColor = color.copy(alpha = 0.12f)
        for (i in 0..3) {
            val y = h * i / 3f
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }

        val stepX = w / (p.size - 1).toFloat()
        val path = Path()
        val dotPositions = p.mapIndexed { index, (_, value) ->
            val x = index * stepX
            val y = h - ((value - minY) / range) * (h * 0.86f) - h * 0.07f
            Offset(x, y)
        }

        // Shaded area
        val area = Path().apply {
            moveTo(dotPositions.first().x, h)
            dotPositions.forEach { lineTo(it.x, it.y) }
            lineTo(dotPositions.last().x, h)
            close()
        }
        drawPath(
            path = area,
            color = color.copy(alpha = 0.10f)
        )

        // Polyline
        dotPositions.forEachIndexed { index, offset ->
            if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )

        // Dots
        if (showDots) {
            dotPositions.forEach { offset ->
                drawCircle(color = color, radius = 4f, center = offset)
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        Text("min ${minY}", style = MaterialTheme.typography.labelSmall, color = color)
        Text("max ${maxY}", style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/**
 * Small progress bar used to visualize level progress towards the next level.
 */
@Composable
fun LevelProgressBar(
    currentXp: Int,
    level: Int,
    modifier: Modifier = Modifier
) {
    val prevLevelXp = com.trichome.app.model.Gamification.xpForPreviousLevelsTotal(level)
    val nextLevelXp = com.trichome.app.model.Gamification.xpForPreviousLevelsTotal(level + 1)
    val range = (nextLevelXp - prevLevelXp).coerceAtLeast(1)
    val progress = ((currentXp - prevLevelXp).toFloat() / range).coerceIn(0f, 1f)

    androidx.compose.material3.LinearProgressIndicator(
        progress = { if (progress.isFinite()) progress else 0f },
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    )
}