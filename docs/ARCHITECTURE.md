# Architecture

Trichome App is an offline-first Android application built with **Kotlin + Jetpack Compose** and a clean, modular data layer. All processing — diagnosis rules, photoperiod math, charts — happens on-device.

## Layers

```
ui/                         Compose screens, navigation, theme, components
  ├── screens/              one package per feature (home, tent, plant, journal, terpenes, diagnosis, …)
  ├── navigation/           AppNavigation (single NavHost with all routes)
  ├── theme/                TrichomeTheme + GlassTokens (appearance engine)
  └── components/           glass components, navigation bar, native charts
viewmodel/                  State holders per screen, built with viewModelFactory
worker/                     WorkManager workers + AlarmManager exact-alarm scheduler
di/                         AppContainer (manual DI root)
data/
  ├── entity/               Room entities
  ├── dao/                  Room DAOs
  ├── database/             AppDatabase (v2) + MIGRATION_1_2
  ├── repository/           repositories + pure engines (DiagnosisEngine, PhotoDiagnosisEngine) + asset content
  └── prefs/                DataStore preferences (appearance, terpene progression, onboarding)
domain/
  ├── engine/               SuperCycleEngine, StageProgressEngine
  └── vision/               PhotoAnalyzer (pixel feature extraction, pure JVM)
model/                      pure domain logic (SuperCycleEngine, StageProgressEngine, Gamification, EventType, TerpeneProgression)
tools/                      asset generators (PowerShell) + legacy enrichment data
TrichomeApp.kt              Application class: container + WorkManager configuration
```

> **DataStore, not Room, for the new state.** Terpene progression and the
> onboarding counter are preferences, not tables. They are per-user progress,
> they are small, and adding them to Room would have meant a v2→v3 schema
> migration plus a `RoomMigrationTest` for data that never needed relational
> storage. Existing user data is untouched.

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

## Real alarms

WorkManager is a *deferred* scheduler: it batches work, so a reminder can land
late or drift. Watering and feeding are not deferrable, so reminders are armed
with `AlarmManager` instead:

| Piece | Role |
| --- | --- |
| `ReminderAlarmScheduler` | `setAlarmClock` when the exact-alarm permission is held, `setAndAllowWhileIdle` otherwise |
| `ReminderAlarmReceiver` | Posts the notification and re-arms the next occurrence |
| `ReminderBootReceiver` | Re-arms every active reminder after a reboot, since alarms do not survive one |
| `nextTriggerAt(reminder, now)` | Pure function computing the next fire instant; the part worth testing |

`nextTriggerAt` is deliberately pure — it takes `now` as a parameter instead of
reading the clock — so `ReminderAlarmSchedulerTest` can assert every
hour/interval combination deterministically, with no device and no flakiness.

## Data flow (example: journal registration)

```
UI → JournalViewModel.addEvent(GrowEvent) → EventRepository (Room) → Flow re-emits
     → GrowRepository.addEvent (facade) → XP reward + platform achievements
     → DailyCheckinWorker computes the new streak from event dates
```

## Diagnosis pipeline

1. `DiagnosisScreen` obtains a photo two ways, both in-process:
   - **Camera** → `CameraCaptureActivity` (CameraX) writes a JPEG and returns its
     path through `EXTRA_OUTPUT`. The previous `TakePicturePreview` + implicit
     `ACTION_IMAGE_CAPTURE` path threw `ActivityNotFoundException` /
     `SecurityException` synchronously whenever no camera app could handle the
     intent, which crashed on tap. It also needed a `<queries>` entry for
     `android.media.action.IMAGE_CAPTURE` to be visible at all on API 30+.
   - **Gallery** → `GetContent` returns a `content://` URI, which is copied into
     the cache directory by `Context.copyUriToCache` before decoding. Passing the
     URI straight to a file decoder produced a blank image.
2. `PhotoAnalyzer.analyze(pixels, w, h)` extracts features from the decoded
   bitmap: `chlorosisRatio`, `necrosisRatio`, `spotDensity`, `trichomeRatio`,
   `webbingRatio`, `greenRatio`, and a normalised `greenHealth`. Pure arithmetic
   on an `IntArray`, so it is unit-testable on the JVM with no Android runtime.
