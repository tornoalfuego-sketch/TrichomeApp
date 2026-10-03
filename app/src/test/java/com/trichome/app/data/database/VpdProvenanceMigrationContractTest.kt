package com.trichome.app.data.database

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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The contract between `MIGRATION_4_5`, the `GrowEvent` entity and the schema Room
 * actually exported for v5.
 *
 * ## Why this test exists, given there is already an instrumented one
 *
 * `MigrationTest` (androidTest) is the strong proof: it runs the migration against a real
 * SQLite file and Room's `onValidateSchema` compares the result with `5.json`. That needs a
 * device. This test is the part that does not.
 *
 * The failure it catches is specific and it is expensive. If a column is added to `GrowEvent`
 * and not to the migration — or to the migration and not to the entity — Room's schema
 * comparison fails on the user's next launch, at open time, before any screen draws. The
 * user's data is intact and the app is a stack trace, and the only place that is visible is on
 * hardware.
 *
 * So this test compares two artefacts that live in the repository:
 *
 *  - the exported `app/schemas/.../5.json`, written by KSP on the last build, which is what
 *    Room validates a live table against;
 *  - [GROW_EVENT_V5_ADDED_COLUMNS], the single list the migration executes.
 *
 * Nothing here asserts that a `Migration` object exists, which would pass whether or not the
 * migration works.
 *
 * ## What this cannot prove
 *
 * It cannot prove the statements *execute* — that needs SQLite, and SQLite needs a device
 * here. What it proves is that the two sides agree, which is the part a review can read.
 */
class VpdProvenanceMigrationContractTest {

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

    /** One field of the exported schema, by column name. */
    private fun field(version: Int, table: String, column: String): JsonObject {
        return entityFields(version, table).map { it.jsonObject }.firstOrNull {
            (it["columnName"] as JsonPrimitive).content == column
        } ?: throw AssertionError("schema $version.json `$table` has no column `$column`")
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString || it.content != "null" }?.content

    /* ── The baseline this migration starts from ───────────────────────────── */

    /**
     * v4's `grow_events` had exactly these 25 columns, pinned by name.
     *
     * Without this the rest of the test could pass against a `4.json` that had already
     * drifted, and the "nothing was rewritten" claim below would be compared against the
     * wrong baseline.
     */
    @Test
    fun versionFourGrowEventsHadExactlyTheOriginalTwentyFiveColumns() {
        val columns = entityFields(4, "grow_events").map {
            (it.jsonObject["columnName"] as JsonPrimitive).content
        }

        assertEquals(
            listOf(
                "id", "plantId", "groupId", "eventType", "timestamp", "notes",
                "temperature", "humidity", "ph", "ec", "nutrientN", "nutrientP",
                "nutrientK", "amount", "height", "lampDistance", "trainingType",
                "defoliationLevel", "vpd", "trichomeMaturity", "diagnosisResult",
                "diagnosisCertainty", "imagePath", "isActive"
            ),
            columns
        )
    }

    @Test
    fun theDatabaseVersionIsFive() {
        assertEquals(
            "APP_DATABASE_VERSION must match the schema Room exported and validates against",
            5,
            APP_DATABASE_VERSION
        )
        assertEquals(
            "AppDatabase.VERSION must not carry its own copy of the number",
            APP_DATABASE_VERSION,
            AppDatabase.VERSION
        )
        assertEquals(5, schema(5)["version"].let { (it as JsonPrimitive).content.toInt() })
    }

    /* ── The migration agrees with the exported schema ─────────────────────── */

    @Test
    fun everyColumnTheMigrationAddsExistsInTheExportedV5Schema() {
        val offenders = GROW_EVENT_V5_ADDED_COLUMNS.filter { addition ->
            runCatching { field(5, "grow_events", addition.columnName) }.isFailure
        }
        assertTrue(
            "the migration adds columns the v5 schema does not have: " +
                offenders.joinToString(", "),
            offenders.isEmpty()
        )
    }

