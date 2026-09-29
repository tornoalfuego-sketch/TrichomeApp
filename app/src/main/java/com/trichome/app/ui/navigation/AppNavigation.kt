package com.trichome.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.trichome.app.ui.screens.breeding.BreedingScreen
import com.trichome.app.ui.screens.calendar.CalendarScreen
import com.trichome.app.ui.screens.charts.ChartsScreen
import com.trichome.app.ui.screens.cycle.SuperCycleScreen
import com.trichome.app.ui.screens.diagnosis.DiagnosisScreen
import com.trichome.app.ui.screens.home.HomeScreen
import com.trichome.app.ui.screens.journal.JournalScreen
import com.trichome.app.ui.screens.plant.PlantDetailScreen
import com.trichome.app.ui.screens.protocol.ProtocolScreen
import com.trichome.app.ui.screens.settings.SettingsScreen
import com.trichome.app.ui.screens.tent.TentListScreen
import com.trichome.app.ui.screens.terpenes.TerpeneDetailScreen
import com.trichome.app.ui.screens.terpenes.TerpenesScreen
import com.trichome.app.ui.theme.TrichomeThemeState

/**
 * Every route the `NavHost` below declares, in one list.
 *
 * Exists so a `navigate(...)` call can be checked against the graph instead of
 * against a route name typed twice. `NavHostController.navigate` throws
 * `IllegalArgumentException` at runtime for a route nobody declared, which on a
 * tile tap means a crash rather than a wrong screen — and a `composable("...")`
 * literal is invisible to the compiler.
 */
val DECLARED_ROUTES: Set<String> = setOf(
    "home", "tents", "journal", "diagnosis", "settings",
    "calendar", "charts", "terpenes", "breeding",
    "plant_detail/{plantId}", "protocol/{plantId}",
    "super_cycle/{plantId}", "journal/{plantId}", "terpene/{terpeneId}"
)

/**
 * App navigation graph with all routes:
 * - Bottom tabs: home, tents, journal, diagnosis, settings
 * - Plant scoped: plant_detail, protocol, super_cycle, journal/{plantId}
 * - Feature screens: calendar, charts, terpenes, breeding
 *
 * Every `composable` below is also listed in [DECLARED_ROUTES]; the test
 * `AppNavigationRouteTest` fails if the two drift apart.
 */
@Composable
fun AppNavigation(
    themeState: TrichomeThemeState,
    modifier: Modifier = Modifier
) {
    val navController: NavHostController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen(navController, themeState)
        }
        composable("tents") {
            TentListScreen(navController, themeState)
        }
        composable(
            "plant_detail/{plantId}"
        ) { backStackEntry ->
            val plantId = backStackEntry.arguments?.getLong("plantId") ?: 0L
            PlantDetailScreen(plantId, navController, themeState)
        }
        composable(
            "protocol/{plantId}"
        ) { backStackEntry ->
            val plantId = backStackEntry.arguments?.getLong("plantId") ?: 0L
            ProtocolScreen(plantId, navController, themeState)
        }
        composable(
            "super_cycle/{plantId}"
        ) { backStackEntry ->
            val plantId = backStackEntry.arguments?.getLong("plantId") ?: 0L
            SuperCycleScreen(plantId, navController, themeState)
        }
        composable("journal") {
            JournalScreen(navController, themeState, plantId = null)
        }
        composable("journal/{plantId}") { backStackEntry ->
            val plantId = backStackEntry.arguments?.getLong("plantId") ?: 0L
            JournalScreen(navController, themeState, plantId)
        }
        composable("calendar") {
            CalendarScreen(navController, themeState)
        }
        composable("charts") {
            ChartsScreen(navController, themeState)
        }
        composable("terpenes") {
            TerpenesScreen(navController, themeState)
        }
        composable(
            "terpene/{terpeneId}"
        ) { backStackEntry ->
            val terpeneId = backStackEntry.arguments?.getString("terpeneId").orEmpty()
            TerpeneDetailScreen(terpeneId, navController, themeState)
        }
        composable("breeding") {
            BreedingScreen(navController, themeState)
        }
        composable("diagnosis") {
            DiagnosisScreen(navController, themeState)
        }
        composable("settings") {
            SettingsScreen(navController, themeState)
        }
    }
}