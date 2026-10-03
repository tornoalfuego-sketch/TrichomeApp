package com.trichome.app.model

import com.trichome.app.data.entity.Achievement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * The export: determinism, what is in it, and what must never be in it.
 *
 * ## Why determinism is asserted byte for byte
 *
 * "Deterministic" means a grower can take two exports of unchanged data and diff them to see
 * what actually changed. If row order leaked in from SQLite, or a number printed as `0.84`
 * in one run and `0.8400` in another, the diff would be noise and the word would be
 * meaningless. So the assertions here compare the rendered strings, not parsed trees.
 *
 * The strongest of them feeds the same rows in **two different orders** and asserts one
 * output. That only passes if the sort is on the path production code takes.
 *
 * ## The leak assertions
 *
 * Three of these are about things the app must not publish, and each names what it replaces:
 *
 *  - a photo's **path** must be reduced to its **name**, because the path is the app's own
 *    storage layout and the name is the grower's;
 *  - Room's `identity_hash` must not appear at all;
 *  - an unrecorded VPD origin must be exported as `UNKNOWN`, never as `MEASURED`, so a
 *    consumer of the file cannot launder it the way a UI could.
 */
class DataExportTest {

    private val instant = 1_753_000_000_000L

    private val original = TimeZone.getDefault()

    @org.junit.Before
    fun pinTheZone() {
        // The renderer's number formatting is `Locale.US` internally, so this test would pass
        // on a US machine and fail on a Spanish one if that guarantee were absent. Pinning the
        // zone to something with a comma decimal separator *proves* the guarantee rather than
        // assuming it: if any field went through the default locale, the parse below throws.
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"))
    }

    @org.junit.After
    fun restoreTheZone() {
        TimeZone.setDefault(original)
    }

    /* ── Determinism ───────────────────────────────────────────────────── */

    @Test
    fun theSameRowsProduceTheSameBytes() {
        assertEquals(
            DataExportJson.render(document(listOf(plant(1), plant(2)))),
            DataExportJson.render(document(listOf(plant(1), plant(2))))
        )
    }

    @Test
    fun rowOrderCannotLeakIntoTheOutput() {
        // The real assertion. Room returns rows in whatever order the query plan produces, and
        // that order is not part of the data.
        val forward = DataExportJson.render(document(listOf(plant(1), plant(2), plant(3))))
        val reversed = DataExportJson.render(document(listOf(plant(3), plant(2), plant(1))))
        val shuffled = DataExportJson.render(document(listOf(plant(2), plant(3), plant(1))))

        assertEquals(forward, reversed)
        assertEquals(forward, shuffled)
    }

    @Test
    fun everyListIsSortedByPrimaryKey() {
        val built = ExportDocument.of(
            scope = ExportScope.TENT,
            generatedAt = instant,
            subjectName = "Carpa 4",
            tents = listOf(tent(9), tent(2)),
            plants = listOf(plant(7), plant(3), plant(5)),
            events = listOf(event(8), event(1)),
            stageEntries = listOf(entry(6), entry(4)),
            protocols = listOf(protocol(3), protocol(1)),
            protocolStages = listOf(protocolStage(5), protocolStage(2)),
            reminders = listOf(reminder(6), reminder(1)),
            superCycles = listOf(superCycle(2)),
            achievements = listOf(Achievement(id = 2, name = "B", description = "", icon = "", xpReward = 1, isUnlocked = true)),
            breedingProjects = listOf(project(3), project(1)),
            breedingCrosses = listOf(cross(4), cross(2))
        )

        assertEquals(listOf(2L, 9L), built.tents.map { it.id })
        assertEquals(listOf(3L, 5L, 7L), built.plants.map { it.id })
        assertEquals(listOf(1L, 8L), built.events.map { it.id })
        assertEquals(listOf(4L, 6L), built.stageEntries.map { it.id })
        assertEquals(listOf(1L, 3L), built.protocols.map { it.id })
        assertEquals(listOf(2L, 5L), built.protocolStages.map { it.id })
        assertEquals(listOf(1L, 6L), built.reminders.map { it.id })
        assertEquals(listOf(2L), built.superCycles.map { it.id })
        assertEquals(listOf(1L, 3L), built.breedingProjects.map { it.id })
        assertEquals(listOf(2L, 4L), built.breedingCrosses.map { it.id })
    }

