package com.trichome.app.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the `model/` package free of Android.
 *
 * The engines in this package are the reason a photoperiod dialog, a lunar badge
 * and a VPD readout can be decided — and tested — without a device. That property
 * is not visible in the compiler output: adding `import android.text.format.DateFormat`
 * to an engine compiles perfectly and only shows up as a unit test that cannot run
 * on the JVM, or worse, as a `RuntimeException("Stub!")` the moment it is called.
 *
 * So it is asserted here, against the source, with the comments stripped first —
 * otherwise this file's own prose about Android imports would match itself.
 */
class ModelPurityTest {

    private val files = listOf(
        "LunarEngine.kt",
        "AmbientClimate.kt",
        "PlantMigrationPlan.kt"
    )

    private fun codeOf(name: String): String {
        val candidates = listOf(
            File("src/main/java/com/trichome/app/model/$name"),
            File("app/src/main/java/com/trichome/app/model/$name")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("$name is not in the model package")
        return file.readText(Charsets.UTF_8)
            // Strip /* */ first, then //, so a comment mentioning an import cannot
            // be counted as one.
            .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
            .replace(Regex("""//[^\n]*"""), " ")
    }

    @Test
    fun noEngineImportsAndroid() {
        files.forEach { name ->
            val offenders = Regex("""^\s*import\s+(android|androidx)\S*""", RegexOption.MULTILINE)
                .findAll(codeOf(name))
                .map { it.value.trim() }
                .toList()
            assertTrue(
                "$name must stay pure Kotlin: it imports $offenders",
                offenders.isEmpty()
            )
        }
    }

    @Test
    fun noEngineReadsTheWallClockOnItsOwn() {
        // `now` is injected everywhere on purpose; a default argument reading the
        // clock would make every decision non-deterministic and untestable.
        val offenders = mutableListOf<String>()
        files.forEach { name ->
            val code = codeOf(name)
            if (code.contains("System.currentTimeMillis()") ||
                code.contains("System.nanoTime()") ||
                code.contains("LocalDate.now()") ||
                code.contains("ZoneId.systemDefault()") ||
                code.contains("TimeZone.getDefault()")
            ) {
                offenders += name
            }
        }
        assertTrue(
            "these engines must take the instant as a parameter: $offenders",
            offenders.isEmpty()
        )
    }

    @Test
    fun noEngineDoesAnyIO() {
        files.forEach { name ->
            val code = codeOf(name)
            listOf(
                """java\.net\.""",
                """java\.io\.""",
                """okhttp""",
                """Retrofit""",
                """SharedPreferences""",
                """android\.location""",
                """FusedLocationProvider""",
                """Geocoder"""
            ).forEach { forbidden ->
                assertFalse(
                    "$name reaches for $forbidden, and this app is offline by design",
                    Regex(forbidden).containsMatchIn(code)
                )
            }
        }
    }
}