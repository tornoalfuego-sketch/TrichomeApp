# Entourage expansion — F1 data coherence, F2 volatility curves

## Objective

Widen the Entourage (Séquito) module along the axes a spec proposed, without
disturbing what already ships. F1 closes the places where the module's own data
disagrees with itself. F2 turns a single boiling-point number per terpene into a
vapourisation window and a stepped curve, for all 158 terpenes in the Bible.

## Problem

The module has two independent sources of the same physical fact and nothing
forces them to agree. `terpenes.json` carries a measured `boilingPoint` for all
158 terpenes. `entourage_data.json` carries `boilingPointC` for the 10 that also
have a `min`/`max` window. `pinene` reads 155 °C in the first, `ALPHA_PINENE`
reads 156 °C in the second. Neither side is wrong; they are just two truths with
no arbiter, and F2's unified view has to stand on both of them.

Two pieces of hardcoded text also went stale against the data they describe.
The Entourage achievement says "Acierta 8 de 10" while the real threshold is a
fraction of the asset total, so changing the question count makes the badge lie.
And the vapourisation table's `minTempC` equals `boilingPointC` exactly in 9 of
its 10 rows — that reads like a copy-paste bug and is actually the `require` in
`Entourage.kt:192` being satisfied, so it needs documenting before F2 builds on
it.

## Why this framing

The user asked for "curvas de calor de temperatura de vaporización de cada
terpeno". The measured data is already in the repo for every terpene, so the
honest move is not to invent estimates for the 148 that lack a window but to
derive a window from a measured boiling point and a chemical family, and label
it as derived. `EstimatedClimate` already establishes the precedent: an estimate
that does not announce itself is a fabrication.

## Scope

In scope, F1:
- Cross-source agreement test for the 10 dual-sourced terpenes, naming both
  sides on failure.
- Resolve the pinene delta by declaring which source is canonical and why.
- Derive the Entourage achievement's threshold from the asset instead of
  hardcoding it.
- Document the `minTempC == boilingPointC` invariant in the KDoc.
- Audit the 7 `mechanism_es` strings against the evidence standard: each names
  its level (in vitro / animal / human) in the visible text.

In scope, F2:
- `TerpeneVolatility`: one view type over boiling point + window + provenance
  (`MEASURED` / `DERIVED`).
- `VolatilityWindow.derive` for the 148 terpenes that have a boiling point but no
  window, banded by family.
- `VolatilityCurve`: a stepped curve per selection, reusing the `windowFor` /
  `isViable` model that already exists.
- A volatility section on every one of the 158 terpene detail pages, which today
  show only "Punto de ebullición" (`TerpeneDetailScreen.kt:144`).

Out of scope, and why:
- The "porcentaje de potenciación del efecto" from the source spec. Not
  computable from any published human data; the number would be fabricated.
  Profile match is a real distance and is what the UI shows.
- Cannabinoid additions (CBC, THCV). No synergy exists for them, so the chips
  would select and return nothing. **Open user decision, not yet taken.**
- Agronomy, extraction and decarboxylation (F3, F4). Nothing exists yet:
  zero occurrences of UV, mevalonate, MEP, biosynthesis, harvest, rosin or
  decarboxylation across the whole asset.
- Levelled quiz and extra badges (F5). Highest risk in the plan, sequenced last.
- The 119 °C vacuum caryophyllene figure. It would violate
  `require(minTempC <= boilingPointC)`, and it is a distillation note rather than
  a vaporiser setpoint.

## Constraints

- `versionCode` / `versionName` / `compileSdk` / `targetSdk` untouched.
- `APP_DATABASE_VERSION = 3`. No migration. No `fallbackToDestructiveMigration`.
- `EntourageAssetTest` and `EntourageLabTest` are not loosened. No feature is
  bought by weakening a science assertion.
- Engine logic in `LunarEngine`, `AmbientClimate`, `TerpeneBlender` and
  `EntouragePlanner` is not modified. A bug found in them is reported, not
  patched in the same commit.
- UI strings Spanish. Code, KDoc, comments English.
- Solid Material 3. No hardcoded colours. One scroll owner per axis.
- No new dependencies. Manual DI untouched. `achievements` table reused.
- Nothing published to git until the user asks.

## Tasks

- [x] T1 `823ccaa` — `everyBoilingPointAgreesWithBothCatalogs` walks the raw
      asset rows, not `content().vaporisation`, so a row dropped for a bad window
      cannot take its boiling point out of scope with it. Names both keys and both
      values; verified by mutating `ALPHA_PINENE` to 155.
- [x] T2 `823ccaa` — `terpenes.json` declared canonical (KDoc on
      `EntourageTerpene` and `TerpeneVaporisation`). The 155/156 pinene delta is a
      join artefact: `alpha_pinene` is 156, the generic `pinene` is 155.
      `aModuleTerpeneNeverPairsWithAGenericEncyclopediaEntry` pins the pairing.
      No numeric delta found anywhere: all 10 rows already agree.
- [x] T3 `823ccaa` — badge text is `descriptionFor(rounds)`, built from the
      round count the run actually played; `toAchievementRow(rounds)` takes it too.
      Tested for 10, 11, 15, 7, 0 and every length 1..40.
- [x] T4 `823ccaa` — invariant documented on `TerpeneVaporisation`. The
      `BETA_CARYOPHYLLENE` 12 °C margin is recorded as **not established** from
      anything in the repo; the KDoc says so instead of inventing a reading.
- [x] T5 `823ccaa` — audit report below. **No science text changed.** All 7
      `mechanism_es` name their level in `evidence_es` and all 7 pass the new
      guards; `cbd_caryophyllene` pinned as the reference standard. Two prose
      nits reported, not fixed: the level lives in `evidence_es` rather than
      inside `mechanism_es` itself, and `thc_myrcene`'s "produce sedación y
      relajación muscular" reads slightly more assertively than its level allows.
- [x] T6 `a32b316` — `TerpeneVolatility` (boiling point + band + provenance on
      the instance, no constructor path without it) and the 158-row
      `TerpeneVolatilityIndex`, fused from `terpenes.json` (point) and
      `entourage_data.json` (band). 10 MEASURED, 148 DERIVED, 0 dropped.
      **Already implemented and committed by the previous writer in `cfaa49a`;
      this phase verified it rather than rebuilding it.**
- [x] T7 `cfaa49a` — `VolatilityDerivation.derive` / `floorFor` / `ceilingFor`,
      banded by family with `DERIVED` provenance and every band quantised to
      10 °C. One constant for every family, because the ten shipped rows show no
      per-family effect. Verified against the asset; see "The band model".
- [x] T8 `cfaa49a` — `VolatilityCurve` as steps, with `stages`, `lostSteps` and
      `barFor` geometry in the model. `VolatilityCurves.aggregate` is the single
      aggregate rule, read by both `VolatilityCurve` and `EntouragePlanner.windowFor`.
      The zero-weight crash in `barFor` was already fixed in `cfaa49a` and was not
      redone.
- [x] T9 `cfaa49a` + `a32b316` — volatility section on all 158 detail pages
      (`VolatilityCard`), with the evidence and limits lines rendered
      unconditionally. `a32b316` fixed the one layout defect left: the
      provenance `DataRow` collided with its own value.
- [x] T10 F1 + F2 verification: compile, tests, lint, device screenshots —
      `a32b316`. 991/61 green, lint 0 errors / 353 issues, all three families
      photographed. `assembleRelease` not run; see Progress.

## Verification

Per phase, in order:

```
:app:compileDebugKotlin
:app:cleanTestDebugUnitTest :app:testDebugUnitTest   # baseline 875, read total from XMLs
:app:assembleDebug :app:installDebug                 # then screenshots on the device
:app:lintDebug                                       # 0 errors, baseline 353 issues
```

