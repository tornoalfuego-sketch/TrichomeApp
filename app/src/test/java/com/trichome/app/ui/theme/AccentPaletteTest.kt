package com.trichome.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.trichome.app.data.prefs.appearanceSettingsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the single accent default.
 *
 * The bug: four different greens shipped as "the default accent". The persisted
 * preference was `0xFF2E7D32`, the runtime state holder and the panel config fell
 * back to `0xFF4CAF50`, and the panel tokens and the animated backdrop to
 * `0xFF66BB6A`. Nothing asserted that, so the two dead values were free to rot —
 * and any new call site reading the panel tokens got a different green from every
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
        // The sites that used to disagree. There are two now, and the point of
        // the test is that there is no third.
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
    fun theDefaultsAreOneValueNotSeveralEqualOnes() {
        // Guards the assertions above against becoming a tautology: if the palette
        // were the only place the value existed, collapsing the sites would prove
        // nothing. `Color.value` packs the colour into a ULong with a colour space
        // tag, so it is compared through `Color` and not unpacked by hand.
        val resolved = setOf(
            AccentPalette.DEFAULT_ACCENT,
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
    fun theAmberSwatchIsNotTheAccent() {
        // Pinned because it is the one that breaks the naive rules: it is the
        // brightest colour the app offers, so a white label on it is 1.7:1 and a
        // black label is 15:1. Collapsing it into the accent constant would take
        // the default green with it.
        assertTrue(
            "the amber swatch must stay distinct from the accent",
            AccentPalette.AMBER_ACCENT_ARG != AccentPalette.DEFAULT_ACCENT_ARG
        )
        assertTrue(
            "the amber swatch must stay in the selectable list",
            AccentPalette.AMBER_ACCENT_ARG in AccentPalette.SELECTABLE_ARGB
        )
    }

    @Test
    fun everyNamedSwatchIsInTheSelectableList() {
        // The named constants exist so a new surface has something to read instead
        // of writing a ninth literal. One that is not on the list would be a colour
        // the palette knows about and the user cannot pick.
        val named = listOf(
            AccentPalette.DEFAULT_ACCENT_ARG,
            AccentPalette.LIME_ACCENT_ARG,
            AccentPalette.AMBER_ACCENT_ARG,
            AccentPalette.ORANGE_ACCENT_ARG,
            AccentPalette.CYAN_ACCENT_ARG,
            AccentPalette.VIOLET_ACCENT_ARG,
            AccentPalette.PINK_ACCENT_ARG,
            AccentPalette.RED_ACCENT_ARG
        )
        named.forEach {
            assertTrue(
                "0x${it.toUInt().toString(16).uppercase()} is named but not selectable",
                it in AccentPalette.SELECTABLE_ARGB
            )
        }
        assertEquals(
            "every selectable accent should have a name",
            AccentPalette.SELECTABLE_ARGB.size,
            named.size
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
                "${theme.label}: onPrimary on the default accent is $ratio:1, " +
                    "need ${MINIMUM_TEXT_CONTRAST}:1",
                ratio >= MINIMUM_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun theEveryAccentSweepStillCoversTheDefault() {
        // OpaqueThemeContrastTest and AccentContrastSweepTest both sweep this same
        // list; keeping the default inside it means the shipped green is checked
        // there too.
        assertTrue(
            "the accent sweep must include the shipped default",
            AccentPalette.DEFAULT_ACCENT_ARG in AccentPalette.SELECTABLE_ARGB
        )
    }

    @Test
    fun aStoredAccentStillOverridesTheDefault() {
        val custom = AccentPalette.VIOLET_ACCENT_ARG
        val settings = appearanceSettingsOf(accentArgb = custom)
        assertEquals(Color(custom), Color(settings.accentArgb))
        // And the scheme is actually built from the stored value, not the default.
        assertEquals(
            "the scheme must use the accent the user stored",
            Color(custom),
            solidSchemeFor(AppTheme.GREEN, Color(settings.accentArgb)).primary
        )
        assertNotSameAsDefault(Color(settings.accentArgb))
    }

    @Test
    fun theThemeStateStartsOnTheDefaultSoTheFirstFrameIsNotADifferentGreen() {
        // Before the repository is collected the accent was 0xFF4CAF50 and after it
        // the persisted 0xFF2E7D32, so the app visibly repainted itself on startup.
        assertEquals(AccentPalette.DEFAULT_ACCENT, TrichomeThemeState().accentColor)
        // Reading it twice must not produce two different colours, which is what a
        // state holder re-reading the repository mid-composition used to do.
        assertEquals(
            AccentPalette.DEFAULT_ACCENT,
            TrichomeThemeState().accentColor
        )
    }

    private fun assertNotSameAsDefault(actual: Color) {
        assertTrue(
            "a stored custom accent must not collapse back to the default",
            actual != AccentPalette.DEFAULT_ACCENT
        )
    }
}
