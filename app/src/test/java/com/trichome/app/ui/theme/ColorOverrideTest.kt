package com.trichome.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.trichome.app.data.prefs.AppearanceSettings
import com.trichome.app.data.prefs.appearanceSettingsOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Holds the four user-chosen colours to their contract.
 *
 * These overrides are the widest-reaching change the app has: they are applied in
 * the one function that builds a `ColorScheme`, and that instance is published
 * into `MaterialTheme`, so a bug here does not break one screen -- it repaints
 * or blanks all twenty-five. That is exactly the failure the previous
 * screen-scoped version had: the colour was stored, the dialog reported it, and
 * the list stayed white because a `Surface` publishes its own content colour and
 * ignored the provider wrapped around it. A test that only checks the store
 * would have passed while the feature did nothing.
 */
class ColorOverrideTest {

    private val darkThemes = listOf(AppTheme.GREEN, AppTheme.AUTUMN, AppTheme.NIGHT)
    private val lightThemes = listOf(AppTheme.SUNNY)

    /**
     * [TrichomeThemeState] holds a scope on `Dispatchers.Main`, which a plain JVM
     * test has no module for. Every mutator launches into it, so a test that
     * exercises one has to install a dispatcher first — a state holder that can
     * only be tested by not testing it is a state holder nobody tests.
     */
    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    /* ── Absent preference means the shipped palette ─────────────────────── */

    @Test
    fun anAbsentPreferenceLeavesEveryRoleOnThePalette() {
        // This is the state of every existing install: v1.3.0 added the keys and
        // wrote none of them. If the defaults were seeded with literals instead,
        // upgrading would repaint the app for everybody with no migration and no
        // way to tell later that the value was a default rather than a choice.
        val settings = appearanceSettingsOf()

        assertNull(settings.primaryTextArgb)
        assertNull(settings.secondaryTextArgb)
        assertNull(settings.tertiaryTextArgb)
        assertNull(settings.buttonColorArgb)
    }

    @Test
    fun aSchemeWithNoOverridesIsByteIdenticalToThePalette() {
        // The guarantee that makes the feature safe to ship: a user who never
        // opens the picker gets exactly the colours that shipped.
        darkThemes.forEach { theme ->
            val withDefaults = solidSchemeFor(theme, AccentPalette.DEFAULT_ACCENT)
            val palette = SolidPalettes.forTheme(theme)
            assertEquals(
                "onSurface moved on ${theme.index} without an override",
                palette.onSurface, withDefaults.onSurface
            )
            assertEquals(
                "onSurfaceVariant moved on ${theme.index} without an override",
                palette.onSurfaceVariant, withDefaults.onSurfaceVariant
            )
            assertEquals(
                palette.onSurface, withDefaults.onBackground
            )
        }
    }

    /* ── An override reaches the scheme ──────────────────────────────────── */

    @Test
    fun thePrimaryTextOverrideReachesBothRolesItOwns() {
        val pick = Color(0xFFFFD54F)
        val scheme = solidSchemeFor(
            AppTheme.GREEN,
            AccentPalette.DEFAULT_ACCENT,
            ColorOverrides(primaryText = pick)
        )

        assertEquals(pick, scheme.onSurface)
        // onBackground is a separate role that carries the same literal in all
        // four palettes. Overriding one and not the other left three call sites
        // painting a different colour from the rest of the app.
        assertEquals(pick, scheme.onBackground)
    }

    @Test
    fun theSecondaryTextOverrideReachesTheVariantRole() {
        val pick = Color(0xFF80CBC4)
        val scheme = solidSchemeFor(
            AppTheme.GREEN,
            AccentPalette.DEFAULT_ACCENT,
            ColorOverrides(secondaryText = pick)
        )

        assertEquals(pick, scheme.onSurfaceVariant)
    }

