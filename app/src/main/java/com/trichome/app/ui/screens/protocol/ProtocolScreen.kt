package com.trichome.app.ui.screens.protocol

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.trichome.app.data.entity.Protocol
import com.trichome.app.data.entity.ProtocolStage
import com.trichome.app.model.ProtocolFieldGroup
import com.trichome.app.model.SuperCycleEngine
import com.trichome.app.ui.components.accentButtonColors
import com.trichome.app.ui.components.accentContentOn
import com.trichome.app.ui.components.AppTopBar
import com.trichome.app.ui.components.formatTime
import com.trichome.app.ui.components.rememberDestructiveConfirmation
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.viewmodel.ProtocolViewModel
import com.trichome.app.viewmodel.appViewModel
import kotlinx.coroutines.launch
import com.trichome.app.model.ProtocolTargetEditor
import com.trichome.app.ui.theme.LocalTertiaryText
import com.trichome.app.ui.theme.LocalMetricValue

/**
 * Protocol block editor. Each protocol is a header (name, photoperiod) plus an
 * ordered list of stage blocks ([ProtocolStage]). All data flows through
 * [ProtocolViewModel] — no component builds a database on its own.
 *
 * ## The two surfaces this screen mounts
 *
 * The card prints fifteen declared targets and, until this phase, offered no way to
 * write any of them: fourteen grow-wide columns on `protocols` and one per-stage column
 * on `protocol_stages`, all rendered, all exported, none writable. They are edited on
 * their own pages now — `ProtocolTargetsScreen` for the grow-wide groups and
 * `ProtocolStageTargetScreen` for a stage's band — and they are separate pages rather
 * than more fields on `ProtocolEditorDialog` because they belong to different entities
 * and because that dialog was already measured filling most of a 2000-pixel-tall screen.
 *
 * Both are mounted here **in place of** the list, never inside it: a page that scrolled
 * inside the screen's `LazyColumn` would be the second owner on the vertical axis that
 * `ScrollOwnershipTest` exists for, so the branches are mutually exclusive and the bar
 * and the FAB are hidden while one is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolScreen(
    plantId: Long,
    navController: NavHostController,
    themeState: TrichomeThemeState
) {
    val vm = appViewModel { ProtocolViewModel(it) }
    val accent = themeState.colorScheme().primary
    val scope = rememberCoroutineScope()

    var showEditor by remember { mutableStateOf(false) }
    var editingProtocol by remember { mutableStateOf<Protocol?>(null) }

    // The two target write surfaces, held as session state rather than as a boolean each:
    // every one of them is opened with the row it edits, and a flag plus a second piece
    // of state is two things that can disagree.
    var targetsSession by remember { mutableStateOf<ProtocolTargetSession?>(null) }
    var editingStage by remember { mutableStateOf<ProtocolStage?>(null) }

    // A tap only arms the dialog; the row is deleted inside the repository call
    // the user confirms.
    val deleteConfirmation = rememberDestructiveConfirmation<Protocol>(
        title = { "Eliminar protocolo" },
        message = { protocol ->
            "Se eliminará el protocolo «${protocol.name}» y todas sus etapas. " +
                "Esta acción no se puede deshacer."
        },
        confirmLabel = { "Eliminar" },
        onConfirmed = { protocol -> scope.launch { vm.deleteProtocol(protocol) } }
    )

    LaunchedEffect(plantId) { vm.loadProtocols(plantId) }

    val onTargetsSurface = targetsSession != null || editingStage != null

    Scaffold(
        topBar = {
            // One bar for the screen at a time: the write surfaces bring their own, and
            // two bars over one title is a header the grower reads twice.
            if (!onTargetsSurface) {
                AppTopBar(
                    title = "📋 Protocolos de Cultivo",
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        },
        floatingActionButton = {
            if (!onTargetsSurface) {
                FloatingActionButton(
                    onClick = { editingProtocol = null; showEditor = true },
                    containerColor = accent,
                    contentColor = accentContentOn(accent)
                ) {
                    Icon(Icons.Default.Add, "Añadir Protocolo")
                }
            }
        }
    ) { padding ->
        val session = targetsSession
        val stage = editingStage
        when {
            session != null -> ProtocolTargetsScreen(
                session = session,
                onClose = { targetsSession = null },
                onSave = { resolved ->
                    // Dismissed before the write runs: the surface is still on screen for
                    // the frame between the tap and the recomposition, and a double tap
                    // there writes the same row twice.
                    targetsSession = null
                    scope.launch { vm.saveProtocol(resolved) }
                }
            )

            stage != null -> {
                val owner = vm.protocols.firstOrNull { p ->
                    vm.blocks[p.id]?.any { it.id == stage.id } == true
                }
                if (owner == null) {
                    // The protocol was deleted underneath this row. The band write is
                    // addressed by protocol as well as by stage, so there is nothing to
                    // write to; closing is the honest outcome, and it happens in an effect
                    // rather than by writing state while composing.
                    LaunchedEffect(stage.id) { editingStage = null }
                } else {
                    ProtocolStageTargetScreen(
                        protocol = owner,
                        stage = stage,
                        onClose = { editingStage = null },
                        onSave = { band ->
                            editingStage = null
                            scope.launch { vm.saveStageTarget(owner.id, stage, band) }
                        }
                    )
                }
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text("Editor por bloques", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Cada protocolo define etapas ordenadas; su progreso se aplica a la planta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalTertiaryText.current
                )
                Spacer(Modifier.height(14.dp))

                if (vm.protocols.isEmpty()) {
                    SolidPanel {
                        Column(
                            modifier = Modifier.padding(32.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No hay protocolos registrados", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Toca + para crear tu primer protocolo por bloques",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(vm.protocols, key = { it.id }) { protocol ->
                            ProtocolCard(
                                protocol = protocol,
                                blocks = vm.blocks[protocol.id].orEmpty(),
                                onEdit = { editingProtocol = protocol; showEditor = true },
                                onDelete = { deleteConfirmation.request(protocol) },
                                onEditTargets = { group ->
                                    targetsSession = ProtocolTargetSession(protocol, group)
                                },
                                onEditStageTarget = { editingStage = it },
                                onLogStage = { stageName ->
                                    scope.launch {
                                        vm.logStageTransition(plantId, protocol.id, stageName)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        ProtocolEditorDialog(
            protocol = editingProtocol,
            stages = editingProtocol?.let { vm.blocks[it.id].orEmpty() }.orEmpty(),
            plantId = plantId,
            accent = accent,
            onDismiss = { showEditor = false; editingProtocol = null },
            onSave = { name, lightHours, darkHours, presetType, blocks ->
                scope.launch {
                    if (blocks.isEmpty() || name.isBlank()) {
                        showEditor = false
                        editingProtocol = null
                        return@launch
                    }
                    val protocol = editingProtocol?.copy(
                        name = name.trim(),
                        lightHours = lightHours,
                        darkHours = darkHours,
                        presetType = presetType
                    ) ?: Protocol(
                        plantId = plantId,
                        name = name.trim(),
                        lightHours = lightHours,
                        darkHours = darkHours,
                        presetType = presetType
                    )
                    // insertProtocol uses REPLACE: for edits the row id is preserved,
                    // for new protocols the returned id is used for the stages.
                    val savedId = if (editingProtocol == null) {
                        vm.saveProtocol(protocol)
                    } else {
                        vm.saveProtocol(protocol)
                        protocol.id
                    }
                    // The drafts carry each stage's own id, so this updates those rows in
                    // place and deletes only a stage the grower removed. It does not write
                    // `vpdTarget`: see `ProtocolRepository.saveStages`.
                    vm.saveStages(savedId, blocks)
                    showEditor = false
                    editingProtocol = null
                }
            }
        )
    }
}

@Composable
private fun ProtocolCard(
    protocol: Protocol,
    blocks: List<com.trichome.app.data.entity.ProtocolStage>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onEditTargets: (com.trichome.app.model.ProtocolTargetGroup) -> Unit,
    onEditStageTarget: (com.trichome.app.data.entity.ProtocolStage) -> Unit,
    onLogStage: (String) -> Unit
) {
    SolidPanel {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(protocol.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Fotoperiodo ${protocol.lightHours}/${protocol.darkHours} · ${blocks.sumOf { it.durationDays.coerceAtLeast(0) }} días totales",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar protocolo") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Eliminar protocolo") }
            }

            Spacer(Modifier.height(8.dp))

            if (blocks.isEmpty()) {
                Text("Sin bloques de etapa", style = MaterialTheme.typography.bodyMedium)
            } else {
                blocks.forEachIndexed { index, stage ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$index. ${stage.stageName}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${stage.durationDays} día(s)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { onLogStage(stage.stageName) }) {
                            Text("Registrar", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Preset: ${protocol.presetType} · Inicio: ${formatTime(protocol.lightHours * 60)} / ${formatTime(protocol.darkHours * 60)}",
                style = MaterialTheme.typography.bodySmall,
                color = LocalTertiaryText.current
            )

            Spacer(Modifier.height(12.dp))

            // Extended agronomic fields, then the per-stage targets. Labels, units and
            // the empty state come from model/ProtocolExtendedFields, so a field the
            // grower has not filled in reads "Sin definir" and never a fabricated number.
            //
            // Each group opens its own write surface: these targets were readable and
            // unwritable until this phase, so a group that cannot be tapped is a group
            // that keeps being a fact sheet.
            extendedFieldBlocks(protocol).forEach { group ->
                FieldGroupBlock(
                    group = group,
                    onEdit = targetGroupFor(group.titleEs)?.let { group -> { onEditTargets(group) } }
                )
            }
            stageTargetBlocks(blocks).forEach { group ->
                StageTargetBlock(group = group, stages = blocks, onEdit = onEditStageTarget)
            }
        }
    }
}

/**
 * One titled group of labelled fields, as the protocol card renders them.
 *
 * Extracted rather than inlined so the card can print two runs of groups — the
 * grow-wide targets and the per-stage ones — through one renderer. Two copies of this
 * loop is how the metric register ends up applied to the header's numbers and not to
 * the stage's.
 *
 * No scrollable of its own: the card is a row inside the screen's `LazyColumn`, and a
 * second scroll owner on the same axis is the layout defect `ScrollOwnershipTest` names.
 *
 * @param onEdit null for a group that has no write surface. Every grow-wide group has
 *   one; the null is what makes the affordance conditional rather than a control that
 *   leads nowhere.
 */
