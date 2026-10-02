package com.trichome.app.ui.screens.journal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.trichome.app.data.entity.GrowEvent
import com.trichome.app.data.entity.Plant
import com.trichome.app.data.entity.Reminder
import com.trichome.app.model.EventType
import com.trichome.app.ui.components.AppTopBar
import com.trichome.app.ui.components.MainBottomBar
import com.trichome.app.ui.components.rememberDestructiveConfirmation
import com.trichome.app.ui.components.SelectableChip
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.viewmodel.JournalViewModel
import com.trichome.app.viewmodel.ReminderViewModel
import com.trichome.app.viewmodel.appViewModel
import com.trichome.app.worker.ReminderSchedulerWorker
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Journal (Bitácora). Global entry point supports multi-plant selection
 * (shared [groupId]) and all 16 event types through the dynamic form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    navController: NavHostController,
    themeState: TrichomeThemeState,
    plantId: Long? = null
) {
    val vm = appViewModel { JournalViewModel(it) }
    val reminderVm = appViewModel { ReminderViewModel(it) }
    val accent = themeState.colorScheme().primary
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(plantId) { vm.loadEvents(plantId) }

    // Collected as state so the plant list reacts to the database instead of
    // being sampled once during composition.
    val plants by vm.plants.collectAsState()
    val plantNames = remember(plants) { plants.associate { it.id to it.name } }

    // Observed, not a snapshot: an edit or a delete has to be visible on the
    // same screen that caused it.
    val activeReminders by reminderVm.activeReminders.collectAsState()
    var editingReminder by remember { mutableStateOf<Reminder?>(null) }

    // Events had the same dead end the reminders had: `EventDao.updateEvent` had
    // no caller at all, so a journal row could be recorded and deleted but never
    // corrected. Held the same way as `editingReminder` -- a null means closed,
    // and the write reports its own outcome before the dialog lets go of the row.
    var editingEvent by remember { mutableStateOf<GrowEvent?>(null) }

    val deleteReminderConfirmation = rememberDestructiveConfirmation<Reminder>(
        title = { "Eliminar recordatorio" },
        message = { reminder ->
            "Se eliminará el recordatorio «${reminder.title}» y se cancelarán su alarma " +
                "y su tarea programada, así que dejará de avisarte. " +
                "Esta acción no se puede deshacer."
        },
        confirmLabel = { "Eliminar" },
        onConfirmed = { reminder -> scope.launch { reminderVm.deleteReminder(reminder) } }
    )

    var selectedType by remember { mutableStateOf(EventType.IRRIGATION) }
    // Multi-plant selection (enabled in global mode)
    var multiSelection by remember { mutableStateOf(plantId == null) }
    var selectedPlantIds by remember { mutableStateOf(setOf<Long>()) }

    // A tap only arms the dialog; the event row is deleted once the user
    // confirms, naming the event they picked.
    val deleteConfirmation = rememberDestructiveConfirmation<GrowEvent>(
        title = { "Eliminar evento" },
        message = { event ->
            "Se eliminará el evento ${EventTypeUi.labelResolved(event.eventType)} " +
                "del ${com.trichome.app.ui.screens.plant.dateShort(event.timestamp)}. " +
                "Esta acción no se puede deshacer."
        },
        confirmLabel = { "Eliminar" },
        onConfirmed = { event -> scope.launch { vm.deleteEvent(event) } }
    )

    Scaffold(
        topBar = {
            // The arrow was already conditional: the global journal is a
            // bottom-bar destination, so there is nothing to go back to.
            AppTopBar(
                title = if (plantId != null) "Bitácora de planta" else "📒 Bitácora",
                onNavigateBack = if (plantId != null) {
                    { navController.popBackStack() }
                } else {
                    null
                }
            )
        },
        bottomBar = {
            if (plantId == null) {
                MainBottomBar("journal", { navController.navigate(it) }, themeState)
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
            // ── Plant selector ───────────────────────────────────────
            if (plantId == null) {
                SolidPanel {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = multiSelection, onCheckedChange = { multiSelection = it })
                            Text("Selección múltiple (grupo)", style = MaterialTheme.typography.bodyLarge)
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                SelectableChip(
                                    "🌱 Todas",
                                    selected = selectedPlantIds.isEmpty() && !multiSelection,
                                    onClick = { selectedPlantIds = emptySet() }
                                )
                            }
                            items(plants, key = { it.id }) { p ->
                                SelectableChip(
                                    (if (multiSelection) "☑️ " else "") + p.name,
                                    selected = selectedPlantIds.contains(p.id),
                                    onClick = {
                                        selectedPlantIds = if (multiSelection) {
                                            if (selectedPlantIds.contains(p.id)) selectedPlantIds - p.id else selectedPlantIds + p.id
                                        } else {
                                            setOf(p.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Resolved once, above both cards: `resolveTargets` is a composable
            // so it cannot be called from the lambdas below, and an empty
            // result is the silent-save trap (nothing gets written).
            val targetPlants = resolveTargets(
                plants, plantId, multiSelection, selectedPlantIds
            )

            // ── Dynamic event form (horizontal carousel of types) ───
            SolidPanel {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Tipo de evento", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(EventTypeUi.ALL) { type ->
                            SelectableChip(
                                EventTypeUi.label(type),
                                selected = selectedType == type,
                                onClick = { selectedType = type }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = if (targetPlants.isEmpty()) {
                            "⚠ Sin planta seleccionada: elige una planta arriba para poder guardar"
                        } else {
                            "Aplica a: " + targetPlants.joinToString { it.name }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (targetPlants.isEmpty()) accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    DynamicEventForm(
                        eventType = selectedType,
                        accent = accent,
                        onSave = { form ->
                            if (targetPlants.isEmpty()) {
                                scope.launch { vm.reportSaveError("No hay ninguna planta seleccionada. Elige una antes de guardar el evento.") }
                                return@DynamicEventForm
                            }
                            val groupId = if (targetPlants.size > 1) UUID.randomUUID().toString() else null
                            scope.launch {
                                targetPlants.forEach { p ->
                                    vm.addEvent(form.toGrowEvent(p.id, groupId))
                                }
                                // Refresh only after the writes are queued.
                                vm.loadEvents(plantId)
                            }
                        }
                    )
                }
            }

            // ── Reminder quick-create ────────────────────────────────
            ReminderQuickCard(
                accent = accent,
                targetPlants = targetPlants,
                onCreated = { ReminderSchedulerWorker.syncNow(context) },
                onPersist = { r ->
                    val targets = targetPlants.map { it.id }
                    scope.launch { vm.persistReminder(r, targets) }
                }
            )

            // ── Active reminders (the only place they can be changed) ──
            // The create card above was the only reminder surface in the
            // app, and it could not even list what it had created:
            // `ReminderDao.updateReminder` / `deleteReminder` had no
            // callers at all. The journal is where a reminder is created,
            // so it is where it is edited and removed.
            if (activeReminders.isNotEmpty()) {
                Text("Recordatorios activos", style = MaterialTheme.typography.titleMedium)
                activeReminders.forEach { reminder ->
                    ReminderRow(
                        reminder = reminder,
                        plantName = reminder.plantId?.let { plantNames[it] },
                        onEdit = { editingReminder = reminder },
                        onDelete = { deleteReminderConfirmation.request(reminder) }
                    )
                }
            }

            // A refused edit is saved but will not ring, and a failed delete
            // leaves the reminder armed. Both are silent otherwise.
            reminderVm.saveError?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // ── List ─────────────────────────────────────────────────
            Text("Registro de eventos", style = MaterialTheme.typography.titleMedium)
            if (vm.events.isEmpty()) {
                SolidPanel {
                    Column(
                        modifier = Modifier.padding(32.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Sin eventos registrados", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                vm.events.forEach { e ->
                    EventRow(
                        event = e,
                        plantName = plantNames[e.plantId],
                        onEdit = { editingEvent = e },
                        onDelete = { deleteConfirmation.request(e) }
                    )
                }
            }
        }
    }

    // Armed by a tap, confirmed by the shared dialog: the row is only written
    // once the grower has seen which reminder they picked.
    editingReminder?.let { reminder ->
        EditReminderDialog(
            reminder = reminder,
            accent = accent,
            onDismiss = { editingReminder = null },
            onSave = { edited ->
                scope.launch { reminderVm.updateReminder(edited) { editingReminder = null } }
            }
        )
    }

    // Same pattern for an event. The row is released only once the write reports
    // success, so a refused update leaves the dialog open with the grower's edits
    // still in it rather than dropping them on the floor.
    editingEvent?.let { event ->
        EditEventDialog(
            event = event,
            plants = plants,
            accent = accent,
            onDismiss = { editingEvent = null },
            onSave = { edited ->
                scope.launch { vm.updateEvent(edited) { editingEvent = null } }
            }
        )
    }
}

/** Form field holder built by the dynamic form. */
data class EventFormData(
    val notes: String = "",
    val amount: Float? = null,
    val ph: Float? = null,
    val ec: Float? = null,
    val temperature: Float? = null,
    val humidity: Float? = null,
    val height: Float? = null,
    val lampDistance: Float? = null,
    val vpd: Float? = null,
    val trainingType: String? = null,
    val trichomeMaturity: String? = null
) {
    fun toGrowEvent(plantId: Long, groupId: String?): GrowEvent = GrowEvent(
        plantId = plantId,
        groupId = groupId,
        eventType = eventType.storageKey,
        timestamp = System.currentTimeMillis(),
        notes = notes.trim().ifBlank { null },
        temperature = temperature,
        humidity = humidity,
        ph = ph,
        ec = ec,
        amount = amount,
        height = height,
        lampDistance = lampDistance,
        trainingType = trainingType,
        vpd = vpd,
        trichomeMaturity = trichomeMaturity
    )

    var eventType: EventType = EventType.IRRIGATION
}

@Composable
private fun resolveTargets(
    plants: List<Plant>,
    plantId: Long?,
    multiSelection: Boolean,
    selectedPlantIds: Set<Long>
): List<Plant> {
    if (plantId != null) return plants.filter { it.id == plantId }
    return when {
        multiSelection -> plants.filter { selectedPlantIds.contains(it.id) }
        selectedPlantIds.isEmpty() -> emptyList()
        else -> plants.filter { selectedPlantIds.contains(it.id) }
    }
}

