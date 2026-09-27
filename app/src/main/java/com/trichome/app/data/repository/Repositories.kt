package com.trichome.app.data.repository

import com.trichome.app.data.dao.*
import com.trichome.app.data.entity.*
import kotlinx.coroutines.flow.Flow

class PlantRepository(private val dao: PlantDao) {
    fun getAllPlants(): Flow<List<Plant>> = dao.getAllPlants()
    suspend fun getPlantById(id: Long): Plant? = dao.getPlantById(id)
    suspend fun getPlantsByTent(tentId: Long): List<Plant> = dao.getPlantsByTent(tentId)
    suspend fun getPlantsSnapshot(): List<Plant> = dao.getPlantsSnapshot()
    suspend fun insertPlant(plant: Plant): Long = dao.insertPlant(plant)
    suspend fun updatePlant(plant: Plant) = dao.updatePlant(plant)
    suspend fun deletePlant(plant: Plant) = dao.deletePlant(plant)

    suspend fun movePlantUp(plant: Plant) {
        val tentId = plant.tentId ?: return
        val siblings = dao.getPlantsByTent(tentId).sortedBy { it.sortOrder }
        val index = siblings.indexOfFirst { it.id == plant.id }
        if (index <= 0) return
        val prev = siblings[index - 1]
        dao.updatePlant(prev.copy(sortOrder = plant.sortOrder))
        dao.updatePlant(plant.copy(sortOrder = prev.sortOrder))
    }

    suspend fun movePlantDown(plant: Plant) {
        val tentId = plant.tentId ?: return
        val siblings = dao.getPlantsByTent(tentId).sortedBy { it.sortOrder }
        val index = siblings.indexOfFirst { it.id == plant.id }
        if (index < 0 || index >= siblings.size - 1) return
        val next = siblings[index + 1]
        dao.updatePlant(next.copy(sortOrder = plant.sortOrder))
        dao.updatePlant(plant.copy(sortOrder = next.sortOrder))
    }
}

class TentRepository(private val dao: GrowTentDao) {
    fun getAllTents(): Flow<List<GrowTent>> = dao.getAllTents()
    suspend fun getTentById(id: Long): GrowTent? = dao.getTentById(id)
    suspend fun insertTent(tent: GrowTent): Long = dao.insertTent(tent)
    suspend fun updateTent(tent: GrowTent) = dao.updateTent(tent)
    suspend fun deleteTent(tent: GrowTent) = dao.deleteTent(tent)
}

class ProtocolRepository(
    private val dao: ProtocolDao,
    private val stageDao: ProtocolStageDao
) {
    fun getProtocolsByPlant(plantId: Long): Flow<List<Protocol>> = dao.getProtocolsByPlant(plantId)
    suspend fun getProtocolById(id: Long): Protocol? = dao.getProtocolById(id)
    suspend fun getActiveProtocols(): List<Protocol> = dao.getActiveProtocols()
    suspend fun insertProtocol(protocol: Protocol): Long = dao.insertProtocol(protocol)
    suspend fun updateProtocol(protocol: Protocol) = dao.updateProtocol(protocol)
    suspend fun deleteProtocol(protocol: Protocol) = dao.deleteProtocol(protocol)

    suspend fun getStages(protocolId: Long): List<ProtocolStage> = stageDao.getStagesByProtocol(protocolId)
    fun watchStages(protocolId: Long): Flow<List<ProtocolStage>> = stageDao.watchStagesByProtocol(protocolId)
    suspend fun insertStage(stage: ProtocolStage): Long = stageDao.insertStage(stage)
    suspend fun updateStage(stage: ProtocolStage) = stageDao.updateStage(stage)
    suspend fun deleteStage(stage: ProtocolStage) = stageDao.deleteStage(stage)
    suspend fun replaceStages(protocolId: Long, blocks: List<ProtocolStage>) {
        stageDao.clearStages(protocolId)
        blocks.sortedBy { it.sortOrder }.forEachIndexed { index, block ->
            stageDao.insertStage(block.copy(protocolId = protocolId, sortOrder = index))
        }
    }
}

class StageEntryRepository(private val dao: StageEntryDao) {
    suspend fun getStageEntriesByProtocol(protocolId: Long): List<StageEntry> = dao.getStageEntriesByProtocol(protocolId)
    fun watchStageEntries(plantId: Long): Flow<List<StageEntry>> = dao.watchStageEntries(plantId)
    suspend fun insertStageEntry(entry: StageEntry): Long = dao.insertStageEntry(entry)
    suspend fun getLatestStageEntry(plantId: Long): StageEntry? = dao.getLatestStageEntry(plantId)
}

