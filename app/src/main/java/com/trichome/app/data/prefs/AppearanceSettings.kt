package com.trichome.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trichome.app.ui.theme.AccentPalette
import com.trichome.app.ui.theme.ThemeIndex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance_preferences")

/**
 * Persisted appearance preferences: theme, accent and typography.
 *
 * The three translucency preference keys of the previous release are no longer
 * read here, but their values are deliberately left in the stored preferences
 * file. Removing a key from this class does not remove it from an installed
 * install, and there is nothing to gain from a one-shot migration that rewrites
 * three orphaned integers: they are never read, they cost nothing, and
 * rewriting a user's preferences on upgrade is a risk with no reward.
 */
data class AppearanceSettings(
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
 * defaults out here is what makes "absent key means the shipped default" a
 * fact a JVM test can assert, instead of a behaviour buried in a Flow.
 */
fun appearanceSettingsOf(
    themeIndex: Int? = null,
    fontScale: Float? = null,
    accentArgb: Int? = null,
    fontFamilyIndex: Int? = null,
    fontWeightIndex: Int? = null
): AppearanceSettings = AppearanceSettings(
    themeIndex = themeIndex ?: ThemeIndex.GREEN,
    fontScale = fontScale ?: 1.0f,
    accentArgb = accentArgb ?: AccentPalette.DEFAULT_ACCENT_ARG,
    fontFamilyIndex = fontFamilyIndex ?: 0,
    fontWeightIndex = fontWeightIndex ?: 1
)

/**
 * Persists the appearance settings and reads them as a Flow.
 */
class AppearanceSettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_INDEX = intPreferencesKey("theme_index")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val ACCENT_ARGB = intPreferencesKey("accent_argb")
        val FONT_FAMILY = intPreferencesKey("font_family")
        val FONT_WEIGHT = intPreferencesKey("font_weight")
    }

    val settings: Flow<AppearanceSettings> = context.appearanceDataStore.data.map { prefs ->
        appearanceSettingsOf(
            themeIndex = prefs[Keys.THEME_INDEX],
            fontScale = prefs[Keys.FONT_SCALE],
            accentArgb = prefs[Keys.ACCENT_ARGB],
            fontFamilyIndex = prefs[Keys.FONT_FAMILY],
            fontWeightIndex = prefs[Keys.FONT_WEIGHT]
        )
    }

    suspend fun current(): AppearanceSettings = settings.first()

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
