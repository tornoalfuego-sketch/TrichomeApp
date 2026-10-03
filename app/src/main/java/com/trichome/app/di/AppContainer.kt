package com.trichome.app.di

import android.app.Application
import com.trichome.app.data.database.AppDatabase
import com.trichome.app.data.export.DataExportRepository
import com.trichome.app.data.prefs.AppearanceSettingsRepository
import com.trichome.app.data.prefs.BreedingProgressRepository
import com.trichome.app.data.prefs.OnboardingRepository
import com.trichome.app.data.prefs.TerpeneProgressRepository
import com.trichome.app.data.repository.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container (no Hilt/Koin). Owns the database, repositories,
 * appearance preferences and the global scope. Workers and ViewModels resolve
 * their collaborators from here — no component ever builds a DB on its own.
 */
interface AppContainer {
    val application: Application
    val database: AppDatabase
    val appearanceSettings: AppearanceSettingsRepository
    /**
     * The terpenes encyclopedia progression. Separate from [breedingProgress]:
     * a terpene streak and a breeding medal are different achievements, and
     * merging them would let one system's reset revoke the other's rewards.
     */
    val terpeneProgress: TerpeneProgressRepository

    /** The breeding theory progression: quiz medals, per chapter. */
    val breedingProgress: BreedingProgressRepository
    val onboarding: OnboardingRepository
    val coroutineScope: CoroutineScope

    val growRepository: GrowRepository
    val plantRepository: PlantRepository
    val tentRepository: TentRepository
    val protocolRepository: ProtocolRepository
    val stageEntryRepository: StageEntryRepository

    /** The two writes behind "Cambiar de fase" and "Finalizar cultivo". */
    val growStageRepository: GrowStageRepository
    val eventRepository: EventRepository
    val superCycleRepository: SuperCycleRepository
    val achievementRepository: AchievementRepository
    val reminderRepository: ReminderRepository
    val breedingRepository: BreedingRepository
    val journalRepository: JournalRepository
    val terpenesRepository: TerpenesRepository
    val breedingContentRepository: BreedingContentRepository
    val diagnosisContentRepository: DiagnosisContentRepository

    /**
     * The Séquito (entourage) content library.
     *
     * Third of the read-only content repositories, and for the same reason as
     * the other two: the content is parsed once from `assets/`, cached for the
     * process lifetime, and shared by every screen of its module rather than
     * built per screen.
     */
    val entourageContentRepository: EntourageContentRepository

    /** Short alias used by the diagnosis ViewModel. */
    val diagnosisContent: DiagnosisContentRepository

    /**
     * Builds and writes the JSON export.
     *
     * A repository rather than a service because it is data assembly with one IO step at
     * the end, and the whole of it resolves through the same [database] the rest of the app
     * uses. It is the only thing in this container that writes a file, and the only path
     * that resolves a directory outside the app's own data.
     */
    val dataExportRepository: DataExportRepository
}

class DefaultAppContainer(app: Application) : AppContainer {

    override val application: Application = app

    override val database: AppDatabase by lazy { AppDatabase.getInstance(app) }

    override val appearanceSettings: AppearanceSettingsRepository by lazy {
        AppearanceSettingsRepository(app)
    }

    override val terpeneProgress: TerpeneProgressRepository by lazy {
        TerpeneProgressRepository(app)
    }

    override val breedingProgress: BreedingProgressRepository by lazy {
        BreedingProgressRepository(app)
    }

    override val onboarding: OnboardingRepository by lazy { OnboardingRepository(app) }

    override val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val plantRepository: PlantRepository by lazy { PlantRepository(database.plantDao()) }
    override val tentRepository: TentRepository by lazy { TentRepository(database.tentDao()) }
    override val protocolRepository: ProtocolRepository by lazy {
        ProtocolRepository(database.protocolDao(), database.protocolStageDao())
    }
    override val stageEntryRepository: StageEntryRepository by lazy {
        StageEntryRepository(database.stageEntryDao())
    }

    /**
     * Crop stage control: the transition and the finalize writes.
     *
     * Takes the database rather than just its DAOs because both writes are transactions:
     * a transition has to close the open entry, insert the next one and mirror
     * `plants.currentStage` together, and three separate calls is exactly how a timeline
     * ends up with no open stage or two of them.
     */
    override val growStageRepository: GrowStageRepository by lazy {
        GrowStageRepository(
            stageDao = database.stageEntryDao(),
            plantDao = database.plantDao(),
            eventDao = database.eventDao(),
            protocolDao = database.protocolDao(),
            protocolStageDao = database.protocolStageDao(),
            database = database
        )
    }
    override val eventRepository: EventRepository by lazy { EventRepository(database.eventDao()) }
    override val superCycleRepository: SuperCycleRepository by lazy { SuperCycleRepository(database.superCycleDao(), database.plantDao()) }
    override val achievementRepository: AchievementRepository by lazy { AchievementRepository(database.achievementDao()) }
    override val reminderRepository: ReminderRepository by lazy { ReminderRepository(database.reminderDao(), database.plantDao()) }
    override val breedingRepository: BreedingRepository by lazy { BreedingRepository(database.breedingDao()) }
    override val journalRepository: JournalRepository by lazy { JournalRepository(database.journalDao()) }
    override val terpenesRepository: TerpenesRepository by lazy { TerpenesRepository(app) }
    override val breedingContentRepository: BreedingContentRepository by lazy { BreedingContentRepository(app) }
    override val diagnosisContentRepository: DiagnosisContentRepository by lazy { DiagnosisContentRepository(app) }
    override val entourageContentRepository: EntourageContentRepository by lazy { EntourageContentRepository(app) }
    override val diagnosisContent: DiagnosisContentRepository get() = diagnosisContentRepository

    override val dataExportRepository: DataExportRepository by lazy {
        DataExportRepository(
            plantDao = database.plantDao(),
            tentDao = database.tentDao(),
            eventDao = database.eventDao(),
            stageEntryDao = database.stageEntryDao(),
            protocolDao = database.protocolDao(),
            protocolStageDao = database.protocolStageDao(),
            reminderDao = database.reminderDao(),
            superCycleDao = database.superCycleDao(),
            achievementDao = database.achievementDao(),
            breedingDao = database.breedingDao()
        )
    }

    override val growRepository: GrowRepository by lazy {
        GrowRepository(
            plantRepository = plantRepository,
            tentRepository = tentRepository,
            protocolRepository = protocolRepository,
            stageEntryRepository = stageEntryRepository,
            eventRepository = eventRepository,
            superCycleRepository = superCycleRepository,
            achievementRepository = achievementRepository,
            reminderRepository = reminderRepository,
            breedingRepository = breedingRepository,
            journalRepository = journalRepository,
            growStageRepository = growStageRepository
        )
    }
}