package com.trichome.app.viewmodel

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.trichome.app.data.entity.*
import com.trichome.app.data.repository.*
import com.trichome.app.di.AppContainer
import com.trichome.app.domain.vision.PhotoAnalyzer
import com.trichome.app.model.*
import com.trichome.app.data.repository.toAchievementRow
import com.trichome.app.model.CalendarWindow
import com.trichome.app.worker.ReminderAlarmScheduler
import com.trichome.app.worker.ReminderCancellation
import com.trichome.app.worker.ReminderSchedulerWorker
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** Factory helper: `viewModelFactory { initializer { ... } }` (per architecture rules). */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: (AppContainer) -> VM
): VM {
    val container = container()
    return androidx.lifecycle.viewmodel.compose.viewModel(
        factory = viewModelFactory { initializer { create(container) } }
    )
}

/** Resolves the [AppContainer] from the current composition. */
@Composable
fun container(): AppContainer {
    val context = androidx.compose.ui.platform.LocalContext.current
    return (context.applicationContext as com.trichome.app.TrichomeApp).appContainer
}

/* ─────────────────────────── Home ─────────────────────────────────────── */

class HomeViewModel(container: AppContainer) : ViewModel() {
    val plants: StateFlow<List<Plant>> = container.plantRepository.getAllPlants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tents: StateFlow<List<GrowTent>> = container.tentRepository.getAllTents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val events: StateFlow<List<GrowEvent>> = container.eventRepository.getAllEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val reminders: StateFlow<List<Reminder>> = container.reminderRepository.getAllActiveReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var streak by mutableStateOf(0)
        private set

    init {
        viewModelScope.launch {
            val epochDays = container.eventRepository.getActiveEpochDays().toSet()
            streak = Gamification.currentStreak(epochDays, java.time.LocalDate.now().toEpochDay())
        }
    }
}

/* ─────────────────────────── Tents ────────────────────────────────────── */

class TentViewModel(container: AppContainer) : ViewModel() {
    private val plantRepo = container.plantRepository
    private val tentRepo = container.tentRepository

    val tents: StateFlow<List<GrowTent>> = tentRepo.getAllTents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val plants: StateFlow<List<Plant>> = plantRepo.getAllPlants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTent(name: String, location: String, capacity: Int, lightType: String, watts: Int, isActive: Boolean) {
        viewModelScope.launch {
            tentRepo.insertTent(
                GrowTent(
                    name = name,
                    location = location,
                    capacity = capacity,
                    lightType = lightType,
                    lightPowerWatts = watts,
                    isActive = isActive
                )
            )
        }
    }

    fun updateTent(tent: GrowTent) = viewModelScope.launch { tentRepo.updateTent(tent) }

    fun deleteTent(tent: GrowTent) = viewModelScope.launch { tentRepo.deleteTent(tent) }

    fun addPlant(name: String, tentId: Long?, strain: String, stage: String, growStart: Long) {
        viewModelScope.launch {
            val siblings = tentId?.let { plantRepo.getPlantsByTent(it) } ?: emptyList()
            plantRepo.insertPlant(
                Plant(
                    name = name,
                    tentId = tentId,
                    sortOrder = siblings.size,
                    growStartTimestamp = growStart,
                    currentStage = stage,
                    strain = strain
                )
            )
        }
    }

    fun moveUp(plant: Plant) = viewModelScope.launch { plantRepo.movePlantUp(plant) }
    fun moveDown(plant: Plant) = viewModelScope.launch { plantRepo.movePlantDown(plant) }
    fun deletePlant(plant: Plant) = viewModelScope.launch { plantRepo.deletePlant(plant) }

    fun updatePlant(plant: Plant) = viewModelScope.launch { plantRepo.updatePlant(plant) }

    /**
     * Plants whose tent was deleted.
     *
     * They are surfaced so the promise in the delete dialog -- "sus plantas no se
     * borrarán, quedarán sin asignar a ninguna carpa" -- is true somewhere the
     * grower can act on. Before this, `tentId` became null, every query filtered
     * it away, and the plants were unreachable: counted in the totals, visible
     * nowhere.
     */
    val unassignedPlants: StateFlow<List<Plant>> = plantRepo.getUnassignedPlants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Puts a plant into [tentId].
     *
     * The recovery path for an unassigned plant, and the step the tent delete
     * flow asks for before it lets the tent go: a tent with plants in it is
     * refused rather than emptied, because emptying it is what made them
     * unreachable in the first place.
     */
    fun assignPlantToTent(plantId: Long, tentId: Long) =
        viewModelScope.launch { plantRepo.assignPlantToTent(plantId, tentId) }
}

/* ─────────────────────────── Plant detail ─────────────────────────────── */

/**
 * Screen state of a plant detail.
 *
 * The endless `"Cargando planta…"` spinner came from having no terminal state
 * for a missing id: `loadPlant` wrote nothing when the lookup returned `null`,
 * so the screen had only `Loading` left to draw. Every load now lands on exactly
 * one of these three.
 */
sealed interface PlantDetailUiState {
    /** The lookup is in flight. */
    data object Loading : PlantDetailUiState

    /**
     * The plant resolved.
     *
     * [stageProgress] is null when no active protocol applies, which is a normal
     * state and not a failure.
     */
    data class Success(
        val plant: Plant,
        val daysInGrow: Int,
        val stageProgress: StageProgressEngine.StageProgress?
    ) : PlantDetailUiState

    /** [message] is Spanish and ready to be shown to the user as-is. */
    data class Error(val message: String) : PlantDetailUiState
}

/**
 * The decision behind [PlantDetailUiState], free of Android and Compose so it
 * runs on the JVM under test.
 *
 * [load] cannot return [PlantDetailUiState.Loading]: a lookup that is missing,
 * that fails, or that never answers all end on a terminal state, so no caller
 * can be left hanging on a spinner.
 */
object PlantDetailLoader {

    /** Upper bound on one plant lookup. Room reads are local; this is slack. */
    const val LOOKUP_TIMEOUT_MS = 5_000L

    private const val ERROR_NOT_FOUND =
        "No encontramos esta planta. Puede que se haya eliminado."
    private const val ERROR_TIMEOUT =
        "La planta tardó demasiado en cargar. Inténtalo de nuevo."
    private const val ERROR_READ =
        "No se pudo leer la planta desde la base de datos."

