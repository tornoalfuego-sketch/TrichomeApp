package com.trichome.app.ui.screens.cycle

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.trichome.app.model.Phase
import com.trichome.app.model.SuperCycleEngine
import com.trichome.app.model.SuperCycleResult
import com.trichome.app.data.entity.SuperCycleConfig
import com.trichome.app.ui.components.accentButtonColors
import com.trichome.app.ui.components.AppTopBar
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.components.SolidProgressRing
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.viewmodel.SuperCycleViewModel
import com.trichome.app.viewmodel.appViewModel
import kotlinx.coroutines.launch

/**
 * SuperCycle motor: edit the photoperiod (light/dark) with presets, watch the
 * calculation live (superday, current phase, % remaining) and persist it.
 *
 * Opened from a plant, but what it writes is the *tent's* configuration — every
 * plant under that tent runs on it. The config it loads is resolved by
 * `SuperCycleRepository.getConfigForPlant`, never read off the plant id here.
 * The saved row and the live result live in [SuperCycleViewModel]; the photoperiod
 * being edited lives in [SuperCycleForm], because it has to be one piece of state
 * with a loaded flag — see its KDoc for the data-loss bug that enforces it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperCycleScreen(
    plantId: Long,
    navController: NavHostController,
    themeState: TrichomeThemeState
) {
    val vm = appViewModel { SuperCycleViewModel(it) }
    val scope = rememberCoroutineScope()
    val accent = themeState.colorScheme().primary
    val configsWithoutTent by vm.configsWithoutTent.collectAsState()

    // One state object owns the sliders, and it starts out explicitly not
    // loaded. The previous shape kept three independent `mutableIntStateOf`
    // defaults and assigned them from `vm.config` on the line after a
    // fire-and-forget load, so the assignment always ran against a null config:
    // the sliders showed 18/6 while the result card showed the saved cycle, and
    // saving without dragging a slider overwrote the tent's photoperiod. Now the
    // only thing that writes the hours is `SuperCycleForm.onLoaded`, fed by the
    // row `vm.load` returned, and Save stays disabled until that happened.
    var form by remember { mutableStateOf(SuperCycleForm()) }
    var savedAt by remember { mutableStateOf<Long?>(null) }
    var showSaved by remember { mutableStateOf(false) }

    LaunchedEffect(plantId) {
        form = SuperCycleForm().onLoaded(vm.load(plantId))
    }

    val lightHours = form.lightHours
    val darkHours = form.darkHours
    val selectedPreset = form.presetType

    // Live update whenever sliders/presets change. Held back until the load
    // resolved, because before that the hours are defaults and the result card
    // would briefly advertise a 18/6 cycle the tent is not running.
    LaunchedEffect(lightHours, darkHours, form.loaded) {
        if (!form.loaded) return@LaunchedEffect
        val startAt = form.savedCycleStartAt ?: System.currentTimeMillis()
        vm.liveUpdate(lightHours, darkHours, startAt)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = vm.result?.phaseProgress ?: 0f,
        label = "supercycle_progress"
    )

    Scaffold(
        topBar = {
            AppTopBar(
                title = "☀️ SuperCycle Engine",
                onNavigateBack = { navController.popBackStack() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                // Without this the column is taller than the viewport on a real
                // phone and the last panel is simply clipped off. That is not a
                // cosmetic issue here: the clipped panel is the "Sin carpa" list,
                // so its rows would exist and be unreachable — the same defect
                // class as the orphans themselves.
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Photoperiod configuration ───────────────────────────
            SolidPanel {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Configuración de Fotoperiodo", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("18/6", "12/12", "24/0", "custom").forEach { preset ->
                            FilterChip(
                                selected = selectedPreset == preset,
                                onClick = { form = form.withPreset(preset) },
                                label = { Text(preset.uppercase()) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text("☀️ Horas de Luz: $lightHours h", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = lightHours.toFloat(),
                        onValueChange = { form = form.withLightHours(it.toInt()) },
                        valueRange = 0f..24f,
                        steps = 23
                    )
                    Text("🌙 Horas de Oscuridad: $darkHours h", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = darkHours.toFloat(),
                        onValueChange = { form = form.withDarkHours(it.toInt()) },
                        valueRange = 0f..24f,
                        steps = 23
                    )

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ciclo Total", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${lightHours + darkHours} h",
                            style = MaterialTheme.typography.titleLarge,
                            color = accent
                        )
                    }
                }
            }

            // ── Live results ────────────────────────────────────────
            vm.result?.let { result ->
                SuperCycleResultCard(
                    result = result,
                    progress = animatedProgress
                )
            } ?: SolidPanel {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Ajusta las horas para ver el cálculo en vivo", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // ── Save ────────────────────────────────────────────────
            Button(
                onClick = {
                    // Null while the load is unresolved: the screen never writes
                    // photoperiod it has not shown.
                    val request = form.saveRequest(System.currentTimeMillis()) ?: return@Button
                    scope.launch {
                        vm.save(
                            plantId = plantId,
                            lightHours = request.lightHours,
                            darkHours = request.darkHours,
                            cycleStartAt = request.cycleStartAt,
                            preset = request.presetType
                        )
                        savedAt = System.currentTimeMillis()
                        showSaved = true
                    }
                },
                enabled = form.canSave,
                modifier = Modifier.fillMaxWidth(),
                colors = accentButtonColors(accent)
            ) {
                Text("💾 Guardar Configuración")
            }

            if (showSaved) {
                Text(
                    "✓ Configuración guardada " + (savedAt?.let { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            // ── Configurations the migration could not attach to a tent ──
            //
            // The v2 -> v3 migration moved the supercycle onto the tent, and two
            // rows on the install that motivated it pointed at plants that no
            // longer existed. They are kept, per the grower's decision, so this
            // is where they have to be visible: same precedent and same wording
            // as the "Sin carpa" panel in TentListScreen, which is what stopped
            // detached plants from being invisible. Deleting one is possible and
            // is the only thing in the app that removes one.
            if (configsWithoutTent.isNotEmpty()) {
                SolidPanel {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("🧺 Sin carpa", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Estas configuraciones apuntan a plantas que ya no existen, " +
                                "así que no se pudieron asociar a ninguna carpa. No se han " +
                                "borrado: bórralas tú si ya no las necesitas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        configsWithoutTent.forEach { config ->
                            OrphanConfigRow(config = config, onDelete = { vm.deleteWithoutTent(config) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * One config that no tent owns.
 *
 * A row you can see but cannot act on is the same defect with a label on it, so
 * this carries a delete — named, because an unnamed icon button is announced to
 * TalkBack as just a button.
 */
