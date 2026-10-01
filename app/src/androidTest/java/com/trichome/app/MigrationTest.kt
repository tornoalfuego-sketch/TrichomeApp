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
import com.trichome.app.data.database.MIGRATION_2_3
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    /**
     * Migration v2 -> v3 against a copy of the real install's data.
     *
     * The fixture is the database that prompted this migration, row for row: three
     * tents, eight plants, three supercycle configs of which two point at plant
     * ids that do not exist, and one protocol pointing at plant 0. That last part
     * is why `plantId = 0` appears twice in two different tables, and it is the
     * reason the new `tentId` column cannot be NOT NULL -- the upgrade fails on
     * exactly that row and the app never opens.
     *
     * `room_master_table` is seeded with the v2 identity hash from
     * app/schemas/.../2.json, so the starting state is what a real device has
     * rather than a bare file Room has never seen.
     */
    @Test
    fun migrate2To3MovesTheSupercycleToTheTentAndLosesNothing() {
        createVersion2Database()

        val room: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
        val db = room.openHelper.writableDatabase // triggers onUpgrade

        assertEquals(3, db.version)

        // The column is there and it is nullable, which is the whole point.
        assertTrue(columnExists(db, "super_cycle_configs", "tentId"))
        assertTrue("plantId must still exist; it is what keeps v3 reversible",
            columnExists(db, "super_cycle_configs", "plantId"))
        assertFalse("tentId cannot be NOT NULL: two real rows resolve to nothing",
            columnIsNotNull(db, "super_cycle_configs", "tentId"))
        assertFalse("plantId had to become nullable for a tent-scoped config",
            columnIsNotNull(db, "super_cycle_configs", "plantId"))
        assertTrue("Room validates this index by name",
            indexExists(db, "super_cycle_configs", "index_super_cycle_configs_tentId"))

        // No rows lost anywhere. Exact counts, because "close enough" is how a
        // silent data loss hides in a migration test.
        assertEquals(3, countRows(db, "grow_tents"))
        assertEquals(8, countRows(db, "plants"))
        assertEquals(3, countRows(db, "super_cycle_configs"))
        assertEquals(1, countRows(db, "protocols"))
        assertEquals(0, countRows(db, "protocol_stages"))
        assertEquals(0, countRows(db, "stage_entries"))
        assertEquals(0, countRows(db, "grow_events"))
        assertEquals(0, countRows(db, "achievements"))
        assertEquals(0, countRows(db, "reminders"))
        assertEquals(0, countRows(db, "breeding_projects"))
        assertEquals(0, countRows(db, "breeding_crosses"))

        // The tent was resolved from the config's plant: plant 11 is in tent 4.
        assertEquals(4L, tentIdOf(db, 3L))
        assertEquals("plantId is kept as provenance, not rewritten",
            11L, plantIdOf(db, 3L))

        // The two orphans stay, with no tent. Kept, not deleted and not hidden.
        assertNull("config 1 points at a plant that does not exist",
            tentIdOf(db, 1L))
        assertNull("config 2 points at a plant that does not exist",
            tentIdOf(db, 2L))
        assertEquals(0L, plantIdOf(db, 1L))
        assertEquals(1L, plantIdOf(db, 2L))

        // The photoperiod values came through untouched.
        assertEquals(13, hoursOf(db, 3L, "lightHours"))
        assertEquals(13, hoursOf(db, 3L, "darkHours"))
        assertEquals("custom", presetOf(db, 3L))

        // The orphan protocol is a different table and a different decision: it
        // stays exactly as it was, orphan and all.
        val protocol = db.query(
            "SELECT plantId, name, lightHours, darkHours, presetType FROM protocols WHERE id = 1",
            emptyArray()
        )
        assertTrue(protocol.moveToFirst())
        assertEquals(0L, protocol.getLong(0))
        assertEquals("protocolo 1", protocol.getString(1))
        assertEquals(12, protocol.getInt(2))
        assertEquals(12, protocol.getInt(3))
        assertEquals("1", protocol.getString(4))
        protocol.close()

        // The DAOs agree with the raw SQL, which is what the app actually reads.
        val superCycle = room.superCycleDao()
        assertEquals(3, runBlocking { superCycle.getAllConfigs() }.size)

        val tentConfig = runBlocking { superCycle.getConfigByTent(4L) }
        assertNotNull("tent 4's config must be found by tent", tentConfig)
        assertEquals(3L, tentConfig?.id)
        assertEquals(13, tentConfig?.lightHours)

        // Inheritance: tent 4 holds plants 11, 12, 13 and 14.
        val inheriting = runBlocking { superCycle.getPlantsInheriting(4L, 11L) }
        assertEquals(4, inheriting.size)
        assertEquals(listOf(11L, 12L, 13L, 14L), inheriting.map { it.id })

        // An orphan resolves to no plants at all, rather than to a wrong one.
        assertEquals(0, runBlocking { superCycle.getPlantsInheriting(null, 0L) }.size)

        // And the orphans are reachable, which is the defect this whole change
        // exists to not repeat.
        val orphans = runBlocking { superCycle.getConfigsWithoutTent().first() }
        assertEquals(listOf(1L, 2L), orphans.map { it.id })

        room.close()
    }

    private fun createVersion2Database() {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val sql = db
                    // The full v2 layout, copied from app/schemas/.../2.json.
                    // Only super_cycle_configs is touched by this migration, but
                    // Room validates every entity on open, so a partial fixture
                    // would fail for the wrong reason.
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `grow_tents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `location` TEXT NOT NULL, `capacity` INTEGER NOT NULL, `lightType` TEXT NOT NULL, `lightPowerWatts` INTEGER NOT NULL, `isActive` INTEGER NOT NULL)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `plants` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `tentId` INTEGER, `sortOrder` INTEGER NOT NULL, `growStartTimestamp` INTEGER NOT NULL, `currentStage` TEXT NOT NULL, `strain` TEXT NOT NULL, `notes` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`tentId`) REFERENCES `grow_tents`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                    sql.execSQL("CREATE INDEX IF NOT EXISTS `index_plants_tentId` ON `plants` (`tentId`)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `protocols` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `plantId` INTEGER NOT NULL, `name` TEXT NOT NULL, `lightHours` INTEGER NOT NULL DEFAULT 18, `darkHours` INTEGER NOT NULL DEFAULT 6, `presetType` TEXT NOT NULL DEFAULT 'custom', `cycleStartAt` INTEGER NOT NULL DEFAULT 0, `isActive` INTEGER NOT NULL)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `protocol_stages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `protocolId` INTEGER NOT NULL, `stageName` TEXT NOT NULL, `durationDays` INTEGER NOT NULL, `recurrenceIntervalDays` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, FOREIGN KEY(`protocolId`) REFERENCES `protocols`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    sql.execSQL("CREATE INDEX IF NOT EXISTS `index_protocol_stages_protocolId` ON `protocol_stages` (`protocolId`)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `stage_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `protocolId` INTEGER NOT NULL, `plantId` INTEGER NOT NULL, `stageName` TEXT NOT NULL, `enteredAt` INTEGER NOT NULL, `exitedAt` INTEGER)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `grow_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `plantId` INTEGER NOT NULL, `groupId` TEXT, `eventType` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `notes` TEXT, `temperature` REAL, `humidity` REAL, `ph` REAL, `ec` REAL, `nutrientN` REAL, `nutrientP` REAL, `nutrientK` REAL, `amount` REAL, `height` REAL, `lampDistance` REAL, `trainingType` TEXT, `defoliationLevel` INTEGER, `vpd` REAL, `trichomeMaturity` TEXT, `diagnosisResult` TEXT, `diagnosisCertainty` REAL, `imagePath` TEXT, `isActive` INTEGER NOT NULL, FOREIGN KEY(`plantId`) REFERENCES `plants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    sql.execSQL("CREATE INDEX IF NOT EXISTS `index_grow_events_plantId` ON `grow_events` (`plantId`)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `super_cycle_configs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `plantId` INTEGER NOT NULL, `lightHours` INTEGER NOT NULL, `darkHours` INTEGER NOT NULL, `cycleStartAt` INTEGER NOT NULL, `presetType` TEXT NOT NULL)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `achievements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `icon` TEXT NOT NULL, `xpReward` INTEGER NOT NULL, `isUnlocked` INTEGER NOT NULL)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `plantId` INTEGER, `title` TEXT NOT NULL, `message` TEXT NOT NULL, `recurrenceType` TEXT NOT NULL, `recurrenceIntervalDays` INTEGER NOT NULL, `reminderTime` INTEGER NOT NULL, `isActive` INTEGER NOT NULL)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `breeding_projects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `motherId` TEXT NOT NULL, `fatherId` TEXT NOT NULL, `generation` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `status` TEXT NOT NULL)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `breeding_crosses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `projectId` INTEGER NOT NULL, `parent1` TEXT NOT NULL, `parent2` TEXT NOT NULL, `phenotypeScore` REAL NOT NULL, `notes` TEXT NOT NULL, FOREIGN KEY(`projectId`) REFERENCES `breeding_projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    sql.execSQL("CREATE INDEX IF NOT EXISTS `index_breeding_crosses_projectId` ON `breeding_crosses` (`projectId`)")

                    // Seed the real identity hash so this is the state a device
                    // is actually in, not a file Room has never validated.
                    sql.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                    sql.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '23eb1e14735ca6c0d9ce5af71ee11cd5')")

                    // v2 fixtures: the real three tents.
                    listOf(
                        Triple(1, "Carpa 1", 4),
                        Triple(3, "carpa3", 4),
                        Triple(4, "Carpa 4", 4)
                    ).forEach { (id, name, capacity) ->
                        sql.execSQL(
                            "INSERT INTO grow_tents (id, name, location, capacity, lightType, lightPowerWatts, isActive) " +
                                "VALUES ($id, '$name', 'Sala', $capacity, 'LED', 300, 1)"
                        )
                    }

                    // Eight plants. Plant 6 sits in tent 3 (which the real install
                    // calls carpa3); none of them is id 0 or 1, which is the point.
                    val plantTents = listOf(
                        6 to 3, 8 to 3, 9 to 3, 10 to 1,
                        11 to 4, 12 to 4, 13 to 4, 14 to 4
                    )
                    plantTents.forEachIndexed { index, (plantId, tentId) ->
                        sql.execSQL(
                            "INSERT INTO plants (id, name, tentId, sortOrder, growStartTimestamp, " +
                                "currentStage, strain, notes, isActive, createdAt) VALUES " +
                                "($plantId, 'Planta $plantId', $tentId, $index, 1700000000000, " +
                                "'vegetative', 'Test', '', 1, 1700000000000)"
                        )
                    }

                    // Three supercycle configs. Ids 1 and 2 point at plants that
                    // were never there: the orphans the grower decided to keep.
                    listOf(1L, 1L, 11L).forEachIndexed { index, plantId ->
                        sql.execSQL(
                            "INSERT INTO super_cycle_configs (id, plantId, lightHours, darkHours, cycleStartAt, presetType) " +
                                "VALUES (${index + 1}, $plantId, 13, 13, 1700000000000, 'custom')"
                        )
                    }

                    // One protocol, also orphaned, also kept.
                    sql.execSQL(
                        "INSERT INTO protocols (id, plantId, name, lightHours, darkHours, presetType, cycleStartAt, isActive) " +
                            "VALUES (1, 0, 'protocolo 1', 12, 12, '1', 1700000000000, 1)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = factory.create(config)
        helper.writableDatabase.close()
        helper.close()
        // user_version is 2 as configured by the callback constructor argument.
    }

    private fun countRows(db: SupportSQLiteDatabase, table: String): Int {
        val cursor = db.query("SELECT COUNT(*) FROM `$table`")
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()
        return count
    }

    /** `null` for a column that is NULL, which is the expected orphan result. */
    private fun tentIdOf(db: SupportSQLiteDatabase, configId: Long): Long? =
        longOrNull(db, "SELECT tentId FROM super_cycle_configs WHERE id = $configId")

    private fun plantIdOf(db: SupportSQLiteDatabase, configId: Long): Long? =
        longOrNull(db, "SELECT plantId FROM super_cycle_configs WHERE id = $configId")

    private fun hoursOf(db: SupportSQLiteDatabase, configId: Long, column: String): Int {
        val cursor = db.query("SELECT `$column` FROM super_cycle_configs WHERE id = $configId")
        cursor.moveToFirst()
        val value = cursor.getInt(0)
        cursor.close()
        return value
    }

    private fun presetOf(db: SupportSQLiteDatabase, configId: Long): String {
        val cursor = db.query("SELECT presetType FROM super_cycle_configs WHERE id = $configId")
        cursor.moveToFirst()
        val value = cursor.getString(0)
        cursor.close()
        return value
    }

    private fun longOrNull(db: SupportSQLiteDatabase, sql: String): Long? {
        val cursor = db.query(sql)
        if (!cursor.moveToFirst()) {
            cursor.close()
            return null
        }
        val value = if (cursor.isNull(0)) null else cursor.getLong(0)
        cursor.close()
        return value
    }

    private fun columnIsNotNull(
        db: SupportSQLiteDatabase,
        table: String,
        column: String
    ): Boolean {
        val cursor = db.query("PRAGMA table_info(`$table`)")
        var notNull = false
        while (cursor.moveToNext()) {
            if (cursor.getString(1) == column) {
                notNull = cursor.getInt(3) == 1
                break
            }
        }
        cursor.close()
        return notNull
    }

    private fun indexExists(
        db: SupportSQLiteDatabase,
        table: String,
        index: String
    ): Boolean {
        val cursor = db.query("PRAGMA index_list(`$table`)")
        var found = false
        while (cursor.moveToNext()) {
            if (cursor.getString(1) == index) {
                found = true
                break
            }
        }
        cursor.close()
        return found
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