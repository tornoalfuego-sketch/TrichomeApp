package com.trichome.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trichome.app.data.entity.StageEntry
import com.trichome.app.di.AppContainer
import com.trichome.app.model.GrowStagePlanner
import com.trichome.app.model.PlantEditStages
import com.trichome.app.model.PlantFinalizationPlan
import com.trichome.app.model.StageContext
import com.trichome.app.model.StageOption
import com.trichome.app.model.StageTransitionPlan
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Crop stage control: the stage timeline, the change-stage dialog and the finalize
 * confirmation for one plant.
 *
 * ## Why this is its own ViewModel
 *
 * All three surfaces need the same two facts — the plant's stage context and the stage
 * options for it — and all three resolve them the same way: the active protocol's blocks if
 * there is a protocol, the five lifecycle keys if there is not. A screen that resolved them
 * itself would be a third implementation of that rule, and the tent view and the plant view
 * would drift apart.
 *
 * ## The open entry, and why only its existence is carried
 *
 * A stage transition has to close whatever entry is open. The close statement is
 * `WHERE plantId = :id AND exitedAt IS NULL` rather than `WHERE id = :entryId`, so it cannot
 * leave a second entry open if the timeline was already inconsistent — and it means the planner
 * only needs to know *whether* an entry is open, which is what [StageContext.openStageName]
 * reports. The planner's `openEntryId` parameter therefore carries a sentinel, and
 * [OPEN_ENTRY_SENTINEL] is documented as the fact it is rather than an id.
 *
 * ## The clock
 *
 * [changeStage] and [planFinalization] take [nowMillis], so the tests pin the exact
 * `enteredAt` and `exitedAt` they assert on. The day label the dialog prints is resolved here
 * in the device's zone rather than in the planner, which is why `StageTransitionPlan` takes it
 * as a string instead of formatting it.
 */
class GrowStageViewModel(container: AppContainer) : ViewModel() {

    private val stageRepo = container.growStageRepository

    /** The plant's timeline, oldest first. Observed, so a transition repaints it. */
    var timeline by mutableStateOf<List<StageEntry>>(emptyList())
        private set

    /** The stage context the dialog is planned against, or null while loading. */
    var context by mutableStateOf<StageContext?>(null)
        private set

    /** The stages offered for this plant. Empty until [load] resolves. */
    var options by mutableStateOf<List<StageOption>>(emptyList())
        private set

    /** The last resolve outcome, so the dialog can show why nothing was written. */
    var lastPlan by mutableStateOf<StageTransitionPlan?>(null)
        private set

    /** The last finalize outcome, for the confirmation's body. */
    var lastFinalization by mutableStateOf<PlantFinalizationPlan?>(null)
        private set

    /**
     * Resolves everything the dialog needs.
     *
     * Both reads are awaited before anything is published. A dialog planned against a
     * half-resolved context is the same class of defect as the migration dialog's
     * `migrationInputs`: it would show the plant's stage as unset and offer the lifecycle keys
     * for a plant that actually has protocol blocks.
     */
    fun load(plantId: Long) {
        viewModelScope.launch {
            context = stageRepo.contextFor(plantId)
            options = stageRepo.optionsFor(plantId)
            timeline = stageRepo.timeline(plantId)
        }
    }

    /**
     * Keeps the timeline live.
     *
     * A separate collector from [load] for the reason `PlantDetailViewModel.loadPlant`
     * documents: a bare `collect()` inside the load would never return, so the resolved state
     * would never be published.
     */
    fun observeTimeline(plantId: Long) {
        viewModelScope.launch {
            stageRepo.watchTimeline(plantId).onEach { timeline = it }.collect()
        }
    }

