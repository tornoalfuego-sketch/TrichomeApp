package com.trichome.app.data.dao

import androidx.room.*
import com.trichome.app.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlantDao {
    @Query("SELECT * FROM plants ORDER BY sortOrder ASC, createdAt DESC")
    fun getAllPlants(): Flow<List<Plant>>

    @Query("SELECT * FROM plants WHERE id = :id")
    suspend fun getPlantById(id: Long): Plant?

    @Query("SELECT * FROM plants WHERE tentId = :tentId ORDER BY sortOrder ASC")
    suspend fun getPlantsByTent(tentId: Long): List<Plant>

    /**
     * Plants whose tent no longer exists.
     *
     * `plants.tentId` is declared `ON DELETE SET NULL`, so deleting a tent
     * detaches its plants instead of deleting them, and every other query in the
     * app filters on `tentId = :tentId`. That left them with no way to be read at
     * all: they existed, they were counted by [getAllPlants], and no screen could
     * show them. This query is what makes the set reachable, which is the only
     * reason a row this invisible could survive.
     */
    @Query("SELECT * FROM plants WHERE tentId IS NULL ORDER BY createdAt ASC")
    fun getUnassignedPlants(): Flow<List<Plant>>

    /**
     * Puts a plant into a tent at the end of its order.
     *
     * A targeted UPDATE rather than a full-row write: the caller has only the id
     * and the destination, and replacing the row from a stale snapshot would
     * overwrite whatever the grower changed in the meantime.
     */
    @Query("UPDATE plants SET tentId = :tentId WHERE id = :plantId")
    suspend fun assignPlantToTent(plantId: Long, tentId: Long)

    @Query("SELECT * FROM plants ORDER BY sortOrder ASC")
    suspend fun getPlantsSnapshot(): List<Plant>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlant(plant: Plant): Long

    @Update
    suspend fun updatePlant(plant: Plant)

    @Delete
    suspend fun deletePlant(plant: Plant)

    @Query("UPDATE plants SET sortOrder = sortOrder + 1 WHERE sortOrder >= :from AND tentId = :tentId")
    suspend fun incrementSortOrder(from: Int, tentId: Long)

    @Query("UPDATE plants SET sortOrder = sortOrder - 1 WHERE sortOrder <= :from AND sortOrder > 0 AND tentId = :tentId")
    suspend fun decrementSortOrder(from: Int, tentId: Long)

    /**
     * Writes `plants.currentStage`, the denormalised cache of the open stage entry.
     *
     * A targeted UPDATE for the same reason as [assignPlantToTent]: the caller holds a
     * snapshot that a concurrent write may already have invalidated.
     *
     * This column is a cache, not the record. The record is `stage_entries`, and the two
     * are written in the same transaction by `StageEntryRepository.applyTransition` so
     * they cannot disagree — a disagreement is what `StageContext.isCacheConsistent`
     * detects, and what that transition then repairs.
     */
    @Query("UPDATE plants SET currentStage = :stageName WHERE id = :plantId")
    suspend fun setCurrentStage(plantId: Long, stageName: String)

    /**
     * Marks a plant archived, or active again.
     *
     * A targeted UPDATE rather than `@Update`, for the same reason as
     * [assignPlantToTent]: the caller holds a snapshot that may already be stale, and
     * writing the whole row back from it would overwrite whatever the grower changed
     * while the confirmation dialog was open.
     *
     * This is also the entire write behind "Finalizar cultivo". There is no `DELETE`
     * anywhere in that path: `plants.isActive = 0` is a terminal state on the row, and
     * every journal, protocol, reminder and stage entry keyed to the plant survives it.
     */
    @Query("UPDATE plants SET isActive = :isActive WHERE id = :plantId")
    suspend fun setPlantActive(plantId: Long, isActive: Boolean)

    }

@Dao
interface GrowTentDao {
    @Query("SELECT * FROM grow_tents ORDER BY name ASC")
    fun getAllTents(): Flow<List<GrowTent>>

