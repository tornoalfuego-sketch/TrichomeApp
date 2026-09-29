package com.trichome.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Translucency of a glass panel, derived from the user "opacity" preference.
 *
 * The preference range is 0.05–0.55, but a panel alpha below ~0.35 makes the
 * text unreadable against the animated background, so the raw value is remapped
 * onto a legible window instead of being used verbatim.
 */
fun panelAlphaFor(glassOpacity: Float): Float =
    (0.35f + glassOpacity.coerceIn(0f, 1f) * 1.05f).coerceIn(0.40f, 0.94f)

/**
 * Glassmorphism container: a translucent, tinted surface with a gradient edge
 * highlight that simulates a glass bevel.
 *
 * IMPORTANT: this composable deliberately does **not** apply a `blur` modifier.
 * In Compose, `Modifier.blur` renders the whole subtree of the node into a
 * blurred layer, so putting it on a card destroys the text. A true backdrop
 * blur is not expressible with a `Modifier.blur` on a panel; the frosted depth
 * is instead produced by the tinted surface plus the edge gradient, which keeps
 * every label legible at any blur preference.
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
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(cornerRadius.dp)
    val alpha = panelAlphaFor(glassOpacity)

    // A shallower tint means less depth: nudge the panel towards the background
    // colour so the transparency is still perceivable without losing contrast.
    val depthFactor = (blurRadius / 32f).coerceIn(0f, 1f)
    val panelTop = lerpColor(scheme.surface, scheme.surfaceVariant, depthFactor * 0.6f)
    val panelBottom = lerpColor(scheme.surface, scheme.background, depthFactor * 0.35f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        panelTop.copy(alpha = alpha),
                        panelBottom.copy(alpha = alpha)
                    )
                ),
                shape = shape
            )
            .then(
                if (borderEnabled) {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.75f),
                                accentColor.copy(alpha = 0.18f),
                                accentColor.copy(alpha = 0.75f)
                            )
                        ),
                        shape = shape
                    )
                } else Modifier
            )
    ) {
        // Top inner highlight: the specular reflection of a glass edge.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (scheme.background.luminance() < 0.5f) 0.07f else 0.0f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 120f
                    )
                )
        )
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            content = content
        )
    }
}

/** Linear interpolation between two opaque colours. */
private fun lerpColor(from: Color, to: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * f,
        green = from.green + (to.green - from.green) * f,
        blue = from.blue + (to.blue - from.blue) * f,
        alpha = 1f
    )
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