    /**
     * Resolves the plant into a terminal [PlantDetailUiState].
     *
     * @param lookupPlant read of the row, bounded by [timeoutMillis].
     * @param stageProgressFor best-effort protocol progress. It receives the
     *   plant this call itself resolved, never a field another coroutine may not
     *   have written yet — that read is the race this shape removes.
     */
    suspend fun load(
        lookupPlant: suspend () -> Plant?,
        stageProgressFor: suspend (Plant) -> StageProgressEngine.StageProgress? = { null },
        nowMillis: Long = System.currentTimeMillis(),
        timeoutMillis: Long = LOOKUP_TIMEOUT_MS
    ): PlantDetailUiState {
        val plant = try {
            withTimeout(timeoutMillis) { lookupPlant() }
        } catch (e: TimeoutCancellationException) {
            // Only this scope was cancelled; the caller's job is still active,
            // so reporting a failure here is safe.
            return PlantDetailUiState.Error(ERROR_TIMEOUT)
        } catch (e: CancellationException) {
            // The caller went away. Structured concurrency wins over reporting.
            throw e
        } catch (e: Exception) {
            return PlantDetailUiState.Error(ERROR_READ)
        }
        if (plant == null) return PlantDetailUiState.Error(ERROR_NOT_FOUND)

        // Missing or broken protocol data costs the stage card, never the plant.
        val progress = try {
            withTimeoutOrNull(timeoutMillis) { stageProgressFor(plant) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

        return PlantDetailUiState.Success(
            plant = plant,
            daysInGrow = StageProgressEngine.daysInGrow(plant.growStartTimestamp, nowMillis),
            stageProgress = progress
        )
    }
}

class PlantDetailViewModel(container: AppContainer) : ViewModel() {
    private val plantRepo = container.plantRepository
    private val superCycleRepo = container.superCycleRepository
    private val eventRepo = container.eventRepository
    private val protocolRepo = container.protocolRepository
    private val stageEntryRepo = container.stageEntryRepository

    /** Whole-screen state. Starts and reloads on [PlantDetailUiState.Loading]. */
    var uiState by mutableStateOf<PlantDetailUiState>(PlantDetailUiState.Loading)
        private set

    var superCycleResult by mutableStateOf<SuperCycleResult?>(null)
        private set

    var latestStageEntry by mutableStateOf<StageEntry?>(null)
        private set

    /**
     * Set once the grower deletes this plant, so a load that lands afterwards
     * does not blame them for a read that only failed because the row they just
     * removed is gone. See [deletePlant].
     */
    private var retiredPlantId: Long? = null

    /**
     * Single source of truth for this plant's events.
     *
     * There used to be a second `MutableStateFlow` written on every emission
     * alongside this field; one writer, one value.
     */
    var events by mutableStateOf<List<GrowEvent>>(emptyList())
        private set

    /**
     * Loads [plantId] and publishes a terminal [uiState].
     *
     * Stage progress is resolved here, from the plant this same call returned.
     * The previous `loadStageProgress` read the plain `plant` field from a
     * second coroutine launched in the same `LaunchedEffect`, so on a cold open
     * it always found null and the stage card never rendered.
     */
    fun loadPlant(plantId: Long) {
        viewModelScope.launch {
            uiState = PlantDetailUiState.Loading
            val state = PlantDetailLoader.load(
                lookupPlant = { plantRepo.getPlantById(plantId) },
                stageProgressFor = { plant -> resolveStageProgress(plantId, plant) }
            )
            // A retired plant that no longer resolves is the expected outcome of
            // a delete, not a read failure: publishing Error here would put
            // "no pudimos abrir la planta" on a row the user removed seconds
            // ago. The state is left alone and the screen pops instead.
            if (retiredPlantId == plantId && state is PlantDetailUiState.Error) return@launch
            uiState = state
        }
        // Events stream forever, so they need their own coroutine: a bare
        // `collect()` inside the load above would never return and would keep
        // the state from ever being published.
        viewModelScope.launch {
            eventRepo.getEventsByPlant(plantId)
                .onEach { events = it }
                .collect()
        }
    }

    /**
     * Progress of the protocol bound to [plantId], derived from [current]'s own
     * grow start.
     *
     * @return null when the plant has no active protocol or its stages are
     *   unusable — both are normal and leave the rest of the screen intact.
     */
    private suspend fun resolveStageProgress(
        plantId: Long,
        current: Plant,
        now: Long = System.currentTimeMillis()
    ): StageProgressEngine.StageProgress? {
        val protocol = protocolRepo.getActiveProtocols().firstOrNull { it.plantId == plantId }
            ?: return null
        val blocks = protocolRepo.getStages(protocol.id)
            .sortedBy { it.sortOrder }
            .map { StageProgressEngine.StageBlock(it.stageName, it.durationDays) }
        if (blocks.isEmpty()) return null
        return StageProgressEngine.calculateProgress(
            blocks = blocks,
            startTimestamp = current.growStartTimestamp,
            nowTimestamp = now
        )
    }

    fun loadSuperCycle(plantId: Long, now: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            superCycleRepo.getConfigForPlant(plantId)?.let { config ->
                superCycleResult = SuperCycleEngine.calculateSuperCycle(
                    cycleStartAt = config.cycleStartAt,
                    lightHours = config.lightHours,
                    darkHours = config.darkHours
                )
            }
        }
    }

    fun loadLatestStage(plantId: Long) {
        viewModelScope.launch {
            latestStageEntry = stageEntryRepo.getLatestStageEntry(plantId)
        }
    }

    /* ── Tent migration ───────────────────────────────────────────── */

    private val tentRepo = container.tentRepository

    /** Tents, for the "Cambiar de carpa" destination list. */
    val tents: StateFlow<List<GrowTent>> = tentRepo.getAllTents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The photoperiod each tent runs, keyed by `tentId`.
     *
     * Read through [SuperCycleRepository] rather than off the config id, because the
     * v3 precedence — the tent's row wins, the per-plant row is history — lives in
     * exactly one place and answering it anywhere else is how the two halves drift.
     * A tent with no config row is absent from the map, which is the state
     * [PlantMigrationPlanner] resolves against.
     */
    private val tentPhotoperiods = mutableStateOf<Map<Long, PhotoperiodConfig>>(emptyMap())

    /**
     * The supercycle each candidate tent owns, keyed by `tentId`.
     *
     * An absent key means the tent has **no config row at all** — a real state, not a
     * missing argument, and the one `PlantMigrationPlanner` resolves against with
     * `requiresDestinationConfigWrite`. That is why an unread or incomplete map is
     * dangerous: it would make every destination look unconfigured and quietly plan
     * 18/6 writes. Hence suspending and returning, so the screen cannot read a
     * half-resolved map.
     *
     * @param tentIds the tents to resolve. Passed in from the observed list rather than
     *   re-queried, so this needs no new DAO method.
     */
    suspend fun loadMigrationInputs(tentIds: List<Long>): Map<Long, PhotoperiodConfig> {
        val resolved = mutableMapOf<Long, PhotoperiodConfig>()
        tentIds.forEach { tentId ->
            superCycleRepo.getConfigByTent(tentId)?.let { config ->
                val photoperiod = PhotoperiodConfig(config.lightHours, config.darkHours)
                // Invalid hours are dropped rather than passed on: `PlantMigrationPlanner`
                // sanitizes them too, and a map that carried a 0/0 row would make the
                // destination look configured when it has nothing usable.
                if (photoperiod.isValid) resolved[tentId] = photoperiod
            }
        }
        tentPhotoperiods.value = resolved
        return resolved
    }

    /**
     * The plant's state as [PlantMigrationPlanner] wants it.
     *
     * The photoperiod comes from [SuperCycleRepository.getConfigForPlant], which is the
     * one place the v3 precedence — tent row first, legacy per-plant row as history —
     * is resolved. Reading the config off the plant id here would be a second
     * implementation of a rule the repository already owns.
     */
    suspend fun migrationStateFor(plant: Plant): PlantCycleState {
        val config = superCycleRepo.getConfigForPlant(plant.id)
        val photoperiod = config?.let { PhotoperiodConfig(it.lightHours, it.darkHours) }
            ?.takeIf { it.isValid }
        return PlantCycleState(
            plantId = plant.id,
            plantName = plant.name,
            photoperiod = photoperiod,
            cycleStartAt = config?.cycleStartAt
        )
    }

    /**
     * Applies a resolved [plan] for a move into [destinationTentId].
     *
     * The write is [PlantRepository.assignPlantToTent], the same single
     * `UPDATE plants SET tentId = …` the rest of the app uses. Nothing here touches
     * `grow_events`: journal rows are keyed by `plantId`, so a tent move cannot drop
     * one, and the dialog says so rather than asking.
     *
     * When `plan.requiresDestinationConfigWrite` is true the destination tent is given
     * the config row it never had, keyed by `tentId`. That write is what makes the
     * tent's 18/6 (or the plant's own hours) stick; skipping it would leave the tent
     * still unconfigured while the plant reported a cycle nothing owned.
     *
     * [onMoved] receives the real outcome, so a failed write is never reported as a
     * successful move.
     */
    fun applyMigration(
        plantId: Long,
        destinationTentId: Long,
        plan: PlantMigrationPlan,
        now: Long = System.currentTimeMillis(),
        onMoved: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val ok = runCatching {
                // Give the tent its config before the plant arrives in it, so there is
                // no window where the plant inherits a tent with no supercycle.
                if (plan.requiresDestinationConfigWrite) {
                    val photoperiod = plan.photoperiod
                    if (photoperiod != null) {
                        superCycleRepo.insertSuperCycle(
                            SuperCycleConfig(
                                tentId = destinationTentId,
                                // Obsolete since v3 and deliberately null on a new row.
                                plantId = null,
                                lightHours = photoperiod.lightHours,
                                darkHours = photoperiod.darkHours,
                                cycleStartAt = plan.cycleStartAt,
                                presetType = "migrated"
                            )
                        )
                    }
                }
                plantRepo.assignPlantToTent(plantId, destinationTentId)
            }.isSuccess
            if (ok) {
                // The plant now resolves a different tent's config, so the phase card
                // on this screen is stale.
                superCycleResult = null
                loadSuperCycle(plantId, now)
            }
            onMoved(ok)
        }
    }

