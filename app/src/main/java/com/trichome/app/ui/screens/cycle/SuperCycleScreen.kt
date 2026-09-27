package com.trichome.app.ui.screens.cycle

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.trichome.app.ui.components.FloatingOrbBackground
import com.trichome.app.ui.components.GlassCard
import com.trichome.app.ui.components.GlassProgressIndicator
import com.trichome.app.ui.components.GlassSlider
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.viewmodel.SuperCycleViewModel
import com.trichome.app.viewmodel.appViewModel
import kotlinx.coroutines.launch

/**
 * SuperCycle motor: edit the photoperiod (light/dark) with presets, watch the
 * calculation live (superday, current phase, % remaining) and persist it for
 * the plant. State lives in [SuperCycleViewModel].
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

    var lightHours by remember { mutableIntStateOf(18) }
    var darkHours by remember { mutableIntStateOf(6) }
    var selectedPreset by remember { mutableStateOf("18/6") }
    var savedAt by remember { mutableStateOf<Long?>(null) }
    var showSaved by remember { mutableStateOf(false) }

    LaunchedEffect(plantId) {
        vm.load(plantId)
        vm.config?.let { config ->
            lightHours = config.lightHours
            darkHours = config.darkHours
            selectedPreset = config.presetType
        }
    }

    // Live update whenever sliders/presets change.
    LaunchedEffect(lightHours, darkHours) {
        val startAt = vm.config?.cycleStartAt ?: System.currentTimeMillis()
        vm.liveUpdate(lightHours, darkHours, startAt)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = vm.result?.phaseProgress ?: 0f,
        label = "supercycle_progress"
    )

    Box {
        FloatingOrbBackground(
            accentColor1 = accent,
            accentColor2 = if (vm.result?.isLight == true) Color(0xFFFFD54F) else themeState.accentColor
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("☀️ SuperCycle Engine") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Photoperiod configuration ───────────────────────────
                GlassCard(accentColor = accent, glassOpacity = themeState.glassTokens.glassOpacity) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Configuración de Fotoperiodo", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("18/6", "12/12", "24/0", "custom").forEach { preset ->
                                FilterChip(
                                    selected = selectedPreset == preset,
                                    onClick = {
                                        selectedPreset = preset
                                        when (preset) {
                                            "18/6" -> { lightHours = 18; darkHours = 6 }
                                            "12/12" -> { lightHours = 12; darkHours = 12 }
                                            "24/0" -> { lightHours = 24; darkHours = 0 }
                                        }
                                    },
                                    label = { Text(preset.uppercase()) }
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Text("☀️ Horas de Luz: $lightHours h", style = MaterialTheme.typography.bodyMedium)
                        GlassSlider(
                            value = lightHours.toFloat(),
                            onValueChange = { lightHours = it.toInt() },
                            valueRange = 0f..24f,
                            steps = 23,
                            accentColor = accent
                        )
                        Text("🌙 Horas de Oscuridad: $darkHours h", style = MaterialTheme.typography.bodyMedium)
                        GlassSlider(
                            value = darkHours.toFloat(),
                            onValueChange = { darkHours = it.toInt() },
                            valueRange = 0f..24f,
                            steps = 23,
                            accentColor = accent
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
                        progress = animatedProgress,
                        accent = accent
                    )
                } ?: GlassCard(accentColor = accent) {
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
                        val startAt = vm.config?.cycleStartAt ?: System.currentTimeMillis()
                        scope.launch {
                            vm.save(
                                plantId = plantId,
                                lightHours = lightHours,
                                darkHours = darkHours,
                                cycleStartAt = startAt,
                                preset = selectedPreset
                            )
                            savedAt = System.currentTimeMillis()
                            showSaved = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
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
            }
        }
    }
}

@Composable
private fun SuperCycleResultCard(
    result: SuperCycleResult,
    progress: Float,
    accent: Color
) {
    GlassCard(accentColor = accent, glassOpacity = 0.18f) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("📊 Resultados del SuperCycle", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassProgressIndicator(
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