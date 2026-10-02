package com.trichome.app.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The shipped text is user-facing Spanish, and this guards the three ways it rots.
 *
 * ## Why these three and not a spell-checker
 *
 * A spell-checker needs a Spanish dictionary and would flag correct technical
 * vocabulary — `cannabinoide`, `terpeno`, `monoterpeno` and `fotoperiodo` are all
 * correct Spanish that no general dictionary knows. So this does not judge
 * spelling. It pins the three defects that actually shipped:
 *
 * 1. **Mojibake in the data file.** `terpenes.json` carried `"JazmÃ­n"` — the
 *    UTF-8 bytes of `i` with acute read as latin-1, then a SOFT HYPHEN wedged
 *    into the middle of the word. Two compounds, invisible in a code review.
 *    The same line also read `seÃ±alizacion`, where the enye had collapsed into
 *    `Ã` plus `±`.
 * 2. **A dropped letter in a mechanism string**: `glutamatrgica`, missing the
 *    `o`. Nothing detects a missing letter in a long Spanish sentence.
 * 3. **A typo in a composable's string literal**: the Entourage entry card read
 *    `y legalization` where it should read `cannabinoides`. Found on a device,
 *    not in a diff.
 *
 * ## Why a source scan rather than a runtime assertion
 *
 * The damaged strings live inside a static asset and inside string literals, so
 * the only way to see them is to read the bytes. A runtime assertion would have
 * to already know the bad text in order to look for it, which is the tautology
 * this test exists to avoid.
 *
 * ## The one place this cannot reach
 *
 * Text that is neither in a JSON asset nor in a `.kt` literal — a system
 * provided string, a web view — is not covered, and no test here claims to be.
 */
class ShippedTextIntegrityTest {

    private fun firstExisting(candidates: List<File>): File =
        candidates.firstOrNull { it.isDirectory }
            ?: throw AssertionError("none of these exist: ${candidates.map { it.absolutePath }}")

    private fun assetFiles(): List<File> =
        firstExisting(
            listOf(File("src/main/assets/data"), File("app/src/main/assets/data"))
        ).listFiles { f -> f.isFile && f.extension == "json" }
            ?.sortedBy { it.name }
            .orEmpty()

    private fun sourceFiles(): List<File> =
        firstExisting(
            listOf(
                File("src/main/java/com/trichome/app"),
                File("app/src/main/java/com/trichome/app")
            )
        ).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .toList()

    /**
     * The byte sequences that mean "this file was written through the wrong
     * encoding at some point".
     *
     * `A` with tilde followed by the latin-1 reading of a two-byte UTF-8
     * character, and the capitalised `A` with circumflex of a degree or
     * separator sign. Neither is possible in correctly encoded Spanish text.
     */
    private val mojibakeSequences = listOf(
        "Ã¡", "Ã©", "Ã­", "Ã³", "Ãº",
        "Ã±", "Ã¼", "Ã", "Â°", "Â·", "Â¡"
    )

    @Test
    fun theAssetFilesCarryNoMojibake() {
        val offenders = mutableListOf<String>()
        assetFiles().forEach { file ->
            val text = file.readText(Charsets.UTF_8)
            mojibakeSequences.forEach { seq ->
                var index = text.indexOf(seq)
                while (index >= 0) {
                    val line = text.take(index).count { it == '\n' } + 1
                    val context = text
                        .substring(maxOf(0, index - 30), minOf(text.length, index + 20))
                        .filter { it == ' ' || (it.code in 0x21..0x7E) || it.code > 0xA0 }
                    offenders += "${file.name}:$line holds '$seq' near \"$context\""
                    index = text.indexOf(seq, index + seq.length)
                }
            }
        }
        assertTrue(
            "these shipped strings were written through the wrong encoding and render " +
                "as broken text on screen:\n  ${offenders.joinToString("\n  ")}",
            offenders.isEmpty()
        )
    }

    @Test
    fun noAssetFileCarriesASoftHyphenInsideAWord() {
        // A SOFT HYPHEN is invisible. Inside a Spanish word it is always an
        // accident and never a hyphenation request: this project wraps text at
        // spaces, it does not insert soft hyphens.
        val offenders = assetFiles().mapNotNull { file ->
            val text = file.readText(Charsets.UTF_8)
            val at = text.indexOf('­')
            if (at < 0) return@mapNotNull null
            val line = text.take(at).count { it == '\n' } + 1
            "${file.name}:$line has a soft hyphen in \"${text.split('\n')[line - 1].trim()}\""
        }
        assertTrue(
            "a soft hyphen is invisible on screen and splits the word it sits in:\n" +
                "  ${offenders.joinToString("\n  ")}",
            offenders.isEmpty()
        )
    }

