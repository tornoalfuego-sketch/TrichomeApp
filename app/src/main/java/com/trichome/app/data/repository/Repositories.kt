package com.trichome.app.data.repository

import com.trichome.app.data.dao.*
import com.trichome.app.data.entity.*
import kotlinx.coroutines.flow.Flow

class PlantRepository(private val dao: PlantDao) {
    fun getAllPlants(): Flow<List<Plant>> = dao.getAllPlants()
    suspend fun getPlantById(id: Long): Plant? = dao.getPlantById(id)
    suspend fun getPlantsByTent(tentId: Long): List<Plant> = dao.getPlantsByTent(tentId)

    /**
     * Plants whose tent was deleted.
     *
     * Reachable because `getPlantsByTent` filters on `tentId` and the foreign key
     * is `ON DELETE SET NULL`, so without this these rows exist and are counted
     * but cannot be shown, opened, edited or deleted from anywhere.
     */
    fun getUnassignedPlants(): Flow<List<Plant>> = dao.getUnassignedPlants()

    /** Moves a plant into [tentId], at the end of that tent's order. */
    suspend fun assignPlantToTent(plantId: Long, tentId: Long) =
        dao.assignPlantToTent(plantId, tentId)
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

    /** A plant's journal row count lives on [EventRepository]; nothing here counts events. */

    /**
     * Archives a plant: `isActive = false`, nothing else.
     *
     * ## What "finalizar cultivo" writes, and what it must never write
     *
     * It writes **one column**: `plants.isActive`. That is the terminal state, and it is
     * a state on the row rather than the row's absence.
     *
     * There is no `deletePlant` call anywhere on this path, and that is the whole
     * design. `grow_events` carries `ON DELETE CASCADE` from `plants`, so deleting the
     * plant would take the grower's entire journal with it — the one thing this app must
     * never do, and the reason `PlantDeletionNotice` has to warn about it at all. Every
     * other table keyed to the plant (`protocols`, `stage_entries`, `reminders`,
     * `super_cycle_configs`) carries a `plantId` with no foreign key, so a delete would
     * additionally leave those rows as orphans no screen could reach: the outcome the
     * `getUnassignedPlants` defect is a documented example of.
     *
     * Archiving loses nothing, hides nothing, and is reversible in the data model even
     * though the UI treats it as one-way.
     */
    suspend fun archivePlant(plantId: Long) = dao.setPlantActive(plantId, false)

    /**
     * Un-archives a plant. Not reachable from the UI.
     *
     * Present because the *data model* has to be able to, so "archived is terminal" is a
     * product decision this phase made in the interface rather than an accident of there
     * being no way back. `AGENTS.md` §11 asks for the loss budget to be provably zero, and
     * an irreversible write with no inverse in the model is harder to argue about than
     * one whose inverse simply has no button.
     */
    suspend fun setActive(plantId: Long, isActive: Boolean) = dao.setPlantActive(plantId, isActive)
}

class TentRepository(private val dao: GrowTentDao) {
    fun getAllTents(): Flow<List<GrowTent>> = dao.getAllTents()
    suspend fun getTentById(id: Long): GrowTent? = dao.getTentById(id)
    suspend fun insertTent(tent: GrowTent): Long = dao.insertTent(tent)
    suspend fun updateTent(tent: GrowTent) = dao.updateTent(tent)
    suspend fun deleteTent(tent: GrowTent) = dao.deleteTent(tent)

    /**
     * Tents as a snapshot.
     *
     * For the export picker, which reads once and closes. A `Flow` collected at every open
     * would hold a collector for a dialog that is already gone, and `getAllTents` is observed
     * for the screens that need a live list.
     */
    suspend fun getAllTentsSnapshot(): List<GrowTent> = dao.getAllTentsSnapshot()
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

    /** The whole timeline, oldest first, observed. */
    fun watchStageTimeline(plantId: Long): Flow<List<StageEntry>> = dao.watchStageTimeline(plantId)