    /**
     * Writes an edited plant.
     *
     * The caller must pass the **whole** row, edited fields included: this
     * delegates to [PlantRepository.updatePlant], and an update that dropped a
     * field would write that entity default over the grower's real data.
     * [onSaved] receives the real outcome so a failed write is not shown as a
     * successful one.
     */
    fun updatePlant(plant: Plant, onSaved: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = runCatching { plantRepo.updatePlant(plant) }.isSuccess
            onSaved(ok)
        }
    }

    /**
     * Deletes the plant and takes the screen off it.
     *
     * A delete is not a read failure, so [uiState] is deliberately **not**
     * driven into [PlantDetailUiState.Error]: that state is a dead end by
     * design, and rendering it for a plant the user just removed would show
     * "no pudimos abrir la planta" for a row that existed seconds ago. Instead
     * the row is retired and the screen is told to leave.
     *
     * [onDeleted] is the only signal the UI acts on: it pops the back stack
     * once the write actually succeeded, so a failed delete never navigates away
     * from a plant that is still there.
     */
    fun deletePlant(plant: Plant, onDeleted: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = runCatching { plantRepo.deletePlant(plant) }.isSuccess
            if (ok) {
                retiredPlantId = plant.id
                // Clear the content so no stale card is drawn between the
                // delete and the pop.
                events = emptyList()
                superCycleResult = null
                latestStageEntry = null
            }
            onDeleted(ok)
        }
    }

    fun recordEvent(plantId: Long, event: GrowEvent) {
        viewModelScope.launch { eventRepo.insertEvent(event) }
    }
}

/* ─────────────────────────── Protocols ────────────────────────────────── */

class ProtocolViewModel(container: AppContainer) : ViewModel() {
    private val protocolRepo = container.protocolRepository
    private val stageEntryRepo = container.stageEntryRepository

    var protocols by mutableStateOf<List<Protocol>>(emptyList())
        private set
    var blocks by mutableStateOf<Map<Long, List<ProtocolStage>>>(emptyMap())
        private set

    fun loadProtocols(plantId: Long) {
        viewModelScope.launch {
            protocolRepo.getProtocolsByPlant(plantId)
                .onEach { list ->
                    protocols = list
                    list.forEach { p ->
                        blocks = blocks + (p.id to protocolRepo.getStages(p.id))
                    }
                }
                .collect()
        }
    }

    suspend fun saveProtocol(protocol: Protocol): Long = protocolRepo.insertProtocol(protocol)

    suspend fun replaceStages(protocolId: Long, blocks: List<ProtocolStage>) =
        protocolRepo.replaceStages(protocolId, blocks)

    suspend fun deleteProtocol(protocol: Protocol) = protocolRepo.deleteProtocol(protocol)

    fun logStageTransition(plantId: Long, protocolId: Long, stageName: String) {
        viewModelScope.launch {
            stageEntryRepo.insertStageEntry(
                StageEntry(
                    protocolId = protocolId,
                    plantId = plantId,
                    stageName = stageName,
                    enteredAt = System.currentTimeMillis(),
                    exitedAt = null
                )
            )
        }
    }
}

/* ─────────────────────────── SuperCycle ───────────────────────────────── */

class SuperCycleViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.superCycleRepository

    var config by mutableStateOf<SuperCycleConfig?>(null)
        private set
    var result by mutableStateOf<SuperCycleResult?>(null)
        private set

    /**
     * Configs the v2 -> v3 migration could not attach to a tent.
     *
     * Observed rather than read once, so deleting one from the screen removes it
     * from the list without the screen having to reconcile anything.
     */
    val configsWithoutTent: StateFlow<List<SuperCycleConfig>> = repo.getConfigsWithoutTent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Loads the tent's config and returns it.
     *
     * Suspending, and returning the row it resolved, is the fix for a data-loss
     * bug: the screen used to call a fire-and-forget `load` and read `config` on
     * the next line, which ran before the query resolved, so the sliders kept
     * their 18/6 defaults and saving without touching a slider overwrote the
     * saved photoperiod. Handing the value back makes that window impossible —
     * the caller cannot observe a half-finished load, because it is the one
     * being awaited.
     *
     * Returns null when the tent has no config yet, which is a resolved load
     * with nothing to show, not a failure.
     */
    suspend fun load(plantId: Long): SuperCycleConfig? {
        val existing = repo.getConfigForPlant(plantId)
        config = existing
        if (existing != null) {
            result = SuperCycleEngine.calculateSuperCycle(
                cycleStartAt = existing.cycleStartAt,
                lightHours = existing.lightHours,
                darkHours = existing.darkHours
            )
        }
        return existing
    }

    fun liveUpdate(lightHours: Int, darkHours: Int, cycleStartAt: Long) {
        result = SuperCycleEngine.calculateSuperCycle(
            cycleStartAt = cycleStartAt,
            lightHours = lightHours,
            darkHours = darkHours
        )
    }

    fun save(plantId: Long, lightHours: Int, darkHours: Int, cycleStartAt: Long, preset: String) {
        viewModelScope.launch {
            val existing = config
            val next = SuperCycleConfig(
                id = existing?.id ?: 0L,
                // Keyed by the tent from now on. plantId stays on an existing
                // row so a pre-v3 config keeps its provenance; a new row has none,
                // because there is no plant left to point at.
                tentId = repo.tentIdForPlant(plantId),
                plantId = existing?.plantId,
                lightHours = lightHours,
                darkHours = darkHours,
                cycleStartAt = cycleStartAt,
                presetType = preset
            )
            if (existing == null) repo.insertSuperCycle(next) else repo.updateSuperCycle(next)
            config = next
            result = SuperCycleEngine.calculateSuperCycle(
                cycleStartAt = cycleStartAt, lightHours = lightHours, darkHours = darkHours
            )
        }
    }

    /**
     * Deletes a config the migration left behind, at the grower's request.
     *
     * Nothing in the app calls this on its own. The rows are kept by default and
     * shown under "Sin carpa" precisely so the decision to lose one stays with
     * the person who owns it.
     */
    fun deleteWithoutTent(config: SuperCycleConfig) {
        viewModelScope.launch { repo.deleteSuperCycle(config) }
    }
}

/* ─────────────────────────── Journal ──────────────────────────────────── */

class JournalViewModel(container: AppContainer) : ViewModel() {
    private val eventRepo = container.eventRepository
    private val plantRepo = container.plantRepository
    private val reminderRepo = container.reminderRepository
    private val appContext = container.application.applicationContext

    val plants: StateFlow<List<Plant>> = plantRepo.getAllPlants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var events by mutableStateOf<List<GrowEvent>>(emptyList())
        private set

    /** Non-null when the last save attempt was rejected. */
    var saveError by mutableStateOf<String?>(null)
        private set

    fun loadEvents(plantId: Long? = null) {
        viewModelScope.launch {
            val source = if (plantId == null) eventRepo.getAllEvents() else eventRepo.getEventsByPlant(plantId)
            source.onEach { list -> events = list }.collect()
        }
    }

    fun addEvent(event: GrowEvent) {
        viewModelScope.launch { eventRepo.insertEvent(event) }
    }

    fun deleteEvent(event: GrowEvent) {
        viewModelScope.launch { eventRepo.deleteEvent(event) }
    }

    /**
     * Writes the edited row and reports the real outcome.
     *
     * `EventDao.updateEvent` existed with no caller, so a journal entry could be
     * created and deleted but never corrected. [onSaved] receives what actually
     * happened, and the dialog only closes on `true` -- closing on a refused write
     * would discard every edit the grower had just made.
     */
    fun updateEvent(event: GrowEvent, onSaved: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val written = runCatching { eventRepo.updateEvent(event) }.isSuccess
            saveError = if (written) null else "No se pudo guardar el evento."
            onSaved(written)
        }
    }

    fun clearSaveError() {
        saveError = null
    }

    /** Lets the UI report a rejected save (for example: no plant selected). */
    fun reportSaveError(message: String) {
        saveError = message
    }

    /**
     * Persists a reminder for every target plant and arms a real system alarm.
     *
     * The number of rows actually written is reported through [saveError]: with
     * no target selected this used to insert nothing while still looking saved.
     */
    fun persistReminder(reminder: Reminder, plantIds: List<Long>) {
        viewModelScope.launch {
            if (plantIds.isEmpty()) {
                saveError = "Selecciona al menos una planta para guardar el recordatorio."
                return@launch
            }
            var written = 0
            var armedAll = true
            plantIds.forEach { plantId ->
                val draft = reminder.copy(id = 0L, plantId = plantId)
                val id = runCatching { reminderRepo.insertReminder(draft) }.getOrNull() ?: return@forEach
                written++
                if (id > 0) {
                    if (!ReminderAlarmScheduler.schedule(appContext, draft.copy(id = id))) {
                        armedAll = false
                    }
                }
            }
            saveError = when {
                written == 0 -> "No se pudo guardar el recordatorio."
                !armedAll -> "Recordatorio guardado, pero el sistema no permitió programar la alarma exacta. Revisa los permisos de Ajustes."
                else -> null
            }
        }
    }
}

