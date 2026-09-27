package com.trichome.app

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trichome.app.data.database.AppDatabase
import com.trichome.app.data.database.MIGRATION_1_2
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration v1 -> v2 guaranteed by MIGRATION_1_2 with explicit schema:
 * the v1 baseline is created with raw SQL (mirrors the annotated v1 layout),
 * then Room opens the same file at v2 and validates all tables.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private lateinit var context: Context
    private val dbName = "migration_test.db"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate1To2PreservesDataAndCreatesNewSchema() {
        // 1) Create a real version-1 database.
        createVersion1Database()

        // 2) Open the same file with Room + explicit migration.
        val room: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2)
            .build()
        val db = room.openHelper.writableDatabase // triggers onUpgrade

        // 3) Version was bumped.
        assertEquals(2, db.version)

        // 4) Columns added by the migration.
        assertTrue(columnExists(db, "protocols", "lightHours"))
        assertTrue(columnExists(db, "protocols", "darkHours"))
        assertTrue(columnExists(db, "protocols", "presetType"))
        assertTrue(columnExists(db, "grow_events", "ph"))
        assertTrue(columnExists(db, "grow_events", "ec"))
        assertTrue(columnExists(db, "grow_events", "diagnosisResult"))
        assertTrue(columnExists(db, "grow_events", "groupId"))

        // 5) New tables created.
        listOf(
            "protocol_stages", "stage_entries", "achievements",
            "reminders", "breeding_projects", "breeding_crosses"
        ).forEach { table ->
            assertTrue("table $table should exist", tableExists(db, table))
        }

        // 6) v1 data survived.
        val plant = db.query("SELECT name, tentId FROM plants WHERE name = ?", arrayOf("Planta Prueba"))
        assertTrue(plant.moveToFirst())
        assertEquals("Planta Prueba", plant.getString(0))
        plant.close()

        val protocol = db.query("SELECT name FROM protocols WHERE plantId = ?", arrayOf(1L.toString()))
        assertTrue(protocol.moveToFirst())
        assertEquals("Protocolo V1", protocol.getString(0))
        protocol.close()

        // 7) Room's entities remain usable after migration.
        val daos = room.plantDao()
        val reloaded = runBlocking { daos.getPlantById(1L) }
        assertNotNull("plant readable through DAO after migration", reloaded)
        assertEquals("Planta Prueba", reloaded?.name)

        room.close()
    }

    private fun createVersion1Database() {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val sql = db
                    sql.execSQL("""
                        CREATE TABLE IF NOT EXISTS `grow_tents` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `location` TEXT NOT NULL,
                            `capacity` INTEGER NOT NULL,
                            `lightType` TEXT NOT NULL,
                            `lightPowerWatts` INTEGER NOT NULL,
                            `isActive` INTEGER NOT NULL
                        )
                    """)
                    sql.execSQL("""
                        CREATE TABLE IF NOT EXISTS `plants` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `tentId` INTEGER,
                            `sortOrder` INTEGER NOT NULL,
                            `growStartTimestamp` INTEGER NOT NULL,
                            `currentStage` TEXT NOT NULL,
                            `strain` TEXT NOT NULL,
                            `notes` TEXT NOT NULL,
                            `isActive` INTEGER NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            FOREIGN KEY(`tentId`) REFERENCES `grow_tents`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                        )
                    """)
                    sql.execSQL("CREATE INDEX IF NOT EXISTS `index_plants_tentId` ON `plants` (`tentId`)")
                    sql.execSQL("""
                        CREATE TABLE IF NOT EXISTS `protocols` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `plantId` INTEGER NOT NULL,
                            `name` TEXT NOT NULL,
                            `isActive` INTEGER NOT NULL
                        )
                    """)
                    sql.execSQL("""
                        CREATE TABLE IF NOT EXISTS `grow_events` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `plantId` INTEGER NOT NULL,
                            `eventType` TEXT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `notes` TEXT,
                            `isActive` INTEGER NOT NULL,
                            FOREIGN KEY(`plantId`) REFERENCES `plants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """)
                    sql.execSQL("CREATE INDEX IF NOT EXISTS `index_grow_events_plantId` ON `grow_events` (`plantId`)")
                    sql.execSQL("""
                        CREATE TABLE IF NOT EXISTS `super_cycle_configs` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `plantId` INTEGER NOT NULL,
                            `lightHours` INTEGER NOT NULL,
                            `darkHours` INTEGER NOT NULL,
                            `cycleStartAt` INTEGER NOT NULL,
                            `presetType` TEXT NOT NULL
                        )
                    """)

                    // v1 fixtures
                    sql.execSQL(
                        "INSERT INTO grow_tents (id, name, location, capacity, lightType, lightPowerWatts, isActive) " +
                            "VALUES (1, 'Carpa A', 'Sala', 4, 'LED', 300, 1)"
                    )
                    sql.execSQL(
                        "INSERT INTO plants (id, name, tentId, sortOrder, growStartTimestamp, currentStage, strain, notes, isActive, createdAt) " +
                            "VALUES (1, 'Planta Prueba', 1, 0, 1700000000000, 'vegetative', 'Gorilla', '', 1, 1700000000000)"
                    )
                    sql.execSQL(
                        "INSERT INTO protocols (id, plantId, name, isActive) VALUES (1, 1, 'Protocolo V1', 1)"
                    )
                    sql.execSQL(
                        "INSERT INTO grow_events (id, plantId, eventType, timestamp, notes, isActive) " +
                            "VALUES (1, 1, 'IRRIGATION', 1700000000000, 'riego inicial', 1)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = factory.create(config)
        helper.writableDatabase.close()
        helper.close()
        // user_version is 1 as configured by the callback constructor argument.
    }

    private fun tableExists(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Boolean {
        val cursor = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table)
        )
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    private fun columnExists(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
        column: String
    ): Boolean {
        val cursor = db.query("PRAGMA table_info(`$table`)")
        var found = false
        while (cursor.moveToNext()) {
            if (cursor.getString(1) == column) {
                found = true
                break
            }
        }
        cursor.close()
        return found
    }
}