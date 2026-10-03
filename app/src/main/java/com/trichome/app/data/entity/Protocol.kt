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
    // Grow-wide VPD band: the band the grow is *run at*, which is a property of
    // the setup and not of a phase of it. A per-stage refinement lives on
    // `protocol_stages.vpdTarget` (schema 6) and is a different thing: this is
    // the envelope the whole grow stays inside, that one is what the current
    // stage aims at. Both nullable, so "no opinion" is `null` at either level
    // rather than a `0.0` band nobody typed.
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
 *
 * ## The v6 field: [vpdTarget]
 *
 * A stage is where a grow's environment actually changes. Vegetative is run
 * wetter and cooler than flowering, and the grower's own protocol blocks are
 * where that intent is written down — so the VPD target belongs here and not on
 * the [Protocol] header, which is the band the whole grow is *run at*.
 *
 * ## Why it is a target and not a reading
 *
 * The name says `Target`, and that is load-bearing. Every VPD number this app
 * knows is an offline estimate from latitude, altitude and season
 * ([AmbientClimate]) or a calculation from a temperature and a humidity the
 * grower typed — the climate card's own sentence says so. There is no sensor
 * behind any of them. So a column on a stage named `vpd` would read as an
 * observation of the room, which is exactly the `EstimatedClimate` defect one
 * level down. This is the band the grower is aiming at while the plant is in
 * this stage.
 *
 * ## Why there is no temperature or humidity target here
 *
 * The header already declares `lightTempCelsius`, `darkTempCelsius`,
 * `lightHumidityPercent` and `darkHumidityPercent` (schema 4). A stage-level
 * copy of them would be a second place the same fact is declared with no rule
 * saying which wins, and two sources of truth is the defect this project has
 * already paid for twice — F1's boiling point and F2's temperature models. So
 * the stage adds the one quantity the header has no way to express per phase,
 * and nothing else. If a screen later needs a stage temperature, the resolution
 * has to be stated first; a column that describes a shape the data does not have
 * is the thing `AGENTS.md` §11 forbids.
 *
 * ## Why it is nullable
 *
 * Same rule as the v4 columns and for the same reason. The three stages a real
 * protocol already has — `Germinación`, `Vegetativa`, `Floración` — were
 * written before this column existed, and `MIGRATION_5_6` leaves them NULL.
 * `null` reads as [ProtocolExtendedFields.SIN_DEFINIR] and never as
 * `0,00 – 0,00`, which would be a target the grower never chose.
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
@TypeConverters(GrowRangeConverters::class)
data class ProtocolStage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val protocolId: Long,
    val stageName: String,
    val durationDays: Int,
    val recurrenceIntervalDays: Int = 0,
    val sortOrder: Int = 0,
    // ── v6: the stage's own VPD target band (schema 6, MIGRATION_5_6) ──────
    @ColumnInfo(defaultValue = "NULL") val vpdTarget: GrowRange? = null
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