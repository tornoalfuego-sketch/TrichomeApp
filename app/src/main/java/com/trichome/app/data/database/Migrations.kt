package com.trichome.app.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit migration from schema v1 to v2.
 *
 * v1 (first internal layout): plants, grow_tents, protocols (flat stage block),
 * super_cycle_configs, grow_events (simple bitácora).
 *
 * v2 (1.0.0 release):
 *  - protocols gain photoperiod columns (lightHours, darkHours, presetType, cycleStartAt).
 *  - grow_events gains the nullable metric/diagnosis columns.
 *  - New tables: protocol_stages, stage_entries, achievements, reminders,
 *    breeding_projects, breeding_crosses.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ── Protocols: add photoperiod config ──────────────────────────
        db.execSQL("ALTER TABLE protocols ADD COLUMN lightHours INTEGER NOT NULL DEFAULT 18")
        db.execSQL("ALTER TABLE protocols ADD COLUMN darkHours INTEGER NOT NULL DEFAULT 6")
        db.execSQL("ALTER TABLE protocols ADD COLUMN presetType TEXT NOT NULL DEFAULT 'custom'")
        db.execSQL("ALTER TABLE protocols ADD COLUMN cycleStartAt INTEGER NOT NULL DEFAULT 0")

        // ── grow_events: add nullable metric + diagnosis columns ────────
        db.execSQL("ALTER TABLE grow_events ADD COLUMN groupId TEXT")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN temperature REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN humidity REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN ph REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN ec REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN nutrientN REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN nutrientP REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN nutrientK REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN amount REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN height REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN lampDistance REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN trainingType TEXT")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN defoliationLevel INTEGER")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN vpd REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN trichomeMaturity TEXT")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN diagnosisResult TEXT")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN diagnosisCertainty REAL")
        db.execSQL("ALTER TABLE grow_events ADD COLUMN imagePath TEXT")

        // ── New tables ──────────────────────────────────────────────────
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `protocol_stages` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `protocolId` INTEGER NOT NULL,
                `stageName` TEXT NOT NULL,
                `durationDays` INTEGER NOT NULL,
                `recurrenceIntervalDays` INTEGER NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                FOREIGN KEY(`protocolId`) REFERENCES `protocols`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `stage_entries` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `protocolId` INTEGER NOT NULL,
                `plantId` INTEGER NOT NULL,
                `stageName` TEXT NOT NULL,
                `enteredAt` INTEGER NOT NULL,
                `exitedAt` INTEGER
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `achievements` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `icon` TEXT NOT NULL,
                `xpReward` INTEGER NOT NULL,
                `isUnlocked` INTEGER NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `reminders` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `plantId` INTEGER,
                `title` TEXT NOT NULL,
                `message` TEXT NOT NULL,
                `recurrenceType` TEXT NOT NULL,
                `recurrenceIntervalDays` INTEGER NOT NULL,
                `reminderTime` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `breeding_projects` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `motherId` TEXT NOT NULL,
                `fatherId` TEXT NOT NULL,
                `generation` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `status` TEXT NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `breeding_crosses` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `projectId` INTEGER NOT NULL,
                `parent1` TEXT NOT NULL,
                `parent2` TEXT NOT NULL,
                `phenotypeScore` REAL NOT NULL,
                `notes` TEXT NOT NULL,
                FOREIGN KEY(`projectId`) REFERENCES `breeding_projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """)

        // Indices validated by Room schema.
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_plants_tentId` ON `plants` (`tentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_grow_events_plantId` ON `grow_events` (`plantId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_protocol_stages_protocolId` ON `protocol_stages` (`protocolId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_breeding_crosses_projectId` ON `breeding_crosses` (`projectId`)")
    }
}

/**
 * Explicit migration from schema v2 to v3.
 *
 * v3 (1.4.0): the supercycle moves from the plant to the **tent**. `protocols`
 * keeps its per-plant photoperiod as history; `super_cycle_configs` gains a
 * nullable `tentId` and becomes tent-scoped.
 *
 * ## Why the table is rebuilt instead of ALTERed
 *
 * Two columns change and neither can be reached with `ADD COLUMN`:
 *  - `tentId` is new and nullable, which `ADD COLUMN` could have done.
 *  - `plantId` becomes nullable, because a config written from v3 onwards has no
 *    plant to point at. SQLite cannot drop a `NOT NULL` constraint on a column
 *    in place, and Room compares nullability between the entity and the live
 *    table, so leaving the constraint would fail validation on the first open.
 *
 * Hence the Room rebuild sequence: create the v3 shape, copy the rows, drop the
 * old table, rename, recreate the index. No foreign keys, views or triggers
 * reference this table, so there is nothing else to re-point.
 *
 * ## Why `tentId` is nullable
 *
 * This is not a convenience. On the install that prompted the change, two of the
 * three configs pointed at plant ids that do not exist (`plantId` 0 and 1), so
 * resolving them against `plants` yields nothing. `NOT NULL` here fails the
 * upgrade at that row and the app does not open. They are copied with
 * `tentId = NULL` — the grower's explicit decision — and are listed under
 * "Sin carpa" in the supercycle screen instead of being dropped.
 *
 * ## Reversibility
 *
 * `plantId` is deliberately kept and deliberately nullable. It is the only link
 * the orphaned configs have left, and keeping the column is what keeps v3
 * reversible; dropping it here would make it unrecoverable by any later
 * migration. Nothing reads it to decide which config applies — that is
 * `SuperCycleRepository.getConfigForPlant`, in one place.
 *
 * The copy resolves the tent inline, which is the same statement as the
 * `UPDATE super_cycle_configs SET tentId = (SELECT tentId FROM plants WHERE id
 * = plantId)` it replaces, with the aliases made explicit so the correlated
 * subquery cannot be misread:
 *
 *     UPDATE super_cycle_configs SET tentId =
 *         (SELECT tentId FROM plants WHERE plants.id = super_cycle_configs.plantId)
 *
 * A config whose plant does not exist resolves the subquery to NULL and stays
 * NULL, which is the intended outcome rather than an accident.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. The v3 shape. Column order and constraints are copied verbatim from
        //    the Room-generated schema in app/schemas/.../3.json; Room validates
        //    the live table against that file, not against this comment.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `super_cycle_configs_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `tentId` INTEGER,
                `plantId` INTEGER,
                `lightHours` INTEGER NOT NULL,
                `darkHours` INTEGER NOT NULL,
                `cycleStartAt` INTEGER NOT NULL,
                `presetType` TEXT NOT NULL
            )
        """)

        // 2. Copy every row. No WHERE clause: a filter here is a silent data
        //    loss, and the orphan rows are the ones we cannot afford to drop.
        db.execSQL("""
            INSERT INTO `super_cycle_configs_new`
                (`id`, `tentId`, `plantId`, `lightHours`, `darkHours`, `cycleStartAt`, `presetType`)
            SELECT
                s.`id`,
                (SELECT p.`tentId` FROM `plants` p WHERE p.`id` = s.`plantId`),
                s.`plantId`,
                s.`lightHours`,
                s.`darkHours`,
                s.`cycleStartAt`,
                s.`presetType`
            FROM `super_cycle_configs` s
        """)

        // 3. The old table goes before the rename, so the rename target is free.
        db.execSQL("DROP TABLE `super_cycle_configs`")
        db.execSQL("ALTER TABLE `super_cycle_configs_new` RENAME TO `super_cycle_configs`")

        // 4. The index Room expects for the new column.
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_super_cycle_configs_tentId` " +
                "ON `super_cycle_configs` (`tentId`)"
        )
    }
}

/**
 * One column added to `protocols` by [MIGRATION_3_4].
 *
 * [sqlType] is the SQLite column type as Room spells it in the exported schema,
 * and [addColumnStatement] is the exact statement the migration executes. Both
 * are derived here rather than inlined in [MIGRATION_3_4], so the migration and
 * the test that audits it read the same string instead of two copies of it.
 */
data class ProtocolColumnAddition(
    val columnName: String,
    val sqlType: String
) {
    /**
     * The `ALTER TABLE` that adds this column.
     *
     * `DEFAULT NULL` is written out rather than left implicit so the statement
     * and the entity's `@ColumnInfo(defaultValue = "NULL")` are byte-identical
     * in what they declare. Room compares an entity column's declared default
     * against `PRAGMA table_info` at open time, and a statement that added the
     * column with no `DEFAULT` clause at all would leave the two disagreeing.
     */
    val addColumnStatement: String =
        "ALTER TABLE `protocols` ADD COLUMN `$columnName` $sqlType DEFAULT NULL"
}

/**
 * Every column [MIGRATION_3_4] adds to `protocols`, in the order it adds them.
 *
 * This list is the whole migration. There is no second place where a v4 column
 * is named, which is what lets `ProtocolMigrationContractTest` prove that the
 * entity and the migration agree: a column added to [Protocol] and forgotten
 * here is a mismatch between the exported schema and the live table, and Room
 * refuses to open on exactly that.
 *
 * All fourteen are nullable with a null default, which is the whole reason this
 * is fourteen `ADD COLUMN` statements and not a table rebuild — see
 * [MIGRATION_3_4].
 */
val PROTOCOL_V4_ADDED_COLUMNS: List<ProtocolColumnAddition> = listOf(
    // Declared bands, stored as TEXT by GrowRangeConverters.
    ProtocolColumnAddition("vpdBand", "TEXT"),
    ProtocolColumnAddition("phRange", "TEXT"),
    ProtocolColumnAddition("ecRange", "TEXT"),
    // Light-period climate.
    ProtocolColumnAddition("lightTempCelsius", "REAL"),
    ProtocolColumnAddition("lightHumidityPercent", "REAL"),
    // Dark-period climate.
    ProtocolColumnAddition("darkTempCelsius", "REAL"),
    ProtocolColumnAddition("darkHumidityPercent", "REAL"),
    // Light intensity.
    ProtocolColumnAddition("ppfd", "REAL"),
    ProtocolColumnAddition("dli", "REAL"),
    // Fixture and growing medium.
    ProtocolColumnAddition("lightType", "TEXT"),
    ProtocolColumnAddition("lampPowerWatts", "REAL"),
    ProtocolColumnAddition("substrateType", "TEXT"),
    ProtocolColumnAddition("wateringStrategy", "TEXT"),
    // Grower's own notes.
    ProtocolColumnAddition("observations", "TEXT")
)

/**
 * The statements [MIGRATION_3_4] runs, in order.
 *
 * Exposed so the test can assert what the migration does — fourteen additions
 * and nothing else — without reflecting on the `Migration` instance, which
 * would only prove that an object exists.
 */
val PROTOCOL_V4_STATEMENTS: List<String> =
    PROTOCOL_V4_ADDED_COLUMNS.map { it.addColumnStatement }

/**
 * Explicit migration from schema v3 to v4.
 *
 * v4 (F10a): `protocols` gains the extended grow fields — declared pH / EC / VPD
 * bands, light and dark climate, PPFD and DLI, fixture and medium, and the
 * grower's own agronomic observations.
 *
 * ## Why this is fourteen `ADD COLUMN`s and not a table rebuild
 *
 * [MIGRATION_2_3] had to rebuild `super_cycle_configs`, because a column had to
 * *become* nullable and SQLite cannot drop a `NOT NULL` in place. Nothing here
 * does. Every v4 column is new, nullable, with a null default, and adding a
 * nullable column is the one schema change SQLite performs in place. So this
 * migration is fourteen `ALTER TABLE protocols ADD COLUMN` statements and stops.
 *
 * ## Why the rows are never touched
 *
 * There is no `DROP`, no `DELETE`, no `UPDATE` and no table rebuild in this
 * migration. An existing `protocols` row keeps every one of its original
 * columns — `name`, `lightHours`, `darkHours`, `presetType`, `cycleStartAt`,
 * `isActive`, `plantId` — byte for byte, because not one statement here can
 * touch them.
 *
 * The new columns read back as NULL on that row, which is the correct outcome
 * and not an absence of data: a protocol created before v4 has no declared pH
 * band, and the screen says "Sin definir" rather than inventing one. Fabricating
 * a plausible default here would be the worst available outcome, because it
 * would be indistinguishable from a band the grower actually typed.
 *
 * ## What this migration does not do
 *
 * It does not touch `protocol_stages` or `stage_entries`. A per-stage VPD band
 * is real agronomy and it belongs on the stage row; adding it here, to a table
 * with one row per protocol, would create a column no query can populate. That
 * is the next schema phase, and it needs its own migration and its own test.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        PROTOCOL_V4_STATEMENTS.forEach { statement -> db.execSQL(statement) }
    }
}

/**
 * One column added to `grow_events` by [MIGRATION_4_5].
 *
 * [table] is a field rather than being derived from the class name, because the
 * second migration in this file adds to a different table and the test that audits
 * both has to be able to say which statement belongs to which table. Same shape as
 * [ProtocolColumnAddition], which was extracted from its own migration for the same
 * reason: one list the migration and the audit read, rather than two copies of the
 * same strings that can drift.
 */
data class GrowEventColumnAddition(
    val columnName: String,
    val sqlType: String,
    val table: String = "grow_events"
) {
    /**
     * The `ALTER TABLE` that adds this column.
     *
     * `DEFAULT NULL` is written out rather than left implicit so the statement and the
     * entity's `@ColumnInfo(defaultValue = "NULL")` are byte-identical in what they
     * declare. Room compares a column's declared default against `PRAGMA
     * table_info` at open time, and a statement that added the column with no
     * `DEFAULT` clause at all would leave the two disagreeing — which is a crash on
     * the user's next launch, before any screen draws.
     */
    val addColumnStatement: String =
        "ALTER TABLE `$table` ADD COLUMN `$columnName` $sqlType DEFAULT NULL"
}

/**
 * Every column [MIGRATION_4_5] adds to `grow_events`, in the order it adds them.
 *
 * ## Why the VPD history is NOT a new table
 *
 * `grow_events.vpd` has existed since `MIGRATION_1_2` and it already **is** the VPD
 * history. The chart needs one number and one instant per reading, per plant, and
 * that is exactly a query over this table:
 *
 * ```
 * SELECT timestamp, vpd, vpdSource, vpdLeafOffset, temperature, humidity
 * FROM grow_events
 * WHERE plantId = :plantId AND vpd IS NOT NULL AND timestamp BETWEEN :from AND :to
 * ORDER BY timestamp ASC
 * ```
 *
 * `EventDao.watchVpdHistory` is that statement. Every column the chart plots or
 * labels is already on the row: the value, the moment, and — after this migration —
 * the provenance and the offset that produced it. A `vpd_history` table would hold a
 * *copy* of the same reading under the same timestamp, and two tables holding one
 * truth is the shape of the F1 boiling point and the F2 temperature models, both of
 * which this project already paid for once.
 *
 * ## What the table genuinely could not answer
 *
 * Where the number came from. This screen logs two different quantities into one
 * `REAL` column: a reading off the grower's own hygrometer, and a value the app
 * derived from a temperature and a humidity they typed. Before v5 there was nowhere
 * to record which, so a chart could not keep them apart — and plotting an offline
 * estimate on the same axis as a logged reading with no distinction is the exact
 * defect `EstimatedClimate` already documents, in a new place.
 *
 * So the migration adds the two columns that record the origin, and nothing else.
 */
val GROW_EVENT_V5_ADDED_COLUMNS: List<GrowEventColumnAddition> = listOf(
    // "MEASURED" / "CALCULATED". NULL on every pre-v5 row, and NULL means the origin
    // is UNKNOWN, never "measured": some pre-v5 numbers were typed by the grower and
    // some were derived by an older build, and the column cannot tell which.
    GrowEventColumnAddition("vpdSource", "TEXT"),
    // The leaf-to-air offset a calculated VPD was produced with, so the number can be
    // reproduced instead of trusted. Null for a measured reading.
    GrowEventColumnAddition("vpdLeafOffset", "REAL")
)

/**
 * The statements [MIGRATION_4_5] runs, in order.
 *
 * Exposed so the test can assert what the migration does — two additions and
 * nothing else — without reflecting on the `Migration` instance, which would only
 * prove that an object exists.
 */
val GROW_EVENT_V5_STATEMENTS: List<String> =
    GROW_EVENT_V5_ADDED_COLUMNS.map { it.addColumnStatement }

/**
 * Explicit migration from schema v4 to v5.
 *
 * v4 (F10b): `grow_events` gains the VPD provenance columns — `vpdSource` and
 * `vpdLeafOffset` — so a logged reading stays distinguishable from a calculated one.
 *
 * ## Why this is two `ADD COLUMN`s and nothing else
 *
 * Same reasoning as [MIGRATION_3_4]. Both columns are new, nullable, and default to
 * NULL, and adding a nullable column is the one schema change SQLite performs in
 * place. No table is rebuilt, no `NOT NULL` has to be dropped, so no row is
 * copied through a temporary table and no id can be reassigned on the way.
 *
 * ## Why the rows are never touched
 *
 * There is no `DROP`, no `DELETE`, no `UPDATE` and no rebuild here. Every existing
 * `grow_events` row keeps its 25 original columns byte for byte, because not one
 * statement in this migration can reach them. The two new columns read back NULL on
 * those rows, which is the correct outcome: a reading logged before v5 genuinely has
 * no recorded origin, and `VpdProvenance.UNKNOWN` says so rather than guessing.
 *
 * Defaulting the new columns to `'MEASURED'` would be the worst outcome available —
 * every pre-v5 derived number would be published as a sensor reading, which is the
 * failure this whole column exists to prevent.
 *
 * ## What this migration does not do
 *
 * It adds no table. It does not add a per-stage VPD band to `protocol_stages`: real
 * agronomy belongs there, but no screen in this phase reads one, and a column no
 * query can populate is a column that describes a shape the data does not have. That
 * one arrives with [MIGRATION_5_6], which is also its own migration and its own test.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        GROW_EVENT_V5_STATEMENTS.forEach { statement -> db.execSQL(statement) }
    }
}

/**
 * One column added to `protocol_stages` by [MIGRATION_5_6].
 *
 * The third member of the same family, and deliberately a separate type rather than
 * a shared one. [ProtocolColumnAddition] hardcodes `protocols` and
 * [GrowEventColumnAddition] defaults to `grow_events`; one generic
 * `ColumnAddition(name, type, table)` would read better and would also let a stage
 * column be appended to `PROTOCOL_V4_ADDED_COLUMNS` by accident, which is precisely
 * the drift `ProtocolMigrationContractTest` exists to catch. A per-table type makes
 * the wrong list unrepresentable at the type level.
 */
data class ProtocolStageColumnAddition(
    val columnName: String,
    val sqlType: String,
    val table: String = "protocol_stages"
) {
    /**
     * The `ALTER TABLE` that adds this column.
     *
     * `DEFAULT NULL` written out, for the same reason and with the same force as
     * [GrowEventColumnAddition.addColumnStatement]: Room compares an entity column's
     * declared default against `PRAGMA table_info` at open time, so a column added
     * with no `DEFAULT` clause leaves the two disagreeing and the app fails to open.
     */
    val addColumnStatement: String =
        "ALTER TABLE `$table` ADD COLUMN `$columnName` $sqlType DEFAULT NULL"
}

/**
 * Every column [MIGRATION_5_6] adds to `protocol_stages`, in the order it adds them.
 *
 * This list is the whole migration, so the migration and
 * `StageVpdTargetMigrationContractTest` read the same strings rather than two copies
 * that can drift.
 *
 * One column, and it is the debt [MIGRATION_3_4] and [MIGRATION_4_5] each named as
 * out of scope: the per-stage VPD band. It is a [com.trichome.app.data.model.GrowRange],
 * stored as TEXT by the same `GrowRangeConverters` the protocol header already uses —
 * a second band type or a second converter pair would be a second representation of
 * one declared band, and the encoding would then be the only thing telling them apart.
 *
 * Nullable with a null default, so the three stages a real protocol already holds read
 * back as "no target" rather than as a fabricated `0.0`.
 */
val PROTOCOL_STAGE_V6_ADDED_COLUMNS: List<ProtocolStageColumnAddition> = listOf(
    // The band's kPa bounds, as the stored TEXT form "low:high". NOT NULL is impossible:
    // an ALTER TABLE ADD COLUMN that is NOT NULL and has no value fails the upgrade on
    // every existing protocol_stages row, which is all of them.
    ProtocolStageColumnAddition("vpdTarget", "TEXT")
)

/**
 * The statements [MIGRATION_5_6] runs, in order.
 *
 * Read from the column list above rather than written out here, so an auditor can
 * check the statements by reading the list the migration iterates and not a second
 * transcription of it.
 */
val PROTOCOL_STAGE_V6_STATEMENTS: List<String> =
    PROTOCOL_STAGE_V6_ADDED_COLUMNS.map { it.addColumnStatement }

/**
 * Explicit migration from schema v5 to v6.
 *
 * v6 (F12): `protocol_stages` gains `vpdTarget`, the per-stage VPD band. F10a put
 * the grow-wide band on the `protocols` header and said in its own KDoc that a
 * per-stage band belongs on the stage table; that debt is paid here.
 *
 * ## Why this is one `ADD COLUMN` and nothing else
 *
 * Same reasoning as [MIGRATION_4_5]. The column is new, nullable and defaults to
 * NULL, and adding a nullable column is the one schema change SQLite performs in
 * place. Nothing here forces a table rebuild:
 *
 *  - `protocol_stages` has a foreign key and an index, and neither is touched — no
 *    `NOT NULL` is dropped, no column is renamed, no type changes, so no `CREATE
 *    TABLE …_new` / `INSERT SELECT` / `DROP` / `RENAME` cycle is required. A rebuild
 *    would copy every stage row through a temporary table and is the shape that
 *    silently reassigns ids on the way.
 *
 * ## Why the rows are never touched
 *
 * There is no `DROP`, no `DELETE`, no `UPDATE` and no rebuild. The `stage_entries`
 * rows that reference stages by *name*, not by id, are unaffected as well, so a
 * stage transition recorded before the upgrade still resolves after it.
 *
 * The new column reads back NULL on every existing stage, which is the correct
 * outcome and not a placeholder: those stages were written before the app could
 * record a per-stage target, and `null` says so. A default of `0.0:0.0` would
 * publish a band of zero as something the grower wrote.
 *
 * ## What this migration does not do
 *
 * It adds no stage temperature or humidity target. The `protocols` header already
 * declares four of those (schema 4) and a second copy with no stated precedence is
 * two sources of truth, which is the F1/F2 defect this project has already paid for.
 * It adds no table and no index. And it adds no second band type.
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        PROTOCOL_STAGE_V6_STATEMENTS.forEach { statement -> db.execSQL(statement) }
    }
}