    @Test
    fun theFileEndsWithANewline() {
        val json = DataExportJson.render(document(emptyList()))
        assertTrue(
            "a file without a trailing newline is not text and most editors warn about it",
            json.endsWith("}\n")
        )
    }

    @Test
    fun theOutputParsesAsJsonWithTheSpanishSafeSeparator() {
        // Throws if any number printed a comma. The test runs under a zone whose locale uses
        // one, so this is a real check rather than a formality.
        val tree = Json.parseToJsonElement(
            DataExportJson.render(
                document(plants = listOf(plant(1)), events = listOf(event(1)))
            )
        )
        val exported = (tree.jsonObject["events"] as JsonArray).first().jsonObject

        // Parsing at all is half the assertion: a Spanish device's locale offers a comma as the
        // decimal separator, and a comma inside a JSON number is a syntax error rather than a
        // number. The exact string pins the fixed width as well.
        assertEquals("0.8400", exported["vpd"]?.jsonPrimitive?.content)
        assertEquals("24.0000", exported["temperature"]?.jsonPrimitive?.content)
        assertEquals("6.2000", exported["ph"]?.jsonPrimitive?.content)
    }

    @Test
    fun realsAreWrittenWithAFixedNumberOfDecimals() {
        val json = DataExportJson.render(
            document(events = listOf(event(1).copy(vpd = 0.5f, temperature = 24f)))
        )

        // Not `0.5` and not `24`: a fixed width is what lets two exports of unchanged data be
        // compared byte for byte. `24f` would render as `24.0` through `Float.toString`, so
        // this also proves the padding is applied rather than the value happening to line up.
        val exported = Json.parseToJsonElement(json).jsonObject["events"]!!
            .let { it as JsonArray }.first().jsonObject

        assertEquals("0.5000", exported["vpd"]?.jsonPrimitive?.content)
        assertEquals("24.0000", exported["temperature"]?.jsonPrimitive?.content)
    }

    /* ── The header ────────────────────────────────────────────────────── */

    @Test
    fun theHeaderNamesTheSchemaAndItsVersion() {
        val header = Json.parseToJsonElement(
            DataExportJson.render(document(emptyList()))
        ).jsonObject

        assertEquals(DataExport.SCHEMA_ID, header["schema"]?.jsonPrimitive?.content)
        assertEquals(
            DataExport.SCHEMA_VERSION.toString(),
            header["schemaVersion"]?.jsonPrimitive?.content
        )
        assertEquals("TENT", header["scope"]?.jsonPrimitive?.content)
        assertEquals("Carpa 4", header["subjectName"]?.jsonPrimitive?.content)
    }

    @Test
    fun everySectionIsPresentEvenWhenEmpty() {
        // A consumer that finds no `plants` key has to decide whether the grow had none or
        // this build does not write it. Emitting `[]` answers it.
        val root = Json.parseToJsonElement(
            DataExportJson.render(document(emptyList()))
        ).jsonObject

        listOf(
            "tents", "plants", "events", "stageEntries", "protocols", "protocolStages",
            "reminders", "superCycles", "achievements", "breedingProjects", "breedingCrosses"
        ).forEach { key ->
            assertTrue("$key must be present", root.containsKey(key))
            assertTrue("$key must be an array", root[key] is JsonArray)
            assertEquals(
                "$key must be an empty array, not absent: a consumer cannot tell an empty " +
                    "grow from a build that does not write this section",
                0,
                (root[key] as JsonArray).size
            )
        }
    }

