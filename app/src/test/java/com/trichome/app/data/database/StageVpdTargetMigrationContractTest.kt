package com.trichome.app.data.database

import com.trichome.app.data.entity.ProtocolStage
import com.trichome.app.data.model.GrowRange
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The contract between `MIGRATION_5_6`, the [ProtocolStage] entity and the schema Room
 * actually exported for v6.
 *
 * ## Why this file exists, given `MigrationTest` already covers the upgrade
 *
 * `MigrationTest` (androidTest) is the strong proof: it runs the migration against a real
 * SQLite file and Room's `onValidateSchema` compares the resulting table with `6.json`.
 * That needs a device.
 *
 * This is the part that does not, and the failure it exists to catch is specific and
 * expensive. If `vpdTarget` is on the entity and not in the migration — or in the
 * migration and not on the entity — Room's schema comparison fails on the grower's next
 * launch, at open time, before any screen draws. Their data is intact, the app is a stack
 * trace, and the only place that is visible is on hardware.
 *
 * So it compares two artefacts in the repository:
 *
 *  - `app/schemas/.../6.json`, written by KSP on the last build, which is what Room
 *    validates a live table against;
 *  - [PROTOCOL_STAGE_V6_ADDED_COLUMNS], the single list the migration executes.
 *
 * Nothing here asserts that a `Migration` object exists, which would pass whether or not
 * the migration works.
 *
 * ## What this cannot prove
 *
 * That the statements *execute*, and that the three stages on the real device survive the
 * upgrade. SQLite needs a device here, so that half is `MigrationTest.migrate5To6…`, and
 * as of this commit that file has still never been executed on hardware.
 */
class StageVpdTargetMigrationContractTest {

    /* ── Schema loading ────────────────────────────────────────────────────── */

    private val schemaDir: File by lazy {
        listOf(
            File("schemas/com.trichome.app.data.database.AppDatabase"),
            File("app/schemas/com.trichome.app.data.database.AppDatabase")
        ).firstOrNull { it.isDirectory }
            ?: throw AssertionError(
                "the exported Room schema directory is missing. The schema is written by " +
                    "KSP on the last compile - run :app:compileDebugKotlin first."
            )
    }

    private fun schema(version: Int): JsonObject {
        val file = File(schemaDir, "$version.json")
        assertTrue("$file not found; run :app:compileDebugKotlin to export it", file.isFile)
        return Json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject["database"]!!.jsonObject
    }

    private fun entityFields(version: Int, table: String): JsonArray {
        val database = schema(version)
        assertEquals(
            "schema $version.json declares version ${database["version"]}",
            version,
            (database["version"] as JsonPrimitive).content.toInt()
        )
        val match = (database["entities"] as JsonArray).map { it.jsonObject }.firstOrNull {
            (it["tableName"] as JsonPrimitive).content == table
        } ?: throw AssertionError("schema $version.json has no `$table` entity")
        return match["fields"] as JsonArray
    }

