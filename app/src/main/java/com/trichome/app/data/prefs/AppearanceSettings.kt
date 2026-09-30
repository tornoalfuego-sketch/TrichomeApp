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
    val fontWeightIndex: Int = 1,
    /**
     * The three text levels and the button colour, all nullable.
     *
     * Null means "the theme's own", which is the state of every existing install:
     * the keys have never been written, so a user who never opens the picker sees
     * exactly the palette that shipped. That is the whole reason these are
     * nullable rather than seeded with a literal — seeding would repaint the app
     * on upgrade for everybody, with no migration and no way to tell afterwards
     * that the value was a default rather than a choice.
     */
    val primaryTextArgb: Int? = null,
    val secondaryTextArgb: Int? = null,
    val tertiaryTextArgb: Int? = null,
    val buttonColorArgb: Int? = null
)

/**
 * Pure projection from the raw stored values onto [AppearanceSettings].
 *
 * A `null` means "the key was never written", which is what DataStore hands
 * back for a fresh install or a user who never opened this screen. Pulling the
 * defaults out here is what makes "absent key means the shipped default" a
 * fact a JVM test can assert, instead of a behaviour buried in a Flow.
 *
 * A stored `0` is treated the same way, and for the same reason: the old
 * `toArgbInt()` masked the low half of `Color.value` instead of the high half and
 * returned 0 for every colour, so an accent a user picked was written to its key
 * as zero. Reading it as-is means `Color(0)` -- fully transparent -- and
 * `accentArgb ?: DEFAULT` cannot rescue it, because 0 is not null. Verified on a
 * real device: `accent_argb` was 0 with every border and label blending to
 * nothing. A zero is corruption, never a choice, and the rule lives here so the
 * accent and the four colour keys all obey the same one.
 */
fun appearanceSettingsOf(
    themeIndex: Int? = null,
    fontScale: Float? = null,
    accentArgb: Int? = null,
    fontFamilyIndex: Int? = null,
    fontWeightIndex: Int? = null,
    primaryTextArgb: Int? = null,
    secondaryTextArgb: Int? = null,
    tertiaryTextArgb: Int? = null,
    buttonColorArgb: Int? = null
): AppearanceSettings = AppearanceSettings(
    themeIndex = themeIndex ?: ThemeIndex.GREEN,
    fontScale = fontScale ?: 1.0f,
    accentArgb = accentArgb?.takeIf { it != 0 } ?: AccentPalette.DEFAULT_ACCENT_ARG,
    fontFamilyIndex = fontFamilyIndex ?: 0,
    fontWeightIndex = fontWeightIndex ?: 1,
    primaryTextArgb = primaryTextArgb?.takeIf { it != 0 },
    secondaryTextArgb = secondaryTextArgb?.takeIf { it != 0 },
    tertiaryTextArgb = tertiaryTextArgb?.takeIf { it != 0 },
    buttonColorArgb = buttonColorArgb?.takeIf { it != 0 }
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

        // The four user-chosen colours. Added in v1.3.0; every existing install
        // has none of them, which is exactly why they are nullable above.
        val PRIMARY_TEXT = intPreferencesKey("primary_text_argb")
        val SECONDARY_TEXT = intPreferencesKey("secondary_text_argb")
        val TERTIARY_TEXT = intPreferencesKey("tertiary_text_argb")
        val BUTTON_COLOR = intPreferencesKey("button_color_argb")
    }

    val settings: Flow<AppearanceSettings> = context.appearanceDataStore.data.map { prefs ->
        // The raw stored values go in untouched: every rule about what a missing
        // or corrupt value means lives in [appearanceSettingsOf], so a key added
        // later cannot forget to apply it.
        appearanceSettingsOf(
            themeIndex = prefs[Keys.THEME_INDEX],
            fontScale = prefs[Keys.FONT_SCALE],
            accentArgb = prefs[Keys.ACCENT_ARGB],
            fontFamilyIndex = prefs[Keys.FONT_FAMILY],
            fontWeightIndex = prefs[Keys.FONT_WEIGHT],
            primaryTextArgb = prefs[Keys.PRIMARY_TEXT],
            secondaryTextArgb = prefs[Keys.SECONDARY_TEXT],
            tertiaryTextArgb = prefs[Keys.TERTIARY_TEXT],
            buttonColorArgb = prefs[Keys.BUTTON_COLOR]
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

    suspend fun setPrimaryTextArgb(argb: Int?) = writeColor(Keys.PRIMARY_TEXT, argb)

    suspend fun setSecondaryTextArgb(argb: Int?) = writeColor(Keys.SECONDARY_TEXT, argb)

    suspend fun setTertiaryTextArgb(argb: Int?) = writeColor(Keys.TERTIARY_TEXT, argb)

    suspend fun setButtonColorArgb(argb: Int?) = writeColor(Keys.BUTTON_COLOR, argb)

    /** Clears all four colour choices in one edit, back to the theme's own. */
    suspend fun clearColorOverrides() {
        context.appearanceDataStore.edit { prefs ->
            prefs.remove(Keys.PRIMARY_TEXT)
            prefs.remove(Keys.SECONDARY_TEXT)
            prefs.remove(Keys.TERTIARY_TEXT)
            prefs.remove(Keys.BUTTON_COLOR)
        }
    }

    /**
     * Writes one colour, or removes the key when the value is null.
     *
     * Removing rather than storing a sentinel is what makes "unset" stay
     * distinguishable from "set to something" forever, including for a user who
     * picks a colour, changes theme, and comes back.
     */
    private suspend fun writeColor(key: androidx.datastore.preferences.core.Preferences.Key<Int>, argb: Int?) {
        context.appearanceDataStore.edit { prefs ->
            if (argb == null) prefs.remove(key) else prefs[key] = argb
        }
    }
}