    @Query("SELECT * FROM grow_tents WHERE id = :id")
    suspend fun getTentById(id: Long): GrowTent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTent(tent: GrowTent): Long

    /**
     * Tents as a snapshot, name-ordered like [getAllTents].
     *
     * For the export picker, which reads once inside a modal. `getAllTents` returns a `Flow`
     * for the screens that keep a live list, and collecting it at every dialog open is how a
     * collector outlives the dialog that wanted it.
     */
    @Query("SELECT * FROM grow_tents ORDER BY name ASC")
    suspend fun getAllTentsSnapshot(): List<GrowTent>

    @Update
    suspend fun updateTent(tent: GrowTent)

    @Delete
    suspend fun deleteTent(tent: GrowTent)
}

@Dao
interface ProtocolDao {
    @Query("SELECT * FROM protocols WHERE plantId = :plantId ORDER BY id DESC")
    fun getProtocolsByPlant(plantId: Long): Flow<List<Protocol>>

    @Query("SELECT * FROM protocols WHERE id = :id")
    suspend fun getProtocolById(id: Long): Protocol?

    @Query("SELECT * FROM protocols WHERE isActive = 1")
    suspend fun getActiveProtocols(): List<Protocol>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProtocol(protocol: Protocol): Long

    @Update
    suspend fun updateProtocol(protocol: Protocol)

    @Delete
    suspend fun deleteProtocol(protocol: Protocol)
}

@Dao
interface ProtocolStageDao {
    @Query("SELECT * FROM protocol_stages WHERE protocolId = :protocolId ORDER BY sortOrder ASC")
    suspend fun getStagesByProtocol(protocolId: Long): List<ProtocolStage>

    @Query("SELECT * FROM protocol_stages WHERE protocolId = :protocolId ORDER BY sortOrder ASC")
    fun watchStagesByProtocol(protocolId: Long): Flow<List<ProtocolStage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStage(stage: ProtocolStage): Long

    @Update
    suspend fun updateStage(stage: ProtocolStage)

    @Delete
    suspend fun deleteStage(stage: ProtocolStage)

    @Query("DELETE FROM protocol_stages WHERE protocolId = :protocolId")
    suspend fun clearStages(protocolId: Long)
}

@Dao
interface StageEntryDao {
    @Query("SELECT * FROM stage_entries WHERE protocolId = :protocolId ORDER BY enteredAt ASC")
    suspend fun getStageEntriesByProtocol(protocolId: Long): List<StageEntry>

    @Query("SELECT * FROM stage_entries WHERE plantId = :plantId ORDER BY enteredAt DESC")
    fun watchStageEntries(plantId: Long): Flow<List<StageEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStageEntry(entry: StageEntry): Long

    @Query("SELECT * FROM stage_entries WHERE plantId = :plantId ORDER BY enteredAt DESC LIMIT 1")
    suspend fun getLatestStageEntry(plantId: Long): StageEntry?

    /**
     * The plant's open stage entry, or null when it has none.
     *
     * Read by id before a transition, and the id is what the transition plan carries:
     * the name in the row may be stale relative to `plants.currentStage`, but the row's
     * existence is not something a name can imply. Both halves of a transition — closing
     * this row and opening the next one — are decided from what this returns.
     */
    @Query("SELECT * FROM stage_entries WHERE plantId = :plantId AND exitedAt IS NULL ORDER BY enteredAt DESC LIMIT 1")
    suspend fun getOpenStageEntry(plantId: Long): StageEntry?

    /**
     * How many entries a plant has, open or closed.
     *
     * The finalize confirmation quotes it, so the grower sees "3 cambios de etapa" rather
     * than a bare "archivada". `COUNT(*)` on the plant's own rows, not the table.
     */
    @Query("SELECT COUNT(*) FROM stage_entries WHERE plantId = :plantId")
    suspend fun countStageEntries(plantId: Long): Int

