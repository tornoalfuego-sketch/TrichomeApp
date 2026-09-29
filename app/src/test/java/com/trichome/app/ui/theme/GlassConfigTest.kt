package com.trichome.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.trichome.app.data.prefs.appearanceSettingsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the glassmorphism toggle and the opaque themes it selects.
 *
 * The bug this locks down: `GlassCard` took `glassOpacity` / `blurRadius` /
 * `accentColor` as non-null parameters with hardcoded defaults, so roughly a
 * dozen of its call sites passed literals and silently ignored the user's
 * preference. The fix is a `staticCompositionLocalOf` carrying the resolved
 * config plus a solid colour scheme used whenever the flag is off.
 *
 * Everything asserted here is pure colour math, so none of it needs a
 * composition, a Context or Robolectric. Relative luminance and the contrast
 * ratio are computed from the ARGB components by [contrastRatio] in the theme
 * layer — `isReturnDefaultValues = true` is on, so a test that leaned on
 * `android.graphics` or on `Color.luminance()` would silently compare zeros and
 * pass without checking anything.
 */
class GlassConfigTest {

    /** The accent the app ships with; also the value used for every card test. */
    private val accent = Color(0xFF4CAF50)

    /* ── 1. Flag off selects the solid scheme, flag on the translucent one ── */

    /**
     * `ColorScheme` does not override `equals`: every role is a `mutableStateOf`
     * and the class inherits reference equality from `Any`. Comparing two
     * separately built schemes with `assertEquals` therefore fails no matter
     * what the colours are, and `assertNotEquals` passes no matter what they are
     * — a vacuous test. Schemes are compared role by role through
     * [schemeColorsOf], where `Color` is a value class with real equality.
     */
    private fun sameColours(a: androidx.compose.material3.ColorScheme, b: androidx.compose.material3.ColorScheme) =
        schemeColorsOf(a) == schemeColorsOf(b)

    @Test
    fun flagOffSelectsTheSolidScheme() {
        AppTheme.ALL.forEach { theme ->
            val chosen = activeSchemeFor(theme, accent, glassEnabled = false)
            assertTrue(
                "the solid scheme must be chosen for ${theme.solidLabel}",
                sameColours(solidSchemeFor(theme, accent), chosen)
            )
        }
    }

    @Test
    fun flagOnSelectsTheTranslucentScheme() {
        AppTheme.ALL.forEach { theme ->
            val chosen = activeSchemeFor(theme, accent, glassEnabled = true)
            assertTrue(
                "the glass scheme must be chosen for ${theme.label}",
                sameColours(schemeFor(theme, accent), chosen)
            )
        }
    }

    @Test
    fun theTwoSchemesAreNotTheSameLook() {
        // Otherwise "off" would be a no-op flag and the toggle would be theatre.
        // Compared by colour role, not by reference: see sameColours.
        AppTheme.ALL.forEach { theme ->
            val solid = schemeColorsOf(activeSchemeFor(theme, accent, glassEnabled = false))
            val glass = schemeColorsOf(activeSchemeFor(theme, accent, glassEnabled = true))
            val differing = solid.keys.filter { solid[it] != glass[it] }
            assertTrue(
                "solid and glass schemes must differ for ${theme.solidLabel}, " +
                    "but ${solid.size - differing.size} of ${solid.size} roles matched",
                differing.isNotEmpty()
            )
            // The flag has to be more than a repaint of two or three roles.
            assertTrue(
                "the toggle must change most roles for ${theme.solidLabel}, " +
                    "only ${differing.size} of ${solid.size} differ",
                differing.size >= solid.size / 2
            )
        }
    }

    @Test
    fun solidSchemesAreNotAllTheSame() {
        // Guards a copy-paste in the four hand-written opaque palettes.
        val distinct = AppTheme.ALL.map { schemeColorsOf(solidSchemeFor(it, accent)).toString() }.toSet()
        assertEquals("each of the four solid themes must have its own palette", AppTheme.ALL.size, distinct.size)
    }

    /* ── 2. Every solid scheme is fully opaque ────────────────────────────── */

    @Test
    fun everySolidSchemeIsFullyOpaque() {
        for (theme in AppTheme.ALL) {
            val colors = schemeColorsOf(solidSchemeFor(theme, accent))
            colors.forEach { (role, color) ->
                assertEquals(
                    "${theme.solidLabel}/$role must be fully opaque",
                    1f,
                    color.alpha,
                    0f
                )
            }
        }
    }

