package com.trichome.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.trichome.app.data.dao.*
import com.trichome.app.data.entity.*

/**
 * Room schema version — the single source of truth for `@Database(version = …)`
 * and for the Settings screen, which used to print a literal `Room v2` that
 * went stale on the first migration.
 *
 * Declared at file scope because an annotation argument cannot safely reference
 * a member of the class it annotates. Bumping the schema means adding a
 * `Migration` and bumping this one value; nothing else carries a version.
 */
const val APP_DATABASE_VERSION: Int = 3

@Database(
    entities = [
        Plant::class,
        GrowTent::class,
        Protocol::class,
        ProtocolStage::class,
        StageEntry::class,
        GrowEvent::class,
        SuperCycleConfig::class,
        Achievement::class,
        Reminder::class,
        BreedingProject::class,
        BreedingCross::class
    ],
    version = APP_DATABASE_VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun plantDao(): PlantDao
    abstract fun tentDao(): GrowTentDao
    abstract fun protocolDao(): ProtocolDao
    abstract fun protocolStageDao(): ProtocolStageDao
    abstract fun stageEntryDao(): StageEntryDao
    abstract fun eventDao(): EventDao
    abstract fun superCycleDao(): SuperCycleDao
    abstract fun achievementDao(): AchievementDao
    abstract fun reminderDao(): ReminderDao
    abstract fun breedingDao(): BreedingDao
    abstract fun journalDao(): JournalDao

    companion object {
        /**
         * Room schema version, for callers that read it through the database
         * class rather than importing [APP_DATABASE_VERSION] directly.
         */
        const val VERSION: Int = APP_DATABASE_VERSION

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "trichome_app.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}