    /**
     * Closes the plant's open entry at [exitedAt].
     *
     * ## Why `exitedAt IS NULL` rather than `id = :id`
     *
     * Closing *every* open row, and returning how many it touched, is the property that
     * makes a timeline self-healing: a plant whose timeline already holds two open
     * entries — which a pre-fix build could write, and which no screen could repair —
     * ends with exactly one after any transition, instead of accumulating a third. The
     * caller also learns whether anything was actually closed.
     *
     * @return the number of rows closed. `0` means the plant had no open entry, which is
     *   a normal state and not a failure.
     */
    @Query("UPDATE stage_entries SET exitedAt = :exitedAt WHERE plantId = :plantId AND exitedAt IS NULL")
    suspend fun closeOpenStageEntries(plantId: Long, exitedAt: Long): Int

    /**
     * Closes exactly the entry named by [entryId], at [exitedAt].
     *
     * The counterpart to [closeOpenStageEntries] for the finalize path, and the reason it
     * exists separately: archiving is not a stage change, so it must not open a new entry,
     * but it must still close the one that is open. A single-entry close makes that
     * guarantee explicit — a finalize cannot possibly write a stage, which is the one
     * thing an archive must never do.
     *
     * @return the number of rows closed: `0` or `1`.
     */
    @Query("UPDATE stage_entries SET exitedAt = :exitedAt WHERE id = :entryId AND exitedAt IS NULL")
    suspend fun closeStageEntry(entryId: Long, exitedAt: Long): Int

    /**
     * The plant's whole timeline, oldest first, observed.
     *
     * `enteredAt` then `id`, so two entries written in the same millisecond still have a
     * defined order and the timeline cannot render out of sequence.
     */
    @Query("SELECT * FROM stage_entries WHERE plantId = :plantId ORDER BY enteredAt ASC, id ASC")
    fun watchStageTimeline(plantId: Long): Flow<List<StageEntry>>

    /**
     * One plant's whole timeline as a snapshot, oldest first.
     *
     * The exporter reads snapshots rather than subscribing, so it needs a `suspend` form;
     * collecting a `Flow` once at every call site is how a caller ends up leaking a
     * subscription. Neither existing read fits: [getStageEntriesByProtocol] filters by
     * protocol, and [watchStageEntries] is observed.
     */
    @Query("SELECT * FROM stage_entries WHERE plantId = :plantId ORDER BY enteredAt ASC, id ASC")
    suspend fun getTimelineForExport(plantId: Long): List<StageEntry>
}

@Dao
interface EventDao {
    @Query("SELECT * FROM grow_events WHERE plantId = :plantId ORDER BY timestamp DESC")
    fun getEventsByPlant(plantId: Long): Flow<List<GrowEvent>>

    @Query("SELECT * FROM grow_events WHERE plantId = :plantId ORDER BY timestamp DESC")
    suspend fun getEventsByPlantSnapshot(plantId: Long): List<GrowEvent>

    @Query("SELECT * FROM grow_events WHERE groupId = :groupId ORDER BY timestamp ASC")
    suspend fun getEventsByGroup(groupId: String): List<GrowEvent>

    @Query("SELECT * FROM grow_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<GrowEvent>>

    @Query("SELECT * FROM grow_events WHERE id = :id")
    suspend fun getEventById(id: Long): GrowEvent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: GrowEvent): Long

    @Update
    suspend fun updateEvent(event: GrowEvent)

    @Delete
    suspend fun deleteEvent(event: GrowEvent)

