# v1.3.0 — Eliminar Glassmorphism, contraste sólido

## Objective

Remove the glassmorphism engine entirely and leave a purely opaque, high-contrast
Material 3 interface. Ship as **1.3.0**, not 1.0.2: v1.0.1, v1.1.0 and v1.2.0 are
already published, so a lower versionName reads as a regression.

## Problem

1. **The glass engine has to go.** 25 production files carry it: 56 `GlassCard`
   calls, 29 `FloatingOrbBackground` references, 44 references to
   `GlassConfig` / `GlassRanges` / `LocalGlassConfig`, plus `GlassmorphicBottomBar`
   (16), `GlassChip` (18), `GlassSlider` (8) and `GlassProgressIndicator` (6).
   Three test files depend on the glass vocabulary too.
2. **Button contrast is not guaranteed.** `ProtocolScreen.kt:406` and many others
   set `containerColor = accent`, where `accent` is a colour the user picks. The
   accompanying `onPrimary` has to clear 4.5:1 against *any* accent, not the
   shipped green.

## Scope — authorised

- [ ] **G1 — Opaque panels.** Replace `GlassCard` with a solid, M3-based panel.
      Call sites pass `accentColor` 40 times, `glassOpacity` 27 times and
      `cornerRadius` 2 times. `accentColor` and `cornerRadius` are kept;
      `glassOpacity` is deleted outright, not deprecated.
- [ ] **G2 — Remove the animated background.** `FloatingOrbBackground` and its
      29 references are the translucency: with no glass there is nothing for an
      orb to show through.
- [ ] **G3 — Solid bottom bar, slider, chip and progress ring.** M3
      `NavigationBar` / `Slider` / `FilterChip` / `CircularProgressIndicator`
      equivalents, keeping the accent as the selected state.
- [ ] **G4 — Delete the glass tokens and preferences.** `GlassTokens`,
      `GlassConfig`, `LocalGlassConfig`, `GlassRanges`, the `glass_enabled`,
      `glass_opacity` and `blur_radius` DataStore keys, and the "Efecto de
      cristal" switch and the "Panel de cristal" slider card in Settings.
- [ ] **G5 — Four themes, not eight.** The four glass palettes are deleted; the
      four solid ones take the plain names. Every one of them is already verified
      opaque, with `outline` at 3:1 and the ink roles at 4.5:1.
- [ ] **G6 — Guaranteed button contrast.** `onPrimary`, `onSecondary` and
      `onTertiary` resolved against whatever accent the user chose, by colour
      math rather than by hope, with a test sweeping every accent.
- [ ] **G7 — Tests and gate.**

Out of scope, explicitly:

- The Room v2→v3 migration and the supercycle move to the tent. The SQL in the
  brief targets a table called `tents` that does not exist (it is `grow_tents`)
  with four columns that do not exist on `GrowTent`, and needs the migration that
  cannot be verified without a device.
- `SavedStateHandle`. The brief's
  `savedStateHandle.get<String>("plantId")?.toLongOrNull()` would break
  navigation: the route declares the argument as `Long` and reads it with
  `getLong`, so `getString` returns null and every plant detail falls to its
  error state.
- The breeding theory "crash" and the delete dialog. Neither exists: the chapter
  lookups are all `firstOrNull`, and the confirmation dialog's visibility already
  derives from `pending` in the same composition.

## Decisions

- **A new panel component, not a repurposed `GlassCard`.** The name is the
  vocabulary: keeping it after removing the effect would leave a lie in the code.
  The replacement is M3 `Card` with `colorScheme.surface`, an `outline` border and
  elevation. The accent survives only where it carries meaning — selected states,
  progress, active borders — never as a translucent fill.
- **The accent never paints a large surface.** It is user-chosen, so it cannot be
  trusted to contrast against a card. Borders use `outline`, which is verified.
- **Existing DataStore keys are left in place, unread.** Deleting a key from
  preferences does not remove it from an installed app, and there is no cost to
  leaving three orphaned integers.
- **Font family, weight and scale survive.** They are accessibility settings, not
  glassmorphism.
- **Work in three buildable steps**, so the compiler can check each stage: add the
  replacements, migrate the call sites, then delete what is unused.

## Constraints

- `com.trichome.app` frozen. **No schema change.** Room stays at v2.
- No new dependencies.
- UI in Spanish with correct accents; code and comments in English.
- Never two Gradle invocations at once.

## Acceptance criteria

- `grep -rn "Glass" app/src/main` returns nothing.
- `grep -rn "Modifier.blur\|copy(alpha" app/src/main` returns nothing that tints a
  panel background.
- No `FloatingOrbBackground` anywhere.
- Settings offers theme, accent and typography, and nothing about crystal.
- `onPrimary` clears 4.5:1 against every accent the app offers.
- The build stays green after every step, not only at the end.
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
  :app:assembleRelease` passes with zero lint errors.

## Known limits

- No device or AVD here, so the result is verified by compile, lint and JVM tests.
  Whether the interface is pleasant is a judgement the orchestrator cannot make.
