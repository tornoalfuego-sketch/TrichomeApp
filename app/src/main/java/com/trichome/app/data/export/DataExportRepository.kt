package com.trichome.app.data.export

import com.trichome.app.data.dao.AchievementDao
import com.trichome.app.data.dao.BreedingDao
import com.trichome.app.data.dao.EventDao
import com.trichome.app.data.dao.GrowTentDao
import com.trichome.app.data.dao.PlantDao
import com.trichome.app.data.dao.ProtocolDao
import com.trichome.app.data.dao.ProtocolStageDao
import com.trichome.app.data.dao.ReminderDao
import com.trichome.app.data.dao.StageEntryDao
import com.trichome.app.data.dao.SuperCycleDao
import com.trichome.app.data.entity.GrowTent
import com.trichome.app.data.entity.Plant
import com.trichome.app.model.DataExport
import com.trichome.app.model.DataExportCopy
import kotlinx.coroutines.flow.first
import com.trichome.app.model.DataExportJson
import com.trichome.app.model.ExportAchievement
import com.trichome.app.model.ExportDocument
import com.trichome.app.model.ExportEvent
import com.trichome.app.model.ExportPlant
import com.trichome.app.model.ExportProtocol
import com.trichome.app.model.ExportProtocolStage
import com.trichome.app.model.ExportReminder
import com.trichome.app.model.ExportScope
import com.trichome.app.model.ExportStageEntry
import com.trichome.app.model.ExportSuperCycle
import com.trichome.app.model.ExportTent
import com.trichome.app.model.VpdProvenance
import com.trichome.app.model.photoFileNameOf
import java.io.File
import java.io.IOException

/**
 * Builds and writes the export.
 *
 * ## The whole path, and which half is tested where
 *
 * ```
 * ExportDocument.of(...)          model/DataExport.kt      pure, JVM-tested
 *   -> DataExportJson.render(...)  model/DataExport.kt      pure, JVM-tested
 *     -> ExportFileWriter          data/export/…           JVM-tested with real files
 *       -> this class             data/export/…           needs a device for the IO
 * ```
 *
 * Everything with a decision in it lives above the last line and is covered by
 * `DataExportTest` and `ExportFileWriterTest`. This class is the assembly — it resolves
 * the scope, reads the rows, renders, and hands the bytes to the writer — and it is the
 * part that cannot be exercised without hardware.
 *
 * ## Determinism across runs
 *
 * The same database produces the same bytes, because [DataExport.of] sorts every list by
 * primary key, [DataExportJson] writes keys in a declared order, and the numbers are
 * formatted in `Locale.US`. The one field that varies between two exports of unchanged
 * data is `generatedAt`, and the file name carries the same instant, so the two files are
 * both byte-comparable and never overwrite one another.
 *
 * ## Why nothing is read that the scope excludes
 *
 * A tent export covers the tent and every plant in it. A plant export covers one plant
 * and nothing else — not its tent, not the tent's other plants, not the tent's supercycle
 * config. A `plants.tentId` join would be the easy way to get the tent name for the copy,
 * and it is deliberately not used to *include* anything: including a neighbour's rows
 * because the neighbour shares a tent would put another grow's season in this grower's
 * export file.
 */