    @Test
    fun theButtonOverrideBecomesThePrimaryRole() {
        val pick = Color(0xFF7C4DFF)
        val scheme = solidSchemeFor(
            AppTheme.GREEN,
            AccentPalette.DEFAULT_ACCENT,
            ColorOverrides(button = pick)
        )

        assertEquals(
            "every filled button, the selected-tab indicator and every accent " +
                "icon read the primary role, so one pick has to move all of them",
            pick, scheme.primary
        )
        // The label on that fill is resolved from the fill, not from the theme,
        // or a light button would carry an unreadable label.
        assertTrue(
            "the button label does not clear the text bar on its own fill",
            contrastRatio(scheme.onPrimary, scheme.primary) >= MINIMUM_TEXT_CONTRAST
        )
    }

    @Test
    fun theAccentStillDrivesTheRoleWhenNoButtonOverrideIsSet() {
        val accent = AccentPalette.PINK_ACCENT_ARG.let { Color(it) }
        val scheme = solidSchemeFor(AppTheme.NIGHT, accent, ColorOverrides())

        assertEquals(accent, scheme.primary)
    }

    /* ── A pick that cannot be read is dropped, not applied ──────────────── */

    @Test
    fun aTextColourThatCannotBeReadFallsBackToThePalette() {
        // The GREEN surface is near-black, so a dark ink is unreadable on it.
        // Applying it anyway would ship an app with invisible labels; nudging it
        // would render something the user did not pick. Falling back is the only
        // outcome that is both honest and legible, and the picker marks the
        // swatch so the choice is never a surprise.
        val unreadable = Color(0xFF102010)
        assertTrue(
            "the fixture is supposed to be unreadable on this surface",
            contrastRatio(unreadable, SolidPalettes.GREEN.surface) < MINIMUM_TEXT_CONTRAST
        )

        val scheme = solidSchemeFor(
            AppTheme.GREEN,
            AccentPalette.DEFAULT_ACCENT,
            ColorOverrides(primaryText = unreadable, secondaryText = unreadable)
        )

        assertEquals(SolidPalettes.GREEN.onSurface, scheme.onSurface)
        assertEquals(SolidPalettes.GREEN.onSurfaceVariant, scheme.onSurfaceVariant)
    }

    @Test
    fun anUnreadableTertiaryPickIsDroppedToo() {
        val unreadable = Color(0xFF101010)
        val resolved = resolvedTextColors(
            AppTheme.GREEN,
            ColorOverrides(tertiaryText = unreadable)
        )
        assertEquals(SolidPalettes.GREEN.onSurfaceMuted, resolved.tertiary)
    }

    @Test
    fun aButtonColourIsJudgedAsAFillAndNotAsBodyText() {
        // 3:1 for non-text content, not 4.5:1. Judging a fill by the text bar
        // would reject colours that are perfectly usable as buttons.
        val scheme = solidSchemeFor(AppTheme.NIGHT, AccentPalette.DEFAULT_ACCENT)
        val weakest = AccentPalette.SELECTABLE_ARGB
            .map { Color(it) }
            .minByOrNull { contrastRatio(it, scheme.background) }!!

        assertTrue(
            "every offered button colour has to read as a shape on the page",
            contrastRatio(weakest, scheme.background) >= MINIMUM_NON_TEXT_CONTRAST
        )
    }

    /* ── The third text level ────────────────────────────────────────────── */

    @Test
    fun theMutedRoleIsNotTheInkItReplaces() {
        // The seventeen call sites used `onSurface.copy(alpha = 0.5f..0.8f)`.
        // A palette entry that equals onSurface would be a lie about the level.
        darkThemes.forEach { theme ->
            val palette = SolidPalettes.forTheme(theme)
            assertNotEquals(
                "the muted role is the same colour as the primary on ${theme.index}",
                palette.onSurface, palette.onSurfaceMuted
            )
            assertNotEquals(
                palette.onSurfaceVariant, palette.onSurfaceMuted
            )
        }
    }

