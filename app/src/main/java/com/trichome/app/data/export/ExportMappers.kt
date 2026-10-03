package com.trichome.app.data.export

import com.trichome.app.data.entity.GrowTent
import com.trichome.app.data.entity.Plant
import com.trichome.app.data.entity.Protocol
import com.trichome.app.data.entity.ProtocolStage
import com.trichome.app.data.entity.Reminder
import com.trichome.app.data.entity.StageEntry
import com.trichome.app.data.entity.SuperCycleConfig
import com.trichome.app.data.model.GrowRange
import com.trichome.app.model.ExportEvent
import com.trichome.app.model.ExportPlant
import com.trichome.app.model.ExportProtocol
import com.trichome.app.model.ExportProtocolStage
import com.trichome.app.model.ExportReminder
import com.trichome.app.model.ExportStageEntry
import com.trichome.app.model.ExportSuperCycle
import com.trichome.app.model.ExportTent
import com.trichome.app.model.VpdProvenance
import com.trichome.app.model.photoFileNameOf

/**
 * The entity-to-export mappings, one place each.
 *
 * ## Why these are not private to [DataExportRepository] any more
 *
 * They used to be file-private. That put the export's *decisions* — which columns are
 * published, what a band's stored form is, and above all what an unrecorded VPD origin
 * resolves to — behind a wall no JVM test could see, because the class that owns them
 * needs a `Context` and ten DAOs to construct.
 *
 * `DataExportTest` could therefore only assert that `VpdProvenance.fromStorageKey(null)`
 * is `UNKNOWN`, which is a statement about the enum. It never proved that the file a
 * grower takes away carries `UNKNOWN` rather than `MEASURED`. That is the leak this file
 * exists to close, and it is why these are `internal` rather than `private`: internal is
 * visible to the `test` source set, private is not.
 *
 * No behaviour moved with them. `ExportVpdProvenanceTest` covers the mapping that
 * resolves the provenance, and the rest is a straight column-for-column copy that the
 * rendered key order in `DataExportTest` already pins.
 *
 * Each is deliberately `when`-free: a field the export omits is a field nobody can
 * notice is missing, so the mapping states every field explicitly.
 */

/** The stored TEXT form of a declared band, or null. Never re-encoded, never defaulted. */
internal fun vpdBandText(range: GrowRange?): String? = GrowRange.encode(range)

internal fun GrowTent.toExport() = ExportTent(
    id = id,
    name = name,
    location = location,
    capacity = capacity,
    lightType = lightType,
    lightPowerWatts = lightPowerWatts,
    isActive = isActive
)

internal fun Plant.toExport(tentName: String?) = ExportPlant(
    id = id,
    name = name,
    tentName = tentName,
    sortOrder = sortOrder,
    growStartAt = growStartTimestamp,
    currentStage = currentStage,
    strain = strain,
    notes = notes,
    isActive = isActive,
    createdAt = createdAt
)

internal fun com.trichome.app.data.entity.GrowEvent.toExport(plantName: String) = ExportEvent(
    id = id,
    plantName = plantName,
    eventType = eventType,
    timestamp = timestamp,
    notes = notes,
    temperature = temperature,
    humidity = humidity,
    ph = ph,
    ec = ec,
    amount = amount,
    height = height,
    lampDistance = lampDistance,
    trainingType = trainingType,
    defoliationLevel = defoliationLevel,
    vpd = vpd,
    // NULL resolves to UNKNOWN rather than to "measured". A consumer of this file must
    // not read an unrecorded origin as a sensor reading; see VpdProvenance.
    vpdSource = VpdProvenance.fromStorageKey(vpdSource).storageKey,
    vpdLeafOffset = vpdLeafOffset,
    trichomeMaturity = trichomeMaturity,
    diagnosisResult = diagnosisResult,
    diagnosisCertainty = diagnosisCertainty,
    isActive = isActive,
    // The name, never the path.
    photoName = photoFileNameOf(imagePath)
)

internal fun StageEntry.toExport(plantName: String) = ExportStageEntry(
    id = id,
    plantName = plantName,
    stageName = stageName,
    enteredAt = enteredAt,
    exitedAt = exitedAt
)

internal fun Protocol.toExport(plantName: String) = ExportProtocol(
    id = id,
    plantName = plantName,
    name = name,
    lightHours = lightHours,
    darkHours = darkHours,
    presetType = presetType,
    cycleStartAt = cycleStartAt,
    isActive = isActive,
    // The stored TEXT form of each band, verbatim. Re-encoding through `GrowRange` here
    // would mean the export could disagree with the database, and a band the app can read
    // but the file cannot is worse than the raw form.
    vpdBand = vpdBandText(vpdBand),
    phRange = vpdBandText(phRange),
    ecRange = vpdBandText(ecRange),
    lightTempCelsius = lightTempCelsius,
    lightHumidityPercent = lightHumidityPercent,
    darkTempCelsius = darkTempCelsius,
    darkHumidityPercent = darkHumidityPercent,
    ppfd = ppfd,
    dli = dli,
    lightType = lightType,
    lampPowerWatts = lampPowerWatts,
    substrateType = substrateType,
    wateringStrategy = wateringStrategy,
    observations = observations
)

internal fun ProtocolStage.toExport(protocolName: String) = ExportProtocolStage(
    id = id,
    protocolName = protocolName,
    stageName = stageName,
    durationDays = durationDays,
    recurrenceIntervalDays = recurrenceIntervalDays,
    sortOrder = sortOrder,
    // Schema v6. The same stored TEXT form as the header's `vpdBand`, and null for a
    // stage written before the column existed — never "0.0:0.0", which would publish a
    // band of zero as something the grower declared.
    vpdTarget = vpdBandText(vpdTarget)
)

internal fun Reminder.toExport(plantName: String?) = ExportReminder(
    id = id,
    plantName = plantName,
    title = title,
    message = message,
    recurrenceType = recurrenceType,
    recurrenceIntervalDays = recurrenceIntervalDays,
    reminderTime = reminderTime,
    isActive = isActive
)

internal fun SuperCycleConfig.toExport(
    tentName: String?,
    plantName: String?
) = ExportSuperCycle(
    id = id,
    tentName = tentName,
    plantName = plantName,
    lightHours = lightHours,
    darkHours = darkHours,
    cycleStartAt = cycleStartAt,
    presetType = presetType
)
