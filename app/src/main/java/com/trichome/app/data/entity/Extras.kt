package com.trichome.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A photoperiod configuration.
 *
 * Since v3 the owner is the **tent**: every plant inside the tent inherits it.
 * Keying it by plant was what allowed two plants under the same tent to report
 * different cycles, and [com.trichome.app.data.model.Protocol] kept a second copy
 * of `lightHours`/`darkHours` per plant to disagree with.
 *
 * [tentId] is nullable, and that is load-bearing rather than tidy. On the install
 * that motivated the migration, two of the three existing configs pointed at
 * plant ids that no longer existed: `ALTER TABLE ... ADD COLUMN tentId INTEGER
 * NOT NULL` fails the upgrade at that row and the app never opens. Those rows are
 * kept with `tentId = NULL` — the grower's decision — and are listed under
 * "Sin carpa" in the supercycle screen instead of being deleted or hidden.
 *
 * [plantId] is **obsolete but retained**. It records the plant a config was
 * written against before v3, which is the only thing left tying the orphaned rows
 * to anything, and keeping the column is what keeps the v3 change reversible: a
 * column dropped here cannot be restored by a later migration. New writes set
 * [tentId] and leave [plantId] null. Nothing should read it to decide which
 * config applies — that resolution lives in one place,
 * `SuperCycleRepository.getConfigForPlant`.
 */
@Entity(tableName = "super_cycle_configs", indices = [Index("tentId")])
data class SuperCycleConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tentId: Long? = null,
    // Obsolete since v3 and nullable for that reason; see the class KDoc.
    val plantId: Long? = null,
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