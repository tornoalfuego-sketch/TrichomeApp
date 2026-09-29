# Expands build/gen/terp_*.txt (17 pipe-separated fields) into the shipped
# assets/data/terpenes.json catalog. Run from the repo root in PowerShell 5.1.
$ErrorActionPreference = 'Stop'
$enc = New-Object System.Text.UTF8Encoding($false)

$out = [System.Collections.Generic.List[object]]::new()
$seen = [System.Collections.Generic.HashSet[string]]::new()

function New-Entry {
  param($id, $name, $formula, $mass, $family, $aroma, $taste, $effects,
        $medical, $mechanism, $biosynth, $toxicity, $pairs, $foundIn,
        $strains, $richness, $bp, $favorite)
  # An empty array returned from a function is unrolled to nothing, and a bare
  # `= & $list $x` then serialises as `{}`, which breaks the List<String> field
  # in the app's schema. Wrapping the call in `@()` re-collects the output, so
  # empty renders as `[]` and a single value still renders as a one-item array.
  $list = { param($s) if ([string]::IsNullOrWhiteSpace($s)) { @() } else { @($s -split ';' | Where-Object { $_.Trim() }) } }
  [ordered]@{
    id                 = $id
    name               = $name
    formula            = $formula
    molarMass          = $mass
    family             = $family
    aroma              = $aroma
    taste              = $taste
    effects            = @(& $list $effects)
    medicalProperties  = @(& $list $medical)
    mechanism          = $mechanism
    biosynthesis       = $biosynth
    toxicity           = $toxicity
    pairsWith          = @(& $list $pairs)
    strains            = @(& $list $strains)
    foundIn            = @(& $list $foundIn)
    richness           = $richness
    boilingPoint       = $bp
    isFavorite         = $favorite
  }
}

# 1. Existing catalog first, so any user favourite survives the expansion.
# Every field is carried over verbatim: re-running the converter must never
# blank out content that a previous run wrote, otherwise the script would
# silently destroy the encyclopedia it just generated.
$existingPath = Resolve-Path 'app\src\main\assets\data\terpenes.json'
$existing = ([System.IO.File]::ReadAllText($existingPath, [System.Text.Encoding]::UTF8) | ConvertFrom-Json)

# ── Source data, and the order of authority ────────────────────────────────────
# The pipe files are the authored source. terpenes.json is generated OUTPUT and
# must never be treated as the source of truth for text, because a corrupted
# output that "already has content" is then preserved forever.
#
# That is not hypothetical: an earlier version gave the existing JSON priority,
# so a run that had written mojibake into the accents -- every accented Spanish
# character became two box-drawing codepoints, and the degree sign in 158 boiling
# points became U+252C U+2591 -- could never be repaired. The pipe files were
# clean the whole time. The source now wins; the JSON is a cache.
$source = @{}
foreach ($candidate in @('tools\terp_source.txt', 'build\gen\terp_*.txt')) {
  $files = @(Get-ChildItem $candidate -ErrorAction SilentlyContinue | Sort-Object Name)
  if ($files.Count -eq 0) { continue }
  foreach ($file in $files) {
    foreach ($line in [System.IO.File]::ReadAllLines($file.FullName, [System.Text.Encoding]::UTF8)) {
      if ([string]::IsNullOrWhiteSpace($line)) { continue }
      $f = $line.Split('|')
      if ($f.Count -ne 17) { throw "Source field count $($f.Count) for $($f[0])" }
      if (-not $source.ContainsKey($f[0])) { $source[$f[0]] = $f }
    }
  }
  break
}
if ($source.Count -eq 0) { throw "No terpene source found; refusing to regenerate from nothing." }

