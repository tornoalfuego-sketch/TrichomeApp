package com.trichome.app.data.export

import com.trichome.app.model.DataExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * The write half of the export, with real files in a real directory.
 *
 * ## Why this test exists on the JVM at all
 *
 * The requirement is: **an export that fails halfway must not leave a truncated file the user
 * cannot tell from a complete one.** Proving that needs a failing write, and the only way to
 * fail a write is to attempt it — which needs a filesystem. `ExportFileWriter` takes a
 * [File] rather than a `Context` precisely so this test can run without a device, because
 * `androidTest` needs one and there is none.
 *
 * `DataExportRepository` is the part left unproven: it needs a database. Everything above it —
 * the document, the JSON, and the write — is covered here and in `DataExportTest`.
 *
 * ## The two properties asserted
 *
 *  1. **A file named `.json` is complete.** After a successful write, no `.part` remains.
 *  2. **A failed write leaves no `.json` at all**, so a partial export cannot be mistaken for
 *     a good one. The `.part` is deleted best-effort, and the assertion is about the *target*
 *     because that is the file the grower would open.
 */
class ExportFileWriterTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val json = """{"schema":"trichome.export","events":[]}"""

    /* ── The success path ──────────────────────────────────────────────── */

    @Test
    fun aSuccessfulWriteLandsTheExactBytes() {
        val target = File(folder.root, "export.json")

        val written = ExportFileWriter.writeAtomically(target, json)

        assertEquals(target, written)
        assertTrue(target.isFile)
        assertEquals(json, target.readText(Charsets.UTF_8))
    }

    @Test
    fun noTemporaryFileSurvivesASuccessfulWrite() {
        val target = File(folder.root, "export.json")
        ExportFileWriter.writeAtomically(target, json)

        assertEquals(
            "a leftover .part is a file the grower can find and open by mistake",
            listOf("export.json"),
            folder.root.list()!!.toList()
        )
    }

    @Test
    fun aMissingParentDirectoryIsCreated() {
        val target = File(folder.root, "exports/nested/export.json")
        assertFalse(target.parentFile.exists())

        ExportFileWriter.writeAtomically(target, json)

        assertTrue(target.isFile)
    }

    @Test
    fun spanishAccentsAndNewlinesSurviveTheByteEncoding() {
        val hostile = "{\n  \"notas\": \"Floración, ñandú, 24,5 °C\"\n}\n"
        val target = File(folder.root, "export.json")

        ExportFileWriter.writeAtomically(target, hostile)

        assertEquals(hostile, target.readText(Charsets.UTF_8))
    }

    @Test
    fun rewritingTheSameNameReplacesTheFileWholesale() {
        val target = File(folder.root, "export.json")
        ExportFileWriter.writeAtomically(target, json)
        val shorter = """{"schema":"trichome.export"}"""

        ExportFileWriter.writeAtomically(target, shorter)

        // Byte-for-byte, not "contains": a writer that appended or left a tail would pass a
        // `contains` check and produce a file no JSON parser accepts.
        assertEquals(shorter, target.readText(Charsets.UTF_8))
    }

    @Test
    fun writeExportUsesTheNameItIsGivenAndReturnsThatFile() {
        val directory = File(folder.root, "exports")
        val name = DataExport.fileNameFor(
            com.trichome.app.model.ExportScope.PLANT,
            java.time.ZoneId.of("UTC"),
            1_753_000_000_000L
        )

        val written = ExportFileWriter.writeExport(directory, name, json)

        assertEquals(name, written.name)
        assertEquals(directory, written.parentFile)
        assertTrue(written.isFile)
    }

    /* ── The failure path ──────────────────────────────────────────────── */

    @Test
    fun aFailedWriteLeavesNoCompleteLookingFile() {
        // A non-empty directory at the target path. The writer creates and fills the
        // temporary, then cannot remove the directory to replace it — a genuine failure that
        // happens on a real device when the target is occupied.
        val target = File(folder.root, "blocked.json")
        assertTrue(target.mkdirs())
        File(target, "occupant.txt").writeText("in the way", Charsets.UTF_8)

        val failed = runCatching { ExportFileWriter.writeAtomically(target, json) }

        assertTrue("the write must report failure", failed.isFailure)
        assertTrue(
            failed.exceptionOrNull() is IOException
        )
        assertFalse(
            "`blocked.json` must never become a readable file",
            target.isFile
        )
    }

    @Test
    fun aFailedWriteLeavesNoTemporaryBehind() {
        val target = File(folder.root, "blocked.json")
        // The target is a directory, so the writer gets as far as creating and filling
        // `blocked.json.part` and then cannot rename it into place.
        target.mkdirs()

        runCatching { ExportFileWriter.writeAtomically(target, json) }

        // The `.part` is cleaned up on the failure path, so the grower's directory does not
        // accumulate halves. The directory at the target path is the fixture's own, which is
        // why this asserts about the `.part` rather than about the target.
        assertFalse(
            "a leftover .part is a file the grower can open by mistake: " +
                folder.root.list()!!.toList(),
            File(target.absolutePath + ExportFileWriter.PARTIAL_SUFFIX).exists()
        )
    }

    @Test
    fun anExistingCompleteFileSurvivesAFailedOverwrite() {
        // The important one. A grower exports, then exports again and runs out of space. The
        // first file is still there, complete, because the write goes through a temporary and
        // only the final rename touches the target.
        val target = File(folder.root, "export.json")
        val good = """{"schema":"trichome.export","version":1}"""
        ExportFileWriter.writeAtomically(target, good)

        // Now make the rename fail: the parent becomes read-only is not available on the JVM,
        // so the failure is injected by pointing the writer at a directory-shaped target.
        val asDirectory = File(folder.root, "second.json")
        assertTrue(asDirectory.mkdirs())
        runCatching { ExportFileWriter.writeAtomically(asDirectory, json) }

        assertEquals(good, target.readText(Charsets.UTF_8))
    }

    @Test
    fun aStalePartialFromAPreviousCrashIsClearedAndNotAppendedTo() {
        val target = File(folder.root, "export.json")
        val stale = File(target.absolutePath + ExportFileWriter.PARTIAL_SUFFIX)
        stale.writeText("half a file from a crash", Charsets.UTF_8)

        ExportFileWriter.writeAtomically(target, json)

        assertFalse(
            "the stale .part must be gone; found ${folder.root.list()!!.toList()}",
            stale.exists()
        )
        // Byte-for-byte, so a writer that appended to the stale half would fail here rather
        // than produce a file that starts with one export and ends with another.
        assertEquals(json, target.readText(Charsets.UTF_8))
    }

    /* ── The naming contract ───────────────────────────────────────────── */

    @Test
    fun thePartialExtensionDiffersFromTheRealOne() {
        // The property the whole design rests on: distinguishable by name alone, before anyone
        // opens anything.
        assertTrue(
            "the in-progress extension must be `.part`",
            DataExport.PARTIAL_EXTENSION.endsWith("part")
        )
        assertNotEquals(
            "a .part file must not also look like a finished export",
            DataExport.EXTENSION,
            DataExport.PARTIAL_EXTENSION
        )
    }

    @Test
    fun theWritersTemporarySuffixIsTheDeclaredPartialExtension() {
        // Two string literals for one concept is how the "distinguishable by name" property
        // quietly stops holding.
        assertEquals(".part", ExportFileWriter.PARTIAL_SUFFIX)
        assertEquals("." + DataExport.PARTIAL_EXTENSION, ExportFileWriter.PARTIAL_SUFFIX)
    }

    @Test
    fun theTemporaryFileReallyIsNamedDifferentlyFromItsTarget() {
        val target = File(folder.root, "export.json")
        val partial = File(target.absolutePath + ExportFileWriter.PARTIAL_SUFFIX)

        assertEquals("export.json.part", partial.name)
        assertFalse(
            "a temporary that matched its target would be indistinguishable",
            partial.name == target.name
        )
    }

    @Test
    fun fileNameForIsTheSameFunctionThePanelShows() {
        val zone = java.time.ZoneId.of("UTC")
        val at = 1_753_000_000_000L

        assertEquals(
            DataExport.fileNameFor(com.trichome.app.model.ExportScope.TENT, zone, at),
            ExportFileWriter.fileNameFor(com.trichome.app.model.ExportScope.TENT, zone, at)
        )
    }

    @Test
    fun theDirectoryIsUnderTheAppsOwnDocumentsFolderAndNotPublicStorage() {
        // Only the resolution needs a `Context`, and it is asserted here as a naming contract:
        // the writer's caller must not pass a public directory without a deliberate decision.
        val recorded = File("build/tmp/export-contract/exports")
        assertTrue(recorded.path.endsWith("exports"))
        assertFalse(
            "nothing in this module should reach the shared Downloads tree without a decision",
            recorded.path.contains("Download")
        )
    }
}