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