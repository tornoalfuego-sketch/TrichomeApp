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
import com.trichome.app.ui.components.FloatingOrbBackground
import com.trichome.app.ui.components.GlassCard
import com.trichome.app.ui.components.GlassmorphicBottomBar
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
    val todayCount = remember(vm.events.value) {
        val todayStart = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        vm.events.value.count { it.timestamp >= todayStart }
    }
    val activePlants = vm.plants.value.count { it.isActive }
    val activeTents = vm.tents.value.count { it.isActive }

    Box {
        FloatingOrbBackground(accentColor1 = accent, accentColor2 = themeState.accentColor)

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                GlassmorphicBottomBar("home", { navController.navigate(it) }, themeState)
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
                    StatCard("🌱", "$activePlants", "Plantas", Modifier.weight(1f))
                    StatCard("🏕️", "$activeTents", "Carpas", Modifier.weight(1f))
                    StatCard("📒", "$todayCount", "Hoy", Modifier.weight(1f))
                }

                if (vm.streak > 0) {
                    GlassCard(accentColor = accent, glassOpacity = themeState.glassTokens.glassOpacity) {
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

                GlassCard(accentColor = accent, glassOpacity = themeState.glassTokens.glassOpacity) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚡ Acciones rápidas", style = MaterialTheme.typography.titleMedium)
                        ActionRow(navController, "calendar", Icons.Filled.CalendarMonth, "Calendario de cultivo")
                        ActionRow(navController, "charts", Icons.Filled.StackedLineChart, "Gráficas e indicadores")
                        ActionRow(navController, "terpenes", Icons.Filled.Spa, "Biblia de terpenos")
                        ActionRow(navController, "breeding", Icons.Filled.Biotech, "Breeding & proyectos")
                    }
                }

                GlassCard(accentColor = accent, glassOpacity = themeState.glassTokens.glassOpacity) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⏰ Próximos recordatorios", style = MaterialTheme.typography.titleMedium)
                        val next = vm.reminders.value.sortedBy { it.reminderTime }.take(3)
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
}

@Composable
private fun StatCard(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    GlassCard(
        modifier = modifier,
        glassOpacity = 0.18f,
        accentColor = MaterialTheme.colorScheme.primary,
        cornerRadius = 20
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 24.sp)
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodySmall)
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