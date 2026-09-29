package com.trichome.app.ui.screens.charts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.trichome.app.data.entity.GrowEvent
import com.trichome.app.model.Gamification
import com.trichome.app.ui.components.FloatingOrbBackground
import com.trichome.app.ui.components.GlassCard
import com.trichome.app.ui.components.GlassChip
import com.trichome.app.ui.components.LevelProgressBar
import com.trichome.app.ui.components.NativeLineChart
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.viewmodel.ChartsViewModel
import com.trichome.app.viewmodel.appViewModel

/**
 * Grow metrics journal: native Canvas charts per metric plus gamification
 * summary (XP, level, streak, achievements).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    navController: NavHostController,
    themeState: TrichomeThemeState
) {
    val vm = appViewModel { ChartsViewModel(it) }
    val accent = themeState.colorScheme().primary
    val plants by vm.plants.collectAsState()

    var metric by remember { mutableStateOf("ph") }
    val level = Gamification.levelFromXp(vm.xp)

    LaunchedEffect(Unit) { vm.refresh() }

    val metrics = listOf(
        "ph" to "pH",
        "ec" to "EC",
        "temp" to "Temp",
        "hum" to "Humedad",
        "height" to "Altura"
    )

    Box {
        FloatingOrbBackground(accentColor1 = accent, accentColor2 = themeState.accentColor)

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("📈 Diario de Indicadores") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Gamification summary ────────────────────────────────
                item {
                    GlassCard(accentColor = accent, glassOpacity = themeState.glassTokens.glassOpacity) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Nivel $level", style = MaterialTheme.typography.headlineSmall)
                                    Text(
                                        "XP total: ${vm.xp} · Racha: ${vm.streak} día(s)",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Text("🏆", fontSize = 34.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            LevelProgressBar(currentXp = vm.xp, level = level)

                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Logros: ${vm.achievements.count { it.isUnlocked }}/${vm.achievements.size}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            vm.achievements.take(5).forEach { a ->
                                Text(
                                    "${if (a.isUnlocked) "✅" else "🔒"} ${a.name} — ${a.description}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // ── Plant filter ────────────────────────────────────────
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            GlassChip("🌱 Todas", selected = vm.selectedPlantId == null, onClick = { vm.selectPlant(null) })
                        }
                        items(plants, key = { it.id }) { p ->
                            GlassChip(p.name, selected = vm.selectedPlantId == p.id, onClick = { vm.selectPlant(p.id) })
                        }
                    }
                }

                // ── Metrics selector ────────────────────────────────────
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(metrics) { (key, label) ->
                            GlassChip(label, selected = metric == key, onClick = { metric = key })
                        }
                    }
                }

                // ── Chart ───────────────────────────────────────────────
                item {
                    GlassCard(accentColor = accent, glassOpacity = themeState.glassTokens.glassOpacity) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            val label = metrics.first { it.first == metric }.second
                            Text("$label a lo largo del cultivo", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(10.dp))
                            NativeLineChart(
                                points = buildSeries(vm.events, metric),
                                color = accent
                            )
                        }
                    }
                }

                // ── Recent events with metrics ──────────────────────────
                item {
                    Text("Últimos registros métricos", style = MaterialTheme.typography.titleMedium)
                }
                val recent = vm.events.filter { metricValue(it, metric) != null }.take(10)
                if (recent.isEmpty()) {
                    item {
                        Text("Sin datos suficientes", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    items(recent, key = { it.id }) { event ->
                        MetricRow(event, metric, accent)
                    }
                }
            }
        }
    }
}

private fun metricValue(event: GrowEvent, metric: String): Float? = when (metric) {
    "ph" -> event.ph
    "ec" -> event.ec
    "temp" -> event.temperature
    "hum" -> event.humidity
    "height" -> event.height
    else -> null
}

private fun buildSeries(events: List<GrowEvent>, metric: String): List<Pair<Long, Float>> {
    val chrono = events.sortedBy { it.timestamp }
    return chrono.mapNotNull { e ->
        metricValue(e, metric)?.let { e.timestamp to it }
    }.takeLast(40)
}

@Composable
private fun MetricRow(event: GrowEvent, metric: String, accent: Color) {
    val value = metricValue(event, metric)
    val unit = when (metric) {
        "ph" -> ""
        "ec" -> " mS/cm"
        "temp" -> " °C"
        "hum" -> " %"
        "height" -> " cm"
        else -> ""
    }
    GlassCard(accentColor = accent, cornerRadius = 12) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(metricLabel(metric), style = MaterialTheme.typography.titleSmall)
                Text(
                    com.trichome.app.ui.screens.plant.dateShort(event.timestamp) + " · " + event.notes.orEmpty().ifBlank { "sin notas" },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "${value?.toString() ?: "—"}$unit",
                style = MaterialTheme.typography.titleMedium,
                color = accent
            )
        }
    }
}

private fun metricLabel(metric: String): String = when (metric) {
    "ph" -> "pH"
    "ec" -> "EC"
    "temp" -> "Temperatura"
    "hum" -> "Humedad"
    "height" -> "Altura"
    else -> metric
}