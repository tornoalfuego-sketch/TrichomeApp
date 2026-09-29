package com.trichome.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.trichome.app.ui.theme.LocalGlassConfig
import com.trichome.app.ui.theme.TrichomeThemeState

data class BottomBarItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

private val MainTabItems = listOf(
    BottomBarItem("home", "Inicio", Icons.Filled.Home, Icons.Filled.Home),
    BottomBarItem("tents", "Carpas", Icons.Filled.Festival, Icons.Filled.Festival),
    BottomBarItem("journal", "Bitácora", Icons.Filled.Book, Icons.AutoMirrored.Filled.MenuBook),
    // Terpenes and the calendar were reachable only by deep link, so two of the
    // app's main destinations looked like they did not exist at all.
    BottomBarItem("terpenes", "Terpenos", Icons.Filled.Science, Icons.Filled.Science),
    BottomBarItem("calendar", "Calendario", Icons.Filled.CalendarMonth, Icons.Filled.CalendarMonth),
    BottomBarItem("diagnosis", "Diagnóstico", Icons.Filled.MedicalServices, Icons.Filled.MedicalServices),
    BottomBarItem("settings", "Ajustes", Icons.Filled.Settings, Icons.Filled.Settings)
)

/**
 * Floating, translucent bottom navigation.
 *
 * The container is derived from the active colour scheme instead of a hardcoded
 * black, so the bar keeps its contrast on both the dark and the light theme.
 * A hairline top border plus a bottom content inset keep it visually detached
 * from the scrolling content behind it.
 */
@Composable
fun GlassmorphicBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    themeState: TrichomeThemeState,
    modifier: Modifier = Modifier
) {
    val scheme = themeState.colorScheme()
    val accent = scheme.primary
    // Opaque mode drops the translucency instead of reusing the glass alpha.
    val alpha = if (themeState.isGlassmorphismEnabled) {
        panelAlphaFor(themeState.glassTokens.glassOpacity)
    } else {
        1f
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = 0f),
                            accent.copy(alpha = 0.45f),
                            accent.copy(alpha = 0f)
                        )
                    )
                )
        )
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = scheme.surface.copy(alpha = alpha),
            tonalElevation = 0.dp
        ) {
            MainTabItems.forEach { item ->
                val selected = currentRoute.startsWith(item.route)
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(item.route) },
                    icon = {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.icon,
                            contentDescription = item.label
                        )
                    },
                    label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = accent,
                        selectedTextColor = scheme.onSurface,
                        indicatorColor = accent.copy(alpha = 0.20f),
                        unselectedIconColor = scheme.onSurfaceVariant,
                        unselectedTextColor = scheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

/**
 * Quick chip with glass style, e.g. event type chips.
 *
 * [accentColor] is a nullable override so the chip follows the user's accent by
 * default; the previous hardcoded green made every chip the wrong colour as soon
 * as the accent was changed. In opaque mode the chip also drops its translucency
 * and uses the solid surface roles instead of a faded panel.
 */
@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    val scheme = MaterialTheme.colorScheme
    val config = LocalGlassConfig.current
    val accent = accentColor ?: config.accentColor

    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        modifier = modifier,
        colors = if (config.enabled) {
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = accent.copy(alpha = 0.25f),
                selectedLabelColor = scheme.onSurface,
                containerColor = scheme.surface.copy(alpha = 0.4f)
            )
        } else {
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = scheme.secondaryContainer,
                selectedLabelColor = scheme.onSecondaryContainer,
                containerColor = scheme.surfaceVariant
            )
        },
        border = if (config.enabled) {
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = accent.copy(alpha = 0.6f),
                selectedBorderColor = accent,
                borderWidth = 1.dp,
                selectedBorderWidth = 1.dp
            )
        } else {
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = scheme.outline,
                selectedBorderColor = scheme.outline,
                borderWidth = 1.dp,
                selectedBorderWidth = 1.dp
            )
        }
    )
}