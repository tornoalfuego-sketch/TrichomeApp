package com.trichome.app.ui.screens.settings

import androidx.compose.ui.graphics.Color
import com.trichome.app.ui.theme.AccentPalette
import com.trichome.app.ui.theme.AppTheme
import com.trichome.app.ui.theme.ColorOverrides
import com.trichome.app.ui.theme.MINIMUM_TEXT_CONTRAST
import com.trichome.app.ui.theme.SolidPalettes
import com.trichome.app.ui.theme.contrastRatio
import com.trichome.app.ui.theme.readableOnStrict
import com.trichome.app.ui.theme.resolvedTextColors
import com.trichome.app.ui.theme.solidSchemeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the dialog's two presentation promises.
 *
 * A user picked a text colour and saw nothing change. The app was right -- the
 * scheme already carries the resolved colour and the dialog already reported it
 * truthfully -- so the only thing that could have been wrong was what the dialog
 * *showed*. Four equal panels, each with a description, never told the user which
 * role owns the big text, which is the question they were actually asking.
 *
 * Separate from `ColorOverrideTest` on purpose. That file guards the resolution
 * rules in the theme -- what survives a pick, what gets derived from it. This one
 * guards what the dialog does with the answer, and it reads the dialog's source
 * because a composable cannot be exercised from a plain JVM test: neither
 * Robolectric nor `compose-ui-test` is on the unit-test classpath, and adding
 * either is out of scope for a presentation change.
 */
class ColorRolesDialogPreviewTest {

    private val darkThemes = listOf(AppTheme.GREEN, AppTheme.AUTUMN, AppTheme.NIGHT)
    private val lightThemes = listOf(AppTheme.SUNNY)

