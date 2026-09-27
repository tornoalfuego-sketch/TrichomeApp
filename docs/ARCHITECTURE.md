# Architecture

Trichome App is an offline-first Android application built with **Kotlin + Jetpack Compose** and a clean, modular data layer. All processing — diagnosis rules, photoperiod math, charts — happens on-device.

## Layers

```
ui/                         Compose screens, navigation, theme, components
  ├── screens/              one package per feature (home, tent, plant, journal, …)
  ├── navigation/           AppNavigation (single NavHost with all routes)
  ├── theme/                TrichomeTheme + GlassTokens (appearance engine)
  └── components/           glass components, navigation bar, native charts
viewmodel/                  State holders per screen, built with viewModelFactory
worker/                     WorkManager workers (reminders, daily check-in, cycle check)
di/                         AppContainer (manual DI root)
data/
  ├── entity/               Room entities
  ├── dao/                  Room DAOs
  ├── database/             AppDatabase (v2) + MIGRATION_1_2
  ├── repository/           repositories + pure engines (DiagnosisEngine) + asset content
  └── prefs/                DataStore preferences (appearance)
model/                      pure domain logic (SuperCycleEngine, StageProgressEngine, Gamification, EventType)
TrichomeApp.kt              Application class: container + WorkManager configuration
```

## Dependency injection

No Hilt, no Koin. A single `AppContainer` interface is implemented by
`DefaultAppContainer` and created once in `TrichomeApp.onCreate`:

```kotlin
class TrichomeApp : Application(), Configuration.Provider {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)
    }

    // Lazy so appContainer is always available when WorkManager asks for it.
    private val wmConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(TrichomeWorkerFactory(appContainer))
            .setMinimumLoggingLevel(Log.INFO)
            .build()
    }

    override val workManagerConfiguration: Configuration get() = wmConfiguration
}
```

> **WorkManager uses on-demand initialization.** The default
> `androidx.work.WorkManagerInitializer` is removed from the manifest:
>
> ```xml
> <provider
>     android:name="androidx.startup.InitializationProvider"
>     android:authorities="${applicationId}.androidx-startup"
>     android:exported="false"
>     tools:node="merge">
>     <meta-data
>         android:name="androidx.work.WorkManagerInitializer"
>         android:value="androidx.startup"
>         tools:node="remove" />
> </provider>
> ```
>
> `InitializationProvider` is a `ContentProvider`, so it runs **before**
> `Application.onCreate()` and would initialize WorkManager first. A manual
> `WorkManager.initialize()` from `onCreate()` then throws
> `IllegalStateException: WorkManager is already initialized` and kills the
> process before the first frame — the app never appears to start.
> `WorkManagerInitTest` guards the three parts of this contract.

Every screen resolves its `ViewModel` through `viewModelFactory { initializer { … } }`:

```kotlin
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = container() // (applicationContext as TrichomeApp).appContainer
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}
```

**Rule:** no worker or screen ever constructs its own database — everything goes
through the container (fixed bug where workers built their own Room instances).

## Workers

All workers obtain collaborators from `AppContainer`:

| Worker | Purpose |
| --- | --- |
| `ReminderSchedulerWorker` | Fires a reminder notification at the configured time and **re-queues the next occurrence** under a new work name (`reminder_due_<id>`) |
| `ReminderReschedulerWorker` | Periodic (15 min) sweep that schedules any active reminder whose first occurrence falls in a future window |
| `DailyCheckinWorker` | Daily maintenance (streak/consistency support) |
| `CycleCheckWorker` | SuperCycle phase check notifications |

`TrichomeWorkerFactory` maps worker names → constructors using the container.

## Data flow (example: journal registration)

```
UI → JournalViewModel.addEvent(GrowEvent) → EventRepository (Room) → Flow re-emits
     → GrowRepository.addEvent (facade) → XP reward + platform achievements
     → DailyCheckinWorker computes the new streak from event dates
```

## Diagnosis pipeline

1. `DiagnosisScreen` captures a photo (CameraX `TakePicturePreview` or system gallery via `GetContent`).
2. User selects visible symptoms; each maps to an identifier in `assets/data/diagnostics.json`.
3. `DiagnosisEngine.diagnose(symptoms)` — pure, deterministic rule engine (weighted coverage over 15 conditions + healthy) runs on-device.
4. TFLite local classifier is present as an optional dependency with **fallback to the rule engine**.
5. The report resolves Spanish labels + action plan from the asset catalog; the result can be registered in the journal as a `DIAGNOSIS` or `PEST_CONTROL` event.

## Testing

- **JVM unit tests** (`app/src/test`): `SuperCycleEngineTest`, `StageProgressEngineTest` (day-1 semantics), `DiagnosisEngineTest`, `EventTypeTest`, `GamificationTest`.
- **Instrumentation** (`app/src/androidTest`): `MigrationTest` creates a real v1 database with raw SQL, runs `MIGRATION_1_2`, and validates the upgraded schema + preserved data through Room.

## Build

JDK 17 is required (Gradle 8.10.2 / AGP 8.7). Set:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
```

Room schema export: `app/schemas/com.trichome.app.data.database.AppDatabase/` (KSP).