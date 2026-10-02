package com.trichome.app.ui.screens.terpenes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F2 — the structural rules of the terpene detail page's volatility section.
 *
 * ## Why this reads source
 *
 * The failure modes it guards are not behavioural and no JVM test can reach them:
 *
 * 1. **The evidence line is on the card.** [TerpeneVolatilityTest] proves the
 *    card *model* always carries it. This proves the card *composable* renders it
 *    unconditionally, with no `if`, no `AnimatedVisibility` and no "ver más" —
 *    the same rule `EntourageScrollOwnershipTest` holds the Séquito card to. The
 *    user asked for this module's standard explicitly: the evidence line has to
 *    be visible, not behind a disclosure.
 * 2. **One scroll owner per axis.** A nested scroll inside the page's
 *    `LazyColumn` throws at runtime with an infinite maximum height. That crash
 *    shipped once already, which is why `ScrollOwnershipTest` exists.
 * 3. **No hardcoded colour, no unspecified surface.** A literal colour is one no
 *    theme check covers; `Color.Unspecified` into a `Surface` is a subtree
 *    painted from an undefined value.
 *
 * Comments are stripped before every scan, so a KDoc that mentions
 * `verticalScroll` or `AnimatedVisibility` is not a hit — the same rule the
 * other source-scanning tests follow, and the one that has broken twice.
 */
class TerpeneDetailVolatilityTest {

    private val sources: List<File> = listOf(
        File("src/main/java/com/trichome/app/ui/screens/terpenes"),
        File("app/src/main/java/com/trichome/app/ui/screens/terpenes")
    ).filter { it.isDirectory }

    /** Source with block and line comments removed. */
    private fun codeOf(name: String): String {
        val file = sources.flatMap { dir ->
            dir.listFiles { f -> f.name.endsWith(".kt") }?.toList() ?: emptyList()
        }.firstOrNull { it.name == name }
            ?: error("$name is not in the terpenes package")
        return stripComments(file.readText(Charsets.UTF_8))
    }

    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("""//[^\n]*"""), " ")

    private fun detailScreen(): String = codeOf("TerpeneDetailScreen.kt")

    /* ── The evidence line is rendered, and is not a disclosure ──────────── */

    @Test
    fun theVolatilityCardIsOnThePageForEveryCompound() {
        val text = detailScreen()

        assertTrue(
            "the volatility row must reach the screen",
            text.contains("VolatilityCard(")
        )
        assertTrue(
            "the row comes from the ViewModel's single index, not from a local guess",
            text.contains("vm.volatilityOf(")
        )
        assertFalse(
            "the page must not assemble a temperature of its own",
            Regex("""windowEs\s*=\s*""").containsMatchIn(text)
        )
    }

    @Test
    fun theEvidenceAndLimitsLinesAreRenderedUnconditionally() {
        val text = detailScreen()

        assertTrue(
            "the sentence saying where the band came from must be rendered",
            text.contains("content.evidenceEs")
        )
        assertTrue(
            "the sentence saying what a band is not must be rendered",
            text.contains("content.limitsEs")
        )
        assertTrue(
            "the provenance label must be a rendered row, not a comment",
            text.contains("content.provenanceLabelEs")
        )
        assertTrue(
            "the row label must name where the band came from",
            text.contains("\"Origen de la ventana\"")
        )
    }

    @Test
    fun theEvidenceLineIsNotBehindADisclosure() {
        val text = detailScreen()

        // `DetailParagraph` early-returns on a blank value, which is the only
        // conditional allowed: an evidence line that exists is an evidence line
        // that shows. A disclosure would hide a field the model always fills.
        assertTrue(
            "the render has to go through DetailParagraph, whose contract is to " +
                "print any non-blank value",
            text.contains("DetailParagraph(\"\", content.evidenceEs)") &&
                text.contains("DetailParagraph(\"\", content.limitsEs)")
        )
    }

    @Test
    fun theBoosterReportsTheContradictionAsRungsAndNotOnlyAsABoolean() {
        val text = codeOfIn(
            "src/main/java/com/trichome/app/ui/screens/entourage",
            "EntourageBoosterSection.kt"
        )

        assertTrue(
            "the staged curve has to reach the module's screen too",
            text.contains("vapour.stageLinesEs")
        )
        assertTrue(
            "the curve must be named so it is not read as another score",
            text.contains("Curva de calor")
        )
        assertTrue(
            "the curve's scope must be stated",
            text.contains("Orden de salida de los compuestos elegidos")
        )
    }

    /* ── One scroll owner per axis ────────────────────────────────────────── */

    @Test
    fun theVolatilitySectionDeclaresNoSecondScroll() {
        val text = detailScreen()

        assertFalse(
            "the page's LazyColumn owns the scroll; a nested verticalScroll inside " +
                "it is measured with an infinite maximum height and throws",
            text.contains("verticalScroll")
        )
    }

    @Test
    fun thePageStillOwnsExactlyOneLazyColumn() {
        val text = detailScreen()

        assertTrue(
            "the detail page must still be scrollable",
            text.contains("LazyColumn(")
        )
        assertEquals(
            "a second list would be a second scroll owner",
            1,
            Regex("""LazyColumn\(""").findAll(text).count()
        )
    }

    /* ── Colours go through the theme ────────────────────────────────────── */

    @Test
    fun noHardcodedColourReachesTheSection() {
        val offenders = Regex("""Color\(0x""").findAll(detailScreen()).map { it.value }.toList()

        assertTrue("these hardcode a colour instead of reading the scheme: $offenders", offenders.isEmpty())
    }

    @Test
    fun noSurfaceIsPublishedAnUnspecifiedColour() {
        val offenders = sources.flatMap { dir ->
            dir.listFiles { f -> f.name.endsWith(".kt") }?.toList() ?: emptyList()
        }.map { it.name }.filter { name ->
            Regex("""Surface\((?:[^)]*\bcolor\s*=\s*Color\.Unspecified)""")
                .containsMatchIn(codeOf(name))
        }

        assertTrue("a Surface must never be given Color.Unspecified: $offenders", offenders.isEmpty())
    }

    @Test
    fun theSectionHasNoGlassmorphism() {
        // The terpenes package is solid Material 3. A blurred or translucent
        // surface would reintroduce the contrast class of defect the theme
        // package removed.
        listOf("blur(", "BlurredEdgeTreatment", "graphicsLayer", "GlassPanel").forEach { banned ->
            assertFalse(
                "the terpenes screens must stay solid, found $banned",
                detailScreen().contains(banned)
            )
        }
    }

    /* ── The lookup rule lives in the domain layer ───────────────────────── */

    @Test
    fun theBarGeometryIsComputedInTheModelNotInTheComposable() {
        val text = detailScreen()

        assertTrue(
            "Compose has no JVM unit-test runtime here, so the fractions must be " +
                "the model's",
            text.contains("curve.barFor(")
        )
        assertFalse(
            "the composable must not divide temperatures itself",
            Regex("""\.toFloat\(\)\s*/""").containsMatchIn(text)
        )
    }

    private fun codeOfIn(dirPath: String, name: String): String {
        val file = listOf(File(dirPath), File("app/$dirPath"))
            .firstOrNull { it.isDirectory }
            ?.listFiles { f -> f.name == name }
            ?.firstOrNull()
            ?: error("could not locate $name in $dirPath")
        return stripComments(file.readText(Charsets.UTF_8))
    }
}