/* ─────────────────────────── Reminders ────────────────────────────────── */

private const val ERROR_REMINDER_NOT_ARMED =
    "Recordatorio guardado, pero el sistema no permitió programar la alarma exacta. " +
        "Revisa los permisos de Ajustes."

/**
 * Edit and delete for reminders.
 *
 * `ReminderDao.updateReminder` and `deleteReminder` had **zero** callers in
 * `main`: a reminder could be created and then never changed or removed, and no
 * UI could mark one inactive, so `ReminderAlarmScheduler.cancel` was reachable
 * only from a branch no screen could take.
 *
 * Both operations need something Room cannot do. A reminder is not just a row:
 * it is an `AlarmManager` exact alarm plus a `WorkManager` unique job, and
 * deleting the row leaves both registered. A delete here is therefore a full
 * cancel, and an edit re-arms at the new time.
 */
class ReminderViewModel(container: AppContainer) : ViewModel() {
    private val reminderRepo = container.reminderRepository
    private val appContext = container.application.applicationContext

    /**
     * Active reminders, observed.
     *
     * A snapshot read would show the row the grower just edited or deleted as
     * if nothing happened, which is the same class of bug as the calendar's
     * one-shot month query.
     */
    val activeReminders: StateFlow<List<Reminder>> = reminderRepo.getAllActiveReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Non-null when the last write or cancel failed. Spanish, shown as-is. */
    var saveError by mutableStateOf<String?>(null)
        private set

    fun clearSaveError() {
        saveError = null
    }

    /**
     * Writes the edited row and re-arms the alarm, so the change takes effect
     * instead of firing at the old time tomorrow.
     *
     * Only the WorkManager job is dropped first: the pending alarm is
     * re-registered for the same reminder at the new time right after, and
     * cancelling it would open a window where neither exists.
     */
    fun updateReminder(reminder: Reminder, onSaved: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val written = runCatching { reminderRepo.updateReminder(reminder) }.isSuccess
            if (!written) {
                saveError = "No se pudo guardar el recordatorio."
                onSaved(false)
                return@launch
            }
            ReminderCancellation.cancelStaleWork(reminder.id) { name ->
                WorkManager.getInstance(appContext).cancelUniqueWork(name)
            }
            val armed = ReminderAlarmScheduler.schedule(appContext, reminder)
            // Re-sync the recurring chain so the new interval is honoured even
            // if the alarm itself was refused.
            ReminderSchedulerWorker.syncNow(appContext)
            // The row landed, so the editor closes. A refused alarm is reported
            // separately: the change is saved, it just will not ring.
            saveError = armProblem(reminder, armed)
            onSaved(true)
        }
    }

    /**
     * Full cancel: the alarm, the unique job, then the row.
     *
     * The order matters. A row deleted while its alarm stays registered still
     * fires, and the receiver then finds nothing to look up — a "deleted"
     * reminder the system keeps nagging about.
     */
    fun deleteReminder(reminder: Reminder, onDeleted: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            ReminderCancellation.cancelAll(
                reminderId = reminder.id,
                cancelAlarm = { ReminderAlarmScheduler.cancel(appContext, it) },
                cancelWork = { WorkManager.getInstance(appContext).cancelUniqueWork(it) }
            )
            val deleted = runCatching { reminderRepo.deleteReminder(reminder) }.isSuccess
            saveError = if (deleted) null else "No se pudo eliminar el recordatorio."
            onDeleted(deleted)
        }
    }

    /**
     * What to tell the user after a re-arm.
     *
     * An inactive reminder is cancelled on purpose, so `schedule` returning
     * false is the expected outcome and not a failure to report.
     */
    private fun armProblem(reminder: Reminder, armed: Boolean): String? = when {
        !reminder.isActive -> null
        armed -> null
        else -> ERROR_REMINDER_NOT_ARMED
    }
}

/* ─────────────────────────── Calendar ─────────────────────────────────── */

class CalendarViewModel(container: AppContainer) : ViewModel() {
    private val eventRepo = container.eventRepository
    private val reminderRepo = container.reminderRepository
    private val plantRepo = container.plantRepository
    private val tentRepo = container.tentRepository
    private val appContext = container.application.applicationContext

    val plants: StateFlow<List<Plant>> = plantRepo.getAllPlants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Tents, for the estimated-climate card's latitude.
     *
     * This app has no location permission and no geocoder — it is offline by design —
     * so the only place a latitude can come from is a tent's free-text `location`
     * field. [com.trichome.app.model.ClimateCardCopy.resolveLocation] decides whether
     * that text actually contains coordinates and falls back to a documented default
     * when it does not; the card says which of the two it used.
     */
    val tents: StateFlow<List<GrowTent>> = tentRepo.getAllTents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var events by mutableStateOf<List<GrowEvent>>(emptyList())
        private set
    var reminders by mutableStateOf<List<Reminder>>(emptyList())
        private set

    /**
     * Window the collector is currently reading, or null before the first call.
     *
     * Compared on every [loadMonth] so the same month is not re-queried and the
     * previous collector is never left running next to the new one.
     */
    private var activeWindow: LongRange? = null
    private var monthCollector: Job? = null

    init {
        // Reminders are observed for the same reason the events are: a reminder
        // created or deleted from the calendar has to show up without leaving
        // the screen. `getActiveRemindersSnapshot()` was a one-shot read, so it
        // missed exactly the writes this screen performs itself.
        viewModelScope.launch {
            reminderRepo.getAllActiveReminders()
                .onEach { reminders = it }
                .collect()
        }
    }

    /** Day currently expanded in the detail list, in epoch millis. */
    var selectedDay by mutableStateOf<Long?>(null)
        private set

    var saveError by mutableStateOf<String?>(null)
        private set

    fun selectDay(millis: Long?) {
        selectedDay = millis
    }

    fun clearSaveError() {
        saveError = null
    }

    /**
     * Observes the events of the month window `[from, to]`.
     *
     * This replaces a one-shot `getEventsBetween(from, to)` that ran once per
     * `LaunchedEffect(month, filterPlantId)`, which is why a just-saved event
     * stayed invisible until the user changed month or plant filter: the write
     * succeeded, but nothing re-read it. The window is still honoured, so this
     * is a bounded query and not a whole-table read.
     *
     * Re-entrant calls with the same window are ignored — the collector is
     * already reading exactly those rows — and a different window cancels the
     * previous collector instead of running two of them.
     */
    fun loadMonth(from: Long, to: Long) {
        val next = from..to
        if (!CalendarWindow.shouldReload(activeWindow, next)) return
        activeWindow = next

        monthCollector?.cancel()
        monthCollector = viewModelScope.launch {
            eventRepo.watchEventsBetween(from, to)
                .onEach { events = it }
                .collect()
        }
    }

    /**
     * Writes an event on a specific day.
     *
     * [onSaved] receives the real outcome so the calendar sheet only closes on a
     * successful write instead of pretending an insert happened.
     */
    fun addEvent(event: GrowEvent, onSaved: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val id = runCatching { eventRepo.insertEvent(event) }.getOrNull()
            if (id != null && id > 0) {
                saveError = null
                onSaved(true)
            } else {
                saveError = "No se pudo guardar el evento en el calendario."
                onSaved(false)
            }
        }
    }

    /** Writes a reminder and arms its exact alarm. */
    fun addReminder(reminder: Reminder, onSaved: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val id = runCatching { reminderRepo.insertReminder(reminder) }.getOrNull()
            if (id != null && id > 0) {
                ReminderAlarmScheduler.schedule(appContext, reminder.copy(id = id))
                saveError = null
                onSaved(true)
            } else {
                saveError = "No se pudo guardar el recordatorio."
                onSaved(false)
            }
        }
    }

    fun deleteEvent(event: GrowEvent) {
        viewModelScope.launch { eventRepo.deleteEvent(event) }
    }

    /** Fake task markers: recurring reminders mapped onto the month window. */
    fun reminderDaysInRange(from: Long, to: Long): List<Long> {
        val out = mutableListOf<Long>()
        reminders.forEach { r ->
            if (r.recurrenceIntervalDays > 0) {
                var day = (from / 86_400_000L) * 86_400_000L + r.reminderTime
                while (day <= to) {
                    if (day >= from) out.add(day)
                    day += r.recurrenceIntervalDays * 86_400_000L
                }
            }
        }
        return out.distinct()
    }
}

/* ─────────────────────────── Terpenes ─────────────────────────────────── */

class TerpenesViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.terpenesRepository
    private val progress = container.terpeneProgress

    /**
     * F4: where the resin badge is written.
     *
     * The **existing** `achievements` repository, resolved from the same container
     * as everything else. A field rather than a `get()` because a property getter
     * would have `container` resolve to the imported `@Composable container()`
     * helper instead of the constructor parameter, and it would not compile. Naming
     * it once here also makes it obvious that the badge goes where every other
     * reward goes rather than somewhere of its own.
     */
    private val badgeAchievements = container.achievementRepository

    /**
     * F2: the module's temperature table, for the ten bands it measures.
     *
     * Held as a field because the constructor parameter is not a property, and
     * because both the initial load and the detail page's lazy fallback need it.
     */
    private val entourageContent = container.entourageContentRepository

    /**
     * F2's one volatility index, built once from both assets.
     *
     * The container already exposes both content repositories, so this is the
     * existing DI path and not a new one: the encyclopedia supplies the boiling
     * point for all 158 compounds and the Séquito module supplies the ten bands
     * it measures. [TerpeneVolatilityIndex.from] decides which is which and
     * labels every row, so no screen has to know where a number came from.
     *
     * Empty until the assets have been read, which is why it is a `var` and not
     * a `val`: the detail screen's `LaunchedEffect` awaits the same load it
     * already does for the entry itself.
     */
    var volatilityIndex by mutableStateOf(TerpeneVolatilityIndex(emptyList()))
        private set

    /**
     * F3: the agronomy block, keyed by terpene.
     *
     * Same lazy-fallback shape as [volatilityIndex], and for the same reason: the
     * detail page's `LaunchedEffect` can run before the initial load has
     * finished, and a page that showed a "no data" line because of a race would
     * be a lie about the content rather than about the timing.
     */
    var agronomyIndex by mutableStateOf(EntourageAgronomyIndex())

    // F4: the processing block, beside the agronomy index and for the same
    // reason. Derived once per content load rather than per card, so the badge and
    // the detail page read the same object and cannot disagree about how many
    // compounds the catalog documents.
    var processingIndex by mutableStateOf(EntourageProcessingIndex())
        private set

    /**
     * Names already in the `achievements` table, so the F4 badge cannot be paid
     * twice.
     *
     * The same idempotency rule `EntourageViewModel.awardedNames` uses, and for
     * the same reason: the table's primary key is an auto-generated id, so a second
     * insert of the same reward is a **new row** rather than an update. The badge
     * condition is derived from live state, so this grant has to be safe to
     * recompute on every progress emission — and progress emits on every
     * discovery, every quiz answer and every streak change.
     */
    private var awardedBadgeNames by mutableStateOf<Set<String>>(emptySet())
        private set

    var terpenes by mutableStateOf<List<Terpene>>(emptyList())
        private set
    var query by mutableStateOf("")
        private set
    var favoritesOnly by mutableStateOf(false)
        private set
    var familyFilter by mutableStateOf<String?>(null)
        private set
    var effectFilter by mutableStateOf<String?>(null)
        private set
    var aromaFilter by mutableStateOf<String?>(null)
        private set

    var families by mutableStateOf<List<String>>(emptyList())
        private set
    var effectGroups by mutableStateOf<List<String>>(emptyList())
        private set
    var aromaFamilies by mutableStateOf<List<String>>(emptyList())
        private set

    /* Progression */
    var discovered by mutableStateOf<Set<String>>(emptySet())
        private set
    var xp by mutableStateOf(0)
        private set
    var streak by mutableStateOf(0)
        private set
    var quizzesCorrect by mutableStateOf(0)
        private set
    var quizzesCompleted by mutableStateOf(0)
        private set
    var earnedBadges by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Longest terpene streak reached, which is what the streak medal reads. */
    private var bestStreak by mutableStateOf(0)
        private set


    val level: Int get() = TerpeneProgression.levelFor(xp)
    val levelProgress: Float get() = TerpeneProgression.levelProgress(xp)
    val rankTitle: String get() = TerpeneProgression.rankTitle(level)

    /** Badge requirements, derived from live state. */
    val badgeCounters: BadgeCounters
        get() = BadgeCounters(
            discoveredCount = discovered.size,
            familiesCompleted = TerpeneProgression.completedFamilies(familyMembers(), discovered),
            favorites = terpenes.count { it.isFavorite },
            bestStreak = bestStreak,
            quizzesCorrect = quizzesCorrect
        )

    val badges: List<Badge>
        get() = TerpeneProgression.badges(badgeCounters, earnedBadges)

    /**
     * The catalog reduced to what the family rule needs, so the reward logic
     * stays free of any dependency on the repository layer.
     */
    private fun familyMembers(): List<TerpeneFamilyMember> =
        terpenes.map { TerpeneFamilyMember(it.id, it.family) }

    init {
        viewModelScope.launch {
            val all = repo.getTerpenes()
            terpenes = all
            families = all.map { it.family }.filter { it.isNotBlank() }.distinct().sorted()
            effectGroups = all.map { it.effectGroup }.distinct().sorted()
            aromaFamilies = all.map { it.aromaFamily }.distinct().sorted()
            volatilityIndex = TerpeneVolatilityIndex.from(
                catalog = repo.volatilityRows(),
                measured = entourageContent.getVaporisation()
            )
            agronomyIndex = entourageContent.getAgronomyIndex()
            processingIndex = entourageContent.getProcessingIndex()
            // F4: read what has already been paid **before** the progress collector
            // can call `grantProcessingBadge`. Loaded here rather than lazily inside
            // the grant so a player who earned the badge in an earlier session is not
            // paid again the first time their discovered set happens to change.
            runCatching { badgeAchievements.getAllAchievements().first() }
                .onSuccess { rows ->
                    awardedBadgeNames = rows.map { it.name }.toSet()
                }
            refresh()
        }
        viewModelScope.launch {
            progress.progress.collect { p ->
                discovered = p.discovered
                streak = p.streak
                quizzesCorrect = p.quizCorrect
                quizzesCompleted = p.quizzesCompleted
                earnedBadges = p.earnedBadges
                bestStreak = maxOf(bestStreak, p.bestStreak)
                xp = TerpeneProgression.totalXp(
                    TerpeneXpSources(
                        discovered = p.discovered.size,
                        familiesCompleted = TerpeneProgression.completedFamilies(familyMembers(), p.discovered),
                        quizCorrect = p.quizCorrect,
                        quizzesCompleted = p.quizzesCompleted
                    )
                )
                // Bank anything newly earned, so a later dip in the counters
                // cannot revoke it.
                val held = TerpeneProgression.unlockedBadgeIds(badgeCounters, p.earnedBadges)
                if (held != p.earnedBadges) {
                    earnedBadges = held
                    progress.keepBadges(held)
                }
                // F4: the resin engineer badge. Evaluated here rather than on the
                // detail page, because its condition is the **whole** discovered set
                // and a page only knows about itself. The decision is pure
                // (`EntourageRewards.forProcessingRead`); this block only pays it,
                // and `pending` is what makes it safe to run on every emission.
                grantProcessingBadge()
            }
        }
    }

    /** Re-applies every active filter over the cached catalog. */
    private suspend fun refresh() {
        var list = repo.search(query)
        if (favoritesOnly) list = list.filter { it.isFavorite }
        familyFilter?.let { f -> list = list.filter { it.family == f } }
        effectFilter?.let { f -> list = list.filter { it.effectGroup == f } }
        aromaFilter?.let { f -> list = list.filter { it.aromaFamily == f } }
        terpenes = list
    }

    fun updateQuery(q: String) {
        query = q
        viewModelScope.launch { refresh() }
    }

    fun toggleFavoritesOnly() {
        favoritesOnly = !favoritesOnly
        viewModelScope.launch { refresh() }
    }

    fun toggleFamilyFilter(family: String?) {
        familyFilter = if (familyFilter == family) null else family
        viewModelScope.launch { refresh() }
    }

    fun toggleEffectFilter(group: String?) {
        effectFilter = if (effectFilter == group) null else group
        viewModelScope.launch { refresh() }
    }

    fun toggleAromaFilter(family: String?) {
        aromaFilter = if (aromaFilter == family) null else family
        viewModelScope.launch { refresh() }
    }


    fun clearFilters() {
        query = ""
        favoritesOnly = false
        familyFilter = null
        effectFilter = null
        aromaFilter = null
        viewModelScope.launch { refresh() }
    }

    fun toggleFavorite(terpene: Terpene) {
        viewModelScope.launch {
            repo.toggleFavorite(terpene)
            refresh()
        }
    }

    /**
     * Registers that the full card of [terpene] was opened.
     *
     * @return true the first time it is seen, i.e. when experience was awarded.
     */
    suspend fun markDiscovered(terpene: Terpene): Boolean =
        progress.discover(terpene.id, java.time.LocalDate.now().toEpochDay())

    fun recordQuiz(correct: Boolean) {
        viewModelScope.launch { progress.recordQuiz(correct) }
    }

    /** Registers a run played through to the last round, for the completion bonus. */
    fun recordQuizCompleted() {
        viewModelScope.launch { progress.recordQuizCompleted() }
    }

    suspend fun detail(id: String): Terpene? = repo.getTerpene(id)

    suspend fun partners(terpene: Terpene): List<Terpene> = repo.resolve(terpene.pairsWith)

    /**
     * [terpene]'s volatility row, or null when the index has not loaded yet.
     *
     * The detail screen calls this instead of reading the index itself, so the
     * lookup rule lives in one place and a screen cannot invent a fallback
     * window for a compound the index does not know.
     */
    suspend fun volatilityOf(terpene: Terpene): TerpeneVolatility? {
        val index = volatilityIndex
        return index.forId(terpene.id) ?: TerpeneVolatilityIndex
            .from(repo.volatilityRows(), entourageContent.getVaporisation())
            .also { volatilityIndex = it }
            .forId(terpene.id)
    }

    /**
     * The staged curve for [terpene] and the compounds the catalog associates
     * with it.
     *
     * `pairsWith` rather than a chosen profile, because a detail page has no
     * selection: it has the four to eight partners the encyclopedia already
     * ships for this compound, and a curve over those is a real selection
     * rather than an invented one. Every shipped compound has between three and
     * eight, so nothing is truncated.
     */
    suspend fun curveFor(terpene: Terpene): VolatilityCurve {
        val index = volatilityIndex.takeIf { it.size > 0 } ?: run {
            TerpeneVolatilityIndex.from(
                repo.volatilityRows(),
                entourageContent.getVaporisation()
            ).also { volatilityIndex = it }
        }
        return index.curveFor(listOf(terpene.id) + terpene.pairsWith)
    }

    /**
     * F3: [terpene]'s agronomy block, or null when it is not a module compound.
     *
     * Returns the **copy** ([TerpeneAgronomyContent]) rather than the raw entry,
     * because Compose has no JVM unit-test runtime in this project: every Spanish
     * sentence and every show-or-hide decision a composable would otherwise make
     * for itself lives in the model and is asserted there. The call site decides
     * where the block goes, nothing else.
     *
     * `null` means "this encyclopedia page is not about a compound the Séquito
     * module models" — the same gate the page's "🧬 Efecto Séquito" button uses,
     * so a compound with no module role gets no agronomy block rather than one
     * that is empty.
     */
    suspend fun agronomyFor(terpene: Terpene): TerpeneAgronomyContent? {
        val moduleTerpene = EntourageFilters.terpeneForCatalogId(terpene.id) ?: return null
        val index = if (agronomyIndex.size > 0) {
            agronomyIndex
        } else {
            entourageContent.getAgronomyIndex().also { agronomyIndex = it }
        }
        return TerpeneAgronomyCopy.contentOf(moduleTerpene, index.forTerpene(moduleTerpene))
    }

    /**
     * F4: [terpene]'s processing block, or null when it is not a module compound.
     *
     * The same shape and the same reasons as [agronomyFor]: the copy arrives fully
     * built from the model — every method with its level, its basis **and its
     * safety line**, every preservation factor with its basis, plus the shared
     * comparison and the residual-solvent sentence — so this file decides only
     * where the block goes.
     *
     * The gate is identical: "is this a compound the Séquito module models", not
     * "does it have an entry". Gating on the entry would make the honest
     * no-entry sentence unreachable, which is the same reasoning F3 recorded.
     */
    suspend fun processingFor(terpene: Terpene): TerpeneProcessingContent? {
        val moduleTerpene = EntourageFilters.terpeneForCatalogId(terpene.id) ?: return null
        val index = if (processingIndex.size > 0) {
            processingIndex
        } else {
            entourageContent.getProcessingIndex().also { processingIndex = it }
        }
        return TerpeneProcessingCopy.contentOf(moduleTerpene, index.forTerpene(moduleTerpene))
    }

    /**
     * F4: the processing block the player has actually opened.
     *
     * Resolved from the same `discovered` set that pays the discovery XP, rather
     * than from a second "has this page been read" ledger. There is none, and
     * there is deliberately still none: a second record of the same fact is a
     * second source of truth for it, and this module has exactly one progression
     * system.
     */
    fun processingReadIndex(): EntourageProcessingIndex =
        EntourageProcessingIndex(
            discovered.mapNotNull { EntourageFilters.terpeneForCatalogId(it) }
                .mapNotNull { processingIndex.forTerpene(it) }
        )

    /**
     * F4: the resin engineer reward, if the player has now read every compound
     * the shipped block documents.
     *
     * Pure delegation to [EntourageRewards.forProcessingRead]; the ViewModel
     * decides nothing. It returns a **list** rather than a reward so that "not
     * earned" and "earned and already paid" are the same empty answer to the
     * caller, and [EntourageRewards.pending] does the deduplication against the
     * names already in the `achievements` table.
     */
    fun processingBadgeReward(): List<EntourageReward> =
        EntourageRewards.forProcessingRead(
            readCompounds = processingReadIndex().documentedTerpenes,
            index = processingIndex
        )

    /**
     * F4: pays the resin engineer badge if it has just been earned.
     *
     * Called from the progress collector rather than from the detail page, and the
     * reason is the condition: it is satisfied by the **union** of every page the
     * player has opened, so the only place that can see the moment it becomes true
     * is the place that already watches the whole set.
     *
     * Three guards, in order:
     * 1. the processing block has to have loaded — otherwise `documentedTerpenes`
     *    is empty and the condition is trivially satisfied, which would award a
     *    badge for content that has not arrived yet;
     * 2. [EntourageRewards.forProcessingRead] is the pure decision;
     * 3. [EntourageRewards.pending] against [awardedBadgeNames] is the
     *    idempotency, because the table would otherwise take a second row for the
     *    same reward on the very next emission.
     */
    private fun grantProcessingBadge() {
        if (processingIndex.size == 0) return
        // Cheap pure gate first, so the common case — the badge is not earned — costs
        // no database read at all.
        val rewards = EntourageRewards.forProcessingRead(
            readCompounds = processingReadIndex().documentedTerpenes,
            index = processingIndex
        )
        if (rewards.isEmpty()) return

        viewModelScope.launch {
            // The table is the source of truth, re-read here rather than trusted from
            // [awardedBadgeNames].
            //
            // Two attempts at this shipped duplicate rows on the device. The first was
            // a snapshot: the ViewModel read the names once at load, so any evaluation
            // before the insert landed paid again, and the progress collector emits on
            // every discovery. The second was believing the snapshot was enough once
            // the claim was moved before the insert — it is not, because
            // `appViewModel` scopes to the navigation entry, so **two** live
            // `TerpenesViewModel`s can each hold a snapshot taken before the other
            // wrote. Reading the table inside the same coroutine that writes it, under
            // [badgeGrantLock], makes the check and the insert one serialised step.
            badgeGrantLock.withLock {
                val persisted = runCatching {
                    badgeAchievements.getAllAchievements().first()
                }.getOrDefault(emptyList())

                // Repair rows a pre-fix build wrote, before deciding anything.
                //
                // The duplicates already on a device (four rows, 1220 XP, one badge)
                // cannot be removed from outside the app, and this app has no schema
                // migration that would sweep them because the schema never changed —
                // the *data* was wrong, not the table. So the app repairs its own
                // history the first time the badge is evaluated, keeping the oldest
                // row per name, which is the one whose description was written when
                // the badge was first granted.
                //
                // Reported, never silent: the removed count is logged, because a repair
                // nobody can verify is indistinguishable from a repair that did not
                // happen. Idempotent — a clean table returns 0 and the work stops.
                persisted.groupBy { it.name }
                    .filterValues { it.size > 1 }
                    .keys
                    .forEach { name ->
                        runCatching { badgeAchievements.deleteDuplicateAchievements(name) }
                            .onSuccess { removed ->
                                if (removed > 0) {
                                    android.util.Log.i(
                                        "TrichomeAchievements",
                                        "collapsed $removed duplicate rows for badge '$name'"
                                    )
                                }
                            }
                            .onFailure { error ->
                                // Not fatal. A failed repair leaves the duplicates and
                                // must not stop the badge from being granted.
                                android.util.Log.w(
                                    "TrichomeAchievements",
                                    "could not collapse duplicate rows for '$name'",
                                    error
                                )
                            }
                    }

                val names = awardedBadgeNames + persisted.map { it.name }
                val fresh = EntourageRewards.pending(rewards, names)
                if (fresh.isEmpty()) return@withLock
                // Claim before inserting, inside the lock, so a crash costs a missing
                // badge rather than a duplicated one.
                awardedBadgeNames = names + fresh.map { it.nameEs }
                // The **existing** projector: `EntourageReward.toAchievementRow` is
                // the only path the Séquito module has into the `achievements` table,
                // and reusing it is what keeps one decision about `isUnlocked` and
                // `xpReward` instead of two.
                //
                // `insertAchievement` is a conditional `INSERT ... WHERE NOT EXISTS`,
                // so the statement itself refuses a second row for the same name. That
                // is what holds even when two ViewModels race; the check above is a
                // courtesy, not the guarantee.
                fresh.forEach { badgeAchievements.insertAchievement(it.toAchievementRow()) }
            }
        }
    }

    /**
     * Serialises the read-then-write above across every live `TerpenesViewModel`.
     *
     * Deliberately a companion of the class rather than a repository-level lock:
     * the only caller of the table from this side is this grant, so a wider lock
     * would be a claim about code that does not exist.
     */
    private companion object {
        val badgeGrantLock = Mutex()
    }

    /* ── F11: the Master Blender's progression ─────────────────────────── */

    /**
     * F11: the player's total XP, summed from the `achievements` table.
     *
     * Read from the same rows the app sums its own total from, so the level the
     * blender panel shows and the level the profile screen shows cannot differ.
     * A blender-local counter would be exactly the second source of truth
     * [EntourageAchievement]'s KDoc refuses to create, one layer away.
     */
    var blenderTotalXp by mutableIntStateOf(0)
        private set

    /**
     * F11: every name in the `achievements` table, for the history readout.
     *
     * The history is read off the rows rather than from a stored list, so the
     * levels on screen cannot disagree with the table holding them.
     */
    var blenderAwardedNames by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * F11: persists the rows a blender run earned.
     *
     * Deliberately **not** called from a recomposition or a slider callback: the
     * dialog hands over the rows on a deliberate press, because paying for a
     * comparison the player was still adjusting would make every drag a purchase.
     *
     * Shares [badgeGrantLock] with the two Séquito grants. The claim being made
     * is narrow — "two coroutines cannot both read `not granted` and both write"
     * — and it is a claim about the table, not about this class, so a lock
     * narrower than the table would be the wrong width.
     *
     * The identity guarantee is still the DAO's `WHERE NOT EXISTS`; the read
     * below is a courtesy that saves a pointless insert.
     */
    fun recordBlend(rewards: List<EntourageReward>) {
        if (rewards.isEmpty()) return
        viewModelScope.launch {
            badgeGrantLock.withLock {
                val persisted = runCatching {
                    badgeAchievements.getAllAchievements().first()
                }.getOrDefault(emptyList())

                val names = blenderAwardedNames + persisted.map { it.name }
                val fresh = BlenderProgress.pending(rewards, names)
                if (fresh.isEmpty()) return@withLock

                fresh.forEach { badgeAchievements.insertAchievement(it.toAchievementRow()) }

                // Re-read rather than assume: `insertAchievement` returns -1 when
                // the row was already there, so "we inserted it" is not a fact this
                // coroutine gets to assert. The table is.
                val after = runCatching {
                    badgeAchievements.getAllAchievements().first()
                }.getOrDefault(persisted)
                blenderAwardedNames = after.map { it.name }.toSet()
                blenderTotalXp = after.sumOf { it.xpReward }
            }
        }
    }

    /**
     * F11: loads the XP total and the badge names the panel renders.
     *
     * Called once when the ViewModel is created rather than on every open of the
     * dialog, because a total that only refreshes when a player looks at it is a
     * total the profile screen would contradict.
     */
    fun loadBlenderProgress() {
        viewModelScope.launch {
            val rows = runCatching {
                badgeAchievements.getAllAchievements().first()
            }.getOrDefault(emptyList())
            blenderAwardedNames = rows.map { it.name }.toSet()
            blenderTotalXp = rows.sumOf { it.xpReward }
        }
    }
}

