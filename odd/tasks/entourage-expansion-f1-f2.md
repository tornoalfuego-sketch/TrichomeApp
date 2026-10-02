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
- [ ] T6 `TerpeneVolatility` with provenance, and the 158-entry source.
- [ ] T7 `VolatilityWindow.derive` banded by family, marked `DERIVED`.
- [ ] T8 `VolatilityCurve` as steps, reusing `windowFor` / `isViable`.
- [ ] T9 Volatility section on the terpene detail page.
- [ ] T10 F1 + F2 verification: compile, tests, lint, device screenshots.

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

F1 closed at `823ccaa`, off `18d5d29`. Branch
`fix/navigation-theming-and-confirmations`. 886 tests green (was 875, +11 new,
0 failures, no suite lost), lintDebug 0 errors / 353 issues unchanged,
`compileDebugKotlin` green. No schema change, `APP_DATABASE_VERSION` still 3, no
new dependency.

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

## Next step

T6, F2. T1's guarantee stands, so `TerpeneVolatility` can read `terpenes.json` as
the single boiling-point source and must not re-derive it from
`entourage_data.json`.
