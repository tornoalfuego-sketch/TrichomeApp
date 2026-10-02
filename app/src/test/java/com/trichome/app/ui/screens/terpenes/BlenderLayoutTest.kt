package com.trichome.app.ui.screens.terpenes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the contract that the Master Blender's content is actually reachable.
 *
 * ## The bug this locks down
 *
 * The body was `Column(Modifier.verticalScroll(state).heightIn(max = 60% of the
 * screen))`. Modifier order is not cosmetic: `heightIn` applied *after*
 * `verticalScroll` constrains the content the scroll is scrolling, so the
 * viewport and the content were the same height and there was nothing to travel.
 * The dialog could not be scrolled at all -- confirmed on the device, where a
 * 500px swipe produced a byte-identical screenshot. The fifth slider, the score
 * report and the honesty note were gone, not merely hidden.
 *
 * ## What is pinned, and what is not
 *
 * Compose cannot be unit-tested in this module, so what is asserted here is the
 * two things that made the bug: the arithmetic that decides the cap
 * ([BlenderLayout], pure and testable) and the *order* of the modifiers in the
 * source, which is the part a compiler and a lint run both accept happily.
 *
 * What this does NOT cover: that the dialog actually scrolls on a device, and
 * that the report renders. Those were checked by hand on real hardware and are
 * not automatable here.
 */
class BlenderLayoutTest {

    @Test
    fun theBodyGetsTheScreenMinusTheDialogsOwnChrome() {
        // 851dp screen - 168dp of chrome. The old 60% gave 510dp, which fit four
        // sliders on this device and would have fit fewer on a shorter one.
        assertEquals(851 - BlenderLayout.DIALOG_CHROME_DP, BlenderLayout.bodyMaxHeightDp(851))
    }

    @Test
    fun theBodyIsNeverLeftWithNoHeightAtAll() {
        // The regression that matters: a cap that returns 0 or less reproduces the
        // clipping bug with a smaller number in it. Every screen, however short,
        // has to leave the body something to work with.
        listOf(0, 1, 50, 100, 168, 169, 200, 320, 400, 851, 1200).forEach { screen ->
            assertTrue(
                "a ${screen}dp screen left the body ${BlenderLayout.bodyMaxHeightDp(screen)}dp",
                BlenderLayout.bodyMaxHeightDp(screen) >= BlenderLayout.MIN_BODY_DP
            )
        }
    }

    @Test
    fun aScreenShorterThanTheChromeFallsBackToTheFloorRatherThanToZero() {
        // A 100dp screen minus 168dp of chrome is -68dp. Coerced to the floor, not
        // to a negative height that a `heightIn` would silently treat as "no cap".
        assertEquals(BlenderLayout.MIN_BODY_DP, BlenderLayout.bodyMaxHeightDp(100))
    }

    @Test
    fun theBodyAlwaysLeavesRoomForTheTitleAndTheConfirmButton() {
        // The reason the cap exists at all. Above this line the dialog runs off the
        // window and the "Cerrar" button is stranded somewhere in the middle of it.
        listOf(240, 360, 480, 640, 851, 1024).forEach { screen ->
            val body = BlenderLayout.bodyMaxHeightDp(screen)
            assertTrue(
                "on a ${screen}dp screen the body (${body}dp) plus the chrome " +
                    "(${BlenderLayout.DIALOG_CHROME_DP}dp) is taller than the screen",
                body + BlenderLayout.DIALOG_CHROME_DP <= screen ||
                    screen < BlenderLayout.MIN_BODY_DP + BlenderLayout.DIALOG_CHROME_DP
            )
        }
    }

    @Test
    fun theBodyGrowsWithTheScreenSoATallerDeviceSeesMore() {
        assertTrue(
            "a 1200dp screen must not show less than an 800dp one",
            BlenderLayout.bodyMaxHeightDp(1200) > BlenderLayout.bodyMaxHeightDp(800)
        )
    }

