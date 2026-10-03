package com.trichome.app.data.database

import com.trichome.app.data.entity.Protocol
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
 * The contract between `MIGRATION_3_4`, the [Protocol] entity and the schema
 * Room actually exported for v4.
 *
 * ## Why this test exists at all, given there is already an instrumented one
 *
 * `MigrationTest` (androidTest) is the strong proof: it runs the migration
 * against a real SQLite file and Room's `onValidateSchema` compares the result
 * with `4.json`. That is the only proof of the whole chain, and it needs a
 * device. This test is the part that does not.
 *
 * The failure it exists to catch is specific and has a specific cost. If a
 * column is added to [Protocol] and not to the migration — or added to the
 * migration and not to the entity — Room's schema comparison fails on the
 * user's next launch, at open time, before any screen draws. The user's data is
 * intact but the app is a stack trace, and the only place that is visible is on
 * hardware. This test catches that disagreement between two artefacts in the
 * repository, with no hardware:
 *
 *  - the exported `app/schemas/.../4.json`, written by KSP on the last build,
 *    which is what Room validates a live table against;
 *  - [PROTOCOL_V4_ADDED_COLUMNS], the single list the migration executes.
 *
 * Everything asserted here is read from those two files. Nothing asserts that a
 * `Migration` object exists, which would pass whether or not the migration works.
 *
 * ## What this cannot prove, and what can
 *
 * It cannot prove the statements *execute* — that needs SQLite, and SQLite needs
 * a device here. `MigrationTest.migrate3To4...` covers that. What this test
 * proves is that the two sides agree, which is the part a review can read.
 */
class ProtocolMigrationContractTest {

    /* ── Schema loading ────────────────────────────────────────────────────── */

    private val schemaDir: File by lazy {
        firstExisting(
            listOf(
                File("schemas/com.trichome.app.data.database.AppDatabase"),
                File("app/schemas/com.trichome.app.data.database.AppDatabase")
            ),
            "the exported Room schema directory"
        )
    }

    private fun firstExisting(candidates: List<File>, what: String): File =
        candidates.firstOrNull { it.isDirectory }
            ?: throw AssertionError(
                "$what is missing: ${candidates.map { it.absolutePath }}. The schema is " +
                    "written by KSP on the last compile — run :app:compileDebugKotlin first."
            )

    private fun schema(version: Int): JsonObject {
        val file = File(schemaDir, "$version.json")
        assertTrue("$file not found; run :app:compileDebugKotlin to export it", file.isFile)
        val parsed = Json.parseToJsonElement(file.readText(Charsets.UTF_8))
        return parsed.jsonObject["database"]!!.jsonObject
    }

    private fun entityFields(version: Int, table: String): JsonArray {
        val database = schema(version)
        assertEquals(
            "schema $version.json declares version ${database["version"]}",
            version,
            (database["version"] as JsonPrimitive).content.toInt()
        )
        val entities = database["entities"] as JsonArray
        val match = entities.map { it.jsonObject }.firstOrNull {
            (it["tableName"] as JsonPrimitive).content == table
        } ?: throw AssertionError("schema $version.json has no `$table` entity")
        return match["fields"] as JsonArray
    }

    /** One field of the exported schema, by column name. */
    private fun field(version: Int, table: String, column: String): JsonObject {
        val fields = entityFields(version, table).map { it.jsonObject }
        return fields.firstOrNull {
            (it["columnName"] as JsonPrimitive).content == column
        } ?: throw AssertionError("schema $version.json `$table` has no column `$column`")
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString || it.content != "null" }?.content

    /* ── The baseline this migration starts from ───────────────────────────── */

    /**
     * The eight columns v3 had, pinned by name.
     *
     * Without this the rest of the test could pass against a `3.json` that had
     * already drifted, and the "nothing was rewritten" claim below would be
     * compared against the wrong baseline. This is the contract in
     * `AGENTS.md` §11 in test form: the pre-migration row has eight columns and
     * the test says so.
     */
    @Test
    fun versionThreeProtocolsHadExactlyTheEightOriginalColumns() {
        val columns = entityFields(3, "protocols").map {
            (it.jsonObject["columnName"] as JsonPrimitive).content
        }
        assertEquals(
            listOf(
                "id", "plantId", "name", "lightHours",
                "darkHours", "presetType", "cycleStartAt", "isActive"
            ),
            columns
        )
    }

    @Test
    fun theDatabaseVersionIsFour() {
        assertEquals(
            "APP_DATABASE_VERSION must match the schema Room exported and validates against",
            4,
            APP_DATABASE_VERSION
        )
        assertEquals(
            "AppDatabase.VERSION must not carry its own copy of the number",
            APP_DATABASE_VERSION,
            AppDatabase.VERSION
        )
        assertEquals(4, schema(4)["version"].let { (it as JsonPrimitive).content.toInt() })
    }

    /* ── The migration agrees with the exported schema ─────────────────────── */

    @Test
    fun everyColumnTheMigrationAddsExistsInTheExportedV4Schema() {
        val offenders = PROTOCOL_V4_ADDED_COLUMNS.filter { addition ->
            runCatching { field(4, "protocols", addition.columnName) }.isFailure
        }
        assertTrue(
            "the migration adds columns the v4 schema does not have: " +
                offenders.joinToString(", "),
            offenders.isEmpty()
        )
    }

