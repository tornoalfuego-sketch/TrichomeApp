package com.trichome.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trichome.app.ui.theme.AccentPalette
import com.trichome.app.ui.theme.GlassRanges
import com.trichome.app.ui.theme.ThemeIndex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance_preferences")

/**
 * Default for [AppearanceSettings.glassEnabled] when the key is absent.
 *
 * `false`, not `true`: glassmorphism has to be opted into. A user who has
 * never opened this screen gets the opaque, high-contrast themes, which is the
 * legible default.
 */
const val DEFAULT_GLASS_ENABLED = false

/**
 * Persisted appearance preferences.
 *
 * [glassEnabled] defaults to [DEFAULT_GLASS_ENABLED]: glassmorphism has to be
 * opted into, and the opaque themes are the legible default for anyone who
 * never chose.
 */
data class AppearanceSettings(
    val glassEnabled: Boolean = DEFAULT_GLASS_ENABLED,
    val glassOpacity: Float = GlassRanges.OPACITY_DEFAULT,
    val blurRadius: Float = GlassRanges.BLUR_DEFAULT,
    val themeIndex: Int = ThemeIndex.GREEN,
    val fontScale: Float = 1.0f,
    /**
     * Reads [AccentPalette.DEFAULT_ACCENT_ARG], which is the value every existing
     * install already has stored.
     */
    val accentArgb: Int = AccentPalette.DEFAULT_ACCENT_ARG,
    val fontFamilyIndex: Int = 0,
    val fontWeightIndex: Int = 1
)

/**
 * Pure projection from the raw stored values onto [AppearanceSettings].
 *
 * A `null` means "the key was never written", which is what DataStore hands
 * back for a fresh install or a user who never opened this screen. Pulling the
 * defaults out here is what makes "absent key means the opaque themes" a fact a
 * JVM test can assert, instead of a behaviour buried in a Flow.
 */
fun appearanceSettingsOf(
    glassEnabled: Boolean? = null,
    glassOpacity: Float? = null,
    blurRadius: Float? = null,
    themeIndex: Int? = null,
    fontScale: Float? = null,
    accentArgb: Int? = null,
    fontFamilyIndex: Int? = null,
    fontWeightIndex: Int? = null
): AppearanceSettings = AppearanceSettings(
    glassEnabled = glassEnabled ?: DEFAULT_GLASS_ENABLED,
    glassOpacity = GlassRanges.clampOpacity(glassOpacity ?: GlassRanges.OPACITY_DEFAULT),
    blurRadius = GlassRanges.clampBlur(blurRadius ?: GlassRanges.BLUR_DEFAULT),
    themeIndex = themeIndex ?: ThemeIndex.GREEN,
    fontScale = fontScale ?: 1.0f,
    accentArgb = accentArgb ?: AccentPalette.DEFAULT_ACCENT_ARG,
    fontFamilyIndex = fontFamilyIndex ?: 0,
    fontWeightIndex = fontWeightIndex ?: 1
)

/**
 * Persists the glassmorphism engine settings and reads them as StateFlows.
 */
class AppearanceSettingsRepository(private val context: Context) {

    private object Keys {
        val GLASS_ENABLED = booleanPreferencesKey("glass_enabled")
        val GLASS_OPACITY = floatPreferencesKey("glass_opacity")
        val BLUR_RADIUS = floatPreferencesKey("blur_radius")
        val THEME_INDEX = intPreferencesKey("theme_index")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val ACCENT_ARGB = intPreferencesKey("accent_argb")
        val FONT_FAMILY = intPreferencesKey("font_family")
        val FONT_WEIGHT = intPreferencesKey("font_weight")
    }

    val settings: Flow<AppearanceSettings> = context.appearanceDataStore.data.map { prefs ->
        appearanceSettingsOf(
            glassEnabled = prefs[Keys.GLASS_ENABLED],
            glassOpacity = prefs[Keys.GLASS_OPACITY],
            blurRadius = prefs[Keys.BLUR_RADIUS],
            themeIndex = prefs[Keys.THEME_INDEX],
            fontScale = prefs[Keys.FONT_SCALE],
            accentArgb = prefs[Keys.ACCENT_ARGB],
            fontFamilyIndex = prefs[Keys.FONT_FAMILY],
            fontWeightIndex = prefs[Keys.FONT_WEIGHT]
        )
    }

    suspend fun current(): AppearanceSettings = settings.first()

    suspend fun setGlassEnabled(enabled: Boolean) {
        context.appearanceDataStore.edit { it[Keys.GLASS_ENABLED] = enabled }
    }

    suspend fun setGlassOpacity(value: Float) {
        context.appearanceDataStore.edit { it[Keys.GLASS_OPACITY] = GlassRanges.clampOpacity(value) }
    }

    suspend fun setBlurRadius(value: Float) {
        context.appearanceDataStore.edit { it[Keys.BLUR_RADIUS] = GlassRanges.clampBlur(value) }
    }

    suspend fun setThemeIndex(index: Int) {
        context.appearanceDataStore.edit { it[Keys.THEME_INDEX] = index.coerceIn(0, 3) }
    }

    suspend fun setFontScale(value: Float) {
        context.appearanceDataStore.edit { it[Keys.FONT_SCALE] = value.coerceIn(0.85f, 1.30f) }
    }

    suspend fun setAccentArgb(argb: Int) {
        context.appearanceDataStore.edit { it[Keys.ACCENT_ARGB] = argb }
    }

    suspend fun setFontFamilyIndex(index: Int) {
        context.appearanceDataStore.edit { it[Keys.FONT_FAMILY] = index.coerceIn(0, 3) }
    }

    suspend fun setFontWeightIndex(index: Int) {
        context.appearanceDataStore.edit { it[Keys.FONT_WEIGHT] = index.coerceIn(0, 4) }
    }
}