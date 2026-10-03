package com.trichome.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A journal row.
 *
 * ## The v5 VPD provenance columns, and why the history is not a new table
 *
 * `vpd` has existed since `MIGRATION_1_2` and it **is** the VPD history: the chart
 * needs `(timestamp, vpd)` per plant, and that is a query over this table, not a second
 * table. A separate history table would hold a copy of the same reading with the same
 * timestamp and would be a second truth about one number — the shape of the F1 boiling
 * point and the F2 temperature models, both of which this project already paid for.
 * `EventDao.watchVpdHistory` is the query that settles it, and it reads only this table.
 *
 * What the table genuinely could not answer is *where the number came from*. This screen
 * logs two different quantities into one `REAL` column: a reading off the grower's own
 * hygrometer, and a value this app derived from a temperature and a humidity. Before v5
 * there was nowhere to record which, so the chart could not keep them apart — the defect
 * `EstimatedClimate` exists to prevent, in a new place.
 *
 * So v5 adds exactly two nullable columns:
 *
 *  - [vpdSource] — `VpdProvenance.storageKey`. NULL on every pre-v5 row, and NULL means
 *    **unknown**, never "measured". Some pre-v5 numbers were typed by the grower and some
 *    were derived by an older build, and the column cannot tell which; defaulting NULL
 *    to measured would launder an unknown into a sensor reading.
 *  - [vpdLeafOffset] — the leaf-to-air offset a calculated VPD was produced with, °C.
 *    Stored so the number can be reproduced rather than trusted. Null for a measured
 *    reading, which has no offset behind it.
 *
 * Both are `ADD COLUMN` statements and neither rewrites a row. See `MIGRATION_4_5`.
 */
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
    /** Schema v5. See the class KDoc. Null means the origin is not recorded. */
    @ColumnInfo(defaultValue = "NULL") val vpdSource: String? = null,
    /** Schema v5. Leaf-to-air offset of a calculated VPD, °C. */
    @ColumnInfo(defaultValue = "NULL") val vpdLeafOffset: Float? = null,
    val trichomeMaturity: String? = null,
    val diagnosisResult: String? = null,
    val diagnosisCertainty: Float? = null,
    val imagePath: String? = null,
    val isActive: Boolean = true
)
