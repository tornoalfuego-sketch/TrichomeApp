package com.trichome.app.ui.screens.plant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.trichome.app.data.entity.GrowEvent
import com.trichome.app.data.entity.Plant
import com.trichome.app.model.GrowStageCopy
import com.trichome.app.model.GrowStagePlanner
import com.trichome.app.model.Phase
import com.trichome.app.model.PhotoperiodConfig
import com.trichome.app.model.PlantCycleState
import com.trichome.app.model.PlantFinalizationPlan
import com.trichome.app.model.StageOptionSource
import com.trichome.app.model.StageProgressEngine
import com.trichome.app.model.SuperCycleEngine
import com.trichome.app.model.SuperCycleResult
import com.trichome.app.ui.components.accentButtonColors
import com.trichome.app.ui.components.MainBottomBar
import com.trichome.app.ui.components.rememberDestructiveConfirmation
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.components.SolidProgressRing
import com.trichome.app.ui.navigation.PLANT_ID_ARG
import com.trichome.app.ui.navigation.PROTOCOL_ID_ROUTE
import com.trichome.app.ui.navigation.SUPER_CYCLE_ID_ROUTE
import com.trichome.app.ui.navigation.JOURNAL_ID_ROUTE
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.viewmodel.GrowStageViewModel
import com.trichome.app.viewmodel.PlantDetailUiState
import com.trichome.app.viewmodel.PlantDetailViewModel
import com.trichome.app.viewmodel.appViewModel
import com.trichome.app.viewmodel.stageEntryDateLabelEs
import com.trichome.app.viewmodel.stageLabelEs
import kotlinx.coroutines.launch

/**
 * Whole days between two instants, floored.
 *
 * `StageProgressEngine.daysInGrow` is 1-based because a plant created today is on day one;
 * a duration is not, and a stage entered today has lasted zero days rather than one. So this
 * is a separate function rather than a reused one with an off-by-one adjustment at the call
 * site — the off-by-one adjustment at the call site is the bug this codebase already fixed
 * once, in `daysInGrow`.
 */
private fun daysBetween(fromMillis: Long, toMillis: Long): Long =
    ((toMillis - fromMillis).coerceAtLeast(0L)) / 86_400_000L

