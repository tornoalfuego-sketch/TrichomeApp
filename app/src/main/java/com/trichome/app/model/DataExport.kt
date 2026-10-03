package com.trichome.app.model

import com.trichome.app.data.entity.Achievement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale

/**
 * The data export: a documented, deterministic JSON snapshot of a plant or a whole
 * tent.
 *
 * ## Scope, and why PDF is not here
 *
 * The format is JSON and only JSON. There is no PDF writer on the Android platform and no
 * PDF library in this project's dependency graph, and adding one is the owner's decision,
 * not this phase's (`AGENTS.md` §11 and the project's own rule against unrequested
 * dependencies). So the export is JSON, stated plainly rather than as a "coming soon".
 *
 * ## What the export must not leak
 *
 * The file is written to a directory on a device, so anything in it can be read by
 * anything that can read that directory. The rule is that the export holds what the grower
 * put in plus the numbers the app derived from it, and nothing about the device or the
 * app's own storage:
 *
 *  - **`imagePath` is reduced to its file name.** The stored value is an absolute path into
 *    the app's private external storage, which reveals the app's directory layout and the
 *    device's user-visible folders. The photo's file name *is* something the grower put in
 *    — they named it — so the name is kept and the path is not.
 *  - **No `room_master_table`.** Room's internal identity hash is not the grower's data and
 *    not theirs to publish.
 *  - **No device identifiers, no build fingerprint, no locale, no timezone.** The only
 *    timestamp in the file is the moment the export was taken, which the grower asked for.
 *
 * Every other column of every entity is included verbatim, including the ones that look
 * like internals: `isActive`, `sortOrder`, `defoliationLevel`, `diagnosisCertainty`.
 * Excluding them would produce a file that does not round-trip into anything.
 *
 * ## Determinism
 *
 * The same data must produce byte-identical output, or "deterministic" means nothing and a
 * grower cannot diff two exports to see what changed. Three rules get that:
 *
 *  1. **Every list is sorted by its primary key**, in [ExportDocument.of], so Room's row
 *     order cannot leak in.
 *  2. **Every object writes its keys in a declared order**, not a `Map`'s iteration order.
 *     Each object is a `buildJsonObject` block with the keys written out in order.
 *  3. **Numbers are formatted in `Locale.US` with a fixed number of decimals.** A Spanish
 *     phone formats `0.84` as `0,84`, which is not a JSON number.
 *
 * [generatedAt] is a parameter rather than a clock read, which is both what makes the output
 * reproducible in a test and what keeps this file off the `ModelPurityTest` wall-clock guard.
 *
 * ## Documents
 *
 * `docs/DATA_EXPORT.md` describes the format field by field. The two must change together:
 * [DataExport.SCHEMA_VERSION] is what a future reader checks, so a format that changed
 * without a bump would be silently misread.
 */
object DataExport {

    /**
     * Version of the exported shape. Bumped whenever a field changes meaning, is removed,
     * or changes type.
     */
    const val SCHEMA_VERSION: Int = 1

    /** The `schema` value every export carries. */
    const val SCHEMA_ID: String = "trichome.export"

    /** File name stem, before the scope suffix and the instant. */
    const val FILE_STEM: String = "trichome-export"

    /** Extension of the produced file. */
    const val EXTENSION: String = "json"

    /**
     * Extension of the in-progress file.
     *
     * Different from [EXTENSION] on purpose: a half-written export must be distinguishable
     * from a complete one by name alone, before anyone opens it. `export.json` in progress is
     * `export.json.part`.
     */
    const val PARTIAL_EXTENSION: String = "part"

    /** Decimal places every exported real number is written with. */
    const val DECIMALS: Int = 4

    /** Date-time pattern for [fileNameFor]. */
    private const val FILE_DATE_TIME_PATTERN = "yyyyMMdd-HHmmss"

