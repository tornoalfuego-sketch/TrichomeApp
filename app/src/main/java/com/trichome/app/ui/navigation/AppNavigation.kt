package com.trichome.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trichome.app.ui.screens.breeding.BreedingScreen
import com.trichome.app.ui.screens.calendar.CalendarScreen
import com.trichome.app.ui.screens.charts.ChartsScreen
import com.trichome.app.ui.screens.cycle.SuperCycleScreen
import com.trichome.app.ui.screens.diagnosis.DiagnosisScreen
import com.trichome.app.model.EntourageTab
import com.trichome.app.ui.screens.entourage.ENTOURAGE_ROUTE
import com.trichome.app.ui.screens.entourage.ENTOURAGE_TAB_ARG
import com.trichome.app.ui.screens.entourage.ENTOURAGE_TERPENE_ARG
import com.trichome.app.ui.screens.entourage.EntourageModuleScreen
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
    PLANT_ID_ROUTE, PROTOCOL_ID_ROUTE, SUPER_CYCLE_ID_ROUTE, JOURNAL_ID_ROUTE,
    TERPENE_ID_ROUTE, ENTOURAGE_ROUTE
)

/** Argument name shared by every plant-scoped route. */
const val PLANT_ID_ARG: String = "plantId"

/** Argument name for the terpene encyclopedia detail. */
const val TERPENE_ID_ARG: String = "terpeneId"

const val PLANT_ID_ROUTE: String = "plant_detail/{$PLANT_ID_ARG}"
const val PROTOCOL_ID_ROUTE: String = "protocol/{$PLANT_ID_ARG}"
const val SUPER_CYCLE_ID_ROUTE: String = "super_cycle/{$PLANT_ID_ARG}"
const val JOURNAL_ID_ROUTE: String = "journal/{$PLANT_ID_ARG}"
const val TERPENE_ID_ROUTE: String = "terpene/{$TERPENE_ID_ARG}"

/**
 * Reads a `Long` path argument, tolerating a value that arrived as a String.
 *
 * Declaring `NavType.LongType` is what makes the argument a Long in the first
 * place. This is the fallback for a route reached with a hand-built string: the
 * accessor would throw on a non-numeric segment, and a plant detail is not worth
 * killing the process over.
 *
 * @return the id, or [MISSING_LONG_ARG] when it is absent or unparseable — the
 *   same sentinel the destinations already treat as "no such plant".
 */
const val MISSING_LONG_ARG: Long = 0L

fun NavBackStackEntry.longArg(name: String): Long =
    arguments?.getLong(name)
        ?: arguments?.getString(name)?.toLongOrNull()
        ?: MISSING_LONG_ARG

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

        // A path argument with no `navArgument` block reaches the destination as
        // a String, so `getLong("plantId")` returned 0 for every plant and the
        // detail screen reported "No encontramos esta planta" for rows that were
        // sitting right there in the list. Verified on a device: plant id 1
        // existed in Room and the screen still refused to open it.
        //
        // Declaring the type is the fix. `toLongOrNull()` is also applied as a
        // belt-and-braces read, because a hand-built route with a non-numeric
        // segment should not crash the argument accessor.
        composable(
            route = PLANT_ID_ROUTE,
            arguments = listOf(navArgument(PLANT_ID_ARG) { type = NavType.LongType })
        ) { backStackEntry ->
            val plantId = backStackEntry.longArg(PLANT_ID_ARG)
            PlantDetailScreen(plantId, navController, themeState)
        }
        // Same defect as plant_detail: an untyped path argument is a String, so
        // getLong returned 0. Protocol, supercycle and the plant journal were all
        // opening plant 0 or nothing at all.
        composable(
            route = PROTOCOL_ID_ROUTE,
            arguments = listOf(navArgument(PLANT_ID_ARG) { type = NavType.LongType })
        ) { backStackEntry ->
            ProtocolScreen(backStackEntry.longArg(PLANT_ID_ARG), navController, themeState)
        }
        composable(
            route = SUPER_CYCLE_ID_ROUTE,
            arguments = listOf(navArgument(PLANT_ID_ARG) { type = NavType.LongType })
        ) { backStackEntry ->
            SuperCycleScreen(backStackEntry.longArg(PLANT_ID_ARG), navController, themeState)
        }
        composable("journal") {
            JournalScreen(navController, themeState, plantId = null)
        }
        composable(
            route = JOURNAL_ID_ROUTE,
            arguments = listOf(navArgument(PLANT_ID_ARG) { type = NavType.LongType })
        ) { backStackEntry ->
            JournalScreen(navController, themeState, backStackEntry.longArg(PLANT_ID_ARG))
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
            route = TERPENE_ID_ROUTE,
            arguments = listOf(navArgument(TERPENE_ID_ARG) { type = NavType.StringType })
        ) { backStackEntry ->
            val terpeneId = backStackEntry.arguments?.getString(TERPENE_ID_ARG).orEmpty()
            TerpeneDetailScreen(terpeneId, navController, themeState)
        }
        composable("breeding") {
            BreedingScreen(navController, themeState)
        }
        // The Séquito module. Both arguments are optional query parameters with
        // declared defaults rather than path segments, so the module is reachable
        // three ways — bare, on a named section, and on a section pre-filtered
        // to one terpene — from one destination. A path segment would force
        // three destinations, and the third would be a second place for the
        // terpene's identity to be typed.
        composable(
            route = ENTOURAGE_ROUTE,
            arguments = listOf(
                navArgument(ENTOURAGE_TAB_ARG) {
                    type = NavType.StringType
                    defaultValue = EntourageTab.NETWORK.key
                },
                navArgument(ENTOURAGE_TERPENE_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            EntourageModuleScreen(
                initialTabKey = backStackEntry.arguments?.getString(ENTOURAGE_TAB_ARG),
                initialTerpeneKey = backStackEntry.arguments?.getString(ENTOURAGE_TERPENE_ARG),
                navController = navController,
                themeState = themeState
            )
        }
        composable("diagnosis") {
            DiagnosisScreen(navController, themeState)
        }
        composable("settings") {
            SettingsScreen(navController, themeState)
        }
    }
}