package com.trichome.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "grow_events",
    foreignKeys = [
        ForeignKey(
            entity = Plant::class,
            parentColumns = ["id"],
            childColumns = ["plantId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("plantId")]
)
data class GrowEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantId: Long,
    val groupId: String? = null,
    val eventType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String? = null,
    val temperature: Float? = null,
    val humidity: Float? = null,
    val ph: Float? = null,
    val ec: Float? = null,
    val nutrientN: Float? = null,
    val nutrientP: Float? = null,
    val nutrientK: Float? = null,
    val amount: Float? = null,
    val height: Float? = null,
    val lampDistance: Float? = null,
    val trainingType: String? = null,
    val defoliationLevel: Int? = null,
    val vpd: Float? = null,
    val trichomeMaturity: String? = null,
    val diagnosisResult: String? = null,
    val diagnosisCertainty: Float? = null,
    val imagePath: String? = null,
    val isActive: Boolean = true
)
