package com.trichome.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.trichome.app.data.prefs.appearanceSettingsOf
import com.trichome.app.ui.components.panelBorderColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the four opaque themes.
 *
 * The app used to ship eight palettes: a translucent one and an opaque one per
 * theme, chosen by a settings toggle. There is now one palette per theme, and
 * this file is what holds it to its promise — every role opaque, every `on*`
 * role legible against the surface it is drawn on, and every panel edge visible.
 *
 * Everything asserted here is pure colour math, so none of it needs a
 * composition, a Context or Robolectric. Relative luminance and the contrast
 * ratio are computed from the ARGB components by [contrastRatio] in the theme
 * layer — `isReturnDefaultValues = true` is on, so a test that leaned on
 * `android.graphics` or on `Color.luminance()` would silently compare zeros and
 * pass without checking anything.
 */
class OpaqueThemeContrastTest {

    /** The accent every install already has stored. */
    private val accent = AccentPalette.DEFAULT_ACCENT

    /** Every accent the app offers, so nothing is checked against only the green. */
    private val allAccents: List<Color> = AccentPalette.SELECTABLE_ARGB.map { Color(it) }

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

    /* ── 1. Four distinct palettes ─────────────────────────────────────────── */

    @Test
    fun theFourThemesDoNotShareAPalette() {
        // Guards a copy-paste in the four hand-written palettes.
        val distinct = AppTheme.ALL.map { schemeColorsOf(solidSchemeFor(it, accent)).toString() }.toSet()
        assertEquals("each of the four themes must have its own palette", AppTheme.ALL.size, distinct.size)
    }

    @Test
    fun theFourPalettesThemselvesAreDistinct() {
        val distinct = AppTheme.ALL.map { SolidPalettes.forTheme(it) }.toSet()
        assertEquals("each theme must map to its own palette", AppTheme.ALL.size, distinct.size)
    }

    @Test
    fun anUnknownThemeIndexFallsBackInsteadOfThrowing() {
        // The persisted index is user data: a value written by a build that had
        // five themes must not crash the app on launch.
        val orphan = AppTheme(index = 99, label = "Futuro", emoji = "🧪", isDark = true)
        assertEquals(SolidPalettes.GREEN, SolidPalettes.forTheme(orphan))
        assertTrue(schemeColorsOf(solidSchemeFor(orphan, accent)).isNotEmpty())
    }

    @Test
    fun everyThemeCarriesADistinctSpanishLabel() {
        val labels = AppTheme.ALL.map { it.label }
        assertEquals(
            listOf("Brote Verde", "Cosecha de Otoño", "Cuidado Nocturno", "Invernadero Soleado"),
            labels
        )
        assertEquals("labels must be unique", labels.size, labels.distinct().size)
    }

    @Test
    fun everyThemeIndexIsTheOneItIsKeyedBy() {
        // The persisted value is the index, so a swapped constant silently
        // repaints an existing install with another theme on upgrade.
        AppTheme.ALL.forEach { theme ->
            assertEquals(theme.index, AppTheme.ALL.indexOf(theme))
            assertEquals(
                "theme ${theme.label} is not reachable from the theme it is keyed by",
                theme,
                AppTheme.ALL.firstOrNull { it.index == theme.index }
            )
        }
    }

    /* ── 2. Every scheme is fully opaque ───────────────────────────────────── */

    @Test
    fun everySchemeIsFullyOpaque() {
        for (theme in AppTheme.ALL) {
            for (candidate in allAccents) {
                val colors = schemeColorsOf(solidSchemeFor(theme, candidate))
                colors.forEach { (role, color) ->
                    assertEquals(
                        "${theme.label}/$role must be fully opaque for accent $candidate",
                        1f,
                        color.alpha,
                        0f
                    )
                }
            }
        }
    }

