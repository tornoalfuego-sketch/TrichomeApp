package com.trichome.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
 * The bottom navigation bar: seven destinations, an opaque Material 3
 * `NavigationBar` and a solid `outline` hairline above it.
 *
 * The container is `colorScheme.surface` at full alpha and the hairline is
 * `colorScheme.outline`, so the bar keeps a verified edge on both the dark and
 * the light theme instead of fading into the content behind it. The accent
 * survives as the **selected indicator** — the one place a large fill of a
 * user-chosen colour is safe, because its icon and label are painted with
 * [accentContentOn] of that same accent.
 */
@Composable
fun MainBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    themeState: TrichomeThemeState,
    modifier: Modifier = Modifier
) {
    val scheme = themeState.colorScheme()
    val accent = scheme.primary
    val indicatorContent = accentContentOn(accent)

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = scheme.outline)
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = scheme.surface,
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
                        selectedIconColor = indicatorContent,
                        selectedTextColor = scheme.onSurface,
                        indicatorColor = accent,
                        unselectedIconColor = scheme.onSurfaceVariant,
                        unselectedTextColor = scheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

// `SelectableChip` lives in Panels.kt, next to the other opaque surfaces, so
// every solid surface in the app is described in one file.