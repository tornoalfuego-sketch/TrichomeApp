package com.trichome.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A protocol is the header of a grow plan for a plant. Its stages are stored
 * as ordered [ProtocolStage] blocks (table `protocol_stages`).
 */
@Entity(tableName = "protocols")
data class Protocol(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantId: Long,
    val name: String,
    @ColumnInfo(defaultValue = "18") val lightHours: Int = 18,
    @ColumnInfo(defaultValue = "6") val darkHours: Int = 6,
    @ColumnInfo(defaultValue = "custom") val presetType: String = "custom",
    @ColumnInfo(defaultValue = "0") val cycleStartAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

/**
 * Stage block inside a protocol. Ordered by [sortOrder].
 */
@Entity(
    tableName = "protocol_stages",
    foreignKeys = [
        ForeignKey(
            entity = Protocol::class,
            parentColumns = ["id"],
            childColumns = ["protocolId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("protocolId")]
)
data class ProtocolStage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val protocolId: Long,
    val stageName: String,
    val durationDays: Int,
    val recurrenceIntervalDays: Int = 0,
    val sortOrder: Int = 0
)

/**
 * Transition log entry: when a plant entered (and possibly exited) a stage.
 */
@Entity(tableName = "stage_entries")
data class StageEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val protocolId: Long,
    val plantId: Long,
    val stageName: String,
    val enteredAt: Long = System.currentTimeMillis(),
    val exitedAt: Long? = null
)