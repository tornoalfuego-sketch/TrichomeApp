package com.trichome.app.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.trichome.app.ui.theme.AccentPalette
import com.trichome.app.ui.theme.AppTheme
import com.trichome.app.ui.theme.MINIMUM_NON_TEXT_CONTRAST
import com.trichome.app.ui.theme.MINIMUM_TEXT_CONTRAST
import com.trichome.app.ui.theme.contrastRatio
import com.trichome.app.ui.theme.solidSchemeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the shared top bar.
 *
 * The bug this locks down: eight hand-written `TopAppBar`s, every one of them
 * `title` plus a back arrow and no `colors` argument at all. They drew over a
 * `Scaffold` whose container was transparent, on top of an animated backdrop, so
 * the back arrow was whatever happened to be behind it. Extracting the component
 * is only half the fix; the guarantee that matters is that the content colour
 * contrasts with the container it was resolved against, and that is colour math,
 * so it is asserted here rather than eyeballed.
 *
 * The bar is now opaque in every state, which makes the assertion *stronger*
 * than it was: previously the title had to clear 4.5:1 against a translucent
 * surface, which meant compositing two colours by hand and measuring the
 * composite — a number that is only right if the backdrop is also known. With an
 * opaque container the pixels behind the title are the scheme's own `surface`,
 * so the ratio below is the ratio the user actually sees.
 *
 * All four themes and the whole accent palette are swept, because the accent is
 * user-chosen and the bar inherits it.
 */
class AppTopBarTest {

    private val accents: List<Color> = AccentPalette.SELECTABLE_ARGB.map { Color(it) }

    /** Every theme paired with every accent the user can pick. */
    private fun sweep(): List<Pair<AppTheme, Color>> = buildList {
        AppTheme.ALL.forEach { theme -> accents.forEach { accent -> add(theme to accent) } }
    }

    private fun schemeFor(theme: AppTheme, accent: Color): ColorScheme = solidSchemeFor(theme, accent)

    private fun paletteFor(theme: AppTheme, accent: Color): AppTopBarDefaults.Palette =
        appTopBarPaletteFor(scheme = schemeFor(theme, accent))

    /* ── 1. The guarantee: content colour contrasts with its container ────── */