    @Test
    fun noShippedFileCarriesTheReplacementCharacter() {
        // U+FFFD means a decode already failed and the original byte is lost. It
        // cannot be recovered, so it must never reach a commit.
        val offenders = assetFiles()
            .filter { it.readText(Charsets.UTF_8).contains('�') }
            .map { it.name }
        assertTrue(
            "these assets carry U+FFFD, which means a decode failed and the original " +
                "byte is gone: $offenders",
            offenders.isEmpty()
        )
    }

    @Test
    fun everyAssetFileIsValidJsonAndKeepsItsTopLevelShape() {
        // A truncated or damaged write must fail here rather than render as an
        // empty screen. The shapes are pinned so a refactor that renames a
        // top-level key is caught rather than discovered as an empty catalog.
        val shapes = mapOf(
            "terpenes.json" to listOf("version", "terpenes"),
            "entourage_data.json" to listOf(
                "version", "disclaimer_es", "synergies", "profiles",
                "vaporisation", "cases", "quiz", "agronomy", "processing"
            ),
            "breeding.json" to null,
            "diagnostics.json" to null
        )
        assetFiles().forEach { file ->
            val text = file.readText(Charsets.UTF_8)
            val parsed = runCatching { Json.parseToJsonElement(text) }.getOrNull()
            assertTrue("${file.name} is not valid JSON", parsed != null)

            val expected = shapes[file.name]
            if (expected != null) {
                val keys = (parsed as JsonObject).keys.toList()
                assertEquals("${file.name} top-level keys changed", expected, keys)
            }
        }
    }

    @Test
    fun theSpanishCompoundsThatShippedDamagedAreNowWholeWords() {
        // The exact strings that shipped damaged, asserted repaired. If someone
        // reintroduces the mojibake this test names the word instead of leaving
        // the reader to spot a stray U+00C3 in a diff.
        val terpenes = assetFiles().first { it.name == "terpenes.json" }.readText(Charsets.UTF_8)

        assertTrue(
            "Jazmin must appear intact; it shipped as \"JazmÃ­n\" with a soft hyphen " +
                "wedged into the middle of the word",
            terpenes.contains("Jazmín")
        )
        assertFalse(
            "the broken Jazmin form is back",
            terpenes.contains("JazmÃ") || terpenes.contains("Ã­n")
        )
        assertTrue(
            "the mechanism string must read 'señalización glutamatégica'; it shipped " +
                "as 'seÃ±alizacion glutamatrgica'",
            terpenes.contains("señalización glutamatégica")
        )
        assertFalse(
            "the dropped letter came back",
            terpenes.contains("glutamatrgica")
        )
    }

    @Test
    fun theEntourageEntryCardReadsCannabinoides() {
        // Found on the device, not in a diff. The correct word is spelled out in
        // its own characters so this assertion cannot itself inherit the typo.
        val correct = "cannabinoides"
        val screen = firstExisting(
            listOf(
                File("src/main/java/com/trichome/app/ui/screens/terpenes"),
                File("app/src/main/java/com/trichome/app/ui/screens/terpenes")
            )
        ).resolve("TerpenesScreen.kt")

        assertTrue("${screen.absolutePath} not found", screen.isFile)
        val text = screen.readText(Charsets.UTF_8)

        assertTrue(
            "the Entourage entry card must name the compounds it links ($correct)",
            text.contains(correct)
        )
        assertFalse(
            "the typo form is back in the Entourage entry card",
            text.contains("terpenos y " + "y" + "abinoides")
        )
    }

    @Test
    fun theTerpeneCatalogKeepsItsCompoundCount() {
        // A damaged write that still parses as JSON can quietly drop entries.
        // 158 is the shipped count, and the breadth of the Bible is its point.
        val terpenes = Json.parseToJsonElement(
            assetFiles().first { it.name == "terpenes.json" }.readText(Charsets.UTF_8)
        ) as JsonObject
        assertEquals("the terpene catalog changed size", 158, (terpenes["terpenes"] as JsonArray).size)
    }
}