`assembleRelease` closes F2 only if F3-F5 follow; R8 fails only in release and
that build is the only thing that catches it.

## Progress

**F2 closed at `a32b316`** (code) plus the doc commit after it, off `cfaa49a`
which carried the previous writer's T6-T9 and the zero-weight crash fix.
**991 tests, 61 suites, 0 failures, 0 skipped** (was 981/60: +10 tests, +1
suite, no suite lost). `lintDebug` **0 errors / 353 issues**, count identical to
baseline. `compileDebugKotlin`, `assembleDebug` and `installDebug` all
`BUILD SUCCESSFUL`. Device pass over all three families on `FYJBONYTT8GENBTW`
with no crash and no `FATAL` line in `logcat`. No schema change,
`APP_DATABASE_VERSION` still 3, no new dependency, no Room migration.

Still open: `assembleRelease` / `bundleRelease` with R8 — not run in this phase,
and it is the only build that catches a minification problem. T10 is ticked for
compile / tests / lint / screenshots only.

F1 closed at `823ccaa`, off `18d5d29`. Branch
`fix/navigation-theming-and-confirmations`. 886 tests green (was 875, +11 new,
0 failures, no suite lost), lintDebug 0 errors / 353 issues unchanged,
`compileDebugKotlin` green. No schema change, `APP_DATABASE_VERSION` still 3, no
new dependency.

Re-verified independently, then extended with the structural fix above: **889
tests, 57 suites, 0 failures**; `lintDebug` 0 errors / 353 issues with an identical
category distribution. The writer's own last test run had left a mutation-run XML
on disk showing 3 failures, so the 886 figure was confirmed by a clean re-run
before anything was reported green.

**Lesson worth keeping:** a mutation test that leaves its mutated state in the
working tree poisons the next reader. The `test-results` XML on disk was the
failing run, not the passing one. Always re-run before quoting a total.

No device verification: F1 adds no visible feature, so a screenshot could only
show a module that looks exactly as it did before.

### T5 evidence-level audit

`evidence_es` is rendered prominently on the synergy card, and every level below
is stated there in visible text. "Level in `mechanism_es`" is the column that
matters for the finding: the level is *not* repeated inside the mechanism prose
for any of the seven.

| synergy | level declared in `evidence_es` | accurate? | asserts a human outcome with no human data? |
| --- | --- | --- | --- |
| `thc_myrcene` | pre-clinical, mechanistic; explicitly denies BBB and couch-lock human trials | yes | borderline — "produce sedación y relajación muscular" is unhedged in the mechanism prose while the level is animal/in vitro |
| `thc_limonene` | animal models + in vitro; denies clinical evidence for reduced paranoia | yes, and `mechanism_es` itself says "en modelos animales" | no |
| `thc_linalool` | in vitro + animal; denies human interaction data | yes | no — the combination claim is "se ha propuesto" |
| `thc_pinene` | in vitro + animal; denies human confirmation of an attention effect | yes | no — hedged as "podría sostener la atención" |
| `cbd_caryophyllene` | well characterised in animal models; **explicitly not proven in human trials** | yes — the reference standard | no. Pinned by `theCbdCaryophylleneReferenceCaseStillDeniesHumanProof` |
| `cbg_limonene_myrcene` | pre-clinical, rodents or in vitro; states this exact combination is unstudied | yes | no — "similar al antidepresivo" is attributed to animal models in the same sentence |
| `cbn_linalool_myrcene` | states popularisation without human data, unreplicated studies | yes | no — closes by saying the real effect in humans is not well established |

Nothing reads as a clinical claim and no therapeutic outcome is asserted. Two
prose observations, left for a content decision:

1. The level appears in `evidence_es`, not inside `mechanism_es`. If a reader
   sees only the mechanism paragraph, they get no level. `evidenceEs` is on the
   same card, so this is a layout question, not a correctness one.
2. `thc_myrcene`'s "En dosis altas, esa vía mediada por CB1 produce sedación y
   relajación muscular" is the only unhedged present-tense outcome claim among the
   seven, and its own evidence line says the data is pre-clinical and mechanistic.
   Not changed.
2. `thc_myrcene`'s "En dosis altas, esa vía mediada por CB1 produce sedación y
   relajación muscular" is the only unhedged present-tense outcome claim among the
   seven, and its own evidence line says the data is pre-clinical and mechanistic.
   Not changed.

## F1 finding the T5 audit was scoped past

The T5 audit read `mechanism_es` for overclaiming. The bigger defect lives in
`outcome_es`, which nobody audited, and it is not one bad sentence — it is the
whole headline layer.

Asked directly of each synergy: does the headline assert what the evidence line
denies? **Five of seven do.**

| synergy | headline on screen | its own evidence line |
| --- | --- | --- |
| `thc_pinene` | "Atenuación de la paranoia con atención sostenida" | AChE measured in vitro and in animals, no human confirmation |
| `cbd_caryophyllene` | "Analgesia y antiinflamación sistémica" | the additivity is **not** proven in human trials |
| `cbn_linalool_myrcene` | "Inducción de sueño y relajación muscular prolongada" | CBN has no human support as a sleep aid |
| `thc_linalool` | "Euforia atenuada con sedación suave" | in vitro and animals, no human interaction data |
| `cbg_limonene_myrcene` | "Claridad y bienestar sin sedación pesada" | evidence almost entirely preclinical |

The mechanism prose is mostly careful. The headline above it is not, and the
headline is what a user reads first.

**The cause is structural, not editorial.** `EntourageCards` rendered the headline
under the bare label `"Efecto"` — a noun that asserts the body below it *is* an
effect. Rewriting five headlines into disclaimers would make the card unreadable,
and the disclaimer is already at the top of every tab with the evidence line
directly beneath the headline. So the label carries the qualifier for the layer.

Two-part fix:

1. `thc_myrcene` was the worst case: it claimed "Sedación profunda y efecto
   couch-lock" while its own evidence line said couch-lock is not attributable to
   myrcene. A headline naming the exact outcome its evidence denies carries the
   denial inline now.
2. The layer label became `"Efecto descrito"`, qualifying every headline by its own
   label. Pinned by a test admitting only a qualified label.

Three tests added, all refusing to soften anything: the outcome label must stay
qualified; a headline denied by its evidence must carry the denial inline; and a
synergy that disclaims human proof must still render that disclaimer on the same
card. `cbd_caryophyllene` stays pinned and untouched.

**Not done, offered to the user:** hedging all five headlines inline. It is the
maximally conservative reading and it stacks five disclaimers in the layer that
already carries one. Recommended against; their call.

## Next step

T10 closes with the numbers below. T1's guarantee holds and is still enforced by
`everyBoilingPointAgreesWithBothCatalogs`: `TerpeneVolatility` reads the boiling
point from `terpenes.json` only and never re-derives it from
`entourage_data.json`. `APP_DATABASE_VERSION` is still 3, no migration, no new
dependency. F3 (agronomy) is the next phase; F2 alone does not close the module,
and `assembleRelease` has not been run — R8 fails only in release.

## What T6-T9 needed, given what already existed

Honest accounting: **T6, T7, T8 and most of T9 were already implemented and
committed in `cfaa49a`** together with 81 tests. This phase did not rebuild
them. What it added:

1. The last T9 defect, verified on device: the `DataRow` reading
   `Origen de la ventana` collided with `Medida en la tabla de Séquito`.
2. Ten tests — nine for that row's geometry, one pinning the derivation's
   premise against the asset.
3. A KDoc correction found while verifying T7 (below).
4. F1's cross-source guarantee re-verified, and the device pass across all three
   families.

### The band model

`derive` uses the compound's own measured boiling point and one constant:

```
floor   = 10 * floor(bp / 10)          // round DOWN
ceiling = 10 * ceil((bp + 30) / 10)     // round UP
headroom = 30 °C for every family
```

