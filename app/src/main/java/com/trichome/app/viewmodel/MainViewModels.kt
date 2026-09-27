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
import com.trichome.app.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

class PlantDetailViewModel(container: AppContainer) : ViewModel() {
    private val plantRepo = container.plantRepository
    private val superCycleRepo = container.superCycleRepository
    private val eventRepo = container.eventRepository
    private val protocolRepo = container.protocolRepository
    private val stageEntryRepo = container.stageEntryRepository

    var plant by mutableStateOf<Plant?>(null)
        private set

    var superCycleResult by mutableStateOf<SuperCycleResult?>(null)
        private set

    var daysInGrow by mutableStateOf(1)
        private set

    var events by mutableStateOf<List<GrowEvent>>(emptyList())
        private set

    var latestStageEntry by mutableStateOf<StageEntry?>(null)
        private set

    var stageProgress by mutableStateOf<StageProgressEngine.StageProgress?>(null)
        private set

    val eventsFlow = MutableStateFlow<List<GrowEvent>>(emptyList())

    fun loadPlant(plantId: Long) {
        viewModelScope.launch {
            plantRepo.getPlantById(plantId)?.let { p ->
                plant = p
                daysInGrow = StageProgressEngine.daysInGrow(p.growStartTimestamp)
            }
            eventRepo.getEventsByPlant(plantId)
                .onEach { eventsFlow.value = it; events = it }
                .collect()
        }
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

    fun loadStageProgress(plantId: Long) {
        viewModelScope.launch {
            val current = plant ?: return@launch
            val activeProtocols = protocolRepo.getActiveProtocols().filter { it.plantId == plantId }
            val protocol = activeProtocols.firstOrNull() ?: return@launch
            val blocks = protocolRepo.getStages(protocol.id)
                .sortedBy { it.sortOrder }
                .map { StageProgressEngine.StageBlock(it.stageName, it.durationDays) }
            if (blocks.isNotEmpty()) {
                stageProgress = StageProgressEngine.calculateProgress(
                    blocks = blocks,
                    startTimestamp = current.growStartTimestamp,
                    nowTimestamp = System.currentTimeMillis()
                )
            }
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

    val plants: StateFlow<List<Plant>> = plantRepo.getAllPlants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var events by mutableStateOf<List<GrowEvent>>(emptyList())
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

    fun persistReminder(reminder: Reminder) {
        viewModelScope.launch { reminderRepo.insertReminder(reminder) }
    }
}

/* ─────────────────────────── Calendar ─────────────────────────────────── */

class CalendarViewModel(container: AppContainer) : ViewModel() {
    private val eventRepo = container.eventRepository
    private val reminderRepo = container.reminderRepository
    private val plantRepo = container.plantRepository

    val plants: StateFlow<List<Plant>> = plantRepo.getAllPlants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var events by mutableStateOf<List<GrowEvent>>(emptyList())
        private set
    var reminders by mutableStateOf<List<Reminder>>(emptyList())
        private set

    fun loadMonth(from: Long, to: Long) {
        viewModelScope.launch {
            events = eventRepo.getEventsBetween(from, to)
            reminders = reminderRepo.getActiveRemindersSnapshot()
        }
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

    var terpenes by mutableStateOf<List<Terpene>>(emptyList())
        private set
    var query by mutableStateOf("")
        private set

    init {
        viewModelScope.launch { terpenes = repo.getTerpenes() }
    }

    fun updateQuery(q: String) {
        query = q
        viewModelScope.launch { terpenes = repo.search(q) }
    }

    fun toggleFavorite(terpene: Terpene) {
        viewModelScope.launch {
            repo.toggleFavorite(terpene)
            terpenes = repo.search(query)
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

    val plants: StateFlow<List<Plant>> = grow.allPlants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var selectedSymptoms by mutableStateOf<Set<String>>(emptySet())
        private set
    var result by mutableStateOf<DiagnosisResult?>(null)
        private set
    var latestImagePath by mutableStateOf<String?>(null)
        private set

    fun toggleSymptom(id: String) {
        selectedSymptoms = if (selectedSymptoms.contains(id)) selectedSymptoms - id else selectedSymptoms + id
    }

    fun setImagePath(path: String?) {
        latestImagePath = path
    }

    fun diagnose() {
        result = DiagnosisEngine.diagnose(selectedSymptoms)
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
                notes = notes?.takeIf { it.isNotBlank() } ?: "Diagnóstico: ${diag.condition} (${(diag.confidence * 100).toInt()}%)",
                diagnosisResult = diag.condition,
                diagnosisCertainty = diag.confidence,
                imagePath = latestImagePath
            )
        }
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