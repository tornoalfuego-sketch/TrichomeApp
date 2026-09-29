package com.trichome.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/* ─────────────────────────── Theme catalogue ──────────────────────────── */

/** Index kept for DataStore/back-compat with previous releases. */
object ThemeIndex {
    const val GREEN = 0
    const val AUTUMN = 1
    const val NIGHT = 2
    const val SUNNY = 3
}

/** Selectable font families. Only generic families are used, so no font assets ship. */
enum class AppFontFamily(val label: String, val family: FontFamily) {
    SANS("Sans Serif", FontFamily.SansSerif),
    SERIF("Serif", FontFamily.Serif),
    MONO("Monoespaciada", FontFamily.Monospace),
    SCRIPT("Manuscrita", FontFamily.Cursive)
}

/** Selectable global font weights. */
enum class AppFontWeight(val label: String, val weight: FontWeight) {
    LIGHT("Ligera", FontWeight.Light),
    NORMAL("Normal", FontWeight.Normal),
    MEDIUM("Media", FontWeight.Medium),
    SEMIBOLD("Seminegrita", FontWeight.SemiBold),
    BOLD("Negrita", FontWeight.Bold)
}

/**
 * A complete, self-contained palette.
 *
 * [isDark] decides whether the scheme is built with `darkColorScheme` or
 * `lightColorScheme`. Every foreground colour is declared explicitly: deriving
 * them from the Material defaults is what made the dark themes unreadable,
 * because a dark background paired with `lightColorScheme` produced dark
 * `onBackground` / `onSurface` text on a dark surface.
 */
data class AppTheme(
    val index: Int,
    val label: String,
    val emoji: String,
    val isDark: Boolean,
    /** App backdrop. Deliberately much further from [surface] than a glass panel. */
    val background: Color,
    /** Panel base. Clearly lighter (dark themes) or darker (light theme) than [background]. */
    val surface: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val secondaryBase: Color,
    val tertiaryBase: Color,
    val error: Color,
    val onError: Color = Color.White
) {
    /** Text colour that stays readable on top of [background]. */
    val onBackground: Color get() = if (isDark) Color(0xFFE9F1EA) else Color(0xFF12180F)
    val onSurface: Color get() = if (isDark) Color(0xFFE9F1EA) else Color(0xFF12180F)
    val onSurfaceVariant: Color get() = if (isDark) Color(0xFFB6C4B8) else Color(0xFF44503F)

    companion object {
        val GREEN = AppTheme(
            index = ThemeIndex.GREEN,
            label = "Brote Verde",
            emoji = "🌿",
            isDark = true,
            background = Color(0xFF0A120C),
            surface = Color(0xFF1A2A1D),
            surfaceVariant = Color(0xFF243527),
            outline = Color(0xFF43573F),
            secondaryBase = Color(0xFF7FD1A0),
            tertiaryBase = Color(0xFFFFC857),
            error = Color(0xFFFF8A80)
        )

        val AUTUMN = AppTheme(
            index = ThemeIndex.AUTUMN,
            label = "Cosecha de Otoño",
            emoji = "🍂",
            isDark = true,
            background = Color(0xFF140A05),
            surface = Color(0xFF2C1A10),
            surfaceVariant = Color(0xFF3B2416),
            outline = Color(0xFF6B4A32),
            secondaryBase = Color(0xFFFFB86B),
            tertiaryBase = Color(0xFFFFD166),
            error = Color(0xFFFF8A80)
        )

        val NIGHT = AppTheme(
            index = ThemeIndex.NIGHT,
            label = "Cuidado Nocturno",
            emoji = "🌙",
            isDark = true,
            background = Color(0xFF05070F),
            surface = Color(0xFF111726),
            surfaceVariant = Color(0xFF1B2438),
            outline = Color(0xFF38455F),
            secondaryBase = Color(0xFF8AB4F8),
            tertiaryBase = Color(0xFFC7A8FF),
            error = Color(0xFFFF8A80)
        )

        val SUNNY = AppTheme(
            index = ThemeIndex.SUNNY,
            label = "Invernadero Soleado",
            emoji = "☀️",
            isDark = false,
            background = Color(0xFFEFF5E9),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFDCE8D2),
            outline = Color(0xFF9DB095),
            secondaryBase = Color(0xFFB4631C),
            tertiaryBase = Color(0xFF2F7D32),
            error = Color(0xFFB3261E)
        )

        val ALL = listOf(GREEN, AUTUMN, NIGHT, SUNNY)
    }
}

