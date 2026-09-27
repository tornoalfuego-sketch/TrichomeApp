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
import com.trichome.app.model.Phase
import com.trichome.app.model.StageProgressEngine
import com.trichome.app.model.SuperCycleEngine
import com.trichome.app.ui.components.FloatingOrbBackground
import com.trichome.app.ui.components.GlassCard
import com.trichome.app.ui.components.GlassProgressIndicator
import com.trichome.app.ui.components.GlassmorphicBottomBar
import com.trichome.app.ui.theme.TrichomeThemeState
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
        vm.loadStageProgress(plantId)
    }

    val plant = vm.plant
    val tintAccent = themeState.accentColor

    Box {
        FloatingOrbBackground(
            accentColor1 = accent,
            accentColor2 = if (vm.superCycleResult?.isLight == true) Color(0xFFFFD54F) else tintAccent
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(plant?.name ?: "Detalle de Planta") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        }
                    }
                )
            },
            bottomBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { navController.navigate("protocol/$plantId") },
                        modifier = Modifier.weight(1f)
                    ) { Text("📋 Protocolos") }
                    Button(
                        onClick = { navController.navigate("super_cycle/$plantId") },
                        modifier = Modifier.weight(1f)
                    ) { Text("☀️ SuperCycle") }
                    Button(
                        onClick = { navController.navigate("journal/$plantId") },
                        modifier = Modifier.weight(1f)
                    ) { Text("📒 Bitácora") }
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
                if (plant == null) {
                    Text("Cargando planta…", style = MaterialTheme.typography.bodyLarge)
                    return@Column
                }

                // ── Header info ──────────────────────────────────────────
                GlassCard(accentColor = accent, glassOpacity = themeState.glassTokens.glassOpacity) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Día de Crecimiento", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "${vm.daysInGrow} días",
                                style = MaterialTheme.typography.displaySmall,
                                color = accent
                            )
                            Text(
                                plant.strain.ifBlank { "Cepa sin nombre" } + " · " + plant.currentStage,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        GlassProgressIndicator(
                            percentage = 1f,
                            size = 72,
                            color = accent
                        )
                    }
                }

                // ── SuperCycle status ────────────────────────────────────
                vm.superCycleResult?.let { result ->
                    GlassCard(accentColor = accent, glassOpacity = themeState.glassTokens.glassOpacity) {
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
                                GlassProgressIndicator(
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

                // ── Stage progress (protocol blocks) ─────────────────────
                vm.stageProgress?.let { sp ->
                    GlassCard(accentColor = accent, glassOpacity = themeState.glassTokens.glassOpacity) {
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

                // ── Latest events ────────────────────────────────────────
                GlassCard(accentColor = accent, glassOpacity = themeState.glassTokens.glassOpacity) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📒 Últimos eventos", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        val latest = vm.events.take(4)
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
                    GlassCard(accentColor = accent) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("📝 Notas", style = MaterialTheme.typography.titleMedium)
                            Text(plant.notes, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
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