# v1.1.0 — Visual toggle & plant detail fixes

## Objective

Ship the verifiable half of the five-phase request as v1.1.0: fix the endless
loading bug when opening a plant, make glassmorphism genuinely optional with four
opaque high-contrast themes, and stop every destructive action from firing on a
single tap. The Room v2→v3 migration and the plant wizard are deliberately
deferred to v1.2.0.

## Problem

1. **Opening a plant never finishes loading.** `TentListScreen.kt:95-97` wires
   `onOpen` to the whole tent card and always resolves the tent's *first* plant,
   ignoring which row was tapped (`TentListScreen.kt:168` calls `onOpen()` with
   no argument). An empty tent navigates to `plant_detail/-1`. On top of that,
   `PlantDetailViewModel.loadPlant` (`MainViewModels.kt:143-153`) has no
   not-found branch, so a bad id leaves `plant == null` and `PlantDetailScreen`
   renders `"Cargando planta…"` forever.
2. **Glassmorphism is not optional and not theme-aware.** `GlassCard`
   (`GlassmorphismComponents.kt:50`) cannot read the theme: it takes
   `glassOpacity`, `blurRadius` and `accentColor` as parameters with hardcoded
   defaults, and roughly a dozen of its ~30 call sites pass literals that ignore
   the user's preference (`CalendarScreen.kt:344` = `0.10f`,
   `HomeScreen.kt:133` = `0.18f`, `DiagnosisScreen.kt:334` passes nothing).
   There is no on/off switch and no opaque alternative.
3. **Six destructive actions have no confirmation.** Tents, plants, protocols,
   breeding projects, crosses and journal events all delete on a single tap.
   No `AlertDialog` in the app is a destructive confirmation; all eight are
   create/edit forms.
4. **Editing a protocol discards its stages.** `ProtocolScreen.kt:254-262`
   initialises `remember` with a hardcoded three-block default and never reads
   the protocol's real `ProtocolStage` rows, so saving an edit overwrites the
   existing schedule.

## Why this order

The v2→v3 migration is the only part of the request that cannot be verified on
this machine: there is no AVD, `room-testing` is not a dependency, and
`unitTests.isReturnDefaultValues = true` would make a JVM Room test pass without
checking anything. Shipping it in the same release as safe fixes would mean a
broken migration on a real phone drags the fixes down with it. User chose to
split: v1.1.0 verifiable, v1.2.0 schema.

## Scope — authorised

In scope:

- [ ] **T1 — Navigation reaches the right plant.** Pass the tapped plant's id
      instead of the tent's first; stop emitting `-1`.
- [ ] **T2 — `PlantDetailUiState` replaces the endless spinner.** Sealed
      interface `Loading` / `Success` / `Error`, a bounded lookup, an explicit
      not-found message with a way back, and the `loadStageProgress` race fixed
      by loading stages only after the plant resolves.
- [ ] **T3 — Glass components read the theme.** Introduce a `CompositionLocal`
      carrying the glass config so `GlassCard` resolves opacity/border from the
      active theme by default, and change its parameters to nullable overrides.
      Remove the hardcoded literals from the call sites so no screen can
      silently ignore the preference.
- [ ] **T4 — `isGlassmorphismEnabled` preference and solid themes.** DataStore
      key defaulting to `false`, a Switch in Settings → Apariencia, and four
      opaque high-contrast schemes (`Brote Verde Sólido`, `Cosecha Otoñal
      Sólida`, `Oscuro Extremo Sólido`, `Claro Solar Sólido`) selected
      automatically when the flag is off. When off, `GlassCard` renders a
      `Surface` with elevation, `colorScheme.surface` and a solid border.
- [ ] **T5 — Every destructive action confirms.** One shared confirm-dialog
      pattern, applied to all six existing delete sites.
- [ ] **T6 — Protocol edit preserves its stages.**
- [ ] **T7 — Settings reads its own version.** `BuildConfig.VERSION_NAME` and
      the real Room schema version instead of the literals `"1.0.1"` and
      `"Room v${2}"`.