    /**
     * The file name for one export.
     *
     * Carries the scope and an instant, so two exports never collide and never overwrite one
     * another by accident. The instant is formatted in `Locale.US` digits, so the name does
     * not depend on the phone's locale.
     */
    fun fileNameFor(scope: ExportScope, zone: java.time.ZoneId, atMillis: Long): String {
        val stamp = java.time.format.DateTimeFormatter
            .ofPattern(FILE_DATE_TIME_PATTERN, Locale.US)
            .format(java.time.Instant.ofEpochMilli(atMillis).atZone(zone))
        return "$FILE_STEM-${scope.fileSuffix}-$stamp.$EXTENSION"
    }
}

/** What an export covers. */
enum class ExportScope(
    val labelEs: String,
    val descriptionEs: String,
    val fileSuffix: String
) {
    /** One plant and everything keyed to it. */
    PLANT(
        labelEs = "Una planta",
        descriptionEs = "La planta, su bitácora, sus cambios de etapa, sus protocolos y sus recordatorios.",
        fileSuffix = "planta"
    ),

    /** One tent and every plant inside it. */
    TENT(
        labelEs = "Una carpa entera",
        descriptionEs = "La carpa, todas sus plantas y todo lo que cuelga de cada una.",
        fileSuffix = "carpa"
    )
}

/** A tent, as exported. */
data class ExportTent(
    val id: Long,
    val name: String,
    val location: String,
    val capacity: Int,
    val lightType: String,
    val lightPowerWatts: Int,
    val isActive: Boolean
)

/** A plant, as exported. [tentName] resolves the tent for a human reader. */
data class ExportPlant(
    val id: Long,
    val name: String,
    val tentName: String?,
    val sortOrder: Int,
    val growStartAt: Long,
    val currentStage: String,
    val strain: String,
    val notes: String,
    val isActive: Boolean,
    val createdAt: Long
)

/**
 * A journal row, as exported.
 *
 * [photoName] is `GrowEvent.imagePath` reduced to its last segment. The stored path is
 * absolute and points into the app's own storage; the name is the grower's. See the class
 * KDoc on [DataExport].
 */
data class ExportEvent(
    val id: Long,
    val plantName: String,
    val eventType: String,
    val timestamp: Long,
    val notes: String?,
    val temperature: Float?,
    val humidity: Float?,
    val ph: Float?,
    val ec: Float?,
    val amount: Float?,
    val height: Float?,
    val lampDistance: Float?,
    val trainingType: String?,
    val defoliationLevel: Int?,
    val vpd: Float?,
    /**
     * `MEASURED`, `CALCULATED` or `UNKNOWN`.
     *
     * Exported rather than omitted for the same reason the chart draws it: a consumer of this
     * file must not read an unknown origin as a measurement. `null` before schema v5 resolves
     * to [VpdProvenance.UNKNOWN].
     */
    val vpdSource: String,
    /** The leaf offset a calculated VPD was produced with, in degrees Celsius. */
    val vpdLeafOffset: Float?,
    val trichomeMaturity: String?,
    val diagnosisResult: String?,
    val diagnosisCertainty: Float?,
    val isActive: Boolean,
    /** File name of the attached photo, or null. Never the path. */
    val photoName: String?
)

/** A stage transition, as exported. */
data class ExportStageEntry(
    val id: Long,
    val plantName: String,
    val stageName: String,
    val enteredAt: Long,
    val exitedAt: Long?
) {
    /** Whether this entry is still open, that is, the plant has not left this stage. */
    val isOpen: Boolean get() = exitedAt == null
}

/** A protocol header, as exported. Bands keep their stored TEXT form. */
data class ExportProtocol(
    val id: Long,
    val plantName: String,
    val name: String,
    val lightHours: Int,
    val darkHours: Int,
    val presetType: String,
    val cycleStartAt: Long,
    val isActive: Boolean,
    val vpdBand: String?,
    val phRange: String?,
    val ecRange: String?,
    val lightTempCelsius: Float?,
    val lightHumidityPercent: Float?,
    val darkTempCelsius: Float?,
    val darkHumidityPercent: Float?,
    val ppfd: Float?,
    val dli: Float?,
    val lightType: String?,
    val lampPowerWatts: Float?,
    val substrateType: String?,
    val wateringStrategy: String?,
    val observations: String?
)

