package com.trichome.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    // Seven tabs leave about 51dp each on a 360dp phone. "Calendario" and
    // "Diagnóstico" do not fit there at any readable size: measured on a device,
    // they either wrapped to "Calend ario" / "Diagn ostico" or clipped to
    // "Calendari" / "Diagnosti". Both are worse than a shorter word, so the
    // labels are shortened and the route, the contentDescription and the
    // accessibility text keep the full word.
    BottomBarItem("calendar", "Calend.", Icons.Filled.CalendarMonth, Icons.Filled.CalendarMonth),
    BottomBarItem("diagnosis", "Diag.", Icons.Filled.MedicalServices, Icons.Filled.MedicalServices),
    BottomBarItem("settings", "Ajustes", Icons.Filled.Settings, Icons.Filled.Settings)
)

/** The full, unabbreviated name of each tab, for accessibility and tooltips. */
private val MainTabFullLabels: Map<String, String> = mapOf(
    "home" to "Inicio",
    "tents" to "Carpas",
    "journal" to "Bitácora",
    "terpenes" to "Biblia de terpenos",
    "calendar" to "Calendario de cultivo",
    "diagnosis" to "Diagnóstico inteligente",
    "settings" to "Ajustes"
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
    // The bar sits on `surface`, and on a dark theme that is lighter than the
    // page behind it. Measuring the label against the wrong backdrop is how a
    // 4.94:1 accent ends up rendering at 1.60:1.
    val labelInk = accentLabelOn(scheme, accent, scheme.surface)
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = scheme.outline)
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            // The navigation-bar inset is applied here, explicitly and exactly
            // once. `NavigationBarDefaults.windowInsets` already defaults to
            // `systemBars.only(Horizontal + Bottom)`, so the inset does not have
            // to be re-declared to be honoured -- but the default is also what
            // made an earlier `.navigationBarsPadding()` here a *second*
            // application of the same bottom inset, which floats the bar that far
            // above the screen edge. Naming the inset in the `windowInsets` slot
            // keeps the padding, drops the duplication, and states the intent.
            //
            // Either way the bar's content stays clear of the system bar: with
            // the three-button bar (navigation_mode = 0) the items used to sit
            // underneath it, verified on a device at 1080x2340.
            windowInsets = WindowInsets.navigationBars,
            containerColor = scheme.surface,
            tonalElevation = 0.dp
        ) {
            MainTabItems.forEach { item ->
                val selected = currentRoute.startsWith(item.route)
                val spoken = MainTabFullLabels[item.route] ?: item.label
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(item.route) },
                    icon = {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.icon,
                            contentDescription = spoken
                        )
                    },
                    label = {
                        // One line, never wrapped. A label that breaks mid-word
                        // ("Carpa s", "Diagn ostico") or clips ("Calendari") is
                        // worse than a shorter one, so the shortening happens in
                        // MainTabItems and TalkBack still reads the full name.
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                            textAlign = TextAlign.Center
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = indicatorContent,
                        selectedTextColor = labelInk,
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