    @Test
    fun theCapIsAppliedBeforeTheScrollAndNotAfterIt() {
        // The whole fix, as a source assertion.
        //
        // `heightIn` before `verticalScroll` constrains what the scroll is allowed
        // to *occupy*; the content is still measured unbounded and can travel. The
        // reverse -- which is what shipped -- constrains the content, and the
        // scroll ends up with a viewport exactly as tall as its content.
        val code = sourceOf("MasterBlenderDialog.kt")

        assertTrue(
            "the dialog must still scroll, or nothing past the first slider is reachable",
            code.contains(".verticalScroll(")
        )
        assertTrue(
            "the height cap must not be applied after the scroll: that constrains " +
                "the content instead of the viewport and the dialog cannot scroll",
            !Regex("""\.verticalScroll\([^)]*\)\s*\n?\s*\.heightIn\(""").containsMatchIn(code)
        )
        assertTrue(
            "the cap must come from BlenderLayout, not from a literal share of the " +
                "screen: the arithmetic is what the tests above cover",
            code.contains("BlenderLayout.bodyMaxHeightDp")
        )
    }

    @Test
    fun theCapIsNoLongerAShareOfTheScreen() {
        // A regression guard on the specific wrong value. A future edit that
        // restores "60% of the screen" passes the modifier-order test above and
        // still clips on a short device; this one does not.
        val code = sourceOf("MasterBlenderDialog.kt")
        assertTrue(
            "the 0.6f screen share is what shipped with the dead scroll and must not " +
                "come back",
            !Regex("""screenHeightDp\s*\*\s*0\.\d+f?\.dp""").containsMatchIn(code)
        )
    }

    @Test
    fun theDialogSaysWhatItIsFor() {
        // The other half of the complaint: "no se entiende para qué esa
        // funcionalidad". A blender that never states the comparison it performs
        // is five sliders and a number with no question attached to it.
        val code = sourceOf("MasterBlenderDialog.kt")
        assertTrue(
            "the dialog must state what it compares, in Spanish, before the sliders",
            code.contains("perfil de terpenos de tu planta") &&
                code.contains("perfil de referencia")
        )
        assertTrue(
            "the report must be signposted as the answer to the sliders, not left " +
                "as an unlabelled block below them",
            code.contains("2 · Coincidencia con la referencia")
        )
    }

    @Test
    fun theSliderSectionIsLabelledAsTheInputAndNotAsAResult() {
        // A first draft of this fix put "Resultado de la mezcla" above the sliders.
        // It read correctly as a heading and was still wrong: those are the controls
        // the grower is setting, and labelling the input as the output is the same
        // confusion the complaint describes, relocated. The two sections are
        // numbered, and the number has to increase down the dialog.
        val code = sourceOf("MasterBlenderDialog.kt")
        val input = code.indexOf("1 · Tu análisis")
        val output = code.indexOf("2 · Coincidencia con la referencia")

        assertTrue("the input section must be labelled", input > 0)
        assertTrue("the result section must be labelled", output > 0)
        assertTrue(
            "the input section has to come before the result section",
            input < output
        )
        assertTrue(
            "the sliders belong to the input section, not the result one",
            code.indexOf("featured.forEach") in input until output
        )
    }

    private fun sourceOf(name: String): String =
        listOf(
            File("src/main/java/com/trichome/app/ui/screens/terpenes"),
            File("app/src/main/java/com/trichome/app/ui/screens/terpenes")
        )
            .filter { it.isDirectory }
            .flatMap { dir -> dir.listFiles { f -> f.name == name }?.toList() ?: emptyList() }
            .firstOrNull()
            ?.let { stripComments(it.readText(Charsets.UTF_8)) }
            ?: error("$name is not in the terpenes package")

    /**
     * Source with block and line comments removed.
     *
     * Both kinds: this file and the dialog it guards both explain the dead scroll
     * in prose, and a `//` naming `verticalScroll` after the modifier would
     * otherwise register as a call site and make this test fail on its own
     * diagnosis.
     */
    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("""(?m)//.*$"""), " ")
}