/** A protocol block, as exported. */
data class ExportProtocolStage(
    val id: Long,
    val protocolName: String,
    val stageName: String,
    val durationDays: Int,
    val recurrenceIntervalDays: Int,
    val sortOrder: Int
)

/** A reminder, as exported. */
data class ExportReminder(
    val id: Long,
    val plantName: String?,
    val title: String,
    val message: String,
    val recurrenceType: String,
    val recurrenceIntervalDays: Int,
    val reminderTime: Long,
    val isActive: Boolean
)

/** A supercycle config, as exported. */
data class ExportSuperCycle(
    val id: Long,
    val tentName: String?,
    val plantName: String?,
    val lightHours: Int,
    val darkHours: Int,
    val cycleStartAt: Long,
    val presetType: String
)

/** An achievement, as exported. [isUnlocked] is the grower's own progress. */
data class ExportAchievement(
    val id: Long,
    val name: String,
    val description: String,
    val icon: String,
    val xpReward: Int,
    val isUnlocked: Boolean
)

/** A breeding project, as exported. */
data class ExportBreedingProject(
    val id: Long,
    val name: String,
    val motherId: String,
    val fatherId: String,
    val generation: String,
    val createdAt: Long,
    val status: String
)

/** A breeding cross, as exported. */
data class ExportBreedingCross(
    val id: Long,
    val projectName: String,
    val parent1: String,
    val parent2: String,
    val phenotypeScore: Float,
    val notes: String
)

/**
 * The whole snapshot, ready to be rendered.
 *
 * Every list is already sorted by its primary key when this is built — [of] is the only
 * construction point and it sorts. [DataExportJson.render] does not re-sort, because sorting
 * at render time would hide an unsorted builder from the tests.
 *
 * @param subjectName the tent's or the plant's name, so a file identifies itself in a folder
 *   of files.
 */
data class ExportDocument(
    val scope: ExportScope,
    val generatedAt: Long,
    val subjectName: String,
    val tents: List<ExportTent>,
    val plants: List<ExportPlant>,
    val events: List<ExportEvent>,
    val stageEntries: List<ExportStageEntry>,
    val protocols: List<ExportProtocol>,
    val protocolStages: List<ExportProtocolStage>,
    val reminders: List<ExportReminder>,
    val superCycles: List<ExportSuperCycle>,
    val achievements: List<ExportAchievement>,
    val breedingProjects: List<ExportBreedingProject>,
    val breedingCrosses: List<ExportBreedingCross>
) {
    /** Totals for the one-line summary printed after the export succeeds. */
    fun totalsEs(): String =
        "${plants.size} planta(s), ${events.size} evento(s) de bitácora, " +
            "${stageEntries.size} cambio(s) de etapa, ${protocols.size} protocolo(s)"

    companion object {

        /**
         * Builds the document, sorting every list by its primary key.
         *
         * Sorting here rather than in [DataExportJson.render] is what makes the byte-for-byte
         * determinism test meaningful: the test feeds the same rows in two different orders and
         * asserts one output, so the sort has to live on the path production code takes.
         */
        fun of(
            scope: ExportScope,
            generatedAt: Long,
            subjectName: String,
            tents: List<ExportTent>,
            plants: List<ExportPlant>,
            events: List<ExportEvent>,
            stageEntries: List<ExportStageEntry>,
            protocols: List<ExportProtocol>,
            protocolStages: List<ExportProtocolStage>,
            reminders: List<ExportReminder>,
            superCycles: List<ExportSuperCycle>,
            achievements: List<Achievement> = emptyList(),
            breedingProjects: List<ExportBreedingProject> = emptyList(),
            breedingCrosses: List<ExportBreedingCross> = emptyList()
        ): ExportDocument = ExportDocument(
            scope = scope,
            generatedAt = generatedAt,
            subjectName = subjectName,
            tents = tents.sortedBy { it.id },
            plants = plants.sortedBy { it.id },
            events = events.sortedBy { it.id },
            stageEntries = stageEntries.sortedBy { it.id },
            protocols = protocols.sortedBy { it.id },
            protocolStages = protocolStages.sortedBy { it.id },
            reminders = reminders.sortedBy { it.id },
            superCycles = superCycles.sortedBy { it.id },
            // Achievements arrive as entities so the caller cannot accidentally export a
            // subset; they are reduced and sorted here.
            achievements = achievements
                .map {
                    ExportAchievement(
                        id = it.id,
                        name = it.name,
                        description = it.description,
                        icon = it.icon,
                        xpReward = it.xpReward,
                        isUnlocked = it.isUnlocked
                    )
                }
                .sortedBy { it.id },
            breedingProjects = breedingProjects.sortedBy { it.id },
            breedingCrosses = breedingCrosses.sortedBy { it.id }
        )
    }
}

