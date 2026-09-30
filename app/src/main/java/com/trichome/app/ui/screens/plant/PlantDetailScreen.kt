package com.trichome.app.ui.screens.plant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.trichome.app.data.entity.GrowEvent
import com.trichome.app.data.entity.Plant
import com.trichome.app.model.Phase
import com.trichome.app.model.StageProgressEngine
import com.trichome.app.model.SuperCycleEngine
import com.trichome.app.model.SuperCycleResult
import com.trichome.app.ui.components.accentButtonColors
import com.trichome.app.ui.components.MainBottomBar
import com.trichome.app.ui.components.rememberDestructiveConfirmation
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.components.SolidProgressRing
import com.trichome.app.ui.navigation.PLANT_ID_ARG
import com.trichome.app.ui.navigation.PROTOCOL_ID_ROUTE
import com.trichome.app.ui.navigation.SUPER_CYCLE_ID_ROUTE
import com.trichome.app.ui.navigation.JOURNAL_ID_ROUTE
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.viewmodel.PlantDetailUiState
import com.trichome.app.viewmodel.PlantDetailViewModel
import com.trichome.app.viewmodel.appViewModel

/**
 * Plant detail. `daysInGrow` uses [StageProgressEngine.daysInGrow] — created
 * today counts as **Day 1** (off-by-one bug fixed).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantDetailScreen(
    plantId: Long,
    navController: NavHostController,
    themeState: TrichomeThemeState
) {
    val vm = appViewModel { PlantDetailViewModel(it) }
    val accent = themeState.colorScheme().primary

    LaunchedEffect(plantId) {
        vm.loadPlant(plantId)
        vm.loadSuperCycle(plantId)
        vm.loadLatestStage(plantId)
    }

    val uiState = vm.uiState
    val plant = (uiState as? PlantDetailUiState.Success)?.plant
    val tintAccent = themeState.accentColor

    // The screen had no action icons at all: a plant could only be renamed or
    // deleted from a tent row, and `PlantDetailViewModel` had neither method.
    // `rememberDestructiveConfirmation` is the shared one every other delete
    // uses; a tap only arms it, the write happens after the grower confirms.
    var editing by remember { mutableStateOf(false) }
    val deleteConfirmation = rememberDestructiveConfirmation<Plant>(
        title = { "Eliminar planta" },
        message = { PlantDeletionNotice.message(it) },
        confirmLabel = { "Eliminar" },
        onConfirmed = { target ->
            vm.deletePlant(target) { deleted -> if (deleted) navController.popBackStack() }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plant?.name ?: "Detalle de Planta") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    // Only offered once the row has resolved: there is
                    // nothing to edit or delete while it is still loading,
                    // and a wrong id is worse than no button.
                    plant?.let { current ->
                        IconButton(onClick = { editing = true }) {
                            Icon(Icons.Default.Edit, "Editar planta")
                        }
                        IconButton(onClick = { deleteConfirmation.request(current) }) {
                            Icon(
                                Icons.Default.Delete,
                                "Eliminar planta",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // The three-button system bar sat on top of these buttons and
                    // the labels were unreadable; verified on a device at
                    // 1080x2340 with navigation_mode = 0.
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Two lines, not one. Three Spanish labels plus an emoji in three
                // equal columns is about 340dp each, and at the largest text size
                // the user can pick a single line either clipped mid-word ("Bitác
                // ora") or overflowed into its neighbour. Letting it wrap makes the
                // row a little taller and the label whole, which is the trade the
                // user asked for: the label has to be readable.
                Button(
                    onClick = { navController.navigate(PROTOCOL_ID_ROUTE.replace("{${PLANT_ID_ARG}}", "$plantId")) },
                    modifier = Modifier.weight(1f)
                ) { Text("📋 Protocolos", maxLines = 2) }
                Button(
                    onClick = { navController.navigate(SUPER_CYCLE_ID_ROUTE.replace("{${PLANT_ID_ARG}}", "$plantId")) },
                    modifier = Modifier.weight(1f)
                ) { Text("☀️ SuperCiclo", maxLines = 2) }
                Button(
                    onClick = { navController.navigate(JOURNAL_ID_ROUTE.replace("{${PLANT_ID_ARG}}", "$plantId")) },
                    modifier = Modifier.weight(1f)
                ) { Text("📒 Bitácora", maxLines = 2) }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (uiState) {
                PlantDetailUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = accent)
                        Text("Cargando planta…", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                is PlantDetailUiState.Error -> {
                    SolidPanel(accentColor = accent) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "⚠️ No pudimos abrir la planta",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                uiState.message,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = { navController.popBackStack() },
                                colors = accentButtonColors(accent)
                            ) {
                                Text("← Volver a las carpas")
                            }
                        }
                    }
                }

                is PlantDetailUiState.Success -> {
                    val success = uiState
                    PlantDetailContent(
                        success = success,
                        superCycleResult = vm.superCycleResult,
                        events = vm.events,
                        accent = accent,
                    )
                }
            }
        }
    }

    if (editing && plant != null) {
        PlantEditDialog(
            plant = plant,
            accent = accent,
            onDismiss = { editing = false },
            onSave = { updated ->
                vm.updatePlant(updated) { saved ->
                    if (saved) {
                        editing = false
                        // Re-read only once the write has landed. Reloading from
                        // `onSave` instead would race the update and repaint the
                        // values the grower just changed.
                        vm.loadPlant(plant.id)
                    }
                }
            }
        )
    }
}

/**
 * Everything the plant itself contributes to the screen, rendered only once the
 * row has resolved.
 */
