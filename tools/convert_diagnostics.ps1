# Merges build/gen/diag_*.txt into the shipped assets/data/diagnostics.json.
#
# Input conventions (all pipe-delimited, UTF-8 no BOM):
#   diag_conditions.txt  11 fields: id|category|label_es|short_es|severity|
#                                   chlorosisMin|necrosisMin|spotDensityMin|
#                                   trichomeMin|webbingMin|greenMin
#                        `-1` means "this feature is not diagnostic here".
#   diag_plans.txt        3 fields: id|cause_es|<8 ';'-separated items>
#                        items 1-5 -> actionPlan_es, items 6-8 -> prevention_es
#   diag_symptoms.txt     4 fields: id|label_es|category|conditionId
#
# Run from the repo root.
$ErrorActionPreference = 'Stop'
$enc = New-Object System.Text.UTF8Encoding($false)
$target = Resolve-Path 'app\src\main\assets\data\diagnostics.json'

function Read-Lines($p) {
  [System.IO.File]::ReadAllLines($p, [System.Text.Encoding]::UTF8) | Where-Object { $_.Trim() }
}
function Split-List($s) {
  if ([string]::IsNullOrWhiteSpace($s)) { return @() }
  @($s -split ';' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}
# `-1` is the "not relevant" sentinel for a photo threshold.
function Threshold($raw) {
  if ($raw -eq '-1') { return $null }
  $v = 0.0
  if ([double]::TryParse($raw, [ref]$v)) { return $v }
  $null
}

# ---------------------------------------------------------------- conditions
$conditions = [System.Collections.Generic.List[object]]::new()
$ids = [System.Collections.Generic.HashSet[string]]::new()

# Plans, keyed by id, so a missing plan is visible rather than silently empty.
$plans = @{}
foreach ($l in Read-Lines 'build\gen\diag_plans.txt') {
  $p = $l.Split('|')
  $items = @(Split-List $p[2])
  $plans[$p[0]] = @{
    cause = $p[1]
    action = if ($items.Count -ge 5) { $items[0..4] } else { $items }
    prevention = if ($items.Count -ge 8) { $items[5..7] } else { @() }
  }
}

$generated = 0
$missingPlan = @()
foreach ($l in Read-Lines 'build\gen\diag_conditions.txt') {
  $p = $l.Split('|')
  if ($p.Count -ne 11) { throw "Condition field count $($p.Count): $l" }
  $id = $p[0]
  if (-not $ids.Add($id)) { continue }

  $ev = [ordered]@{}
  $map = @{
    5 = 'chlorosisMin'; 6 = 'necrosisMin'; 7 = 'spotDensityMin'
    8 = 'trichomeMin'; 9 = 'webbingMin';  10 = 'greenMin'
  }
  foreach ($i in $map.Keys) {
    $t = Threshold $p[$i]
    if ($null -ne $t) { $ev[$map[$i]] = $t }
  }

    $entry = [ordered]@{
    id          = $id
    category    = $p[1]
    label_es    = $p[2]
    short_es    = $p[3]
    severity    = $p[4]
    cause_es    = ''
    actionPlan_es  = @()
    prevention_es  = @()
    # No stage-specific restriction is documented for the generated entries.
    affectedPlants = @()
  }
  if ($ev.Count -gt 0) { $entry['photoEvidence'] = $ev }
  if ($plans.ContainsKey($id)) {
    $entry['cause_es']      = $plans[$id].cause
    $entry['actionPlan_es'] = $plans[$id].action
    $entry['prevention_es'] = $plans[$id].prevention
  } else {
    $missingPlan += $id
  }
  $conditions.Add($entry)
  $generated++
}

# Existing rows are appended last so their authored prose and symptom weights
# survive; the ids of the generated set are disjoint from the shipped ones.
$existing = [System.IO.File]::ReadAllText($target, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$kept = 0
# `null` is not the same as "absent" for kotlinx.serialization: an explicit
# null overrides the declared default and fails the decode. An old asset that
# shipped `"affectedPlants": null` would therefore crash the whole catalogue,
# so every list field is coerced back to a list.
function Or-List($v) {
  if ($null -eq $v) { return @() }
  if ($v -is [string]) { return @($v) }
  if ($v -is [System.Collections.IEnumerable]) { return @($v | ForEach-Object { "$_" }) }
  return @()
}
function Or-Map($v) {
  if ($null -eq $v) { return [ordered]@{} }
  $m = [ordered]@{}
  foreach ($p in $v.PSObject.Properties) { $m[$p.Name] = $p.Value }
  return $m
}
function Or-Text($v) { if ($null -eq $v) { return '' } else { return "$v" } }

foreach ($c in $existing.conditions) {
  if ($ids.Add($c.id)) {
    $row = [ordered]@{
      id = $c.id; category = $c.category; label_es = $c.label_es
      short_es = Or-Text $c.short_es
      severity = $(if (Or-Text $c.severity) { Or-Text $c.severity } else { 'moderada' })
      cause_es = Or-Text $c.cause_es
      actionPlan_es = @(Or-List $c.actionPlan_es)
      prevention_es = @(Or-List $c.prevention_es)
      affectedPlants = @(Or-List $c.affectedPlants)
      # A map already serialises as `{}` when empty, which is what
      # Map<String, Int> expects, so it must not be wrapped in `@()`:
      # that would enumerate it into a list of dictionary entries.
      symptomWeights = Or-Map $c.symptomWeights
    }
    # Keep the photographic evidence of the legacy rows: dropping it here once
    # made 15 shipped conditions unreachable by photo.
    if ($null -ne $c.photoEvidence) { $row['photoEvidence'] = $c.photoEvidence }
    if (Or-Text $c.photoNotes_es) { $row['photoNotes_es'] = Or-Text $c.photoNotes_es }
    $conditions.Add($row)
    $kept++
  }
}

# ------------------------------------------------------------------ symptoms
$symptoms = [System.Collections.Generic.List[object]]::new()
$sids = [System.Collections.Generic.HashSet[string]]::new()

# `DiagnosisSymptom` has no back-reference to its condition: the link is
# condition -> symptom -> weight, held in the condition's `symptomWeights` map.
# Without that map a condition is unreachable from the symptom picker, so the
# weights are derived here from the generated symptom file.
$weightsByCondition = @{}
foreach ($c in $conditions) {
  $w = $c['symptomWeights']
  if ($w) { $weightsByCondition[$c.id] = $w }
}

foreach ($s in $existing.symptoms) {
  if ($sids.Add($s.id)) {
    $symptoms.Add([ordered]@{
      id = $s.id
      label_es = Or-Text $s.label_es
      icon = Or-Text $s.icon
      # An explicit null overrides the declared default and fails the decode of
      # the whole catalogue, so it is coerced back to the default value.
      category = $(if (Or-Text $s.category) { Or-Text $s.category } else { 'general' })
    })
  }
}
$existingSymptomCount = $symptoms.Count
$addedSymptoms = 0
foreach ($l in Read-Lines 'build\gen\diag_symptoms.txt') {
  $p = $l.Split('|')
  if ($p.Count -ne 4) { throw "Symptom field count $($p.Count): $l" }
  $symptomId = $p[0]
  $conditionId = $p[3]
  if ($sids.Add($symptomId)) {
    $symptoms.Add([ordered]@{
      id = $symptomId; label_es = $p[1]; icon = ''; category = $p[2]
    })
    $addedSymptoms++
  }
  if (-not $weightsByCondition.ContainsKey($conditionId)) {
    $weightsByCondition[$conditionId] = [ordered]@{}
  }
  # A symptom that names a condition contributes to it. More specific wording
  # is not scored higher here: the engine only needs a non-zero contribution
  # for the condition to be reachable at all.
  $weightsByCondition[$conditionId][$symptomId] = 1
}

# Stamp the derived weights back onto the conditions that had none.
$stamped = 0
for ($i = 0; $i -lt $conditions.Count; $i++) {
  $c = $conditions[$i]
  if ($c.Contains('symptomWeights')) { continue }
  $w = $weightsByCondition[$c.id]
  if ($w -and $w.Count -gt 0) {
    $ordered = [ordered]@{}
    foreach ($k in $w.Keys) { $ordered[$k] = $w[$k] }
    $rebuilt = [ordered]@{}
    foreach ($k in $c.Keys) {
      $rebuilt[$k] = $c[$k]
      if ($k -eq 'severity') { $rebuilt['symptomWeights'] = $ordered }
    }
    if (-not $rebuilt.Contains('symptomWeights')) { $rebuilt['symptomWeights'] = $ordered }
    $conditions[$i] = $rebuilt
    $stamped++
  }
}

$doc = [ordered]@{
  version    = 2
  conditions = $conditions
  symptoms   = $symptoms
}
[System.IO.File]::WriteAllText($target, ($doc | ConvertTo-Json -Depth 8), $enc)

# -------------------------------------------------------------- read-back QA
$check = [System.IO.File]::ReadAllText($target, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$bytes = [System.IO.File]::ReadAllBytes($target)

# Schema conformance. PowerShell's ConvertTo-Json renders an empty array that
# came back unrolled from a function as `{}`, and an explicit null as `null`;
# kotlinx.serialization then rejects the whole catalogue at runtime. The write
# is only accepted if every list field really is a list and nothing is null.
$raw = [System.IO.File]::ReadAllText($target, [System.Text.Encoding]::UTF8)
$listFields = @('actionPlan_es', 'prevention_es', 'affectedPlants')
$schemaErrors = @()
foreach ($f in $listFields) {
  $asObject = ([regex]::Matches($raw, "`"$f`"\s*:\s*\{")).Count
  $asNull   = ([regex]::Matches($raw, "`"$f`"\s*:\s*null")).Count
  if ($asObject -gt 0) { $schemaErrors += "$f written as an object $asObject time(s)" }
  if ($asNull   -gt 0) { $schemaErrors += "$f written as null $asNull time(s)" }
}
# Any explicit null in a non-nullable field breaks the decode, so the whole
# file is swept rather than only the fields known to have been affected.
$anyNull = ([regex]::Matches($raw, ':\s*null')).Count
if ($anyNull -gt 0) { $schemaErrors += "$anyNull explicit null value(s) in the document" }
if ($schemaErrors.Count -gt 0) {
  $schemaErrors | ForEach-Object { "SCHEMA ERROR: $_" }
  throw "diagnostics.json does not match the app's schema; refusing to pass this build off as valid."
}
$condIds = @($check.conditions | ForEach-Object { $_.id })
$symIds = @($check.symptoms | ForEach-Object { $_.id })
# Every condition must be reachable from the symptom picker through its
# symptomWeights map, and every referenced symptom must exist.
$unreachable = @()
$dangling = @()
foreach ($c in $check.conditions) {
  $w = $c.symptomWeights
  if (-not $w -or $c.symptomWeights.PSObject.Properties.Count -eq 0) { $unreachable += $c.id; continue }
  foreach ($p in $c.symptomWeights.PSObject.Properties) {
    if ($p.Name -notin $symIds) { $dangling += "$($c.id)->$($p.Name)" }
  }
}
"generated conditions : $generated  (without a plan: $($missingPlan.Count))"
"kept old conditions  : $kept"
"total conditions     : $($check.conditions.Count)  unique=$(($condIds | Select-Object -Unique).Count)"
"with photoEvidence   : $(($check.conditions | Where-Object { $_.photoEvidence }).Count)"
"with actionPlan      : $(($check.conditions | Where-Object { $_.actionPlan_es.Count -gt 0 }).Count)"
"with symptomWeights  : $(($check.conditions | Where-Object { $_.symptomWeights.PSObject.Properties.Count -gt 0 }).Count)  stamped=$stamped"
"unreachable from UI  : $($unreachable.Count)  $(($unreachable -join ','))"
"dangling symptom ref : $($dangling.Count)  $(($dangling -join ','))"
"symptoms             : $($check.symptoms.Count)  (old $($existingSymptomCount) + new $($addedSymptoms))"
"first byte           : $($bytes[0])  (must be 123)"