/**
 * Builds the Material 3 scheme for a theme, overlaying the user accent onto the
 * primary/secondary/tertiary roles so that picking an accent actually changes
 * the UI. Foreground colours are re-derived from the accent luminance so the
 * contrast never collapses.
 */
fun schemeFor(theme: AppTheme, accent: Color): ColorScheme {
    val onAccent = if (accent.luminance() > 0.45f) Color(0xFF101410) else Color.White
    val accentContainer = blend(accent, theme.surface, 0.65f)
    val onAccentContainer = if (accentContainer.luminance() > 0.45f) Color(0xFF101410) else Color.White

    val build: (
        primary: Color, onPrimary: Color, primaryContainer: Color, onPrimaryContainer: Color,
        secondary: Color, onSecondary: Color, secondaryContainer: Color, onSecondaryContainer: Color,
        tertiary: Color, onTertiary: Color, tertiaryContainer: Color, onTertiaryContainer: Color,
        background: Color, onBackground: Color, surface: Color, onSurface: Color,
        surfaceVariant: Color, onSurfaceVariant: Color, outline: Color,
        error: Color, onError: Color
    ) -> ColorScheme

    if (theme.isDark) {
        build = { p, op, pc, opc, s, os, sc, osc, t, ot, tc, otc, bg, ob, su, osu, sv, osv, ol, er, oer ->
            darkColorScheme(
                primary = p, onPrimary = op, primaryContainer = pc, onPrimaryContainer = opc,
                secondary = s, onSecondary = os, secondaryContainer = sc, onSecondaryContainer = osc,
                tertiary = t, onTertiary = ot, tertiaryContainer = tc, onTertiaryContainer = otc,
                background = bg, onBackground = ob, surface = su, onSurface = osu,
                surfaceVariant = sv, onSurfaceVariant = osv, outline = ol,
                error = er, onError = oer
            )
        }
    } else {
        build = { p, op, pc, opc, s, os, sc, osc, t, ot, tc, otc, bg, ob, su, osu, sv, osv, ol, er, oer ->
            lightColorScheme(
                primary = p, onPrimary = op, primaryContainer = pc, onPrimaryContainer = opc,
                secondary = s, onSecondary = os, secondaryContainer = sc, onSecondaryContainer = osc,
                tertiary = t, onTertiary = ot, tertiaryContainer = tc, onTertiaryContainer = otc,
                background = bg, onBackground = ob, surface = su, onSurface = osu,
                surfaceVariant = sv, onSurfaceVariant = osv, outline = ol,
                error = er, onError = oer
            )
        }
    }

    val secondary = if (theme.isDark) theme.secondaryBase else theme.secondaryBase.darken(0.25f)
    val tertiary = if (theme.isDark) theme.tertiaryBase else theme.tertiaryBase.darken(0.3f)

    return build(
        accent, onAccent, accentContainer, onAccentContainer,
        secondary, readableOn(secondary), blend(secondary, theme.surface, 0.7f), readableOn(blend(secondary, theme.surface, 0.7f)),
        tertiary, readableOn(tertiary), blend(tertiary, theme.surface, 0.7f), readableOn(blend(tertiary, theme.surface, 0.7f)),
        theme.background, theme.onBackground,
        theme.surface, theme.onSurface,
        theme.surfaceVariant, theme.onSurfaceVariant,
        theme.outline,
        theme.error, theme.onError
    )
}

/** Black or white, whichever stays readable on [color]. */
fun readableOn(color: Color): Color =
    if (color.luminance() > 0.45f) Color(0xFF101410) else Color.White

