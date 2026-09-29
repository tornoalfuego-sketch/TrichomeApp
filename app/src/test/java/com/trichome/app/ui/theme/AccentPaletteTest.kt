package com.trichome.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.trichome.app.data.prefs.appearanceSettingsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the single accent default.
 *
 * The bug: four different greens shipped as "the default accent".
 * `AppearanceSettings` stored `0xFF2E7D32`, `TrichomeThemeState` and `GlassConfig`
 * fell back to `0xFF4CAF50`, and `GlassTokens` and the orb background to
 * `0xFF66BB6A`. Nothing asserted that, so the two dead values were free to rot —
 * and any new call site reading `GlassTokens()` got a different green from every
 * other surface.
 *
 * `0xFF2E7D32` is the value that wins on a fresh install, because
 * `TrichomeThemeState.collectFromRepository()` reads it out of
 * `appearanceSettingsOf`. It is therefore the default, and these tests pin the
 * literal so "consolidate onto one constant" cannot quietly become "recolour the
 * app".
 */
class AccentPaletteTest {

    @Test
    fun theShippedDefaultIsTheGreenUsersAlreadyHave() {
        assertEquals(
            "changing the default accent would silently repaint every install",
            0xFF2E7D32.toInt(),
            AccentPalette.DEFAULT_ACCENT_ARG
        )
        assertEquals(Color(0xFF2E7D32), AccentPalette.DEFAULT_ACCENT)
    }

    @Test
    fun everyDefaultResolvesToThatOneGreen() {
        // The four sites that used to disagree.
        assertEquals(
            "GlassTokens fell back to 0xFF66BB6A",
            AccentPalette.DEFAULT_ACCENT,
            GlassTokens().accentColor
        )
        assertEquals(
            "GlassConfig fell back to 0xFF4CAF50",
            AccentPalette.DEFAULT_ACCENT,
            GlassConfig().accentColor
        )
        assertEquals(
            "TrichomeThemeState fell back to 0xFF4CAF50",
            AccentPalette.DEFAULT_ACCENT,
            TrichomeThemeState().accentColor
        )
        assertEquals(
            "AppearanceSettings stored 0xFF2E7D32 as a literal",
            AccentPalette.DEFAULT_ACCENT_ARG,
            appearanceSettingsOf().accentArgb
        )
    }

    @Test
    fun theDefaultsAreOneValueNotFourEqualOnes() {
        // Guards the assertions above against becoming a tautology: if the palette
        // were the only place the value existed, collapsing the four sites would
        // prove nothing. `Color.value` packs the colour into a ULong with a colour
        // space tag, so it is compared through `Color` and not unpacked by hand.
        val resolved = setOf(
            AccentPalette.DEFAULT_ACCENT,
            GlassTokens().accentColor,
            GlassConfig().accentColor,
            TrichomeThemeState().accentColor,
            Color(appearanceSettingsOf().accentArgb)
        )
        assertEquals("the defaults must be one single colour", 1, resolved.size)
        assertEquals(
            "and it must round-trip through the Int the DataStore stores",
            AccentPalette.DEFAULT_ACCENT,
            Color(AccentPalette.DEFAULT_ACCENT_ARG)
        )
    }

    @Test
    fun theDefaultAccentSurvivesTheIntRoundTripDataStoreUses() {
        // `accentArgb` is persisted as an Int. A Color built from the literal and
        // one built from that Int have to be the same colour, or the first launch
        // after an upgrade would repaint.
        assertEquals(
            AccentPalette.DEFAULT_ACCENT,
            Color(0xFF2E7D32.toInt())
        )
    }

    @Test
    fun theOrbSecondaryIsNotTheAccent() {
        // The two orb hues are decoration. Pinning the secondary stops a future
        // "make everything one constant" pass from collapsing them together.
        assertTrue(
            "the orb secondary must stay distinct from the accent",
            AccentPalette.ORB_SECONDARY_ARG != AccentPalette.DEFAULT_ACCENT_ARG
        )
    }

    @Test
    fun theSelectablePaletteStartsWithTheDefaultAndHasNoDuplicates() {
        assertEquals(
            AccentPalette.DEFAULT_ACCENT_ARG,
            AccentPalette.SELECTABLE_ARGB.first()
        )
        assertEquals(
            "the accent picker offered the same colour twice",
            AccentPalette.SELECTABLE_ARGB.size,
            AccentPalette.SELECTABLE_ARGB.distinct().size
        )
    }

    @Test
    fun theOpaqueThemePaletteStaysReadableOnTheDefaultAccent() {
        // The consolidation must not cost contrast: the green is the default accent,
        // so `onPrimary` on it still has to clear the text bar.
        AppTheme.ALL.forEach { theme ->
            val scheme = solidSchemeFor(theme, AccentPalette.DEFAULT_ACCENT)
            val ratio = contrastRatio(scheme.onPrimary, scheme.primary)
            assertTrue(
                "${theme.solidLabel}: onPrimary on the default accent is $ratio:1, " +
                    "need ${MINIMUM_TEXT_CONTRAST}:1",
                ratio >= MINIMUM_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun theEveryAccentSweepStillCoversTheDefault() {
        // GlassConfigTest sweeps this same list for onPrimary contrast; keeping the
        // default inside it means the shipped green is checked there too.
        assertTrue(
            "the accent sweep must include the shipped default",
            AccentPalette.DEFAULT_ACCENT_ARG in AccentPalette.SELECTABLE_ARGB
        )
    }

    @Test
    fun aStoredAccentStillOverridesTheDefault() {
        val custom = 0xFF7C4DFF.toInt()
        val settings = appearanceSettingsOf(accentArgb = custom)
        val config = glassConfigOf(
            enabled = false,
            glassOpacity = settings.glassOpacity,
            blurRadius = settings.blurRadius,
            accentArgb = settings.accentArgb
        )
        assertEquals(Color(custom), config.accentColor)
    }

    @Test
    fun theThemeStateStartsOnTheDefaultSoTheFirstFrameIsNotADifferentGreen() {
        // Before the repository is collected the accent was 0xFF4CAF50 and after it
        // the persisted 0xFF2E7D32, so the app visibly repainted itself on startup.
        assertEquals(AccentPalette.DEFAULT_ACCENT, TrichomeThemeState().accentColor)
        assertEquals(
            AccentPalette.DEFAULT_ACCENT,
            TrichomeThemeState().accentColor
        )
    }
}
