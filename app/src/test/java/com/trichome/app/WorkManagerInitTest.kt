package com.trichome.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression guard for the launch crash:
 * `java.lang.IllegalStateException: WorkManager is already initialized.`
 *
 * `androidx.startup.InitializationProvider` is a ContentProvider, so it runs
 * **before** `Application.onCreate()`. It declares the default
 * `androidx.work.WorkManagerInitializer`, therefore WorkManager is already up by
 * the time `TrichomeApp.onCreate()` executes. Calling
 * `WorkManager.initialize()` from there throws and kills the process before any
 * activity is drawn — the app simply never appeared to start.
 *
 * The supported fix is WorkManager's on-demand initialization:
 * 1. `TrichomeApp` implements `Configuration.Provider` (custom WorkerFactory).
 * 2. The `WorkManagerInitializer` meta-data is removed from the manifest.
 * 3. `WorkManager.initialize()` is never called manually.
 *
 * These assertions read the sources because that contract lives in code, not in
 * a runtime value we can observe from a plain JVM test.
 */
class WorkManagerInitTest {

    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val application = stripComments(
        File("src/main/java/com/trichome/app/TrichomeApp.kt").readText()
    )

    /** KDoc mentions the offending call, so assertions must run on code only. */
    private fun stripComments(source: String): String = source
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

    @Test
    fun `default WorkManagerInitializer is removed from the manifest`() {
        assertTrue(
            "InitializationProvider must be declared with tools:node=\"merge\"",
            manifest.contains("androidx.startup.InitializationProvider")
        )
        assertTrue(
            "WorkManagerInitializer must be removed with tools:node=\"remove\"",
            Regex(
                """androidx\.work\.WorkManagerInitializer[\s\S]{0,200}?tools:node="remove""""
            ).containsMatchIn(manifest)
        )
    }

    @Test
    fun `WorkManager is never initialized manually`() {
        assertFalse(
            "Calling WorkManager.initialize() in Application.onCreate() throws " +
                "\"WorkManager is already initialized\" — use Configuration.Provider instead",
            application.contains("WorkManager.initialize")
        )
    }

    @Test
    fun `application provides a custom WorkManager configuration`() {
        assertTrue(
            "TrichomeApp must implement Configuration.Provider",
            application.contains("Configuration.Provider")
        )
        assertTrue(
            "The custom configuration must inject TrichomeWorkerFactory",
            application.contains("TrichomeWorkerFactory")
        )
    }
}