private fun blend(foreground: Color, background: Color, ratio: Float): Color {
    val r = ratio.coerceIn(0f, 1f)
    return Color(
        red = foreground.red * r + background.red * (1 - r),
        green = foreground.green * r + background.green * (1 - r),
        blue = foreground.blue * r + background.blue * (1 - r),
        alpha = 1f
    )
}

private fun Color.darken(factor: Float): Color = blend(Color.Black, this, factor.coerceIn(0f, 1f))

/* ─────────────────────────── Glass tokens ─────────────────────────────── */

data class GlassTokens(
    val glassOpacity: Float = 0.15f,
    val blurRadius: Float = 12f,
    val accentColor: Color = Color(0xFF66BB6A)
)

/* ─────────────────────────── Typography ───────────────────────────────── */

private val LineHeightStyleCompat = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

/**
 * Builds the full Material typography from the user preferences.
 * [scale] is the 0.85–1.30 size multiplier, [family] the typeface and [weight]
 * the base weight applied to every style.
 */
fun buildTypography(
    scale: Float,
    family: AppFontFamily = AppFontFamily.SANS,
    weight: AppFontWeight = AppFontWeight.NORMAL
): Typography {
    fun style(size: Int, lineHeight: Int, w: FontWeight? = null, letterSpacing: Double = 0.0) =
        androidx.compose.ui.text.TextStyle(
            fontFamily = family.family,
            fontWeight = w ?: weight.weight,
            fontSize = size.sp * scale,
            lineHeight = lineHeight.sp * scale,
            letterSpacing = letterSpacing.sp,
            lineHeightStyle = LineHeightStyleCompat
        )

    return Typography(
        displayLarge = style(57, 64, weight.weight, (-0.25)),
        displayMedium = style(45, 52),
        displaySmall = style(36, 44),
        headlineLarge = style(32, 40),
        headlineMedium = style(28, 36),
        headlineSmall = style(24, 32),
        titleLarge = style(22, 28, weight.weight.lift()),
        titleMedium = style(16, 24, weight.weight.lift(), 0.15),
        titleSmall = style(14, 20, weight.weight.lift(), 0.1),
        bodyLarge = style(16, 24, letterSpacing = 0.5),
        bodyMedium = style(14, 20, letterSpacing = 0.25),
        bodySmall = style(12, 16, letterSpacing = 0.4),
        labelLarge = style(14, 20, weight.weight.lift(), 0.1),
        labelMedium = style(12, 16, weight.weight.lift(), 0.5),
        labelSmall = style(11, 16, weight.weight.lift(), 0.5)
    )
}

private fun FontWeight.lift(): FontWeight = when (this) {
    FontWeight.Light -> FontWeight.Normal
    FontWeight.Normal -> FontWeight.Medium
    FontWeight.Medium -> FontWeight.SemiBold
    else -> this
}

/* ─────────────────────────── Runtime state ────────────────────────────── */

/**
 * Runtime holder for the appearance preferences, persisted through
 * [com.trichome.app.data.prefs.AppearanceSettingsRepository].
 */
