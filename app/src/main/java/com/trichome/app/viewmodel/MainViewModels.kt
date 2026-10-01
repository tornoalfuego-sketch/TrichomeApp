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
import com.trichome.app.model.CalendarWindow
import com.trichome.app.worker.ReminderAlarmScheduler
import com.trichome.app.worker.ReminderCancellation
import com.trichome.app.worker.ReminderSchedulerWorker
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    fun load(plantId: Long) {
        viewModelScope.launch {
            val existing = repo.getConfigForPlant(plantId)
            config = existing
            if (existing != null) {
                result = SuperCycleEngine.calculateSuperCycle(
                    cycleStartAt = existing.cycleStartAt,
                    lightHours = existing.lightHours,
                    darkHours = existing.darkHours
                )
            }
        }
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
    private val appContext = container.application.applicationContext

    val plants: StateFlow<List<Plant>> = plantRepo.getAllPlants()
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