    @Test
    fun theTitleContrastsItsContainerInEveryThemeAndAccent() {
        sweep().forEach { (theme, accent) ->
            val palette = paletteFor(theme, accent)
            val ratio = contrastRatio(palette.titleContent, palette.containerColor)
            assertTrue(
                "${theme.label} $accent: title on the bar is $ratio:1, " +
                    "need ${MINIMUM_TEXT_CONTRAST}:1",
                ratio >= MINIMUM_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun theBackArrowContrastsItsContainerEverywhere() {
        sweep().forEach { (theme, accent) ->
            val palette = paletteFor(theme, accent)
            val ratio = contrastRatio(palette.navigationIconContent, palette.containerColor)
            assertTrue(
                "${theme.label} $accent: back arrow on the bar is $ratio:1, " +
                    "need ${MINIMUM_TEXT_CONTRAST}:1",
                ratio >= MINIMUM_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun theActionIconsContrastTheirContainerEverywhere() {
        sweep().forEach { (theme, accent) ->
            val palette = paletteFor(theme, accent)
            val ratio = contrastRatio(palette.actionIconContent, palette.containerColor)
            assertTrue(
                "${theme.label} $accent: action icon on the bar is $ratio:1, " +
                    "need ${MINIMUM_TEXT_CONTRAST}:1",
                ratio >= MINIMUM_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun theScrollContainerAlsoHasToCarryTheTitle() {
        // `scrolledContainerColor` is what the title sits on once content has
        // scrolled under the bar, and the previous eight bars let that one be the
        // framework default: invisible over a transparent Scaffold.
        sweep().forEach { (theme, accent) ->
            val palette = paletteFor(theme, accent)
            val ratio = contrastRatio(palette.titleContent, palette.scrolledContainerColor)
            assertTrue(
                "${theme.label} $accent: title on the scrolled bar is $ratio:1, " +
                    "need ${MINIMUM_TEXT_CONTRAST}:1",
                ratio >= MINIMUM_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun theContrastAssertionsAreNotVacuous() {
        // Guards the sweep above: if the contrast function returned a constant, all
        // of them would pass without comparing anything.
        assertNotEquals(
            contrastRatio(Color.Black, Color.Black),
            contrastRatio(Color.Black, Color.White)
        )
        assertTrue(sweep().isNotEmpty())
        assertEquals(AppTheme.ALL.size * accents.size, sweep().size)
        assertTrue("the sweep must cover more than one accent", accents.size > 1)
    }

    /* ── 2. The bar is actually visible ───────────────────────────────────── */

    @Test
    fun theContainerIsFullyOpaqueInEveryState() {
        // A bar with alpha 0 is the bug in its purest form: whatever is behind it
        // is the bar. An opaque container removes the whole class, and it is what
        // lets the contrast assertions above measure real pixels.
        sweep().forEach { (theme, accent) ->
            val palette = paletteFor(theme, accent)
            assertTrue(
                "${theme.label} $accent: the bar is fully transparent",
                palette.containerColor.alpha >= AppTopBarDefaults.CONTAINER_ALPHA
            )
            assertTrue(
                "${theme.label} $accent: the scrolled bar is fully transparent",
                palette.scrolledContainerColor.alpha >= AppTopBarDefaults.CONTAINER_ALPHA
            )
        }
    }

    @Test
    fun theContainerIsTheSchemesSurfaceNotATintedOrBlendedColour() {
        // Tinting the bar towards the accent would be a large accent-painted
        // surface, which is exactly what the opaque panels refuse to do — and it
        // would make the title's contrast depend on the accent.
        sweep().forEach { (theme, accent) ->
            val palette = paletteFor(theme, accent)
            assertEquals(
                "${theme.label} $accent: the bar must be the plain surface colour",
                schemeFor(theme, accent).surface,
                palette.containerColor
            )
        }
    }

    @Test
    fun theRestingAndScrolledContainersAreIndistinguishable() {
        // There is no scroll-dependent translucency any more, so the two states
        // must not be able to drift apart. They used to: the resting bar honoured
        // an opacity preference and the scrolled one did not.
        AppTheme.ALL.forEach { theme ->
            val palette = paletteFor(theme, Color(AccentPalette.DEFAULT_ACCENT_ARG))
            assertEquals(palette.containerColor, palette.scrolledContainerColor)
        }
    }

    @Test
    fun theBarIsOpaqueForTheBrightestAccentToo() {
        // The amber swatch is the one that would have exposed a translucent
        // container, since it is the brightest colour the app offers.
        val palette = paletteFor(AppTheme.NIGHT, Color(AccentPalette.AMBER_ACCENT_ARG))
        assertEquals(1f, palette.containerColor.alpha, 0f)
        assertEquals(1f, palette.scrolledContainerColor.alpha, 0f)
    }

    /* ── 3. It is not a repaint of the framework default ──────────────────── */

    @Test
    fun theBarFollowsTheUserAccentRatherThanAFixedGreen() {
        // The container is the scheme's `surface`, which deliberately does not
        // move with the accent — the same choice the bottom bar makes, and the
        // right one, since a bar that repaints with every accent choice is a bar
        // that fights the content. The accent reaches the bar through the divider,
        // and it has to: a bar entirely independent of the accent would let the
        // user's chosen colour stop dead above the content.
        val green = paletteFor(AppTheme.GREEN, Color(AccentPalette.DEFAULT_ACCENT_ARG))
        val violet = paletteFor(AppTheme.GREEN, Color(AccentPalette.VIOLET_ACCENT_ARG))

        assertNotEquals(
            "the bar must not be pinned to one green",
            green.dividerColor,
            violet.dividerColor
        )
        assertEquals(
            "the container stays the surface colour in both cases",
            green.containerColor,
            violet.containerColor
        )
    }

    @Test
    fun theDividerIsDerivedFromTheSchemeAccentNotAHardcodedValue() {
        sweep().forEach { (theme, accent) ->
            assertEquals(
                "${theme.label} $accent",
                schemeFor(theme, accent).primary.copy(alpha = 0.45f),
                paletteFor(theme, accent).dividerColor
            )
        }
    }

    @Test
    fun theDividerIsVisibleAgainstTheBarItSitsOn() {
        // A hairline is non-text, so it is judged against the non-text bar. At 45%
        // accent over the surface it has to clear 3:1 somewhere in the sweep, or
        // it is decoration that costs a draw and shows nothing.
        val clear = sweep().count { (theme, accent) ->
            val palette = paletteFor(theme, accent)
            contrastRatio(palette.dividerColor, palette.containerColor) >= MINIMUM_NON_TEXT_CONTRAST
        }
        assertTrue(
            "the accent divider cleared the non-text bar in only $clear of " +
                "${sweep().size} combinations",
            clear > 0
        )
    }

    @Test
    fun twoAccentsOnTheSameThemeDoNotProduceTheSameBar() {
        AppTheme.ALL.forEach { theme ->
            val green = paletteFor(theme, Color(AccentPalette.DEFAULT_ACCENT_ARG))
            val violet = paletteFor(theme, Color(AccentPalette.VIOLET_ACCENT_ARG))
            assertTrue(
                "${theme.label}: the bar is identical for two accents",
                !green.sameLookAs(violet)
            )
        }
    }

    @Test
    fun everyThemeProducesADistinctBar() {
        val bars = AppTheme.ALL.map { theme ->
            paletteFor(theme, Color(AccentPalette.DEFAULT_ACCENT_ARG)).containerColor
        }
        assertEquals(
            "two themes resolve to the same bar",
            bars.size,
            bars.distinct().size
        )
    }

    @Test
    fun theTitleColourIsTheSchemesOnSurfaceNotAHardcodedInk() {
        sweep().forEach { (theme, accent) ->
            assertEquals(
                "${theme.label} $accent",
                schemeFor(theme, accent).onSurface,
                paletteFor(theme, accent).titleContent
            )
        }
    }

    /* ── 4. One component, not eight ──────────────────────────────────────── */

    @Test
    fun theSharedBarExposesTheActionSlotTheOldBarsNeverHad() {
        // Every one of the eight screens had zero action icons. The component has
        // to accept them, or the next screen repeats the same omission.
        assertTrue("AppTopBar must offer an actions slot", AppTopBarDefaults.HAS_ACTIONS_SLOT)
        assertTrue(
            "AppTopBar must offer an optional back arrow",
            AppTopBarDefaults.OFFERS_NAVIGATION_SLOT
        )
    }

    @Test
    fun aScreenThatIsATabRootCanHideTheBackArrow() {
        // The bottom-bar destinations have no previous entry worth returning to,
        // and a dead arrow there is the same defect in miniature.
        val scheme = schemeFor(AppTheme.GREEN, Color(AccentPalette.DEFAULT_ACCENT_ARG))
        val withBack = appTopBarPaletteFor(scheme = scheme, showNavigationIcon = true)
        val withoutBack = appTopBarPaletteFor(scheme = scheme, showNavigationIcon = false)
        assertEquals(withBack.containerColor, withoutBack.containerColor)
        assertEquals(withBack.titleContent, withoutBack.titleContent)
    }
}
