package com.trichome.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "super_cycle_configs")
data class SuperCycleConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantId: Long,
    val lightHours: Int,
    val darkHours: Int,
    val cycleStartAt: Long = System.currentTimeMillis(),
    val presetType: String = "custom"
)

@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val icon: String = "ic_leaf",
    val xpReward: Int = 100,
    val isUnlocked: Boolean = false
)

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantId: Long?,
    val title: String,
    val message: String = "",
    val recurrenceType: String = "weekly",
    val recurrenceIntervalDays: Int = 7,
    /** Millis-of-day of the first scheduled execution (default 09:00). */
    val reminderTime: Long = 9 * 3600000L,
    val isActive: Boolean = true
)

@Entity(tableName = "breeding_projects")
data class BreedingProject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val motherId: String = "",
    val fatherId: String = "",
    val generation: String = "F1",
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "active"
)

@Entity(
    tableName = "breeding_crosses",
    foreignKeys = [
        ForeignKey(
            entity = BreedingProject::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class BreedingCross(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val parent1: String = "",
    val parent2: String = "",
    val phenotypeScore: Float = 0f,
    val notes: String = ""
)