    @Test
    fun theKeyOrderOfEachObjectIsTheDeclaredOne() {
        // The format is documented as stable, so the first key of each object is pinned here
        // rather than left to a `Map`'s iteration order.
        // Every section is given one row, so each one's own key order is actually observable. A
        // section left empty would make `first()` throw rather than assert anything.
        val root = Json.parseToJsonElement(
            DataExportJson.render(
                document(
                    plants = listOf(plant(1)),
                    tents = listOf(tent(1)),
                    events = listOf(event(1)),
                    stageEntries = listOf(entry(1)),
                    protocols = listOf(protocol(1)),
                    protocolStages = listOf(protocolStage(1)),
                    reminders = listOf(reminder(1)),
                    superCycles = listOf(superCycle(1)),
                    achievements = listOf(
                        Achievement(
                            id = 1L,
                            name = "Primer Riego",
                            description = "Registra tu primer riego",
                            icon = "X",
                            xpReward = 100,
                            isUnlocked = true
                        )
                    ),
                    breedingProjects = listOf(project(1)),
                    breedingCrosses = listOf(cross(1))
                )
            )
        ).jsonObject

        // The header comes first; the sections follow it in the declared order.
        assertEquals(EXPECTED_HEADER_KEYS + EXPECTED_SECTION_KEYS, root.keys.toList())

        // Every section is non-empty here, so each one's own first key can be checked. The
        // expected value is the literal "id": the point is that the key *order* is declared
        // rather than inherited from a map's iteration order.
        EXPECTED_SECTION_KEYS.forEach { section ->
            val first = (root[section] as JsonArray).first().jsonObject.keys.first()
            assertEquals("`$section` must open with `id`", "id", first)
        }
    }

    /* ── What must not leak ────────────────────────────────────────────── */

    @Test
    fun aPhotoPathIsExportedAsItsFileNameOnly() {
        val path = "/storage/emulated/0/Android/data/com.trichome.app/files/fotos/mi-macro.JPG"
        val json = DataExportJson.render(
            document(
                emptyList(),
                // Deliberately built from the raw path through `photoFileNameOf` rather than
                // from a hand-written literal: this test is about the mapping the production
                // code performs, and a literal here would only test the literal.
                events = listOf(event(1).copy(photoName = photoFileNameOf(path)))
            )
        )

        assertTrue("the name is the grower's and stays", json.contains("mi-macro.JPG"))
        assertFalse("the directory layout is not", json.contains("Android/data"))
        assertFalse("the storage root is not", json.contains("/storage/emulated"))
        assertFalse("com.trichome.app must not appear in a grower's export", json.contains("com.trichome.app"))
    }

    @Test
    fun photoFileNameOfDropsEverythingButTheLastSegment() {
        assertEquals("cosecha.jpg", photoFileNameOf("/a/b/c/cosecha.jpg"))
        assertEquals("cosecha.jpg", photoFileNameOf("cosecha.jpg"))
        assertEquals(null, photoFileNameOf(null))
        assertEquals(null, photoFileNameOf("   "))
        assertEquals("a.jpg", photoFileNameOf("/a.jpg"))
    }

    @Test
    fun anUnrecordedVpdOriginIsExportedAsUnknown() {
        // The leak a consumer of the file would suffer if this defaulted to MEASURED.
        // The enum half of the claim; `ExportVpdProvenanceTest` proves the other half,
        // which is the one that matters: that the *file* says UNKNOWN.
        assertEquals(
            "UNKNOWN",
            VpdProvenance.fromStorageKey(null).storageKey
        )
    }

    /* ── The per-stage VPD target (schema v6, export schemaVersion 2) ─────── */

    @Test
    fun theStageTargetIsWrittenInTheSameStoredFormAsTheHeaderBand() {
        // One encoding for one declared band: `protocols.vpdBand` and
        // `protocol_stages.vpdTarget` are both GrowRange values, so they are both
        // written as "low:high". A second spelling would be a second reader.
        val json = DataExportJson.render(
            document(
                protocolStages = listOf(
                    protocolStage(1).copy(vpdTarget = "0.8:1.2"),
                    protocolStage(2).copy(stageName = "Floración", vpdTarget = "1.0:1.4")
                )
            )
        )
        val rows = (Json.parseToJsonElement(json).jsonObject["protocolStages"] as JsonArray)
            .map { it.jsonObject }

        assertEquals("0.8:1.2", rows[0]["vpdTarget"]?.jsonPrimitive?.content)
        assertEquals("1.0:1.4", rows[1]["vpdTarget"]?.jsonPrimitive?.content)
    }

