package com.trichome.app.ui.screens.journal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.trichome.app.ui.components.FloatingOrbBackground
import com.trichome.app.ui.components.GlassCard
import com.trichome.app.ui.components.GlassChip
import com.trichome.app.ui.components.GlassmorphicBottomBar
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.viewmodel.JournalViewModel
import com.trichome.app.viewmodel.appViewModel
import com.trichome.app.worker.ReminderSchedulerWorker
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Journal (Bitácora). Global entry point supports multi-plant selection
 * (shared [groupId]) and all 16 event types through the dynamic glass form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    navController: NavHostController,
    themeState: TrichomeThemeState,
    plantId: Long? = null
) {
    val vm = appViewModel { JournalViewModel(it) }
    val accent = themeState.colorScheme().primary
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(plantId) { vm.loadEvents(plantId) }

    var selectedType by remember { mutableStateOf(EventType.IRRIGATION) }
    // Multi-plant selection (enabled in global mode)
    var multiSelection by remember { mutableStateOf(plantId == null) }
    var selectedPlantIds by remember { mutableStateOf(setOf<Long>()) }

    Box {
        FloatingOrbBackground(accentColor1 = accent, accentColor2 = themeState.accentColor)

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(if (plantId != null) "Bitácora de planta" else "📒 Bitácora") },
                    navigationIcon = {
                        if (plantId != null) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (plantId == null) {
                    GlassmorphicBottomBar("journal", { navController.navigate(it) }, themeState)
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
                    GlassCard(accentColor = accent, glassOpacity = themeState.glassTokens.glassOpacity) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = multiSelection, onCheckedChange = { multiSelection = it })
                                Text("Selección múltiple (grupo)", style = MaterialTheme.typography.bodyLarge)
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    GlassChip(
                                        "🌱 Todas",
                                        selected = selectedPlantIds.isEmpty() && !multiSelection,
                                        onClick = { selectedPlantIds = emptySet() }
                                    )
                                }
                                items(vm.plants.value, key = { it.id }) { p ->
                                    GlassChip(
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

                // ── Dynamic event form (horizontal carousel of types) ───
                GlassCard(accentColor = accent, glassOpacity = themeState.glassTokens.glassOpacity) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Tipo de evento", style = MaterialTheme.typography.titleSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(EventTypeUi.ALL) { type ->
                                GlassChip(
                                    EventTypeUi.label(type),
                                    selected = selectedType == type,
                                    onClick = { selectedType = type }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        val targetPlants = resolveTargets(vm.plants.value, plantId, multiSelection, selectedPlantIds)
                        Text(
                            "Aplica a: " + targetPlants.joinToString { it.name }.ifBlank { "—" },
                            style = MaterialTheme.typography.bodySmall
                        )

                        DynamicEventForm(
                            eventType = selectedType,
                            accent = accent,
                            backdrop = themeState.glassTokens.glassOpacity,
                            onSave = { form ->
                                val groupId = if (targetPlants.size > 1) UUID.randomUUID().toString() else null
                                scope.launch {
                                    targetPlants.forEach { p ->
                                        vm.addEvent(form.toGrowEvent(p.id, groupId))
                                    }
                                }
                                // keep the journal fresh
                                scope.launch { vm.loadEvents(plantId) }
                            }
                        )
                    }
                }

                // ── Reminder quick-create ────────────────────────────────
                ReminderQuickCard(
                    accent = accent,
                    targetPlants = resolveTargets(vm.plants.value, plantId, multiSelection, selectedPlantIds),
                    onCreated = {
                        ReminderSchedulerWorker.syncNow(context)
                    },
                    onPersist = { r -> scope.launch { vm.persistReminder(r) } }
                )

                // ── List ─────────────────────────────────────────────────
                Text("Registro de eventos", style = MaterialTheme.typography.titleMedium)
                if (vm.events.isEmpty()) {
                    GlassCard(accentColor = accent) {
                        Column(
                            modifier = Modifier.padding(32.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Sin eventos registrados", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                } else {
                    vm.events.forEach { e ->
                        EventRow(e, onDelete = { scope.launch { vm.deleteEvent(e) } })
                    }
                }
            }
        }
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

@Composable
private fun EventRow(event: GrowEvent, onDelete: () -> Unit) {
    GlassCard(
        glassOpacity = 0.12f,
        accentColor = MaterialTheme.colorScheme.primary,
        cornerRadius = 14
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    EventTypeUi.labelResolved(event.eventType),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    buildString {
                        append(com.trichome.app.ui.screens.plant.dateShort(event.timestamp))
                        append(if (event.groupId != null) " · grupo" else "")
                        event.notes?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error) }
        }
    }
}