    @Test
    fun everyV4ColumnIsNullableWithANullDefaultInTheExportedSchema() {
        PROTOCOL_V4_ADDED_COLUMNS.forEach { addition ->
            val exported = field(4, "protocols", addition.columnName)
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
    fun everyV4ColumnHasTheTypeTheMigrationDeclares() {
        PROTOCOL_V4_ADDED_COLUMNS.forEach { addition ->
            val exported = field(4, "protocols", addition.columnName)
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
     * Missing and extra are both failures and both crash a user's next launch,
     * for opposite reasons: a column the entity expects and the table lacks, or a
     * column the table has and the entity never declared. Asserting both halves
     * is what stops this test passing on a partial edit.
     */
    @Test
    fun theAddedColumnSetsAreIdenticalInBothDirections() {
        val inSchema = entityFields(4, "protocols").map {
            (it.jsonObject["columnName"] as JsonPrimitive).content
        }
        val inV3 = entityFields(3, "protocols").map {
            (it.jsonObject["columnName"] as JsonPrimitive).content
        }
        val inMigration = PROTOCOL_V4_ADDED_COLUMNS.map { it.columnName }

        val addedInSchema = inSchema - inV3

        assertEquals(
            "the entity gained a column the migration never adds — Room will refuse " +
                "to open on the user's next launch",
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
     * Fourteen `ADD COLUMN`s and nothing else.
     *
     * The data-loss budget is zero, so the shape of the migration is asserted
     * directly rather than inferred: a `DROP TABLE`, a `DELETE`, a `UPDATE` or a
     * rebuild would all rewrite the existing `protocols` row, and each is a
     * statement this migration has no reason to issue. `ALTER TABLE ... ADD
     * COLUMN` is the only verb that adds structure while leaving every existing
     * cell exactly as it was.
     */
    @Test
    fun theMigrationOnlyEverAddsColumns() {
        assertTrue(
            "the migration must add all fourteen columns",
            PROTOCOL_V4_ADDED_COLUMNS.size == 14
        )
        assertEquals(
            "column names must be unique or the second ADD COLUMN throws on device",
            PROTOCOL_V4_ADDED_COLUMNS.size,
            PROTOCOL_V4_ADDED_COLUMNS.map { it.columnName }.distinct().size
        )
        PROTOCOL_V4_STATEMENTS.forEach { statement ->
            val trimmed = statement.trim()
            assertTrue(
                "not an ADD COLUMN: $statement",
                trimmed.startsWith("ALTER TABLE `protocols` ADD COLUMN `")
            )
            listOf("DROP", "DELETE", "UPDATE", "INSERT", "RENAME").forEach { verb ->
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

    /* ── Out of scope, and must stay out of scope ──────────────────────────── */

    /**
     * The stage tables are a different schema phase.
     *
     * A per-stage VPD band is real agronomy and it belongs on `protocol_stages`.
     * Landing it on the header instead would create a column that no query can
     * populate and no screen can read — one that describes a shape the data does
     * not have. This test fails if a v4 column leaks into either stage table, so
     * the boundary is enforced rather than just documented.
     */
    @Test
    fun theStageTablesAreUntouchedByThisPhase() {
        listOf("protocol_stages", "stage_entries").forEach { table ->
            assertEquals(
                "`$table` must not change in v4; it is its own migration",
                entityFields(3, table),
                entityFields(4, table)
            )
        }
    }

    /**
     * No other table moved either.
     *
     * `MIGRATION_3_4` is about `protocols`; if the diff between `3.json` and
     * `4.json` shows anything else changing, something was edited that the
     * migration does not account for and the two would disagree on device.
     */
    @Test
    fun noTableOtherThanProtocolsChangedInV4() {
        val v3Tables = schema(3)["entities"]!!.jsonArray.map {
            (it.jsonObject["tableName"] as JsonPrimitive).content
        }
        val v4Tables = schema(4)["entities"]!!.jsonArray.map {
            (it.jsonObject["tableName"] as JsonPrimitive).content
        }
        assertEquals("the entity list changed in v4", v3Tables, v4Tables)

        v3Tables.filter { it != "protocols" }.forEach { table ->
            assertEquals(
                "`$table` changed in v4 but MIGRATION_3_4 does not touch it",
                entityFields(3, table),
                entityFields(4, table)
            )
        }
    }

    /* ── The entity itself ─────────────────────────────────────────────────── */

    @Test
    fun aProtocolBuiltFromDefaultsDeclaresNoTarget() {
        // The reason the columns are nullable: a new protocol has no declared
        // pH band, and the test that a default does not invent one is the same
        // reason the migration leaves the column NULL.
        val protocol = Protocol(plantId = 1L, name = "Exterior")
        assertEquals(null, protocol.phRange)
        assertEquals(null, protocol.ecRange)
        assertEquals(null, protocol.vpdBand)
        assertEquals(null, protocol.ppfd)
        assertEquals(null, protocol.dli)
        assertEquals(null, protocol.lightType)
        assertEquals(null, protocol.observations)
    }

    @Test
    fun theStageEntitiesAreNotRepointedAtProtocol() {
        // Guards the reasoning behind the phase split: `protocol_stages` keeps
        // exactly the columns it had, so this migration did not quietly widen
        // its scope by changing the entity without migrating the table.
        val stageColumns = entityFields(4, "protocol_stages").map {
            (it.jsonObject["columnName"] as JsonPrimitive).content
        }
        assertEquals(
            listOf(
                "id", "protocolId", "stageName",
                "durationDays", "recurrenceIntervalDays", "sortOrder"
            ),
            stageColumns
        )
    }
}