    @Test
    fun theMutedRoleStaysDimmerThanTheSecondaryOne() {
        // Three levels that all read the same are not three levels. It is still
        // required to clear the text bar: it is a caption, not a decoration.
        darkThemes.forEach { theme ->
            val palette = SolidPalettes.forTheme(theme)
            assertTrue(
                "the muted role is not dimmer than the secondary on ${theme.index}",
                contrastRatio(palette.onSurfaceMuted, palette.surface) <
                    contrastRatio(palette.onSurfaceVariant, palette.surface)
            )
            assertTrue(
                "the muted role does not clear the text bar on ${theme.index}",
                contrastRatio(palette.onSurfaceMuted, palette.surface) >= MINIMUM_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun theMutedRoleClearsTheTextBarOnTheLightThemeToo() {
        val palette = SolidPalettes.SUNNY
        assertTrue(
            contrastRatio(palette.onSurfaceMuted, palette.surface) >= MINIMUM_TEXT_CONTRAST
        )
        assertTrue(
            contrastRatio(palette.onSurfaceMuted, palette.background) >= MINIMUM_TEXT_CONTRAST
        )
    }

    @Test
    fun theThreeLevelsAreDistinctOnEveryTheme() {
        (darkThemes + lightThemes).forEach { theme ->
            val resolved = resolvedTextColors(theme, ColorOverrides())
            assertNotEquals("primary and secondary agree on ${theme.index}", resolved.primary, resolved.secondary)
            assertNotEquals("secondary and tertiary agree on ${theme.index}", resolved.secondary, resolved.tertiary)
            assertNotEquals("primary and tertiary agree on ${theme.index}", resolved.primary, resolved.tertiary)
        }
    }

    @Test
    fun theTertiaryOverrideReachesTheResolvedLevel() {
        val pick = Color(0xFFFFF59D)
        val resolved = resolvedTextColors(
            AppTheme.NIGHT,
            ColorOverrides(tertiaryText = pick)
        )
        assertEquals(pick, resolved.tertiary)
    }

    /* ── The levels derive from the primary when only it was picked ───────── */

    /**
     * A pick that is legible on [theme], so the test exercises the accepted path.
     *
     * The light theme needs a dark ink and the dark themes a bright one: amber on
     * white is about 1.9:1, so it is *rejected* there, and a test that assumed
     * otherwise would be asserting that an unreadable pick takes effect. The list
     * spans both ends so [first] finds a candidate for every theme.
     */
    private fun legibleOn(theme: AppTheme): Color {
        val surface = SolidPalettes.forTheme(theme).surface
        return legibleCandidates().firstOrNull {
            contrastRatio(it, surface) >= MINIMUM_TEXT_CONTRAST
        } ?: error("no legible pick exists for theme ${theme.index}")
    }

    private fun legibleCandidates(): List<Color> = listOf(
        Color(0xFFFFC107),
        Color(0xFF80CBC4),
        Color(0xFFFFF59D),
        Color(0xFFB39DDB),
        Color(0xFF1A237E),
        Color(0xFF263238),
        Color(0xFF4E342E)
    )

    @Test
    fun aPickedPrimaryCarriesTheLevelsBelowItWithIt() {
        // The report: the user set the text to one colour and the captions stayed
        // in the old palette's cool grey, so the screen read as two unrelated
        // systems and the lower level was the unreadable one.
        (darkThemes + lightThemes).forEach { theme ->
            val pick = legibleOn(theme)
            val resolved = resolvedTextColors(theme, ColorOverrides(primaryText = pick))
            val palette = SolidPalettes.forTheme(theme)

            assertNotEquals(
                "the secondary stayed in the old palette on theme ${theme.index}",
                palette.onSurfaceVariant, resolved.secondary
            )
            assertNotEquals(
                "the tertiary stayed in the old palette on theme ${theme.index}",
                palette.onSurfaceMuted, resolved.tertiary
            )
        }
    }

    @Test
    fun aDerivedLevelIsTheSameFamilyAsThePrimary() {
        // The blend pulls every channel towards the surface, so the channel
        // *values* shrink -- what has to survive is the hue, i.e. the order and
        // the relative balance of the channels. A derived level that changed hue
        // would reintroduce the two-system problem in a subtler form.
        val pick = Color(0xFFFFC107) // red >> green >> blue
        val resolved = resolvedTextColors(AppTheme.NIGHT, ColorOverrides(primaryText = pick))

        listOf(resolved.primary, resolved.secondary, resolved.tertiary).forEach { level ->
            assertTrue(
                "a derived level changed the channel order of the pick",
                level.red >= level.green && level.green >= level.blue
            )
        }
        // And each level is dimmer than the one above it, in the same hue.
        assertTrue(resolved.secondary.red < resolved.primary.red)
        assertTrue(resolved.tertiary.red < resolved.secondary.red)
    }

    @Test
    fun aRejectedPrimaryDoesNotDragTheOtherLevelsWithIt() {
        // An unreadable pick is dropped, and the levels below must not then be
        // derived from the palette's own primary -- that would repaint them off a
        // colour the user never chose.
        val unreadable = Color(0xFF102010)
        val resolved = resolvedTextColors(
            AppTheme.GREEN,
            ColorOverrides(primaryText = unreadable, secondaryText = unreadable)
        )
        assertEquals(SolidPalettes.GREEN.onSurface, resolved.primary)
        assertEquals(SolidPalettes.GREEN.onSurfaceVariant, resolved.secondary)
    }

    @Test
    fun aDerivedLevelStillClearsTheTextBar() {
        // The whole point of walking the blend back: a level that belongs to the
        // pick and cannot be read is not a solution.
        (darkThemes + lightThemes).forEach { theme ->
            val surface = SolidPalettes.forTheme(theme).surface
            legibleCandidates().forEach { pick ->
                val resolved = resolvedTextColors(theme, ColorOverrides(primaryText = pick))
                listOf(resolved.secondary to "secondary", resolved.tertiary to "tertiary")
                    .forEach { (level, name) ->
                        if (contrastRatio(pick, surface) < MINIMUM_TEXT_CONTRAST) {
                            // Rejected pick, nothing derived, nothing to judge.
                            return@forEach
                        }
                        assertTrue(
                            "the derived $name is unreadable for ${pick.toArgbHex()} " +
                                "on theme ${theme.index}: " +
                                "${contrastRatio(level, surface)}",
                            contrastRatio(level, surface) >= MINIMUM_TEXT_CONTRAST
                        )
                    }
            }
        }
    }

    @Test
    fun aDerivedLevelStaysDistinctFromTheOneAboveIt() {
        val resolved = resolvedTextColors(
            AppTheme.NIGHT,
            ColorOverrides(primaryText = Color(0xFFFFC107))
        )
        assertNotEquals(
            "the derived secondary collapsed onto the primary",
            resolved.primary, resolved.secondary
        )
        assertNotEquals(
            "the derived tertiary collapsed onto the secondary",
            resolved.secondary, resolved.tertiary
        )
    }

    @Test
    fun anExplicitSecondaryIsNotOverriddenByTheDerivation() {
        // Deriving is a fallback, never an override: a user who set all three
        // levels gets all three.
        val secondary = Color(0xFF80CBC4)
        val resolved = resolvedTextColors(
            AppTheme.NIGHT,
            ColorOverrides(primaryText = Color(0xFFFFC107), secondaryText = secondary)
        )
        assertEquals(secondary, resolved.secondary)
    }

    @Test
    fun noDerivationHappensWhenNoTextLevelWasPicked() {
        // With nothing picked, the palette is the answer -- deriving from the
        // palette's own primary would be a no-op at best and a regression at worst.
        (darkThemes + lightThemes).forEach { theme ->
            val resolved = resolvedTextColors(theme, ColorOverrides())
            val palette = SolidPalettes.forTheme(theme)
            assertEquals(palette.onSurface, resolved.primary)
            assertEquals(palette.onSurfaceVariant, resolved.secondary)
            assertEquals(palette.onSurfaceMuted, resolved.tertiary)
        }
    }

    /* ── The state holder ────────────────────────────────────────────────── */

    @Test
    fun theStateStartsWithNoOverridesSoTheFirstFrameIsThePalette() {
        // Same reasoning as `accentColor`: a first frame that differs from the
        // second is a visible repaint on every launch.
        val state = TrichomeThemeState()
        assertEquals(ColorOverrides(), state.colorOverrides)
    }

    @Test
    fun theStateResolvesTheSameTextLevelsTheSchemeDoes() {
        val state = TrichomeThemeState()
        val scheme = state.colorScheme()
        assertEquals(scheme.onSurface, state.textColors.primary)
        assertEquals(scheme.onSurfaceVariant, state.textColors.secondary)
    }

    @Test
    fun aTransparentPickIsRejectedBeforeItReachesTheState() {
        // The old `toArgbInt()` masked the low half of Color.value and wrote 0
        // for every colour. `Color(0)` is fully transparent, so accepting one
        // here would render invisible text -- the accent bug, one level up.
        val state = TrichomeThemeState()
        state.updatePrimaryTextColor(Color.Transparent)
        assertNull(state.colorOverrides.primaryText)
    }

    @Test
    fun anOpaquePickIsKept() {
        val pick = Color(0xFFFFD54F)
        val state = TrichomeThemeState()
        state.updatePrimaryTextColor(pick)
        assertEquals(pick, state.colorOverrides.primaryText)
    }

    /* ── The store ───────────────────────────────────────────────────────── */

    @Test
    fun theStoreReadsBackExactlyWhatWasProjected() {
        val projected = appearanceSettingsOf(
            themeIndex = ThemeIndex.NIGHT,
            primaryTextArgb = 0xFFFFD54F.toInt(),
            secondaryTextArgb = 0xFF80CBC4.toInt(),
            tertiaryTextArgb = 0xFFFFF59D.toInt(),
            buttonColorArgb = 0xFF7C4DFF.toInt()
        )
        assertEquals(0xFFFFD54F.toInt(), projected.primaryTextArgb)
        assertEquals(0xFF80CBC4.toInt(), projected.secondaryTextArgb)
        assertEquals(0xFFFFF59D.toInt(), projected.tertiaryTextArgb)
        assertEquals(0xFF7C4DFF.toInt(), projected.buttonColorArgb)
        assertEquals(ThemeIndex.NIGHT, projected.themeIndex)
    }

    @Test
    fun aStoredZeroIsCorruptionAndNotAChoice() {
        // `takeIf { it != 0 }` on the way in. Reading a 0 as-is means Color(0),
        // and `?:` cannot rescue it because 0 is not null -- the exact trap the
        // accent key was already caught in.
        val projected = appearanceSettingsOf(
            primaryTextArgb = 0,
            secondaryTextArgb = 0,
            tertiaryTextArgb = 0,
            buttonColorArgb = 0
        )
        assertNull(projected.primaryTextArgb)
        assertNull(projected.secondaryTextArgb)
        assertNull(projected.tertiaryTextArgb)
        assertNull(projected.buttonColorArgb)
    }

    @Test
    fun theDefaultSettingsInstanceCarriesNoOverrides() {
        val defaults = AppearanceSettings()
        assertNull(defaults.primaryTextArgb)
        assertNull(defaults.secondaryTextArgb)
        assertNull(defaults.tertiaryTextArgb)
        assertNull(defaults.buttonColorArgb)
    }

    @Test
    fun theAccentDefaultIsUnchangedByTheNewFields() {
        // The new keys are additive; the accent default is the value every
        // existing install already has stored and must not move.
        assertEquals(
            AccentPalette.DEFAULT_ACCENT_ARG,
            appearanceSettingsOf().accentArgb
        )
        assertEquals(AccentPalette.DEFAULT_ACCENT_ARG, AppearanceSettings().accentArgb)
    }

    @Test
    fun switchingTheThemeActuallyChangesTheMutedRole() {
        // A level that stayed the same across all four themes would make the
        // setting look broken on three of them.
        val muted = (darkThemes + lightThemes).map { SolidPalettes.forTheme(it).onSurfaceMuted }
        assertEquals(
            "two themes share a muted text colour",
            muted.size, muted.toSet().size
        )
    }
}
