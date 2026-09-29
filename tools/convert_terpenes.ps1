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

# Enrichment for rows that shipped as stubs. The original ten entries carried
# no chemistry at all, which left them as bare names in an encyclopedia whose
# whole promise is doctoral-level detail. Blank fields are filled from here;
# fields that already have content are never overwritten.
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
function Enrich($id, $field, $current) {
  if ($current -and "$current".Trim()) { return $current }
  if (-not $enrich.ContainsKey($id)) { return $current }
  $v = $enrich[$id][$fieldMap[$field]]
  if ($v -and $v.Trim()) { return $v.Trim() }
  return $current
}
# A field can hold either a list or, in older assets, a stray object. Coerce
# both shapes to a list so the shipped JSON matches the app's schema.
function As-List($v) {
  if ($null -eq $v) { return @() }
  if ($v -is [string]) { return @($v) }
  if ($v -is [System.Collections.IEnumerable]) { return @($v | ForEach-Object { "$_" }) }
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
if ($schemaErrors.Count -gt 0) {
  $schemaErrors | ForEach-Object { "SCHEMA ERROR: $_" }
  throw "terpenes.json does not match the app's schema; refusing to pass this build off as valid."
}
"kept existing : $kept"
"added         : $added"
"total written : $($check.terpenes.Count)"
"first byte    : $($bytes[0])  (must be 123 for '{')"
"with formula  : $(($check.terpenes | Where-Object { $_.formula }).Count)"
"with pairs    : $(($check.terpenes | Where-Object { $_.pairsWith.Count -gt 0 }).Count)"
