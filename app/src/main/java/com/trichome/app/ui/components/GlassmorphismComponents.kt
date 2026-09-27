package com.trichome.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

/**
 * Glassmorphism container: translucent surface + dynamic blur + 1dp gradient
 * border that simulates a glass edge highlight.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glassOpacity: Float = 0.15f,
    blurRadius: Float = 8f,
    accentColor: Color = Color(0xFF66BB6A),
    contentColor: Color = Color.White,
    borderEnabled: Boolean = true,
    cornerRadius: Int = 16,
    content: @Composable () -> Unit
) {
    val base = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.Black else Color.White
    val shape = RoundedCornerShape(cornerRadius.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(color = base.copy(alpha = glassOpacity), shape = shape)
            .blur(if (blurRadius > 0f) blurRadius.dp else 0.dp)
            .then(
                if (borderEnabled) {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.55f),
                                accentColor.copy(alpha = 0.08f),
                                accentColor.copy(alpha = 0.55f)
                            )
                        ),
                        shape = shape
                    )
                } else Modifier
            )
    ) {
        content()
    }
}

/**
 * Animated background with floating gradient orbs drifting behind panels.
 */
@Composable
fun FloatingOrbBackground(
    modifier: Modifier = Modifier,
    accentColor1: Color = Color(0xFF66BB6A),
    accentColor2: Color = Color(0xFFFFC107),
    orbCount: Int = 6
) {
    val infinite = rememberInfiniteTransition(label = "orb")
    val time = infinite.animateFloat(
        initialValue = 0f,
        targetValue = 6283f, // ~2π * 1000
        animationSpec = infiniteRepeatable(
            animation = tween(40_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_time"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        repeat(orbCount) { index ->
            val phase = index * 1.7f
            val t = time.value / 1000f
            val cx = w * (0.5f + 0.38f * sin(t * 0.21f + phase))
            val cy = h * (0.5f + 0.36f * sin(t * 0.17f + phase * 1.3f))
            val radius = size.minDimension * (0.16f + 0.05f * sin(t * 0.31f + index))

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor1.copy(alpha = 0.16f),
                        accentColor2.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = radius
                ),
                radius = radius,
                center = Offset(cx, cy)
            )
        }
    }
}

/**
 * Glass slider used for appearance settings.
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    accentColor: Color = Color(0xFF66BB6A),
    modifier: Modifier = Modifier
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = accentColor,
            activeTrackColor = accentColor,
            inactiveTrackColor = accentColor.copy(alpha = 0.2f),
            activeTickColor = accentColor.copy(alpha = 0.6f)
        )
    )
}

/**
 * Circular progress indicator with a glass/arc style.
 */
@Composable
fun GlassProgressIndicator(
    percentage: Float,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF66BB6A),
    size: Int = 96
) {
    val p = percentage.coerceIn(0f, 1f)
    Canvas(modifier = modifier.size(size.dp)) {
        val stroke = 10f
        val r = (size.dp.toPx() / 2f) - stroke / 2f
        val center = this.center

        drawCircle(
            color = color.copy(alpha = 0.12f),
            radius = r,
            center = center
        )
        drawArc(
            brush = Brush.sweepGradient(listOf(color, color.copy(alpha = 0.25f))),
            startAngle = -90f,
            sweepAngle = 360f * p,
            useCenter = false,
            topLeft = Offset(center.x - r, center.y - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}

/**
 * Formats display strings for duration chips (e.g. "3 h", "1 h 30 min").
 * Fixed formatting: the previous `replace` approach was a no-op.
 */
fun formatTime(totalMinutes: Int): String {
    if (totalMinutes <= 0) return "0 min"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> "${minutes} min"
        minutes == 0 -> "${hours} h"
        else -> "${hours} h ${minutes} min"
    }
}