/* ─────────────────────────── Entourage (Séquito) ────────────────────────── */

/**
 * State and rewards for the Séquito module.
 *
 * Reads its content from [EntourageContentRepository] and pays into the
 * **existing** `achievements` table through [AchievementRepository]. There is
 * no second XP ledger here: the table's `xpReward` is what the app already sums
 * into the player's total, and a module that kept its own counter would drift
 * from it.
 *
 * [awardedNames] is the snapshot that makes granting idempotent. The table
 * primary key is an auto-generated id, so re-inserting a reward would add a
 * second row rather than update the first — replaying the same Lab case would
 * pay again, forever. The reward's display name is the stable handle, and
 * [EntourageRewards.pending] drops what has already been paid.
 */
class EntourageViewModel(container: AppContainer) : ViewModel() {
    private val content = container.entourageContentRepository
    private val achievements = container.achievementRepository

    /** The parsed library, or null while it loads. */
    var library by mutableStateOf<EntourageContent?>(null)
        private set

    /**
     * True when the asset could not be read at all.
     *
     * A separate flag rather than a null [library] so the screen can tell "still
     * loading" from "the file is missing": both are a null library, and
     * reporting a spinner forever for a broken asset is the app looking busy
     * while it has nothing.
     */
    var loadFailed by mutableStateOf(false)
        private set

