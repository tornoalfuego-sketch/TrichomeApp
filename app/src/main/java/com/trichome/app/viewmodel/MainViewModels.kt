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
import com.trichome.app.worker.ReminderAlarmScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
            uiState = PlantDetailLoader.load(
                lookupPlant = { plantRepo.getPlantById(plantId) },
                stageProgressFor = { plant -> resolveStageProgress(plantId, plant) }
            )
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
            superCycleRepo.getSuperCycleByPlant(plantId)?.let { config ->
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

    fun saveSuperCycle(config: SuperCycleConfig, isNew: Boolean) {
        viewModelScope.launch {
            if (isNew) superCycleRepo.insertSuperCycle(config) else superCycleRepo.updateSuperCycle(config)
            loadSuperCycle(config.plantId)
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

    fun load(plantId: Long) {
        viewModelScope.launch {
            val existing = repo.getSuperCycleByPlant(plantId)
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
                plantId = plantId,
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
                !armedAll -> "Recordatorio guardado, pero el sistema no permitio programar la alarma exacta. Revisa los permisos de Ajustes."
                else -> null
            }
        }
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

    fun loadMonth(from: Long, to: Long) {
        viewModelScope.launch {
            events = eventRepo.getEventsBetween(from, to)
            reminders = reminderRepo.getActiveRemindersSnapshot()
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

    val level: Int get() = TerpeneProgression.levelFor(xp)
    val levelProgress: Float get() = TerpeneProgression.levelProgress(xp)
    val rankTitle: String get() = TerpeneProgression.rankTitle(level)

    val badges: List<Badge>
        get() {
            val completedFamilies = terpenes
                .filter { it.family.isNotBlank() }
                .groupBy { it.family }
                .count { (_, group) -> group.all { it.id in discovered } }
            return TerpeneProgression.badges(
                discoveredCount = discovered.size,
                familiesCompleted = completedFamilies,
                favorites = terpenes.count { it.isFavorite },
                streak = streak,
                quizzesCorrect = quizzesCorrect
            )
        }

    init {
        viewModelScope.launch {
            val all = repo.getTerpenes()
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
                xp = TerpeneProgression.XP_PER_DISCOVERY * p.discovered.size +
                    TerpeneProgression.XP_PER_QUIZ_CORRECT * p.quizCorrect
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

    fun deleteProject(project: BreedingProject) = viewModelScope.launch { repo.deleteProject(project) }

    fun addCross(projectId: Long, parent1: String, parent2: String, score: Float, notes: String) {
        viewModelScope.launch {
            repo.insertCross(
                BreedingCross(projectId = projectId, parent1 = parent1, parent2 = parent2, phenotypeScore = score, notes = notes)
            )
        }
    }

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