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
}

@Dao
interface GrowTentDao {
    @Query("SELECT * FROM grow_tents ORDER BY name ASC")
    fun getAllTents(): Flow<List<GrowTent>>

    @Query("SELECT * FROM grow_tents WHERE id = :id")
    suspend fun getTentById(id: Long): GrowTent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTent(tent: GrowTent): Long

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievement(achievement: Achievement): Long

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