    @Test
    fun everySchemeRoleIsCovered() {
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

    /* ── 3. Contrast of every on* role against its own background ──────────── */

    @Test
    fun onSurfaceContrastsWithSurface() {
        for (theme in AppTheme.ALL) {
            for (candidate in allAccents) {
                val scheme = solidSchemeFor(theme, candidate)
                val ratio = contrastRatio(scheme.onSurface, scheme.surface)
                assertTrue(
                    "${theme.label}/$candidate: onSurface on surface is $ratio:1, need 4.5:1",
                    ratio >= MINIMUM_TEXT_CONTRAST
                )
            }
        }
    }

    @Test
    fun onBackgroundContrastsWithBackground() {
        for (theme in AppTheme.ALL) {
            val scheme = solidSchemeFor(theme, accent)
            val ratio = contrastRatio(scheme.onBackground, scheme.background)
            assertTrue(
                "${theme.label}: onBackground on background is $ratio:1, need 4.5:1",
                ratio >= MINIMUM_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun onPrimaryContrastsWithPrimaryForEveryAccent() {
        // The accent is user-chosen, so this has to hold for the whole palette
        // and not only for the shipped green.
        for (theme in AppTheme.ALL) {
            allAccents.forEach { candidate ->
                val scheme = solidSchemeFor(theme, candidate)
                val ratio = contrastRatio(scheme.onPrimary, scheme.primary)
                assertTrue(
                    "${theme.label}: onPrimary on accent $candidate is $ratio:1",
                    ratio >= MINIMUM_TEXT_CONTRAST
                )
            }
        }
    }

    @Test
    fun onPrimaryIsResolvedFromTheAccentAndNotCopiedFromTheTheme() {
        // The point of resolving it is that it tracks the accent. A future edit
        // that hardcoded one `onPrimary` per theme would pass the contrast
        // assertion above for the green and fail for the other seven.
        for (theme in AppTheme.ALL) {
            allAccents.forEach { candidate ->
                assertEquals(
                    "${theme.label}/$candidate: onPrimary must be readableOnStrict of the accent",
                    readableOnStrict(candidate),
                    solidSchemeFor(theme, candidate).onPrimary
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
                "${theme.label}: onSurfaceVariant is $ratio:1, need 4.5:1",
                ratio >= MINIMUM_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun everyDerivedOnRoleClearsTheTextBar() {
        // secondary, tertiary and the containers are all derived by mixing a
        // user-chosen accent into a theme surface, so none of them can be
        // assumed legible from the palette alone. Each `on*` role is measured
        // against the role it is drawn on, which for the containers is the
        // container and not the surface.
        val pairs = listOf(
            "onSecondary" to { s: androidx.compose.material3.ColorScheme -> s.onSecondary to s.secondary },
            "onTertiary" to { s: androidx.compose.material3.ColorScheme -> s.onTertiary to s.tertiary },
            "onPrimaryContainer" to { s: androidx.compose.material3.ColorScheme ->
                s.onPrimaryContainer to s.primaryContainer
            },
            "onSecondaryContainer" to { s: androidx.compose.material3.ColorScheme ->
                s.onSecondaryContainer to s.secondaryContainer
            },
            "onTertiaryContainer" to { s: androidx.compose.material3.ColorScheme ->
                s.onTertiaryContainer to s.tertiaryContainer
            },
            "onError" to { s: androidx.compose.material3.ColorScheme -> s.onError to s.error },
            "onErrorContainer" to { s: androidx.compose.material3.ColorScheme ->
                s.onErrorContainer to s.errorContainer
            }
        )
        for (theme in AppTheme.ALL) {
            for (candidate in allAccents) {
                val scheme = solidSchemeFor(theme, candidate)
                pairs.forEach { (role, resolve) ->
                    val (ink, backdrop) = resolve(scheme)
                    val ratio = contrastRatio(ink, backdrop)
                    assertTrue(
                        "${theme.label}/$candidate: $role on its own backdrop is $ratio:1, " +
                            "need 4.5:1",
                        ratio >= MINIMUM_TEXT_CONTRAST
                    )
                }
            }
        }
    }

    @Test
    fun aContainerIsAtLeastNotInvisibleBehindItsOwnLabel() {
        for (theme in AppTheme.ALL) {
            for (candidate in allAccents) {
                val scheme = solidSchemeFor(theme, candidate)
                val ratio = contrastRatio(scheme.primaryContainer, scheme.onPrimaryContainer)
                assertTrue(
                    "${theme.label}/$candidate: the label on primaryContainer is $ratio:1",
                    ratio >= MINIMUM_TEXT_CONTRAST
                )
            }
        }
    }

    /**
     * A container is a boundary, so it owes the 3:1 of WCAG 1.4.11 against the
     * surface it sits on, not just legibility for the label on top of it.
     *
     * The blend factors used to be constants — 0.72, 0.75, 0.75, 0.80 — which
     * produced a `primaryContainer` 2.25:1 from its own surface on the default
     * green. A constant cannot work: the accent is user-chosen and the four
     * themes differ widely in lightness, so one factor is right for a dark theme
     * and invisible on a light one. `containerFor` now searches for the strongest
     * tint that still clears the bar.
     *
     * The earlier version of this file asserted a 3:1 bar, failed, and the test
     * was weakened to only check the label. That was the wrong resolution: the
     * failure was a real defect in a real component, not a bad expectation.
     */
    @Test
    fun everyContainerIsADistinctEdgeAgainstItsOwnSurface() {
        for (theme in AppTheme.ALL) {
            for (candidate in allAccents) {
                val scheme = solidSchemeFor(theme, candidate)
                listOf(
                    "primaryContainer" to scheme.primaryContainer,
                    "secondaryContainer" to scheme.secondaryContainer,
                    "tertiaryContainer" to scheme.tertiaryContainer,
                    "errorContainer" to scheme.errorContainer
                ).forEach { (role, container) ->
                    val ratio = contrastRatio(container, scheme.surface)
                    assertTrue(
                        "${theme.label}/$candidate: $role is $ratio:1 from surface, " +
                            "need ${MINIMUM_NON_TEXT_CONTRAST}:1 for a visible edge",
                        ratio >= MINIMUM_NON_TEXT_CONTRAST
                    )
                }
            }
        }
    }

    @Test
    fun containerForKeepsTheStrongestTintThatStillClearsTheBar() {
        val surface = Color(0xFFFFFFFF)
        // A dark accent on white can be tinted hard before it stops reading.
        val strong = containerFor(Color(0xFF1B2A1B), surface)
        assertTrue(
            "a dark accent on white should still take a visible tint",
            contrastRatio(strong, surface) >= MINIMUM_NON_TEXT_CONTRAST
        )
        // The result must be the strongest accessible tint, not merely an
        // accessible one: zeroing the container out would also pass.
        val weaker = containerFor(Color(0xFF1B2A1B), surface, steps = 6)
        assertTrue(
            "a coarser search must not find a stronger tint than a fine one",
            contrastRatio(strong, surface) <= contrastRatio(weaker, surface) + 0.001f
        )
    }

    @Test
    fun containerForPushesAnUnreachableTintAwayFromTheSurface() {
        // Lime is already bright, so tinting it towards the white surface only
        // lowers contrast: even undiluted it sits near 1.9:1. Returning the plain
        // surface would be a 1:1 "edge" — a marker nobody can see. The container
        // has to move the accent away from the surface instead.
        val surface = Color(0xFFFFFFFF)
        val lime = Color(0xFF7CB342)
        val container = containerFor(lime, surface)
        assertTrue(
            "lime on white must still yield a visible container, got $container",
            contrastRatio(container, surface) >= MINIMUM_NON_TEXT_CONTRAST
        )
        assertNotEquals(
            "the container must not be the surface itself",
            surface, container
        )
    }

    @Test
    fun containerForKeepsTheAccentHueWhenItHasTo() {
        // The second search moves the accent, so the result must still be
        // recognisably that accent rather than a generic grey.
        val lime = Color(0xFF7CB342)
        val container = containerFor(lime, Color(0xFFFFFFFF))
        val accentHue = hueOf(lime)
        val containerHue = hueOf(container)
        val delta = kotlin.math.abs(accentHue - containerHue)
        assertTrue(
            "the pushed container drifted $delta degrees from lime's hue",
            delta < 20f || delta > 340f
        )
    }

    /** Coarse hue in degrees, good enough to tell "still lime" from "now grey". */
    private fun hueOf(color: Color): Float {
        val max = maxOf(color.red, color.green, color.blue)
        val min = minOf(color.red, color.green, color.blue)
        val delta = max - min
        if (delta < 0.001f) return -1f
        val hue = when (max) {
            color.red -> ((color.green - color.blue) / delta) % 6f
            color.green -> (color.blue - color.red) / delta + 2f
            else -> (color.red - color.green) / delta + 4f
        }
        return ((hue * 60f) + 360f) % 360f
    }

    @Test
    fun containerForAlwaysReturnsAnOpaqueColour() {
        for (candidate in allAccents) {
            for (surface in listOf(Color(0xFFFFFFFF), Color(0xFF040A06))) {
                assertEquals(
                    "containerFor($candidate, $surface) must be fully opaque",
                    1f, containerFor(candidate, surface).alpha, 0.0001f
                )
            }
        }
    }

    /**
     * Borders are not text, so 4.5:1 is the wrong bar for them — but 3:1 is
     * WCAG 1.4.11 and it still matters: the whole point of an opaque theme is a
     * visible edge around each panel. The first hand-written palettes landed at
     * 2.55:1 (NIGHT), 2.65:1 (AUTUMN) and 3.45:1 (GREEN) and every text
     * assertion still passed, because nothing looked at a non-text role.
     */
    @Test
    fun panelOutlineIsVisibleAgainstItsOwnSurface() {
        for (theme in AppTheme.ALL) {
            val scheme = solidSchemeFor(theme, accent)
            val ratio = contrastRatio(scheme.outline, scheme.surface)
            assertTrue(
                "${theme.label}: outline on surface is $ratio:1, " +
                    "need ${MINIMUM_NON_TEXT_CONTRAST}:1 for a visible panel edge",
                ratio >= MINIMUM_NON_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun everyDividerRoleClearsTheNonTextBar() {
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
                    "${theme.label}: outlineVariant on $name is $ratio:1, " +
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

    /* ── 4. The accent never becomes an invisible panel edge ──────────────── */

    @Test
    fun thePanelEdgeIsAlwaysVisibleWhicheverAccentIsPicked() {
        // The accent is a user colour, and the panel edge is the one place the
        // panel spends it. The amber swatch is 1.57:1 on the white surface of
        // `Invernadero Soleado`: as a border that is not a border.
        for (theme in AppTheme.ALL) {
            val scheme = solidSchemeFor(theme, accent)
            allAccents.forEach { candidate ->
                val edge = panelBorderColor(scheme.outline, scheme.surface, candidate)
                val ratio = contrastRatio(edge, scheme.surface)
                assertTrue(
                    "${theme.label}/$candidate: the panel edge is $ratio:1, " +
                        "need ${MINIMUM_NON_TEXT_CONTRAST}:1",
                    ratio >= MINIMUM_NON_TEXT_CONTRAST
                )
            }
        }
    }

    @Test
    fun theAccentIsUsedAsTheEdgeOnlyWhenItCanCarryIt() {
        val outline = Color(0xFF6E7A66)
        val white = Color(0xFFFFFFFF)
        val darkGreen = Color(0xFF1B5E20)

        // Clears 3:1 on white, so it is spent on the edge.
        assertEquals(darkGreen, panelBorderColor(outline, white, darkGreen))
        // Does not, so the verified outline takes over.
        assertEquals(outline, panelBorderColor(outline, white, Color(0xFFFFC107)))
        // No accent at all: the outline, always.
        assertEquals(outline, panelBorderColor(outline, white, null))
    }

    @Test
    fun anAccentThatFallsBackIsNotSilentlyIgnored() {
        // A fallback that returned the accent anyway would make the guard above
        // pass while the edge disappeared, so pin the two outcomes apart.
        val outline = Color(0xFF6E7A66)
        val white = Color(0xFFFFFFFF)
        val amber = Color(0xFFFFC107)
        assertNotEquals(
            "an unreadable accent must not be drawn as the edge",
            amber,
            panelBorderColor(outline, white, amber)
        )
    }

    /* ── 5. The persisted defaults still describe the shipped look ─────────── */

    @Test
    fun anAbsentPreferenceReadsAsTheShippedDefault() {
        // A `null` means the key was never written, which is what DataStore hands
        // back on a fresh install. The default has to be the green every
        // existing install already has, or the app repaints itself on first run.
        val settings = appearanceSettingsOf()
        assertEquals(ThemeIndex.GREEN, settings.themeIndex)
        assertEquals(AccentPalette.DEFAULT_ACCENT_ARG, settings.accentArgb)
        assertEquals(Color(AccentPalette.DEFAULT_ACCENT_ARG), Color(settings.accentArgb))
        assertEquals(1.0f, settings.fontScale, 0f)
    }

    @Test
    fun persistedValuesSurviveTheRoundTrip() {
        // The accent has to survive the Int round-trip used by DataStore, or the
        // scheme is built from a colour the user never chose.
        allAccents.forEach { candidate ->
            val argb = candidate.toArgbInt()
            assertEquals(candidate, Color(appearanceSettingsOf(accentArgb = argb).accentArgb))
        }
    }

    @Test
    fun everySelectableAccentIsDistinct() {
        assertEquals(
            "two swatches share a colour, so one of them is unreachable",
            AccentPalette.SELECTABLE_ARGB.size,
            AccentPalette.SELECTABLE_ARGB.distinct().size
        )
    }
}
