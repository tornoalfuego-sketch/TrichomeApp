package com.trichome.app.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.trichome.app.ui.theme.AppTheme
import com.trichome.app.ui.theme.GlassRanges
import com.trichome.app.ui.theme.MINIMUM_NON_TEXT_CONTRAST
import com.trichome.app.ui.theme.MINIMUM_TEXT_CONTRAST
import com.trichome.app.ui.theme.activeSchemeFor
import com.trichome.app.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the shared top bar.
 *
 * The bug: eight hand-written `TopAppBar`s, every one of them `title` plus a back
 * arrow and no `colors` argument at all. They draw over a `Scaffold` whose
 * container is transparent, on top of the drifting orb background, so the back
 * arrow was whatever happened to be behind it. Extracting the component is only
 * half the fix; the guarantee that matters is that the content colour contrasts
 * with the container it was resolved against, and that is colour math, so it is
 * asserted here rather than eyeballed.
 *
 * Both looks are swept, across all four themes and the whole accent palette,
 * because the accent is user-chosen and the bar inherits it.
 */
class AppTopBarTest {

    private val accents = listOf(
        Color(0xFF2E7D32), Color(0xFF7CB342), Color(0xFFFFC107), Color(0xFFE65100),
        Color(0xFF00ACC1), Color(0xFF7C4DFF), Color(0xFFEC407A), Color(0xFFE53935)
    )

    /** Both looks, for every theme and every accent the user can pick. */
    private fun sweep(): List<Triple<AppTheme, Color, Boolean>> = buildList {
        AppTheme.ALL.forEach { theme ->
            accents.forEach { accent ->
                add(Triple(theme, accent, false))
                add(Triple(theme, accent, true))
            }
        }
    }

    private fun schemeFor(theme: AppTheme, accent: Color, glass: Boolean): ColorScheme =
        activeSchemeFor(theme, accent, glassEnabled = glass)

    private fun paletteFor(
        theme: AppTheme,
        accent: Color,
        glass: Boolean,
        glassOpacity: Float = GlassRanges.OPACITY_DEFAULT
    ): AppTopBarDefaults.Palette = appTopBarPaletteFor(
        scheme = schemeFor(theme, accent, glass),
        glassOpacity = glassOpacity,
        glassEnabled = glass
    )

    /* ── 1. The guarantee: content colour contrasts with its container ────── */

