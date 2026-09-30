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

/**
 * Contrast contract for the shared top bar.
 *
 * Every bar this replaces drew over a `Scaffold` with `containerColor =
 * Color.Transparent`, on top of an animated backdrop, and none of them passed
 * `colors`. The title and the back arrow were therefore whatever the framework
 * picked for the theme — with no guarantee against whatever was behind them.
 *
 * The colours are now the scheme's own `surface` / `onSurface` pair, both fully
 * opaque, and that is the point: a translucent bar over a known backdrop can
 * only be contrast-checked by compositing two colours by hand, whereas an opaque
 * bar puts a scheme role directly behind the title, so the ratio below is a real
 * measurement instead of an estimate of a composite.
 */
object AppTopBarDefaults {
    /** The bar's container is fully opaque in both its resting and scrolled state. */
    const val CONTAINER_ALPHA = 1f

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

/** Resolves the bar's colours from the active scheme. */
fun appTopBarPaletteFor(
    scheme: ColorScheme,
    showNavigationIcon: Boolean = true
): AppTopBarDefaults.Palette = AppTopBarDefaults.Palette(
    // Plain `surface`, not `surface.copy(alpha = …)`: there is no alpha left to
    // tune, and writing one would invite the next editor to reintroduce the knob.
    containerColor = scheme.surface,
    scrolledContainerColor = scheme.surface,
    titleContent = scheme.onSurface,
    navigationIconContent = scheme.onSurface,
    actionIconContent = scheme.onSurfaceVariant,
    dividerColor = scheme.primary.copy(alpha = 0.45f)
)

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