/**
 * Plant detail. `daysInGrow` uses [StageProgressEngine.daysInGrow] — created
 * today counts as **Day 1** (off-by-one bug fixed).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantDetailScreen(
    plantId: Long,
    navController: NavHostController,
    themeState: TrichomeThemeState
) {
    val vm = appViewModel { PlantDetailViewModel(it) }
    val accent = themeState.colorScheme().primary

    LaunchedEffect(plantId) {
        vm.loadPlant(plantId)
        vm.loadSuperCycle(plantId)
        vm.loadLatestStage(plantId)
    }

    val uiState = vm.uiState
    val plant = (uiState as? PlantDetailUiState.Success)?.plant
    val tintAccent = themeState.accentColor
    val scope = rememberCoroutineScope()

    // The screen had no action icons at all: a plant could only be renamed or
    // deleted from a tent row, and `PlantDetailViewModel` had neither method.
    // `rememberDestructiveConfirmation` is the shared one every other delete
    // uses; a tap only arms it, the write happens after the grower confirms.
    var editing by remember { mutableStateOf(false) }

    // ── Cambiar de carpa ───────────────────────────────────────────
    //
    // Snapshot state, same discipline as `rememberDestructiveConfirmation`: a plain
    // `var` would not invalidate the composition, and the dialog would not appear
    // until something else forced a recomposition. Both the destination tents and the
    // per-tent supercycle inputs are awaited before the dialog opens, so the plan can
    // never be resolved against an empty map — which would make every destination
    // look unconfigured and quietly plan an 18/6 write.
    var migrating by remember { mutableStateOf(false) }
    var migrationInputs by remember { mutableStateOf<Map<Long, PhotoperiodConfig>>(emptyMap()) }
    var migrationState by remember { mutableStateOf<PlantCycleState?>(null) }
    var migrationError by remember { mutableStateOf<String?>(null) }
    val tents by vm.tents.collectAsState()

    // ── Crop stage control ────────────────────────────────────────────
    //
    // Three actions, one panel, and the dialogs hoisted here because they need the plant and
    // the ViewModel rather than the header content.
    //
    // Both flags are snapshot state, the same discipline as
    // `rememberDestructiveConfirmation`: a plain `var` would not invalidate the composition
    // and the dialog would not appear until something else forced a recomposition.
    val stageVm = appViewModel { GrowStageViewModel(it) }
    var changingStage by remember { mutableStateOf(false) }
    var finalizing by remember { mutableStateOf(false) }
    var finalizePlan by remember { mutableStateOf<PlantFinalizationPlan?>(null) }
    var stageNotice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(plantId) {
        stageVm.load(plantId)
        stageVm.observeTimeline(plantId)
    }

    val deleteConfirmation = rememberDestructiveConfirmation<Plant>(
        title = { "Eliminar planta" },
        message = { PlantDeletionNotice.message(it) },
        confirmLabel = { "Eliminar" },
        onConfirmed = { target ->
            vm.deletePlant(target) { deleted -> if (deleted) navController.popBackStack() }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plant?.name ?: "Detalle de Planta") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    // Only offered once the row has resolved: there is
                    // nothing to edit or delete while it is still loading,
                    // and a wrong id is worse than no button.
                    plant?.let { current ->
                        // The move needs the destination tents and the per-tent
                        // supercycle rows resolved before it opens, so both loads are
                        // awaited here rather than inside the dialog. A dialog planned
                        // against an empty map would tell the grower the destination has
                        // no config when it actually does.
                        IconButton(
                            onClick = {
                                migrationError = null
                                scope.launch {
                                    val inputs = vm.loadMigrationInputs(tents.map { it.id })
                                    val state = vm.migrationStateFor(current)
                                    migrationInputs = inputs
                                    migrationState = state
                                    migrating = true
                                }
                            },
                            enabled = tents.any { it.id != current.tentId }
                        ) {
                            Icon(
                                Icons.Default.SwapHoriz,
                                "Cambiar de carpa",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { editing = true }) {
                            Icon(Icons.Default.Edit, "Editar planta")
                        }
                        IconButton(onClick = { deleteConfirmation.request(current) }) {
                            Icon(
                                Icons.Default.Delete,
                                "Eliminar planta",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // The three-button system bar sat on top of these buttons and
                    // the labels were unreadable; verified on a device at
                    // 1080x2340 with navigation_mode = 0.
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Two lines, not one. Three Spanish labels plus an emoji in three
                // equal columns is about 340dp each, and at the largest text size
                // the user can pick a single line either clipped mid-word ("Bitác
                // ora") or overflowed into its neighbour. Letting it wrap makes the
                // row a little taller and the label whole, which is the trade the
                // user asked for: the label has to be readable.
                Button(
                    onClick = { navController.navigate(PROTOCOL_ID_ROUTE.replace("{${PLANT_ID_ARG}}", "$plantId")) },
                    modifier = Modifier.weight(1f)
                ) { Text("📋 Protocolos", maxLines = 2) }
                Button(
                    onClick = { navController.navigate(SUPER_CYCLE_ID_ROUTE.replace("{${PLANT_ID_ARG}}", "$plantId")) },
                    modifier = Modifier.weight(1f)
                ) { Text("☀️ SuperCiclo", maxLines = 2) }
                Button(
                    onClick = { navController.navigate(JOURNAL_ID_ROUTE.replace("{${PLANT_ID_ARG}}", "$plantId")) },
                    modifier = Modifier.weight(1f)
                ) { Text("📒 Bitácora", maxLines = 2) }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (uiState) {
                PlantDetailUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = accent)
                        Text("Cargando planta…", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                is PlantDetailUiState.Error -> {
                    SolidPanel {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "⚠️ No pudimos abrir la planta",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                uiState.message,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = { navController.popBackStack() },
                                colors = accentButtonColors(accent)
                            ) {
                                Text("← Volver a las carpas")
                            }
                        }
                    }
                }

                is PlantDetailUiState.Success -> {
                    val success = uiState
                    PlantDetailContent(
                        success = success,
                        superCycleResult = vm.superCycleResult,
                        events = vm.events,
                        accent = accent,
                        stageVm = stageVm,
                        onChangeStage = { changingStage = true },
                        onFinalize = {
                            // The plan is resolved before the dialog opens so the counts
                            // ("3 eventos de bitácora") are the real ones, not the zeros a
                            // dialog rendered from an unresolved read would show.
                            scope.launch {
                                finalizePlan = stageVm.planFinalization(plantId)
                                finalizing = finalizePlan != null
                            }
                        }
                    )
                }
            }
        }
    }

    if (migrating && plant != null && migrationState != null) {
        PlantMigrationDialog(
            plantId = plant.id,
            plantName = plant.name,
            current = migrationState!!,
            currentTentId = plant.tentId,
            tents = tents,
            tentPhotoperiods = migrationInputs,
            accent = accent,
            onDismiss = {
                migrating = false
                migrationState = null
            },
            onConfirm = { destination, plan ->
                // Cleared before the write: the write recomposes, and a dialog still on
                // screen for that frame would be a second target for a double tap.
                migrating = false
                migrationState = null
                migrationError = null
                vm.applyMigration(plant.id, destination.id, plan) { moved ->
                    if (moved) {
                        vm.loadPlant(plant.id)
                    } else {
                        // The write failed, so say so rather than leaving the grower
                        // believing the plant moved.
                        migrationError = "No se pudo mover la planta de carpa."
                    }
                }
            }
        )
    }

    migrationError?.let { problem ->
        SolidPanel {
            Text(
                text = problem,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    // ── Crop stage dialogs ───────────────────────────────────────────
    //
    // Both clear their flag *before* the write: the write recomposes, and a dialog still on
    // screen for that frame is a second target for a double tap. A double-tapped stage
    // change writes two entries; a double-tapped finalize is harmless but still wrong.
    if (changingStage && plant != null) {
        ChangeStageDialog(
            options = stageVm.options,
            currentStage = plant.currentStage,
            sourceLabelEs = stageVm.options.firstOrNull()?.source?.labelEs
                ?: StageOptionSource.LIFECYCLE.labelEs,
            reasonEs = stageVm.lastPlan?.takeIf { !it.isApplied }?.reasonEs,
            accent = accent,
            onDismiss = { changingStage = false },
            onConfirm = { option ->
                changingStage = false
                stageVm.changeStage(plant.id, option) { plan, ok ->
                    stageNotice = when {
                        plan == null -> null
                        !ok && plan.isApplied ->
                            "No se pudo guardar el cambio de etapa."
                        !ok -> plan.reasonEs
                        else -> GrowStageCopy.transitionAppliedEs(plan)
                    }
                }
            }
        )
    }

    finalizePlan?.takeIf { finalizing }?.let { plan ->
        FinalizeGrowDialog(
            bodyEs = GrowStagePlanner.finalizeDialogBodyEs(plan),
            accent = accent,
            onDismiss = { finalizing = false; finalizePlan = null },
            onConfirm = {
                finalizing = false
                stageVm.finalizePlant(plan) { written, ok ->
                    finalizePlan = null
                    stageNotice = if (ok && written != null) {
                        written.confirmationEs
                    } else {
                        "No se pudo finalizar el cultivo. No se ha borrado nada."
                    }
                }
            }
        )
    }

    stageNotice?.let { message ->
        SolidPanel {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    if (editing && plant != null) {
        PlantEditDialog(
            plant = plant,
            accent = accent,
            onDismiss = { editing = false },
            onSave = { updated ->
                vm.updatePlant(updated) { saved ->
                    if (saved) {
                        editing = false
                        // Re-read only once the write has landed. Reloading from
                        // `onSave` instead would race the update and repaint the
                        // values the grower just changed.
                        vm.loadPlant(plant.id)
                    }
                }
            }
        )
    }
}

/**
 * Everything the plant itself contributes to the screen, rendered only once the
 * row has resolved.
 */
