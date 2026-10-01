package com.trichome.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.trichome.app.ui.theme.relativeLuminance
import com.trichome.app.ui.theme.MINIMUM_TEXT_CONTRAST
import com.trichome.app.ui.theme.contrastRatio
import com.trichome.app.ui.theme.readableOnStrict

/* ─────────────────────────── Panel chrome ──────────────────────────────── */

/**
 * The hairline a panel draws around itself.
 *
 * It stays a neutral step of the panel's own surface, and the accent is not
 * consulted at all.
 *
 * Two measurements decided that, and both contradict the comfortable assumption:
 *
 * - **The fill cannot do this job.** `surface` against `background` is 1.11:1 on
 *   `Cuidado Nocturno` and 1.26:1 on `Brote Verde`. The panel is nearly the colour
 *   of the page, so removing the edge would make every card vanish into the
 *   background. The border is load-bearing, not decoration.
 * - **The old edge shouted.** `outline` against `surface` measures 3.6:1 on all
 *   three dark themes and 4.5:1 on the light one, while body text sits at 4.5:1
 *   or better. The frame was louder than the content, and with the accent
 *   substituted into it -- a saturated cyan on a near-black panel -- it shouted
 *   louder still, in a second colour that fought the text the user had chosen.
 *
 * So the edge is rebuilt as [EDGE_STEP] of neutral ink over the surface, which
 * measures 1.28-1.40:1 across all four themes: enough to see the edge on a real
 * 1dp line, and well below the text it frames. Structure, not content.
 *
 * Pure and platform-free on purpose: this is a contrast decision, so it has to
 * be assertable from a JVM test instead of being eyeballed on a device.
 *
 * @param surface the whole input. It decides both the *direction* of the step --
 *   white on a dark panel, black on a light one -- and the amount of it, so the
 *   edge is a property of the palette rather than of a scheme role. That is also
 *   why there is no second parameter: the previous `onSurface` was never read,
 *   and threading a colour the caller can override per role through a function
 *   that ignores it is an argument that only looks like control.
 */
fun panelBorderColor(surface: Color): Color {
    val toward = if (relativeLuminance(surface) < 0.2f) Color.White else Color.Black
    return blendToward(toward, surface, EDGE_STEP)
}

/**
 * How far the panel edge steps from the surface.
 *
 * Measured, not guessed: 0.10 lands at 1.25-1.35:1 and 0.12 at 1.32-1.45:1 across
 * the four palettes. 0.11 is the middle of the band and keeps the edge the
 * quietest thing on the screen that is still a line.
 *
 * Private on purpose, so this constant is nobody's contract. What callers and
 * tests can rely on is the *band* it produces, and `OpaqueThemeContrastTest`
 * pins that band from both sides rather than the number behind it. The upper
 * side matters as much as the lower one: pushing this to 0.45 lands the edge at
 * 3.35-4.52:1, which is the loud frame this design replaced, and nothing else in
 * the suite would notice.
 */
private const val EDGE_STEP = 0.11f

private fun blendToward(ink: Color, surface: Color, step: Float): Color = Color(
    red = ink.red * step + surface.red * (1f - step),
    green = ink.green * step + surface.green * (1f - step),
    blue = ink.blue * step + surface.blue * (1f - step),
    alpha = 1f
)

/* ─────────────────────────── Solid panel ──────────────────────────────── */

