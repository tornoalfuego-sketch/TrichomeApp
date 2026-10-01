package com.trichome.app.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit migration from schema v1 to v2.
 *
 * v1 (first internal layout): plants, grow_tents, protocols (flat stage block),
 * super_cycle_configs, grow_events (simple bitácora).
 *
 * v2 (1.0.0 release):
 *  - protocols gain photoperiod columns (lightHours, darkHours, presetType, cycleStartAt).
 *  - grow_events gains the nullable metric/diagnosis columns.
 *  - New tables: protocol_stages, stage_entries, achievements, reminders,
 *    breeding_projects, breeding_crosses.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ── Protocols: add photoperiod config ──────────────────────────
        db.execSQL("ALTER TABLE protocols ADD COLUMN lightHours INTEGER NOT NULL DEFAULT 18")
        db.execSQL("ALTER TABLE protocols ADD COLUMN darkHours INTEGER NOT NULL DEFAULT 6")
        db.execSQL("ALTER TABLE protocols ADD COLUMN presetType TEXT NOT NULL DEFAULT 'custom'")
        db.execSQL("ALTER TABLE protocols ADD COLUMN cycleStartAt INTEGER NOT NULL DEFAULT 0")

        // ── grow_events: add nullable metric + diagnosis columns ────────
        db.execSQL("ALTER TABLE grow_events ADD COLUMN groupId TEXT")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN temperature REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN humidity REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN ph REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN ec REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN nutrientN REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN nutrientP REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN nutrientK REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN amount REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN height REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN lampDistance REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN trainingType TEXT")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN defoliationLevel INTEGER")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN vpd REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN trichomeMaturity TEXT")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN diagnosisResult TEXT")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN diagnosisCertainty REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN imagePath TEXT")

        // ── New tables ──────────────────────────────────────────────────
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `protocol_stages` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `protocolId` INTEGER NOT NULL,
                `stageName` TEXT NOT NULL,
                `durationDays` INTEGER NOT NULL,
                `recurrenceIntervalDays` INTEGER NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                FOREIGN KEY(`protocolId`) REFERENCES `protocols`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `stage_entries` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `protocolId` INTEGER NOT NULL,
                `plantId` INTEGER NOT NULL,
                `stageName` TEXT NOT NULL,
                `enteredAt` INTEGER NOT NULL,
                `exitedAt` INTEGER
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `achievements` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `icon` TEXT NOT NULL,
                `xpReward` INTEGER NOT NULL,
                `isUnlocked` INTEGER NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `reminders` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `plantId` INTEGER,
                `title` TEXT NOT NULL,
                `message` TEXT NOT NULL,
                `recurrenceType` TEXT NOT NULL,
                `recurrenceIntervalDays` INTEGER NOT NULL,
                `reminderTime` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `breeding_projects` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `motherId` TEXT NOT NULL,
                `fatherId` TEXT NOT NULL,
                `generation` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `status` TEXT NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `breeding_crosses` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `projectId` INTEGER NOT NULL,
                `parent1` TEXT NOT NULL,
                `parent2` TEXT NOT NULL,
                `phenotypeScore` REAL NOT NULL,
                `notes` TEXT NOT NULL,
                FOREIGN KEY(`projectId`) REFERENCES `breeding_projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """)

        // Indices validated by Room schema.
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_plants_tentId` ON `plants` (`tentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_grow_events_plantId` ON `grow_events` (`plantId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_protocol_stages_protocolId` ON `protocol_stages` (`protocolId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_breeding_crosses_projectId` ON `breeding_crosses` (`projectId`)")
    }
}

/**
 * Explicit migration from schema v2 to v3.
 *
 * v3 (1.4.0): the supercycle moves from the plant to the **tent**. `protocols`
 * keeps its per-plant photoperiod as history; `super_cycle_configs` gains a
 * nullable `tentId` and becomes tent-scoped.
 *
 * ## Why the table is rebuilt instead of ALTERed
 *
 * Two columns change and neither can be reached with `ADD COLUMN`:
 *  - `tentId` is new and nullable, which `ADD COLUMN` could have done.
 *  - `plantId` becomes nullable, because a config written from v3 onwards has no
 *    plant to point at. SQLite cannot drop a `NOT NULL` constraint on a column
 *    in place, and Room compares nullability between the entity and the live
 *    table, so leaving the constraint would fail validation on the first open.
 *
 * Hence the Room rebuild sequence: create the v3 shape, copy the rows, drop the
 * old table, rename, recreate the index. No foreign keys, views or triggers
 * reference this table, so there is nothing else to re-point.
 *
 * ## Why `tentId` is nullable
 *
 * This is not a convenience. On the install that prompted the change, two of the
 * three configs pointed at plant ids that do not exist (`plantId` 0 and 1), so
 * resolving them against `plants` yields nothing. `NOT NULL` here fails the
 * upgrade at that row and the app does not open. They are copied with
 * `tentId = NULL` — the grower's explicit decision — and are listed under
 * "Sin carpa" in the supercycle screen instead of being dropped.
 *
 * ## Reversibility
 *
 * `plantId` is deliberately kept and deliberately nullable. It is the only link
 * the orphaned configs have left, and keeping the column is what keeps v3
 * reversible; dropping it here would make it unrecoverable by any later
 * migration. Nothing reads it to decide which config applies — that is
 * `SuperCycleRepository.getConfigForPlant`, in one place.
 *
 * The copy resolves the tent inline, which is the same statement as the
 * `UPDATE super_cycle_configs SET tentId = (SELECT tentId FROM plants WHERE id
 * = plantId)` it replaces, with the aliases made explicit so the correlated
 * subquery cannot be misread:
 *
 *     UPDATE super_cycle_configs SET tentId =
 *         (SELECT tentId FROM plants WHERE plants.id = super_cycle_configs.plantId)
 *
 * A config whose plant does not exist resolves the subquery to NULL and stays
 * NULL, which is the intended outcome rather than an accident.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. The v3 shape. Column order and constraints are copied verbatim from
        //    the Room-generated schema in app/schemas/.../3.json; Room validates
        //    the live table against that file, not against this comment.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `super_cycle_configs_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `tentId` INTEGER,
                `plantId` INTEGER,
                `lightHours` INTEGER NOT NULL,
                `darkHours` INTEGER NOT NULL,
                `cycleStartAt` INTEGER NOT NULL,
                `presetType` TEXT NOT NULL
            )
        """)

        // 2. Copy every row. No WHERE clause: a filter here is a silent data
        //    loss, and the orphan rows are the ones we cannot afford to drop.
        db.execSQL("""
            INSERT INTO `super_cycle_configs_new`
                (`id`, `tentId`, `plantId`, `lightHours`, `darkHours`, `cycleStartAt`, `presetType`)
            SELECT
                s.`id`,
                (SELECT p.`tentId` FROM `plants` p WHERE p.`id` = s.`plantId`),
                s.`plantId`,
                s.`lightHours`,
                s.`darkHours`,
                s.`cycleStartAt`,
                s.`presetType`
            FROM `super_cycle_configs` s
        """)

        // 3. The old table goes before the rename, so the rename target is free.
        db.execSQL("DROP TABLE `super_cycle_configs`")
        db.execSQL("ALTER TABLE `super_cycle_configs_new` RENAME TO `super_cycle_configs`")

        // 4. The index Room expects for the new column.
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_super_cycle_configs_tentId` " +
                "ON `super_cycle_configs` (`tentId`)"
        )
    }
}