    private fun field(version: Int, table: String, column: String): JsonObject =
        entityFields(version, table).map { it.jsonObject }.firstOrNull {
            (it["columnName"] as JsonPrimitive).content == column
        } ?: throw AssertionError("schema $version.json `$table` has no column `$column`")

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString || it.content != "null" }?.content

    private fun columns(version: Int, table: String) =
        entityFields(version, table).map { (it.jsonObject["columnName"] as JsonPrimitive).content }

    /* ── The baseline this migration starts from ───────────────────────────── */

    /**
     * v5's `protocol_stages` had exactly these six columns, pinned by name.
     *
     * Without this the set-difference assertions below could pass against a `5.json` that
     * had already drifted, and the "nothing was rewritten" claim would be measured against
     * the wrong starting point.
     */
    @Test
    fun versionFiveProtocolStagesHadExactlyTheOriginalSixColumns() {
        assertEquals(
            listOf(
                "id", "protocolId", "stageName",
                "durationDays", "recurrenceIntervalDays", "sortOrder"
            ),
            columns(5, "protocol_stages")
        )
    }

    @Test
    fun theDatabaseVersionIsSix() {
        assertEquals(
            "APP_DATABASE_VERSION must match the schema Room exported and validates against",
            6,
            APP_DATABASE_VERSION
        )
        assertEquals(
            "AppDatabase.VERSION must not carry its own copy of the number",
            APP_DATABASE_VERSION,
            AppDatabase.VERSION
        )
        assertEquals(6, schema(6)["version"].let { (it as JsonPrimitive).content.toInt() })
    }

    @Test
    fun schemaFiveIsStillFiveAfterTheBump() {
        // The v4->v5 artefact is what Room validated that upgrade against. A regeneration
        // that rewrote it would invalidate `VpdProvenanceMigrationContractTest` without
        // anything in the source having changed.
        assertEquals(5, schema(5)["version"].let { (it as JsonPrimitive).content.toInt() })
    }

    /* ── The migration agrees with the exported schema ─────────────────────── */

    @Test
    fun everyColumnTheMigrationAddsExistsInTheExportedV6Schema() {
        val offenders = PROTOCOL_STAGE_V6_ADDED_COLUMNS.filter { addition ->
            runCatching { field(6, "protocol_stages", addition.columnName) }.isFailure
        }
        assertTrue(
            "the migration adds columns the v6 schema does not have: " +
                offenders.joinToString(", "),
            offenders.isEmpty()
        )
    }

    @Test
    fun everyV6ColumnIsNullableWithANullDefaultInTheExportedSchema() {
        PROTOCOL_STAGE_V6_ADDED_COLUMNS.forEach { addition ->
            val exported = field(6, "protocol_stages", addition.columnName)
            assertFalse(
                "`${addition.columnName}` must be nullable: a NOT NULL column with no " +
                    "value in an existing row fails the upgrade on that row",
                exported["notNull"]?.jsonPrimitive?.booleanOrNull ?: false
            )
            assertEquals(
                "`${addition.columnName}` default must be NULL in both the entity's " +
                    "@ColumnInfo and the migration DDL",
                "NULL",
                exported.str("defaultValue")
            )
        }
    }

    @Test
    fun everyV6ColumnHasTheTypeTheMigrationDeclares() {
        PROTOCOL_STAGE_V6_ADDED_COLUMNS.forEach { addition ->
            val exported = field(6, "protocol_stages", addition.columnName)
            assertEquals(
                "`${addition.columnName}` affinity disagrees with the migration DDL, " +
                    "which Room compares at open time",
                addition.sqlType,
                (exported["affinity"] as JsonPrimitive).content
            )
        }
    }

    /**
     * The two sides of the migration, each checked against the other.
     *
     * Missing and extra are both failures and both crash a launch, for opposite reasons: a
     * column the entity expects and the table lacks, or a column the table has and the
     * entity never declared. Asserting both halves is what stops this passing on a partial
     * edit.
     */
    @Test
    fun theAddedColumnSetsAreIdenticalInBothDirections() {
        val addedInSchema = columns(6, "protocol_stages") - columns(5, "protocol_stages")
        val inMigration = PROTOCOL_STAGE_V6_ADDED_COLUMNS.map { it.columnName }

        assertEquals(
            "the entity gained a column the migration never adds - Room will refuse to " +
                "open on the grower's next launch",
            addedInSchema.sorted(),
            inMigration.sorted()
        )
        assertEquals(
            "the migration adds a column the entity does not declare",
            inMigration.sorted(),
            addedInSchema.sorted()
        )
    }

    /* ── The migration cannot rewrite a row ────────────────────────────────── */

    /**
     * One `ADD COLUMN` and nothing else.
     *
     * The data-loss budget is zero, so the shape of the migration is asserted directly
     * rather than inferred: a `DROP TABLE`, a `DELETE`, an `UPDATE`, an `INSERT` or a
     * `RENAME` would each rewrite the existing stage rows, and none of them is a verb this
     * migration has any reason to issue. `ALTER TABLE ... ADD COLUMN` is the only one that
     * adds structure while leaving every existing cell exactly as it was, and a rebuild
     * would additionally put every stage id through a temporary table.
     */
    @Test
    fun theMigrationOnlyEverAddsColumns() {
        assertEquals(
            "the migration adds exactly the per-stage VPD band and nothing else",
            1,
            PROTOCOL_STAGE_V6_ADDED_COLUMNS.size
        )
        assertEquals(
            "column names must be unique or the second ADD COLUMN throws on device",
            PROTOCOL_STAGE_V6_ADDED_COLUMNS.size,
            PROTOCOL_STAGE_V6_ADDED_COLUMNS.map { it.columnName }.distinct().size
        )
        PROTOCOL_STAGE_V6_STATEMENTS.forEach { statement ->
            val trimmed = statement.trim()
            assertTrue(
                "not an ADD COLUMN: $statement",
                trimmed.startsWith("ALTER TABLE `protocol_stages` ADD COLUMN `")
            )
            listOf("DROP", "DELETE", "UPDATE", "INSERT", "RENAME", "CREATE").forEach { verb ->
                assertFalse(
                    "`$verb` in a zero-data-loss migration: $statement",
                    trimmed.contains(verb)
                )
            }
            assertTrue(
                "every added column declares DEFAULT NULL so it matches the entity: $statement",
                trimmed.endsWith(" DEFAULT NULL")
            )
        }
    }

    /**
     * The one column, named for what it records.
     *
     * Pinned because the export's key, the card's row and the contract test in
     * `DataExportTest` all reference this string. A rename that left them alone would
     * compile and then export nothing.
     */
    @Test
    fun theAddedColumnIsTheStageVpdTarget() {
        assertEquals(listOf("vpdTarget"), PROTOCOL_STAGE_V6_ADDED_COLUMNS.map { it.columnName })
        assertEquals(listOf("TEXT"), PROTOCOL_STAGE_V6_ADDED_COLUMNS.map { it.sqlType })
    }

    /**
     * The column is not called `vpd`.
     *
     * The name is the whole honesty mechanism for a declared target. `grow_events.vpd` is
     * a reading and this is not one: every VPD this app knows is an offline estimate from
     * latitude or a calculation from two typed numbers, and the climate card says so on
     * every card. A stage column named `vpd` would be read as an observation of the room.
     */
    @Test
    fun theColumnIsNamedAsATargetAndNotAsAReading() {
        val name = PROTOCOL_STAGE_V6_ADDED_COLUMNS.single().columnName

        assertEquals("vpdTarget", name)
        assertTrue(
            "`$name` must not be the bare `vpd` column name",
            name != "vpd"
        )
        assertTrue(
            "and it must not be the header's `vpdBand` either: two tables, two different " +
                "things, and one name for both would hide which is which",
            name != "vpdBand"
        )
    }

    /* ── Out of scope, and must stay out of scope ──────────────────────────── */

    /**
     * No stage temperature or humidity target.
     *
     * This is the shape decision of the phase, asserted rather than only documented. The
     * `protocols` header already declares `lightTempCelsius`, `darkTempCelsius`,
     * `lightHumidityPercent` and `darkHumidityPercent`, so a stage-level copy would be a
     * second place the same fact is declared with no rule saying which wins — two sources
     * of truth, which is the F1 boiling point and the F2 temperature models.
     */
    @Test
    fun noStageTemperatureOrHumidityTargetWasAdded() {
        listOf(
            "tempCelsius", "temperatureCelsius", "lightTempCelsius", "darkTempCelsius",
            "humidityPercent", "lightHumidityPercent", "darkHumidityPercent",
            "phRange", "ecRange", "ppfd", "dli"
        ).forEach { column ->
            assertFalse(
                "`protocol_stages.$column` would be a second source for a target the " +
                    "header already declares",
                columns(6, "protocol_stages").contains(column)
            )
        }
    }

    @Test
    fun noNewTableWasAdded() {
        val tables = schema(6)["entities"]!!.jsonArray.map {
            (it.jsonObject["tableName"] as JsonPrimitive).content
        }

        assertEquals(
            "the entity list must not change in v6: the per-stage band is a column on an " +
                "existing table",
            schema(5)["entities"]!!.jsonArray.map {
                (it.jsonObject["tableName"] as JsonPrimitive).content
            },
            tables
        )
        assertFalse(
            "a stage_targets table would duplicate one declared band per row",
            tables.any { it.contains("target", ignoreCase = true) }
        )
    }

    @Test
    fun noTableOtherThanProtocolStagesChangedInV6() {
        schema(5)["entities"]!!.jsonArray.map {
            (it.jsonObject["tableName"] as JsonPrimitive).content
        }.filter { it != "protocol_stages" }.forEach { table ->
            assertEquals(
                "`$table` changed in v6 but MIGRATION_5_6 does not touch it",
                entityFields(5, table),
                entityFields(6, table)
            )
        }
    }

    /**
     * F10a's fourteen protocol columns and F10b's two provenance columns are untouched.
     *
     * Both are owned by their own contract tests against their own schema file. This
     * asserts they survived v5 to v6 without a rewrite, which is the same zero-data-loss
     * argument applied one version later.
     */
    @Test
    fun theColumnsFromV4AndV5AreUntouchedInV6() {
        assertEquals(columns(5, "protocols"), columns(6, "protocols"))
        assertEquals(columns(5, "grow_events"), columns(6, "grow_events"))

        assertTrue(
            "F10a's declared targets must still exist",
            columns(6, "protocols").containsAll(
                listOf("vpdBand", "phRange", "ecRange", "lightTempCelsius", "observations")
            )
        )
        assertTrue(
            "F10b's provenance pair must still exist",
            columns(6, "grow_events").containsAll(listOf("vpdSource", "vpdLeafOffset"))
        )
    }

    @Test
    fun stageEntriesKeepTheirShape() {
        // The timeline references stages by NAME, not by id, so it is unaffected by this
        // migration either way. Asserted so a future phase that adds an id reference here
        // knows it has to say what happens to the rows that have no id.
        assertEquals(entityFields(5, "stage_entries"), entityFields(6, "stage_entries"))
    }

    /* ── The entity itself ─────────────────────────────────────────────────── */

    @Test
    fun aStageBuiltFromDefaultsDeclaresNoTarget() {
        // The reason the column is nullable and the reason the migration leaves it NULL:
        // a new stage has no opinion about VPD, and this is the same assertion in Kotlin
        // that the migration makes in SQL.
        val fresh = ProtocolStage(protocolId = 1L, stageName = "Vegetativa", durationDays = 35)
        assertNull(fresh.vpdTarget)
    }

    @Test
    fun aStageCarriesTheSameBandTypeAsTheHeader() {
        // One band type, one converter pair, one encoding. A second type would be a second
        // representation of the same declared band, and the two would drift apart the
        // first time either was edited.
        val header = com.trichome.app.data.entity.Protocol(plantId = 1L, name = "Exterior")
        val stage = ProtocolStage(
            protocolId = 1L,
            stageName = "Floración",
            durationDays = 56,
            vpdTarget = GrowRange(1f, 1.4f)
        )

        assertEquals(GrowRange::class, stage.vpdTarget!!::class)
        assertEquals(GrowRange.encode(GrowRange(1f, 1.4f)), GrowRange.encode(stage.vpdTarget))
        assertNull(header.vpdBand)
    }

    @Test
    fun theExistingSixColumnsKeepTheirOrder() {
        // Column order is not something Room validates, but it is what a raw-SQL reader of
        // the file sees, and the instrumented test asserts positions by index.
        assertEquals(
            listOf(
                "id", "protocolId", "stageName",
                "durationDays", "recurrenceIntervalDays", "sortOrder", "vpdTarget"
            ),
            columns(6, "protocol_stages")
        )
    }
}
