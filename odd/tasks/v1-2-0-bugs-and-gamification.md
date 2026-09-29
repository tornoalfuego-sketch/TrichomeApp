# v1.2.0 — Bugs de persistencia, navegación y gamificación

## Objective

Ship the nine confirmed bugs and the four new gamification surfaces from the
five-phase request, with **no schema change**. The Room migration that moves the
supercycle to the tent and extends `breeding_projects` is deferred to v1.3.0.

## Problem — nine real defects, none of which is the `#00000000` accent bug

1. **The calendar ignores a newly saved event.** `CalendarViewModel.events` is
   `mutableStateOf`, `getEventsBetween(from, to)` is a one-shot `suspend`, and
   `loadMonth` runs only from `LaunchedEffect(month, filterPlantId)`
   (`CalendarScreen.kt:63`). Saving an event writes to the database and the event
   does not appear until the user changes month or plant filter. The journal is
   reactive because it collects a `Flow`; the calendar is not.
2. **The trivia explanation is unseeable.** The tap that answers increments
   `round`, which regenerates the question on the next recomposition, so the
   explanation block renders *for question N+1 describing question N* and the
   correct-answer highlight is already gone. There is no next button and no
   right/wrong feedback, so the user cannot tell whether they were correct.
3. **Cancelling a reminder is unreachable.** `ReminderDao.updateReminder` and
   `deleteReminder` have zero callers in `main`. `ReminderAlarmScheduler.cancel`
   fires only while saving an inactive reminder, and no UI can mark one inactive.
   The WorkManager unique name is a bare `String` template in three places with
   no constant, while every other worker uses one.
4. **Breeding cannot be edited and its dialog duplicates.** `BreedingDao` has no
   `@Update` at all, so opening `CrossDialog` on an existing cross inserts a
   duplicate. `status` and `generation` are write-once.
5. **`XP_PER_FAMILY = 120` is never awarded.** The badge is computed; the XP is
   not added anywhere.
6. **Two home tiles lead nowhere.** The "Plantas" and "Carpas" `StatCard`s have
   no `onClick` at all. `tents` is a one-line fix; `plants` needs a route, since
   plants are currently reachable only through a tent row.
7. **Three different default greens.** `AppearanceSettings` uses `0xFF2E7D32`
   (the one that wins on a fresh install), `TrichomeThemeState` and `GlassConfig`
   use `0xFF4CAF50`, and `GlassTokens` uses `0xFF66BB6A`. The latter two are
   effectively dead but nothing asserts that.
8. **No `TopAppBar` defines `colors`.** All eight bars have zero action icons,
   only a back arrow, and draw over a transparent `Scaffold` on top of the orb
   background. There is **no shared top-bar component** — eight copies — so the
   fix is to extract one rather than patch eight.
9. **The themed launcher icon is a green blob.** `ic_launcher.xml` points
   `<monochrome>` at the *coloured* foreground, so a themed icon loses the
   silhouette. A dedicated `ic_launcher_monochrome.xml` does not exist.

## Also in scope — four new gamification surfaces

- **Master Blender simulator**: sliders for terpene percentages, matched against
  the real profile of known strains, with a match percentage.
- **Punnett square**: interactive allele combination with hereditary
  probabilities.
- **Theory chapters with mini-quizzes**: chapters with medal unlocks, on top of
  the existing theory tab.
- **Extended terpene XP**: family completion XP actually awarded, quiz-completion
  XP, and the streak/badge wiring the request asks for.

## Why the migration is deferred

`MIGRATION_2_3` is the only part of the request that cannot be verified on this
machine: there is no AVD, `room-testing` is not a dependency, and
`unitTests.isReturnDefaultValues = true` would make a JVM Room test pass without
checking anything. The user chose to ship everything else first.

The migration is also not a small one. It has to:

- re-key `super_cycle_configs` from `plantId` to `tentId`,
- add the missing `UNIQUE` index, because the table currently has no uniqueness
  constraint and the UI silently picks one of N rows,
- add a `@Delete` to `SuperCycleDao`, which has none,
- add the `breeding_projects` columns,
- and resolve a design fork that must be settled **before** writing it.

## Scope — authorised

In scope:

- [ ] **B1 — Calendar refreshes after insert.** Events become a `Flow`, or the
      month reloads on write. A new event must appear immediately.
- [ ] **B2 — Reminder edit and delete.** An `EditReminderDialog` for title, time
      and recurrence, plus delete. Both must reach the `ReminderDao` methods that
      are currently dead code, and delete must cancel **both** the `AlarmManager`
      alarm and the WorkManager job. The unique work name becomes a single
      constant like every other worker.
- [ ] **B3 — Plant edit and delete from the detail screen.** `PlantDetailScreen`
      currently has zero action icons and `PlantDetailViewModel` has no edit or
      delete method at all.
- [ ] **B4 — Breeding update.** `@Update` on `BreedingDao` for project and
      cross, so the dialogs update instead of duplicating.
- [ ] **B5 — Trivia lifecycle.** Show the explanation for the question just
      answered, then advance on an explicit action, with right/wrong feedback.
- [ ] **B6 — Family XP awarded.** `XP_PER_FAMILY` stops being dead, and the XP
      total stops being recomputed inline in one ViewModel.
- [ ] **B7 — Master Blender simulator.**
- [ ] **B8 — Punnett square.**
- [ ] **B9 — Theory chapters with mini-quizzes and medals.**
- [ ] **B10 — Home tiles navigate.** One shared accent constant, one shared
      `AppTopBar` with explicit colours, a real monochrome icon, and the two dead
      tiles wired to real destinations.
- [ ] **B11 — Tests and quality gate.**

Out of scope, explicitly:

- `MIGRATION_2_3`, the supercycle move to the tent, and the new
  `breeding_projects` columns — v1.3.0.
- `parentA` / `parentB`. `BreedingProject` already has `motherId` / `fatherId`
  and `BreedingCross` already has `parent1` / `parent2`; adding a third pair of
  free-text parent fields would be duplication.
- `isSupercycleEnabled` on the tent, until the `Protocol` photoperiod fork is
  settled. `Protocol` already carries its own `lightHours` / `darkHours` /
  `cycleStartAt`, so the app has two competing photoperiod sources today.
- Anything that would change the Room schema.

## Decisions

- **Room stays at v2.** No migration, no entity change, no `@ColumnInfo`.
  Everything in this release is reachable without touching the schema.
- **Reminder delete is a full cancel**, not just a row delete: the `AlarmManager`
  alarm, the WorkManager unique work, and the row. The request named
  `ReminderScheduler.cancelReminder(reminderId)`; the real API is
  `ReminderAlarmScheduler.cancel(context, reminderId)` plus
  `WorkManager.cancelUniqueWork`, and both are needed.
- **Master Blender scoring is a pure function** of the user's percentages and the
  catalogue, so it is JVM-testable. The strain profiles come from the existing
  `pairsWith` and `families` data rather than a new hardcoded table, unless the
  data turns out not to support it.
- **The Punnett square is a pure function** of two parents' genotypes. It must
  be, or it is untestable.
- **The shared top bar is extracted, not duplicated.** One component, eight
  call sites migrated, explicit `topAppBarColors` with a guaranteed-contrast
  content colour.
- **TDD is on**, runner `./gradlew.bat :app:testDebugUnitTest`, same as v1.1.0.

## Known limits of this release

- No device or AVD here, so visual results are verified by compile, lint and JVM
  tests, not by running the app.
- `breathing`/`isSupercycleEnabled` per tent remains unresolved and will be
  decided before the v1.3.0 migration is written.
