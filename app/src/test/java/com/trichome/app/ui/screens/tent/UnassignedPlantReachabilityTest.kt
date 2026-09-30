package com.trichome.app.ui.screens.tent

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds the promise the tent delete dialog used to make and could not keep.
 *
 * It said: "sus plantas no se borrarán: quedarán sin asignar a ninguna carpa". The
 * first half was true. The second half was not: `plants.tentId` is declared
 * `ON DELETE SET NULL`, every plant query filtered on `tentId = :tentId`, and no
 * query in the app returned the detached rows. So deleting a tent made its plants
 * exist, get counted by the totals, and become unreachable -- not visible, not
 * openable, not editable, not deletable from any screen. Three of them were found
 * that way in a real install.
 *
 * A room is only safe to empty if somewhere is able to hold what falls out of it.
 */
class UnassignedPlantReachabilityTest {

    private val sources: List<File> = listOf(
        File("src/main/java/com/trichome/app"),
        File("app/src/main/java/com/trichome/app")
    ).firstOrNull { it.isDirectory }
        ?.walkTopDown()
        ?.filter { it.isFile && it.name.endsWith(".kt") }
        ?.toList()
        ?: emptyList()

    private fun source(suffix: String): String? =
        sources.firstOrNull { it.path.replace('\\', '/').endsWith(suffix) }
            ?.readText(Charsets.UTF_8)
            ?.let(::stripComments)

    /**
     * Source with comments removed, block and line alike.
     *
     * This file and the one it guards both *quote* the old promise while
     * explaining why it was wrong, so a scan that reads comments fails on its own
     * explanation -- which is how the first run of this test reported a defect in
     * code that had already been fixed.
     */
    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("""(?m)//.*$"""), " ")

    @Test
    fun theSourcesWereActuallyFound() {
        assertTrue(
            "the source scan matched nothing, so every check here is vacuous",
            sources.size > 20
        )
    }

    @Test
    fun thereIsAQueryThatReturnsDetachedPlants() {
        // The single defect that made them unreachable. `getAllPlants` counts
        // them, so the totals were always right while the screens were not.
        val dao = source("data/dao/AllDaos.kt")
        assertTrue(
            "no query returns plants whose tent is gone; they exist, are counted, " +
                "and cannot be reached from any screen",
            dao != null && dao.contains("tentId IS NULL")
        )
    }

    @Test
    fun aDetachedPlantCanBeMovedBackIntoATent() {
        // Reachability is not enough on its own: a row you can see but cannot act
        // on is the same defect with a label on it.
        val dao = source("data/dao/AllDaos.kt")
        assertTrue(
            "no way to assign a detached plant back to a tent",
            dao != null && dao.contains("assignPlantToTent")
        )
    }

    @Test
    fun theDeleteDialogStopsPromisingSomethingTheAppCannotDo() {
        val screen = source("ui/screens/tent/TentListScreen.kt")
        assertTrue(
            "the tent delete dialog still promises the plants 'quedarán sin " +
                "asignar', which is the promise that was never kept",
            screen != null && !screen.contains("quedarán sin ")
        )
    }

    @Test
    fun deletingATentWithPlantsIsRefusedRatherThanEmptied() {
        // The guard, asserted structurally: the confirm path re-reads the plants
        // and routes to a refusal instead of deleting.
        val screen = source("ui/screens/tent/TentListScreen.kt")
        assertTrue(
            "the delete must check for occupants before deleting",
            screen != null && screen.contains("tentToClear") &&
                screen.contains("any { it.tentId == tent.id }")
        )
    }

    @Test
    fun theRefusalExplainsWhyAndOffersTheWayOut() {
        val screen = source("ui/screens/tent/TentListScreen.kt")
        assertTrue(
            "the refusal has to say what would happen, or it reads as a broken " +
                "button: emptying the tent is what stranded the plants",
            screen != null && screen.contains("No se puede ") && screen.contains("Mover a ")
        )
    }

    @Test
    fun theDestructiveIconsAreNamed() {
        // An unnamed icon button is announced by TalkBack as just a button, and it
        // gave the automated checks nothing to target -- which is why this delete
        // path stayed untested until the data had already been stranded.
        val screen = source("ui/screens/tent/TentListScreen.kt")
        listOf("Eliminar carpa", "Editar carpa").forEach { label ->
            assertTrue(
                "the tent card has no icon named \"$label\"",
                screen != null && screen.contains("\"$label\"")
            )
        }
    }
}
