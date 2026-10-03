package com.trichome.app.data.repository

import com.trichome.app.data.dao.EventDao
import com.trichome.app.data.dao.PlantDao
import com.trichome.app.data.dao.ProtocolDao
import com.trichome.app.data.dao.ProtocolStageDao
import com.trichome.app.data.dao.StageEntryDao
import com.trichome.app.data.database.AppDatabase
import com.trichome.app.model.PlantFinalizationPlan
import com.trichome.app.model.ProtocolStageBlock
import com.trichome.app.model.StageContext
import com.trichome.app.model.StageOption
import com.trichome.app.model.StageTransitionPlan
import com.trichome.app.model.GrowStagePlanner
import androidx.room.withTransaction

/**
 * Crop stage control: the two writes behind "Cambiar de fase" and "Finalizar cultivo".
 *
 * ## Why these two operations share one repository
 *
+ * They share an invariant and one transaction each, and they share nothing else. Splitting
 * them would put a transition write in `StageEntryRepository`, an archive write in
+ * `PlantRepository`, and the rule that an archive must close a stage entry while never
+ * opening one somewhere in a ViewModel — where the next caller would not see it.
 *
+ * ## The invariant, stated once
 *
+ * **A plant has at most one open stage entry, and a stage change always closes the previous
+ * one before opening the next.** The timeline is the only record of what happened to the
+ * plant, so an entry left open — or two entries open at once — is not a cosmetic defect: it
+ * makes "which stage is this plant in" unanswerable except by the denormalised
+ * `plants.currentStage`, which is a cache of this table rather than its record.
 *
+ * ## What neither operation does
 *
+ * Neither issues a `DELETE`. `grow_events` carries `ON DELETE CASCADE` from `plants`, so a
+ * delete would take the grower's entire journal with it, and the tables keyed to a plant by
+ * bare id — `protocols`, `stage_entries`, `reminders`, `super_cycle_configs` — would be left
+ * as orphans no screen can reach. The data-loss budget here is zero, which is why
+ * "Finalizar cultivo" archives rather than deletes.
 */
class GrowStageRepository(
    private val stageDao: StageEntryDao,
    private val plantDao: PlantDao,
    private val eventDao: EventDao,
    private val protocolDao: ProtocolDao,
    private val protocolStageDao: ProtocolStageDao,
    private val database: AppDatabase
) {

    /**
     * The plant's stage context, resolved from the tables rather than assembled by a caller.
     *
     * The open entry is read here so [StageTransitionPlan.closesOpenEntry] is an
     * observation. Reading it in the dialog instead would leave a window between the tap and
     * the confirm in which a stage entry written by another surface goes unclosed.
     */
    suspend fun contextFor(plantId: Long): StageContext? {
        val plant = plantDao.getPlantById(plantId) ?: return null
        val open = stageDao.getOpenStageEntry(plantId)
        // The active protocol only: `getActiveProtocols` is the one place the active flag is
        // interpreted, so "the protocol this plant follows" has a single answer in this app.
        val protocol = protocolDao.getActiveProtocols().firstOrNull { it.plantId == plantId }
        return StageContext(
            plantId = plant.id,
            plantName = plant.name,
            currentStage = plant.currentStage,
            growStartMillis = plant.growStartTimestamp,
            openStageName = open?.stageName,
            isActive = plant.isActive,
            protocolId = protocol?.id
        )
    }

    /** The stages offered for [plantId]. Protocol blocks if there is a protocol. */
    suspend fun optionsFor(plantId: Long): List<StageOption> {
        val context = contextFor(plantId) ?: return emptyList()
        val protocol = protocolDao.getActiveProtocols().firstOrNull { it.plantId == plantId }
        val blocks = protocol
            ?.let { protocolStageDao.getStagesByProtocol(it.id) }
            .orEmpty()
            .map { ProtocolStageBlock(it.stageName, it.durationDays, it.sortOrder) }
        return GrowStagePlanner.optionsFor(context, blocks)
    }

    /**
     * Closes the open entry, opens the new one and mirrors the cache — atomically.
     *
     * @return the plan that was applied, or null when the write failed. Returning the plan
     *   on success and null on failure is what keeps a dialog from showing a confirmation
     *   for a transition that did not happen.
     */
    suspend fun applyTransition(plan: StageTransitionPlan): StageTransitionPlan? =
        database.withTransaction {
            if (!plan.isApplied) return@withTransaction null
            runCatching {
                if (plan.closesOpenEntry) {
                    // `exitedAt` is the same instant the new entry opens, so the timeline is
                    // contiguous: no gap and no overlap between the two entries. The statement
                    // closes *every* open row, so a timeline that was already inconsistent ends
                    // with exactly one open entry rather than accumulating another.
                    stageDao.closeOpenStageEntries(plan.plantId, plan.exitedAt)
                }
                stageDao.insertStageEntry(
                    com.trichome.app.data.entity.StageEntry(
                        protocolId = plan.protocolId,
                        plantId = plan.plantId,
                        stageName = plan.stageName,
                        enteredAt = plan.enteredAt,
                        exitedAt = null
                    )
                )
                plantDao.setCurrentStage(plan.plantId, plan.stageName)
            }.fold(
                onSuccess = { plan },
                onFailure = { null }
            )
        }

    /**
     * Archives a plant and closes its open stage entry.
     *
     * ## The two writes, and their order
     *
     * Close the entry, then set `isActive = 0`. Both are inside one transaction, so the order
     * only matters for the failure the transaction cannot prevent — a process death. With the
     * close first, the worst surviving state is an archived plant whose entry is still open,
     * which is visible and repairable. The reverse order would leave an archived plant with no
     * way to learn what stage it ended in.
     *
     * No `stage_entries` row is inserted, and no row is deleted. An archive is not a stage
     * change and must never look like one.
     *
     * @return the plan when the write succeeded, null otherwise.
     */
    suspend fun archive(plan: PlantFinalizationPlan): PlantFinalizationPlan? =
        database.withTransaction {
            if (!plan.isArchived) return@withTransaction null
            runCatching {
                if (plan.closesOpenStageEntry) {
                    stageDao.closeOpenStageEntries(plan.plantId, plan.finalizedAt)
                }
                plantDao.setPlantActive(plan.plantId, false)
            }.fold(
                onSuccess = { plan },
                onFailure = { null }
            )
        }

    /** How many entries a plant has, open or closed. For the finalize confirmation. */
    suspend fun stageEntryCount(plantId: Long): Int = stageDao.countStageEntries(plantId)

    /** How many journal rows a plant has. For the finalize confirmation. */
    suspend fun journalRowCount(plantId: Long): Int = eventDao.countEventsForPlant(plantId)

    /** The whole timeline, oldest first. */
    fun watchTimeline(plantId: Long) = stageDao.watchStageTimeline(plantId)

    /** The timeline as a snapshot, for a screen that reads it once. */
    suspend fun timeline(plantId: Long) = stageDao.getTimelineForExport(plantId)

    /**
     * Restores an archived plant.
     *
     * Not reachable from the UI. Present because the *model* has to be able to, so "archived
     * is terminal" is a product decision this phase made in the interface rather than an
     * accident of there being no way back — which is the argument `AGENTS.md` §11 needs for a
     * one-way write on the grower's own data.
     */
    suspend fun setActive(plantId: Long, isActive: Boolean) =
        plantDao.setPlantActive(plantId, isActive)
}