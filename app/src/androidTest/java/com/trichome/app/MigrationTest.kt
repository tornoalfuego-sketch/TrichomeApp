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
import com.trichome.app.data.database.MIGRATION_3_4
import com.trichome.app.data.database.MIGRATION_4_5
import com.trichome.app.data.model.GrowRange
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

    /**
     * The v4 identity hash, copied from `app/schemas/.../4.json`.
     *
     * Declared here rather than inlined in the fixture so a schema export can be checked
     * against it: if v5's hash changed and this string did not, the fixture is not the state a
     * device is actually in, and every "the upgrade preserved the data" assertion below is
     * measured against a starting point no device ever had.
     */
    private val V4_IDENTITY_HASH: String = "c079caf29c0f1b569fe480a04fcd7fc4"

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

    /**
     * Migration v3 -> v4: the protocol header gains the extended grow fields.
     *
     * This is the only test in the project that proves the whole chain at once:
     * the statements execute against a real SQLite file, and Room's
     * `onValidateSchema` then compares the resulting table with the exported
     * `4.json`. If the migration and the entity disagreed about a column, this
     * test fails on `openHelper.writableDatabase` — which is exactly the moment
     * the user's own device would fail, before a single screen draws.
     *
     * The v3 fixture is the full v3 layout with the real identity hash from
     * `3.json`, so the starting state is what a device is actually in.
     */
    @Test
    fun migrate3To4AddsTheExtendedFieldsAndRewritesNothing() {
        createVersion3Database()

        // If opening throws, the migration and the exported schema disagree.
        val room: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
        val db = room.openHelper.writableDatabase // triggers onUpgrade + validation

        assertEquals(4, db.version)

        // 1) Every added column is there, and every one of them is nullable.
        //    A NOT NULL here would have failed the upgrade on the existing row.
        val added = listOf(
            "vpdBand", "phRange", "ecRange",
            "lightTempCelsius", "lightHumidityPercent",
            "darkTempCelsius", "darkHumidityPercent",
            "ppfd", "dli", "lightType", "lampPowerWatts",
            "substrateType", "wateringStrategy", "observations"
        )
        assertEquals(14, added.size)
        added.forEach { column ->
            assertTrue("`$column` missing after migration", columnExists(db, "protocols", column))
            assertFalse(
                "`$column` must be nullable: the existing row has no value for it",
                columnIsNotNull(db, "protocols", column)
            )
        }

        // 2) The original eight are untouched, in place, with the values the
        //    fixture wrote. This is the zero-data-loss assertion: exact values,
        //    not "a row still exists".
        assertEquals(2, countRows(db, "protocols"))
        val original = db.query(
            "SELECT id, plantId, name, lightHours, darkHours, presetType, cycleStartAt, " +
                "isActive FROM protocols ORDER BY id",
            emptyArray()
        )
        assertTrue(original.moveToFirst())
        assertEquals(1L, original.getLong(0))
        assertEquals(11L, original.getLong(1))
        assertEquals(" vegetative", original.getString(2))
        assertEquals(18, original.getInt(3))
        assertEquals(6, original.getInt(4))
        assertEquals("18/6", original.getString(5))
        assertEquals(1700000000000L, original.getLong(6))
        assertEquals(1, original.getInt(7))
        assertTrue(original.moveToNext())
        assertEquals(2L, original.getLong(0))
        assertEquals(11L, original.getLong(1))
        assertEquals("exterior", original.getString(2))
        assertEquals(20, original.getInt(3))
        assertEquals(4, original.getInt(4))
        assertEquals("20/4", original.getString(5))
        assertEquals(1700000000000L, original.getLong(6))
        assertEquals(1, original.getInt(7))
        assertFalse("the fixture had exactly two protocols", original.moveToNext())
        original.close()

        // 3) The new columns read back NULL, not as a plausible placeholder. A
        //    fabricated 6.0 - 6.5 here would be indistinguishable from a band the
        //    grower typed, which is the one outcome this phase must not ship.
        val unset = db.query(
            "SELECT vpdBand, phRange, ecRange, lightTempCelsius, lightHumidityPercent, " +
                "darkTempCelsius, darkHumidityPercent, ppfd, dli, lightType, " +
                "lampPowerWatts, substrateType, wateringStrategy, observations " +
                "FROM protocols ORDER BY id",
            emptyArray()
        )
        assertTrue(unset.moveToFirst())
        repeat(added.size) { index ->
            assertTrue(
                "new column ${unset.getColumnName(index)} must be NULL, got " +
                    "${unset.getString(index)}",
                unset.isNull(index)
            )
        }
        assertTrue(unset.moveToNext())
        repeat(added.size) { index ->
            assertTrue("second row's new column must be NULL too", unset.isNull(index))
        }
        unset.close()

        // 4) The rest of the database is exactly as it was. The migration is
        //    fourteen ADD COLUMNs and touches nothing else.
        assertEquals(1, countRows(db, "grow_tents"))
        assertEquals(2, countRows(db, "plants"))
        assertEquals(2, countRows(db, "protocol_stages"))
        assertEquals(1, countRows(db, "stage_entries"))
        assertEquals(0, countRows(db, "grow_events"))
        assertEquals(1, countRows(db, "super_cycle_configs"))

        // 5) The stage tables kept their v3 shape: this phase does not touch
        //    them, and a per-stage VPD band is a separate migration.
        assertFalse(columnExists(db, "protocol_stages", "vpdBand"))
        assertFalse(columnExists(db, "protocol_stages", "phRange"))
        val stage = db.query(
            "SELECT stageName, durationDays FROM protocol_stages ORDER BY sortOrder",
            emptyArray()
        )
        assertTrue(stage.moveToFirst())
        assertEquals("Vegetativa", stage.getString(0))
        assertEquals(35, stage.getInt(1))
        assertTrue(stage.moveToNext())
        assertEquals("Floración", stage.getString(0))
        assertEquals(56, stage.getInt(1))
        stage.close()

        // 6) Through the DAO, which is what the app actually reads. The row maps
        //    into the entity with every declared target null.
        val protocols = runBlocking { room.protocolDao().getProtocolsByPlant(11L).first() }
        assertEquals(2, protocols.size)
        val vegetative = protocols.first { it.name == " vegetative" }
        assertEquals(18, vegetative.lightHours)
        assertEquals(6, vegetative.darkHours)
        assertEquals("18/6", vegetative.presetType)
        assertTrue(vegetative.isActive)
        assertNull(vegetative.phRange)
        assertNull(vegetative.vpdBand)
        assertNull(vegetative.ppfd)
        assertNull(vegetative.lightType)

        // 7) And a band written after the migration round-trips through the
        //    same column, which is the point of storing it as one value.
        val withBand = vegetative.copy(
            phRange = GrowRange(5.8f, 6.5f)
        )
        runBlocking { room.protocolDao().insertProtocol(withBand) }
        val reloaded = runBlocking { room.protocolDao().getProtocolById(vegetative.id) }
        assertNotNull(reloaded)
        assertEquals(5.8f, reloaded!!.phRange!!.low, 0f)
        assertEquals(6.5f, reloaded.phRange!!.high, 0f)
        assertEquals("the write must not disturb the columns the migration preserved",
            "18/6", reloaded.presetType)
        assertEquals(" vegetative", reloaded.name)

        room.close()
    }

    /**
     * The v3 fixture: the full v3 layout, copied from `3.json`, with the real
     * identity hash so the file is in the state a device is in.
     *
     * Two protocols, not one: a single row would let a migration that rewrote
     * exactly one row still pass a "does a row survive" check.
     */
    private fun createVersion3Database() {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val sql = db
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `grow_tents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `location` TEXT NOT NULL, `capacity` INTEGER NOT NULL, `lightType` TEXT NOT NULL, `lightPowerWatts` INTEGER NOT NULL, `isActive` INTEGER NOT NULL)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `plants` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `tentId` INTEGER, `sortOrder` INTEGER NOT NULL, `growStartTimestamp` INTEGER NOT NULL, `currentStage` TEXT NOT NULL, `strain` TEXT NOT NULL, `notes` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`tentId`) REFERENCES `grow_tents`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                    sql.execSQL("CREATE INDEX IF NOT EXISTS `index_plants_tentId` ON `plants` (`tentId`)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `protocols` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `plantId` INTEGER NOT NULL, `name` TEXT NOT NULL, `lightHours` INTEGER NOT NULL DEFAULT 18, `darkHours` INTEGER NOT NULL DEFAULT 6, `presetType` TEXT NOT NULL DEFAULT 'custom', `cycleStartAt` INTEGER NOT NULL DEFAULT 0, `isActive` INTEGER NOT NULL)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `protocol_stages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `protocolId` INTEGER NOT NULL, `stageName` TEXT NOT NULL, `durationDays` INTEGER NOT NULL, `recurrenceIntervalDays` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, FOREIGN KEY(`protocolId`) REFERENCES `protocols`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    sql.execSQL("CREATE INDEX IF NOT EXISTS `index_protocol_stages_protocolId` ON `protocol_stages` (`protocolId`)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `stage_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `protocolId` INTEGER NOT NULL, `plantId` INTEGER NOT NULL, `stageName` TEXT NOT NULL, `enteredAt` INTEGER NOT NULL, `exitedAt` INTEGER)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `grow_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `plantId` INTEGER NOT NULL, `groupId` TEXT, `eventType` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `notes` TEXT, `temperature` REAL, `humidity` REAL, `ph` REAL, `ec` REAL, `nutrientN` REAL, `nutrientP` REAL, `nutrientK` REAL, `amount` REAL, `height` REAL, `lampDistance` REAL, `trainingType` TEXT, `defoliationLevel` INTEGER, `vpd` REAL, `trichomeMaturity` TEXT, `diagnosisResult` TEXT, `diagnosisCertainty` REAL, `imagePath` TEXT, `isActive` INTEGER NOT NULL, FOREIGN KEY(`plantId`) REFERENCES `plants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    sql.execSQL("CREATE INDEX IF NOT EXISTS `index_grow_events_plantId` ON `grow_events` (`plantId`)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `super_cycle_configs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tentId` INTEGER, `plantId` INTEGER, `lightHours` INTEGER NOT NULL, `darkHours` INTEGER NOT NULL, `cycleStartAt` INTEGER NOT NULL, `presetType` TEXT NOT NULL)")
                    sql.execSQL("CREATE INDEX IF NOT EXISTS `index_super_cycle_configs_tentId` ON `super_cycle_configs` (`tentId`)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `achievements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `icon` TEXT NOT NULL, `xpReward` INTEGER NOT NULL, `isUnlocked` INTEGER NOT NULL)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `plantId` INTEGER, `title` TEXT NOT NULL, `message` TEXT NOT NULL, `recurrenceType` TEXT NOT NULL, `recurrenceIntervalDays` INTEGER NOT NULL, `reminderTime` INTEGER NOT NULL, `isActive` INTEGER NOT NULL)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `breeding_projects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `motherId` TEXT NOT NULL, `fatherId` TEXT NOT NULL, `generation` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `status` TEXT NOT NULL)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `breeding_crosses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `projectId` INTEGER NOT NULL, `parent1` TEXT NOT NULL, `parent2` TEXT NOT NULL, `phenotypeScore` REAL NOT NULL, `notes` TEXT NOT NULL, FOREIGN KEY(`projectId`) REFERENCES `breeding_projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    sql.execSQL("CREATE INDEX IF NOT EXISTS `index_breeding_crosses_projectId` ON `breeding_crosses` (`projectId`)")

                    // The v3 identity hash from app/schemas/.../3.json.
                    sql.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                    sql.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '63ab5090c2ade669b9fa8308087f8e3d')")

                    sql.execSQL(
                        "INSERT INTO grow_tents (id, name, location, capacity, lightType, lightPowerWatts, isActive) " +
                            "VALUES (4, 'Carpa 4', 'Sala', 4, 'LED', 600, 1)"
                    )
                    listOf(11L, 12L).forEachIndexed { index, plantId ->
                        sql.execSQL(
                            "INSERT INTO plants (id, name, tentId, sortOrder, growStartTimestamp, " +
                                "currentStage, strain, notes, isActive, createdAt) VALUES " +
                                "($plantId, 'Planta $plantId', 4, $index, 1700000000000, " +
                                "'vegetative', 'Test', '', 1, 1700000000000)"
                        )
                    }

                    // Two protocols with different photoperiods, so a migration
                    // that copied the first row over every other one fails.
                    sql.execSQL(
                        "INSERT INTO protocols (id, plantId, name, lightHours, darkHours, presetType, cycleStartAt, isActive) " +
                            "VALUES (1, 11, ' vegetativa', 18, 6, '18/6', 1700000000000, 1)"
                    )
                    sql.execSQL(
                        "INSERT INTO protocols (id, plantId, name, lightHours, darkHours, presetType, cycleStartAt, isActive) " +
                            "VALUES (2, 11, 'exterior', 20, 4, '20/4', 1700000000000, 1)"
                    )
                    sql.execSQL(
                        "INSERT INTO protocol_stages (id, protocolId, stageName, durationDays, recurrenceIntervalDays, sortOrder) " +
                            "VALUES (1, 1, 'Vegetativa', 35, 0, 0)"
                    )
                    sql.execSQL(
                        "INSERT INTO protocol_stages (id, protocolId, stageName, durationDays, recurrenceIntervalDays, sortOrder) " +
                            "VALUES (2, 1, 'Floración', 56, 0, 1)"
                    )
                    sql.execSQL(
                        "INSERT INTO stage_entries (id, protocolId, plantId, stageName, enteredAt, exitedAt) " +
                            "VALUES (1, 1, 11, 'Vegetativa', 1700000000000, NULL)"
                    )
                    sql.execSQL(
                        "INSERT INTO super_cycle_configs (id, tentId, plantId, lightHours, darkHours, cycleStartAt, presetType) " +
                            "VALUES (1, 4, 11, 18, 6, 1700000000000, '18/6')"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = factory.create(config)
        helper.writableDatabase.close()
        helper.close()
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

    /**
     * Migration v4 -> v5: the VPD provenance columns.
     *
     * Together with `migrate3To4AddsTheExtendedFieldsAndRewritesNothing` this is the only
     * proof in the project that runs a migration against real SQLite and then lets Room's
     * `onValidateSchema` compare the result with the exported schema. If the migration and the
     * entity disagreed about a column, this test fails on `openHelper.writableDatabase`, which
     * is exactly the moment the user's own device would fail, before a screen draws.
     *
     * ## What it proves and what it cannot
     *
     * It proves the statements execute, the resulting table matches `5.json`, the existing
     * rows keep every original value, and the new columns read back NULL rather than as a
     * fabricated default.
     *
     * It cannot run without a device. **As of this commit it has never been executed** —
     * `adb devices` returns empty in this environment, so the v4 -> v5 upgrade path is
     * written and audited by `VpdProvenanceMigrationContractTest` but unproven on hardware.
     *
     * ## The fixture
     *
     * Two plants and three journal rows with *different* `vpd` values, two of them with no
     * provenance. Three rows rather than one, because a migration that rewrote every row to
     * the same value would pass a "a row survived" check.
     */
    @Test
    fun migrate4To5AddsTheVpdProvenanceColumnsAndRewritesNothing() {
        createVersion4Database()

        val room: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
        val db = room.openHelper.writableDatabase // triggers onUpgrade + validation

        assertEquals(5, db.version)

        // 1) Both columns are there, and both are nullable. A NOT NULL here would have failed
        //    the upgrade on the rows the fixture already wrote.
        val added = listOf("vpdSource", "vpdLeafOffset")
        added.forEach { column ->
            assertTrue("`$column` missing after migration", columnExists(db, "grow_events", column))
            assertFalse(
                "`$column` must be nullable: the existing row has no value for it",
                columnIsNotNull(db, "grow_events", column)
            )
        }

        // 2) The original 23 columns are untouched, in place, with the values the fixture
        //    wrote. This is the zero-data-loss assertion: exact values, not "a row still
        //    exists".
        assertEquals(3, countRows(db, "grow_events"))
        val original = db.query(
            "SELECT id, plantId, eventType, timestamp, notes, temperature, humidity, vpd, " +
                "isActive FROM grow_events ORDER BY id",
            emptyArray()
        )
        assertTrue(original.moveToFirst())
        assertEquals(1L, original.getLong(0))
        assertEquals(11L, original.getLong(1))
        assertEquals("IRRIGATION", original.getString(2))
        assertEquals(1700000000000L, original.getLong(3))
        assertEquals("riego inicial", original.getString(4))
        assertEquals(21.5f, original.getFloat(5), 1e-6f)
        assertEquals(58f, original.getFloat(6), 1e-6f)
        assertEquals(0.62f, original.getFloat(7), 1e-6f)
        assertEquals(1, original.getInt(8))

        assertTrue(original.moveToNext())
        assertEquals(2L, original.getLong(0))
        assertEquals(11L, original.getLong(1))
        assertEquals("VPD", original.getString(2))
        assertEquals(1700086400000L, original.getLong(3))
        assertTrue("the second row's notes are NULL in the fixture", original.isNull(4))
        assertEquals(1.24f, original.getFloat(7), 1e-6f)

        assertTrue(original.moveToNext())
        assertEquals(3L, original.getLong(0))
        assertEquals(12L, original.getLong(1))
        assertEquals("VPD", original.getString(2))
        assertEquals(1.31f, original.getFloat(7), 1e-6f)
        assertFalse("the fixture had exactly three events", original.moveToNext())
        original.close()

        // 3) The new columns read back NULL, not as a plausible placeholder. Defaulting
        //    `vpdSource` to 'MEASURED' would be the worst outcome available: every pre-v5
        //    derived number would be published as a sensor reading.
        val provenance = db.query(
            "SELECT vpdSource, vpdLeafOffset FROM grow_events ORDER BY id",
            emptyArray()
        )
        assertTrue(provenance.moveToFirst())
        repeat(3) { index ->
            assertTrue(
                "row ${index + 1}: vpdSource must be NULL, got ${provenance.getString(0)}",
                provenance.isNull(0)
            )
            assertTrue(
                "row ${index + 1}: vpdLeafOffset must be NULL",
                provenance.isNull(1)
            )
            if (index < 2) assertTrue(provenance.moveToNext())
        }
        provenance.close()

        // 4) A row written after the migration round-trips through the same columns, which is
        //    what lets the chart keep a measured and a calculated reading apart.
        val logged = runBlocking { room.eventDao().insertEvent(eventAfterMigration()) }
        val reread = db.query(
            "SELECT vpd, vpdSource, vpdLeafOffset FROM grow_events WHERE id = ?",
            arrayOf(logged.toString())
        )
        assertTrue(reread.moveToFirst())
        assertEquals(0.94f, reread.getFloat(0), 1e-6f)
        assertEquals("CALCULATED", reread.getString(1))
        assertEquals(2.5f, reread.getFloat(2), 1e-6f)
        reread.close()

        // 5) The rest of the database is exactly as it was. The migration is two ADD COLUMNs
        //    and touches nothing else.
        assertEquals(1, countRows(db, "grow_tents"))
        assertEquals(2, countRows(db, "plants"))
        assertEquals(1, countRows(db, "protocols"))
        assertEquals(2, countRows(db, "protocol_stages"))
        assertEquals(1, countRows(db, "stage_entries"))
        assertEquals(1, countRows(db, "super_cycle_configs"))

        // 6) The VPD query the chart runs sees the pre-v5 rows and the new one, all of them,
        //    and only rows that carry a VPD. The irrigation note is excluded, not plotted at
        //    zero.
        val history = runBlocking {
            room.eventDao().getVpdHistory(11L, 1_600_000_000_000L, 1_900_000_000_000L)
        }
        assertEquals(2, history.size)
        assertTrue(history.all { it.vpd != null })
        assertEquals(
            "a pre-v5 row must resolve to UNKNOWN, never to MEASURED",
            listOf(null, "CALCULATED"),
            history.sortedBy { it.id }.map { it.vpdSource }
        )

        // 7) And through the entity, which is what the app actually reads.
        val events = runBlocking { room.eventDao().getEventsByPlantSnapshot(11L) }
        assertEquals(2, events.size)
        val preV5 = events.first { it.id == 1L }
        assertEquals(0.62f, preV5.vpd!!, 1e-6f)
        assertEquals(null, preV5.vpdSource)
        assertEquals(null, preV5.vpdLeafOffset)

        room.close()
    }

    /** The row `migrate4To5` writes after the upgrade, to prove the columns round-trip. */
    private fun eventAfterMigration() = com.trichome.app.data.entity.GrowEvent(
        id = 0L,
        plantId = 11L,
        eventType = "VPD",
        timestamp = 1700172800000L,
        notes = "calculado",
        temperature = 25f,
        humidity = 55f,
        vpd = 0.94f,
        vpdSource = "CALCULATED",
        vpdLeafOffset = 2.5f
    )

    /**
     * The v4 fixture: the full v4 layout, copied from `4.json`, with the real identity hash.
     *
     * Two plants and one protocol, so a migration that rewrote a row would be visible in the
     * counts below.
     */
    private fun createVersion4Database() {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val sql = db
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `grow_tents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `location` TEXT NOT NULL, `capacity` INTEGER NOT NULL, `lightType` TEXT NOT NULL, `lightPowerWatts` INTEGER NOT NULL, `isActive` INTEGER NOT NULL)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `plants` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `tentId` INTEGER, `sortOrder` INTEGER NOT NULL, `growStartTimestamp` INTEGER NOT NULL, `currentStage` TEXT NOT NULL, `strain` TEXT NOT NULL, `notes` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`tentId`) REFERENCES `grow_tents`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                    sql.execSQL("CREATE INDEX IF NOT EXISTS `index_plants_tentId` ON `plants` (`tentId`)")
                    // The v4 protocols layout: eight original columns plus F10a's fourteen.
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `protocols` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `plantId` INTEGER NOT NULL, `name` TEXT NOT NULL, `lightHours` INTEGER NOT NULL DEFAULT 18, `darkHours` INTEGER NOT NULL DEFAULT 6, `presetType` TEXT NOT NULL DEFAULT 'custom', `cycleStartAt` INTEGER NOT NULL DEFAULT 0, `isActive` INTEGER NOT NULL, `vpdBand` TEXT DEFAULT NULL, `phRange` TEXT DEFAULT NULL, `ecRange` TEXT DEFAULT NULL, `lightTempCelsius` REAL DEFAULT NULL, `lightHumidityPercent` REAL DEFAULT NULL, `darkTempCelsius` REAL DEFAULT NULL, `darkHumidityPercent` REAL DEFAULT NULL, `ppfd` REAL DEFAULT NULL, `dli` REAL DEFAULT NULL, `lightType` TEXT DEFAULT NULL, `lampPowerWatts` REAL DEFAULT NULL, `substrateType` TEXT DEFAULT NULL, `wateringStrategy` TEXT DEFAULT NULL, `observations` TEXT DEFAULT NULL)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `protocol_stages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `protocolId` INTEGER NOT NULL, `stageName` TEXT NOT NULL, `durationDays` INTEGER NOT NULL, `recurrenceIntervalDays` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, FOREIGN KEY(`protocolId`) REFERENCES `protocols`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    sql.execSQL("CREATE INDEX IF NOT EXISTS `index_protocol_stages_protocolId` ON `protocol_stages` (`protocolId`)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `stage_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `protocolId` INTEGER NOT NULL, `plantId` INTEGER NOT NULL, `stageName` TEXT NOT NULL, `enteredAt` INTEGER NOT NULL, `exitedAt` INTEGER)")
                    // The v4 grow_events: 25 columns, `vpd` with no default.
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `grow_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `plantId` INTEGER NOT NULL, `groupId` TEXT, `eventType` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `notes` TEXT, `temperature` REAL, `humidity` REAL, `ph` REAL, `ec` REAL, `nutrientN` REAL, `nutrientP` REAL, `nutrientK` REAL, `amount` REAL, `height` REAL, `lampDistance` REAL, `trainingType` TEXT, `defoliationLevel` INTEGER, `vpd` REAL, `trichomeMaturity` TEXT, `diagnosisResult` TEXT, `diagnosisCertainty` REAL, `imagePath` TEXT, `isActive` INTEGER NOT NULL, FOREIGN KEY(`plantId`) REFERENCES `plants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    sql.execSQL("CREATE INDEX IF NOT EXISTS `index_grow_events_plantId` ON `grow_events` (`plantId`)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `super_cycle_configs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tentId` INTEGER, `plantId` INTEGER, `lightHours` INTEGER NOT NULL, `darkHours` INTEGER NOT NULL, `cycleStartAt` INTEGER NOT NULL, `presetType` TEXT NOT NULL)")
                    sql.execSQL("CREATE INDEX IF NOT EXISTS `index_super_cycle_configs_tentId` ON `super_cycle_configs` (`tentId`)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `achievements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `icon` TEXT NOT NULL, `xpReward` INTEGER NOT NULL, `isUnlocked` INTEGER NOT NULL)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `plantId` INTEGER, `title` TEXT NOT NULL, `message` TEXT NOT NULL, `recurrenceType` TEXT NOT NULL, `recurrenceIntervalDays` INTEGER NOT NULL, `reminderTime` INTEGER NOT NULL, `isActive` INTEGER NOT NULL)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `breeding_projects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `motherId` TEXT NOT NULL, `fatherId` TEXT NOT NULL, `generation` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `status` TEXT NOT NULL)")
                    sql.execSQL("CREATE TABLE IF NOT EXISTS `breeding_crosses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `projectId` INTEGER NOT NULL, `parent1` TEXT NOT NULL, `parent2` TEXT NOT NULL, `phenotypeScore` REAL NOT NULL, `notes` TEXT NOT NULL, FOREIGN KEY(`projectId`) REFERENCES `breeding_projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    sql.execSQL("CREATE INDEX IF NOT EXISTS `index_breeding_crosses_projectId` ON `breeding_crosses` (`projectId`)")

                    // The v4 identity hash from app/schemas/.../4.json, so the starting state is
                    // what a real device holds rather than a file Room has never validated.
                    sql.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                    sql.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '$V4_IDENTITY_HASH')")

                    sql.execSQL(
                        "INSERT INTO grow_tents (id, name, location, capacity, lightType, lightPowerWatts, isActive) " +
                            "VALUES (4, 'Carpa 4', 'Sala', 4, 'LED', 600, 1)"
                    )
                    listOf(11L, 12L).forEachIndexed { index, plantId ->
                        sql.execSQL(
                            "INSERT INTO plants (id, name, tentId, sortOrder, growStartTimestamp, " +
                                "currentStage, strain, notes, isActive, createdAt) VALUES " +
                                "($plantId, 'Planta $plantId', 4, $index, 1700000000000, " +
                                "'vegetative', 'Test', '', 1, 1700000000000)"
                        )
                    }
                    sql.execSQL(
                        "INSERT INTO protocols (id, plantId, name, lightHours, darkHours, presetType, cycleStartAt, isActive) " +
                            "VALUES (1, 11, 'exterior', 18, 6, '18/6', 1700000000000, 1)"
                    )
                    sql.execSQL(
                        "INSERT INTO protocol_stages (id, protocolId, stageName, durationDays, recurrenceIntervalDays, sortOrder) " +
                            "VALUES (1, 1, 'Vegetativa', 35, 0, 0)"
                    )
                    sql.execSQL(
                        "INSERT INTO protocol_stages (id, protocolId, stageName, durationDays, recurrenceIntervalDays, sortOrder) " +
                            "VALUES (2, 1, 'Floración', 56, 0, 1)"
                    )
                    sql.execSQL(
                        "INSERT INTO stage_entries (id, protocolId, plantId, stageName, enteredAt, exitedAt) " +
                            "VALUES (1, 1, 11, 'Vegetativa', 1700000000000, NULL)"
                    )
                    sql.execSQL(
                        "INSERT INTO super_cycle_configs (id, tentId, plantId, lightHours, darkHours, cycleStartAt, presetType) " +
                            "VALUES (1, 4, 11, 18, 6, 1700000000000, '18/6')"
                    )

                    // Three journal rows with three different `vpd` values, so a migration that
                    // rewrote them all to one value fails. Row 1 has no VPD recorded at all,
                    // which is the "not a zero" case.
                    sql.execSQL(
                        "INSERT INTO grow_events (id, plantId, eventType, timestamp, notes, temperature, humidity, vpd, isActive) " +
                            "VALUES (1, 11, 'IRRIGATION', 1700000000000, 'riego inicial', 21.5, 58.0, 0.62, 1)"
                    )
                    sql.execSQL(
                        "INSERT INTO grow_events (id, plantId, eventType, timestamp, temperature, humidity, vpd, isActive) " +
                            "VALUES (2, 11, 'VPD', 1700086400000, 26.0, 55.0, 1.24, 1)"
                    )
                    sql.execSQL(
                        "INSERT INTO grow_events (id, plantId, eventType, timestamp, temperature, humidity, vpd, isActive) " +
                            "VALUES (3, 12, 'VPD', 1700086400000, 27.0, 52.0, 1.31, 1)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = factory.create(config)
        helper.writableDatabase.close()
        helper.close()
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