    /** Reward names already in the `achievements` table. */
    private var awardedNames by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * F5: badge names this ViewModel has claimed for itself.
     *
     * Separate from [awardedNames] for the same reason it is separate on
     * `TerpenesViewModel`: a claim made inside [badgeGrantLock] has to survive
     * past the coroutine that made it, and it is the badge grant that races, not
     * the case payment.
     */
    private var awardedBadgeNames by mutableStateOf<Set<String>>(emptySet())

    /**
     * F5: serialises the badge's read-then-write across every live
     * [EntourageViewModel].
     *
     * Deliberately a companion of the class, matching `TerpenesViewModel`: the
     * only caller of the table from this side is the badge grant, so a wider lock
     * would be a claim about code that does not exist.
     */
    private val badgeGrantLock = Mutex()

    /** The last reward granted, so the screen can acknowledge it once. */
    var lastReward by mutableStateOf<EntourageReward?>(null)
        private set

    init {
        viewModelScope.launch {
            // The repository parses the asset with no fallback, so an unreadable
            // or malformed file throws. Left unhandled that kills the coroutine
            // and the screen sits on its spinner forever; reported, the user can
            // say so instead.
            runCatching { content.getContent() }
                .onSuccess { loaded ->
                    library = loaded
                    loadFailed = false
                }
                .onFailure { loadFailed = true }

            awardedNames = achievements.getAllAchievements().first().map { it.name }.toSet()
        }
    }

    /**
     * Pays [rewards] that have not been paid yet.
     *
     * The filter happens here rather than in the DAO, and it is the whole
     * idempotency guarantee: a double tap on "Evaluar", a replayed quiz, or
     * simply coming back to a solved case all land on the same empty list.
     */
    fun grant(rewards: List<EntourageReward>) {
        if (rewards.isEmpty()) return
        viewModelScope.launch {
            val fresh = EntourageRewards.pending(rewards, awardedNames)
            if (fresh.isEmpty()) return@launch
            fresh.forEach { achievements.insertAchievement(it.toAchievementRow()) }
            awardedNames = awardedNames + fresh.map { it.nameEs }
            lastReward = fresh.last()
            // F5: this payment may have completed the case set, so the badge is
            // evaluated here, inside the same coroutine that wrote the row the
            // condition reads. Not in a collector: this is the one place that
            // knows a verdict was just paid.
            grantCaseBadgeIfComplete()
        }
    }