**The width is 30 °C or 40 °C, and only 30 when `bp` is already a multiple of
10.** Across the 158 shipped rows: 37 rows at 30 °C and 121 at 40 °C. So the
band is wider than the KDoc's `NARROWEST_DERIVED_WIDTH_C = 30` on four rows in
five — the honest statement is "30 or 40 °C", and the code says exactly that in
the docs while the constant names the floor.

**Why 30 °C survives the spread within a family** — measured from
`terpenes.json`, not quoted:

| family | compounds | boiling-point range | spread |
| --- | --- | --- | --- |
| Monoterpeno | 85 | 100–285 °C | 185 °C |
| Sesquiterpeno | 63 | 166–307 °C | 141 °C |
| Diterpeno | 10 | 300–350 °C | 50 °C |

A monoterpene label cannot distinguish a 100 °C ketone from a 285 °C aromatic,
so no per-degree window is defensible from it. The band is 30–40 °C against
spreads of 50–185 °C, i.e. **1/5 to 3/5 of the family's own spread** — coarse,
never finer than the data. For comparison, the ten shipped (measured) bands are
18–30 °C wide, so a derived band is never the tighter promise. Both facts are
asserted against the asset, not against a fixture.

**Correction made in `a32b316`:** the KDoc table said the monoterpene range was
131–285 °C (spread 154 °C). It is 100–285 °C (185 °C) — `umbellulone` at 100 °C
was missing, and the type KDoc's "it files `hexanal` (131 °C) and `vanillin`
(285 °C) under Monoterpeno" picked the wrong example. The error made the model
look *finer* than its own justification, so the fix strengthens the case. The
table is now pinned by `theWithinFamilySpreadIsWhatForbidsAFinerBand`, which
reads the asset.

### How a derived value reaches the user

Four things travel together and none of them is optional:

- the number carries the approximation mark: `≈ 350–380 °C`, not `350–380 °C`;
- the row below says where it came from: `Origen de la ventana` →
  **"Estimada por la app"** (or "Medida en la tabla de Séquito");
- an evidence sentence is rendered unconditionally, not behind a disclosure:
  *"Ventana estimada por la app: el punto de ebullición (350 °C) está medido y
  viene de la enciclopedia, pero la banda ≈ 350–380 °C se ha calculado a partir
  de él y de la familia Diterpeno. No es una ventana medida: sirve para comparar
  y ordenar, no para fijar una temperatura."*;
- a limits sentence: *"Una ventana de temperatura no dice cuánto rinde el
  compuesto, cuánto dura el aroma ni si tu equipo alcanza esa temperatura."*

`evidenceEs` and `limitsEs` are fields of `TerpeneVolatilityContent`, so a call
site cannot render the number without them, and
`theEvidenceAndLimitsLinesAreRenderedUnconditionally` reads the composable's
source to prove the render has no `if` around them.

