package com.trichome.app.data.export

import com.trichome.app.data.entity.GrowEvent
import com.trichome.app.data.entity.ProtocolStage
import com.trichome.app.data.model.GrowRange
import com.trichome.app.model.DataExportJson
import com.trichome.app.model.ExportDocument
import com.trichome.app.model.ExportScope
import com.trichome.app.model.VpdProvenance
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The VPD readings on their way out of the database and into the file.
 *
 * ## Why this exists when `DataExportTest` already mentions VPD provenance
 *
 * `DataExportTest.anUnrecordedVpdOriginIsExportedAsUnknown` asserts that
 * `VpdProvenance.fromStorageKey(null)` is `UNKNOWN`. That is a statement about an enum,
 * and it would still pass if the export wrote `MEASURED` for everything — the assertion
 * never touches the mapping that turns a `GrowEvent` into an `ExportEvent`, and that
 * mapping was file-private, behind a class needing a `Context` and ten DAOs to build.
 *
 * So the leak this project is most explicit about — "an export that silently upgrades an
 * unknown-origin reading to a sensor value is the same defect as a chart that does it" —
 * had no test on the path that could actually commit it. These do, in two halves:
 *
 *  1. **the mapping**, through `GrowEvent.toExport` (now `internal`, see
 *     [ExportMappers]), which is where the resolution happens;
 *  2. **the rendered file**, because a mapping that is right and a writer that drops the
 *     field produce the same pass on the first half.
 *
 * ## What this still cannot prove
 *
 * That `DataExportRepository` reads the rows it is asked to read, and that the file lands
 * on disk. That needs a database, and it has never been executed on hardware. What is
 * proven here is the byte a consumer of the file will see for each of the three
 * provenances.
 */
class ExportVpdProvenanceTest {

    private val instant = 1_753_000_000_000L

    /* ── The mapping ──────────────────────────────────────────────────────── */

    @Test
    fun aRowWithNoStoredOriginIsExportedAsUnknown() {
        val exported = event(vpdSource = null).toExport("Planta 1")

        assertEquals(VpdProvenance.UNKNOWN.storageKey, exported.vpdSource)
        assertFalse(
            "`MEASURED` is the one value a reader would take as a sensor reading",
            exported.vpdSource == VpdProvenance.MEASURED.storageKey
        )
    }

    @Test
    fun aBlankOriginIsAlsoUnknownRatherThanMeasured() {
        assertEquals(
            VpdProvenance.UNKNOWN.storageKey,
            event(vpdSource = "   ").toExport("Planta 1").vpdSource
        )
    }

    @Test
    fun anOriginWrittenBySomeFutureBuildIsUnknownRatherThanCrashingOrGuessing() {
        // A key this build has never heard of is not a measurement and is not a crash
        // inside a row mapper. It is `UNKNOWN`, which is the only honest reading of a
        // value this build cannot interpret.
        assertEquals(
            VpdProvenance.UNKNOWN.storageKey,
            event(vpdSource = "ESTIMATED_FROM_WEATHER").toExport("Planta 1").vpdSource
        )
    }

    @Test
    fun aMeasuredOriginSurvivesAsMeasured() {
        assertEquals(
            VpdProvenance.MEASURED.storageKey,
            event(vpdSource = "MEASURED").toExport("Planta 1").vpdSource
        )
    }

    @Test
    fun aCalculatedOriginKeepsItsLeafOffsetSoTheNumberCanBeReproduced() {
        val exported = event(vpdSource = "CALCULATED", vpdLeafOffset = 2.5f).toExport("Planta 1")

        assertEquals(VpdProvenance.CALCULATED.storageKey, exported.vpdSource)
        assertEquals(2.5f, exported.vpdLeafOffset!!, 1e-6f)
    }

    @Test
    fun theValueItselfIsNotTouchedOnTheWayOut() {
        // The provenance is resolved; the number is not. A mapping that also clamped or
        // rounded the value would make the file disagree with the chart.
        val exported = event(vpd = 0.94f, vpdSource = null).toExport("Planta 1")

        assertEquals(0.94f, exported.vpd!!, 1e-6f)
    }

    /* ── The file ─────────────────────────────────────────────────────────── */