    @Query("SELECT * FROM grow_events WHERE eventType = :type ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getEventsByType(type: String, limit: Int): List<GrowEvent>

    @Query("SELECT * FROM grow_events")
    suspend fun getAllEventsSnapshot(): List<GrowEvent>

    /**
     * Events whose timestamp falls inside a month window, observed.
     *
     * This used to be a one-shot `suspend fun getEventsBetween`, and that is
     * exactly why the calendar ignored a just-saved event: the screen read it
     * once from `LaunchedEffect(month, filterPlantId)` and never again, so a
     * row written while the month was already open stayed invisible. The window
     * stays bounded — this is not [getAllEvents] behind a filter — so it cannot
     * turn into a whole-table read.
     */
    @Query("SELECT * FROM grow_events WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp ASC")
    fun watchEventsBetween(from: Long, to: Long): Flow<List<GrowEvent>>

    @Query("SELECT * FROM grow_events WHERE eventType = :type AND plantId = :plantId AND timestamp BETWEEN :from AND :to ORDER BY timestamp ASC")
    suspend fun getEventsByTypeInRange(plantId: Long, type: String, from: Long, to: Long): List<GrowEvent>

    /** Distinct epoch days (millis/86400000) with at least one event — used for streaks/calendar. */
    @Query("SELECT DISTINCT CAST(timestamp / 86400000 AS INTEGER) FROM grow_events WHERE eventType != 'DIAGNOSIS'")
    suspend fun getActiveEpochDays(): List<Long>

    /**
     * The plant's VPD history over a window, observed.
     *
     * ## This query is the answer to "is a history table needed?"
     *
     * `grow_events.vpd` has existed since `MIGRATION_1_2` and this statement is the
     * history: value, moment, and — after `MIGRATION_4_5` — the provenance and the leaf
     * offset that produced it, all on the row that already holds them. A dedicated
     * history table would be a second copy of one reading under one timestamp, which is
     * the shape of the F1 boiling point and the F2 temperature models.
     *
     * `vpd IS NOT NULL` is load-bearing rather than a filter most rows pass: most journal
     * rows are irrigation notes with no VPD, and plotting one as `0.0` would draw a flat
     * line along the floor that no grower ever measured.
     *
     * Bounded by [from]/[to] and observed as a `Flow`, so a reading saved while the chart
     * is open appears without the screen reloading — the same defect
     * [watchEventsBetween] documents for the calendar.
     */
    @Query(
        """
        SELECT * FROM grow_events
        WHERE plantId = :plantId
          AND vpd IS NOT NULL
          AND timestamp BETWEEN :from AND :to
        ORDER BY timestamp ASC
        """
    )
    fun watchVpdHistory(plantId: Long, from: Long, to: Long): Flow<List<GrowEvent>>

    /** One-shot form of [watchVpdHistory], for the exporter and for a first load. */
    @Query(
        """
        SELECT * FROM grow_events
        WHERE plantId = :plantId
          AND vpd IS NOT NULL
          AND timestamp BETWEEN :from AND :to
        ORDER BY timestamp ASC
        """
    )
    suspend fun getVpdHistory(plantId: Long, from: Long, to: Long): List<GrowEvent>

    /**
     * Events for a plant, newest first. Used by the exporter, which reads a snapshot and
     * renders it rather than subscribing to changes.
     */
    @Query("SELECT * FROM grow_events WHERE plantId = :plantId ORDER BY timestamp DESC")
    suspend fun getEventsForExport(plantId: Long): List<GrowEvent>

    /**
     * How many journal rows a plant has.
     *
     * Here and not on [PlantDao], because the count is of `grow_events`: a reader asking "how
     * much history does this plant have" is asking about the journal.
     *
     * `COUNT(*)` on the plant's own rows and never on the table — a whole-table count would
     * answer a different question, and the finalize confirmation would then quote the whole
     * database's size as this plant's history.
     */
    @Query("SELECT COUNT(*) FROM grow_events WHERE plantId = :plantId")
    suspend fun countEventsForPlant(plantId: Long): Int
}

/**
 * The VPD history and the stage timeline for one plant, observed together.
 *
 * ## Why these two queries live here and not in their own DAOs
 *
 * The VPD chart needs a `Flow` that re-emits when a reading is logged, and the stage
 * panel needs a `Flow` that re-emits when a transition is written. Subscribing to two
 * repositories' flows is fine, and it is *also* how the two screens end up disagreeing
 * about which window they are reading. Putting the statements in one DAO means the
 * window and the ordering are written once and the combination is the thing that is
 * named.
 *
 * It adds no query of its own: both statements are the ones documented on [EventDao] and
 * [StageEntryDao], restated here rather than delegated, because a DAO cannot call another
 * DAO. The duplication is deliberate and the alternative — two interfaces holding one
 * statement each — would move the definition of "the VPD history" away from the DAO
 * whose name says what it is.
 */
@Dao
interface StageAndEventDao {