    /**
     * F5: pays the terpene alchemist badge if every shipped case now has a
     * verdict row.
     *
     * ## Why the table is re-read rather than trusted from [awardedNames]
     *
     * The F4 lesson, applied verbatim. `appViewModel` scopes to the navigation
     * entry, so two live [EntourageViewModel]s can each hold a snapshot taken
     * before the other wrote; a check against the snapshot would let the second
     * one decide the badge is not earned when the row is already in the table.
     * Reading the table inside the same coroutine that writes it, under
     * [badgeGrantLock], makes the check and the insert one serialised step.
     *
     * There is no second "which cases have I played" ledger, deliberately: the
     * condition is read from the rows [EntourageRewards.forLabVerdict] already
     * writes, and a second record of the same fact would be a second source of
     * truth for it.
     */
    private fun grantCaseBadgeIfComplete() {
        viewModelScope.launch {
            badgeGrantLock.withLock {
                val persisted = runCatching {
                    achievements.getAllAchievements().first()
                }.getOrDefault(emptyList())

                val cases = runCatching { content.getCases() }.getOrDefault(emptyList())
                // Cheap pure gate first, so the common case — not every case
                // played yet — costs nothing beyond the read the insert already
                // needed.
                val rewards = EntourageRewards.forAllCasesVerdicted(
                    cases = cases,
                    awardedNames = persisted.map { it.name }.toSet()
                )
                if (rewards.isEmpty()) return@withLock

                val names = awardedBadgeNames + persisted.map { it.name }
                val fresh = EntourageRewards.pending(rewards, names)
                if (fresh.isEmpty()) return@withLock
                // Claim before inserting, inside the lock, so a crash costs a
                // missing badge rather than a duplicated one.
                awardedBadgeNames = names + fresh.map { it.nameEs }
                // The same existing projector every other reward goes through.
                fresh.forEach { achievements.insertAchievement(it.toAchievementRow()) }
            }
        }
    }
}

/* ─────────────────────────── Breeding ─────────────────────────────────── */

class BreedingViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.breedingRepository

    val projects: StateFlow<List<BreedingProject>> = repo.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var crosses by mutableStateOf<List<BreedingCross>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            repo.getAllCrosses().onEach { crosses = it }.collect()
        }
    }

    fun addProject(name: String, mother: String, father: String, generation: String) {
        viewModelScope.launch {
            repo.insertProject(
                BreedingProject(name = name, motherId = mother, fatherId = father, generation = generation)
            )
        }
    }

    /**
     * Writes an edited project.
     *
     * Separate from [addProject] on purpose: there is no `@Insert`-then-fix
     * shortcut here, because an insert would mint a new id and orphan every
     * cross that pointed at the old one.
     */
    fun updateProject(project: BreedingProject) = viewModelScope.launch { repo.updateProject(project) }

    fun deleteProject(project: BreedingProject) = viewModelScope.launch { repo.deleteProject(project) }

    fun addCross(projectId: Long, parent1: String, parent2: String, score: Float, notes: String) {
        viewModelScope.launch {
            repo.insertCross(
                BreedingCross(projectId = projectId, parent1 = parent1, parent2 = parent2, phenotypeScore = score, notes = notes)
            )
        }
    }

    /**
     * Writes an edited cross in place.
     *
     * The dialog used to reach [addCross] whatever it was opened on, so editing
     * a cross produced a second row with identical parents.
     */
    fun updateCross(cross: BreedingCross) = viewModelScope.launch { repo.updateCross(cross) }

    fun deleteCross(cross: BreedingCross) = viewModelScope.launch { repo.deleteCross(cross) }
}

/* ─────────────────────────── Diagnosis ────────────────────────────────── */

class DiagnosisViewModel(container: AppContainer) : ViewModel() {
    private val grow = container.growRepository
    private val content = container.diagnosisContent

    val plants: StateFlow<List<Plant>> = grow.allPlants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var selectedSymptoms by mutableStateOf<Set<String>>(emptySet())
        private set
    var result by mutableStateOf<DiagnosisResult?>(null)
        private set
    var latestImagePath by mutableStateOf<String?>(null)
        private set

    /* Photo analysis */
    var photoFeatures by mutableStateOf<PhotoAnalyzer.Features?>(null)
        private set
    var photoMatches by mutableStateOf<List<PhotoDiagnosisEngine.Match>>(emptyList())
        private set
    var analyzing by mutableStateOf(false)
        private set
    var photoError by mutableStateOf<String?>(null)
        private set

    /* Encyclopedia */
    var conditions by mutableStateOf<List<DiagnosisCondition>>(emptyList())
        private set

    init {
        viewModelScope.launch { conditions = content.getConditions() }
    }

    fun toggleSymptom(id: String) {
        selectedSymptoms = if (selectedSymptoms.contains(id)) selectedSymptoms - id else selectedSymptoms + id
    }

    /**
     * Stores the photo and analyses it.
     *
     * The previous implementation only remembered the file path and never read
     * it, so the verdict came only from the ticked symptoms and the picture was
     * decorative.
     */
    fun setImagePath(path: String?) {
        latestImagePath = path
        photoError = null
        if (path == null) {
            photoFeatures = null
            photoMatches = emptyList()
            return
        }
        analyze(path)
    }

    fun analyze(path: String) {
        viewModelScope.launch {
            analyzing = true
            photoError = null
            val features = withContext(Dispatchers.Default) {
                runCatching { measure(path) }.getOrNull()
            }
            analyzing = false
            if (features == null) {
                photoFeatures = null
                photoMatches = emptyList()
                photoError = "No se pudo leer la imagen. Prueba con otra foto."
                return@launch
            }
            photoFeatures = features
            if (features.isUsable) {
                photoMatches = PhotoDiagnosisEngine.rank(features, conditions)
            } else {
                photoMatches = emptyList()
                photoError =
                    "La imagen no muestra suficiente planta. Acercate a la hoja y vuelve a intentarlo."
            }
            if (result != null) recompute()
        }
    }

    /** Decodes, down-samples and measures the photo. */
    private fun measure(path: String): PhotoAnalyzer.Features? {
        val bitmap = com.trichome.app.ui.screens.diagnosis.CameraCaptureActivity
            .decodeSampled(path) ?: return null
        val longest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longest > ANALYSIS_EDGE) {
            val ratio = ANALYSIS_EDGE.toFloat() / longest
            android.graphics.Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        return PhotoAnalyzer.analyze(pixels, scaled.width, scaled.height)
    }

    fun diagnose() {
        recompute()
    }

    /** Merges the symptom engine with the photographic evidence. */
    private fun recompute() {
        val symptoms = DiagnosisEngine.diagnose(selectedSymptoms)
        result = PhotoDiagnosisEngine.combine(photoMatches, symptoms, conditions)
    }

    fun reset() {
        selectedSymptoms = emptySet()
        result = null
    }

    fun registerOnJournal(plantId: Long, notes: String? = null) {
        val diag = result ?: return
        viewModelScope.launch {
            val type = if (diag.category == "pest" || diag.category == "fungus") EventType.PEST_CONTROL else EventType.DIAGNOSIS
            grow.addEvent(
                plantId = plantId,
                type = type,
                notes = notes?.takeIf { it.isNotBlank() }
                    ?: "Diagnóstico: ${diag.condition} (${(diag.confidence * 100).toInt()}%)",
                diagnosisResult = diag.condition,
                diagnosisCertainty = diag.confidence,
                imagePath = latestImagePath
            )
        }
    }

    private companion object {
        /** Longest edge fed to the analyser; the measures are ratio based. */
        const val ANALYSIS_EDGE = 512
    }
}

/* ─────────────────────────── Charts ───────────────────────────────────── */

class ChartsViewModel(container: AppContainer) : ViewModel() {
    private val grow = container.growRepository

    var selectedPlantId by mutableStateOf<Long?>(null)
        private set
    var events by mutableStateOf<List<GrowEvent>>(emptyList())
        private set
    var achievements by mutableStateOf<List<Achievement>>(emptyList())
        private set
    var xp by mutableStateOf(0)
        private set
    var streak by mutableStateOf(0)
        private set

    val plants: StateFlow<List<Plant>> = grow.allPlants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refresh()
        viewModelScope.launch {
            grow.allAchievements().onEach { achievements = it }.collect()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val pid = selectedPlantId
            events = if (pid == null) grow.eventRepository.getAllEventsSnapshot().sortedByDescending { it.timestamp }
            else grow.eventRepository.getEventsByPlantSnapshot(pid).sortedByDescending { it.timestamp }
            achievements = grow.achievementRepository.getAllAchievements().first()
            val (xpVal, streakVal) = grow.gamificationSummary()
            xp = xpVal
            streak = streakVal
        }
    }

    fun selectPlant(id: Long?) {
        selectedPlantId = id
        refresh()
    }
}

/* ─────────────────────────── Settings ─────────────────────────────────── */

class SettingsViewModel(container: AppContainer) : ViewModel() {
    val appearanceSettings = container.appearanceSettings
    var persisted by mutableStateOf(com.trichome.app.data.prefs.AppearanceSettings())
        private set

    init {
        viewModelScope.launch {
            appearanceSettings.settings.collect { persisted = it }
        }
    }
}