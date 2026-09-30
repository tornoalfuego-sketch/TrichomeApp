package com.trichome.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.trichome.app.ui.components.MainBottomBar
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.viewmodel.HomeViewModel
import com.trichome.app.viewmodel.appViewModel

@Composable
fun HomeScreen(
    navController: NavHostController,
    themeState: TrichomeThemeState
) {
    val vm = appViewModel { HomeViewModel(it) }
    val accent = themeState.colorScheme().primary
    // Read as state, not as `.value`: sampling the flow during composition would
    // capture whatever happened to be there when the screen was first drawn and
    // never recompose when the database emits a change.
    val events by vm.events.collectAsState()
    val plants by vm.plants.collectAsState()
    val tents by vm.tents.collectAsState()
    val reminders by vm.reminders.collectAsState()
    val todayCount = remember(events) {
        val todayStart = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        events.count { it.timestamp >= todayStart }
    }
    val activePlants = plants.count { it.isActive }
    val activeTents = tents.count { it.isActive }

    Scaffold(
        bottomBar = {
            MainBottomBar("home", { navController.navigate(it) }, themeState)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("🌿 Trichome App", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Gestión y diagnóstico inteligente de tus cultivos",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatTile(
                    tile = HomeStatTile.PLANTS,
                    value = "$activePlants",
                    modifier = Modifier.weight(1f),
                    onClick = { HomeStatTile.PLANTS.route?.let(navController::navigate) }
                )
                StatTile(
                    tile = HomeStatTile.TENTS,
                    value = "$activeTents",
                    modifier = Modifier.weight(1f),
                    onClick = { HomeStatTile.TENTS.route?.let(navController::navigate) }
                )
                StatTile(
                    tile = HomeStatTile.TODAY,
                    value = "$todayCount",
                    modifier = Modifier.weight(1f),
                    onClick = { HomeStatTile.TODAY.route?.let(navController::navigate) }
                )
            }

            if (vm.streak > 0) {
                SolidPanel(accentColor = accent) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔥", fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Racha de registro: ${vm.streak} día(s)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text("Mantén el hábito diario", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            SolidPanel(accentColor = accent) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚡ Acciones rápidas", style = MaterialTheme.typography.titleMedium)
                    ActionRow(navController, "calendar", Icons.Filled.CalendarMonth, "Calendario de cultivo")
                    ActionRow(navController, "charts", Icons.Filled.StackedLineChart, "Gráficas e indicadores")
                    ActionRow(navController, "terpenes", Icons.Filled.Spa, "Biblia de terpenos")
                    ActionRow(navController, "breeding", Icons.Filled.Biotech, "Breeding & proyectos")
                }
            }

            SolidPanel(accentColor = accent) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⏰ Próximos recordatorios", style = MaterialTheme.typography.titleMedium)
                    val next = reminders.sortedBy { it.reminderTime }.take(3)
                    if (next.isEmpty()) {
                        Text("Sin recordatorios activos", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        next.forEach { r ->
                            Text(
                                "• ${r.title} — cada ${r.recurrenceIntervalDays} día(s)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A stat card, now with a destination.
 *
 * [tile] carries the route; the click is applied through [Modifier.clickable]
 * only when there is somewhere to go, so a tile without a destination renders as
 * a label rather than as a card that swallows taps. The routes are read from
 * [HomeStatTile], which is asserted against the `NavHost` in
 * `HomeStatTileTest` — a typo there would otherwise throw at the tap.
 */
@Composable
private fun StatTile(
    tile: HomeStatTile,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    SolidPanel(
        modifier = modifier.clickable(onClick = onClick),
        accentColor = MaterialTheme.colorScheme.primary,
        cornerRadius = 20
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(tile.icon, fontSize = 24.sp)
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(tile.labelEs, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ActionRow(
    navController: NavHostController,
    route: String,
    icon: ImageVector,
    label: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { navController.navigate(route) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null)
    }
}