    /** The plant's VPD history over a window, observed. See [EventDao.watchVpdHistory]. */
    @Query(
        """
        SELECT * FROM grow_events
        WHERE plantId = :plantId
          AND vpd IS NOT NULL
          AND timestamp BETWEEN :from AND :to
        ORDER BY timestamp ASC
        """
    )
    fun watchVpdHistory(plantId: Long, from: Long, to: Long): Flow<List<GrowEvent>>

    /** The plant's whole timeline, oldest first. See [StageEntryDao.watchStageTimeline]. */
    @Query("SELECT * FROM stage_entries WHERE plantId = :plantId ORDER BY enteredAt ASC, id ASC")
    fun watchStageTimeline(plantId: Long): Flow<List<StageEntry>>
}

@Dao
interface SuperCycleDao {

    /**
     * The config a tent runs on.
     *
     * `LIMIT 1` because a tent may legitimately end up with more than one row —
     * `insertSuperCycle` is called from a screen that knows only the plant — and
     * the newest one is the one the grower last saved. `id DESC` makes that
     * choice explicit instead of letting SQLite pick.
     */
    @Query("SELECT * FROM super_cycle_configs WHERE tentId = :tentId ORDER BY id DESC LIMIT 1")
    suspend fun getConfigByTent(tentId: Long): SuperCycleConfig?

    /**
     * A config still addressed by plant, which after v3 means one written before
     * the tent became the owner.
     *
     * Never preferred over [getConfigByTent] — the tent's supercycle wins, and
     * the per-plant row stays as history. It is still needed because a plant with
     * no tent has nothing else to resolve against.
     */
    @Query("SELECT * FROM super_cycle_configs WHERE tentId IS NULL AND plantId = :plantId ORDER BY id DESC LIMIT 1")
    suspend fun getLegacyConfigByPlant(plantId: Long): SuperCycleConfig?

    /**
     * Configs whose tent could not be resolved — the rows v2 left pointing at
     * plants that no longer exist.
     *
     * The same defect the plant list already had: `getAllConfigs` counts these,
     * and until now no query could return one, so they existed and were
     * unreachable. Same remedy, same naming as `getUnassignedPlants`.
     */
    @Query("SELECT * FROM super_cycle_configs WHERE tentId IS NULL ORDER BY id ASC")
    fun getConfigsWithoutTent(): Flow<List<SuperCycleConfig>>

    /**
     * The plants a config applies to.
     *
     * Both branches live in one query on purpose. A tent-scoped config resolves
     * to the whole tent; a pre-v3 row, or a config whose plant has no tent,
     * resolves to its single plant. Splitting this into two queries and letting
     * each caller pick is exactly how the two scopes would drift apart again.
     */
    @Query("""
        SELECT * FROM plants
        WHERE (:tentId IS NOT NULL AND tentId = :tentId)
           OR (:tentId IS NULL AND :plantId IS NOT NULL AND id = :plantId)
        ORDER BY sortOrder ASC, createdAt DESC
    """)
    suspend fun getPlantsInheriting(tentId: Long?, plantId: Long?): List<Plant>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuperCycle(config: SuperCycleConfig): Long

    @Update
    suspend fun updateSuperCycle(config: SuperCycleConfig)

    @Delete
    suspend fun deleteSuperCycle(config: SuperCycleConfig)

    @Query("SELECT * FROM super_cycle_configs")
    suspend fun getAllConfigs(): List<SuperCycleConfig>
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements ORDER BY id ASC")
    fun getAllAchievements(): Flow<List<Achievement>>

    @Query("SELECT * FROM achievements WHERE isUnlocked = 1")
    suspend fun getUnlockedAchievements(): List<Achievement>