/**
 * The app's one panel component: an opaque Material 3 surface.
 *
 * Replaces the former translucent card. Three rules it exists to enforce:
 *
 * 1. **The body is never tinted.** It is `colorScheme.surface` at full alpha.
 *    There is no `opacity` parameter to pass, so no call site can reintroduce
 *    translucency, and text painted on top is measured against a colour that
 *    is actually on screen.
 * 2. **The edge carries no colour of its own.** It is [panelBorderColor] over
 *    that same `surface`: a neutral step towards white or black, sized so it
 *    measures 1.28-1.40:1 on every palette. The accent is not accepted here at
 *    all -- an argument that changes nothing is worse than no argument, because
 *    every one of the call sites that passed it read as if the panel were
 *    accent-tinted when it has not been since the edge stopped taking it.
 * 3. **The edge is load-bearing.** `surface` against `background` measures only
 *    1.11:1 on `Cuidado Nocturno` and 1.26:1 on `Brote Verde`, so a card with no
 *    edge sinks into the page and stops being a card. The edge is deliberately
 *    *not* held to the 3:1 of WCAG 1.4.11 -- it is structure, not a state
 *    indicator, and `outline` at 3.6:1 was the frame that made every card shout
 *    over its own text. `OpaqueThemeContrastTest` holds it inside a measured
 *    band instead: visible on every theme, and quiet enough to stay under the
 *    content it frames.
 *
 * @param cornerRadius kept as the call sites spell it, in whole `dp`. It is a
 *   shape, not a translucency knob, so it survives the refactor unchanged.
 * @param contentColor `Color.Unspecified` by default, which is Material 3's own
 *   "let the surface decide" sentinel: content falls back to `onSurface` rather
 *   than to a hardcoded white that was only ever legible on a dark translucent one.
 */
@Composable
fun SolidPanel(
    modifier: Modifier = Modifier,
    contentColor: Color = Color.Unspecified,
    borderEnabled: Boolean = true,
    cornerRadius: Int = 16,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    // `Color.Unspecified` is Material's "decide for me" sentinel, and `Surface`
    // does not decide: it publishes whatever it is handed straight into
    // `LocalContentColor`. So a panel that omitted `contentColor` handed its
    // whole subtree an *undefined* content colour, and everything inside that
    // resolved its colour from it -- icons, chevrons, unstyled text -- was painted
    // black. On a dark panel that is invisible.
    //
    // It surfaced as several unrelated reports at once: the tent card's edit and
    // delete icons, the Master Blender buttons, the reminder text, the plant edit
    // control. One line in a shared component, not eight patches at the call sites.
    val resolvedContentColor =
        if (contentColor == Color.Unspecified) LocalContentColor.current else contentColor
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius.dp),
        color = scheme.surface,
        contentColor = resolvedContentColor,
        // Shadow only. Tonal elevation would tint the panel towards `primary`,
        // which is the user-chosen accent, and that is exactly the large tinted
        // surface this component refuses to paint.
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        border = if (borderEnabled) {
            BorderStroke(
                width = 1.dp,
                color = panelBorderColor(surface = scheme.surface)
            )
        } else {
            null
        },
        content = content
    )
}

/* ─────────────────────────── Progress ring ─────────────────────────────── */

/**
 * Determinate progress ring.
 *
 * Built on [CircularProgressIndicator] so the arc geometry, the rounded cap and
 * the track all come from Material 3 instead of a hand-rolled `Canvas`. The
 * track is `surfaceVariant`, an opaque scheme role, so the unfilled part of the
 * ring is still a visible shape and not a translucent smear.
 *
 * @param color the arc colour; defaults to `primary`, which *is* the user's
 *   accent, so the ring follows the accent without any call site passing one.
 */
@Composable
fun SolidProgressRing(
    percentage: Float,
    modifier: Modifier = Modifier,
    color: Color? = null,
    size: Int = 96
) {
    val scheme = MaterialTheme.colorScheme
    CircularProgressIndicator(
        progress = { percentage.coerceIn(0f, 1f) },
        modifier = modifier.size(size.dp),
        color = color ?: scheme.primary,
        trackColor = scheme.surfaceVariant,
        strokeWidth = 10.dp,
        strokeCap = StrokeCap.Round
    )
}

/* ─────────────────────────── Selectable chip ───────────────────────────── */

/**
 * Filter chip that carries the user's accent in its selected state.
 *
 * A selected chip is filled with the accent and labelled with
 * [readableOnStrict] of it, so the label clears WCAG AA against *any* accent
 * rather than against the green that happened to ship. An unselected chip uses
 * `surfaceVariant` / `onSurfaceVariant` and an `outline` edge, so the two states
 * differ in fill, label and border instead of in a 20% alpha overlay.
 */