@Composable
private fun PlantDetailContent(
    success: PlantDetailUiState.Success,
    superCycleResult: SuperCycleResult?,
    events: List<GrowEvent>,
    accent: Color,
) {
    val plant = success.plant

    // ── Header info ─────────────────────────────────────────────────────
    SolidPanel(accentColor = accent) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Día de Crecimiento", style = MaterialTheme.typography.labelLarge)
                Text(
                    // "1 días" reads as a bug to a Spanish speaker; the plural is
                    // only correct from two.
                    "${success.daysInGrow} ${if (success.daysInGrow == 1) "día" else "días"}",
                    style = MaterialTheme.typography.displaySmall,
                    color = accent
                )
                Text(
                    plant.strain.ifBlank { "Cepa sin nombre" } + " · " + plant.currentStage,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            SolidProgressRing(
                percentage = 1f,
                size = 72,
                color = accent
            )
        }
    }

    // ── SuperCycle status ───────────────────────────────────────────────
    superCycleResult?.let { result ->
        SolidPanel(accentColor = accent) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("☀️ Fase actual", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val phaseText = when (result.phase) {
                        Phase.LIGHT -> "☀️ LUZ"
                        Phase.DARK -> "🌙 OSCURIDAD"
                        Phase.OFF -> "⏸️ OFF"
                    }
                    Text(phaseText, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.weight(1f))
                    SolidProgressRing(
                        percentage = result.phaseProgress,
                        size = 64,
                        color = if (result.isLight) Color(0xFFFFD54F) else Color(0xFF7C4DFF)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Superday ${result.superday} · ${result.hoursRemainingInPhase}h restantes · ${result.phasePercentage.toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    // ── Stage progress (protocol blocks) ─────────────────────────────────
    success.stageProgress?.let { sp ->
        SolidPanel(accentColor = accent) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📈 Progreso del protocolo", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Etapa: ${sp.stageName}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "Día ${sp.daysInCurrentStage} en etapa · ${sp.daysRemainingInStage} restantes · de ${sp.totalCycleDays} días",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { sp.overallProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent
                )
            }
        }
    }

    // ── Latest events ───────────────────────────────────────────────────
    SolidPanel(accentColor = accent) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📒 Últimos eventos", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val latest = events.take(4)
            if (latest.isEmpty()) {
                Text("Sin eventos registrados", style = MaterialTheme.typography.bodyMedium)
            } else {
                latest.forEach { e ->
                    Text(
                        "• ${com.trichome.app.ui.screens.journal.EventTypeUi.labelResolved(e.eventType)} — ${dateShort(e.timestamp)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    if (plant.notes.isNotBlank()) {
        SolidPanel(accentColor = accent) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📝 Notas", style = MaterialTheme.typography.titleMedium)
                Text(plant.notes, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Short date like "12/05". */
fun dateShort(millis: Long): String {
    val d = java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return "%02d/%02d".format(d.dayOfMonth, d.monthValue)
}