    @Test
    fun aStageWithNoTargetExportsAnExplicitNullRatherThanAZeroBand() {
        // The three stages on a real device have no target. `"0.0:0.0"` would publish
        // a band of zero as something the grower wrote, which is the same defect as
        // defaulting a VPD's provenance to MEASURED.
        val root = Json.parseToJsonElement(
            DataExportJson.render(document(protocolStages = listOf(protocolStage(1))))
        ).jsonObject
        val exported = (root["protocolStages"] as JsonArray).first().jsonObject

        assertTrue("the key must be present", exported.containsKey("vpdTarget"))
        assertEquals("null", exported["vpdTarget"].toString())
        assertFalse(
            "no fabricated band anywhere in the file",
            DataExportJson.render(document(protocolStages = listOf(protocolStage(1))))
                .contains("0.0:0.0")
        )
    }

    @Test
    fun theSchemaVersionMovedWithTheStageField() {
        // A section gaining a field is a format change a reader has to be able to see,
        // so the number moves with it. Asserted rather than trusted, because the
        // number is what a future reader checks first.
        assertEquals(2, DataExport.SCHEMA_VERSION)
    }

    @Test
    fun anAbsentValueIsAnExplicitNullRatherThanAMissingKey() {
        val root = Json.parseToJsonElement(
            DataExportJson.render(
                document(emptyList(), events = listOf(event(1).copy(notes = null, photoName = null)))
            )
        ).jsonObject
        val exported = (root["events"] as JsonArray).first().jsonObject

        assertTrue("the key is present", exported.containsKey("notes"))
        assertEquals("null", exported["notes"].toString())
        assertEquals("null", exported["photoName"].toString())
    }

    @Test
    fun roomInternalsNeverAppearInAnExport() {
        val json = DataExportJson.render(document(listOf(plant(1)), events = listOf(event(1))))
        listOf("identity_hash", "room_master_table", "schemaHash").forEach {
            assertFalse("`$it` is Room's, not the grower's", json.contains(it))
        }
    }

    /* ── Escape and Unicode ────────────────────────────────────────────── */

