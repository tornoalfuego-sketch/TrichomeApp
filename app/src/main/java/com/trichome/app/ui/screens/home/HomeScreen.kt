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
import com.trichome.app.model.LunarCardCopy
import com.trichome.app.model.LunarEngine
import com.trichome.app.ui.components.MainBottomBar
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.screens.entourage.entourageRoute
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.viewmodel.HomeViewModel
import com.trichome.app.viewmodel.appViewModel
import com.trichome.app.ui.theme.LocalTertiaryText
import com.trichome.app.model.LunarCardContent
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HomeScreen(
    navController: NavHostController,
    themeState: TrichomeThemeState
) {
    val vm = appViewModel { HomeViewModel(it) }
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

    // Re-read on the day, not the frame. The phase moves on the order of hours and
    // a per-frame read would recompose the whole home screen sixty times a second
    // for a constant; the calendar's minute tick is the wrong cadence here because
    // this value cannot have moved within a session that is still open.
    var today by remember { mutableStateOf(LocalDate.now()) }
    val lunarGlance = remember(today) {
        val now = System.currentTimeMillis()
        LunarCardCopy.contentOf(LunarEngine.snapshot(now), expanded = false)
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            val current = LocalDate.now(ZoneId.systemDefault())
            if (current != today) today = current
        }
    }

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
                color = LocalTertiaryText.current
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
                SolidPanel {
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

            // ── Lunar phase, at a glance ───────────────────────────
            // The engine shipped, was correct, and was still invisible: the phase
            // lived only in the calendar's top bar, so "no se ve implementado el
            // motor del ciclo de la luna" was a fair reading of the app. One line
            // here proves the feature exists and leads to the full panel.
            //
            // Deliberately not the card. `EstimatedClimateCard` and the expanded
            // `LunarPhaseBar` stay exactly where they are; duplicating either on
            // Home would be two sources of truth for one value, and the climate
            // estimate in particular must never read as a second reading of
            // something. This row is one glanceable presence plus a destination.
            LunarGlanceRow(
                content = lunarGlance,
                onClick = { navController.navigate("calendar") }
            )

            SolidPanel {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚡ Acciones rápidas", style = MaterialTheme.typography.titleMedium)
                    ActionRow(navController, "calendar", Icons.Filled.CalendarMonth, "Calendario de cultivo")
                    ActionRow(navController, "charts", Icons.Filled.StackedLineChart, "Gráficas e indicadores")
                    ActionRow(navController, "terpenes", Icons.Filled.Spa, "Biblia de terpenos")
                    ActionRow(navController, "breeding", Icons.Filled.Biotech, "Breeding & proyectos")
                    // The Séquito module was reachable only from the encyclopedia's
                    // own card, two screens deep, which is why a whole feature read as
                    // absent. The route comes from [entourageRoute] rather than a
                    // literal, so it cannot drift from the destination the module
                    // declares; with no arguments it is the bare route, which is the
                    // one the `NavHost` registers.
                    ActionRow(
                        navController = navController,
                        route = entourageRoute(),
                        icon = Icons.Filled.Hub,
                        label = "Efecto Séquito · sinergias"
                    )
                }
            }

            SolidPanel {
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

/**
 * The lunar phase in one row, linking to the calendar where the full panel lives.
 *
 * Same colour discipline as [ActionRow] and for the same reason: the glyph and
 * the chevron are furniture and take the third text level, so this row does not
 * repaint when the accent changes. The phase name is content and takes the
 * default text role.
 *
 * Not scrollable. `ScrollOwnershipTest` exists because a nested scroll measured
 * with an infinite maximum height killed the process on a tap, and this composable
 * is mounted inside Home's own `verticalScroll`.
 */
@Composable
private fun LunarGlanceRow(
    content: LunarCardContent,
    onClick: () -> Unit
) {
    val furniture = LocalTertiaryText.current
    SolidPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(content.glyph, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Fase lunar", style = MaterialTheme.typography.labelSmall, color = furniture)
                Text(
                    content.phaseLabelEs,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "${content.illuminationEs} ilumina · ${content.trendLabelEs} · " +
                        "consejo en el calendario",
                    style = MaterialTheme.typography.bodySmall,
                    color = furniture
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = furniture
            )
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
    // One row, one colour source.
    //
    // This used to resolve the leading glyph through `accentLabelOn`, so the icon
    // followed the accent while the label followed the text role and the chevron
    // followed neither: three colours in one row, and the icon repainted every
    // time the accent changed. It was the only list row in the app that did it --
    // the other two `accentLabelOn` call sites are chrome (the bottom bar, the
    // encyclopedia header), not content.
    //
    // The rule now: in a row, the label is the content and the two glyphs are
    // furniture, so both glyphs take the third text level. The accent keeps its
    // meaning everywhere else -- filled buttons, the selected tab and the FAB --
    // and a list that changes colour with the accent stops being a list the user
    // can scan. It does not keep it on a panel edge either: that edge is a
    // neutral step of the surface, so it is the same line whatever the accent.
    val furniture = LocalTertiaryText.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { navController.navigate(route) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = furniture)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = furniture
        )
    }
}