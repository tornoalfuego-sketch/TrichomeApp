package com.trichome.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.trichome.app.data.repository.UiLabels
import com.trichome.app.ui.components.FloatingOrbBackground
import com.trichome.app.ui.components.GlassCard
import com.trichome.app.ui.components.GlassSlider
import com.trichome.app.ui.theme.ThemeIndex
import com.trichome.app.ui.theme.TrichomeThemeState

/**
 * Glassmorphism appearance engine: theme chips, glass opacity/blur sliders,
 * accent color swatches and font scale. Persistence handled by
 * [TrichomeThemeState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    themeState: TrichomeThemeState
) {
    val accent = themeState.colorScheme().primary

    val accentSwatches = remember {
        listOf(
            "Verde" to Color(0xFF2E7D32),
            "Ámbar" to Color(0xFFFFC107),
            "Naranja" to Color(0xFFE65100),
            "Cian" to Color(0xFF00BCD4),
            "Violeta" to Color(0xFF7C4DFF),
            "Rosa" to Color(0xFFE91E63)
        )
    }

    Box {
        FloatingOrbBackground(accentColor1 = accent, accentColor2 = themeState.accentColor)

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("⚙️ Ajustes de Apariencia") },
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
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Theme ───────────────────────────────────────────────
                GlassCard(accentColor = accent, glassOpacity = themeState.glassTokens.glassOpacity) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("🎨 Tema", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        listOf(
                            ThemeIndex.GREEN to "🌿 Brote Verde",
                            ThemeIndex.AUTUMN to "🍂 Cosecha de Otoño",
                            ThemeIndex.NIGHT to "🌙 Cuidado Nocturno",
                            ThemeIndex.SUNNY to "☀️ Invernadero Soleado"
                        ).forEach { (index, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { themeState.selectColor(index) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = themeState.selectedColorIndex == index,
                                    onClick = { themeState.selectColor(index) }
                                )
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }

                // ── Glass opacity ───────────────────────────────────────
                GlassCard(accentColor = accent, glassOpacity = themeState.glassTokens.glassOpacity) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "🧊 Opacidad del Vidrio (${(themeState.glassTokens.glassOpacity * 100).toInt()}%)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        GlassSlider(
                            value = themeState.glassTokens.glassOpacity,
                            onValueChange = { themeState.setGlassOpacity(it) },
                            valueRange = 0.05f..0.50f,
                            steps = 43,
                            accentColor = accent
                        )
                    }
                }

                // ── Blur ────────────────────────────────────────────────
                GlassCard(accentColor = accent, glassOpacity = themeState.glassTokens.glassOpacity) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "💨 Intensidad de Desenfoque (${themeState.glassTokens.blurRadius.toInt()} dp)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        GlassSlider(
                            value = themeState.glassTokens.blurRadius,
                            onValueChange = { themeState.setBlurRadius(it) },
                            valueRange = 0f..32f,
                            steps = 31,
                            accentColor = accent
                        )
                    }
                }

                // ── Accent color ────────────────────────────────────────
                GlassCard(accentColor = accent, glassOpacity = themeState.glassTokens.glassOpacity) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("🎯 Color de Acento", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            accentSwatches.forEach { (name, color) ->
                                val selected = themeState.accentColor.value == color.value
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable { themeState.updateAccentColor(color) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Text("✓", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Acento actual: ${themeState.accentColor.toArgbHex()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // ── Font scale ──────────────────────────────────────────
                GlassCard(accentColor = accent, glassOpacity = themeState.glassTokens.glassOpacity) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "🔠 Escala de Fuente (${String.format("%.2f", themeState.fontScale)})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        GlassSlider(
                            value = themeState.fontScale,
                            onValueChange = { themeState.updateFontScale(it) },
                            valueRange = 0.85f..1.30f,
                            steps = 44,
                            accentColor = accent
                        )
                    }
                }

                // ── About ───────────────────────────────────────────────
                GlassCard(accentColor = accent, glassOpacity = themeState.glassTokens.glassOpacity) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("🌿 Trichome App", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Versión 1.0.0\nGestión y diagnóstico inteligente de cultivos de cannabis.\n" +
                                "Motor de apariencia Glassmorphism · SuperCycle · Bitácora · Breeding.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

private fun Color.toArgbHex(): String =
    "#%08X".format(this.toArgb())

private fun Color.toArgb(): Int = (this.value).toLong().toInt()