    /**
     * Resolves a finalize without writing anything.
     *
     * Suspending and returning the plan, so the confirmation can print the counts — "3 eventos
     * de bitácora" — *before* the grower commits. `SuperCycleViewModel.load` fixed the same
     * class of bug: a fire-and-forget load read on the next line renders defaults.
     *
     * @return the plan, or null when the plant no longer exists.
     */
    suspend fun planFinalization(
        plantId: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): PlantFinalizationPlan? {
        val context = stageRepo.contextFor(plantId) ?: return null
        val plan = GrowStagePlanner.finalize(
            context = context,
            openEntryId = context.openStageName?.let { OPEN_ENTRY_SENTINEL },
            stageEntryCount = stageRepo.stageEntryCount(plantId),
            journalRowCount = stageRepo.journalRowCount(plantId),
            nowMillis = nowMillis
        )
        lastFinalization = plan
        return plan
    }

    /**
     * Applies a stage change.
     *
     * The context is re-read here rather than taken from [context], because the grower's tap
     * and this write are separated by a dialog: a stage entry written in between is the one
     * that has to be closed, and a cached context cannot know about it.
     *
     * The dialog passes a [StageOption] it rendered rather than a string it typed, so the name
     * that gets stored is one this app offered.
     *
     * [onChanged] receives the real outcome — a failed write is reported as a failure, never as a
     * stage the plant is now in.
     */
    fun changeStage(
        plantId: Long,
        option: StageOption,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        onChanged: (StageTransitionPlan?, Boolean) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val current = stageRepo.contextFor(plantId)
            if (current == null) {
                onChanged(null, false)
                return@launch
            }
            val plan = GrowStagePlanner.transition(
                context = current,
                option = option,
                openEntryId = current.openStageName?.let { OPEN_ENTRY_SENTINEL },
                nowMillis = nowMillis,
                dayLabelEs = dayLabelEs(nowMillis, zone)
            )
            lastPlan = plan
            if (!plan.isApplied) {
                onChanged(plan, false)
                return@launch
            }
            val written = stageRepo.applyTransition(plan)
            if (written != null) {
                timeline = stageRepo.timeline(plantId)
                // `plants.currentStage` was just rewritten, so the cached context is stale and
                // the next dialog must plan against the new value.
                context = stageRepo.contextFor(plantId)
            }
            onChanged(written, written != null)
        }
    }

    /**
     * Archives the plant.
     *
     * One column is written (`plants.isActive`) plus the closing of the open stage entry.
     * Nothing is deleted — see `GrowStageRepository.archive` for why that is the whole design
     * rather than a shortcut.
     */
    fun finalizePlant(
        plan: PlantFinalizationPlan,
        onFinalized: (PlantFinalizationPlan?, Boolean) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val written = stageRepo.archive(plan)
            if (written != null) {
                timeline = stageRepo.timeline(plan.plantId)
                context = stageRepo.contextFor(plan.plantId)
            }
            onFinalized(written, written != null)
        }
    }

    /** Restores an archived plant. Not offered by the UI; the model supports it. */
    fun reopenPlant(plantId: Long) {
        viewModelScope.launch { stageRepo.setActive(plantId, true) }
    }

    companion object {
        /**
         * Stands for "an entry is open" where only the fact matters.
         *
         * Never used as an id. The close statement filters on `exitedAt IS NULL` rather than on
         * a row id precisely so this sentinel cannot address the wrong row: a timeline that was
         * already inconsistent ends with exactly one open entry, whatever this constant is.
         */
        const val OPEN_ENTRY_SENTINEL: Long = 1L
    }
}

/**
 * `dd/MM/yyyy` in [zone].
 *
 * Formatted with US digits and swapped, so the date a dialog prints does not depend on the
 * phone's locale — the string is asserted by JVM tests that never change device settings, and
 * `dd/mm/yyyy` vs `mm/dd/yyyy` is not a distinction worth guessing at.
 */
internal fun dayLabelEs(epochMillis: Long, zone: ZoneId): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    return String.format(Locale.US, "%02d/%02d/%04d", date.dayOfMonth, date.monthValue, date.year)
}

/** `dd/MM/yyyy HH:mm` of [epochMillis] in [zone], for a timeline row. */
internal fun stageEntryDateLabelEs(epochMillis: Long, zone: ZoneId): String =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.US)
        .format(Instant.ofEpochMilli(epochMillis).atZone(zone))

/** Spanish label for a stage key. */
internal fun stageLabelEs(stage: String): String = PlantEditStages.labelEs(stage)