class TrichomeThemeState(
    private val appContainer: com.trichome.app.di.AppContainer? = null
) {
    var glassTokens by mutableStateOf(GlassTokens())
        private set

    var fontScale by mutableStateOf(1.0f)
        private set

    var selectedColorIndex by mutableStateOf(ThemeIndex.GREEN)
        private set

    var accentColor by mutableStateOf(Color(0xFF4CAF50))
        private set

    var fontFamilyIndex by mutableStateOf(0)
        private set

    var fontWeightIndex by mutableStateOf(AppFontWeight.NORMAL.ordinal)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val theme: AppTheme
        get() = AppTheme.ALL.firstOrNull { it.index == selectedColorIndex } ?: AppTheme.GREEN

    val fontFamily: AppFontFamily
        get() = AppFontFamily.entries.getOrElse(fontFamilyIndex) { AppFontFamily.SANS }

    val fontWeight: AppFontWeight
        get() = AppFontWeight.entries.getOrElse(fontWeightIndex) { AppFontWeight.NORMAL }

    /** One-shot collection of the persisted preferences. */
    suspend fun collectFromRepository() {
        val container = appContainer ?: return
        container.appearanceSettings.settings.collect { prefs ->
            selectedColorIndex = prefs.themeIndex.coerceIn(AppTheme.ALL.indices)
            fontScale = prefs.fontScale
            accentColor = Color(prefs.accentArgb)
            fontFamilyIndex = prefs.fontFamilyIndex.coerceIn(AppFontFamily.entries.indices)
            fontWeightIndex = prefs.fontWeightIndex.coerceIn(AppFontWeight.entries.indices)
            glassTokens = GlassTokens(
                glassOpacity = prefs.glassOpacity,
                blurRadius = prefs.blurRadius,
                accentColor = Color(prefs.accentArgb)
            )
        }
    }

    fun selectColor(index: Int) {
        val safe = index.coerceIn(AppTheme.ALL.indices)
        selectedColorIndex = safe
        scope.launch { appContainer?.appearanceSettings?.setThemeIndex(safe) }
    }

    fun setGlassOpacity(opacity: Float) {
        val v = opacity.coerceIn(0.05f, 0.55f)
        glassTokens = glassTokens.copy(glassOpacity = v)
        scope.launch { appContainer?.appearanceSettings?.setGlassOpacity(v) }
    }

    fun setBlurRadius(radius: Float) {
        val v = radius.coerceIn(0f, 32f)
        glassTokens = glassTokens.copy(blurRadius = v)
        scope.launch { appContainer?.appearanceSettings?.setBlurRadius(v) }
    }

    /** The accent is the single source of truth for the `primary` role. */
    fun updateAccentColor(color: Color) {
        val argb = color.toArgbInt()
        accentColor = color
        glassTokens = glassTokens.copy(accentColor = color)
        scope.launch { appContainer?.appearanceSettings?.setAccentArgb(argb) }
    }

    fun updateFontScale(scale: Float) {
        val v = scale.coerceIn(0.85f, 1.30f)
        fontScale = v
        scope.launch { appContainer?.appearanceSettings?.setFontScale(v) }
    }

    fun updateFontFamily(index: Int) {
        val safe = index.coerceIn(AppFontFamily.entries.indices)
        fontFamilyIndex = safe
        scope.launch { appContainer?.appearanceSettings?.setFontFamilyIndex(safe) }
    }

    fun updateFontWeight(index: Int) {
        val safe = index.coerceIn(AppFontWeight.entries.indices)
        fontWeightIndex = safe
        scope.launch { appContainer?.appearanceSettings?.setFontWeightIndex(safe) }
    }

    fun colorScheme(): ColorScheme = schemeFor(theme, accentColor)

    fun typography(): Typography = buildTypography(fontScale, fontFamily, fontWeight)
}

/* ─────────────────────────── ARGB helpers ─────────────────────────────── */

/**
 * Converts a Compose [Color] to a packed 32-bit ARGB int.
 *
 * `Color.value` is a `ULong` holding the RGBA bits in the low 32 positions, so
 * masking is the only safe way to get a Java `int` colour back.
 */
fun Color.toArgbInt(): Int = (value.toLong() and 0xFFFFFFFFL).toInt()

/** Hex string for the accent picker readout, e.g. `FF2E7D32`. */
fun Color.toArgbHex(): String = toArgbInt().toUInt().toString(16).uppercase().padStart(8, '0')

/**
 * Applies the appearance state to the whole tree.
 *
 * The colour scheme and the typography are recomputed on every preference
 * change, so a theme, accent, typeface or size tweak is reflected immediately
 * without recreating the activity.
 */
@Composable
fun TrichomeTheme(
    themeState: TrichomeThemeState,
    content: @Composable () -> Unit
) {
    val scheme = themeState.colorScheme()
    val typography = themeState.typography()
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        val context = androidx.compose.ui.platform.LocalContext.current
        SideEffect {
            (context as? android.app.Activity)?.window?.let { window ->
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = typography,
        content = content
    )
}