@Composable
private fun PlantDetailContent(
    success: PlantDetailUiState.Success,
    superCycleResult: SuperCycleResult?,
    events: List<GrowEvent>,
    accent: Color,
    stageVm: GrowStageViewModel,
    onChangeStage: () -> Unit,
    onFinalize: () -> Unit
) {
    val plant = success.plant
    val zone = remember { java.time.ZoneId.systemDefault() }

    // ── Header info ─────────────────────────────────────────────────────
    SolidPanel {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Día de Crecimiento", style = MaterialTheme.typography.labelLarge)
                Text(
                    // "1 días" reads as a bug to a Spanish speaker; the plural is
                    // only correct from two.
                    //
                    // Deliberately still prose. The metric register cannot reach
                    // this one without a layout change: the count and its unit
                    // word are a single interpolated string, so a monospace face
                    // would have to cover "días" too, and splitting it into two
                    // Texts rewrites the header of a screen whose bottom bar is
                    // three fixed buttons. Same reason for the photoperiod and
                    // superday sentences further down.
                    "${success.daysInGrow} ${if (success.daysInGrow == 1) "día" else "días"}",
                    style = MaterialTheme.typography.displaySmall,
                    color = accent
                )
                Text(
                    plant.strain.ifBlank { "Cepa sin nombre" } + " · " + plant.currentStage,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            SolidProgressRing(
                percentage = 1f,
                size = 72,
                color = accent
            )
        }
    }

    // ── SuperCycle status ───────────────────────────────────────────────
    superCycleResult?.let { result ->
        SolidPanel {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("☀️ Fase actual", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val phaseText = when (result.phase) {
                        Phase.LIGHT -> "☀️ LUZ"
                        Phase.DARK -> "🌙 OSCURIDAD"
                        Phase.OFF -> "⏸️ OFF"
                    }
                    Text(phaseText, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.weight(1f))
                    SolidProgressRing(
                        percentage = result.phaseProgress,
                        size = 64,
                        color = if (result.isLight) Color(0xFFFFD54F) else Color(0xFF7C4DFF)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Superday ${result.superday} · ${result.hoursRemainingInPhase}h restantes · ${result.phasePercentage.toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    // ── Stage progress (protocol blocks) ─────────────────────────────────
    success.stageProgress?.let { sp ->
        SolidPanel {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📈 Progreso del protocolo", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Etapa: ${sp.stageName}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "Día ${sp.daysInCurrentStage} en etapa · ${sp.daysRemainingInStage} restantes · de ${sp.totalCycleDays} días",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { sp.overallProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent
                )
            }
        }
    }

    // ── Crop stage control ───────────────────────────────────────────────
    //
    // The panel is built from the ViewModel's resolved state rather than from local
    // strings, so the timeline wording, the archived banner and the transition confirmation
    // all come from `GrowStageCopy` and `GrowStagePlanner` — which is what lets a JVM test
    // assert that this screen authors no Spanish of its own.
    SolidPanel {
        GrowStagePanel(
            currentStageLabelEs = stageLabelEs(plant.currentStage),
            timelineLinesEs = stageVm.timeline.map { entry ->
                if (entry.exitedAt == null) {
                    GrowStageCopy.openEntryEs(
                        stageName = entry.stageName,
                        daysInStage = daysBetween(entry.enteredAt, System.currentTimeMillis())
                    )
                } else {
                    GrowStageCopy.closedEntryEs(
                        stageName = entry.stageName,
                        enteredLabelEs = stageEntryDateLabelEs(entry.enteredAt, zone),
                        exitedLabelEs = stageEntryDateLabelEs(entry.exitedAt, zone),
                        daysInStage = daysBetween(entry.enteredAt, entry.exitedAt)
                    )
                }
            },
            timelineEmptyEs = GrowStageCopy.TIMELINE_EMPTY_ES,
            timelineHintEs = GrowStageCopy.TIMELINE_HINT_ES,
            archivedBannerEs = GrowStageCopy.ARCHIVED_BANNER_ES,
            archivedChipEs = GrowStageCopy.ARCHIVED_CHIP_ES,
            isArchived = !plant.isActive,
            onChangeStage = onChangeStage,
            onFinalize = onFinalize,
            accent = accent
        )
    }

    // ── Latest events ───────────────────────────────────────────────────
    SolidPanel {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📒 Últimos eventos", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val latest = events.take(4)
            if (latest.isEmpty()) {
                Text("Sin eventos registrados", style = MaterialTheme.typography.bodyMedium)
            } else {
                latest.forEach { e ->
                    Text(
                        "• ${com.trichome.app.ui.screens.journal.EventTypeUi.labelResolved(e.eventType)} — ${dateShort(e.timestamp)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    if (plant.notes.isNotBlank()) {
        SolidPanel {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📝 Notas", style = MaterialTheme.typography.titleMedium)
                Text(plant.notes, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Short date like "12/05". */
fun dateShort(millis: Long): String {
    val d = java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return "%02d/%02d".format(d.dayOfMonth, d.monthValue)
}