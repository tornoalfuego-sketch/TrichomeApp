package com.trichome.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A plant belongs to one tent (or none). Deleting a tent sets `tentId` to
 * NULL (FK SET_NULL) so the plant survives and moves to "Sin carpa".
 */
@Entity(
    tableName = "plants",
    foreignKeys = [
        ForeignKey(
            entity = GrowTent::class,
            parentColumns = ["id"],
            childColumns = ["tentId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("tentId")]
)
data class Plant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val tentId: Long?,
    val sortOrder: Int = 0,
    val growStartTimestamp: Long = System.currentTimeMillis(),
    val currentStage: String = "seedling",
    val strain: String = "",
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)