package com.trichome.app.data.export

import com.trichome.app.model.DataExport
import java.io.File
import java.io.IOException

/**
 * Writes an export file so that a failure cannot leave a truncated one.
 *
 * ## The rule
 *
 * **A file named `.json` is complete. Always.** The grower cannot tell a half-written
 * export from a whole one by opening it — a JSON viewer reports a syntax error at the
 * truncation point and offers nothing else — and a partial export that looks complete
 * is worse than no export, because the grower's next move is to delete the app's data
 * assuming the backup is good.
 *
 * So the bytes go to `<name>.json.part`, are flushed and fsynced, and only then replace
 * `<name>.json` through a rename. A crash, a full disk or an exception leaves the
 * `.part` behind and never touches the target. The `.part` is deleted on the failure
 * path, best effort; if even that fails, the name still differs, which is the whole
 * point of naming it differently.
 *
 * ## Why this is a plain object over a `File`, not a `Context`
 *
 * It takes a [File] and nothing else, so the entire write path — including the failure
 * path — runs on the JVM with a real temporary directory. `ExportFileWriterTest` creates
 * a directory that cannot be written to, triggers the failure and asserts no `.json`
 * exists afterwards. A writer that needed an Android `Context` would have that test in
 * `androidTest`, which needs a device, and there is no device.
 *
 * ## Where the file goes
 *
 * `exportDirectory` resolves the app-scoped external documents directory, which needs no
 * permission on any API level from 26 to 36 and is the one destination this project can
 * be confident about without hardware. `MediaStore.Downloads` would put the file where a
 * grower expects it and needs no permission either, but it is untestable here and a
 * mistake in it fails silently as "no file appeared"; that is the owner's call, not
 * this phase's.
 */
object ExportFileWriter {

    /** Where exports go, or null when the device has no external storage at all. */
    fun exportDirectory(context: android.content.Context): File? {
        val base = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
            ?: return null
        return File(base, "exports")
    }

    /**
     * Writes [contents] to [target] atomically.
     *
     * @return the target file, on success.
     * @throws IOException when the directory could not be created, the temporary file
     *   could not be written, or the rename failed. Nothing is left at [target] in any
     *   of those cases.
     */
    @Throws(IOException::class)
    fun writeAtomically(target: File, contents: String): File {
        val directory = target.parentFile
        if (directory != null && !directory.isDirectory && !directory.mkdirs()) {
            throw IOException("no se pudo crear la carpeta ${directory.name}")
        }

        val partial = File(target.absolutePath + PARTIAL_SUFFIX)
        // A `.part` from a previous crash is never merged into this write and never read:
        // clearing it first means a directory cannot accumulate stale halves.
        if (partial.exists() && !partial.delete()) {
            throw IOException("no se pudo limpiar un archivo a medias anterior")
        }

        try {
            partial.outputStream().use { stream ->
                stream.write(contents.toByteArray(Charsets.UTF_8))
                stream.flush()
                // `flush()` only pushes the bytes to the OS. Without the sync they can sit in
                // the page cache when the process dies, and the rename then publishes a file
                // whose contents were never written.
                stream.fd.sync()
            }
            // The old file has to go before the rename on some filesystems, where `renameTo`
            // refuses to overwrite. Deleting it here is safe precisely because the new content
            // is already complete in `.part`: the window this opens is "no file at all",
            // never "half a file", which is the property that matters.
            if (target.exists() && !target.delete()) {
                throw IOException("no se pudo reemplazar el archivo anterior")
            }
            if (!partial.renameTo(target)) {
                throw IOException("no se pudo renombrar el archivo temporal")
            }
        } catch (e: IOException) {
            // Best effort: the target never received partial content, and the `.part` name
            // means a leftover is not mistakable for a finished export.
            partial.delete()
            throw e
        }

        return target
    }

    /**
     * Suffix appended to a target's path to form the temporary file's path.
     *
     * `export.json` becomes `export.json.part`, so the completed file keeps its own extension
     * and the half-written one is identifiable from the name alone.
     *
     * Derived from [DataExport.PARTIAL_EXTENSION] rather than written out, because the two have
     * to be the same string: a `.part` whose extension does not say it is incomplete defeats
     * the entire design. Asserted against it in
     * `ExportFileWriterTest.theWritersTemporarySuffixIsTheDeclaredPartialExtension`.
     */
    const val PARTIAL_SUFFIX: String = "." + DataExport.PARTIAL_EXTENSION

    /**
     * Writes a rendered export for [contents] under the name [DataExport.fileNameFor]
     * produced.
     *
     * @return the written file.
     * @throws IOException when the export directory does not exist, or the write failed.
     */
    @Throws(IOException::class)
    fun writeExport(directory: File, fileName: String, contents: String): File {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("no se pudo crear la carpeta ${directory.name}")
        }
        return writeAtomically(File(directory, fileName), contents)
    }

    /**
     * The file name an export would use, built from the same pieces as [writeExport].
     *
     * Public so the screen can show the name it is about to write without writing it
     * first — a grower should know where the file lands before they press the button.
     */
    fun fileNameFor(
        scope: com.trichome.app.model.ExportScope,
        zone: java.time.ZoneId,
        atMillis: Long
    ): String = DataExport.fileNameFor(scope, zone, atMillis)
}