    /**
     * Inserts a badge unless one with the same [Achievement.name] is already stored.
     *
     * Deliberately NOT `@Insert(onConflict = REPLACE)`. `REPLACE` resolves a
     * conflict on a primary key or a unique index, and this table has neither on
     * `name` — only an autoincrementing `id`, which is always new. So `REPLACE`
     * never matched anything and every call appended a row: the Séquito badge paid
     * four times on one device (+600 XP) because four calls each believed they
     * were first. A unique index on `name` would have caught it, but adding one to
     * a shipped table needs a migration with its own test, and this project does
     * not add schema without one.
     *
     * `NOT EXISTS` inside the statement makes the guarantee live in SQL rather
     * than in a caller: two coroutines that both pass an in-memory check still
     * produce one row. A read-then-write guard in the ViewModel is not that
     * guarantee, it is a hopeful version of it — which is exactly how four
     * duplicates shipped while a guard existed.
     *
     * Returns the row id inserted, or `-1` when the badge was already present.
     */
    @Query(
        """
        INSERT INTO achievements (name, description, icon, xpReward, isUnlocked)
        SELECT :name, :description, :icon, :xpReward, :isUnlocked
        WHERE NOT EXISTS (SELECT 1 FROM achievements WHERE name = :name)
        """
    )
    suspend fun insertAchievementIfAbsent(
        name: String,
        description: String,
        icon: String,
        xpReward: Int,
        isUnlocked: Boolean
    ): Long

    /**
     * Collapses duplicate rows for one badge, keeping the lowest id.
     *
     * The repair for rows a pre-fix build already wrote, for the badge whose
     * description identifies it. Public rather than a migration because the
     * duplicates are app-level history, not a schema change, and because
     * `APP_DATABASE_VERSION` stays at 3: no schema change is involved.
     */
    @Query(
        """
        DELETE FROM achievements
        WHERE name = :name
          AND id NOT IN (SELECT MIN(id) FROM achievements WHERE name = :name)
        """
    )
    suspend fun deleteDuplicateAchievements(name: String): Int

    @Update
    suspend fun updateAchievement(achievement: Achievement)

    @Query("SELECT COALESCE(SUM(xpReward),0) FROM achievements WHERE isUnlocked = 1")
    suspend fun getTotalXp(): Int
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE isActive = 1 ORDER BY reminderTime ASC")
    fun getAllActiveReminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE plantId = :plantId AND isActive = 1")
    suspend fun getRemindersForPlant(plantId: Long): List<Reminder>

    @Query("SELECT * FROM reminders WHERE isActive = 1")
    suspend fun getActiveRemindersSnapshot(): List<Reminder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder): Long

    @Update
    suspend fun updateReminder(reminder: Reminder)

    @Delete
    suspend fun deleteReminder(reminder: Reminder)
}

@Dao
interface BreedingDao {
    @Query("SELECT * FROM breeding_projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<BreedingProject>>

    @Query("SELECT * FROM breeding_projects WHERE id = :id")
    suspend fun getProjectById(id: Long): BreedingProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: BreedingProject): Long

    /**
     * Without this, opening the project editor on an existing row had nowhere
     * to write: `BreedingDao` had no `@Update` at all, so the only way to change
     * a project was to delete it and lose `createdAt` and `status` with it.
     */
    @Update
    suspend fun updateProject(project: BreedingProject)

    @Delete
    suspend fun deleteProject(project: BreedingProject)

    @Query("SELECT * FROM breeding_crosses WHERE projectId = :projectId ORDER BY id DESC")
    suspend fun getCrossesByProject(projectId: Long): List<BreedingCross>

    @Query("SELECT * FROM breeding_crosses")
    fun getAllCrosses(): Flow<List<BreedingCross>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCross(cross: BreedingCross): Long

    /**
     * The missing half of the same story: `CrossDialog` only ever inserted, so
     * editing an existing cross wrote a duplicate row next to it instead of
     * changing it.
     */
    @Update
    suspend fun updateCross(cross: BreedingCross)

    @Delete
    suspend fun deleteCross(cross: BreedingCross)
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM grow_events WHERE plantId = :plantId ORDER BY timestamp DESC")
    suspend fun getJournalEntries(plantId: Long): List<GrowEvent>
}