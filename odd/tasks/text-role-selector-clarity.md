# Text role selector: make the effect visible instead of described

## Objective

A user picks a text colour and sees nothing change. Not because the app is
wrong — `solidSchemeFor` already injects `resolvedTextColors` into the scheme
(line 507/545), so the dialog reports the real derived value — but because the
dialog only *describes* what each role paints. Picking is a leap of faith.

## Problem

Verified on device `FYJBONYTT8GENBTW`: `secondary_text_argb = #FFFFC107` is
persisted while `primary_text_argb`, `tertiary_text_argb` and `button_argb` are
absent from the DataStore file. Titles are painted with the primary role, so they
fall back to the palette default (near-white on `Cuidado Nocturno`). The user
reports "el color del texto no se ha arreglado".

The dialog already says `Ahora: el color del tema` for the unset roles, so the
information exists — but four equal-looking panels, each with a description line
and a row of circles, do not tell you that the role you set is not the role that
paints the biggest text on screen.

## Why this framing

Show, do not tell. The highest-value change is a live preview: sample text
rendered at the real type style, in that role's resolved colour, on the real
backdrop. A second one-line summary answers "what have I actually changed?" at a
glance, which the dialog currently cannot.

## Scope

In scope:
- `ColorRolesDialog.kt` — per-role live preview, at-a-glance summary of which
  roles are customised.

Out of scope (separate, already identified):
- The dead parameters and stale KDoc left by the panel-edge commit `099f7c2`
  (7 should-fix items, including `ReminderRow` losing its last accent).
- The device's `sans-serif` being a handwriting font. The app's selector works.

## Constraints

- UI strings in Spanish. Code, KDoc and comments in English.
- No new dependencies. Manual DI untouched.
- No Room/schema change.
- A role's preview must use the *resolved* colour, never the raw override, so the
  derivation rule stays visible.

## Tasks

- [x] T1 Live preview per role: sample text in the resolved colour, on the real
      backdrop, at the type style that role actually paints with. — `ad7991e`
- [x] T2 At-a-glance summary naming which roles are customised and which are on
      the theme default. — `ad7991e`
- [x] T3 Tests pinning that the preview colour is the resolved one and not the
      raw override. — `ad7991e` (14 tests)
- [x] T4 The preview exposed a contradiction the dialog already had: a colour
      refused for contrast still drew the *selected* ring, still printed its hex
      in `Ahora:`, and was still counted as customised by the summary, while the
      app painted the palette fallback. Three reports, two answers. — `d77864e`
      (11 more tests)

## Progress

- 2026-10-01: feature document created. Findings above verified against the
  device DataStore and `TrichomeTheme.kt` source, not inferred.
- 2026-10-01: T1-T3 landed in `ad7991e`, 478 tests green. Device-verified on
  `FYJBONYTT8GENBTW`: the summary reads "Personalizado: Texto secundario. Del
  tema: Texto primario, Texto terciario y Color de los botones", which is exactly
  the answer that was missing. The primary preview renders at title size, the
  secondary at body size.
- 2026-10-01: T4 landed in `d77864e`, 489 tests green. Reproduced the original
  contradiction on device before fixing it and re-verified after: the refused
  swatch now carries a 2dp `error` ring, the readout reads "Descartado: #FFE53935
  no se lee sobre esta superficie. Se usa el del tema: #FFF5F7FF.", and the
  summary no longer counts it.
- 2026-10-01: the writer deliberately exempted the button role from T4, because
  `solidSchemeFor:511` applies `overrides.button ?: accent` verbatim with no
  contrast gate. Verified that line directly; the exemption is correct, and
  applying the rule there would have produced the same class of false report,
  inverted.

## Known remaining

- `ThemeSwatch` selects on `selected == null`, so when a pick is stored *and*
  refused, the "Del tema" swatch draws no ring even though the app is painting the
  theme colour. Same defect class as T4, opposite direction. One line, not fixed.
- `RejectedPicks.NONE` as a defaulted argument is a footgun: a caller that forgets
  it re-opens T4. Two wiring tests are the only guard.

## Acceptance criteria

- A user can tell, without leaving the dialog, which role paints titles.
- Setting only the secondary role is visibly distinguishable from setting the
  primary.
- No test regresses; the existing colour-override contract is untouched.

## Verification

- `./gradlew :app:testDebugUnitTest` — must report 0 failures.
- Device check on `FYJBONYTT8GENBTW`: open Ajustes, confirm each section shows
  its resolved colour as sample text. Verify `mCurrentFocus` before each tap —
  a third-party app steals foreground.

## Route

Delegated writer (2+ non-trivial files with an unresolved design decision).
Trigger: writer rule.

## Progress

- 2026-10-01: feature document created. Findings above verified against the
  device DataStore and `TrichomeTheme.kt` source, not inferred.