/**
 * The renderer: [ExportDocument] to JSON text.
 *
 * Written by hand rather than through `@Serializable` for one reason that is also the reason
 * the whole format is documented: **key order has to be the declared order.** A `@Serializable`
 * data class writes its properties in declaration order, which would work, but it would also
 * put a compiler-generated property order into a file format this project documents as stable,
 * and any later refactor that reorders two fields would silently change the format. Here the
 * order is written out in each builder block, and a test asserts the first key of every
 * object.
 *
 * `kotlinx.serialization-json` builds and escapes, because escaping a Spanish note containing a
 * quote, a backslash or a newline by hand is exactly the kind of thing that produces an
 * invalid file on a grower's device.
 */
object DataExportJson {

    /** Indentation of the rendered file. Two spaces; declared in the format doc. */
    const val INDENT: String = "  "

    /**
     * Renders [document] as pretty-printed JSON with a trailing newline.
     *
     * Trailing newline on purpose: a file without one is not text, and most editors then warn
     * about "no newline at end of file" on a grower's export.
     */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun render(document: ExportDocument): String = kotlinx.serialization.json.Json {
        // Both flags are experimental in kotlinx.serialization 1.7.3, and opting in here is
        // deliberate: this is the one place that decides the file's exact bytes, and having
        // the compiler hold the decision is worth more than the annotation's tidiness.
        prettyPrint = true
        prettyPrintIndent = INDENT
    }.encodeToString(
        kotlinx.serialization.json.JsonObject.serializer(),
        toJson(document)
    ) + "\n"

    /* ── The typed writers, in one place ────────────────────────────────────
     *
     * `JsonObjectBuilder.put` has no primitive overloads, so every field would otherwise be
     * `put("id", JsonPrimitive(tent.id))`. Routing the types through named helpers makes a
     * field readable at the call site and, more importantly, it is the single point where a
     * number becomes JSON, so [DataExport.DECIMALS] is applied once and a Spanish comma can
     * never reach the file.
     */

    private fun JsonObjectBuilder.putLong(key: String, value: Long?) = put(key, numberOrNull(value))

    private fun JsonObjectBuilder.putInt(key: String, value: Int?) = put(key, numberOrNull(value))

    private fun JsonObjectBuilder.putFloat(key: String, value: Float?) =
        put(key, value?.let { JsonPrimitive(formatDecimal(it)) } ?: JsonNull)

    private fun JsonObjectBuilder.putBool(key: String, value: Boolean) = put(key, JsonPrimitive(value))

    private fun JsonObjectBuilder.putText(key: String, value: String) = put(key, JsonPrimitive(value))

    /**
     * A nullable string, as an explicit JSON `null`.
     *
     * Distinct from [putText] on purpose: [putText] takes a non-null value, so a nullable
     * column cannot be written through it at all. The compiler refuses to let a `String?`
     * reach a field that is always present, and this is the only way to write one.
     */
    private fun JsonObjectBuilder.putNullable(key: String, value: String?) =
        put(key, value?.let { JsonPrimitive(it) } ?: JsonNull)