# Enrichment for rows that shipped as stubs. The original ten entries carried no
# chemistry at all, which left them as bare names in an encyclopedia whose whole
# promise is doctoral-level detail. It is a FALLBACK now, not an overlay.
$enrichPath = $null
foreach ($candidate in @('tools\terp_legacy_enrichment.txt', 'build\gen\terp_legacy_enrichment.txt')) {
  if (Test-Path $candidate) { $enrichPath = Join-Path (Get-Location) $candidate; break }
}
$enrich = @{}
if (Test-Path $enrichPath) {
  foreach ($line in [System.IO.File]::ReadAllLines($enrichPath, [System.Text.Encoding]::UTF8)) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $f = $line.Split('|')
    if ($f.Count -ne 17) { throw "Enrichment field count $($f.Count) for $($f[0])" }
    $enrich[$f[0]] = $f
  }
}
# $f[$field] for every field New-Entry receives, in the same order.
$fieldMap = @{
  name = 1; formula = 2; mass = 3; family = 4; aroma = 5; taste = 6
  effects = 7; medical = 8; mechanism = 9; biosynth = 10; toxicity = 11
  pairs = 12; foundIn = 13; strains = 14; richness = 15; bp = 16
}
# Codepoints a mojibake pass leaves behind. A single accented Spanish character
# was being written as two of these, so "Cítrico" arrived as C U+251C U+00A1 trico
# and nothing downstream could tell it from intended text.
$mojibakePattern = '[\u251C\u2502\u2551\u252C\u2591\u2592\u2500\u2550\u256D]'
function Is-Corrupt($v) {
  if ($null -eq $v) { return $false }
  return "$v" -match $mojibakePattern
}
# Source first, enrichment second, the existing JSON only as a last resort -- and
# never when it is corrupt.
function Enrich($id, $field, $current) {
  $idx = $fieldMap[$field]
  if ($source.ContainsKey($id)) {
    $v = $source[$id][$idx]
    if ($v -and $v.Trim()) { return $v.Trim() }
  }
  if ($enrich.ContainsKey($id)) {
    $v = $enrich[$id][$idx]
    if ($v -and $v.Trim()) { return $v.Trim() }
  }
  if ((Is-Corrupt $current) -or -not ($current -and "$current".Trim())) { return '' }
  return $current
}
# A field can hold either a list or, in older assets, a stray object. Coerce
# both shapes to a list so the shipped JSON matches the app's schema.
#
# A `;`-joined string must be SPLIT before it can be merged. Returning it whole
# made every merge append the enrichment as one opaque blob instead of
# deduplicating item by item, so re-running the generator grew strains, effects,
# synergies and sources on every pass: 517 references became 544, and myrcene
# ended up listing OG Kush and Blue Dream twice. An empty result also unrolls to
# $null in PowerShell 5.1, so the call site wraps this in @().
function As-List($v) {
  if ($null -eq $v) { return @() }
  if ($v -is [string]) {
    if (-not $v.Trim()) { return @() }
    return @($v.Split(';') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
  }
  if ($v -is [System.Collections.IEnumerable]) {
    return @($v | ForEach-Object { "$_".Trim() } | Where-Object { $_ })
  }
  return @()
}
function As-Text($v) { if ($null -eq $v) { return '' } else { return "$v" } }

foreach ($t in $existing.terpenes) {
  if ($seen.Add($t.id)) {
    $out.Add((New-Entry $t.id (Enrich $t.id 'name' (As-Text $t.name)) `
      (Enrich $t.id 'formula' (As-Text $t.formula)) `
      (Enrich $t.id 'mass' (As-Text $t.molarMass)) `
      (Enrich $t.id 'family' (As-Text $t.family)) `
      (Enrich $t.id 'aroma' (As-Text $t.aroma)) `
      (Enrich $t.id 'taste' (As-Text $t.taste)) `
      (@(As-List $t.effects) + @(As-List (Enrich $t.id 'effects' '')) | Select-Object -Unique) -join ';' `
      (@(As-List $t.medicalProperties) + @(As-List (Enrich $t.id 'medical' '')) | Select-Object -Unique) -join ';' `
      (Enrich $t.id 'mechanism' (As-Text $t.mechanism)) `
      (Enrich $t.id 'biosynth' (As-Text $t.biosynthesis)) `
      (Enrich $t.id 'toxicity' (As-Text $t.toxicity)) `
      (@(As-List $t.pairsWith) + @(As-List (Enrich $t.id 'pairs' '')) | Select-Object -Unique) -join ';' `
      (@(As-List $t.foundIn) + @(As-List (Enrich $t.id 'foundIn' '')) | Select-Object -Unique) -join ';' `
      (@(As-List $t.strains) + @(As-List (Enrich $t.id 'strains' '')) | Select-Object -Unique) -join ';' `
      (Enrich $t.id 'richness' (As-Text $t.richness)) `
      (Enrich $t.id 'bp' (As-Text $t.boilingPoint)) `
      ([bool]$t.isFavorite)))
  }
}
$kept = $out.Count

# 2. Generated catalog, skipping ids already present.
$added = 0
Get-ChildItem 'build\gen\terp_*.txt' | Sort-Object Name | ForEach-Object {
  foreach ($line in [System.IO.File]::ReadAllLines($_.FullName, [System.Text.Encoding]::UTF8)) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $p = $line.Split('|')
    if ($p.Count -ne 17) { throw "Field count $($p.Count) in $($_.Name): $line" }
    if (-not $seen.Add($p[0])) { continue }
    $out.Add((New-Entry $p[0] $p[1] $p[2] $p[3] $p[4] $p[5] $p[6] $p[7] `
      $p[8] $p[9] $p[10] $p[11] $p[12] $p[13] $p[14] $p[15] $p[16] $false))
    $added++
  }
}

$catalog = [ordered]@{ version = 2; terpenes = $out }
$json = $catalog | ConvertTo-Json -Depth 6
[System.IO.File]::WriteAllText($existingPath, $json, $enc)

# 3. Read back and validate what was actually written to disk.
$check = ([System.IO.File]::ReadAllText($existingPath, [System.Text.Encoding]::UTF8) | ConvertFrom-Json)
$bytes = [System.IO.File]::ReadAllBytes($existingPath)

# Schema conformance. PowerShell's ConvertTo-Json writes an empty array that
# was unrolled on return as `{}`, and `null` when a list field resolved to
# nothing; kotlinx.serialization then rejects the entire catalogue and the
# encyclopedia renders empty at runtime. Refuse to report success instead.
$raw = [System.IO.File]::ReadAllText($existingPath, [System.Text.Encoding]::UTF8)
$schemaErrors = @()
foreach ($f in @('effects', 'medicalProperties', 'pairsWith', 'strains', 'foundIn')) {
  $asObject = ([regex]::Matches($raw, "`"$f`"\s*:\s*\{")).Count
  $asNull   = ([regex]::Matches($raw, "`"$f`"\s*:\s*null")).Count
  if ($asObject -gt 0) { $schemaErrors += "$f written as an object $asObject time(s)" }
  if ($asNull   -gt 0) { $schemaErrors += "$f written as null $asNull time(s)" }
}
# Content regression guard: the converter is run repeatedly, and an earlier
# version blanked the chemical fields of every entry on each re-run.
$withChemistry = @($check.terpenes | Where-Object { $_.formula -and $_.mechanism }).Count
if ($check.terpenes.Count -gt 10 -and $withChemistry -lt ($check.terpenes.Count * 4 / 5)) {
  $schemaErrors += "only $withChemistry of $($check.terpenes.Count) entries carry a formula and mechanism"
}
# Idempotence guard on the list fields. As-List once failed to split the
# `;`-joined enrichment strings, so every re-run appended the whole enrichment
# blob as one extra item and the catalogue grew: 517 references became 544 and
# myrcene listed OG Kush and Blue Dream twice. The schema checks above all
# passed while this happened, because a duplicated list is still a valid list.
foreach ($t in $check.terpenes) {
  foreach ($f in @('effects', 'medicalProperties', 'pairsWith', 'strains', 'foundIn')) {
    $items = @($t.$f)
    if ($items.Count -ne @($items | Select-Object -Unique).Count) {
      $schemaErrors += "$($t.id)/$f lists $($items.Count) items with a repeat: $($items -join ', ')"
    }
  }
}
# Display names must be unique too: three isomer pairs once shipped under one
# Spanish name, so the encyclopedia showed two identical rows.
$nameCollisions = @($check.terpenes | Group-Object name | Where-Object { $_.Count -gt 1 })
foreach ($c in $nameCollisions) {
  $schemaErrors += "display name '$($c.Name)' is used by $($c.Count) entries: $(($c.Group | ForEach-Object { $_.id }) -join ', ')"
}
# Mojibake guard. A corrupted accent is still a valid string, so every schema
# check above passes while the encyclopedia shows C U+251C U+00A1 trico instead
# of Cítrico. This is the one check that catches an encoding regression.
foreach ($t in $check.terpenes) {
  foreach ($f in @('name', 'aroma', 'taste', 'mechanism', 'biosynthesis', 'toxicity', 'richness', 'boilingPoint')) {
    if (Is-Corrupt $t.$f) { $schemaErrors += "$($t.id)/$f contains mojibake: '$($t.$f)'" }
  }
}
if ($schemaErrors.Count -gt 0) {
  $schemaErrors | ForEach-Object { "SCHEMA ERROR: $_" }
  throw "terpenes.json does not match the app's schema; refusing to pass this build off as valid."
}
"source rows   : $($source.Count)"
"enrichment    : $(if ($enrich.Count -gt 0) { $enrich.Count } else { 'none' })"
"kept existing : $kept"
"added         : $added"
"total written : $($check.terpenes.Count)"
"first byte    : $($bytes[0])  (must be 123 for '{')"
"with formula  : $(($check.terpenes | Where-Object { $_.formula }).Count)"
"with pairs    : $(($check.terpenes | Where-Object { $_.pairsWith.Count -gt 0 }).Count)"
