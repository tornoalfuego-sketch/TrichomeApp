package com.trichome.app.ui.screens.breeding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the rule that a screen has one owner per scroll axis.
 *
 * The crash this exists for was real and reproducible on a device: tapping
 * "Teoría" in the Biblia de Breeding killed the process with
 * `IllegalStateException: Vertically scrollable component was measured with an
 * infinity maximum height constraints`. The cause is structural and invisible to
 * the compiler -- `TheoryTab` scrolls, and it mounts two children that each
 * scrolled too. Compose measures a child of a `verticalScroll` with an infinite
 * maximum height, so the inner scrollable throws when it composes.
 *
 * Nothing about that shape fails to compile, fails lint, or fails a unit test of
 * the genetics underneath. It only fails when a human taps a tab, which is why
 * it shipped. The rule is checked against the source instead.
 */
class ScrollOwnershipTest {

    private val sources: List<File> = listOf(
        File("src/main/java/com/trichome/app/ui/screens/breeding"),
        File("app/src/main/java/com/trichome/app/ui/screens/breeding")
    ).filter { it.isDirectory }.flatMap { dir ->
        dir.listFiles { f -> f.name.endsWith(".kt") }?.toList() ?: emptyList()
    }

    private fun sourceOf(name: String): String =
        sources.firstOrNull { it.name == name }
            ?.readText(Charsets.UTF_8)
            ?: error("$name is not in the breeding package")

    /** Source with block comments removed, so a KDoc example is not a hit. */
    private fun codeOf(name: String): String =
        sourceOf(name).replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")

    /**
     * The composables that are mounted *inside* the theory tab's own scroll.
     *
     * They are listed by name rather than discovered, because the discovery would
     * be the same fragile text scan the test is trying to replace: a new child
     * added to the `when` is a new crash, and it has to be added here on purpose.
     */
    private val childrenOfTheScrollableHost = listOf(
        "TheoryChaptersTab" to "TheoryChaptersTab.kt",
        "PunnettSquarePanel" to "PunnettSquarePanel.kt"
    )

    @Test
    fun theHostOfTheTheoryTabStillScrolls() {
        // The other half of the contract. Removing the children's scroll without
        // this one would leave the tab unable to scroll at all, and it would look
        // like a fix until the content overflowed.
        val text = codeOf("BreedingScreen.kt")
        assertTrue(
            "TheoryTab must own the vertical scroll, or nothing in the tab can scroll",
            Regex("""private fun TheoryTab\(""").containsMatchIn(text) &&
                text.contains("verticalScroll")
        )
    }

    @Test
    fun noChildOfTheScrollableHostScrollsAgain() {
        val offenders = childrenOfTheScrollableHost.filter { (fn, file) ->
            bodyOf(codeOf(file), fn).contains("verticalScroll")
        }

        assertTrue(
            "these are mounted inside TheoryTab, which already scrolls, and each " +
                "scrolls again: ${offenders.map { it.first }}",
            offenders.isEmpty()
        )
    }

    /**
     * The text of one composable, from its declaration to the next one.
     *
     * Scoped to a single composable on purpose: an import, a KDoc example, or an
     * unrelated composable in the same file must not decide this.
     */
    private fun bodyOf(text: String, functionName: String): String {
        val start = text.indexOf("fun $functionName(")
        assertTrue("$functionName is not in the source", start > 0)
        val rest = text.substring(start)
        val end = Regex("""\n(@Composable|(private |internal )?fun )""").find(rest)
            ?.range?.first
            ?: rest.length
        return rest.substring(0, end)
    }

    @Test
    fun theChildPanelsStillFillTheWidthTheyAreGiven() {
        // Dropping the scroll must not drop the layout: a panel that forgets its
        // modifier stops filling and the cards collapse to the text width.
        childrenOfTheScrollableHost.forEach { (fn, file) ->
            val body = bodyOf(codeOf(file), fn)
            assertTrue(
                "$fn must keep fillMaxWidth after the scroll was removed",
                body.contains("fillMaxWidth")
            )
        }
    }

    @Test
    fun theVerticalScrollImportIsNotLeftBehind() {
        // An unused import is not a crash, but it is the fingerprint of this bug
        // being half-reverted, and it is exactly what a partial merge leaves.
        val unused = childrenOfTheScrollableHost.filter { (_, file) ->
            val text = sourceOf(file)
            val importsVerticalScroll = text.contains("import androidx.compose.foundation.verticalScroll")
            val bodyUsesIt = Regex("""\.verticalScroll\(""").containsMatchIn(text)
            importsVerticalScroll && !bodyUsesIt
        }

        assertEquals(
            "these files still import verticalScroll without using it",
            emptyList<String>(),
            unused.map { it.second }
        )
    }
}