    suspend fun countStageEntries(plantId: Long): Int = dao.countStageEntries(plantId)

    /**
     * Closes the plant's open entry at [exitedAt].
     *
     * The write behind a finalize. Kept on this repository, and separate from
     * [GrowStageRepository.applyTransition], so archiving can close a stage entry while
     * having no path at all to insert one — the guarantee that an archive never looks like
     * a stage change is a structural property here, not a convention.
     *
     * @return the number of rows closed. `0` means the plant had no open entry, which is a
     *   normal state.
     */
    suspend fun closeOpenEntries(plantId: Long, exitedAt: Long): Int =
        dao.closeOpenStageEntries(plantId, exitedAt)
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
    /**
     * Events inside a month window, observed. Replaces the one-shot
     * `getEventsBetween`, which left the calendar stale until the user changed
     * month. The window stays bounded, so this never reads the whole table.
     */
    fun watchEventsBetween(from: Long, to: Long): Flow<List<GrowEvent>> =
        dao.watchEventsBetween(from, to)
    suspend fun getEventsByTypeInRange(plantId: Long, type: String, from: Long, to: Long): List<GrowEvent> =
        dao.getEventsByTypeInRange(plantId, type, from, to)

    suspend fun getActiveEpochDays(): List<Long> = dao.getActiveEpochDays()

    /**
     * The plant's VPD history over a window, observed.
     *
     * The history is `grow_events.vpd` read directly — there is no separate table, and
     * `MIGRATION_4_5` adds none. See [EventDao.watchVpdHistory] for the window's bounds.
     */
    fun watchVpdHistory(plantId: Long, from: Long, to: Long): Flow<List<GrowEvent>> =
        dao.watchVpdHistory(plantId, from, to)

    /** One-shot form of [watchVpdHistory], for the chart's first load. */
    suspend fun getVpdHistory(plantId: Long, from: Long, to: Long): List<GrowEvent> =
        dao.getVpdHistory(plantId, from, to)

    /** The plant's events as a snapshot, for the exporter. */
    suspend fun getEventsForExport(plantId: Long): List<GrowEvent> = dao.getEventsForExport(plantId)

    /**
     * How many journal rows a plant has.
     *
     * The finalize confirmation quotes it, so the grower sees how much history an archive is
     * keeping rather than being asked to trust that it is. `COUNT(*)` on the plant's own rows
     * rather than on the table.
     */
    suspend fun countEvents(plantId: Long): Int = dao.countEventsForPlant(plantId)
}

/**
 * The one place the tent/plant supercycle rule is resolved.
 *
 * Since v3 a supercycle belongs to a tent and that tent's plants inherit it. Two
 * directions have to be answered, and answering either of them at the call site
 * is how the two halves drift apart again:
 *
 *  - [plantsInheriting]: config -> the plants it applies to.
 *  - [getConfigForPlant]: plant -> the config that applies to it.
 *
 * Same discipline the theme already follows with `solidSchemeFor` for colours:
 * one function, everyone calls it, no screen re-derives the rule. The worker that
 * notifies phase changes needs the same answer as the screen that displays it,
 * and it must not be a second implementation.
 *
 * Precedence is the grower's decision, not an implementation detail: the tent's
 * supercycle wins over the per-plant row, which stays as history.
 */
