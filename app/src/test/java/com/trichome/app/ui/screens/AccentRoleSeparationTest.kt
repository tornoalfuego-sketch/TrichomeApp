package com.trichome.app.ui.screens

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the rule that separates the accent from body content.
 *
 * The complaint that prompted it was concrete: the quick-action rows on the home
 * screen changed colour when the accent changed, and disagreed with themselves --
 * the leading glyph followed the accent, the label followed the text role, and the
 * chevron followed a third thing. The fix was to give a row one colour source, but
 * a comment does not stop the next call site from reintroducing it, and this
 * class of inconsistency is invisible to the compiler, to lint, and to every test
 * that does not look for it.
 *
 * The rule: `accentLabelOn` is for chrome that *means* the accent -- the selected
 * tab, an accent-coloured action label, a header action. A row of content takes
 * its colours from the text roles, so nothing in a list repaints when the user
 * changes the accent.
 */
class AccentRoleSeparationTest {

    private val productionSources: List<File> = listOf(
        File("src/main/java/com/trichome/app"),
        File("app/src/main/java/com/trichome/app")
    ).firstOrNull { it.isDirectory }
        ?.walkTopDown()
        ?.filter { it.isFile && it.name.endsWith(".kt") }
        ?.toList()
        ?: emptyList()

    /**
     * The composables whose content is a list row, and the file each lives in.
     *
     * Named rather than discovered: a new list row has to be added here on
     * purpose, which is the point. A scan that found them automatically would
     * happily pass a row nobody had thought about.
     */
    private val listRows = listOf(
        "ActionRow" to "home/HomeScreen.kt"
    )

    /** The three call sites that are chrome and are allowed to use the accent. */
    private val chromeThatMayFollowTheAccent = setOf(
        "ui/components/NavigationComponents.kt",
        "ui/components/Panels.kt",
        "ui/screens/terpenes/TerpenesScreen.kt",
        "ui/screens/diagnosis/DiagnosisScreen.kt"
    )

    private fun source(relative: String): String? =
        productionSources.firstOrNull { it.path.replace('\\', '/').endsWith(relative) }
            ?.readText(Charsets.UTF_8)
            ?.let(::stripComments)

    /**
     * Source with comments removed, block and line alike.
     *
     * Both kinds matter: this file documents the change it is guarding, so a KDoc
     * or a `//` that names `accentLabelOn` would otherwise register as a call
     * site and the test would fail on its own explanation.
     */
    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("""(?m)//.*$"""), " ")

    @Test
    fun theSourcesWereActuallyFound() {
        // A scan that silently matches nothing passes every other test here.
        assertTrue(
            "the source scan found no production Kotlin files; the guard is inert",
            productionSources.size > 20
        )
    }

    @Test
    fun noListRowResolvesAGlyphFromTheAccent() {
        val offenders = listRows.filter { (fn, path) ->
            val text = source(path) ?: return@filter false
            val start = text.indexOf("fun $fn(")
            start > 0 && text.substring(start).take(1200).contains("accentLabelOn")
        }

        assertTrue(
            "these are content rows and must take their colours from the text " +
                "roles, or they repaint whenever the accent changes: " +
                offenders.map { it.first },
            offenders.isEmpty()
        )
    }

    @Test
    fun aListRowPaintsBothGlyphsFromTheSameTier() {
        // The other half of the complaint: the row disagreed with itself. Two
        // glyphs, one tier.
        listRows.forEach { (fn, path) ->
            val text = source(path) ?: return@forEach
            val start = text.indexOf("fun $fn(")
            assertTrue("$fn is not in $path", start > 0)
            val body = text.substring(start).take(1200)
            val furniture = Regex("""val (\w+) = LocalTertiaryText\.current""").find(body)
            assertTrue(
                "$fn must resolve one furniture colour for both glyphs",
                furniture != null
            )
            val glyphs = Regex("""Icon\(""").findAll(body).count()
            val tinted = Regex("""tint = ${furniture!!.groupValues[1]}""").findAll(body).count()
            assertTrue(
                "$fn paints $glyphs icons but tints only $tinted from the same tier",
                tinted >= glyphs
            )
        }
    }

    @Test
    fun accentIsStillUsedForTheThingsThatMeanIt() {
        // The rule is a separation, not a purge. If this ever fails, the accent has
        // been stripped from the places it carries meaning -- buttons, the selected
        // tab -- which would be a different kind of inconsistency.
        val navigation = source("components/NavigationComponents.kt")
        assertTrue(
            "the selected tab must still be painted with the accent",
            navigation != null && navigation.contains("indicatorColor = accent")
        )

        val panels = source("components/Panels.kt")
        assertTrue(
            "Panels.kt must still publish the accent-filled button helpers, or the " +
                "accent has no home left in the panel layer at all",
            panels != null &&
                panels.contains("fun accentButtonColors") &&
                panels.contains("fun accentLabelOn")
        )
    }

    @Test
    fun thePanelEdgeCannotBeGivenAColourBack() {
        // The other half of the separation, and the half this file used to state
        // backwards. It asserted that `Panels.kt` still *mentions*
        // `panelBorderColor`, which stayed true forever because only the argument
        // went away, and its message claimed the edge "must still be able to carry
        // the accent" while the code has done the opposite since the edge became a
        // neutral step of the surface.
        //
        // The real contract is that the edge is a pure function of the panel's own
        // surface. A second colour parameter is precisely how a saturated user
        // colour ended up framing every card and out-shouting its text, so the
        // signature is pinned instead of the name. Comments are already stripped
        // from this scan, so the KDoc around it cannot satisfy the assertion.
        val panels = source("components/Panels.kt")
        assertTrue(
            "panelBorderColor must take the surface and nothing else: a second " +
                "parameter is how the accent came back to the panel edge",
            panels != null &&
                Regex("""fun panelBorderColor\(\s*surface:\s*Color\s*\)""").containsMatchIn(panels)
        )
    }

    @Test
    fun theAllowedChromeCallSitesAreTheOnesThatAreDocumentedAsChrome() {
        // A guard on the guard: if a new `accentLabelOn` call site appears outside
        // the four allowed files, this fails and forces a decision rather than
        // letting the list quietly start following the accent again.
        val allowed = chromeThatMayFollowTheAccent
        val actual = productionSources
            .filter { file ->
                Regex("""accentLabelOn\(""").containsMatchIn(stripComments(file.readText(Charsets.UTF_8)))
            }
            .map { it.path.replace('\\', '/').substringAfter("com/trichome/app/") }
            .toSet()

        val unexpected = actual - allowed
        assertTrue(
            "accentLabelOn is now used outside the documented chrome sites: $unexpected. " +
                "A content row that follows the accent repaints when the accent changes.",
            unexpected.isEmpty()
        )
    }
}