    @Test
    fun everyV5ColumnIsNullableWithANullDefaultInTheExportedSchema() {
        GROW_EVENT_V5_ADDED_COLUMNS.forEach { addition ->
            val exported = field(5, "grow_events", addition.columnName)
            assertFalse(
                "`${addition.columnName}` must be nullable: a NOT NULL column with no " +
                    "value in the existing row fails the upgrade on that row",
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
    fun everyV5ColumnHasTheTypeTheMigrationDeclares() {
        GROW_EVENT_V5_ADDED_COLUMNS.forEach { addition ->
            val exported = field(5, "grow_events", addition.columnName)
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
     * Missing and extra are both failures and both crash a user's next launch, for opposite
     * reasons: a column the entity expects and the table lacks, or a column the table has and
     * the entity never declared. Asserting both halves is what stops this test passing on a
     * partial edit.
     */
    @Test
    fun theAddedColumnSetsAreIdenticalInBothDirections() {
        val inSchema = entityFields(5, "grow_events").map {
            (it.jsonObject["columnName"] as JsonPrimitive).content
        }
        val inV4 = entityFields(4, "grow_events").map {
            (it.jsonObject["columnName"] as JsonPrimitive).content
        }
        val inMigration = GROW_EVENT_V5_ADDED_COLUMNS.map { it.columnName }
        val addedInSchema = inSchema - inV4

        assertEquals(
            "the entity gained a column the migration never adds - Room will refuse to open " +
                "on the user's next launch",
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
     * Two `ADD COLUMN`s and nothing else.
     *
     * The data-loss budget is zero, so the shape of the migration is asserted directly rather
     * than inferred: a `DROP TABLE`, a `DELETE`, an `UPDATE` or a rebuild would all rewrite
     * the existing journal rows, and each is a statement this migration has no reason to
     * issue. `ALTER TABLE ... ADD COLUMN` is the only verb that adds structure while leaving
     * every existing cell exactly as it was.
     */
    @Test
    fun theMigrationOnlyEverAddsColumns() {
        assertEquals(
            "the migration adds exactly the two provenance columns and nothing else",
            2,
            GROW_EVENT_V5_ADDED_COLUMNS.size
        )
        assertEquals(
            "column names must be unique or the second ADD COLUMN throws on device",
            GROW_EVENT_V5_ADDED_COLUMNS.size,
            GROW_EVENT_V5_ADDED_COLUMNS.map { it.columnName }.distinct().size
        )
        GROW_EVENT_V5_STATEMENTS.forEach { statement ->
            val trimmed = statement.trim()
            assertTrue(
                "not an ADD COLUMN: $statement",
                trimmed.startsWith("ALTER TABLE `grow_events` ADD COLUMN `")
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
     * The added columns are named for what they record.
     *
     * Pinned by name because the chart's queries and the export both reference these strings.
     * A rename that left the queries alone would compile and then plot nothing.
     */
    @Test
    fun theAddedColumnsAreTheProvenancePair() {
        assertEquals(
            listOf("vpdSource", "vpdLeafOffset"),
            GROW_EVENT_V5_ADDED_COLUMNS.map { it.columnName }
        )
        assertEquals(
            listOf("TEXT", "REAL"),
            GROW_EVENT_V5_ADDED_COLUMNS.map { it.sqlType }
        )
    }

    /* ── Out of scope, and must stay out of scope ──────────────────────────── */

    /**
     * No history table, which is the whole design decision of this phase.
     *
     * `grow_events.vpd` has existed since `MIGRATION_1_2` and is the history: value, instant,
     * and after this migration the provenance and the offset, all on the row that already holds
     * them. A `vpd_history` table would be a second copy of one reading under one timestamp,
     * which is the shape of F1's boiling point and F2's temperature models.
     *
     * So this test fails if a table appears that no query in this phase reads.
     */
    @Test
    fun noHistoryTableWasAdded() {
        val tables = schema(5)["entities"]!!.jsonArray.map {
            (it.jsonObject["tableName"] as JsonPrimitive).content
        }
        val v4Tables = schema(4)["entities"]!!.jsonArray.map {
            (it.jsonObject["tableName"] as JsonPrimitive).content
        }

        assertEquals(
            "the entity list must not change in v5: the VPD history is a query over " +
                "grow_events, not a second table",
            v4Tables,
            tables
        )
        assertFalse(
            "a vpd_history table would duplicate one reading under one timestamp",
            tables.any { it.contains("vpd", ignoreCase = true) }
        )
    }

    /**
     * No table other than `grow_events` changed.
     *
     * `MIGRATION_4_5` is about `grow_events`; if the diff between `4.json` and `5.json` shows
     * anything else changing, something was edited that the migration does not account for and
     * the two would disagree on device.
     */
    @Test
    fun noTableOtherThanGrowEventsChangedInV5() {
        val v4Tables = schema(4)["entities"]!!.jsonArray.map {
            (it.jsonObject["tableName"] as JsonPrimitive).content
        }

        v4Tables.filter { it != "grow_events" }.forEach { table ->
            assertEquals(
                "`$table` changed in v5 but MIGRATION_4_5 does not touch it",
                entityFields(4, table),
                entityFields(5, table)
            )
        }
    }

    /**
     * The v4 protocol columns are still there, unchanged.
     *
     * F10a's fourteen columns were verified by `ProtocolMigrationContractTest` against
     * `4.json`. This asserts they survived v4 to v5 without a rewrite, which is the same
     * zero-data-loss argument applied one version later.
     */
    @Test
    fun theProtocolColumnsFromV4AreUntouchedInV5() {
        val v4Protocols = entityFields(4, "protocols").map {
            (it.jsonObject["columnName"] as JsonPrimitive).content
        }
        val v5Protocols = entityFields(5, "protocols").map {
            (it.jsonObject["columnName"] as JsonPrimitive).content
        }

        assertEquals(v4Protocols, v5Protocols)
        assertTrue(
            "F10a's columns must still exist after the v4 to v5 upgrade",
            v5Protocols.containsAll(
                listOf("vpdBand", "phRange", "ecRange", "lightTempCelsius", "observations")
            )
        )
    }

    /**
     * The stage tables keep the shape F10a asserted.
     *
     * `ProtocolMigrationContractTest.theStageTablesAreUntouchedByThisPhase` pinned these
     * between v3 and v4. A per-stage VPD band is real agronomy that belongs here, but no
     * screen in this phase reads one, so adding it would create a column that describes a
     * shape the data does not have.
     */
    @Test
    fun theStageTablesKeepTheirV4Shape() {
        listOf("protocol_stages", "stage_entries").forEach { table ->
            assertEquals(
                "`$table` must not change in v5; a per-stage VPD band is its own migration " +
                    "with its own screen",
                entityFields(4, table),
                entityFields(5, table)
            )
        }
    }

    /**
     * The `vpd` column F10a's phase inherited is still `REAL` and still nullable.
     *
     * It is the column the whole history reads, and `MIGRATION_1_2` added it without a
     * default. If a v5 edit had given it a default, every pre-v5 row would read back as a
     * fabricated 0.0 deficit rather than as "no VPD recorded", and the chart would draw a
     * flat line along the floor that no grower ever measured.
     */
    @Test
    fun theOriginalVpdColumnIsStillNullableWithNoDefault() {
        val exported = field(5, "grow_events", "vpd")

        assertFalse("`vpd` must stay nullable", exported["notNull"]?.jsonPrimitive?.booleanOrNull ?: false)
        assertEquals("REAL", (exported["affinity"] as JsonPrimitive).content)
        assertEquals(
            "`vpd` must keep no default: a fabricated 0.0 would be indistinguishable from " +
                "a measurement",
            null,
            exported.str("defaultValue")
        )
    }
}