3. `PhotoDiagnosisEngine.rank(features, conditions)` scores every condition whose
   `photoEvidence` block declares thresholds, and returns the matches with an
   `evidenceFit` in 0..1. A condition with no `photoEvidence` can never be
   matched by a photo — that was the bug where the photo changed nothing.
4. `DiagnosisEngine.diagnose(symptoms)` is the other engine: a deterministic
   weighted rule engine over the symptom catalogue.
5. `PhotoDiagnosisEngine.combine(photoMatches, symptomResult)` merges them:
   - no photo → the symptom verdict survives untouched;
   - symptoms say **healthy** → the photo drives the verdict;
   - both agree → confidence is blended up (`0.6·symptom + 0.4·evidenceFit`);
   - they disagree → the deliberately ticked symptom wins and confidence is damped.

   A ticked symptom is something the grower actually observed and selected, so it
   outranks a photo hint; the photo fills in when symptoms say nothing.
6. The report resolves Spanish labels, cause and action plan from the asset
   catalog, and can be registered in the journal as a `DIAGNOSIS` or
   `PEST_CONTROL` event.

> **No TFLite in this build.** There is no `.tflite` file in `assets`, so the
> classifier has nothing to load. Rather than ship a stub, photographic
> diagnosis is the deterministic pipeline above: extract measurable features,
> compare them against thresholds authored in the catalog, and show the user
> both the verdict and the measurements it was based on. A model can be dropped
> in behind `PhotoAnalyzer` later without changing the pipeline.

## Testing

75 JVM unit tests in `app/src/test`, all runnable without a device:

| Suite | Covers |
| --- | --- |
| `SuperCycleEngineTest` | Photoperiod phases and superdays |
| `StageProgressEngineTest` | Day-1 semantics |
| `DiagnosisEngineTest` | Symptom rule engine |
| `EventTypeTest`, `GamificationTest` | Enum and XP contracts |
| `WorkManagerInitTest` | The on-demand WorkManager init contract |
| `PhotoAnalyzerTest` | Feature extraction on synthetic frames |
| `PhotoDiagnosisEngineTest` | Ranking, evidence exclusion, combine precedence |
| `ReminderAlarmSchedulerTest` | `nextTriggerAt` across every hour/interval pair |
| `TerpeneProgressionTest` | Level curve, band ordering, badge unlocks |
| `AssetCatalogTest` | Both shipped catalogs, parsed with the app's own schema classes |

`AssetCatalogTest` decodes `terpenes.json` and `diagnostics.json` with the very
same `@Serializable` classes the app uses, so a field rename fails the build
instead of shipping an encyclopedia that silently renders empty. It also
enforces the content rules: ≥150 terpenes, unique ids, no dangling `pairsWith`,
every condition carrying an action plan, and **every condition reachable from
the symptom picker** through its `symptomWeights` map.

- **Instrumentation** (`app/src/androidTest`): `MigrationTest` creates a real v1 database with raw SQL, runs `MIGRATION_1_2`, and validates the upgraded schema + preserved data through Room.

## Asset generation

`app/src/main/assets/data/*.json` is the shipped source of truth. It is produced
by the generators in `tools/`, which are re-runnable and self-validating:

```powershell
.\tools\convert_terpenes.ps1
.\tools\convert_diagnostics.ps1
```

Both scripts **fail loudly** rather than reporting success on a bad file. They
guard against the two failure modes that actually occurred:

- **Schema violations.** PowerShell's `ConvertTo-Json` writes an array that was
  unrolled on return as `{}`, and an explicit null as `null`. Both make
  `kotlinx.serialization` reject the whole catalogue, so the encyclopedia renders
  empty at runtime. Wrapping list fields in `@()` and coercing nulls to the
  declared defaults fixes it; the scripts assert the result has no `{}` or
  `null` where a list or a non-null value belongs.
- **Non-idempotent rewrites.** An earlier version rebuilt existing rows with
  empty strings and silently blanked the chemical data of all 158 entries on
  every re-run. The generators now carry every field over verbatim, fill blanks
  from `tools/terp_legacy_enrichment.txt`, and assert the chemistry coverage did
  not drop.

## Build

JDK 17 is required (Gradle 8.10.2 / AGP 8.7). Set:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
```

Room schema export: `app/schemas/com.trichome.app.data.database.AppDatabase/` (KSP).