    @Test
    fun theRenderedFileSaysUnknownForAReadingWithNoRecordedOrigin() {
        val rendered = render(
            listOf(
                event(id = 1L, vpd = 0.62f, vpdSource = null),
                event(id = 2L, vpd = 0.94f, vpdSource = "CALCULATED", vpdLeafOffset = 2.5f)
            )
        )
        val rows = rowsOf(rendered, "events")

        assertEquals("UNKNOWN", rows[0]["vpdSource"]?.jsonPrimitive?.content)
        assertEquals("CALCULATED", rows[1]["vpdSource"]?.jsonPrimitive?.content)
        assertEquals(
            "the number and its origin travel together: one is useless without the other",
            "0.6200",
            rows[0]["vpd"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun theFileNeverContainsTheWordMeasuredForAReadingThisBuildCannotVouchFor() {
        val rendered = render(
            listOf(
                event(id = 1L, vpd = 0.62f, vpdSource = null),
                event(id = 2L, vpd = 0.71f, vpdSource = "")
            )
        )

        assertFalse(
            "a pre-v5 row's origin must not be laundered into a sensor reading on the way out",
            rendered.contains("MEASURED")
        )
    }

    @Test
    fun aMeasuredReadingIsStillLabelledMeasured() {
        // The other direction: the fix must not flatten every origin into UNKNOWN, which
        // would be a different lie — refusing to say what the app does know.
        val rendered = render(listOf(event(id = 1L, vpd = 0.8f, vpdSource = "MEASURED")))

        assertTrue(rendered.contains("MEASURED"))
    }

    /* ── The stage target travels too ─────────────────────────────────────── */

    @Test
    fun aStagesDeclaredTargetIsExportedVerbatimAndItsAbsenceIsAnExplicitNull() {
        val rendered = DataExportJson.render(
            ExportDocument.of(
                scope = ExportScope.PLANT,
                generatedAt = instant,
                subjectName = "Planta 1",
                tents = emptyList(),
                plants = emptyList(),
                events = emptyList(),
                stageEntries = emptyList(),
                protocols = emptyList(),
                protocolStages = listOf(
                    stage(id = 1L, name = "Germinación", sortOrder = 0, target = null),
                    stage(id = 2L, name = "Floración", sortOrder = 2, target = GrowRange(1f, 1.4f))
                ).map { it.toExport("Exterior") },
                reminders = emptyList(),
                superCycles = emptyList()
            )
        )
        val rows = rowsOf(rendered, "protocolStages")

        assertEquals(
            "a stage with no target is `null`, not a band of zero",
            "null",
            rows[0]["vpdTarget"].toString()
        )
        assertEquals("1.0:1.4", rows[1]["vpdTarget"]?.jsonPrimitive?.content)
    }

    @Test
    fun aStagesTargetGoesThroughTheSameEncoderAsTheHeaderBand() {
        // One encoder, declared once. A second one for the stage column would be a
        // second representation of one declared band, and the two would then disagree
        // the first time either was changed.
        assertEquals(
            GrowRange.encode(GrowRange(0.8f, 1.2f)),
            stage(id = 1L, name = "Vegetativa", sortOrder = 1, target = GrowRange(0.8f, 1.2f))
                .toExport("Exterior").vpdTarget
        )
        assertNull(
            stage(id = 1L, name = "Vegetativa", sortOrder = 1, target = null)
                .toExport("Exterior").vpdTarget
        )
    }

    /* ── Fixtures ─────────────────────────────────────────────────────────── */

    private fun render(events: List<GrowEvent>): String =
        DataExportJson.render(
            ExportDocument.of(
                scope = ExportScope.PLANT,
                generatedAt = instant,
                subjectName = "Planta 1",
                tents = emptyList(),
                plants = emptyList(),
                // Entity to export to file in one path, so a writer that dropped the
                // provenance column would fail here rather than pass on the mapping alone.
                events = events.map { it.toExport("Planta 1") },
                stageEntries = emptyList(),
                protocols = emptyList(),
                protocolStages = emptyList(),
                reminders = emptyList(),
                superCycles = emptyList()
            )
        )

    private fun rowsOf(json: String, section: String) =
        (Json.parseToJsonElement(json).jsonObject[section] as JsonArray).map { it.jsonObject }

    private fun event(
        id: Long = 1L,
        vpd: Float? = 0.84f,
        vpdSource: String?,
        vpdLeafOffset: Float? = null
    ) = GrowEvent(
        id = id,
        plantId = 11L,
        eventType = "VPD",
        timestamp = instant,
        temperature = 24f,
        humidity = 60f,
        vpd = vpd,
        vpdSource = vpdSource,
        vpdLeafOffset = vpdLeafOffset
    )

    private fun stage(id: Long, name: String, sortOrder: Int, target: GrowRange?) =
        ProtocolStage(
            id = id,
            protocolId = 1L,
            stageName = name,
            durationDays = 30,
            sortOrder = sortOrder,
            vpdTarget = target
        )
}