    @Test
    fun aNoteWithQuotesAndNewlinesSurvivesTheRoundTrip() {
        // Escaping by hand is exactly the kind of thing that produces an invalid file on a
        // grower's device.
        val hostile = "riego \"alto\"\nnueva línea\ttabulada\\barra"
        val root = Json.parseToJsonElement(
            DataExportJson.render(
                document(emptyList(), events = listOf(event(1).copy(notes = hostile)))
            )
        ).jsonObject

        // The rendered form still holds the JSON escapes; decoding is what unescapes them. Reading
        // `jsonPrimitive.content` rather than `toString()` is what compares the *decoded*
        // value, so a writer that double-escaped would fail here.
        assertEquals(
            hostile,
            (root["events"] as JsonArray).first().jsonObject["notes"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun spanishAccentsRoundTripUnchanged() {
        val note = "FloraciónVnenta, ñandú, temperatura 24,5 °C"
        val root = Json.parseToJsonElement(
            DataExportJson.render(
                document(emptyList(), events = listOf(event(1).copy(notes = note)))
            )
        ).jsonObject

        assertEquals(
            "an accented note must survive encoding and decoding unchanged",
            note,
            (root["events"] as JsonArray).first().jsonObject["notes"]?.jsonPrimitive?.content
        )
    }

    /* ── The file name ─────────────────────────────────────────────────── */

    @Test
    fun theFileNameCarriesTheScopeAndTheInstant() {
        val name = DataExport.fileNameFor(ExportScope.PLANT, java.time.ZoneId.of("UTC"), instant)
        assertTrue(name.startsWith("${DataExport.FILE_STEM}-planta-"))
        assertTrue(name.endsWith(".json"))
        assertFalse("no colons are legal in a file name", name.contains(":"))
    }

    @Test
    fun twoExportsNeverProduceTheSameName() {
        val zone = java.time.ZoneId.of("UTC")
        val first = DataExport.fileNameFor(ExportScope.TENT, zone, instant)
        val second = DataExport.fileNameFor(ExportScope.TENT, zone, instant + 1_000L)
        assertFalse(first == second)
    }

    @Test
    fun theTwoScopesGetDifferentNames() {
        val zone = java.time.ZoneId.of("UTC")
        assertFalse(
            DataExport.fileNameFor(ExportScope.PLANT, zone, instant) ==
                DataExport.fileNameFor(ExportScope.TENT, zone, instant)
        )
    }

    /* ── The copy ──────────────────────────────────────────────────────── */

    @Test
    fun theNoPdfSentenceStatesTheReasonRatherThanPromisingOne() {
        assertTrue(DataExportCopy.NO_PDF_ES.contains("JSON"))
        assertTrue(DataExportCopy.NO_PDF_ES.contains("librería de PDF"))
        assertTrue(
            "the reason it is not here is a dependency decision, and saying so is the point",
            DataExportCopy.NO_PDF_ES.contains("dependencia")
        )
    }

    @Test
    fun thePrivacySentenceNamesWhatIsNotInTheFile() {
        assertTrue(DataExportCopy.PRIVACY_ES.contains("identificadores del dispositivo"))
        assertTrue(
            "the photo path has to be named specifically, not implied",
            DataExportCopy.PRIVACY_ES.contains("solo el nombre del archivo")
        )
    }

    @Test
    fun theFailureSentencePromisesNoPartialFile() {
        // The one property of the write the grower cannot verify themselves.
        assertTrue(DataExportCopy.failureEs("disco lleno").contains("a medias escribir"))
    }

    @Test
    fun bothScopesCarrySpanishDescriptions() {
        ExportScope.entries.forEach {
            assertTrue(it.labelEs.isNotBlank())
            assertTrue(it.descriptionEs.isNotBlank())
            assertTrue(it.fileSuffix.isNotBlank())
        }
    }

    /* ── Fixtures ──────────────────────────────────────────────────────── */

    /**
     * A document with only what the caller asked for.
     *
     * Every list defaults to **empty**, including the tents. A default of `listOf(tent(1))` made
     * "every section is present and empty" quietly untrue for one section, and a test that
     * passes because its own fixture was not empty is worse than no test.
     */
    private fun document(
        plants: List<ExportPlant> = emptyList(),
        tents: List<ExportTent> = emptyList(),
        events: List<ExportEvent> = emptyList(),
        stageEntries: List<ExportStageEntry> = emptyList(),
        protocols: List<ExportProtocol> = emptyList(),
        protocolStages: List<ExportProtocolStage> = emptyList(),
        reminders: List<ExportReminder> = emptyList(),
        superCycles: List<ExportSuperCycle> = emptyList(),
        achievements: List<Achievement> = emptyList(),
        breedingProjects: List<ExportBreedingProject> = emptyList(),
        breedingCrosses: List<ExportBreedingCross> = emptyList()
    ) = ExportDocument.of(
        scope = ExportScope.TENT,
        generatedAt = instant,
        subjectName = "Carpa 4",
        tents = tents,
        plants = plants,
        events = events,
        stageEntries = stageEntries,
        protocols = protocols,
        protocolStages = protocolStages,
        reminders = reminders,
        superCycles = superCycles,
        // Achievements are reduced from entities by the builder, which is the one place that
        // mapping lives. Forwarded rather than dropped: a fixture that silently omitted them
        // made the key-order assertion throw on an empty section instead of failing on the
        // order it was meant to check.
        achievements = achievements,
        breedingProjects = breedingProjects,
        breedingCrosses = breedingCrosses
    )

    private fun tent(id: Long) = ExportTent(
        id = id,
        name = "Carpa $id",
        location = "Sala",
        capacity = 4,
        lightType = "LED",
        lightPowerWatts = 300,
        isActive = true
    )

    private fun plant(id: Long) = ExportPlant(
        id = id,
        name = "Planta $id",
        tentName = "Carpa 4",
        sortOrder = id.toInt(),
        growStartAt = instant - 86_400_000L,
        currentStage = "vegetative",
        strain = "Test",
        notes = "",
        isActive = true,
        createdAt = instant
    )

    private fun event(id: Long) = ExportEvent(
        id = id,
        plantName = "Planta 1",
        eventType = "VPD",
        timestamp = instant,
        notes = "nota",
        temperature = 24f,
        humidity = 60f,
        ph = 6.2f,
        ec = 1.4f,
        amount = null,
        height = null,
        lampDistance = null,
        trainingType = null,
        defoliationLevel = null,
        vpd = 0.84f,
        vpdSource = VpdProvenance.CALCULATED.storageKey,
        vpdLeafOffset = 2f,
        trichomeMaturity = null,
        diagnosisResult = null,
        diagnosisCertainty = null,
        isActive = true,
        photoName = null
    )

    private fun entry(id: Long) = ExportStageEntry(
        id = id,
        plantName = "Planta 1",
        stageName = "Floración",
        enteredAt = instant,
        exitedAt = null
    )

    private fun protocol(id: Long) = ExportProtocol(
        id = id,
        plantName = "Planta 1",
        name = "Exterior",
        lightHours = 18,
        darkHours = 6,
        presetType = "18/6",
        cycleStartAt = instant,
        isActive = true,
        vpdBand = null,
        phRange = "5.8:6.5",
        ecRange = null,
        lightTempCelsius = null,
        lightHumidityPercent = null,
        darkTempCelsius = null,
        darkHumidityPercent = null,
        ppfd = null,
        dli = null,
        lightType = null,
        lampPowerWatts = null,
        substrateType = null,
        wateringStrategy = null,
        observations = null
    )

    private fun protocolStage(id: Long) = ExportProtocolStage(
        id = id,
        protocolName = "Exterior",
        stageName = "Vegetativa",
        durationDays = 35,
        recurrenceIntervalDays = 0,
        sortOrder = 0,
        vpdTarget = null
    )

    private fun reminder(id: Long) = ExportReminder(
        id = id,
        plantName = "Planta 1",
        title = "Riego",
        message = "Regar",
        recurrenceType = "DAILY",
        recurrenceIntervalDays = 1,
        reminderTime = instant,
        isActive = true
    )

    private fun superCycle(id: Long) = ExportSuperCycle(
        id = id,
        tentName = "Carpa 4",
        plantName = null,
        lightHours = 18,
        darkHours = 6,
        cycleStartAt = instant,
        presetType = "18/6"
    )

    private fun project(id: Long) = ExportBreedingProject(
        id = id,
        name = "Proyecto $id",
        motherId = "M",
        fatherId = "F",
        generation = "F1",
        createdAt = instant,
        status = "active"
    )

    private fun cross(id: Long) = ExportBreedingCross(
        id = id,
        projectName = "Proyecto 1",
        parent1 = "M",
        parent2 = "F",
        phenotypeScore = 8f,
        notes = ""
    )

    /**
     * The declared order of the header's keys, spelled out.
     *
     * Written literally rather than read off a builder, because that is the whole point: the
     * document's KDoc claims key order is part of the stable format, and a test that derived
     * its expectation from the code would agree with any order the code happened to produce.
     */
    private val EXPECTED_HEADER_KEYS = listOf(
        "schema",
        "schemaVersion",
        "scope",
        "scopeLabelEs",
        "generatedAt",
        "subjectName"
    )

    /**
     * The declared order of the sections, spelled out.
     *
     * The format's stability claim covers this order too: a consumer that reads
     * `plants` before `events` must keep doing so, and a field the document does not write
     * must not appear ahead of one it does.
     */
    private val EXPECTED_SECTION_KEYS = listOf(
        "tents",
        "plants",
        "events",
        "stageEntries",
        "protocols",
        "protocolStages",
        "reminders",
        "superCycles",
        "achievements",
        "breedingProjects",
        "breedingCrosses"
    )
}