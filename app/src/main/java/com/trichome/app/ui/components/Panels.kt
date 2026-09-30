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
import com.trichome.app.ui.theme.MINIMUM_NON_TEXT_CONTRAST
import com.trichome.app.ui.theme.MINIMUM_TEXT_CONTRAST
import com.trichome.app.ui.theme.contrastRatio
import com.trichome.app.ui.theme.readableOnStrict

/* ─────────────────────────── Panel chrome ──────────────────────────────── */

/**
 * The border colour a panel may safely draw.
 *
 * The accent is a colour *the user picks*, so it is not a scheme role and
 * nothing guarantees it separates from the surface it sits on: the sunny amber
 * (`#FFC107`) is 1.6:1 against the white panel of the `Invernadero Soleado`
 * theme. A 1dp edge that fails [MINIMUM_NON_TEXT_CONTRAST] is not a border, it
 * is a hairline that disappears, so an accent that cannot carry the edge falls
 * back to `outline`, which is verified per theme.
 *
 * Pure and platform-free on purpose: this is a contrast decision, so it has to
 * be assertable from a JVM test instead of being eyeballed on a device.
 */
fun panelBorderColor(outline: Color, surface: Color, accent: Color?): Color {
    if (accent == null) return outline
    val contrast = contrastRatio(accent, surface)
    return if (contrast >= MINIMUM_NON_TEXT_CONTRAST) accent else outline
}

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
 * 2. **The accent never paints a large surface.** It is accepted because the
 *    call sites use it, and it is spent on the 1dp edge only, through
 *    [panelBorderColor] — where it carries meaning and where a bad pick is
 *    detectable. See that function for why it can fall back.
 * 3. **The edge is a real edge.** `outline` at [MINIMUM_NON_TEXT_CONTRAST] is
 *    what makes a panel read as a panel; a flat translucent bevel did not.
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
    accentColor: Color? = null,
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
                color = panelBorderColor(scheme.outline, scheme.surface, accentColor)
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
