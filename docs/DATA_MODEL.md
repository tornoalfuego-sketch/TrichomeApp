# Data Model

Room database **version 2** (`exportSchema = true`, schemas exported to `app/schemas/`).

## Entities

| Table | Entity | Notes |
| --- | --- | --- |
| `plants` | `Plant` | FK `tentId → grow_tents.id` (`SET_NULL`: deleting a tent keeps the plant as "Sin carpa"). `sortOrder` for manual ordering inside a tent. |
| `grow_tents` | `GrowTent` | Container for plants; intentionally FK-free (ownership lives on `Plant`). |
| `protocols` | `Protocol` | Protocol header: name, photoperiod (`lightHours`, `darkHours`), `presetType`, `cycleStartAt`, `isActive`. |
| `protocol_stages` | `ProtocolStage` | Ordered stage blocks of a protocol (FK `protocolId` → `protocols.id`, `CASCADE`), `sortOrder` for ordering, `durationDays`, optional `recurrenceIntervalDays`. |
| `stage_entries` | `StageEntry` | Transition log: which plant entered which stage, when (`enteredAt`/`exitedAt`). |
| `grow_events` | `GrowEvent` | Journal bitácora: 16 event types, nullable metric columns (temp/humidity/pH/EC/amount/height/VPD…), `groupId` for multi-plant registrations, `diagnosisResult`/`diagnosisCertainty`/`imagePath`. FK `plantId → plants.id` (`CASCADE`). |
| `super_cycle_configs` | `SuperCycleConfig` | Per-plant SuperCycle photoperiod configuration. |
| `achievements` | `Achievement` | Gamification achievements seeded at first launch. |
| `reminders` | `Reminder` | Recurring reminders (`recurrenceIntervalDays`, `reminderTime` as millis-of-day). |
| `breeding_projects` | `BreedingProject` | Breeding projects (mother/father, generation, status). |
| `breeding_crosses` | `BreedingCross` | Crosses inside a project (FK `projectId` → `breeding_projects.id`, `CASCADE`), `phenotypeScore`. |

## Migration v1 → v2 (`MIGRATION_1_2`)

The migration is explicit, in code (`data/database/Migrations.kt`):

- **`protocols`**: adds `lightHours` (18), `darkHours` (6), `presetType` ('custom'), `cycleStartAt` (0).
- **`grow_events`**: adds `groupId`, `temperature`, `humidity`, `ph`, `ec`, `nutrientN/P/K`, `amount`, `height`, `lampDistance`, `trainingType`, `defoliationLevel`, `vpd`, `trichomeMaturity`, `diagnosisResult`, `diagnosisCertainty`, `imagePath`.
- **New tables**: `protocol_stages`, `stage_entries`, `achievements`, `reminders`, `breeding_projects`, `breeding_crosses` (+ indices `index_protocol_stages_protocolId`, `index_breeding_crosses_projectId`).

No `createFromAsset`, no `fallbackToDestructiveMigration` — data is preserved. Covered by the instrumentation test `MigrationTest` (raw v1 baseline → open with Room + migration → validate).

> **The schema is still v2.** Everything added in 1.0.1 lives outside Room, so
> no `MIGRATION_2_3` was needed and no existing user data was touched.

## Supporting data

- **DataStore** (`data/prefs`):
  - `AppearanceSettings` — opacity, blur, theme index, font family/weight/scale, accent ARGB, custom text/background/border colours.
  - `TerpeneProgressRepository` — discovered terpene ids, favourites, completed families, quiz score and daily streak.
  - `OnboardingRepository` — launch counter, so the tour stops after the third open.
- **Assets** (`assets/data/`): `terpenes.json`, `diagnostics.json`, `breeding.json` — read-only catalogs parsed with kotlinx-serialization.

## Asset schemas

Both catalogs are validated by `AssetCatalogTest` using the app's own
`@Serializable` classes, so the file and the parser can never drift apart.

### `terpenes.json` (v2) — 158 compounds

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string | Unique; snake_case for generated entries |
| `name` | string | Spanish display name |
| `formula`, `molarMass` | string | Kept as strings: formulas are displayed, not computed |
| `family` | string | `Monoterpeno` / `Sesquiterpeno` / `Diterpeno` |
| `aroma`, `taste` | string | Free Spanish prose |
| `effects`, `medicalProperties` | string[] | Short tags |
| `mechanism`, `biosynthesis`, `toxicity` | string | The doctoral-level detail |
| `pairsWith` | string[] | Entourage links; **every id must exist in the catalog** |
| `strains`, `foundIn` | string[] | Cannabis cultivars and non-cannabis sources |
| `richness` | string | `muy alto` / `alto` / `medio` / `bajo` |
| `boilingPoint` | string | Rendered as a temperature, e.g. `167 °C` |

### `diagnostics.json` (v2) — 49 conditions, 116 symptoms

| Field | Type | Notes |
| --- | --- | --- |
| `id` | string | Unique condition id |
| `category` | string | `deficiency` / `pest` / `fungus` / `environment` / `nutrient` |
| `label_es`, `short_es` | string | Full and chip-sized Spanish labels |
| `severity` | string | `leve` / `moderada` / `severa` |
| `symptomWeights` | map symptomId → weight | **The only link from a condition to the symptom picker** |
| `photoEvidence` | object? | `chlorosisMin`, `necrosisMin`, `spotDensityMin`, `trichomeMin`, `webbingMin`, `greenMin`, `weight`; `-1` means "not relevant" |
| `photoNotes_es` | string | How to frame the photo |
| `cause_es` | string | Why it happens |
| `actionPlan_es` | string[] | What to do now |
| `prevention_es` | string[] | How to avoid it |
| `affectedPlants` | string[] | Stage restriction; empty means unrestricted |

> **`DiagnosisSymptom` has no `conditionId`.** The relationship is one-way:
> condition → symptom → weight, held in `symptomWeights`. A condition with an
> empty map is invisible to the user, because the symptom picker is the only way
> to reach it. That is why the 34 generated conditions carry a derived map, and
> why `AssetCatalogTest` asserts every condition is reachable.

## Design notes (fixed during development)

- The FK that was once declared on `GrowTent` (`tentId`) referenced a column that table does not own → moved to `Plant` where `tentId` lives (`SET_NULL`), matching the original intent.
- `GrowEvent.plantId` FK uses `CASCADE` so deleting a plant removes its journal entries.
- **An explicit `null` is not the same as an absent field** for
  `kotlinx.serialization`: it overrides the declared default and fails the whole
  decode. The original asset shipped `"affectedPlants": null` on 15 conditions
  and `"medicalProperties": {}` on 10 terpenes, which made the entire catalogue
  unparseable. The generators now coerce both, and assert it.
- **An empty array returned from a PowerShell function is unrolled to nothing**,
  and `ConvertTo-Json` then writes `{}`. Every list field is wrapped in `@()` at
  the call site so empty renders as `[]`.