@Composable
fun SelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    val scheme = MaterialTheme.colorScheme
    val accent = accentColor ?: scheme.primary

    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = scheme.surfaceVariant,
            labelColor = scheme.onSurfaceVariant,
            selectedContainerColor = accent,
            selectedLabelColor = accentContentOn(accent)
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = scheme.outline,
            selectedBorderColor = scheme.outline,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp
        )
    )
}

/* ─────────────────────── Accent-filled buttons ────────────────────────── */

/**
 * The content colour an accent-filled button must use.
 *
 * [readableOnStrict] picks whichever of black or white actually contrasts more
 * with the fill, which is provably at least 4.58:1 for *any* input. A luminance
 * threshold is not enough here: the accent is whatever the user picked, and the
 * worst case for any threshold rule is an accent sitting on it.
 */
fun accentContentOn(accent: Color): Color = readableOnStrict(accent)

/**
 * The only button colour set allowed for a button filled with the user's accent.
 *
 * Exists because declaring `onPrimary` correctly in the scheme is not the same
 * as the button using it: `ButtonDefaults.buttonColors(containerColor = accent)`
 * reads well, compiles, and silently hands the label a hardcoded default content
 * colour that has nothing to do with that accent. Every accent-filled button in
 * the app goes through here so the pairing cannot drift.
 */
@Composable
fun accentButtonColors(accent: Color): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = accent,
    contentColor = accentContentOn(accent)
)

/**
 * The colour for an accent-coloured label that sits **directly on the app
 * background** — a text button, a heading, an icon that is not on a fill.
 *
 * [accentContentOn] solves the opposite problem: it puts ink on top of the
 * accent. This puts the accent on top of the background, and there the accent is
 * on its own. Measured against the four shipped backgrounds, the default green
 * is 3.90:1 on `Brote Verde` and 4.10:1 on `Cuidado Nocturno`, and the amber
 * swatch is 1.57:1 on `Invernadero Soleado` — all of them below the 4.5:1 that
 * body text needs. So the accent is used where it earns its place and dropped
 * for the theme's verified `onSurface` where it does not.
 *
 * The answer depends on both the accent and the theme, so it cannot be a
 * constant resolved once: it is a pure function of the two, and a test sweeps
 * every accent against every theme.
 */
fun accentLabelOn(scheme: ColorScheme, accent: Color): Color =
    accentLabelOn(scheme, accent, scheme.background)

/**
 * [accentLabelOn] against a chosen backdrop.
 *
 * The one-argument form measures against `scheme.background`, which is right for
 * a label floating on the page and wrong for one painted on a panel: on a dark
 * theme `surface` is lighter than `background`, and the same accent can clear
 * 4.5:1 on one and fail on the other. Measured on a real device, the accent read
 * 4.94:1 against the page and 1.60:1 against the card it was actually drawn on.
 *
 * @param backdrop the surface this label sits on, not the page behind it.
 */
fun accentLabelOn(scheme: ColorScheme, accent: Color, backdrop: Color): Color =
    if (contrastRatio(accent, backdrop) >= MINIMUM_TEXT_CONTRAST) {
        accent
    } else {
        scheme.onSurface
    }

/**
 * Text-button colours for a label drawn straight onto the app background.
 *
 * The same pairing problem as [accentButtonColors], with the background instead
 * of the accent as the backdrop.
 */
@Composable
fun accentTextButtonColors(scheme: ColorScheme, accent: Color): ButtonColors =
    ButtonDefaults.textButtonColors(contentColor = accentLabelOn(scheme, accent))

/* ─────────────────────────── Formatting ────────────────────────────────── */

/**
 * Formats display strings for duration chips (e.g. "3 h", "1 h 30 min").
 *
 * Lives here rather than in a screen because the protocol preview is not the
 * only caller that formats a duration.
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