@Composable
private fun OrphanConfigRow(config: SuperCycleConfig, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${config.lightHours}h luz / ${config.darkHours}h oscuridad",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Configuración #${config.id}" +
                    (config.plantId?.let { " · planta #$it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                "Eliminar configuración sin carpa",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SuperCycleResultCard(
    result: SuperCycleResult,
    progress: Float
) {
    SolidPanel {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("📊 Resultados del SuperCycle", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                SolidProgressRing(
                    percentage = progress,
                    size = 84,
                    color = if (result.isLight) Color(0xFFFFD54F) else Color(0xFF7C4DFF)
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    val phaseText = when (result.phase) {
                        Phase.LIGHT -> "☀️ LUZ"
                        Phase.DARK -> "🌙 OSCURIDAD"
                        Phase.OFF -> "⏸️ OFF"
                    }
                    Text(phaseText, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Fase actual (${result.totalCycleHours}h de ciclo)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatLabel("Superday", "Día ${result.superday}")
                StatLabel("% restante", "${result.phasePercentage.toInt()}%")
                StatLabel("Tiempo restante", "${result.hoursRemainingInPhase}h")
                StatLabel("Días calendario", "${result.calendarDaysElapsed}")
            }
        }
    }
}

@Composable
private fun StatLabel(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}