class EventRepository(private val dao: EventDao) {
    fun getEventsByPlant(plantId: Long): Flow<List<GrowEvent>> = dao.getEventsByPlant(plantId)
    suspend fun getEventsByPlantSnapshot(plantId: Long): List<GrowEvent> = dao.getEventsByPlantSnapshot(plantId)
    suspend fun getEventsByGroup(groupId: String): List<GrowEvent> = dao.getEventsByGroup(groupId)
    fun getAllEvents(): Flow<List<GrowEvent>> = dao.getAllEvents()
    suspend fun getAllEventsSnapshot(): List<GrowEvent> = dao.getAllEventsSnapshot()
    suspend fun getEventById(id: Long): GrowEvent? = dao.getEventById(id)
    suspend fun insertEvent(event: GrowEvent): Long = dao.insertEvent(event)
    suspend fun updateEvent(event: GrowEvent) = dao.updateEvent(event)
    suspend fun deleteEvent(event: GrowEvent) = dao.deleteEvent(event)
    suspend fun getEventsByType(type: String, limit: Int): List<GrowEvent> = dao.getEventsByType(type, limit)
    suspend fun getEventsBetween(from: Long, to: Long): List<GrowEvent> = dao.getEventsBetween(from, to)
    suspend fun getEventsByTypeInRange(plantId: Long, type: String, from: Long, to: Long): List<GrowEvent> =
        dao.getEventsByTypeInRange(plantId, type, from, to)

    suspend fun getActiveEpochDays(): List<Long> = dao.getActiveEpochDays()
}

class SuperCycleRepository(private val dao: SuperCycleDao) {
    suspend fun getSuperCycleByPlant(plantId: Long): SuperCycleConfig? = dao.getSuperCycleByPlant(plantId)
    suspend fun insertSuperCycle(config: SuperCycleConfig): Long = dao.insertSuperCycle(config)
    suspend fun updateSuperCycle(config: SuperCycleConfig) = dao.updateSuperCycle(config)
    suspend fun getAllConfigs(): List<SuperCycleConfig> = dao.getAllConfigs()
}

class AchievementRepository(private val dao: AchievementDao) {
    fun getAllAchievements(): Flow<List<Achievement>> = dao.getAllAchievements()
    suspend fun getUnlockedAchievements(): List<Achievement> = dao.getUnlockedAchievements()
    suspend fun insertAchievement(achievement: Achievement): Long = dao.insertAchievement(achievement)
    suspend fun updateAchievement(achievement: Achievement) = dao.updateAchievement(achievement)
    suspend fun getTotalXp(): Int = dao.getTotalXp()
}

class ReminderRepository(
    private val dao: ReminderDao,
    private val plantDao: PlantDao
) {
    fun getAllActiveReminders(): Flow<List<Reminder>> = dao.getAllActiveReminders()
    suspend fun getRemindersForPlant(plantId: Long): List<Reminder> = dao.getRemindersForPlant(plantId)
    suspend fun getActiveRemindersSnapshot(): List<Reminder> = dao.getActiveRemindersSnapshot()
    suspend fun insertReminder(reminder: Reminder): Long = dao.insertReminder(reminder)
    suspend fun updateReminder(reminder: Reminder) = dao.updateReminder(reminder)
    suspend fun deleteReminder(reminder: Reminder) = dao.deleteReminder(reminder)
    suspend fun plantName(plantId: Long?): String? = if (plantId == null) null else plantDao.getPlantById(plantId)?.name
}

class BreedingRepository(private val dao: BreedingDao) {
    fun getAllProjects(): Flow<List<BreedingProject>> = dao.getAllProjects()
    suspend fun getProjectById(id: Long): BreedingProject? = dao.getProjectById(id)
    suspend fun insertProject(project: BreedingProject): Long = dao.insertProject(project)
    suspend fun deleteProject(project: BreedingProject) = dao.deleteProject(project)
    suspend fun getCrossesByProject(projectId: Long): List<BreedingCross> = dao.getCrossesByProject(projectId)
    fun getAllCrosses(): Flow<List<BreedingCross>> = dao.getAllCrosses()
    suspend fun insertCross(cross: BreedingCross): Long = dao.insertCross(cross)
    suspend fun deleteCross(cross: BreedingCross) = dao.deleteCross(cross)
}

class JournalRepository(private val dao: JournalDao) {
    suspend fun getJournalEntries(plantId: Long): List<GrowEvent> = dao.getJournalEntries(plantId)
}