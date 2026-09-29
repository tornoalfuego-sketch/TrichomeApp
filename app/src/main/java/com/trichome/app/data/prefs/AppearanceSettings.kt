package com.trichome.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trichome.app.ui.theme.ThemeIndex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance_preferences")

data class AppearanceSettings(
    val glassOpacity: Float = 0.15f,
    val blurRadius: Float = 12f,
    val themeIndex: Int = ThemeIndex.GREEN,
    val fontScale: Float = 1.0f,
    val accentArgb: Int = 0xFF2E7D32.toInt(),
    val fontFamilyIndex: Int = 0,
    val fontWeightIndex: Int = 1
)

/**
 * Persists the glassmorphism engine settings and reads them as StateFlows.
 */
class AppearanceSettingsRepository(private val context: Context) {

    private object Keys {
        val GLASS_OPACITY = floatPreferencesKey("glass_opacity")
        val BLUR_RADIUS = floatPreferencesKey("blur_radius")
        val THEME_INDEX = intPreferencesKey("theme_index")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val ACCENT_ARGB = intPreferencesKey("accent_argb")
        val FONT_FAMILY = intPreferencesKey("font_family")
        val FONT_WEIGHT = intPreferencesKey("font_weight")
    }

    val settings: Flow<AppearanceSettings> = context.appearanceDataStore.data.map { prefs ->
        AppearanceSettings(
            glassOpacity = prefs[Keys.GLASS_OPACITY] ?: 0.15f,
            blurRadius = prefs[Keys.BLUR_RADIUS] ?: 12f,
            themeIndex = prefs[Keys.THEME_INDEX] ?: ThemeIndex.GREEN,
            fontScale = prefs[Keys.FONT_SCALE] ?: 1.0f,
            accentArgb = prefs[Keys.ACCENT_ARGB] ?: 0xFF2E7D32.toInt(),
            fontFamilyIndex = prefs[Keys.FONT_FAMILY] ?: 0,
            fontWeightIndex = prefs[Keys.FONT_WEIGHT] ?: 1
        )
    }

    suspend fun current(): AppearanceSettings = settings.first()

    suspend fun setGlassOpacity(value: Float) {
        context.appearanceDataStore.edit { it[Keys.GLASS_OPACITY] = value.coerceIn(0.05f, 0.50f) }
    }

    suspend fun setBlurRadius(value: Float) {
        context.appearanceDataStore.edit { it[Keys.BLUR_RADIUS] = value.coerceIn(0f, 32f) }
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