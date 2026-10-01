package com.trichome.app.ui.screens.settings

import androidx.compose.ui.graphics.Color
import com.trichome.app.ui.theme.AccentPalette
import com.trichome.app.ui.theme.AppTheme
import com.trichome.app.ui.theme.ColorOverrides
import com.trichome.app.ui.theme.MINIMUM_NON_TEXT_CONTRAST
import com.trichome.app.ui.theme.MINIMUM_TEXT_CONTRAST
import com.trichome.app.ui.theme.SolidPalettes
import com.trichome.app.ui.theme.contrastRatio
import com.trichome.app.ui.theme.readableOnStrict
import com.trichome.app.ui.theme.resolvedTextColors
import com.trichome.app.ui.theme.solidSchemeFor
import com.trichome.app.ui.theme.toArgbHex
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

    /* ── A refused pick must not read as applied anywhere ─────────────────── */

    /**
     * Unreadable on every dark surface, so it stands in for the reported case: the
     * user taps a swatch, the store keeps the colour, and the app paints the
     * palette instead.
     */
    private val unreadable = Color(0xFF102010)

    /** Amber: clears the text bar on every palette shipped. */
    private val readable = Color(0xFFFFC107)

    @Test
    fun theDialogRefusesExactlyWhatTheThemeDrops() {
        // Two implementations of one rule: this predicate in the dialog, and
        // `textOverrideOrNull` in the theme. If they ever disagree, the dialog
        // re-opens the original lie in one direction or the other -- claiming a
        // pick is applied when the app painted the palette, or claiming it was
        // dropped when the app painted the pick.
        (darkThemes + lightThemes).forEach { theme ->
            val surface = SolidPalettes.forTheme(theme).surface
            AccentSwatches.forEach { (name, candidate) ->
                // A kept pick resolves to itself; a dropped one resolves to the
                // palette, or to a level derived from a pick that was not kept.
                val droppedByTheTheme =
                    candidate != resolvedTextColors(theme, ColorOverrides(primaryText = candidate))
                        .primary
                assertEquals(
                    "the dialog and the theme disagree about \"$name\" on theme " +
                        "${theme.index}",
                    droppedByTheTheme,
                    pickIsRejected(candidate, surface, MINIMUM_TEXT_CONTRAST)
                )
            }
        }
    }

    @Test
    fun aRejectedPickIsNotCountedAsCustomisedByTheSummary() {
        // The half of the report the summary got wrong: the store holds the pick,
        // so the line announced a customisation that was never in effect.
        assertTrue(
            "the fixture has to be a refused pick to prove anything",
            pickIsRejected(unreadable, SolidPalettes.NIGHT.surface, MINIMUM_TEXT_CONTRAST)
        )
        val summary = colorOverrideSummary(
            unreadable, null, null, null,
            RejectedPicks(primaryText = true)
        )
        assertEquals(
            "a refused pick was still reported as a customisation: $summary",
            "Ningún color personalizado: los cuatro usan el del tema.",
            summary
        )
    }

    @Test
    fun aRejectedPickIsListedWithTheThemeDefaultsAndAnAcceptedOneIsNot() {
        // The partition has to survive a refusal: one role really was customised
        // and one only looks it, so the line has to put them on opposite sides.
        val summary = colorOverrideSummary(
            unreadable, readable, null, null,
            RejectedPicks(primaryText = true)
        )
        assertEquals(
            "Personalizado: Texto secundario. Del tema: Texto primario, Texto terciario " +
                "y Color de los botones.",
            summary
        )
    }

    @Test
    fun aSuccessfulPickIsStillCountedAsCustomised() {
        // The other side of the same rule. Refusing a pick must not turn the
        // summary into a wall of "Del tema" for picks that did apply.
        assertFalse(
            "the fixture has to clear the text bar to prove anything",
            pickIsRejected(readable, SolidPalettes.NIGHT.surface, MINIMUM_TEXT_CONTRAST)
        )
        assertTrue(
            "a pick that cleared the bar was not reported as customised: " +
                colorOverrideSummary(readable, null, null, null),
            colorOverrideSummary(readable, null, null, null)
                .startsWith("Personalizado: Texto primario.")
        )
    }

    @Test
    fun aButtonFillThatFailsTheNonTextBarIsStillAppliedAndSoIsStillCustomised() {
        // `dropsUnreadablePick = false` on the button role is a claim about the
        // app, not a shortcut: `solidSchemeFor` takes that pick as-is and only
        // derives its label, so calling it dropped would make the dialog lie in
        // the opposite direction -- a fresh report of a change that did happen.
        val weak = Color(0xFF1A1A2E)
        assertTrue(
            "the fixture has to fail the non-text bar to prove anything",
            contrastRatio(weak, SolidPalettes.NIGHT.background) < MINIMUM_NON_TEXT_CONTRAST
        )
        assertEquals(
            "the app no longer applies a button pick that fails the non-text bar, " +
                "so this exemption is no longer true",
            weak,
            solidSchemeFor(
                AppTheme.NIGHT, AccentPalette.DEFAULT_ACCENT, ColorOverrides(button = weak)
            ).primary
        )
        assertTrue(
            "a button pick that is in effect was not reported as customised: " +
                colorOverrideSummary(null, null, null, weak),
            colorOverrideSummary(null, null, null, weak)
                .startsWith("Personalizado: Color de los botones.")
        )
    }

    @Test
    fun theReadoutSaysAPickWasDiscardedAndNamesTheColourActuallyInUse() {
        val inUse = SolidPalettes.NIGHT.onSurface
        val readout = roleReadout(unreadable, rejected = true, inUse = inUse)

        assertFalse(
            "the readout still presents the refused pick as the value in use: $readout",
            readout.contains("Ahora: #${unreadable.toArgbHex()}")
        )
        assertTrue(
            "the readout does not say the pick was discarded: $readout",
            readout.startsWith("Descartado: #${unreadable.toArgbHex()}")
        )
        assertTrue(
            "the readout does not give the reason it was discarded: $readout",
            readout.contains("no se lee sobre esta superficie")
        )
        assertTrue(
            "the readout does not name the colour the role is painting: $readout",
            readout.contains("Se usa el del tema: #${inUse.toArgbHex()}")
        )
    }

    @Test
    fun theReadoutDiffersBetweenAnAcceptedAndARejectedPick() {
        // The one contrast that broke: same stored colour, same section, and the
        // line has to stop claiming it is in use once the app refuses it.
        val inUse = SolidPalettes.NIGHT.onSurface
        assertNotEquals(
            "an accepted and a refused pick read the same",
            roleReadout(readable, rejected = false, inUse = readable),
            roleReadout(readable, rejected = true, inUse = inUse)
        )
    }

    @Test
    fun theAcceptedAndDefaultReadoutWordingIsUnchanged() {
        // Neither of these two was the complaint, and the refusal fix must not
        // have moved a word of them.
        assertEquals(
            "the wording for a pick that applied changed",
            "Ahora: #FFFFC107",
            roleReadout(readable, rejected = false, inUse = readable)
        )
        assertEquals(
            "the wording for a role left on the theme changed",
            "Ahora: el color del tema",
            roleReadout(null, rejected = false, inUse = SolidPalettes.NIGHT.onSurface)
        )
        // And a role on the theme has no pick to refuse, so the flag cannot
        // change what it says.
        assertEquals(
            "the theme wording became conditional on a flag that cannot apply to it",
            "Ahora: el color del tema",
            roleReadout(null, rejected = true, inUse = SolidPalettes.NIGHT.onSurface)
        )
    }

    @Test
    fun theSummaryAndTheReadoutAgreeOnTheSameRefusedPick() {
        // The defect was three answers at once. A pick the summary calls a
        // default must not have a readout calling it the value in use.
        val inUse = SolidPalettes.NIGHT.onSurface
        val summary = colorOverrideSummary(
            unreadable, null, null, null,
            RejectedPicks(primaryText = true)
        )
        val readout = roleReadout(unreadable, rejected = true, inUse = inUse)

        assertTrue(
            "the readout claims the refused pick is in use: $readout",
            readout.contains("Descartado:")
        )
        assertTrue(
            "the summary claims the refused pick is customised: $summary",
            summary.contains("Ningún color personalizado")
        )
    }

    /* ── The wiring, which the behavioural tests above cannot see ──────────── */

    @Test
    fun theDialogThreadsTheRefusedPicksIntoTheSummaryAndEverySection() {
        // The rules above are testable in isolation; this pins the wiring, because
        // a summary called without the refusals counts a refused pick as
        // customised again -- and every behavioural test here still passes.
        assertTrue(
            "the summary is never told which picks were refused",
            Regex("""colorOverrideSummary\([\s\S]*?rejectedPicks""").containsMatchIn(dialogCode)
        )
        assertEquals(
            "a role section was not told whether resolution drops an unreadable pick",
            4,
            Regex("""dropsUnreadablePick\s*=\s*(true|false)""").findAll(dialogCode).count()
        )
        assertTrue(
            "a role section computes the readout without the refusal",
            Regex("""roleReadout\(selected,\s*rejected,""").containsMatchIn(dialogCode)
        )
    }

    @Test
    fun aRefusedSwatchHasItsOwnRingAndIsNotDrawnAsSelected() {
        val swatch = dialogCode.substringAfter("private fun ColorSwatch(")
        assertTrue(
            "the swatch ring has no state of its own for a refused pick",
            Regex("""rejected\s*->""").containsMatchIn(swatch)
        )
        assertTrue(
            "the refused flag never reaches the swatch row, so the ring cannot differ",
            Regex("""rejected\s*=\s*rejected\s*&&\s*selected\s*==\s*color""")
                .containsMatchIn(dialogCode)
        )
        // "Del tema" keeps the ordinary ring. When nothing is picked it really is
        // in effect, so a refused pick must not have redefined what selected means.
        val themeSwatch = dialogCode.substringAfter("private fun ThemeSwatch(")
            .substringBefore("private fun ColorSwatch(")
        assertTrue(
            "the theme swatch lost the ring that marks it as the colour in use",
            Regex("""width\s*=\s*if\s*\(\s*selected\s*\)""").containsMatchIn(themeSwatch)
        )
    }
}