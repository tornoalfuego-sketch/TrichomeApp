package com.trichome.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.trichome.app.ui.theme.GlassRanges
import com.trichome.app.ui.theme.LocalGlassConfig

/**
 * Contrast contract for the shared top bar.
 *
 * Every bar this replaces drew over a `Scaffold` with `containerColor =
 * Color.Transparent`, on top of the animated orb background, and none of them
 * passed `colors`. The title and the back arrow were therefore whatever the
 * framework picked for the theme — with no guarantee against the *orb* behind
 * them, which is not part of the colour scheme at all.
 *
 * The colours cannot be "just the scheme roles" either: in glass mode the bar is
 * translucent and what is really behind the title is the drifting orb, whose
 * brightness is not a scheme role. So the container carries a legible minimum
 * alpha — [MINIMUM_ALPHA] — and once it is composited over the background the
 * title is measured against the result, not against a translucent surface where
 * a contrast ratio would compare the wrong two things.
 */
object AppTopBarDefaults {
    /**
     * Floor for the bar's alpha.
     *
     * Below this the orb shows through strongly enough that the title stops
     * being reliably readable, which is the entire defect being fixed.
     */
    const val MINIMUM_ALPHA = 0.92f

    /** The bar is opaque once content scrolls under it. */
    const val SCRATCHED_ALPHA = 1f

    /**
     * The colour set the bar paints with.
     *
     * A plain data class rather than `TopAppBarColors` so it can be compared and
     * contrast-checked on the JVM: `TopAppBarColors` has no value equality, so a
     * test comparing two of them would pass no matter what they contained.
     */
    data class Palette(
        val containerColor: Color,
        val scrolledContainerColor: Color,
        val titleContent: Color,
        val navigationIconContent: Color,
        val actionIconContent: Color,
        /**
         * Accent-tinted hairline under the bar.
         *
         * The container is the scheme's `surface`, which deliberately does not
         * move with the accent — that is the same choice the bottom bar makes. So
         * without this the bar would be entirely accent-independent and the user's
         * chosen colour would stop at the content below it.
         */
        val dividerColor: Color
    )

    /**
     * Whether the shared bar offers an action slot.
     *
     * All eight bars it replaces had zero action icons, so this is asserted
     * rather than assumed: a component with no slot is a component the next
     * screen repeats the same omission against.
     */
    const val HAS_ACTIONS_SLOT = true

    /** Whether the back arrow can be suppressed, for tab roots. */
    const val OFFERS_NAVIGATION_SLOT = true
}

/** Resolves the bar's colours from the active scheme and the glass preference. */
fun appTopBarPaletteFor(
    scheme: ColorScheme,
    glassOpacity: Float,
    glassEnabled: Boolean,
    showNavigationIcon: Boolean = true
): AppTopBarDefaults.Palette {
    val clamped = GlassRanges.clampOpacity(glassOpacity)
    // Translucency is only honoured when the effect is on, and never below the
    // floor: the slider's minimum is a card's minimum, not a bar's.
    val restingAlpha = if (glassEnabled) {
        (panelAlphaFor(clamped)).coerceAtLeast(AppTopBarDefaults.MINIMUM_ALPHA)
    } else {
        1f
    }
    val scrolledAlpha = AppTopBarDefaults.SCRATCHED_ALPHA.coerceAtLeast(restingAlpha)

    return AppTopBarDefaults.Palette(
        containerColor = scheme.surface.copy(alpha = restingAlpha),
        scrolledContainerColor = scheme.surface.copy(alpha = scrolledAlpha),
        titleContent = scheme.onSurface,
        navigationIconContent = scheme.onSurface,
        actionIconContent = scheme.onSurfaceVariant,
        dividerColor = scheme.primary.copy(alpha = 0.45f)
    )
}

/** True when this bar would paint the same pixels for [other]. */
internal fun AppTopBarDefaults.Palette.sameLookAs(other: AppTopBarDefaults.Palette): Boolean =
    containerColor == other.containerColor &&
        scrolledContainerColor == other.scrolledContainerColor &&
        titleContent == other.titleContent &&
        navigationIconContent == other.navigationIconContent &&
        actionIconContent == other.actionIconContent &&
        dividerColor == other.dividerColor

/**
 * The one top bar.
 *
 * Extracted rather than duplicated: eight hand-written `TopAppBar`s had eight
 * places to forget the `colors` argument, eight places to forget an action slot,
 * and no way to fix the contrast once. All of them now read this.
 *
 * [onNavigateBack] is nullable on purpose. A bottom-bar destination has no
 * previous entry worth returning to, and a disabled-looking arrow there is the
 * same defect in miniature.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val palette = appTopBarPaletteFor(
        scheme = scheme,
        glassOpacity = GlassRanges.OPACITY_DEFAULT,
        glassEnabled = LocalGlassConfig.current.enabled,
        showNavigationIcon = onNavigateBack != null
    )

    Column(modifier = modifier) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                if (onNavigateBack != null) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                }
            },
            actions = { actions() },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = palette.containerColor,
                scrolledContainerColor = palette.scrolledContainerColor,
                titleContentColor = palette.titleContent,
                navigationIconContentColor = palette.navigationIconContent,
                actionIconContentColor = palette.actionIconContent
            )
        )
        // The accent hairline, the same device the bottom bar uses. Without it the
        // bar is entirely scheme-surface and the user's accent stops above it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(palette.dividerColor)
        )
    }
}