**One thing the marker does NOT do:** in the curve, a derived rung and a measured
rung are drawn in the *same* tertiary colour, so provenance is carried by the
`≈` in the text and by the `derivedWarningEs` line ("1 de 5 ventanas de esta
curva son estimaciones de la app"). Colour alone does not distinguish them. Left
as is — it is legible and the text is authoritative — but it is the one place
where a glance is not enough.

### The layout fix: the shared row

`DataRow` shipped as `Arrangement.SpaceBetween` with two unweighted `Text`s. Row
measures each unweighted child with the *row's* width and then pushes them
apart; nothing bounded the pair, so a value wider than the leftover space
overflowed the row and landed on the label. Row width on this device is ~347 dp,
the label ~135 dp and the monospace value ~235 dp.

**Chosen: the shared row, not the provenance call site.** Six other rows on the
page have the identical shape — `Fórmula`, `Masa molar`, `Familia química`,
`Punto de ebullición`, `Riqueza en cannabis`, `Ventana de vaporización` — and
every one of them is fine only because its value happens to be short. A fix at
the call site would leave a row that breaks again the first time one of the
others grows a value.

Row measures unweighted children *before* weighted ones, so the label now keeps
its natural width and the value takes `DataRowLayout.VALUE_WEIGHT` — everything
that is left, minus a 12 dp gap — right-aligned inside it. Short values still
land flush against the right edge, which is why the five rows that looked fine
still look fine (verified on device). A long value wraps inside its own column
and cannot touch the label.

The two numbers are in `DataRowLayout` (`model/`) rather than in the composable,
following the lesson the zero-weight crash taught: Compose has no unit-test
runtime here, so a number only a composable can reach is a number nobody can
catch before it ships.

### Aggregate coherence (F1's guarantee, re-verified)

One function, two callers:

- `VolatilityCurves.aggregate` — `TerpeneVolatility.kt:757`
- `VolatilityCurve` reads it at `TerpeneVolatility.kt:662`
- `EntouragePlanner.windowFor` calls it at `Entourage.kt:628` and reads
  `floorC`, `ceilingC` and `isViable` off the result (`Entourage.kt:630-635`)

There is no second implementation of "the band that covers everything" to drift
from the first. Held by
`theCurveAgreesWithTheModulesAggregateWindowOnTheSameSelection` and by
`EntouragePlannerTest`. No refactor in this phase touched either side.

## Verification (F2, observed)

| command | observed |
| --- | --- |
| `:app:compileDebugKotlin --no-daemon` | BUILD SUCCESSFUL in 2m 46s |
| `:app:cleanTestDebugUnitTest :app:testDebugUnitTest --no-daemon` | BUILD SUCCESSFUL in 2m 13s |
| `:app:assembleDebug :app:installDebug --no-daemon` | BUILD SUCCESSFUL in 2m 45s, `Installed on 1 device` |
| `:app:lintDebug --no-daemon` | BUILD SUCCESSFUL in 6m 16s |

**Test total: 991 tests, 61 suites, 0 failures, 0 skipped**, read from
`app/build/test-results/testDebugUnitTest/TEST-*.xml`. Baseline was 981 / 60;
+10 tests, +1 suite, no suite lost. `TerpeneVolatilityTest` 57,
`TerpeneVolatilityAssetTest` 25, `TerpeneDetailVolatilityTest` 10,
`DataRowLayoutTest` 9, `EntouragePlannerTest` 33.

**Lint: 353 issues, 0 errors** (333 warnings, 20 information) — identical to the
baseline count.

One test failed on the first run and was fixed before anything was reported
green: `theValueIsTheWeightedChildAndTheLabelIsNot` scanned from `DataRow` to
end-of-file and picked up `VolatilityBarTrack`'s own `weight(1f)`. The scan is
now bounded by the next composable. The 981 figure was re-confirmed by a clean
re-run, per the lesson already recorded in this file.

### Device verification — `FYJBONYTT8GENBTW`, 03:51–04:03

| compound | family | point | band on screen | provenance | marker visible |
| --- | --- | --- | --- | --- | --- |
| Mirceno | Monoterpeno | 167 °C | `167–195 °C` | **MEASURED** | none, correctly |
| Pineno (Mirceno's curve) | Monoterpeno | 155 °C | `≈ 150–190 °C` | DERIVED | yes, `≈` |
| Cariofileno | Sesquiterpeno | 262 °C | `≈ 260–300 °C` | DERIVED | yes |
| Cariofileno beta | Sesquiterpeno | 262 °C | `250–280 °C` | **MEASURED** | none, correctly |
| Fitol | Diterpeno | 350 °C | `≈ 350–380 °C` | DERIVED | yes |

The 262 °C pair is the useful one: two compounds at the same boiling point, one
measured and one derived, and the page says which is which in two places. The
diterpene curve carries four rungs, all `≈ 310–350` to `≈ 350–380 °C`, and the
monoterpene curve carries one derived rung out of five with
"1 de 5 ventanas de esta curva son estimaciones de la app".

No crash and no `FATAL`/`AndroidRuntime` line from the app in `logcat` across the
whole session. The two rows that collided now render as
`Origen de la ventana` + a right-aligned value wrapped onto two lines.

**Not verified:** the two competing third-party apps never stole the foreground
during this session, so the documented spaced-retry procedure was not needed.
**Side effect to declare:** opening a terpene page is what registers its
discovery, so browsing Fitol added it to the device's discovered set (+20 XP
shown on the page). A tap intended for the family filter landed on the quiz
button and opened "Trivia · ronda 1/5"; it was dismissed with BACK without
answering, and XP stayed at 425.

### What the new tests cover, and what they do not

`DataRowLayoutTest` (9) — the numbers (`GAP_DP > 0`, `VALUE_WEIGHT > 0`, the
remaining-width arithmetic never negative) and the shape of the composable
(no `SpaceBetween`, the value is the weighted child, the gap comes from the
model, the value stays `TextAlign.End`, all seven call sites go through the
shared row and none has a bespoke composable).

What it does **not** cover: measured pixel widths. There is no Compose test
runtime here, so "the label and the value no longer collide" is proven by the
structural assertions plus a device screenshot, not by a JVM test. A source scan
also cannot catch a future composable that reaches for a different width
mechanism entirely.

`theWithinFamilySpreadIsWhatForbidsAFinerBand` — reads `terpenes.json`, pins
100/285, 166/307, 300/350 and the 85/63/10 counts, names `umbellulone`, and
asserts no family's spread is narrower than the narrowest derived band.

What it does **not** cover: whether 30 °C is the *right* headroom. No published
per-compound extraction window for the 148 exists in this repository, so the
constant is justified only against the ten measured rows. The test can prove
the band is coarser than the data allows to be precise about; it cannot prove it
is accurate.
---

# F3 — the agronomic / biological dimension

F3 was not in this file's task list (it was scoped to F1–F2) and is recorded here
now. Objective: widen the module along the axis a grower acts on — **what makes
the plant make more of a compound, and when to cut** — on top of F2's chemistry of
the material.

F1 and F2 both describe the compound. F3 describes the plant.

## What is different about F3's evidence

The pharmacology in this module is almost entirely pre-clinical, so F1 pinned
every claim to its level in visible text and hedged aggressively. **Agronomy does
not need that.** UV-B inducing secondary metabolism through UVR8 → COP1 → HY5,
controlled water deficit as a pre-harvest technique, and harvest timing driving
the monoterpene/sesquiterpene shift are textbook plant physiology and cultivation
practice. F3 therefore states them directly, and only hedges where the evidence
is genuinely mixed for the compound in question — which is recorded per lever,
not globally.

The rules from F1 still hold and are extended, not relaxed:

- the evidence line is visible, never behind a disclosure;
- a claim whose basis disclaims what it cannot establish carries its qualifier in
  the same rendered block;
- `EntourageAssetTest`'s banned phrases are **not** loosened, and neither is
  `EntourageLanguage`.

## Decision D-B — keyed by terpene, not by synergy pair

The source spec asked for agronomic tips inside the synergy card, keyed on the
cannabinoid x terpene pair. **Declined.** Two failure modes:

- **Duplication.** `LIMONENE` appears in `thc_limonene` and in
  `cbg_limonene_myrcene`. A pair-keyed entry writes the same harvest advice twice,
  and the second copy is a second place for it to go stale.
- **Orphaning.** A pair-keyed entry exists only if a synergy exists. `CAMPHENE`
  and `TERPINOLENE` are modelled by `EntourageTerpene` and ship a vaporisation
  row, but **no synergy contains them** — so a pair-keyed entry for those two
  could never be reached at all.

Keyed by `EntourageTerpene`, the advice is written once and is reachable from two
surfaces: the synergy card (for every terpene of the combination) and the
compound's own encyclopedia page (which is where a grower looks). The KDoc on
`EntourageAgronomy` says this, and `theBlockIsReachableFromTheShippedCombinations`
asserts the first half against the shipped asset.

## T-A — the `agronomy` block

`app/src/main/assets/data/entourage_data.json` gains an `agronomy` array of eight
entries, thirteen levers. Resolved in `EntourageBible.toContent()` with five
independent drop-and-record paths, all of them landing in the existing
`unresolvedReferences`:

| dropped when | recorded as |
| --- | --- |
| unknown terpene key | `agronomy.<KEY> -> unknown terpene` |
| unknown lever kind or evidence level | `agronomy.<TERPENE>.levers -> <KEY>` / `agronomy.<TERPENE>.<KIND> -> <LEVEL>` |
| **`basis_es` blank** | `agronomy.<TERPENE>.<KIND> -> the lever ships no basis_es and was dropped rather than shown unqualified` |
| `detail_es` blank | `agronomy.<TERPENE>.<KIND> -> no detail declared` |
| `response_es` blank | `agronomy.<TERPENE> -> no response declared` |

The blank-`basis_es` path is the one the honesty standard turns on: a lever whose
evidence level is missing is exactly what this module exists to not ship, so it is
dropped rather than shown unqualified.

### Which compounds got an entry, and which did not

| compound | levers | why |
| --- | --- | --- |
| Mirceno | HARVEST_POINT, WATER_DEFICIT, UV_B | The only one with all three. Myrcene is a documented drought-responsive monoterpene in essential-oil crops, reported in cannabis without a dose-response. |
| Limoneno | HARVEST_POINT | The canonical volatile-loss compound: oxidises to limonene oxide and carveol. Well characterised. |
| Linalool | HARVEST_POINT | Associated with late flowering in Cannabis characterisation studies — a profile trend, not a controlled comparison, hence MIXTO. |
| Pineno alfa | HARVEST_POINT | Early-peaking; oxidises to pinene oxide on storage. Well characterised. |
| Pineno beta | HARVEST_POINT | Tracks alpha; the basis says outright that routine screening does not separate the isomers. |
| Ocimeno | HARVEST_POINT | Very volatile, low absolute content; marked MIXTO because the cannabis-specific data is thin. |
| Cariofileno beta | UV_B, HARVEST_POINT, WATER_DEFICIT | The best-characterised sesquiterpene: FPP route, documented UV-B induction, later harvest window. |
| Humuleno | UV_B, HARVEST_POINT | Same precursor and same window as beta-caryophyllene; the basis says no measurement separates them. |
| **Camfeno** | **none** | No documented agronomic lever of its own. Ships a vaporisation row and is modelled, but nothing in the plant's response to light, water or harvest point is specific to it. |
| **Terpinoleno** | **none** | Same. |

The two gaps are pinned by `theShippedBlockNamesTheTwoCompoundsItLeavesOut`, which
fails if somebody adds an entry for either without arguing it. Both still get the
block, with `TerpeneAgronomyCopy.NOT_DOCUMENTED_ES` naming the compound, plus the
biosynthetic route — which is a fact about the compound's size and therefore
survives an empty agronomy set.

The reason is a rule, and it is deliberately stricter than "cover everything":
**an entry earns its place when a lever's direction can be stated for that
compound.** The generic behaviour of a chemical family is delivered once, in
`GrowOutGuides`, not repeated eight times as a per-compound claim the literature
does not make.

## T-B — the biosynthetic routes, as one explainer

`BiosynthesisExplainer` in `model/TerpeneAgronomy.kt`: MEP/DOXP (plastid, GPP, C10)
and MVA (cytosol, FPP, C15), seven numbered steps, the capitate-stalked glandular
trichome as the site, and the C10/C15 size rule.

It is one object, not 158 rows, because the chemistry is identical for every
compound in the encyclopedia and only the terpene synthase differs.
`theBiosyntheticStepListIsIdenticalForEveryCompound` and
`whatChangesBetweenCompoundsIsTheRouteSentenceAndNothingElse` assert exactly that:
what varies per compound is the route sentence and nothing else.

`CAVEAT_ES` states what the two-route picture does **not** settle: the plastid also
exports part of the IPP/DMAPP pool the cytosolic route consumes, so the C10/C15
partition is not a clean border. It is rendered unconditionally.

## T-C — the three grow-out levers

`AgronomyLeverKind`: `UV_B`, `WATER_DEFICIT`, `HARVEST_POINT`. The **per-compound**
`AgronomyLever.detailEs` says what this compound does; the **shared**
`AgronomyGuide.whatEs` says how the lever works on the plant. Neither restates the
other, which is what keeps a lever from becoming eight copies of one paragraph.

| lever | level shipped | what the basis says |
| --- | --- | --- |
| UV-B | BIEN_DOCUMENTADO at pathway level | UVR8 → COP1 → HY5 → terpene synthase is well characterised and sesquiterpene induction by UV-B is documented across species. Dose, and the effect on one terpene in one cultivar, are not fixed. UV-B also costs biomass above a certain intensity. |
| Water deficit | MIXTO | Documented in essential-oil crops and reported in cannabis; magnitude and window vary by cultivar, substrate and stage. No published dose-response for a specific terpene in a specific cultivar. Severe/prolonged deficit is not the same technique. |
| Harvest point | MIXTO | The mono/sesqui shift with maturity is described in the Cannabis literature, but the direction is not constant across cultivars. Drying and storage change the measured profile on their own, so a post-hoc assay does not separate maturation from process. Trichomes are a moment indicator, not a composition target. |

### The mono/sesqui shift, stated in both directions

The shift is only honest if both halves are present, and
`theMonoterpeneAndSesquiterpeneEntriesDisagreeAboutTheHarvestPoint` fails if the
block ever describes one family only:

- volatile monoterpenes (limonene, myrcene, pinenos, ocimeno) **fall** with
  maturation and keep falling through drying and storage — BIEN_DOCUMENTADO for
  each of them individually;
- the sesquiterpenes (cariofileno beta, humuleno) **gain relative share**, because
  the volatiles leave first — MIXTO, because the direction is not constant.

## T-D — the two surfaces

1. **Synergy card.** `EntourageCardRole.AGRONOMY`, added to `linesEs` immediately
   after `EVIDENCE` and before the optional lines, because it is the same kind of
   statement. One line per documented compound of the combination, with every
   lever's basis folded into the body as `Base (Evidencia mixta): …` — folded rather
   than split into six paragraphs per combination, and folded *inside* the body so
   the card's single `linesEs.forEach` cannot skip it.
2. **Terpene detail page.** `AgronomyCard`, mounted as a `LazyColumn` item
   immediately **after** `VolatilityCard`, gated on "is this a compound the Séquito
   module models" (the same gate as the page's existing Séquito button), *not* on
   "does it have an entry". Per-compound levers with their basis, the route line,
   the biosynthesis explainer, the three shared guides, and a closing line that a
   lever is not an instruction.

A compound with no documented lever produces **no card line** on the synergy card
and **the gap sentence** on its own page. That asymmetry is deliberate and is
documented on `TerpeneAgronomyCopy.cardLinesFor`: three "no data" lines next to one
line of content is worse than silence, and the compound's page is where the gap is
named in full.

## T-E — the tests

`TerpeneAgronomyTest` (25, `model/`) — the honest-gap sentence names the compound;
a lever with a blank basis is not drawable; every lever carries the level the user
sees; every basis travels inside the card body verbatim; one card line per
documented compound; an undocumented compound adds no line; a card built without
the index is the F2 card unchanged; the agronomy line sits next to the evidence
line and not under the optional ones; the step list is identical across families
and only the route sentence differs; the route is chosen by carbon count; every
explainer carries its caveat; one guide per lever kind, no duplicates; the
authored copy passes `EntourageLanguage`.

`TerpeneAgronomyAssetTest` (24, reads the real asset) — parses with no BOM; no row
dropped; every key resolves; one entry per compound; each of the five
drop-and-record paths verified by mutating the bible; the F1 banned phrases and
`EntourageLanguage` over the shipped agronomy text; every lever's level and basis
present in the card body; a disclaimer-carrying basis carries its qualifier with it
(verified against a fixture the test asserts is non-empty); all three lever kinds
represented; the two harvest-point directions disagree; the two gaps are named; the
gap is stated on both paths that can show it; the rest of the parse is untouched
(10 bands, 4 profiles, 7 synergies, 10 questions, 0 unresolved).

`TerpeneDetailAgronomyTest` (17, structural) — the block sits beside the volatility
card and not instead of it; the gate is the compound and not the entry; every
basis, every level and the biosynthesis caveat render unconditionally; no
disclosure affordance on either surface; the card panel has an `AGRONOMY` branch
and still iterates every line; both call sites pass the index; no second scroll
owner; no hardcoded colour; **no `Surface` at all** in the block; and the block
holds **no non-blank string literal**, which is the strongest form of "every
sentence lives in `model/`".

`ModelPurityTest` now includes `TerpeneAgronomy.kt`.

## F3 verification (observed)

| command | observed |
| --- | --- |
| `:app:compileDebugKotlin --no-daemon` | BUILD SUCCESSFUL |
| `:app:cleanTestDebugUnitTest :app:testDebugUnitTest --no-daemon` | BUILD SUCCESSFUL |
| `:app:assembleDebug :app:installDebug --no-daemon` | BUILD SUCCESSFUL, `Installed on 1 device` |
| `:app:lintDebug --no-daemon` | BUILD SUCCESSFUL |

**1057 tests, 64 suites, 0 failures, 0 skipped.** Baseline was 991 / 61: +66 tests,
+3 suites, no suite lost. **Lint 353 issues, 0 errors** (333 warnings, 20
information) — identical to baseline. No schema change,
`APP_DATABASE_VERSION` still 3, no new dependency, `versionCode` 9 /
`versionName` 1.7.0 untouched.

### F3 device pass

Mirceno's page: the agronomy card renders directly under "🌡️ Vaporización",
carrying the response sentence, three levers each with its `Base: …` line, the
route line, the full biosynthesis explainer and the three shared guides. Camfeno's
page: the same card with the gap sentence in place of the levers, and the route and
explainer still below it. The `thc_myrcene` synergy card: the "🌱 Mirceno" line
sits directly under the evidence block, with every basis folded into the body.

No `FATAL`, no `E AndroidRuntime`, no exception line mentioning the app across the
session. The two competing third-party apps did not steal the foreground.

**Side effect, declared:** opening the Camfeno page registered its discovery, as
the app is designed to do — the header went from 18 to 19 discovered and 445 to 465
XP. Mirceno was already discovered, so browsing it changed nothing.

**Not verified:** `assembleRelease` / `bundleRelease` with R8. Not run in this
phase, and it is still the only build that catches a minification problem.
`LunarEngine`, `AmbientClimate`, `TerpeneBlender`, `EntouragePlanner` and
`VolatilityCurves.aggregate` were not touched.

## Two wording constraints worth keeping

The banned-word guard contains `cura` as a **substring**, and `EntourageLanguage`
contains `daño` and `dano`. `curado`/`curación` and `daño` are therefore both
unavailable in this module's copy. The agronomy text uses `secado`,
`almacenamiento`, `lesiones` and `degradación` instead. That is a real constraint
on the vocabulary, not a preference, and no guard was loosened to accommodate a
nicer word.

# F4 — the engineering / processing dimension

F4 was not in this file's task list either (it was scoped to F1–F2) and is recorded
here now, after F3. Objective: the dimension a user acts on **after** the harvest —
which extraction route, what the heat does, and what the profile does between the
cut and the pen.

F1 and F2 describe the compound, F3 the plant. F4 describes **what you do to the
material you have already grown**, and that is the only dimension of the module
where a wrong sentence is not a wrong opinion but ruined material. So the honesty
rules below are tighter than F1's, not looser.

## What is different about F4's evidence

F1 pinned every pharmacology claim to its level because the whole body is
pre-clinical. F3 stated plant physiology directly because it is textbook. F4 sits
between: the **directions** are well documented chemistry, and the **magnitudes**
are not — for the good reason that they depend on the starting material, the
equipment and the process, none of which this app can see.

That split is expressed in the copy itself. A direction ships with a basis saying
"no hay una comparación controlada entre métodos y entre disolventes concretos", so
the reader gets the direction and the reason it stops there. What F4 refused to
ship is the thing a direction invites: a number.

## The three refusals

| refused | why | how it is pinned |
| --- | --- | --- |
| a temperature | a schedule is a setpoint and a wrong one is irreversible | `noProcessingNumberReadsAsAnInstruction`, `theAuthoredCopyStatesNoTemperatureNoDurationAndNoQuantity`, `theBlockHoldsNoTemperatureOfItsOwn` |
| a duration | same, and F2 already established that a temperature window says nothing about how long a compound survives | same two, plus `noCompoundInTheBlockStatesAShelfLife` |
| a yield or quantity, even as a range | the only honest statement available is "a solvent extraction pulls more total material", which is a **direction** and is stated as one | the same tests, over `°C`, `%`, `g/mg/ml/kg` and `\d+\s*(min\|horas\|días\|semanas)` |

The refusal is **stated on screen**, not merely observed by absence. The
decarboxylation basis says in Spanish, where a number would have been, that there
is none and that a number would be a recommendation rather than a datum —
`theDecarboxylationBasisNamesTheRefusalOutLoud`.

## Solvent safety is part of the block, not a footnote

This is the one design decision that is not a copy decision.

`ProcessingMethodGuide.safetyEs` is a **required constructor argument with no
default**, and the `init` block refuses a blank one. There is therefore no code
path — asset, copy, or a fourth method added later — that produces a named
extraction method without its safety framing. That is what separates this from a
disclaimer: a disclaimer can be scrolled past and a developer can weaken it in one
edit; a constructor argument that will not compile without it cannot be dropped
from the card, the page or the asset.

It is also why the safety line is **not asset content**. There is no `safety_es`
key in `entourage_data.json` at all. A missing key costs a note; it can never cost
the residue sentence.

Where it lands:

- **Detail page.** The shared comparison names all three methods, so the
  residue sentence is rendered immediately after it in the same `Column`, and each
  method's own guide repeats its `safetyEs` next to its mechanism.
- **Synergy card.** The card line *is* the comparison, so
  `TerpeneProcessingCopy.cardLineEs` folds `Residuo y exposición: …` into the same
  body. `theCardLineCarriesTheSolventSafetyBecauseItNamesTheMethods` and
  `theComparisonNamesTheMethodsAndCarriesTheSolventSafety` assert both halves.
- **The undocumented path.** A compound with no entry still renders the comparison
  — and therefore still needs the residue sentence, or the honest "no entry" page
  would be the one page naming a solvent route with no framing.
  `anUndocumentedCompoundStillGetsTheComparisonAndTheSolventSafety`.

What the solvent line says, and why each part is there: **"grado alimentario" on
the bottle describes the bottle, not the product made with it**; as the solvent
evaporates most of the volume leaves and what does not evaporate becomes
progressively more concentrated; **the applicable limit is set by the finished
product and its route of use, not by the bottle**; and the only reliable answer is
analysing the finished product. Pinned by `theFoodGradeLabelIsNotPresentedAsASafetyClaim`.

`usesSolvent` is a property of the enum, read by the copy and not by a composable,
and `onlyTheSolventBasedMethodClaimsASolventAndItIsTheOneThatMentionsResidue`
checks both directions: the solvent route mentions the residue, and each
solvent-free route says **in its own words** that it has no solvent — so "no
safety line" and "no solvent, so no residue question" cannot be confused.

### The wording constraint that shaped it

`EntourageLanguage` bans `peligro`, `insegur`, `tóxico`, `peor`, `daño` and
`malo` **as substrings**, and `EntourageAssetTest` bans `cura` (so `curado` is
also unavailable) and `es seguro`. Writing solvent safety without any of those is
a real constraint, not a stylistic one, and the copy uses `residuo`,
`concentración`, `análisis`, `oxidación`, `almacenamiento` and `degradación`
instead. **No guard was loosened to accommodate a nicer word.**

## T-F1 — the `processing` block

`app/src/main/assets/data/entourage_data.json` gains a `processing` array of ten
entries: one summary claim per compound plus three method notes and four
preservation-factor notes each, seventy notes in total.

Keyed by terpene, for the same two reasons decision D-B gave: `LIMONENE` appears
in two shipped synergies, so a pair-keyed note writes the same paragraph twice
with two chances to drift; and `CAMPHENE` and `TERPINOLENE` appear in no synergy
at all, so a pair-keyed note for those two could never be reached.

Resolved in `EntourageBible.toContent()` with **seven** independent
drop-and-record paths, all landing in the existing `unresolvedReferences`:

| dropped when | recorded as |
| --- | --- |
| unknown terpene key | `processing.<KEY> -> unknown terpene` |
| unknown method key | `processing.<TERPENE>.methods -> <KEY>` |
| unknown preservation-factor key | `processing.<TERPENE>.preservation -> <KEY>` |
| unknown evidence level, at the entry or at a note | `processing.<TERPENE> -> <LEVEL>` / `processing.<TERPENE>.<METHOD\|FACTOR> -> <LEVEL>` |
| **blank entry `basis_es`** | `processing.<TERPENE> -> the row ships no entry basis_es and was dropped rather than shown unqualified` |
| blank note `basis_es` | `processing.<TERPENE>.<METHOD\|FACTOR> -> the note ships no basis_es and was dropped rather than shown unqualified` |
| blank note `detail_es` | `processing.<TERPENE>.<METHOD\|FACTOR> -> no detail declared` |

The **entry-level** basis path is F3's lever rule lifted to the row, and it is the
one that matters most here: a row whose summary claim has nowhere to state what
it cannot establish would still put that claim on screen, so the row goes rather
than degrading.

The unknown-method path has the most bite in this phase. Coercing it to a default
would be the one coercion that could render a **solvent-based route as if it
carried no solvent** —
`anUnknownMethodKeyLandsInUnresolvedReferencesRatherThanBecomingASolventFreeMethod`.

## T-F2 — the methods, as a comparison

`ProcessingGuides.COMPARISON_ES` names all three and states the trade-off in
three sentences. What each note says, and on what evidence:

| method | direction claimed | level | what the basis says it cannot establish |
| --- | --- | --- | --- |
| Resina en vivo (prensado en caliente, sin disolvente) | the only route with **no evaporation step after it**, so the volatile fraction is lost in smaller measure and the aroma stays closest to a freshly cut plant | BIEN_DOCUMENTADO | that drying and storage carry away the volatile fraction is documented, and this route has neither step; a **controlled comparison** with the same starting material and the same measured profile does not exist |
| Extracción con disolvente | pulls **more total material**, and buys a purge-and-dry step in which the volatile fraction goes in good part | BIEN_DOCUMENTADO | both directions are described in the processing literature; there is no controlled comparison between methods or between specific solvents, so the magnitude is open |
| Descarboxilación | **not a third extraction route**: a reaction on the acids, and its effect on terpenes is the side effect of the heat it needs | BIEN_DOCUMENTADO | that heating drives off terpenes and that the acids decarboxylate are both documented; **how long** each takes, and which degradation products form, depend on material and process — so no temperature and no time |

`theVolatileAndTheHeavyCompoundsDisagreeAboutWhatEachMethodDoes` fails if the
block ever describes one volatility class only: limonene, alpha-pinene and ocimene
have to say they leave during the separation step, and beta-caryophyllene — the
least volatile compound in the module — has to say it is among the last to go.
`theDecarboxylationNoteStatesWhatIsLostAndWhatForms` requires **both** halves on
every row, because a note that only said "part of it goes" would not tell a reader
what they end up holding.

`ProcessingGuides.factorFor(HEAT)` reuses F2's framing rather than inventing a
shelf life: the temperature window higher up the page describes where the compound
is useful **in a vaporiser**, which is a use and not a storage, and a window does
not say how long a compound survives in a closed jar.
`theHeatFactorTellsTheReaderThatAVolatilityWindowIsNotAShelfLife` requires that
distinction in the shared guide **and** in every compound's HEAT note, because a
reader who has just scrolled past a band is the one most likely to misread it.

## T-F3 — organoleptic preservation

Four factors, not one: `LIGHT`, `OXIDATION`, `HEAT`, `TIME`. Merged into a single
"store it well" they would teach nothing; kept apart, three of them accelerate the
same oxidation chemistry and the fourth decides whether the other three have had
their effect.

| factor | direction claimed | level |
| --- | --- | --- |
| Luz | the more reactive volatiles react with oxygen **more under light than in the dark**, and the result is a different aroma, not merely a weaker one | BIEN_DOCUMENTADO |
| Oxígeno | the profile shifts towards products that are no longer the compounds the plant built, and the shift accumulates | BIEN_DOCUMENTADO |
| Calor | heat drives off the volatile fraction and accelerates the oxidation of what stays | BIEN_DOCUMENTADO |
| Tiempo | the axis none of the other three replaces: oxidation advances while the material is closed and in air, and a long-stored sample cannot be told from a short-stored one by a later assay | BIEN_DOCUMENTADO |

Per-compound, the entries name the specific transformation where the module already
has it — limonene to limonene oxide and carveol, pinene to pinene oxide, caryophyllene
to its oxide, myrcene's double bond — and **name the reaction rather than a product**
where it does not. Linalool, ocimene, terpinolene and camphene say "oxidation, and
this app cannot say which product predominates" rather than inventing one. That is
the rule the brief set: if what a method does to a specific compound cannot be
established, say that instead of writing a plausible sentence.

## Which compounds got an entry, and which did not

**All ten, and that is a deliberate difference from F3, not an oversight.**

F3's rule was that an entry earns its place when a **lever's** direction can be
stated for the compound — a lever being a response of the *plant* — and Camphene
and Terpinolene have none documented. F4's question is different: what happens to
the **compound** under a named method, which is chemistry of the molecule and
which every one of the ten has. So `agronomy` ships 8 entries and `processing`
ships 10, with Camphene and Terpinolene present in one and absent from the other.

That asymmetry is pinned on both sides —
`theProcessingBlockCoversEveryCompoundF3LeftOut` fails if either entry moves — and
the two entries that look odd have to explain themselves in their own basis: both
state that a palanca agronómica is a response of the plant while a note of
processed material is a property of the compound, so a reader who sees a gap in one
block and content in the other is not looking at a contradiction.

Every compound documents all three methods and all four factors. That is stricter
than F3 was with its levers, and the reason is specific rather than tidy: the
comparison **names all three methods**, so a compound that shipped no solvent note
would still name the method on its own page through the shared block.
`theSolventRouteIsDocumentedForEveryCompoundSoNoPageNamesItWithoutTheResidueLine`.

Evidence is not uniform inside the ten. `LIMONENE`, `ALPHA_PINENE`, `BETA_PINENE`,
`LINALOOL`, `HUMULENE` and `BETA_CARYOPHYLLENE` are entered BIEN_DOCUMENTADO on
their entry-level claim. `OCIMENE`, `TERPINOLENE` and `CAMPHENE` are entered
**MIXTO**, because their absolute content in cannabis is low and its measurement
is not routine, so the direction of their response is documented and the magnitude
is not. That is F3's `MIXTO` reasoning applied to content rather than to the plant.

## T-F4 — the two surfaces

1. **Terpene detail page.** `ProcessingCard`, a **third** card, mounted
   immediately after `AgronomyCard` and gated on the same "is this a compound the
   Séquito module models" test — not on "does it have an entry", or the honest
   no-entry sentence would be unreachable.
   `TerpeneDetailProcessingTest.theProcessingBlockIsOnThePageAsItsOwnCardBesideTheOtherTwo`
   asserts the three cards are all present **and in order**;
   `theProcessingCardDoesNotFoldTheVolatilityOrAgronomyBlocksIntoItself` asserts
   the card reads neither of the other two, because heat in a device and heat in a
   jar are different subjects and only one of them carries a number.
   The card holds **no non-blank string literal at all**, so every sentence is in
   `model/` where a JVM test can reach it.
2. **Synergy card.** `EntourageCardRole.PROCESSING`, added to `linesEs`
   immediately after the agronomy lines and before the optional ones. The card line
   carries the compound's claim with its level and basis **folded into the body**,
   then the comparison, then the residue sentence — because a card that names a
   method owes the reader that sentence in the same block.

The card line deliberately does **not** carry the seven per-compound notes: a line
with seven more paragraphs per combination is a line nobody finishes reading, and
the comparison — which is what a combination has to say about processing — is
method-independent anyway. Those notes are on the compound's own page, which is
where a grower looks.

## T-F5 — the third badge, and the bug the device pass found

`EntourageAchievement.RESIN_ENGINEER`, "Ingeniero de Resina", written into the
**existing** `achievements` table through the existing
`EntourageReward.toAchievementRow` projector and deduplicated by name through the
existing `EntourageRewards.pending`. No second progression system, no column, no
migration.

Its condition is "every compound the shipped `processing` block documents has been
opened", resolved from the `discovered` set that already pays the discovery XP —
there is no second "has this been read" ledger, and there is deliberately still
none, because a second record of the same fact is a second source of truth for it.

The text is **derived, not literal**: `EntourageAchievement.descriptionForProcessing(n)`
builds "Lee el procesado de los n compuestos que el catálogo documenta" from the
parsed block, for exactly the reason F1 derived the quiz badge's threshold — a
literal would drift the first time a compound is added to or removed from
`entourage_data.json`, and it would drift in the one place the player cannot check
it, because the row is written once and then lives in the table forever.

Two naming decisions there. It is **not** `descriptionFor`, because two functions
of the same name and one parameter type would be a silent hazard: a caller holding
the wrong badge would still compile and would put a quiz sentence into the resin
row. And `EntourageAchievement.toAchievementRow(rounds)` **errors** for
`RESIN_ENGINEER` rather than writing the quiz text —
`theBadgeProjectionRefusesTheResinRowRatherThanWritingAQuizSentence`.

### The duplicate rows, and what caused them

The device pass paid the badge **four times**, and the first three causes were
mine. Worth recording because none of them was visible in a unit test.

1. `award the badge` claimed `awardedBadgeNames` **after** awaiting the insert, so
   a second progress emission in the gap paid again.
2. Moving the claim before the insert fixed that and did not fix it: `appViewModel`
   scopes to the navigation entry, so **two live `TerpenesViewModel`s** each hold a
   snapshot taken before the other wrote.
3. The fix that holds re-reads the **table** inside the same coroutine that writes
   it, under a companion `Mutex`, and claims the name inside the lock. The cheap
   pure gate still runs first, so the common case costs no database read.

Verified on the device after the fix: a session with two page opens, two progress
emissions and two ViewModel instances added **zero** rows. The four rows already
written are residue from the three buggy builds and **could not be removed** —
deleting them would mean touching `/data/data/com.trichome.app.debug/databases/`,
which this phase is forbidden to do. They are user data written by a bug in a build
that was never released, and the honest resolution is to leave them and say so.

The duplicate detection a JVM test can do is
`theCallerHasToClaimTheNameTheMomentPendingReturnsIt`, which pins the contract
over the pure functions — it cannot reach `viewModelScope`, and the test says so.

## T-E — the tests

**1139 tests, 68 suites, 0 failures.** Baseline was 1057 / 64: +82 tests, +4
suites, no suite lost.

| suite | n | covers |
| --- | --- | --- |
| `TerpeneProcessingTest` (model) | 24 | the safety constructor refuses a blank line; every named method carries one; `usesSolvent` is correct in both directions; "food grade" is refused as a safety claim; the comparison names all three and carries the residue sentence; the undocumented path still gets both; **no temperature, duration or quantity** in the authored copy; every basis says what it cannot establish; the decarboxylation basis says the refusal out loud; F4 reuses F3's enum and invents no third level; the index is a view over the list; an empty index is a first-class answer; an undocumented compound adds no card line; the card line carries level, basis and residue sentence in the body; a card built without the index is the F3 card unchanged; the processing line sits after agronomy and before the optional lines; the authored copy passes `EntourageLanguage` and the F1 banned list |
| `TerpeneProcessingAssetTest` (reads the file) | 29 | no BOM; no row dropped; every terpene / method / factor / evidence key resolves; one entry per compound; **all seven drop-and-record paths** verified by mutating the bible; the F1 banned phrases and `EntourageLanguage` over the shipped text; **no instruction number** anywhere in the block; every basis states its limit; all three methods and all four factors documented for every compound; the volatile and heavy compounds disagree about the solvent route; decarboxylation states what is lost and what forms; the HEAT note makes the vaporiser-versus-storage distinction; no shelf life is claimed and every TIME note says none is published; every level and basis reaches the card body verbatim; the block is reachable from the shipped combinations; **the processing block covers every compound F3 left out**; the index and the list are one source of truth; the rest of the parse is untouched (10 bands, 4 profiles, 7 synergies, 8 agronomy entries, 10 questions, 0 unresolved) |
| `TerpeneDetailProcessingTest` (structural) | 17 | the three cards are present and in order; the gate is the compound; the card folds neither of the other two in; the comparison and the residue sentence render unconditionally; every method's safety line renders in its block; every shared guide renders including the ones this compound has no note for; no disclosure on either surface; the synergy card has its own `PROCESSING` branch and still iterates every line; both network call sites carry the index; one scroll owner; no hardcoded colour; **no `Surface` at all**; no glassmorphism; **no non-blank string literal in the composable**; every label comes from the content model; the card holds no temperature and never reaches for the volatility window |
| `TerpeneProcessingBadgeTest` | 12 | the badge text follows the shipped block; it changes with the data; the paid description names the shipped count; a partial read pays nothing; an empty block pays nothing; paid exactly once; the caller's claim contract; the reward projects through the existing row builder; the enum is the module's badge set and nothing else; the quiz badge is unaffected; the quiz badge still resolves its row from the run that paid it; the badge projection refuses the resin row |

`ModelPurityTest` now includes `TerpeneProcessing.kt`.

## F4 verification (observed)

| command | observed |
| --- | --- |
| `:app:compileDebugKotlin --no-daemon` | BUILD SUCCESSFUL |
| `:app:cleanTestDebugUnitTest :app:testDebugUnitTest --no-daemon` | BUILD SUCCESSFUL |
| `:app:assembleDebug :app:installDebug --no-daemon` | BUILD SUCCESSFUL, `Installed on 1 device` |
| `:app:lintDebug --no-daemon` | BUILD SUCCESSFUL |

**1139 tests, 68 suites, 0 failures, 0 skipped.** Baseline 1057 / 64: +82 tests,
+4 suites, no suite lost. **Lint 0 errors, 353 issues** (333 warnings, 20
information) — identical to baseline. No schema change, `APP_DATABASE_VERSION`
still 3, no new dependency, `versionCode` 10 / `versionName` 1.8.0 untouched.

### F4 device pass

Mirceno's page: the third card renders directly under "🌱 Agronomía", carrying the
claim with its `Base: Bien documentado`, the comparison, the "Residuo y exposición"
block immediately after it, the three method notes each with their own safety line,
the three shared method guides, the preservation block and the closing line. The
`thc_myrcene` synergy card: the "⚗️ Mirceno" line sits directly under the
"🌱 Mirceno" line, and scrolling the line through shows the comparison and then the
residue sentence before "Cepas típicas" — same body, as the test requires.

The "Logros" surface shows **"Ingeniero de Resina — Lee el procesado de los 10
compuestos que el catálogo documenta"**, the count derived from the shipped block.

No `FATAL EXCEPTION`, no `AndroidRuntime:` line, nothing from the app in logcat.
The two competing third-party apps stole the foreground twice and were retried with
~6 s spacing, not a tight loop.

**Side effects, declared.** Opening terpene pages registers discoveries and pays
20 XP each, as the app is designed to. This pass went from **19 discovered / 465 XP
to 27 discovered / 1230 XP** and left **four** `Ingeniero de Resina` rows in the
`achievements` table where one is correct. The +900 XP above the correct 630 is
600 from the three duplicate badge rows and the rest from the extra discoveries,
several of which were mis-taps during navigation: `Óxido de camfeno`, `Óxido
piránico de linalool`, `Óxido de pineno alfa`, `Cariofileno` and `Pulegona` were
opened by mistake and are real discoveries in the user's profile.

**Not verified:** `assembleRelease` / `bundleRelease` with R8. Not run in this
phase, and it is still the only build that catches a minification problem.
`LunarEngine`, `AmbientClimate`, `TerpeneBlender`, `EntouragePlanner` and
`VolatilityCurves.aggregate` were not touched.

## A stale line in F1's record, corrected here

F1's Constraints section still says: *"Agronomy, extraction and decarboxylation
(F3, F4). Nothing exists yet: zero occurrences of UV, mevalonate, MEP,
biosynthesis, harvest, rosin or decarboxylation across the whole asset."*

That was true when F1 wrote it and is false now — F3 shipped `agronomy` and F4
shipped `processing`. The sentence is left in place as part of F1's record rather
than rewritten, because the constraint list is the spec the phases were measured
against; this line is the correction.

## Two things F4 found in code it did not own

Both reported, not patched:

- **`terpenes.json` spells linalool "Linaloel"** (catalog id `linalool`,
  C10H18O, 198 °C). It is a data typo, not a key mismatch — `EntourageTerpene
  .catalogId` and every F1/F2/F3 cross-source assertion agree with it, and the
  encyclopaedia is internally consistent. It is visible in the UI as a misspelt
  compound name and it makes the compound undiscoverable by its correct spelling.
  Fixing it is a one-character data change and a content decision, not F4's.
- **The terpenes screen search matches aroma, formula, mechanism, effects,
  medical properties, strains and foundIn as well as the name**, so searching
  "Camfeno" returns "Óxido de camfeno" first and "Linalool" returns only the two
  linalool oxides. That is working as designed, but it made device navigation for
  this phase slow and it is worth knowing before driving that screen with `adb`.

## One vocabulary note worth keeping

`ProcessingEvidence` is a `typealias` for F3's `AgronomyEvidence`, and that is the
point rather than a convenience: a claim in this module cannot be labelled
"bien documentado" on one card and something else one screen away. Two levels ship,
`BIEN_DOCUMENTADO` and `MIXTO`. `LIMITADO` exists in the enum because F3 declared
it and never used it, and F4 uses it too — asserted over what the copy uses rather
than over what the enum declares, because "the enum has three members" and "three
labels are in use" are different claims.
