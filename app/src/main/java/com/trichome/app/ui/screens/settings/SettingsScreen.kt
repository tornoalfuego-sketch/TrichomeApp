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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.trichome.app.ui.components.accentButtonColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.trichome.app.BuildConfig
import com.trichome.app.data.database.AppDatabase
import com.trichome.app.ui.components.MainBottomBar
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.theme.AccentPalette
import com.trichome.app.ui.theme.AppFontFamily
import com.trichome.app.ui.theme.AppFontWeight
import com.trichome.app.ui.theme.AppTheme
import com.trichome.app.ui.theme.SolidPalettes
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.ui.theme.toArgbHex

/**
 * The accent swatches offered in Settings.
 *
 * Public and reading [AccentPalette] so the contrast test can sweep exactly
 * what the user can pick, and so the eight literals live in one place: the
 * swatches used to be written out here while the palette kept its own copy, and
 * nothing asserted they agreed.
 */
val AccentSwatches: List<Pair<String, Color>> = listOf(
    "Verde" to Color(AccentPalette.DEFAULT_ACCENT_ARG),
    "Lima" to Color(AccentPalette.LIME_ACCENT_ARG),
    "Ámbar" to Color(AccentPalette.AMBER_ACCENT_ARG),
    "Naranja" to Color(AccentPalette.ORANGE_ACCENT_ARG),
    "Cian" to Color(AccentPalette.CYAN_ACCENT_ARG),
    "Violeta" to Color(AccentPalette.VIOLET_ACCENT_ARG),
    "Rosa" to Color(AccentPalette.PINK_ACCENT_ARG),
    "Rojo" to Color(AccentPalette.RED_ACCENT_ARG)
)

/**
 * Appearance settings: theme, accent and typography.
 *
 * Every control here writes through [TrichomeThemeState] to DataStore and is
 * applied to the running composition immediately, so each change is visible
 * without leaving the screen.
 *
 * Theme, accent and typography are accessibility and legibility settings, so
 * they stay. The panel translucency controls that used to sit at the top and
 * the bottom of this list are gone with the effect they controlled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    themeState: TrichomeThemeState
) {
    val scheme = themeState.colorScheme()
    val accent = scheme.primary
    var showColorRoles by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            MainBottomBar("settings", { navController.navigate(it) }, themeState)
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

        /* ── Tema ─────────────────────────────────────────────────── */
        SolidPanel(
            contentColor = scheme.onSurface
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("🎨 Tema de la aplicación", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Cada tema es opaco y usa tonos distintos para el fondo y las tarjetas, " +
                    "de modo que el texto siempre tenga contraste.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                AppTheme.ALL.forEach { candidate ->
                    ThemeOption(
                        theme = candidate,
                        selected = themeState.selectedColorIndex == candidate.index,
                        onClick = { themeState.selectColor(candidate.index) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        /* ── Color de acento ──────────────────────────────────────── */
        SolidPanel(
            contentColor = scheme.onSurface
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("💧 Color de acento", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Se aplica a botones, iconos activos y al indicador de la pestaña seleccionada.",
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

        /* ── Colores de texto y botones ───────────────────────────── */
        // The accent above and these four are different things: the accent is the
        // one colour a user is expected to pick freely, because a fill that does
        // not contrast is still a usable button. Text is different -- an
        // unreadable label is a broken app -- so these are validated against the
        // surface and a colour that cannot clear the bar is marked and dropped
        // rather than applied.
        SolidPanel(
            contentColor = scheme.onSurface
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "🔤 Colores de texto y botones",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Texto primario, secundario y terciario, y el color de los botones. " +
                        "Se aplican a toda la aplicación.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                val overrides = themeState.colorOverrides
                Button(
                    onClick = { showColorRoles = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = accentButtonColors(accent)
                ) {
                    Text("Elegir colores")
                }
                Spacer(Modifier.height(10.dp))
                ColorReadout(
                    "Primario",
                    overrides.primaryText ?: scheme.onSurface
                )
                ColorReadout(
                    "Secundario",
                    overrides.secondaryText ?: scheme.onSurfaceVariant
                )
                ColorReadout(
                    "Terciario",
                    themeState.textColors.tertiary
                )
                ColorReadout(
                    "Botones",
                    overrides.button ?: accent
                )
            }
        }

        /* ── Tipografía ───────────────────────────────────────────── */
        SolidPanel(
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
                Slider(
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
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.surfaceVariant)
                        .padding(12.dp)
                )
            }
        }

        /* ── Sobre la app ────────────────────────────────────────── */
        SolidPanel(
            contentColor = scheme.onSurface
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("ℹ️ Acerca de", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                // Both rows are derived from the build and from the database
                // class, never from a literal that a release bump or a
                // migration would silently invalidate.
                val about = aboutInfo(BuildConfig.VERSION_NAME, AppDatabase.VERSION)
                InfoRow("Versión", about.version)
                InfoRow("Datos", about.storage)
                InfoRow("Base de datos", about.database)
            }
        }

        Spacer(Modifier.height(80.dp))
    }
    }

    if (showColorRoles) {
        val overrides = themeState.colorOverrides
        ColorRolesDialog(
            theme = themeState.theme,
            scheme = scheme,
            tertiaryCurrent = themeState.textColors.tertiary,
            primaryText = overrides.primaryText,
            secondaryText = overrides.secondaryText,
            tertiaryText = overrides.tertiaryText,
            buttonColor = overrides.button,
            onPickPrimaryText = themeState::updatePrimaryTextColor,
            onPickSecondaryText = themeState::updateSecondaryTextColor,
            onPickTertiaryText = themeState::updateTertiaryTextColor,
            onPickButton = themeState::updateButtonColor,
            onClearAll = themeState::clearColorOverrides,
            onDismiss = { showColorRoles = false }
        )
    }
}

/**
 * One role's resolved colour, with its hex value.
 *
 * Prints the *resolved* value rather than the stored pick, so a role that fell
 * back to the theme is visibly the theme's colour instead of a stale swatch the
 * user cannot account for.
 */
@Composable
private fun ColorReadout(label: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            "#${color.toArgbHex()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemeOption(
    theme: AppTheme,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    // The one palette a theme has, so the preview is always the palette the app
    // is actually rendering. It used to pick between two per theme, which meant a
    // preview could show a palette the user would never see.
    val palette = SolidPalettes.forTheme(theme)
    val previewBackground = palette.background
    val previewSurface = palette.surface
    val previewOutline = palette.outline
    val previewOnBackground = palette.onBackground
    val previewOnSurfaceVariant = palette.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(previewBackground)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) palette.secondaryBase else previewOutline,
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
                "${theme.emoji}  ${theme.label}",
                style = MaterialTheme.typography.titleSmall,
                color = previewOnBackground
            )
            Text(
                // Every theme is opaque now, so "sólido" would be noise: the
                // only thing left to say about a theme is whether it is dark.
                if (theme.isDark) "Oscuro" else "Claro",
                style = MaterialTheme.typography.labelSmall,
                color = previewOnSurfaceVariant
            )
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Seleccionado", tint = palette.secondaryBase)
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
