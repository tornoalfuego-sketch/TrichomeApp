package com.trichome.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The tint rule for the 184 diagnosis glyphs.
 *
 * ## Why this test exists, and what it actually caught
 *
 * F11 authored 184 distinct figures as `ImageVector`s, in code, with no new dependency,
 * and proved them distinct from the JVM. Every one of those proofs passed. The figures
 * were still, on a real phone, **invisible**: authored with `SolidColor(Color.Black)`
 * and rendered through `Icon` with `tint = Color.Unspecified`, which is not "no colour"
 * — it is "install no `ColorFilter`". So the vector drew its own black.
 *
 * Measured on the dark palette, inside a `FilterChip`:
 *
 * | what | colour | contrast on `#0D111C` |
 * |---|---|---|
 * | chip label | `#FFC107` | **11.56:1** |
 * | glyph stroke | `#000000` | **1.11:1** |
 *
 * WCAG 1.4.11 asks 3:1 for a non-text graphic. The figures were drawn correctly and
 * then made unreadable by a one-token default argument, and nothing in the suite could
 * see it: `DiagnosisGlyphsTest` proves the *catalog* is distinct, and no test on this
 * classpath can measure what Compose resolves a `ColorFilter` to.
 *
 * This test closes that gap at the only level a JVM test can reach — the source of the
 * composable — and it closes it in the direction that matters: not "the caller passes a
 * tint today", which is a fact about one call site, but **"the untinted state is
 * unrepresentable"**, which is a property of the signature.
 *
 * ## Why it reads source
 *
 * Compose has no unit-test runtime in this project — neither Robolectric nor
 * `compose-ui-test` is on the `test` classpath. A composable's body is unreachable from
 * a JVM test. Comments are stripped before every scan, so this file's own prose about
 * `Color.Unspecified` cannot register as a hit; that rule has broken twice in this repo.
 */
class DiagnosisGlyphTintTest {

    private val glyphs = sourceFile("ui/components/DiagnosisGlyphs.kt")
    private val screen = sourceFile("ui/screens/diagnosis/DiagnosisScreen.kt")

    @Test
    fun `the composable default is a resolved colour and not Unspecified`() {
        val body = glyphs.substringAfter("fun DiagnosisGlyphIcon(")

        assertTrue(
            "DiagnosisGlyphIcon must still exist; this test found nothing to guard",
            body.isNotEmpty()
        )
        assertTrue(
            "`tint: Color = Color.Unspecified` reinstates the bug this test was written " +
                "for. Compose applies no ColorFilter for Unspecified, so the vector draws " +
                "its own black. The default has to be `LocalContentColor.current`.",
            !body.contains("tint: Color = Color.Unspecified")
        )
        assertTrue(
            "the default tint should resolve from the theme, not be left to the caller",
            Regex("tint:\\s*Color\\s*=\\s*LocalContentColor\\.current").containsMatchIn(body)
        )
    }

    @Test
    fun `no call site leaves the glyph untinted`() {
        // Belt and braces. Even with a safe default, an explicit `Color.Unspecified`
        // at a call site would reopen the hole, so the call sites are checked too.
        assertFalse(
            "a DiagnosisGlyphIcon call site passes Color.Unspecified, which disables " +
                "the tint and renders the glyph black",
            Regex("DiagnosisGlyphIcon\\s*\\((?:(?!\\)).)*Color\\.Unspecified", RegexOption.DOT_MATCHES_ALL)
                .containsMatchIn(screen)
        )
    }

    @Test
    fun `the vector stroke stays authored in black`() {
        // The stored colour is correct and should stay: a vector authored in black is
        // what makes it tintable at all. This test exists so the fix above is never
        // "solved" later by hardcoding a light stroke into the geometry, which would
        // make the figure unreadable on the light palette instead of the dark one.
        assertTrue(
            "the glyph stroke should be authored black so the tint can recolour it",
            glyphs.contains("stroke = SolidColor(Color.Black)")
        )
    }

    @Test
    fun `LocalContentColor is imported`() {
        assertTrue(
            "LocalContentColor must be imported for the default tint to compile",
            Regex("^import\\s+androidx\\.compose\\.material3\\.LocalContentColor\\s*$", RegexOption.MULTILINE)
                .containsMatchIn(glyphs)
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
                "could not locate $relativePath. Tried:\n" +
                    candidates.joinToString("\n") { "  $it" }
            )
        return stripComments(file.readText())
    }

    /** Comments cannot register as hits; this rule has broken twice in this repo. */
    private fun stripComments(source: String): String =
        source
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("//[^\\n]*"), " ")
}
