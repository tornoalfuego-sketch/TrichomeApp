package com.trichome.app.data.repository

import com.trichome.app.data.entity.*
import com.trichome.app.model.EventType
import com.trichome.app.model.Gamification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Unified facade over the data layer. All [Workers](com.trichome.app.worker)
 * and screens resolve data through this repository — no worker ever builds a
 * Room database on its own.
 */
class GrowRepository(
    val plantRepository: PlantRepository,
    val tentRepository: TentRepository,
    val protocolRepository: ProtocolRepository,
    val stageEntryRepository: StageEntryRepository,
    val eventRepository: EventRepository,
    val superCycleRepository: SuperCycleRepository,
    val achievementRepository: AchievementRepository,
    val reminderRepository: ReminderRepository,
    val breedingRepository: BreedingRepository,
    val journalRepository: JournalRepository
) {

    fun allPlants(): Flow<List<Plant>> = plantRepository.getAllPlants()
    fun allTents(): Flow<List<GrowTent>> = tentRepository.getAllTents()
    fun allEvents(): Flow<List<GrowEvent>> = eventRepository.getAllEvents()
    fun allAchievements(): Flow<List<Achievement>> = achievementRepository.getAllAchievements()
    fun allReminders(): Flow<List<Reminder>> = reminderRepository.getAllActiveReminders()

    suspend fun addEvent(
        plantId: Long,
        type: EventType,
        timestamp: Long = System.currentTimeMillis(),
        notes: String? = null,
        temperature: Float? = null,
        humidity: Float? = null,
        ph: Float? = null,
        ec: Float? = null,
        amount: Float? = null,
        height: Float? = null,
        lampDistance: Float? = null,
        trainingType: String? = null,
        vpd: Float? = null,
        trichomeMaturity: String? = null,
        diagnosisResult: String? = null,
        diagnosisCertainty: Float? = null,
        imagePath: String? = null,
        groupId: String? = null
    ): Long {
        val event = GrowEvent(
            plantId = plantId,
            groupId = groupId,
            eventType = type.storageKey,
            timestamp = timestamp,
            notes = notes,
            temperature = temperature,
            humidity = humidity,
            ph = ph,
            ec = ec,
            amount = amount,
            height = height,
            lampDistance = lampDistance,
            trainingType = trainingType,
            vpd = vpd,
            trichomeMaturity = trichomeMaturity,
            diagnosisResult = diagnosisResult,
            diagnosisCertainty = diagnosisCertainty,
            imagePath = imagePath
        )
        return eventRepository.insertEvent(event)
    }

    /**
     * Levels and streak derived from the whole journal.
     */
    suspend fun gamificationSummary(): Pair<Int, Int> {
        val xp = achievementRepository.getTotalXp() + eventXp()
        val streak = gamificationStreak()
        return Pair(xp, streak)
    }

    suspend fun eventXp(): Int =
        eventRepository.getAllEventsSnapshot().sumOf {
            Gamification.xpForEvent(EventType.fromKey(it.eventType))
        }

    suspend fun gamificationStreak(): Int {
        val epochDays = eventRepository.getActiveEpochDays().toSet()
        val today = java.time.LocalDate.now().toEpochDay()
        return Gamification.currentStreak(epochDays, today)
    }

    suspend fun seedDefaultAchievements() {
        val existing = achievementRepository.getAllAchievements().first()
        if (existing.isNotEmpty()) return
        val defaults = listOf(
            Achievement(name = "Primer Riego", description = "Registra tu primer riego", icon = "💧", xpReward = 100),
            Achievement(name = "Cronista", description = "Registra 10 eventos en la bitácora", icon = "📓", xpReward = 200),
            Achievement(name = "Agricultor Verde", description = "Crea tu primera carpa", icon = "🏕️", xpReward = 150),
            Achievement(name = "Cultivador", description = "Añade tu primera planta", icon = "🌱", xpReward = 150),
            Achievement(name = "Fotoperiodista", description = "Configura el SuperCycle de una planta", icon = "☀️", xpReward = 150),
            Achievement(name = "Doctor", description = "Realiza el primer diagnóstico", icon = "🩺", xpReward = 250),
            Achievement(name = "Coleccionista", description = "Guarda un terpeno favorito", icon = "⭐", xpReward = 100)
        )
        defaults.forEach { achievementRepository.insertAchievement(it) }
    }
}