- [ ] **T8 — Single source for the glass opacity clamp.** The repository
      clamps to `0.50f` while the state holder and the slider use `0.55f`, so
      values in between are silently lost on restart.
- [ ] **T9 — Tests and quality gate.** JVM tests for T2, T3, T4, T5 and T6;
      then `testDebugUnitTest lintDebug assembleDebug assembleRelease`.

Out of scope, explicitly:

- Room v2→v3 migration, new `Plant` columns, `AddPlantScreen` wizard, the
  "Mis Plantas" screen, plant edit/delete orchestration (T5 only adds the
  confirmation to the existing delete) — all v1.2.0.
- Calendar event detail sheet, calendar and reminder CRUD — v1.2.0.
- Protocol template library, `protocols_default.json`, custom protocol editor
  improvements beyond T6 — v1.2.0.
- Real `Modifier.blur`. There are zero blur call sites in `app/src/main`, by
  design (`GlassmorphismComponents.kt:41-47`); `blurRadius` is a colour-mix
  depth factor. This release does not reintroduce a blur.

## Constraints

- `com.trichome.app` is frozen. No schema change in this release.
- Manual DI only; ViewModels via `viewModelFactory { initializer { ... } }`.
- UI strings in Spanish; code, comments, identifiers and commit messages in
  English.
- No `fallbackToDestructiveMigration`, no Hilt/Koin/Dagger.
- Do not add a dependency unless it is unavoidable. T3 must be solved with
  Compose's own `staticCompositionLocalOf`, not a library.

## Decisions

- **`genetics` is not added.** The request asked for a `genetics: String`
  column, but `Plant.strain` already exists and holds the same thing. Adding
  both would be a duplicate column. T7/v1.2.0 will reuse `strain`.
- **The toggle is an opacity/surface toggle, not a blur toggle.** No blur
  exists in the codebase, so "glassmorphism off" means opaque `Surface` with
  elevation and a solid border.
- **Default is `false`,** as specified. Existing users who never chose a look
  will get the opaque themes after upgrading. That is consistent — they never
  opted in — and is called out in the release notes.
- **Plant deletion orphans four tables.** `protocols`, `super_cycle_configs`,
  `stage_entries` and `reminders` carry a `plantId` with no foreign key; only
  `grow_events` cascades. v1.2.0 will do explicit repository deletes plus
  indices rather than the SQLite create-copy-drop-rename needed for real FKs,
  because rebuilding tables against a user's live database is the riskier path.
- **TDD is on for this feature.** No `sdd-init` record existed, so the mode was
  resolved for this feature only: every task is a contract-shaped fix with a
  testable JVM seam, and the repository's existing gate is already JVM tests.
  This is an ODD-level decision, not an `sdd-init` one. Runner:
  `./gradlew.bat :app:testDebugUnitTest`.

## Progress

| Task | Route | Status | Evidence |
| --- | --- | --- | --- |
| T1 navigation | delegated writer | pending | |
| T2 plant detail state | delegated writer | pending | |
| T3 CompositionLocal | delegated writer | pending | |
| T4 toggle + solid themes | delegated writer | pending | |
| T5 confirmations | delegated writer | pending | |
| T6 protocol stages | delegated writer | pending | |
| T7/T8 settings + clamp | delegated writer | pending | |
| T9 tests + gate | delegated | pending | |

## Acceptance criteria

- Tapping any plant row opens that plant; an empty tent cannot navigate to a
  plant detail.
- A plant detail for an unknown id shows a readable error with navigation back,
  never an indefinite spinner.
- With the toggle off, every glass surface renders opaque with a solid border
  and readable text; with it on, the translucent look returns. Both survive a
  process restart.
- No glass call site passes a hardcoded opacity that overrides the preference.
- No delete happens without an explicit confirmation, on any of the six sites.
- Editing and saving an existing protocol leaves its stages intact.
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
  :app:assembleRelease` succeeds with zero lint errors.

## Known limits of this release

- No device or AVD is available here, so the visual result is verified by
  compile, lint and JVM tests, not by running the app.
- The glass toggle changes surface rendering, not blur; if a real blur is ever
  wanted it is a separate, deliberate decision.
