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
import com.trichome.app.ui.screens.terpenes.TerpenesScreen
import com.trichome.app.ui.theme.TrichomeThemeState

/**
 * App navigation graph with all routes:
 * - Bottom tabs: home, tents, journal, diagnosis, settings
 * - Plant scoped: plant_detail, protocol, super_cycle, journal/{plantId}
 * - Feature screens: calendar, charts, terpenes, breeding
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