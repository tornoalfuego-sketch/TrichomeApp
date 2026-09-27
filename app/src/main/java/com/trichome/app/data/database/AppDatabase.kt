package com.trichome.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.trichome.app.data.dao.*
import com.trichome.app.data.entity.*

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
    version = 2,
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
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}