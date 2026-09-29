package com.trichome.app.ui.screens.settings

import com.trichome.app.BuildConfig
import com.trichome.app.data.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the "Acerca de" card: it must report the build and the schema
 * it actually shipped with, never a literal that goes stale on release.
 */
class AboutInfoTest {

    @Test
    fun theVersionRowShowsTheBuildVersionName() {
        assertEquals(
            "the version must come from BuildConfig, not from a literal",
            BuildConfig.VERSION_NAME,
            aboutInfo(BuildConfig.VERSION_NAME, AppDatabase.VERSION).version
        )
    }

    @Test
    fun theDatabaseRowShowsTheRoomSchemaVersion() {
        assertEquals(
            "Room v${AppDatabase.VERSION}",
            aboutInfo(BuildConfig.VERSION_NAME, AppDatabase.VERSION).database
        )
    }

    @Test
    fun theVersionRowIsNotAFrozenLiteral() {
        // Every value must render: if the row still held "1.0.1" this fails.
        assertEquals("9.9.9", aboutInfo("9.9.9", AppDatabase.VERSION).version)
        assertEquals("Room v7", aboutDatabaseVersion(7))
    }

    @Test
    fun theSchemaConstantMatchesTheSchemaRoomActuallyExported() {
        // Room writes one JSON per version into app/schemas. The constant the
        // Settings screen prints has to be the version that was really built.
        val exported = exportedSchemaVersions()
        assertTrue(
            "no exported Room schema found; the guard below would be vacuous",
            exported.isNotEmpty()
        )
        assertEquals(
            "AppDatabase.VERSION drifted from the schema Room exported",
            exported.max(),
            AppDatabase.VERSION
        )
    }

    @Test
    fun theStorageRowStillDescribesTheOfflinePromise() {
        assertEquals(
            "100 % locales y sin conexión",
            aboutInfo(BuildConfig.VERSION_NAME, AppDatabase.VERSION).storage
        )
    }

    @Test
    fun theVersionIsNotEmpty() {
        assertNotEquals("", aboutInfo(BuildConfig.VERSION_NAME, AppDatabase.VERSION).version)
    }

    private fun exportedSchemaVersions(): List<Int> {
        val root = java.io.File("schemas/com.trichome.app.data.database.AppDatabase")
            .takeIf { it.isDirectory }
            ?: java.io.File("app/schemas/com.trichome.app.data.database.AppDatabase")
        return root.listFiles()
            .orEmpty()
            .mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }
    }
}