    /**
     * A nullable number, as an explicit JSON `null`.
     *
     * The distinction is not cosmetic. A consumer reading `"exitedAt": null` can tell that
     * this stage entry is still open; a consumer reading a key that is missing has to guess
     * between "never set" and "this build does not write it", and the two lead to different
     * behaviour on a grower's own history.
     */
    private fun numberOrNull(value: Long?): JsonElement =
        value?.let { JsonPrimitive(it) } ?: JsonNull

    /** As [numberOrNull], for the `Int` columns: `schemaVersion`, counts, hours. */
    private fun numberOrNull(value: Int?): JsonElement =
        value?.let { JsonPrimitive(it) } ?: JsonNull

    /**
     * A `Float` as JSON, with [DataExport.DECIMALS] decimals and a `.` separator.
     *
     * Two things are deliberate. First, `Locale.US`, because a Spanish phone formats `0.8400`
     * as `0,8400` and a comma inside a JSON number is a syntax error, not a number. Second, a
     * *fixed* number of decimals rather than [Float.toString], so the same reading always
     * produces the same bytes, which is what lets two exports of unchanged data be compared
     * byte for byte.
     */
    private fun formatDecimal(value: Float): String =
        String.format(Locale.US, "%.${DataExport.DECIMALS}f", value)

