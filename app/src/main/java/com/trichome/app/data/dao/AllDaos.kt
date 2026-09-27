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

    @Query("SELECT * FROM grow_events WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp ASC")
    suspend fun getEventsBetween(from: Long, to: Long): List<GrowEvent>

    @Query("SELECT * FROM grow_events WHERE eventType = :type AND plantId = :plantId AND timestamp BETWEEN :from AND :to ORDER BY timestamp ASC")
    suspend fun getEventsByTypeInRange(plantId: Long, type: String, from: Long, to: Long): List<GrowEvent>

    /** Distinct epoch days (millis/86400000) with at least one event — used for streaks/calendar. */
    @Query("SELECT DISTINCT CAST(timestamp / 86400000 AS INTEGER) FROM grow_events WHERE eventType != 'DIAGNOSIS'")
    suspend fun getActiveEpochDays(): List<Long>
}

@Dao
interface SuperCycleDao {
    @Query("SELECT * FROM super_cycle_configs WHERE plantId = :plantId")
    suspend fun getSuperCycleByPlant(plantId: Long): SuperCycleConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuperCycle(config: SuperCycleConfig): Long

    @Update
    suspend fun updateSuperCycle(config: SuperCycleConfig)

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

    @Delete
    suspend fun deleteProject(project: BreedingProject)

    @Query("SELECT * FROM breeding_crosses WHERE projectId = :projectId ORDER BY id DESC")
    suspend fun getCrossesByProject(projectId: Long): List<BreedingCross>

    @Query("SELECT * FROM breeding_crosses")
    fun getAllCrosses(): Flow<List<BreedingCross>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCross(cross: BreedingCross): Long

    @Delete
    suspend fun deleteCross(cross: BreedingCross)
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM grow_events WHERE plantId = :plantId ORDER BY timestamp DESC")
    suspend fun getJournalEntries(plantId: Long): List<GrowEvent>
}