    @Test
    fun everySolidSchemeRoleIsCovered() {
        // Guards the opacity assertion above against silently becoming vacuous:
        // a role missing from schemeColorsOf would never be checked.
        val roles = schemeColorsOf(solidSchemeFor(AppTheme.NIGHT, accent)).keys
        val expected = listOf(
            "primary", "onPrimary", "primaryContainer", "onPrimaryContainer", "inversePrimary",
            "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
            "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
            "background", "onBackground", "surface", "onSurface",
            "surfaceVariant", "onSurfaceVariant", "surfaceTint",
            "inverseSurface", "inverseOnSurface",
            "error", "onError", "errorContainer", "onErrorContainer",
            "outline", "outlineVariant", "scrim",
            "surfaceBright", "surfaceDim",
            "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest",
            "surfaceContainerLow", "surfaceContainerLowest"
        )
        val missing = expected.filterNot { it in roles }
        assertTrue("schemeColorsOf is missing $missing", missing.isEmpty())
        assertEquals("schemeColorsOf must expose no extra roles", expected.size, roles.size)
    }

    /* ── 3. Contrast of every on* role against its own background ─────────── */

    @Test
    fun onSurfaceContrastsWithSurface() {
        for (theme in AppTheme.ALL) {
            val scheme = solidSchemeFor(theme, accent)
            val ratio = contrastRatio(scheme.onSurface, scheme.surface)
            assertTrue(
                "${theme.solidLabel}: onSurface on surface is $ratio:1, need 4.5:1",
                ratio >= MINIMUM_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun onBackgroundContrastsWithBackground() {
        for (theme in AppTheme.ALL) {
            val scheme = solidSchemeFor(theme, accent)
            val ratio = contrastRatio(scheme.onBackground, scheme.background)
            assertTrue(
                "${theme.solidLabel}: onBackground on background is $ratio:1, need 4.5:1",
                ratio >= MINIMUM_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun onPrimaryContrastsWithPrimaryForEveryAccent() {
        // The accent is user-chosen, so this has to hold for the whole palette
        // and not only for the shipped green.
        val accents = listOf(
            Color(0xFF2E7D32), Color(0xFF7CB342), Color(0xFFFFC107), Color(0xFFE65100),
            Color(0xFF00ACC1), Color(0xFF7C4DFF), Color(0xFFEC407A), Color(0xFFE53935)
        )
        for (theme in AppTheme.ALL) {
            accents.forEach { candidate ->
                val scheme = solidSchemeFor(theme, candidate)
                val ratio = contrastRatio(scheme.onPrimary, scheme.primary)
                assertTrue(
                    "${theme.solidLabel}: onPrimary on accent $candidate is $ratio:1",
                    ratio >= MINIMUM_TEXT_CONTRAST
                )
            }
        }
    }

    @Test
    fun onSurfaceVariantContrastsWithSurfaceVariant() {
        for (theme in AppTheme.ALL) {
            val scheme = solidSchemeFor(theme, accent)
            val ratio = contrastRatio(scheme.onSurfaceVariant, scheme.surfaceVariant)
            assertTrue(
                "${theme.solidLabel}: onSurfaceVariant is $ratio:1, need 4.5:1",
                ratio >= MINIMUM_TEXT_CONTRAST
            )
        }
    }

    /**
     * Borders are not text, so 4.5:1 is the wrong bar for them — but 3:1 is
     * WCAG 1.4.11 and it still matters: the whole point of the opaque themes is
     * a visible edge around each panel. The first hand-written palettes landed at
     * 2.55:1 (NIGHT), 2.65:1 (AUTUMN) and 3.45:1 (GREEN) and every text
     * assertion still passed, because nothing here looked at a non-text role.
     */
    @Test
    fun panelOutlineIsVisibleAgainstItsOwnSurface() {
        for (theme in AppTheme.ALL) {
            val scheme = solidSchemeFor(theme, accent)
            val ratio = contrastRatio(scheme.outline, scheme.surface)
            assertTrue(
                "${theme.solidLabel}: outline on surface is $ratio:1, " +
                    "need ${MINIMUM_NON_TEXT_CONTRAST}:1 for a visible panel edge",
                ratio >= MINIMUM_NON_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun everySolidDividerRoleClearsTheNonTextBar() {
        // outlineVariant separates rows inside a panel, so it has to be visible
        // against both surfaces it can land on.
        for (theme in AppTheme.ALL) {
            val scheme = solidSchemeFor(theme, accent)
            listOf(
                "surface" to scheme.surface,
                "surfaceVariant" to scheme.surfaceVariant
            ).forEach { (name, background) ->
                val ratio = contrastRatio(scheme.outlineVariant, background)
                assertTrue(
                    "${theme.solidLabel}: outlineVariant on $name is $ratio:1, " +
                        "need ${MINIMUM_NON_TEXT_CONTRAST}:1",
                    ratio >= MINIMUM_NON_TEXT_CONTRAST
                )
            }
        }
    }

    @Test
    fun readableOnStrictAlwaysClearsTheThreshold() {
        // onSecondary / onTertiary / on*Container are resolved from arbitrary
        // colours, so the black-or-white choice has to clear 4.5:1 for *every*
        // input, not just the ones that happen to ship.
        //
        // The sweep over greys is exhaustive, not a sample: the decision depends
        // only on relative luminance, and a grey of value v has luminance
        // linearize(v), so the greys cover the whole 0..1 luminance range in
        // which the black/white crossover sits.
        var step = 0
        var worst = Float.MAX_VALUE
        var worstProbe: Color? = null
        while (step <= 255) {
            val v = step / 255f
            val probe = Color(v, v, v)
            val ratio = contrastRatio(readableOnStrict(probe), probe)
            if (ratio < worst) {
                worst = ratio
                worstProbe = probe
            }
            step++
        }

        assertTrue("no luminance was probed", worstProbe != null)
        assertTrue(
            "worst case is $worst:1 at grey $worstProbe, need ${MINIMUM_TEXT_CONTRAST}:1",
            worst >= MINIMUM_TEXT_CONTRAST
        )
    }

    /* ── 4. One clamp range, used by every site ───────────────────────────── */

    @Test
    fun clampRangeIsTheSameObjectEverywhere() {
        // The repository, the state holder and the slider must agree, or values
        // in the gap are silently dropped on restart.
        assertTrue(GlassRanges.opacity === GlassRanges.opacity)
        assertEquals(0.05f, GlassRanges.OPACITY_MIN, 0f)
        assertEquals(0.55f, GlassRanges.OPACITY_MAX, 0f)
        assertEquals(GlassRanges.OPACITY_MIN, GlassRanges.opacity.start, 0f)
        assertEquals(GlassRanges.OPACITY_MAX, GlassRanges.opacity.endInclusive, 0f)
        assertEquals(0f, GlassRanges.BLUR_MIN, 0f)
        assertEquals(32f, GlassRanges.BLUR_MAX, 0f)
        assertEquals(GlassRanges.BLUR_MIN, GlassRanges.blur.start, 0f)
        assertEquals(GlassRanges.BLUR_MAX, GlassRanges.blur.endInclusive, 0f)
    }

    @Test
    fun clampHoldsAtBothEnds() {
        assertEquals(0.05f, GlassRanges.clampOpacity(-5f), 0f)
        assertEquals(0.05f, GlassRanges.clampOpacity(0.05f), 0f)
        assertEquals(0.55f, GlassRanges.clampOpacity(0.55f), 0f)
        assertEquals(0.55f, GlassRanges.clampOpacity(99f), 0f)
        // The historical disagreement lived in exactly this window.
        assertEquals(0.53f, GlassRanges.clampOpacity(0.53f), 0f)

        assertEquals(0f, GlassRanges.clampBlur(-1f), 0f)
        assertEquals(0f, GlassRanges.clampBlur(0f), 0f)
        assertEquals(32f, GlassRanges.clampBlur(32f), 0f)
        assertEquals(32f, GlassRanges.clampBlur(64f), 0f)
    }

    @Test
    fun everyValueInsideTheRangeSurvivesTheClampUnchanged() {
        var value = GlassRanges.OPACITY_MIN
        while (value <= GlassRanges.OPACITY_MAX) {
            assertEquals(
                "$value must survive the clamp",
                value,
                GlassRanges.clampOpacity(value),
                0f
            )
            value += 0.05f
        }
    }

    /* ── 5. GlassConfig resolves overrides ─────────────────────────────────── */

    @Test
    fun configResolvesEveryFieldToTheLocalWhenNoOverrideIsPassed() {
        val config = GlassConfig(
            enabled = true,
            glassOpacity = 0.31f,
            blurRadius = 21f,
            accentColor = Color(0xFF00ACC1)
        )
        val resolved = config.resolve()
        assertTrue(resolved.enabled)
        assertEquals(0.31f, resolved.glassOpacity, 0f)
        assertEquals(21f, resolved.blurRadius, 0f)
        assertEquals(Color(0xFF00ACC1), resolved.accentColor)
    }

    @Test
    fun configResolvesEachFieldToTheOverrideWhenOneIsPassed() {
        val config = GlassConfig(
            enabled = true,
            glassOpacity = 0.31f,
            blurRadius = 21f,
            accentColor = Color(0xFF00ACC1)
        )

        val opacity = config.resolve(glassOpacity = 0.10f)
        assertEquals(0.10f, opacity.glassOpacity, 0f)
        assertEquals(21f, opacity.blurRadius, 0f)
        assertEquals(Color(0xFF00ACC1), opacity.accentColor)

        val blur = config.resolve(blurRadius = 4f)
        assertEquals(0.31f, blur.glassOpacity, 0f)
        assertEquals(4f, blur.blurRadius, 0f)
        assertEquals(Color(0xFF00ACC1), blur.accentColor)

        val accent = config.resolve(accentColor = Color(0xFFEC407A))
        assertEquals(0.31f, accent.glassOpacity, 0f)
        assertEquals(21f, accent.blurRadius, 0f)
        assertEquals(Color(0xFFEC407A), accent.accentColor)
    }

    @Test
    fun anOverrideNeverClearsTheEnabledFlag() {
        // This is what makes a call site that still passes an opacity harmless:
        // the flag is read from the local, never from the override.
        val config = GlassConfig(enabled = false, glassOpacity = 0.3f, blurRadius = 8f)
        assertFalse(config.resolve(glassOpacity = 0.12f, blurRadius = 2f).enabled)
        assertTrue(config.copy(enabled = true).resolve(glassOpacity = 0.12f).enabled)
    }

    /* ── 6. The persisted default is OFF ───────────────────────────────────── */

    @Test
    fun anAbsentGlassEnabledKeyReadsAsDisabled() {
        val settings = appearanceSettingsOf(glassEnabled = null)
        assertFalse("an absent key must mean the opaque themes", settings.glassEnabled)
        assertFalse(glassConfigOf(
            enabled = settings.glassEnabled,
            glassOpacity = settings.glassOpacity,
            blurRadius = settings.blurRadius,
            accentArgb = settings.accentArgb
        ).enabled)
    }

    @Test
    fun anExplicitlyPersistedTrueIsHonoured() {
        val settings = appearanceSettingsOf(glassEnabled = true)
        assertTrue(settings.glassEnabled)
        assertTrue(glassConfigOf(
            enabled = settings.glassEnabled,
            glassOpacity = settings.glassOpacity,
            blurRadius = settings.blurRadius,
            accentArgb = settings.accentArgb
        ).enabled)
    }

    @Test
    fun thePersistedDefaultsFeedTheConfig() {
        val settings = appearanceSettingsOf()
        val config = glassConfigOf(
            enabled = settings.glassEnabled,
            glassOpacity = settings.glassOpacity,
            blurRadius = settings.blurRadius,
            accentArgb = settings.accentArgb
        )
        assertFalse("the shipped default must be the opaque look", config.enabled)
        assertEquals(GlassRanges.OPACITY_DEFAULT, config.glassOpacity, 0f)
        assertEquals(GlassRanges.BLUR_DEFAULT, config.blurRadius, 0f)
        // The accent has to survive the Int round-trip used by DataStore.
        assertEquals(Color(0xFF2E7D32), config.accentColor)
    }

    @Test
    fun glassConfigOfClampsOutOfRangePersistedValues() {
        val config = glassConfigOf(
            enabled = true,
            glassOpacity = 4f,
            blurRadius = 400f,
            accentArgb = 0xFF2E7D32.toInt()
        )
        assertEquals(GlassRanges.OPACITY_MAX, config.glassOpacity, 0f)
        assertEquals(GlassRanges.BLUR_MAX, config.blurRadius, 0f)
    }

    @Test
    fun everyThemeCarriesADistinctSpanishSolidLabel() {
        val labels = AppTheme.ALL.map { it.solidLabel }
        assertEquals(listOf(
            "Brote Verde Sólido",
            "Cosecha Otoñal Sólida",
            "Oscuro Extremo Sólido",
            "Claro Solar Sólido"
        ), labels)
        assertEquals(
            "solid labels must be unique",
            labels.size,
            labels.distinct().size
        )
    }
}
