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

## Supporting data

- **DataStore** (`data/prefs`): appearance settings (opacity, blur, theme index, font scale, accent ARGB).
- **Assets** (`assets/data/`): `terpenes.json`, `diagnostics.json`, `breeding.json` — read-only catalogs parsed with kotlinx-serialization.

## Design notes (fixed during development)

- The FK that was once declared on `GrowTent` (`tentId`) referenced a column that table does not own → moved to `Plant` where `tentId` lives (`SET_NULL`), matching the original intent.
- `GrowEvent.plantId` FK uses `CASCADE` so deleting a plant removes its journal entries.