    /** The JSON tree for [document]. Public so a test can assert on structure. */
    fun toJson(document: ExportDocument): JsonObject = buildJsonObject {
        putText("schema", DataExport.SCHEMA_ID)
        putInt("schemaVersion", DataExport.SCHEMA_VERSION)
        putText("scope", document.scope.name)
        putText("scopeLabelEs", document.scope.labelEs)
        putLong("generatedAt", document.generatedAt)
        putText("subjectName", document.subjectName)

        put("tents", buildJsonArray {
            document.tents.forEach { tent ->
                add(buildJsonObject {
                    putLong("id", tent.id)
                    putText("name", tent.name)
                    putText("location", tent.location)
                    putInt("capacity", tent.capacity)
                    putText("lightType", tent.lightType)
                    putInt("lightPowerWatts", tent.lightPowerWatts)
                    putBool("isActive", tent.isActive)
                })
            }
        })

        put("plants", buildJsonArray {
            document.plants.forEach { plant ->
                add(buildJsonObject {
                    putLong("id", plant.id)
                    putText("name", plant.name)
                    // An explicit null rather than an omitted key, so "this plant has no tent"
                    // is readable as a value and not as a field that happens to be missing.
                    putNullable("tentName", plant.tentName)
                    putInt("sortOrder", plant.sortOrder)
                    putLong("growStartAt", plant.growStartAt)
                    putText("currentStage", plant.currentStage)
                    putText("strain", plant.strain)
                    putText("notes", plant.notes)
                    putBool("isActive", plant.isActive)
                    putLong("createdAt", plant.createdAt)
                })
            }
        })

        put("events", buildJsonArray {
            document.events.forEach { event ->
                add(buildJsonObject {
                    putLong("id", event.id)
                    putText("plantName", event.plantName)
                    putText("eventType", event.eventType)
                    putLong("timestamp", event.timestamp)
                    putNullable("notes", event.notes)
                    putFloat("temperature", event.temperature)
                    putFloat("humidity", event.humidity)
                    putFloat("ph", event.ph)
                    putFloat("ec", event.ec)
                    putFloat("amount", event.amount)
                    putFloat("height", event.height)
                    putFloat("lampDistance", event.lampDistance)
                    putNullable("trainingType", event.trainingType)
                    putInt("defoliationLevel", event.defoliationLevel)
                    putFloat("vpd", event.vpd)
                    putText("vpdSource", event.vpdSource)
                    putFloat("vpdLeafOffset", event.vpdLeafOffset)
                    putNullable("trichomeMaturity", event.trichomeMaturity)
                    putNullable("diagnosisResult", event.diagnosisResult)
                    putFloat("diagnosisCertainty", event.diagnosisCertainty)
                    putBool("isActive", event.isActive)
                    // The name only. See [ExportEvent.photoName].
                    putNullable("photoName", event.photoName)
                })
            }
        })

        put("stageEntries", buildJsonArray {
            document.stageEntries.forEach { entry ->
                add(buildJsonObject {
                    putLong("id", entry.id)
                    putText("plantName", entry.plantName)
                    putText("stageName", entry.stageName)
                    putLong("enteredAt", entry.enteredAt)
                    putLong("exitedAt", entry.exitedAt)
                    putBool("isOpen", entry.isOpen)
                })
            }
        })

        put("protocols", buildJsonArray {
            document.protocols.forEach { protocol ->
                add(buildJsonObject {
                    putLong("id", protocol.id)
                    putText("plantName", protocol.plantName)
                    putText("name", protocol.name)
                    putInt("lightHours", protocol.lightHours)
                    putInt("darkHours", protocol.darkHours)
                    putText("presetType", protocol.presetType)
                    putLong("cycleStartAt", protocol.cycleStartAt)
                    putBool("isActive", protocol.isActive)
                    putNullable("vpdBand", protocol.vpdBand)
                    putNullable("phRange", protocol.phRange)
                    putNullable("ecRange", protocol.ecRange)
                    putFloat("lightTempCelsius", protocol.lightTempCelsius)
                    putFloat("lightHumidityPercent", protocol.lightHumidityPercent)
                    putFloat("darkTempCelsius", protocol.darkTempCelsius)
                    putFloat("darkHumidityPercent", protocol.darkHumidityPercent)
                    putFloat("ppfd", protocol.ppfd)
                    putFloat("dli", protocol.dli)
                    putNullable("lightType", protocol.lightType)
                    putFloat("lampPowerWatts", protocol.lampPowerWatts)
                    putNullable("substrateType", protocol.substrateType)
                    putNullable("wateringStrategy", protocol.wateringStrategy)
                    putNullable("observations", protocol.observations)
                })
            }
        })

        put("protocolStages", buildJsonArray {
            document.protocolStages.forEach { stage ->
                add(buildJsonObject {
                    putLong("id", stage.id)
                    putText("protocolName", stage.protocolName)
                    putText("stageName", stage.stageName)
                    putInt("durationDays", stage.durationDays)
                    putInt("recurrenceIntervalDays", stage.recurrenceIntervalDays)
                    putInt("sortOrder", stage.sortOrder)
                })
            }
        })

        put("reminders", buildJsonArray {
            document.reminders.forEach { reminder ->
                add(buildJsonObject {
                    putLong("id", reminder.id)
                    putNullable("plantName", reminder.plantName)
                    putText("title", reminder.title)
                    putText("message", reminder.message)
                    putText("recurrenceType", reminder.recurrenceType)
                    putInt("recurrenceIntervalDays", reminder.recurrenceIntervalDays)
                    putLong("reminderTime", reminder.reminderTime)
                    putBool("isActive", reminder.isActive)
                })
            }
        })

        put("superCycles", buildJsonArray {
            document.superCycles.forEach { config ->
                add(buildJsonObject {
                    putLong("id", config.id)
                    putNullable("tentName", config.tentName)
                    putNullable("plantName", config.plantName)
                    putInt("lightHours", config.lightHours)
                    putInt("darkHours", config.darkHours)
                    putLong("cycleStartAt", config.cycleStartAt)
                    putText("presetType", config.presetType)
                })
            }
        })

        put("achievements", buildJsonArray {
            document.achievements.forEach { badge ->
                add(buildJsonObject {
                    putLong("id", badge.id)
                    putText("name", badge.name)
                    putText("description", badge.description)
                    putText("icon", badge.icon)
                    putInt("xpReward", badge.xpReward)
                    putBool("isUnlocked", badge.isUnlocked)
                })
            }
        })

        put("breedingProjects", buildJsonArray {
            document.breedingProjects.forEach { project ->
                add(buildJsonObject {
                    putLong("id", project.id)
                    putText("name", project.name)
                    putText("motherId", project.motherId)
                    putText("fatherId", project.fatherId)
                    putText("generation", project.generation)
                    putLong("createdAt", project.createdAt)
                    putText("status", project.status)
                })
            }
        })

        put("breedingCrosses", buildJsonArray {
            document.breedingCrosses.forEach { cross ->
                add(buildJsonObject {
                    putLong("id", cross.id)
                    putText("projectName", cross.projectName)
                    putText("parent1", cross.parent1)
                    putText("parent2", cross.parent2)
                    putFloat("phenotypeScore", cross.phenotypeScore)
                    putText("notes", cross.notes)
                })
            }
        })
    }
}