    @Test
    fun theTitleContrastsItsContainerInEveryThemeAccentAndLook() {
        sweep().forEach { (theme, accent, glass) ->
            val palette = paletteFor(theme, accent, glass)
            val ratio = contrastRatio(palette.titleContent, palette.containerColor)
            assertTrue(
                "${theme.solidLabel} $accent glass=$glass: title on the bar is $ratio:1, " +
                    "need ${MINIMUM_TEXT_CONTRAST}:1",
                ratio >= MINIMUM_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun theBackArrowContrastsItsContainerEverywhere() {
        sweep().forEach { (theme, accent, glass) ->
            val palette = paletteFor(theme, accent, glass)
            val ratio = contrastRatio(palette.navigationIconContent, palette.containerColor)
            assertTrue(
                "${theme.solidLabel} $accent glass=$glass: back arrow on the bar is $ratio:1, " +
                    "need ${MINIMUM_NON_TEXT_CONTRAST}:1",
                ratio >= MINIMUM_NON_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun theActionIconsContrastTheirContainerEverywhere() {
        sweep().forEach { (theme, accent, glass) ->
            val palette = paletteFor(theme, accent, glass)
            val ratio = contrastRatio(palette.actionIconContent, palette.containerColor)
            assertTrue(
                "${theme.solidLabel} $accent glass=$glass: action icon on the bar is $ratio:1, " +
                    "need ${MINIMUM_NON_TEXT_CONTRAST}:1",
                ratio >= MINIMUM_NON_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun theScrollContainerAlsoHasToCarryTheTitle() {
        // `scrolledContainerColor` is what the title sits on once content has
        // scrolled under the bar, and the previous eight bars let that one be the
        // framework default: invisible over a transparent Scaffold.
        sweep().forEach { (theme, accent, glass) ->
            val palette = paletteFor(theme, accent, glass)
            val ratio = contrastRatio(palette.titleContent, palette.scrolledContainerColor)
            assertTrue(
                "${theme.solidLabel} $accent glass=$glass: title on the scrolled bar is $ratio:1, " +
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
        assertTrue(sweep().any { it.third } && sweep().any { !it.third })
    }

    /* ── 2. The bar is actually visible ───────────────────────────────────── */

    @Test
    fun theContainerIsNeverFullyTransparent() {
        // A bar with alpha 0 is the bug in its purest form: whatever is behind it
        // is the bar, and the orb background moves.
        sweep().forEach { (theme, accent, glass) ->
            val palette = paletteFor(theme, accent, glass)
            assertTrue(
                "${theme.solidLabel} $accent glass=$glass: the bar is fully transparent",
                palette.containerColor.alpha >= AppTopBarDefaults.MINIMUM_ALPHA
            )
            assertTrue(
                "${theme.solidLabel} $accent glass=$glass: the scrolled bar is fully transparent",
                palette.scrolledContainerColor.alpha >= AppTopBarDefaults.MINIMUM_ALPHA
            )
        }
    }

    @Test
    fun theScrolledContainerIsNoMoreTranslucentThanTheRestingOne() {
        // Once content is scrolling under the bar, translucency stops being a style
        // choice and starts being a legibility bug.
        AppTheme.ALL.forEach { theme ->
            listOf(false, true).forEach { glass ->
                val palette = paletteFor(theme, Color(0xFF2E7D32), glass)
                assertTrue(
                    "${theme.solidLabel} glass=$glass: the scrolled bar is more translucent " +
                        "(${palette.scrolledContainerColor.alpha} < ${palette.containerColor.alpha})",
                    palette.scrolledContainerColor.alpha >= palette.containerColor.alpha
                )
            }
        }
    }

    @Test
    fun theScrolledContainerIsFullyOpaque() {
        AppTheme.ALL.forEach { theme ->
            listOf(false, true).forEach { glass ->
                assertEquals(
                    "${theme.solidLabel} glass=$glass: the scrolled bar must be opaque",
                    1f,
                    paletteFor(theme, Color(0xFF2E7D32), glass).scrolledContainerColor.alpha,
                    0f
                )
            }
        }
    }

    @Test
    fun theBarIsOpaqueWhenGlassIsOffAndTranslucentWhenItIsOn() {
        val opaque = paletteFor(AppTheme.GREEN, Color(0xFF2E7D32), glass = false)
        val glass = paletteFor(AppTheme.GREEN, Color(0xFF2E7D32), glass = true)
        assertEquals(1f, opaque.containerColor.alpha, 0f)
        assertTrue(
            "glass must actually be translucent, got alpha ${glass.containerColor.alpha}",
            glass.containerColor.alpha < 1f
        )
        assertTrue(
            "but never invisible",
            glass.containerColor.alpha >= AppTopBarDefaults.MINIMUM_ALPHA
        )
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
        val green = paletteFor(AppTheme.GREEN, Color(0xFF2E7D32), glass = false)
        val violet = paletteFor(AppTheme.GREEN, Color(0xFF7C4DFF), glass = false)

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
        AppTheme.ALL.forEach { theme ->
            listOf(false, true).forEach { glass ->
                accents.forEach { accent ->
                    val palette = paletteFor(theme, accent, glass)
                    assertEquals(
                        "${theme.solidLabel} $accent glass=$glass",
                        schemeFor(theme, accent, glass).primary.copy(alpha = 0.45f),
                        palette.dividerColor
                    )
                }
            }
        }
    }

    @Test
    fun theDividerIsVisibleAgainstTheBarItSitsOn() {
        // A hairline is non-text, so it is judged against the non-text bar. At 45%
        // accent over the surface it has to clear 3:1 somewhere in the sweep, or
        // it is decoration that costs a draw and shows nothing.
        val clear = sweep().count { (theme, accent, glass) ->
            val palette = paletteFor(theme, accent, glass)
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
            listOf(false, true).forEach { glass ->
                val green = paletteFor(theme, Color(0xFF2E7D32), glass)
                val violet = paletteFor(theme, Color(0xFF7C4DFF), glass)
                assertTrue(
                    "${theme.solidLabel} glass=$glass: the bar is identical for two accents",
                    !green.sameLookAs(violet)
                )
            }
        }
    }

    @Test
    fun everyThemeProducesADistinctBar() {
        val bars = AppTheme.ALL.map { theme ->
            paletteFor(theme, Color(0xFF2E7D32), glass = false).containerColor
        }
        assertEquals(
            "two themes resolve to the same bar",
            bars.size,
            bars.distinct().size
        )
    }

    @Test
    fun theTitleColourIsTheSchemesOnSurfaceNotAHardcodedInk() {
        AppTheme.ALL.forEach { theme ->
            listOf(false, true).forEach { glass ->
                val palette = paletteFor(theme, Color(0xFF2E7D32), glass)
                assertEquals(
                    "${theme.solidLabel} glass=$glass",
                    schemeFor(theme, Color(0xFF2E7D32), glass).onSurface,
                    palette.titleContent
                )
            }
        }
    }

    /* ── 4. The opacity preference is honoured inside a legible window ────── */

    @Test
    fun theOpacityPreferenceIsHonouredButStillFloorsAtTheReadableMinimum() {
        val almostInvisible = paletteFor(
            AppTheme.GREEN, Color(0xFF2E7D32), glass = true, glassOpacity = GlassRanges.OPACITY_MIN
        )
        val veryOpaque = paletteFor(
            AppTheme.GREEN, Color(0xFF2E7D32), glass = true, glassOpacity = GlassRanges.OPACITY_MAX
        )

        assertTrue(
            "alpha ${almostInvisible.containerColor.alpha} is below the floor",
            almostInvisible.containerColor.alpha >= AppTopBarDefaults.MINIMUM_ALPHA
        )
        assertTrue(
            "the slider must still do something",
            veryOpaque.containerColor.alpha > almostInvisible.containerColor.alpha
        )
    }

    @Test
    fun anOutOfRangeOpacityIsClampedRatherThanRenderedInvisible() {
        listOf(-5f, 0f, 99f).forEach { raw ->
            val palette = paletteFor(AppTheme.GREEN, Color(0xFF2E7D32), glass = true, glassOpacity = raw)
            assertTrue(
                "opacity $raw produced alpha ${palette.containerColor.alpha}",
                palette.containerColor.alpha >= AppTopBarDefaults.MINIMUM_ALPHA
            )
        }
    }

    @Test
    fun theOpacityPreferenceIsIgnoredWhileGlassIsOff() {
        listOf(GlassRanges.OPACITY_MIN, GlassRanges.OPACITY_MAX).forEach { opacity ->
            assertEquals(
                "opacity $opacity leaked into the opaque look",
                1f,
                paletteFor(AppTheme.GREEN, Color(0xFF2E7D32), glass = false, glassOpacity = opacity)
                    .containerColor.alpha,
                0f
            )
        }
    }

    @Test
    fun theFloorIsHighEnoughToMatter() {
        // If the floor were 0 the sweep above would be checking nothing. It has to
        // sit above the alpha a genuinely glassy panel would use.
        assertTrue(
            "the floor (${AppTopBarDefaults.MINIMUM_ALPHA}) must exceed a card's translucency",
            AppTopBarDefaults.MINIMUM_ALPHA > panelAlphaFor(GlassRanges.OPACITY_MIN)
        )
    }

    /* ── 5. One component, not eight ──────────────────────────────────────── */

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
        val withBack = appTopBarPaletteFor(
            scheme = schemeFor(AppTheme.GREEN, Color(0xFF2E7D32), false),
            glassOpacity = GlassRanges.OPACITY_DEFAULT,
            glassEnabled = false,
            showNavigationIcon = true
        )
        val withoutBack = appTopBarPaletteFor(
            scheme = schemeFor(AppTheme.GREEN, Color(0xFF2E7D32), false),
            glassOpacity = GlassRanges.OPACITY_DEFAULT,
            glassEnabled = false,
            showNavigationIcon = false
        )
        assertEquals(withBack.containerColor, withoutBack.containerColor)
        assertEquals(withBack.titleContent, withoutBack.titleContent)
    }
}
