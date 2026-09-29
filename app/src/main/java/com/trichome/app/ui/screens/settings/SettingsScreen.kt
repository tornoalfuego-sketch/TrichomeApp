package com.trichome.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.trichome.app.ui.components.FloatingOrbBackground
import com.trichome.app.ui.components.GlassCard
import com.trichome.app.ui.components.GlassSlider
import com.trichome.app.ui.components.GlassmorphicBottomBar
import com.trichome.app.ui.theme.AppFontFamily
import com.trichome.app.ui.theme.AppFontWeight
import com.trichome.app.ui.theme.AppTheme
import com.trichome.app.ui.theme.GlassRanges
import com.trichome.app.ui.theme.SolidPalettes
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.ui.theme.toArgbHex

/** Accent palette offered in Settings. */
private val AccentSwatches = listOf(
    "Verde" to Color(0xFF2E7D32),
    "Lima" to Color(0xFF7CB342),
    "Ámbar" to Color(0xFFFFC107),
    "Naranja" to Color(0xFFE65100),
    "Cian" to Color(0xFF00ACC1),
    "Violeta" to Color(0xFF7C4DFF),
    "Rosa" to Color(0xFFEC407A),
    "Rojo" to Color(0xFFE53935)
)

/**
 * Appearance engine: theme, accent, typography and glass parameters.
 *
 * Every control here writes through [TrichomeThemeState] to DataStore and is
 * applied to the running composition immediately, so each change is visible
 * without leaving the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    themeState: TrichomeThemeState
) {
    val scheme = themeState.colorScheme()
    val accent = scheme.primary
    val glass = themeState.glassTokens

    Box {
        FloatingOrbBackground(accentColor1 = accent, accentColor2 = scheme.tertiary)

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                GlassmorphicBottomBar("settings", { navController.navigate(it) }, themeState)
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
                Text("Ajustes", style = MaterialTheme.typography.headlineSmall)
            }

            /* ── Cristal (global) ─────────────────────────────────────── */
            // Placed first, above every other control, because it governs the
            // whole app: toggling it re-renders each card, chip and menu below.
            GlassCard(
                accentColor = accent,
                contentColor = scheme.onSurface
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("🪟 Efecto de cristal", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Desactívalo para usar superficies opacas y máximo contraste en toda la aplicación.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Cristal translúcido",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                "Se aplica a todos los paneles, tarjetas y menús.",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = themeState.isGlassmorphismEnabled,
                            onCheckedChange = themeState::updateGlassmorphismEnabled
                        )
                    }
                }
            }

            /* ── Tema ─────────────────────────────────────────────────── */
            GlassCard(
                accentColor = accent,
                contentColor = scheme.onSurface
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("🎨 Tema de la aplicación", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "El fondo y las tarjetas usan tonos distintos para que el texto siempre tenga contraste.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    AppTheme.ALL.forEach { candidate ->
                        ThemeOption(
                            theme = candidate,
                            selected = themeState.selectedColorIndex == candidate.index,
                            glassEnabled = themeState.isGlassmorphismEnabled,
                            onClick = { themeState.selectColor(candidate.index) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            /* ── Color de acento ──────────────────────────────────────── */
            GlassCard(
                accentColor = accent,
                contentColor = scheme.onSurface
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("💧 Color de acento", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Se aplica a botones, iconos activos y bordes de tarjeta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    FlowRowSimple(AccentSwatches.chunked(4)) { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(bottom = 10.dp)
                        ) {
                            row.forEach { (name, color) ->
                                AccentSwatch(
                                    color = color,
                                    name = name,
                                    selected = themeState.accentColor.value == color.value,
                                    onClick = { themeState.updateAccentColor(color) }
                                )
                            }
                        }
                    }
                    Text(
                        "Acento actual: #${themeState.accentColor.toArgbHex()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            /* ── Tipografía ───────────────────────────────────────────── */
            GlassCard(
                accentColor = accent,
                contentColor = scheme.onSurface
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("🔠 Tipografía", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))

                    Text("Familia", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    OptionRow(
                        options = AppFontFamily.entries,
                        selectedIndex = themeState.fontFamilyIndex,
                        label = { it.label },
                        onSelect = themeState::updateFontFamily
                    )

                    Spacer(Modifier.height(14.dp))
                    Text("Grosor", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    OptionRow(
                        options = AppFontWeight.entries,
                        selectedIndex = themeState.fontWeightIndex,
                        label = { it.label },
                        onSelect = themeState::updateFontWeight
                    )

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Tamaño · ${"%.2f".format(themeState.fontScale)}×",
                        style = MaterialTheme.typography.labelLarge
                    )
                    GlassSlider(
                        value = themeState.fontScale,
                        onValueChange = themeState::updateFontScale,
                        valueRange = 0.85f..1.30f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))
                    // Live specimen: the grower sees the real rendered style.
                    Text(
                        "Muestra de texto · El mirceno y el limoneno dominan este cultivar",
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp)
                    )
                }
            }

            /* ── Cristal ─────────────────────────────────────────────── */
            GlassCard(
                accentColor = accent,
                contentColor = scheme.onSurface
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("🧊 Panel de cristal", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))

                    Text(
                        "Opacidad · ${(glass.glassOpacity * 100).toInt()} %",
                        style = MaterialTheme.typography.labelLarge
                    )
                    GlassSlider(
                        value = glass.glassOpacity,
                        onValueChange = themeState::setGlassOpacity,
                        valueRange = GlassRanges.opacity,
                        enabled = themeState.isGlassmorphismEnabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Difuminado · ${glass.blurRadius.toInt()} dp",
                        style = MaterialTheme.typography.labelLarge
                    )
                    GlassSlider(
                        value = glass.blurRadius,
                        onValueChange = themeState::setBlurRadius,
                        valueRange = GlassRanges.blur,
                        enabled = themeState.isGlassmorphismEnabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (themeState.isGlassmorphismEnabled) {
                            "El difuminado afecta a la profundidad visual del panel; el texto nunca se difumina para mantenerlo legible."
                        } else {
                            "Estos controles solo se aplican cuando el efecto de cristal está activado."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            /* ── Sobre la app ────────────────────────────────────────── */
            GlassCard(
                accentColor = accent,
                contentColor = scheme.onSurface
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("ℹ️ Acerca de", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Versión", "1.0.1")
                    InfoRow("Datos", "100 % locales y sin conexión")
                    InfoRow("Base de datos", "Room v${2}")
                }
            }

            Spacer(Modifier.height(80.dp))
        }
        }
    }
}

@Composable
private fun ThemeOption(
    theme: AppTheme,
    selected: Boolean,
    glassEnabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    // Preview the palette that is actually on screen, not always the glass one.
    val solid = SolidPalettes.forTheme(theme)
    val previewBackground = if (glassEnabled) theme.background else solid.background
    val previewSurface = if (glassEnabled) theme.surface else solid.surface
    val previewOutline = if (glassEnabled) theme.outline else solid.outline
    val previewOnBackground = if (glassEnabled) theme.onBackground else solid.onBackground
    val previewOnSurfaceVariant = if (glassEnabled) theme.onSurfaceVariant else solid.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(previewBackground)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) theme.secondaryBase else previewOutline,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Two swatches prove the background/surface separation at a glance.
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(previewBackground)
                .border(1.dp, previewOutline, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(previewSurface)
                .border(1.dp, previewOutline, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                // The solid themes have their own name; showing the glass one
                // would label a theme the user is not looking at.
                "${theme.emoji}  ${if (glassEnabled) theme.label else theme.solidLabel}",
                style = MaterialTheme.typography.titleSmall,
                color = previewOnBackground
            )
            Text(
                (if (theme.isDark) "Oscuro" else "Claro") +
                    " · " +
                    (if (glassEnabled) "cristal" else "sólido"),
                style = MaterialTheme.typography.labelSmall,
                color = previewOnSurfaceVariant
            )
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Seleccionado", tint = theme.secondaryBase)
        }
    }
}

@Composable
private fun AccentSwatch(
    color: Color,
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onBackground
                    else Color.White.copy(alpha = 0.4f),
                    shape = CircleShape
                )
                .clickable(onClick = onClick)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Horizontal chip row that wraps onto a second line when needed. */
@Composable
private fun <T> OptionRow(
    options: List<T>,
    selectedIndex: Int,
    label: (T) -> String,
    onSelect: (Int) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options.size) { index ->
            FilterChip(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                label = { Text(label(options[index])) }
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Minimal chunked layout: avoids pulling in the experimental FlowRow API. */
@Composable
private fun <T> FlowRowSimple(chunks: List<List<T>>, content: @Composable (List<T>) -> Unit) {
    Column { chunks.forEach { content(it) } }
}