@Composable
private fun FieldGroupBlock(
    group: ProtocolFieldGroup,
    onEdit: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onEdit != null) Modifier.clickable(onClick = onEdit) else Modifier)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                group.titleEs,
                style = MaterialTheme.typography.titleSmall,
                color = LocalTertiaryText.current,
                modifier = Modifier.weight(1f)
            )
            if (onEdit != null) {
                TargetEditGlyph()
            }
        }
        group.noteEs?.let { note ->
            Text(note, style = MaterialTheme.typography.bodySmall, color = LocalTertiaryText.current)
        }
        if (onEdit != null) {
            Text(
                ProtocolTargetEditor.EDIT_HINT_ES,
                style = MaterialTheme.typography.bodySmall,
                color = LocalTertiaryText.current
            )
            // Said only while the group is empty. A card of fourteen "Sin definir" rows
            // reads as a fact sheet about a grow nobody has measured; this says it is
            // still yours to fill in.
            if (isGroupUnset(group)) {
                Text(
                    ProtocolTargetEditor.UNSET_HINT_ES,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalTertiaryText.current
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        group.rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    row.labelEs,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                // The number takes the metric register; the unit and the grower's own words
                // stay in the prose face. Absent when there is no number, which is what
                // "Sin definir" means.
                row.metricEs?.let { metric ->
                    Text(metric, style = LocalMetricValue.current)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    row.detailEs,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalTertiaryText.current
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * The per-stage block, with each row opening the band editor for that stage.
 *
 * Drawn from the [ProtocolStage] objects rather than from the group's pre-resolved rows,
 * because the row is now a control: the object behind the tap has to be the stage the
 * grower is looking at, and an index back into a sorted query result is exactly the kind
 * of mapping that reads wrong the day a stage is reordered. The text is still the model's
 * — [stageTargetRow] — so the write surface and this one cannot describe a stage
 * differently.
 *
 * Separate from [FieldGroupBlock] for the same reason the write surfaces are: a band
 * belongs to a stage, and rendering it as another grow-wide group would put it on the
 * protocol's row, where the storage is not.
 */
@Composable
private fun StageTargetBlock(
    group: ProtocolFieldGroup,
    stages: List<ProtocolStage>,
    onEdit: (ProtocolStage) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            group.titleEs,
            style = MaterialTheme.typography.titleSmall,
            color = LocalTertiaryText.current
        )
        group.noteEs?.let { note ->
            Text(note, style = MaterialTheme.typography.bodySmall, color = LocalTertiaryText.current)
        }
        Text(
            ProtocolTargetEditor.EDIT_HINT_ES,
            style = MaterialTheme.typography.bodySmall,
            color = LocalTertiaryText.current
        )
        Spacer(Modifier.height(2.dp))
        orderedStages(stages).forEach { stage ->
            val row = stageTargetRow(stage)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEdit(stage) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    row.labelEs,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                row.metricEs?.let { metric ->
                    Text(metric, style = LocalMetricValue.current)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    row.detailEs,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalTertiaryText.current
                )
                TargetEditGlyph()
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * Dialog to create/edit a protocol and its ordered stage blocks.
 *
 * Carries the name, the photoperiod and the schedule — and nothing else. The fifteen
 * declared targets are written on their own pages (`ProtocolTargetEditors.kt`), because
 * this dialog was already measured filling most of a 2000-pixel-tall screen on a real
 * device and fifteen more inputs cannot go in here.
 *
 * The blocks are [ProtocolStage] rows rather than name/duration pairs, and that is the
 * whole difference between a schedule save that preserves each stage's identity and one
 * that does not: `ProtocolRepository.saveStages` tells an edit from a removal by the
 * stage's id, so a pair list — which carries no id — could only ever look like a list of
 * additions.
 *
 * `FlowRow` rather than `Row` because the four photoperiod chips do not fit on one
 * line: "18/6", "12/12" and "24/0" are four characters each and "CUSTOM" is six, so the
 * last chip absorbed the overflow and wrapped one letter per line — verified on a device
 * at 1080x2340, where it read vertically as "CU ST O M". This is the same unweighted-`Row`
 * defect that once clipped "Bitácora" on the plant screen; a chip that cannot say which
 * preset it is has stopped being a control.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProtocolEditorDialog(
    protocol: Protocol?,
    stages: List<ProtocolStage>,
    plantId: Long,
    accent: Color,
    onDismiss: () -> Unit,
    onSave: (name: String, lightHours: Int, darkHours: Int, presetType: String, blocks: List<ProtocolStage>) -> Unit
) {
    // The dialog is reused across protocols, so every field seeded from the
    // protocol is keyed on its id: without the key Compose would keep the first
    // protocol's values — and its blocks — forever.
    val seedKey = editorSeedKey(protocol)
    var name by remember(seedKey) { mutableStateOf(protocol?.name.orEmpty()) }
    var lightHours by remember(seedKey) { mutableStateOf(protocol?.lightHours ?: 18) }
    var darkHours by remember(seedKey) { mutableStateOf(protocol?.darkHours ?: 6) }
    var presetType by remember(seedKey) { mutableStateOf(protocol?.presetType ?: "18/6") }
    var blocks by remember(seedKey) { mutableStateOf(initialStageDrafts(protocol, stages)) }
    var nameError by remember { mutableStateOf(false) }

    // Local editable blocks
    var blockName by remember { mutableStateOf("") }
    var blockDays by remember { mutableStateOf("7") }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var blockError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (protocol == null) "Nuevo Protocolo" else "Editar Protocolo") },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    nameError = true
                } else if (blocks.isEmpty()) {
                    blockError = true
                } else {
                    onSave(
                        name.trim(),
                        lightHours.coerceIn(0, 24),
                        darkHours.coerceIn(0, 24),
                        presetType,
                        blocks
                    )
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Nombre del Protocolo *") },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("El nombre no puede estar vacío") }
                    } else null,
                    singleLine = true
                )

                // Photoperiod presets. Wrapping, not a single line: see the KDoc on
                // this dialog for why "custom" cannot share a row with the three
                // numeric presets at the largest text size.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("18/6", "12/12", "24/0", "custom").forEach { preset ->
                        FilterChip(
                            selected = presetType == preset,
                            onClick = {
                                presetType = preset
                                when (preset) {
                                    "18/6" -> { lightHours = 18; darkHours = 6 }
                                    "12/12" -> { lightHours = 12; darkHours = 12 }
                                    "24/0" -> { lightHours = 24; darkHours = 0 }
                                }
                            },
                            label = { Text(preset.uppercase()) }
                        )
                    }
                }
                Text(
                    "Luz: ${formatTime(lightHours * 60)} · Oscuridad: ${formatTime(darkHours * 60)} · Preset reconocido: ${SuperCycleEngine.getPresetName(lightHours, darkHours)}",
                    style = MaterialTheme.typography.bodySmall
                )

                HorizontalDivider()

                Text("Bloques de etapa", style = MaterialTheme.typography.titleSmall)
                blocks.forEachIndexed { index, stage ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$index. ${stage.stageName} — ${stage.durationDays} día(s)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                blockName = stage.stageName
                                blockDays = stage.durationDays.toString()
                                editingIndex = index
                            }
                        ) { Icon(Icons.Default.Edit, "Editar bloque") }
                        IconButton(
                            onClick = { blocks = blocks.toMutableList().also { it.removeAt(index) } }
                        ) { Icon(Icons.Default.Delete, "Eliminar bloque") }
                    }
                }

                HorizontalDivider()

                // Add / edit block
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = blockName,
                        onValueChange = { blockName = it; blockError = false },
                        label = { Text("Etapa") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = blockDays,
                        onValueChange = { blockDays = it.filter { c -> c.isDigit() } },
                        label = { Text("Días") },
                        singleLine = true,
                        modifier = Modifier.width(88.dp)
                    )
                }
                if (blockError) {
                    Text(
                        "Añade al menos un bloque de etapa",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    onClick = {
                        val days = blockDays.toIntOrNull()
                        if (blockName.isBlank() || days == null || days <= 0) {
                            blockError = true
                        } else {
                            // Edited in place, so the draft keeps its id — and with it the
                            // row it stands for. A new block is a draft with no id, which is
                            // how `saveStages` tells an addition from an edit.
                            val next = blocks.toMutableList()
                            val index = editingIndex
                            if (index != null && next.indices.contains(index)) {
                                next[index] = next[index].copy(
                                    stageName = blockName.trim(),
                                    durationDays = days
                                )
                            } else {
                                next.add(
                                    ProtocolStage(
                                        protocolId = protocol?.id ?: 0L,
                                        stageName = blockName.trim(),
                                        durationDays = days,
                                        sortOrder = next.size
                                    )
                                )
                            }
                            blocks = next
                            blockName = ""
                            blockDays = "7"
                            editingIndex = null
                            blockError = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = accentButtonColors(accent)
                ) {
                    Text(if (editingIndex != null) "Actualizar bloque" else "Añadir bloque")
                }
            }
        }
    )
}