class DataExportRepository(
    private val plantDao: PlantDao,
    private val tentDao: GrowTentDao,
    private val eventDao: EventDao,
    private val protocolDao: ProtocolDao,
    private val protocolStageDao: ProtocolStageDao,
    private val stageEntryDao: StageEntryDao,
    private val reminderDao: ReminderDao,
    private val superCycleDao: SuperCycleDao,
    private val achievementDao: AchievementDao,
    private val breedingDao: BreedingDao
) {

    /**
     * One export's worth of rows, for [scope] on [subjectId].
     *
     * @param subjectId a plant id for [ExportScope.PLANT], a tent id for
     *   [ExportScope.TENT].
     * @param subjectName the name the file and the header carry. Passed rather than re-read
     *   because the picker already displayed it, and a second read can disagree with the row
     *   the grower confirmed.
     * @return the document, sorted and ready to render.
     */
    suspend fun buildDocument(
        scope: ExportScope,
        subjectId: Long,
        subjectName: String,
        generatedAt: Long
    ): ExportDocument = when (scope) {
        ExportScope.PLANT -> plantDocument(subjectId, subjectName, generatedAt)
        ExportScope.TENT -> tentDocument(subjectId, subjectName, generatedAt)
    }

    /**
     * The subject's name, for the file name and the confirmation.
     *
     * `null` when the row no longer exists — a plant deleted between the picker and the
     * press. [export] turns that into a Spanish refusal rather than writing a file named
     * after nothing.
     */
    suspend fun subjectName(scope: ExportScope, subjectId: Long): String? = when (scope) {
        ExportScope.PLANT -> plantDao.getPlantById(subjectId)?.name
        ExportScope.TENT -> tentDao.getTentById(subjectId)?.name
    }

    /**
     * Runs the whole export: build, render, write.
     *
     * @return [ExportResult.Success] with the file's absolute path, or
     *   [ExportResult.Failure] carrying a Spanish sentence the dialog shows as-is.
     */
    suspend fun export(
        scope: ExportScope,
        subjectId: Long,
        subjectName: String,
        generatedAt: Long,
        zone: java.time.ZoneId,
        directory: File
    ): ExportResult {
        val resolved = subjectName(scope, subjectId)
            ?: return ExportResult.Failure(DataExportCopy.NOTHING_TO_EXPORT_ES)

        // The picker already read the name to render the row it showed, so this write uses the
        // name that was on screen rather than a second read that could disagree with it.
        val document = buildDocument(scope, subjectId, subjectName.ifBlank { resolved }, generatedAt)
        val json = DataExportJson.render(document)
        val fileName = DataExport.fileNameFor(scope, zone, generatedAt)

        return try {
            val file = ExportFileWriter.writeExport(directory, fileName, json)
            ExportResult.Success(
                path = file.absolutePath,
                fileName = file.name,
                totalsEs = document.totalsEs(),
                document = document
            )
        } catch (e: java.io.IOException) {
            // The writer guarantees nothing was published at the target, so the message
            // can say that — the grower does not have to go looking for a partial file.
            ExportResult.Failure(DataExportCopy.failureEs(e.message ?: "error de escritura"))
        }
    }

    /* ── The two scopes ─────────────────────────────────────────────────── */

    private suspend fun plantDocument(
        plantId: Long,
        subjectName: String,
        generatedAt: Long
    ): ExportDocument {
        val plant = plantDao.getPlantById(plantId)
            ?: throw IOException(NO_PLANT_ES)
        // The tent name is copy, not content: a plant export does not carry the tent row.
        val tentName = plant.tentId?.let { tentDao.getTentById(it)?.name }
        val events = eventDao.getEventsForExport(plantId)
        val entries = stageEntryDao.getTimelineForExport(plantId)
        val protocols = protocolDao.getProtocolsByPlant(plantId).first()
        val protocolNames = protocols.associate { it.id to it.name }

        return ExportDocument.of(
            scope = ExportScope.PLANT,
            generatedAt = generatedAt,
            subjectName = subjectName,
            // No tent row: the plant export describes the plant, and the tent belongs to
            // the tent export.
            tents = emptyList(),
            plants = listOf(plant.toExport(tentName)),
            events = events.map { it.toExport(plant.name) },
            stageEntries = entries.map { it.toExport(plant.name) },
            protocols = protocols.map { it.toExport(plant.name) },
            protocolStages = protocols.flatMap { protocol ->
                protocolStageDao.getStagesByProtocol(protocol.id).map {
                    it.toExport(protocolNames[protocol.id] ?: protocol.name)
                }
            },
            reminders = reminderDao.getRemindersForPlant(plantId).map {
                it.toExport(plant.name)
            },
            // Not the tent's config: it belongs to the tent and to the tent export.
            superCycles = emptyList(),
            // Achievements are the grower's own progress and are not per-plant, so they
            // ride along with both scopes rather than being duplicated per plant inside
            // a tent export.
            achievements = achievementDao.getUnlockedAchievements()
        )
    }

    private suspend fun tentDocument(
        tentId: Long,
        subjectName: String,
        generatedAt: Long
    ): ExportDocument {
        val tent = tentDao.getTentById(tentId)
            ?: throw IOException(NO_TENT_ES)
        val plants = plantDao.getPlantsByTent(tentId)
        val plantNames = plants.associate { it.id to it.name }

        val events = plants.flatMap { plant ->
            eventDao.getEventsForExport(plant.id).map { it.toExport(plant.name) }
        }
        val entries = plants.flatMap { plant ->
            stageEntryDao.getTimelineForExport(plant.id).map { it.toExport(plant.name) }
        }
        val protocols = plants.flatMap { plant -> protocolDao.getProtocolsByPlant(plant.id).first() }
        val protocolNames = protocols.associate { it.id to it.name }

        return ExportDocument.of(
            scope = ExportScope.TENT,
            generatedAt = generatedAt,
            subjectName = subjectName,
            tents = listOf(tent.toExport()),
            plants = plants.map { it.toExport(tent.name) },
            events = events,
            stageEntries = entries,
            protocols = protocols.map { it.toExport(tentNames(it, plantNames)) },
            protocolStages = protocols.flatMap { protocol ->
                protocolStageDao.getStagesByProtocol(protocol.id).map {
                    it.toExport(protocolNames[protocol.id] ?: protocol.name)
                }
            },
            reminders = plants.flatMap { plant ->
                reminderDao.getRemindersForPlant(plant.id).map { it.toExport(plant.name) }
            },
            superCycles = listOfNotNull(superCycleDao.getConfigByTent(tentId)).map {
                it.toExport(tent.name, it.plantId?.let { id -> plantNames[id] })
            },
            achievements = achievementDao.getUnlockedAchievements()
        )
    }

    /**
     * A protocol's owning plant's name.
     *
     * The raw id is the fallback rather than an empty string, because `protocols.plantId`
     * is `NOT NULL` with **no foreign key** — the v2 fixture this project migrated from
     * contained a protocol pointing at `plantId = 0`, which is a real row, and rendering
     * it as a blank name would hide a dangling reference instead of showing it.
     */
    /**
     * A protocol's owning plant's name, for the `plantName` field.
     *
     * The raw id is the fallback rather than an empty string: `protocols.plantId` is
     * `NOT NULL` with **no foreign key**, and the v2 fixture this project migrated from
     * contained a protocol pointing at `plantId = 0`. That row is real, and rendering it
     * with a blank name would hide a dangling reference instead of showing it.
     */
    private fun tentNames(
        protocol: com.trichome.app.data.entity.Protocol,
        plantNames: Map<Long, String>
    ): String = plantNames[protocol.plantId] ?: protocol.plantId.toString()

    private companion object {
        /**
         * A missing row is a failure the caller turns into a Spanish sentence.
         *
         * Not written inline because an exception message is the one string on this path that
         * reaches the UI, and a grower's screen must not say "no existe la planta" in a
         * different register from the rest of the copy.
         */
        const val NO_PLANT_ES = "No existe la planta."
        const val NO_TENT_ES = "No existe la carpa."
    }
}