/** The Spanish the export screen prints, and the format's own documentation strings. */
object DataExportCopy {

    /** Panel title in Settings. */
    const val PANEL_TITLE_ES: String = "Exportar Datos"

    /** The format this phase ships. Stated, not implied. */
    const val FORMAT_ES: String = "JSON"

    /** Why there is no PDF. Said to the grower rather than left to be discovered. */
    const val NO_PDF_ES: String =
        "Solo se exporta en JSON. Esta app no incluye una librería de PDF y Android no " +
            "tiene un generador de PDF para contenido arbitrario, así que un PDF " +
            "requeriría añadir una dependencia que el propietario de la app todavía no " +
            "ha aprobado. El JSON contiene todos los mismos datos."

    /** Sentence above the two scope buttons. */
    const val SCOPE_HEADING_ES: String = "¿Qué quieres exportar?"

    /** Button that starts a single-plant export. */
    const val EXPORT_PLANT_ES: String = "Exportar una planta"

    /** Button that starts a whole-tent export. */
    const val EXPORT_TENT_ES: String = "Exportar una carpa"

    /** Label of the target directory shown before and after the write. */
    const val DESTINATION_LABEL_ES: String = "Se guarda en"

    /** Sentence shown while the export is being written. */
    const val WORKING_ES: String = "Preparando la exportación"

    /** Label of the dialog's confirm button. */
    const val CONFIRM_ES: String = "Exportar"

    /** Label of the dialog's dismiss button. */
    const val CANCEL_ES: String = "Cancelar"

    /** Sentence shown after a successful write, with the path. */
    fun successEs(path: String, totals: String): String =
        "Exportación terminada: $totals. Archivo: $path"

    /** Sentence shown when the write failed. Names the reason rather than "error". */
    fun failureEs(reason: String): String =
        "No se pudo escribir la exportación: $reason. No se ha dejado ningún archivo a " +
            "medias escribir, así que puedes intentarlo otra vez sin limpiar nada."

    /**
     * The privacy sentence, shown before the buttons.
     *
     * Says what is *not* in the file, because "no leak" only means something to a grower if
     * they are told where the boundary is.
     */
    const val PRIVACY_ES: String =
        "El archivo contiene solo lo que registraste, y las rutas internas de la app no. " +
            "No incluye identificadores del dispositivo ni las carpetas privadas donde la " +
            "app guarda tus fotos: de cada foto se guarda solo el nombre del archivo."

    /** Label of the plant picker. */
    const val PLANT_PICKER_LABEL_ES: String = "Planta"

    /** Label of the tent picker. */
    const val TENT_PICKER_LABEL_ES: String = "Carpa"

    /** Sentence when there is nothing to export yet. */
    const val NOTHING_TO_EXPORT_ES: String =
        "No hay nada que exportar todavía. Crea una planta y registra algún evento " +
            "para que el archivo tenga contenido."

    /**
     * Confirmation shown before the write starts.
     *
     * Names the scope and the subject, and states the atomicity, because that is the one
     * property of the write the grower cannot verify afterwards: a file on disk is either
     * complete or absent.
     */
    fun confirmEs(scope: ExportScope, subjectName: String): String =
        "Se exportará ${scope.descriptionEs.removePrefix("La ")} de $subjectName. " +
            "El archivo se escribe entero o no se escribe nada."
}

/** The photo file name, or null. See [DataExport] for why the path is dropped. */
fun photoFileNameOf(imagePath: String?): String? =
    imagePath?.trim()?.takeIf { it.isNotEmpty() }?.substringAfterLast('/')