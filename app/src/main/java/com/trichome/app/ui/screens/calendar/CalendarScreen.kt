package com.trichome.app.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.trichome.app.data.entity.GrowEvent
import com.trichome.app.data.entity.Plant
import com.trichome.app.data.entity.Reminder
import com.trichome.app.ui.components.AppTopBar
import com.trichome.app.ui.components.FloatingOrbBackground
import com.trichome.app.ui.components.GlassCard
import com.trichome.app.ui.components.GlassmorphicBottomBar
import com.trichome.app.ui.components.GlassChip
import com.trichome.app.ui.screens.journal.EventTypeUi
import com.trichome.app.ui.screens.plant.dateShort
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.viewmodel.CalendarViewModel
import com.trichome.app.viewmodel.appViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Monthly grow calendar: event/task markers per day, plant filters and a
 * day-detail list. Data comes from [CalendarViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    navController: NavHostController,
    themeState: TrichomeThemeState
) {
    val vm = appViewModel { CalendarViewModel(it) }
    val accent = themeState.colorScheme().primary

    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }
    var filterPlantId by remember { mutableStateOf<Long?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }

    val viewEvents = remember(vm.events, filterPlantId) {
        if (filterPlantId == null) vm.events
        else vm.events.filter { it.plantId == filterPlantId }
    }

    LaunchedEffect(month, filterPlantId) {
        val from = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        vm.loadMonth(from, to)
    }

    // Plant names lookup. Collected as state: sampling `.value` in composition
    // would leave the calendar showing stale names for the whole session.
    val plants by vm.plants.collectAsState()
    val plantNames = remember(plants) {
        plants.associate { it.id to it.name }
    }

    val eventsByDay = remember(viewEvents) {
        viewEvents.groupBy { e ->
            java.time.Instant.ofEpochMilli(e.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }
    val remindersByDay = remember(vm.reminders, month) {
        vm.reminderDaysInRange(
            month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            month.plusMonths(1).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        ).groupBy { d ->
            java.time.Instant.ofEpochMilli(d).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }

    Box {
        FloatingOrbBackground(accentColor1 = accent, accentColor2 = themeState.accentColor)

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                AppTopBar(
                    title = "📅 Calendario de Cultivo",
                    onNavigateBack = { navController.popBackStack() }
                )
            },
            floatingActionButton = {
                // The calendar was read-only: there was no way to add anything,
                // so the month markers could only ever reflect the journal.
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    containerColor = accent
                ) {
                    Icon(Icons.Default.Add, "Añadir evento o recordatorio")
                }
            },
            bottomBar = {
                GlassmorphicBottomBar("calendar", { navController.navigate(it) }, themeState)
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Month navigation ─────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { month = month.minusMonths(1) }) { Text("◀") }
                    Text(
                        month.month.name.lowercase().replaceFirstChar { it.uppercase() } + " " + month.year,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    TextButton(onClick = { month = month.plusMonths(1) }) { Text("▶") }
                }

                // ── Filter by plant ──────────────────────────────────────
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        GlassChip(
                            "🌱 Todas",
                            selected = filterPlantId == null,
                            onClick = { filterPlantId = null }
                        )
                    }
                    items(plants, key = { it.id }) { p ->
                        GlassChip(
                            p.name,
                            selected = filterPlantId == p.id,
                            onClick = { filterPlantId = p.id }
                        )
                    }
                }

                // ── Grid ─────────────────────────────────────────────────
                MonthGrid(
                    month = month,
                    selectedDay = selectedDay,
                    eventsByDay = eventsByDay,
                    remindersByDay = remindersByDay,
                    accent = accent,
                    onSelectDay = { selectedDay = it }
                )

    if (showAddSheet) {
        AddCalendarEntryDialog(
            day = selectedDay,
            plants = plants,
            accent = accent,
            onDismiss = { showAddSheet = false },
            onSaveEvent = { plantId, eventType, notes ->
                val at = selectedDay.atTime(12, 0)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                vm.addEvent(
                    GrowEvent(
                        plantId = plantId,
                        eventType = eventType,
                        notes = notes,
                        timestamp = at
                    )
                ) { ok -> if (ok) showAddSheet = false }
            },
            onSaveReminder = { plantId, title ->
                vm.addReminder(
                    Reminder(
                        plantId = plantId,
                        title = title,
                        message = "Recordatorio: $title",
                        recurrenceType = "none",
                        recurrenceIntervalDays = 0,
                        reminderTime = 9 * 3600_000L,
                        isActive = true
                    )
                ) { ok -> if (ok) showAddSheet = false }
            }
        )
    }
                // ── Selected day detail ──────────────────────────────────
                GlassCard(accentColor = accent, glassOpacity = themeState.glassTokens.glassOpacity) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Detalle · ${selectedDay.dayOfMonth}/${selectedDay.monthValue}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        val dayEvents = eventsByDay[selectedDay].orEmpty()
                        val dayReminders = remindersByDay[selectedDay].orEmpty()
                        if (dayEvents.isEmpty() && dayReminders.isEmpty()) {
                            Text("Sin eventos ni tareas este día", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            dayEvents.forEach { e ->
                                Text(
                                    "• ${EventTypeUi.labelResolved(e.eventType)} — ${plantNames[e.plantId] ?: "Planta"} — ${hm(e.timestamp)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            dayReminders.forEach { r ->
                                Text(
                                    "⏰ Tarea recordatorio — ${hm(r)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // ── Month list ───────────────────────────────────────────
                Text("Registros del mes", style = MaterialTheme.typography.titleMedium)
                val monthEvents = viewEvents.sortedByDescending { it.timestamp }
                if (monthEvents.isEmpty()) {
                    Text("Sin registros este mes", style = MaterialTheme.typography.bodyMedium)
                } else {
                    monthEvents.take(30).forEach { e ->
                        MonthEventRow(e, plantName = plantNames[e.plantId] ?: "Planta")
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selectedDay: LocalDate,
    eventsByDay: Map<LocalDate, List<GrowEvent>>,
    remindersByDay: Map<LocalDate, List<Long>>,
    accent: Color,
    onSelectDay: (LocalDate) -> Unit
) {
    val firstDow = month.atDay(1).dayOfWeek // DayOfWeek
    val leadOffset = (firstDow.value - 1) // Monday-first
    val daysInMonth = month.lengthOfMonth()
    val totalCells = ((leadOffset + daysInMonth + 6) / 7) * 7

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Weekday header
        Row {
            DayOfWeek.entries.take(7).forEach { dow ->
                Text(
                    dow.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
                        .take(2),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        // Week rows
        var currentDay = 1
        for (week in 0 until (totalCells / 7)) {
            Row {
                for (col in 0..6) {
                    val index = week * 7 + col
                    val isCurrentMonth = index >= leadOffset && currentDay <= daysInMonth
                    val day = if (isCurrentMonth) {
                        month.atDay(currentDay).also { currentDay++ }
                    } else null

                    DayCell(
                        day = day,
                        isSelected = day == selectedDay,
                        eventCount = day?.let { eventsByDay[it].orEmpty().size } ?: 0,
                        reminderCount = day?.let { remindersByDay[it].orEmpty().size } ?: 0,
                        accent = accent,
                        onClick = { if (day != null) onSelectDay(day) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate?,
    isSelected: Boolean,
    eventCount: Int,
    reminderCount: Int,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .aspectRatio(0.95f)
            .clip(shape)
            .background(if (isSelected) accent.copy(alpha = 0.28f) else Color.Transparent)
            .clickable(enabled = day != null, onClick = onClick)
            .padding(6.dp)
    ) {
        if (day != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${day.dayOfMonth}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) accent else MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (eventCount > 0) DayDot(accent, eventCount)
                    if (reminderCount > 0) DayDot(Color(0xFF7C4DFF), reminderCount)
                }
            }
        }
    }
}

@Composable
private fun DayDot(color: Color, count: Int) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color)
    )
}

@Composable
private fun MonthEventRow(event: GrowEvent, plantName: String) {
    GlassCard(accentColor = MaterialTheme.colorScheme.primary, cornerRadius = 12) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(EventTypeUi.labelResolved(event.eventType), style = MaterialTheme.typography.titleSmall)
                Text(
                    "$plantName · ${dateShort(event.timestamp)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(hm(event.timestamp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun hm(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalTime()
        .let { "%02d:%02d".format(it.hour, it.minute) }
/**
 * Add sheet for a calendar entry.
 *
 * A plant is required rather than defaulted: an event with no plant is
 * invisible to every plant filter, which is how "the calendar lost my entry"
 * bugs start. The event type and the text are validated before handing the
 * write to the view model, which reports the real insert outcome.
 */
@Composable
private fun AddCalendarEntryDialog(
    day: LocalDate,
    plants: List<Plant>,
    accent: Color,
    onDismiss: () -> Unit,
    onSaveEvent: (plantId: Long, eventType: String, notes: String) -> Unit,
    onSaveReminder: (plantId: Long, title: String) -> Unit
) {
    var isReminder by remember { mutableStateOf(false) }
    var plantId by remember { mutableStateOf<Long?>(null) }
    var typeIndex by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isReminder) "Recordatorio · ${day.dayOfMonth}/${day.monthValue}"
                else "Evento · ${day.dayOfMonth}/${day.monthValue}"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("Evento", selected = !isReminder, onClick = { isReminder = false })
                    GlassChip("Recordatorio", selected = isReminder, onClick = { isReminder = true })
                }

                if (plants.isEmpty()) {
                    Text(
                        "No hay plantas registradas. Crea una planta antes de añadir al calendario.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text("Planta", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(plants, key = { it.id }) { p ->
                            GlassChip(p.name, selected = plantId == p.id, onClick = { plantId = p.id })
                        }
                    }
                }

                if (!isReminder) {
                    Text("Tipo", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(EventTypeUi.ALL.size) { i ->
                            val type = EventTypeUi.ALL[i]
                            GlassChip(
                                EventTypeUi.label(type),
                                selected = typeIndex == i,
                                onClick = { typeIndex = i }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; problem = null },
                    label = { Text(if (isReminder) "Título" else "Descripción") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                problem?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pid = plantId
                    when {
                        plants.isEmpty() -> problem = "Primero registra una planta."
                        pid == null -> problem = "Elige a qué planta aplica."
                        text.isBlank() -> problem = "Escribe una descripción."
                        else -> {
                            val notes = text.trim()
                            if (isReminder) onSaveReminder(pid, notes)
                            else onSaveEvent(pid, EventTypeUi.ALL[typeIndex].storageKey, notes)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
