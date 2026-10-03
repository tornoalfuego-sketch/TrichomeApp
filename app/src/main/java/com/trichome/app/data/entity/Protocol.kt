package com.trichome.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.trichome.app.data.model.GrowRange
import com.trichome.app.data.model.GrowRangeConverters

/**
 * A protocol is the header of a grow plan for a plant. Its stages are stored
 * as ordered [ProtocolStage] blocks (table `protocol_stages`).
 *
 * ## The v4 fields, and why they are all nullable
 *
 * The photoperiod columns above describe the machine the grow runs on: every
 * protocol has a light cycle, so they are `NOT NULL` with a default. Everything
 * below describes what the *grower intends to measure and hold*, and most of a
 * given protocol's fields are genuinely unknown when the protocol is created —
 * the pH band is decided when the first run is measured, and often never.
 *
 * So every v4 column is nullable with a null default. The alternative — a
 * plausible-looking `phLow = 5.5` on a protocol nobody has measured — is the
 * `EstimatedClimate` failure this project already documented: a number rendered
 * as a target when it is really a guess reads as a reading, and the UI then
 * shows a plant being held at a pH its grower never asked for. `null` means
 * "not set", and "not set" is what the screen says.
 *
 * @see GrowRange for why a band is one value rather than two float columns.
 */
@Entity(tableName = "protocols")
@TypeConverters(GrowRangeConverters::class)
data class Protocol(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantId: Long,
    val name: String,
    @ColumnInfo(defaultValue = "18") val lightHours: Int = 18,
    @ColumnInfo(defaultValue = "6") val darkHours: Int = 6,
    @ColumnInfo(defaultValue = "custom") val presetType: String = "custom",
    @ColumnInfo(defaultValue = "0") val cycleStartAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,

    // ── v4: declared agronomic targets (schema 4, MIGRATION_3_4) ──────────
    //
    // Grow-wide VPD band. This is deliberately ONE band for the whole grow and
    // not one per stage. Per-stage VPD belongs on `protocol_stages`, which this
    // schema phase does not touch: a stage-level column added here would be
    // written by no query and read by no screen — a field that lies about the
    // shape of the data. What lives here is the band the grow is *run at*,
    // which is a property of the setup and not of a phase of it.
    @ColumnInfo(defaultValue = "NULL") val vpdBand: GrowRange? = null,
    /** Nutrient solution pH band, 5.5-6.5 typical. */
    @ColumnInfo(defaultValue = "NULL") val phRange: GrowRange? = null,
    /** Nutrient solution electrical conductivity band, mS/cm. */
    @ColumnInfo(defaultValue = "NULL") val ecRange: GrowRange? = null,
    /** Air temperature under the lamps, °C. */
    @ColumnInfo(defaultValue = "NULL") val lightTempCelsius: Float? = null,
    /** Relative humidity under the lamps, percent. */
    @ColumnInfo(defaultValue = "NULL") val lightHumidityPercent: Float? = null,
    /** Air temperature during the dark period, °C. */
    @ColumnInfo(defaultValue = "NULL") val darkTempCelsius: Float? = null,
    /** Relative humidity during the dark period, percent. */
    @ColumnInfo(defaultValue = "NULL") val darkHumidityPercent: Float? = null,
    /** Photosynthetic photon flux density at the canopy, µmol/m²/s. */
    @ColumnInfo(defaultValue = "NULL") val ppfd: Float? = null,
    /** Daily light integral, mol/m²/d. */
    @ColumnInfo(defaultValue = "NULL") val dli: Float? = null,
    /** Grower's own words for the fixture — "LED", "HPS", "CMH", "LDP". */
    @ColumnInfo(defaultValue = "NULL") val lightType: String? = null,
    /** Installed lamp power, W. */
    @ColumnInfo(defaultValue = "NULL") val lampPowerWatts: Float? = null,
    /** "Coco", "Perlite", "Soil", "Rockwool", "Aeroponic". */
    @ColumnInfo(defaultValue = "NULL") val substrateType: String? = null,
    /** "Daily", "Every 2 days", "Run to drain", "Top drip". */
    @ColumnInfo(defaultValue = "NULL") val wateringStrategy: String? = null,
    /** Free-text agronomic notes for the protocol. */
    @ColumnInfo(defaultValue = "NULL") val observations: String? = null
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