package com.trichome.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Theme indexes. */
object ThemeIndex {
    const val GREEN = 0
    const val AUTUMN = 1
    const val NIGHT = 2
    const val SUNNY = 3
}

/** Glassmorphism design tokens. */
data class GlassTokens(
    val glassOpacity: Float = 0.15f,
    val blurRadius: Float = 12f,
    val accentColor: Color = Color(0xFF66BB6A)
)

private val GreenScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA5D6A7),
    onPrimaryContainer = Color(0xFF0B3D0F),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF00332E),
    tertiary = Color(0xFFFFC107),
    background = Color(0xFF152315),
    surface = Color(0xFF1B2A1B),
    surfaceVariant = Color(0xFF243824),
    error = Color(0xFFB00020),
    onError = Color.White,
    outline = Color(0xFF4E5D4E)
)

private val AutumnScheme = lightColorScheme(
    primary = Color(0xFFE65100),
    onPrimary = Color(0xFFFFF8E1),
    primaryContainer = Color(0xFFFFCC80),
    onPrimaryContainer = Color(0xFF3E2723),
    secondary = Color(0xFFFF9800),
    onSecondary = Color(0xFF3E2723),
    secondaryContainer = Color(0xFFFFE0B2),
    onSecondaryContainer = Color(0xFF4E1F00),
    tertiary = Color(0xFF8D6E63),
    background = Color(0xFF2E1B12),
    surface = Color(0xFF3E2723),
    surfaceVariant = Color(0xFF4E342E),
    error = Color(0xFFB00020),
    onError = Color.White,
    outline = Color(0xFF6D4C41)
)

private val NightScheme = lightColorScheme(
    primary = Color(0xFF00BCD4),
    onPrimary = Color(0xFF0D1B2A),
    primaryContainer = Color(0xFF80DEEA),
    onPrimaryContainer = Color(0xFF00222A),
    secondary = Color(0xFF7C4DFF),
    onSecondary = Color(0xFF0D1B2A),
    secondaryContainer = Color(0xFFB39DDB),
    onSecondaryContainer = Color(0xFF1A0340),
    tertiary = Color(0xFFE0E0FF),
    background = Color(0xFF070D17),
    surface = Color(0xFF0D1B2A),
    surfaceVariant = Color(0xFF1A237E),
    error = Color(0xFFCF6679),
    onError = Color.White,
    outline = Color(0xFF39445A)
)

private val SunnyScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA5D6A7),
    onPrimaryContainer = Color(0xFF0B3D0F),
    secondary = Color(0xFFFF7043),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFCCBC),
    onSecondaryContainer = Color(0xFF4E1600),
    tertiary = Color(0xFF26A69A),
    background = Color(0xFFF4FAF1),
    surface = Color(0xFFE8F5E9),
    surfaceVariant = Color(0xFFDCEDC8),
    error = Color(0xFFB00020),
    onError = Color.White,
    outline = Color(0xFF8FA68A)
)

private val AllSchemes = listOf(GreenScheme, AutumnScheme, NightScheme, SunnyScheme)

/**
 * Runtime holder for the appearance preferences. Persists through
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

    var accentColor by mutableStateOf(Color(0xFF66BB6A))
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** One-shot collection of the persisted preferences. */
    suspend fun collectFromRepository() {
        val container = appContainer ?: return
        container.appearanceSettings.settings.collect { prefs ->
            selectedColorIndex = prefs.themeIndex
            fontScale = prefs.fontScale
            glassTokens = GlassTokens(
                glassOpacity = prefs.glassOpacity,
                blurRadius = prefs.blurRadius,
                accentColor = Color(prefs.accentArgb)
            )
            accentColor = Color(prefs.accentArgb)
        }
    }

    fun selectColor(index: Int) {
        selectedColorIndex = index.coerceIn(0, AllSchemes.size - 1)
        scope.launch { appContainer?.appearanceSettings?.setThemeIndex(selectedColorIndex) }
    }

    fun setGlassOpacity(opacity: Float) {
        val v = opacity.coerceIn(0.05f, 0.50f)
        glassTokens = glassTokens.copy(glassOpacity = v)
        scope.launch { appContainer?.appearanceSettings?.setGlassOpacity(v) }
    }

    fun setBlurRadius(radius: Float) {
        val v = radius.coerceIn(0f, 32f)
        glassTokens = glassTokens.copy(blurRadius = v)
        scope.launch { appContainer?.appearanceSettings?.setBlurRadius(v) }
    }

    fun updateAccentColor(color: Color) {
        accentColor = color
        glassTokens = glassTokens.copy(accentColor = color)
        scope.launch { appContainer?.appearanceSettings?.setAccentArgb(color.value.toLong().toInt()) }
    }

    fun updateFontScale(scale: Float) {
        val v = scale.coerceIn(0.85f, 1.30f)
        fontScale = v
        scope.launch { appContainer?.appearanceSettings?.setFontScale(v) }
    }

    fun colorScheme(): ColorScheme = AllSchemes[selectedColorIndex]
}

@Composable
fun buildTypography(fontScale: Float): Typography = Typography(
    displayLarge = TextStyle(fontSize = 57.sp * fontScale, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 45.sp * fontScale, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontSize = 36.sp * fontScale, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontSize = 32.sp * fontScale, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 28.sp * fontScale, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 24.sp * fontScale, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp * fontScale, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp * fontScale, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp * fontScale, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp * fontScale, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp * fontScale, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp * fontScale, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp * fontScale, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp * fontScale, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp * fontScale, fontWeight = FontWeight.Medium)
)

@Composable
fun TrichomeTheme(
    themeState: TrichomeThemeState,
    content: @Composable () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val baseScheme = themeState.colorScheme()
    // The "background" backdrop follows the theme; panels add translucency on top.
    val effectiveScheme = baseScheme.copy(
        surface = baseScheme.surface,
        background = baseScheme.background
    )
    val typography = buildTypography(themeState.fontScale)

    MaterialTheme(
        colorScheme = effectiveScheme,
        typography = typography,
        content = content
    )
}