/** The result of one export. */
sealed interface ExportResult {

    /**
     * The file is on disk, complete.
     *
     * [path] is absolute and is shown to the grower, because a file in app-scoped
     * storage that nobody is told the location of is a file nobody finds.
     */
    data class Success(
        val path: String,
        val fileName: String,
        val totalsEs: String,
        val document: ExportDocument
    ) : ExportResult

    /** Nothing was written. [reasonEs] says why, in Spanish, ready to display. */
    data class Failure(val reasonEs: String) : ExportResult
}

/* ── Entity to export mappings ────────────────────────────────────────────
 *
 * One place each, so the export's shape is decided here rather than at fourteen call
 * sites. Each is deliberately a `when`-free straight mapping: a field the export omits
 * is a field nobody can notice is missing, and the tests assert the rendered key order.
 */

private fun GrowTent.toExport() = ExportTent(
    id = id,
    name = name,
    location = location,
    capacity = capacity,
    lightType = lightType,
    lightPowerWatts = lightPowerWatts,
    isActive = isActive
)

private fun Plant.toExport(tentName: String?) = ExportPlant(
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

private fun com.trichome.app.data.entity.GrowEvent.toExport(plantName: String) = ExportEvent(
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
    // Null resolves to UNKNOWN rather than to "measured". A consumer of this file must
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

private fun com.trichome.app.data.entity.StageEntry.toExport(plantName: String) = ExportStageEntry(
    id = id,
    plantName = plantName,
    stageName = stageName,
    enteredAt = enteredAt,
    exitedAt = exitedAt
)

private fun com.trichome.app.data.entity.Protocol.toExport(plantName: String) = ExportProtocol(
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

private fun vpdBandText(range: com.trichome.app.data.model.GrowRange?): String? =
    com.trichome.app.data.model.GrowRange.encode(range)

private fun com.trichome.app.data.entity.ProtocolStage.toExport(protocolName: String) =
    ExportProtocolStage(
        id = id,
        protocolName = protocolName,
        stageName = stageName,
        durationDays = durationDays,
        recurrenceIntervalDays = recurrenceIntervalDays,
        sortOrder = sortOrder
    )

private fun com.trichome.app.data.entity.Reminder.toExport(plantName: String?) = ExportReminder(
    id = id,
    plantName = plantName,
    title = title,
    message = message,
    recurrenceType = recurrenceType,
    recurrenceIntervalDays = recurrenceIntervalDays,
    reminderTime = reminderTime,
    isActive = isActive
)

private fun com.trichome.app.data.entity.SuperCycleConfig.toExport(
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