package com.trichome.app.ui.screens.settings

/**
 * The "Acerca de" rows.
 *
 * Every value is derived from the build or from the database class, never from a
 * literal: a release bump or a schema migration must not require editing this
 * screen, and the two facts it reports have to match what actually shipped.
 */
data class AboutInfo(
    /** `BuildConfig.VERSION_NAME`. */
    val version: String,
    val storage: String,
    /** `Room v<AppDatabase.VERSION>`. */
    val database: String
)

/** Room schema version as rendered next to the "Base de datos" label. */
fun aboutDatabaseVersion(schemaVersion: Int): String = "Room v$schemaVersion"

/**
 * @param versionName `BuildConfig.VERSION_NAME`.
 * @param databaseVersion `AppDatabase.VERSION`, the version `@Database` was built with.
 */
fun aboutInfo(versionName: String, databaseVersion: Int): AboutInfo = AboutInfo(
    version = versionName,
    storage = "100 % locales y sin conexión",
    database = aboutDatabaseVersion(databaseVersion)
)
