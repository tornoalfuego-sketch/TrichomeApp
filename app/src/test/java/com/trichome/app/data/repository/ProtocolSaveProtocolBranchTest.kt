package com.trichome.app.data.repository

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `MainViewModel.saveProtocol` must branch on `id`, because `insertProtocol` cascades.
 *
 * ## Why this reads source rather than calling the function
 *
 * The defect this guards was **not** in the repository. `ProtocolRepository` offers both
 * `insertProtocol` (REPLACE) and `updateProtocol` (`@Update`) and behaved correctly; the
 * bug was that `saveProtocol` reached for the REPLACE unconditionally. That branch lives
 * inside a ViewModel whose constructor takes an `AppContainer`, so a JVM test cannot
 * build one — there is no Robolectric and no `compose-ui-test` on this classpath.
 *
 * A first attempt at this guard asserted the repository's behaviour instead. It passed
 * **with the bug reintroduced**, which is worse than no test at all: it read as coverage
 * of the defect while covering nothing. It is kept in `ProtocolWriteDoesNotCascadeTest`
 * for what it actually proves, and this test covers the branch itself.
 *
 * ## The defect, and how it was found
 *
 * `ProtocolDao.insertProtocol` is `@Insert(onConflict = REPLACE)`. SQLite implements
 * REPLACE as DELETE then INSERT, and `protocol_stages.protocolId` carries
 * `onDelete = CASCADE` against `protocols.id`. So saving any protocol that already
 * existed deleted its row and every stage of it.
 *
 * On a phone: creating a protocol wrote `protocol_stages` ids 7, 8 and 9; saving one
 * grow-wide target cascaded them away; `sqlite_sequence.protocol_stages` read `9` with
 * three rows left, while the protocol card still drew a three-stage schedule that no
 * longer existed. `ProtocolEditorDialog` had masked it by rebuilding the schedule
 * immediately afterwards; F13's target surfaces write the protocol row alone.
 *
 * Comments are stripped before every scan, so this file's own prose cannot register as a
 * hit. That rule has broken twice in this repo and is applied deliberately.
 */
class ProtocolSaveProtocolBranchTest {

    private val viewModel = sourceFile("viewmodel/MainViewModels.kt")

    @Test
    fun `saveProtocol branches on the row id instead of always inserting`() {
        val body = viewModel.substringAfter("suspend fun saveProtocol(protocol: Protocol): Long =")
            .substringBefore("\n    /**")

        assertTrue(
            "could not find `saveProtocol` in MainViewModels.kt; this test is guarding nothing",
            body.isNotBlank()
        )
        assertTrue(
            "`saveProtocol` must distinguish a new protocol from an existing one: " +
                "the insert path is a REPLACE and it cascades into protocol_stages.",
            Regex("if\\s*\\(\\s*protocol\\.id\\s*==\\s*0L\\s*\\)").containsMatchIn(body)
        )
    }

    @Test
    fun `an existing protocol is updated and never inserted`() {
        val body = viewModel.substringAfter("suspend fun saveProtocol(protocol: Protocol): Long =")
            .substringBefore("\n    /**")

        assertTrue(
            "the existing-row branch must call `updateProtocol`",
            Regex("updateProtocol\\s*\\(").containsMatchIn(body)
        )
        assertTrue(
            "the existing-row branch must return the id it already has",
            Regex("protocolRepo\\.updateProtocol\\(protocol\\)[\\s\\S]*?protocol\\.id").containsMatchIn(body)
        )
    }

    @Test
    fun `no caller writes an existing protocol through the REPLACE insert`() {
        // The two write paths, and the one that must stay on insert.
        val screen = sourceFile("ui/screens/protocol/ProtocolScreen.kt")

        assertTrue(
            "`ProtocolTargetsScreen` saves the protocol row on its own. It must reach " +
                "`saveProtocol` and not `insertProtocol`, or every grow-wide target wipes " +
                "the protocol's stages.",
            !Regex("insertProtocol\\s*\\(").containsMatchIn(screen)
        )
    }

    private fun sourceFile(relativePath: String): String {
        val candidates = listOf(
            "src/main/java/com/trichome/app/$relativePath",
            "../app/src/main/java/com/trichome/app/$relativePath",
            "app/src/main/java/com/trichome/app/$relativePath"
        )
        val file = candidates.map(::File).firstOrNull { it.isFile }
            ?: error(
                "could not locate $relativePath. Tried:\n" + candidates.joinToString("\n") { "  $it" }
            )
        return stripComments(file.readText())
    }

    private fun stripComments(source: String): String =
        source
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("//[^\\n]*"), " ")
}
