package com.trichome.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A grow tent is a container for plants (plants hold the `tentId` FK with
 * SET_NULL; see [Plant]). Kept FK-free here so deletions cascade correctly
 * from the owning relationship.
 */
@Entity(tableName = "grow_tents")
data class GrowTent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val location: String = "",
    val capacity: Int = 1,
    val lightType: String = "LED",
    val lightPowerWatts: Int = 200,
    val isActive: Boolean = true
)