    /**
     * The dialog's source with comments removed.
     *
     * Block and line comments alike. Both this file and the dialog's own KDoc name
     * `selected`, `currentRole` and `previewText` in prose, so a scan that read the
     * prose would be asserting against its own explanation -- the trap every other
     * source-scanning test in this project already had to handle.
     */
    private val dialogCode: String by lazy {
        stripComments(
            listOf(
                File("src/main/java/com/trichome/app/ui/screens/settings/ColorRolesDialog.kt"),
                File("app/src/main/java/com/trichome/app/ui/screens/settings/ColorRolesDialog.kt")
            ).firstOrNull { it.isFile }?.readText(Charsets.UTF_8)
                ?: error("could not locate ColorRolesDialog.kt")
        )
    }

    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("""(?m)//.*$"""), " ")

    /* ── The preview reports the resolved colour, not the stored pick ─────── */

    @Test
    fun theSourceScanIsNotInert() {
        // A scan that silently matches nothing passes every other test here.
        assertTrue("the dialog source was not found", dialogCode.length > 500)
        assertTrue(
            "the comment stripper removed the whole file",
            dialogCode.contains("ColorRoleSection")
        )
    }

    @Test
    fun aRejectedPickPreviewsTheFallbackAndNotTheColourTheUserTapped() {
        // The case that makes the whole distinction matter. The user *did* tap a
        // colour, the store *does* hold it, and `Ahora: #...` *does* print it -- but
        // the pick never cleared the text bar, so the app paints the palette. A
        // preview built from the stored pick would show a colour the screen is not
        // using, which is a second false report on top of the first.
        val unreadable = Color(0xFF102010)
        val scheme = solidSchemeFor(
            AppTheme.GREEN,
            AccentPalette.DEFAULT_ACCENT,
            ColorOverrides(primaryText = unreadable)
        )

        // The premise, asserted: this fixture is genuinely a rejected pick, so a
        // preview reading `selected` would differ from what the screen paints.
        assertNotEquals(
            "the fixture is supposed to be a pick the app refuses",
            unreadable, scheme.onSurface
        )
        assertEquals(
            SolidPalettes.GREEN.onSurface,
            scheme.onSurface
        )
    }

    @Test
    fun aDerivedLevelPreviewsTheDerivedColourAndNotThePaletteDefault() {
        // Setting only the primary is the reported bug. The secondary level the
        // screen paints is *derived* from the pick, so it is neither a stored pick
        // nor the palette default. A preview taken from either would misrepresent
        // the one rule the user cannot see.
        val resolved = resolvedTextColors(
            AppTheme.NIGHT,
            ColorOverrides(primaryText = Color(0xFFFFC107))
        )

        assertNotEquals(
            "the derived level is the palette default, so this proves nothing",
            SolidPalettes.NIGHT.onSurfaceVariant, resolved.secondary
        )
        assertNotEquals(
            "the derived level collapsed onto the primary",
            resolved.primary, resolved.secondary
        )
    }

    @Test
    fun aFilledRolePreviewsWithALabelResolvedFromTheFill() {
        // The button role is a fill, not an ink, so its sample sits on the fill the
        // way a real button's label does. The label colour is derived from that
        // fill -- the same pairing `solidSchemeFor` builds into `onPrimary` -- so
        // the preview cannot show a label the app would never draw.
        (darkThemes + lightThemes).forEach { theme ->
            val scheme = solidSchemeFor(
                theme,
                AccentPalette.DEFAULT_ACCENT,
                ColorOverrides(button = Color(0xFF7C4DFF))
            )
            assertEquals(
                "the fill preview on theme ${theme.index} is not the button role",
                scheme.primary,
                Color(0xFF7C4DFF)
            )
            assertTrue(
                "the previewed button label is unreadable on the fill it previews, " +
                    "on theme ${theme.index}",
                contrastRatio(readableOnStrict(scheme.primary), scheme.primary) >=
                    MINIMUM_TEXT_CONTRAST
            )
        }
    }

    @Test
    fun thePreviewIsBoundToTheResolvedRoleAndNeverToTheStoredPick() {
        // The tests above pin what the *value* has to be; this one pins the wiring,
        // because the two can drift apart. A preview rendered from `selected` would
        // pass every behavioural test here -- `currentRole` would still be correct
        // in the caller -- while the preview showed something the screen does not.
        assertTrue(
            "RolePreview is not fed the resolved role",
            Regex("""role\s*=\s*currentRole""").containsMatchIn(dialogCode)
        )
        assertFalse(
            "the preview is rendered from the stored pick rather than the resolved role",
            Regex("""role\s*=\s*selected""").containsMatchIn(dialogCode)
        )
    }

    @Test
    fun everyRoleSectionResolvesItsOwnColour() {
        // Anchored at the start of a line so the `private fun ColorRoleSection(`
        // declaration is not counted as a fifth call site.
        val sections = Regex("""(?m)^\s*ColorRoleSection\(""").findAll(dialogCode).count()
        assertEquals("the dialog no longer has four role sections", 4, sections)
        assertEquals(
            "a role section resolves no colour of its own",
            sections,
            Regex("""currentRole\s*=\s*\w""").findAll(dialogCode).count()
        )
    }

    @Test
    fun everyRoleSectionPreviewsAtTheTypeStyleThatRoleActuallyPaintsWith() {
        // "The preview should look like a title" cannot be asserted on the value
        // alone: four sections in the same colour at the same size would satisfy
        // every behavioural test above and still tell the user nothing about which
        // role owns the big text.
        listOf(
            "Texto primario" to "typography.titleMedium",
            "Texto secundario" to "typography.bodyMedium",
            "Texto terciario" to "typography.labelSmall",
            "Color de los botones" to "typography.labelLarge"
        ).forEach { (title, expectedStyle) ->
            val call = dialogCode
                .substringAfter("title = \"$title\"")
                .substringBefore("onPick =")
            assertTrue(
                "the \"$title\" section does not preview at $expectedStyle, so it " +
                    "cannot be told apart from the other levels",
                call.contains(expectedStyle)
            )
        }
    }

    @Test
    fun everyRoleSectionCarriesASampleString() {
        // A preview with nothing in it previews nothing, and it fails silently: the
        // section still compiles, still scrolls, still looks plausible.
        assertEquals(
            "a role section has no sample text to preview",
            4,
            Regex("""previewText\s*=\s*"[^"]+"""").findAll(dialogCode).count()
        )
    }

    /* ── The one-line summary ─────────────────────────────────────────────── */

    private val roleLabels = listOf(
        "Texto primario", "Texto secundario", "Texto terciario",
        "Color de los botones"
    )

    @Test
    fun anUntouchedInstallIsSummarisedAsUsingTheThemeEverywhere() {
        assertEquals(
            "Ningún color personalizado: los cuatro usan el del tema.",
            colorOverrideSummary(null, null, null, null)
        )
    }

    @Test
    fun aFullyCustomisedInstallNamesAllFourRolesAndNoneAsDefault() {
        val pick = Color(0xFFFFC107)
        val summary = colorOverrideSummary(pick, pick, pick, pick)

        assertTrue(
            "a fully customised install must not claim anything is on the default: $summary",
            !summary.contains("Del tema")
        )
        roleLabels.forEach {
            assertTrue("the summary omits $it: $summary", summary.contains(it))
        }
    }

    @Test
    fun aSingleCustomisedRoleIsNamedSingularlyAndTheRestAsThemeDefaults() {
        // The reported state: only the secondary override was persisted. The line
        // has to make that legible, and in the singular -- one customised role out of
        // four is not "Personalizados".
        assertEquals(
            "Personalizado: Texto secundario. Del tema: Texto primario, Texto terciario " +
                "y Color de los botones.",
            colorOverrideSummary(null, Color(0xFFFFC107), null, null)
        )
    }

    @Test
    fun theSummaryNamesEveryRoleExactlyOnce() {
        // Each role appears once, so the line reads as a partition of the four
        // rather than as a list that happens to mention some of them.
        val pick = Color(0xFFFFC107)
        val summary = colorOverrideSummary(pick, null, pick, null)

        roleLabels.forEach { role ->
            val occurrences = Regex(Regex.escape(role)).findAll(summary).count()
            assertEquals("the role \"$role\" appears $occurrences times in: $summary", 1, occurrences)
        }
    }

    @Test
    fun theSummaryIsDerivedFromTheStoredPicksAndNotFromTheResolvedColours() {
        // Same trap as the preview: splitting on the resolved colours would call a
        // role "on the theme default" whenever the palette happens to agree with
        // the pick, which is a lie about what the user chose.
        val matchesThePalette = SolidPalettes.SUNNY.onSurface

        assertEquals(
            "the fixture has to be the palette's own colour for this to prove anything",
            SolidPalettes.SUNNY.onSurface, matchesThePalette
        )
        val summary = colorOverrideSummary(matchesThePalette, null, null, null)

        assertTrue(
            "a role holding a colour equal to the palette default was reported as " +
                "untouched: $summary",
            summary.startsWith("Personalizado: Texto primario.")
        )
    }

    @Test
    fun theSummaryStaysToOneSentenceForEveryCombination() {
        // It is a summary. Sixteen combinations, and any that wraps into the panel
        // below it is a paragraph where a glance was promised.
        val pick = Color(0xFFFFC107)
        val choices = listOf(pick, null)
        var combinations = 0

        choices.forEach { a ->
            choices.forEach { b ->
                choices.forEach { c ->
                    choices.forEach { d ->
                        val summary = colorOverrideSummary(a, b, c, d)
                        combinations++
                        assertFalse("the summary contains a newline: $summary", summary.contains('\n'))
                        assertTrue(
                            "the summary is not a single sentence: $summary",
                            summary.trimEnd().endsWith(".")
                        )
                        assertTrue(
                            "the summary says nothing at all: $summary",
                            summary.length > 10
                        )
                    }
                }
            }
        }
        assertEquals("the sweep did not cover all sixteen combinations", 16, combinations)
    }
}