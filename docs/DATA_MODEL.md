# Data Model

Room database **version 6** (`exportSchema = true`, schemas exported to `app/schemas/`).

Every step from v1 to v6 has an explicit `Migration` in `data/database/Migrations.kt` and a test.
`fallbackToDestructiveMigration()` is forbidden (`AGENTS.md` §11): the data-loss budget is zero,
and an upgrade either preserves every row or the app does not open.

## Entities

| Table | Entity | Notes |
| --- | --- | --- |
| `plants` | `Plant` | FK `tentId → grow_tents.id` (`SET_NULL`: deleting a tent keeps the plant as "Sin carpa"). `sortOrder` for manual ordering inside a tent. |
| `grow_tents` | `GrowTent` | Container for plants; intentionally FK-free (ownership lives on `Plant`). |
| `protocols` | `Protocol` | Protocol header: name, photoperiod (`lightHours`, `darkHours`), `presetType`, `cycleStartAt`, `isActive`, plus fourteen nullable agronomic **targets** from v4 (`vpdBand`, `phRange`, `ecRange`, light/dark temperature and humidity, `ppfd`, `dli`, fixture and substrate text, `observations`). All declared, none measured. |
| `protocol_stages` | `ProtocolStage` | Ordered stage blocks of a protocol (FK `protocolId` → `protocols.id`, `CASCADE`), `sortOrder` for ordering, `durationDays`, optional `recurrenceIntervalDays`, and `vpdTarget` (v6): the VPD band the grower aims at **while the plant is in this stage**. |
| `stage_entries` | `StageEntry` | Transition log: which plant entered which stage, when (`enteredAt`/`exitedAt`). |
| `grow_events` | `GrowEvent` | Journal bitácora: 16 event types, nullable metric columns (temp/humidity/pH/EC/amount/height/VPD…), `groupId` for multi-plant registrations, `diagnosisResult`/`diagnosisCertainty`/`imagePath`, and from v5 the VPD provenance pair `vpdSource`/`vpdLeafOffset`. FK `plantId → plants.id` (`CASCADE`). |
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

## Migration v2 → v3 (`MIGRATION_2_3`)

`super_cycle_configs` becomes tent-scoped: a new table is built with `tentId`, every row is
copied **without a `WHERE` clause**, and the orphan configs survive the move. `tentId` is
nullable because two real rows resolve to a plant that no longer exists, and they were kept
rather than dropped. The old `plantId` column stays, as provenance.

## Migration v3 → v4 (`MIGRATION_3_4`)

Fourteen nullable columns on `protocols`, one `ALTER TABLE … ADD COLUMN … DEFAULT NULL` each,
declared once in `PROTOCOL_V4_ADDED_COLUMNS` and read by both the migration and its audit test.
The photoperiod columns stay `NOT NULL` with their defaults; everything new describes what the
grower *intends*, and a plausible-looking `phRange = 6.0:6.5` on a protocol nobody measured is
the `EstimatedClimate` defect in a new place.

## Migration v4 → v5 (`MIGRATION_4_5`)

Two nullable columns on `grow_events`: `vpdSource` and `vpdLeafOffset`. They record **where a
VPD number came from**, because `vpd` is a single `REAL` column holding both a hygrometer
reading and a value this app derived. `NULL` on a pre-v5 row resolves to `UNKNOWN`, never to
`MEASURED`: some of those numbers were typed and some were derived, and the column cannot tell.

## Migration v5 → v6 (`MIGRATION_5_6`)

One nullable column on `protocol_stages`: `vpdTarget`, the per-stage VPD band, stored as TEXT by
the same `GrowRangeConverters` the header uses. **One band type, one encoding, one converter.**

- **Why here and not on the header.** `protocols.vpdBand` is the band the grow is *run at*, a
  property of the setup; a stage is where the environment actually changes, so the per-stage
  refinement belongs to the stage. v4's own KDoc said so and declined to do it in that phase.
- **Why a target and not a reading.** Every VPD this app knows is an offline estimate by
  latitude or a calculation from two typed numbers. The column is named `vpdTarget` for the same
  reason the climate card prints `≈`: a column called `vpd` on a stage would read as an
  observation of the room.
- **What was deliberately left out.** No stage temperature or humidity target. The header
  already declares four of those, and a second copy with no stated precedence is two sources of
  truth — the F1 boiling point and the F2 temperature models, both already paid for.
- **Why `ADD COLUMN` sufficed.** A new nullable column is the one change SQLite performs in
  place. Nothing is dropped, renamed or retyped, so the table's foreign key and its index are
  untouched and no row is copied through a temporary table. The three stages a real protocol
  already holds — `Germinación`, `Vegetativa`, `Floración` — come out the other side with their
  `stageName`, `durationDays`, `recurrenceIntervalDays` and `sortOrder` byte for byte, and
  `vpdTarget` NULL, which the card renders as "Sin definir" and the export writes as `null`.

## How a protocol's stages are written, and what that must never cost

`ProtocolRepository.saveStages` updates, inserts and deletes individual `protocol_stages` rows. It
used to `DELETE FROM protocol_stages WHERE protocolId = :id` and re-insert the whole list, which
was survivable only while nothing wrote `vpdTarget`: the moment a band could be set, saving the
protocol's *name* would have erased it, and every stage's primary key would have been regenerated
on every save. The data-loss budget is zero, so the bulk delete is gone from the DAO rather than
left unused.

- **An edit is an id.** A draft carrying a persisted `id` updates that row in place. A persisted row
  no draft mentions is a removal, and it is the only row ever deleted. A draft with `id == 0` is an
  addition. Identity is deliberately not matched on the stage's name (a rename would read as a
  removal plus two additions) or on its position (a removal from the middle would shift every later
  stage's identity onto its neighbour), which is why the protocol editor seeds from the persisted
  rows and hands them back rather than from a rebuilt name/duration list.
- **The schedule save never writes the band.** `vpdTarget` is copied from the persisted row on every
  update, because a caller that never read the column and a caller that means to clear it are
  indistinguishable in a draft. `ProtocolRepository.setStageVpdTarget` is the only path that writes
  it, and it writes one column of one row addressed by both `id` and `protocolId`.
- **The card is re-read after every stage write.** `protocol_stages` writes do not touch the
  `protocols` row, so the flow the protocol screen observes would not re-emit.

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