class SuperCycleRepository(
    private val dao: SuperCycleDao,
    private val plantDao: PlantDao
) {
    suspend fun getConfigForPlant(plantId: Long): SuperCycleConfig? {
        val tentId = plantRepoTentId(plantId)
        // A plant with no tent has nothing else to ask, which is the only case
        // where the pre-v3 row is the answer rather than history.
        return tentId?.let { dao.getConfigByTent(it) } ?: dao.getLegacyConfigByPlant(plantId)
    }

    /**
     * The config [tentId] owns, or null when it has none.
     *
     * Exposed because "does this tent have a supercycle yet" is a question the move
     * dialog must ask, and answering it by calling [getConfigForPlant] with a plant
     * would be wrong: that returns the *legacy per-plant* row when the plant has no
     * tent, which is history and not the destination tent's configuration.
     *
     * Same DAO query, no new one — this is a pass-through, not a second rule.
     */
    suspend fun getConfigByTent(tentId: Long): SuperCycleConfig? = dao.getConfigByTent(tentId)

    /**
     * The tent a config saved from [plantId] belongs to, or null when that plant
     * has no tent.
     *
     * Writes go through here so a new row is keyed by tent from the start rather
     * than by plant and migrated later.
     */
    suspend fun tentIdForPlant(plantId: Long): Long? = plantRepoTentId(plantId)

    private suspend fun plantRepoTentId(plantId: Long): Long? =
        plantDao.getPlantById(plantId)?.tentId

    suspend fun plantsInheriting(config: SuperCycleConfig): List<Plant> =
        dao.getPlantsInheriting(config.tentId, config.plantId)

    /** Configs left without a tent by the v2 -> v3 migration. See the DAO. */
    fun getConfigsWithoutTent(): Flow<List<SuperCycleConfig>> = dao.getConfigsWithoutTent()

    suspend fun insertSuperCycle(config: SuperCycleConfig): Long = dao.insertSuperCycle(config)
    suspend fun updateSuperCycle(config: SuperCycleConfig) = dao.updateSuperCycle(config)
    suspend fun deleteSuperCycle(config: SuperCycleConfig) = dao.deleteSuperCycle(config)
    suspend fun getAllConfigs(): List<SuperCycleConfig> = dao.getAllConfigs()
}

class AchievementRepository(private val dao: AchievementDao) {
    fun getAllAchievements(): Flow<List<Achievement>> = dao.getAllAchievements()
    suspend fun getUnlockedAchievements(): List<Achievement> = dao.getUnlockedAchievements()

    /**
     * Stores a badge unless one with the same name is already there.
     *
     * The name is the identity of a badge in this app: there is no separate
     * ledger, the app sums `xpReward` over unlocked rows, and a badge's text is
     * derived from shipped content. So a second row with the same name is not a
     * harmless duplicate — it is XP paid twice for one unlock, which is what
     * happened on one device (+600 XP, four rows, one badge).
     *
     * The de-duplication lives in the DAO statement, not in this function and not
     * in the caller, because a check in either of those is only a snapshot: two
     * live ViewModels can both read "not granted" and both write.
     *
     * [Achievement.id] is ignored. It is autoincrementing and therefore always
     * new, so it cannot express identity here.
     */
    suspend fun insertAchievement(achievement: Achievement): Long =
        dao.insertAchievementIfAbsent(
            name = achievement.name,
            description = achievement.description,
            icon = achievement.icon,
            xpReward = achievement.xpReward,
            isUnlocked = achievement.isUnlocked
        )

    /**
     * Collapses rows a pre-fix build wrote for one badge, keeping the oldest.
     *
     * Returns how many rows it removed. The caller's job is to report that number,
     * because a repair that silently succeeded is a repair nobody can verify.
     */
    suspend fun deleteDuplicateAchievements(name: String): Int =
        dao.deleteDuplicateAchievements(name)

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
    suspend fun updateProject(project: BreedingProject) = dao.updateProject(project)
    suspend fun deleteProject(project: BreedingProject) = dao.deleteProject(project)
    suspend fun getCrossesByProject(projectId: Long): List<BreedingCross> = dao.getCrossesByProject(projectId)
    fun getAllCrosses(): Flow<List<BreedingCross>> = dao.getAllCrosses()
    suspend fun insertCross(cross: BreedingCross): Long = dao.insertCross(cross)
    suspend fun updateCross(cross: BreedingCross) = dao.updateCross(cross)
    suspend fun deleteCross(cross: BreedingCross) = dao.deleteCross(cross)
}

class JournalRepository(private val dao: JournalDao) {
    suspend fun getJournalEntries(plantId: Long): List<GrowEvent> = dao.getJournalEntries(plantId)
}