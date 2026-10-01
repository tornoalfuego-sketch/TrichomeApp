package com.trichome.app.ui.screens.journal

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.trichome.app.data.entity.Reminder
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.components.accentTextButtonColors

/**
 * Editor for an existing reminder: title, time of day and recurrence.
 *
 * Every field is seeded from the row, never from a default. The create card
 * ([ReminderQuickCard]) can afford defaults because there is nothing to lose;
 * an editor that seeded `title = ""` would destroy the row the first time it
 * was opened, which is the same `remember`-seeding trap that already cost
 * `ProtocolScreen` a round of fixes in v1.1.0.
 *
 * The state is keyed on [Reminder.id] for the same reason: this composable is
 * reused across rows, and a plain `remember { }` would keep the first row's
 * values for the rest of the session.
 */
@Composable
fun EditReminderDialog(
    reminder: Reminder,
    accent: Color,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit
) {
    var draft by remember(reminder.id) { mutableStateOf(ReminderEditing.draftOf(reminder)) }
    var problem by remember(reminder.id) { mutableStateOf<String?>(null) }

    val selectedPreset = ReminderEditing.presetFor(draft)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar recordatorio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it); problem = null },
                    label = { Text("Título (ej. Riego semanal)") },
                    isError = problem != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Hora: ${ReminderEditing.timeLabel(draft.hour, draft.minute)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hora", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = draft.hour.toFloat(),
                            onValueChange = { draft = draft.copy(hour = it.toInt().coerceIn(0, 23)) },
                            valueRange = 0f..23f,
                            steps = 23
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Minuto", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = draft.minute.toFloat(),
                            onValueChange = { draft = draft.copy(minute = it.toInt().coerceIn(0, 59)) },
                            valueRange = 0f..59f,
                            steps = 58
                        )
                    }
                }

                Text("Repetición", style = MaterialTheme.typography.labelLarge)
                ReminderEditing.RECURRENCE_PRESETS.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { preset ->
                            FilterChip(
                                selected = selectedPreset == preset,
                                onClick = {
                                    draft = draft.copy(
                                        recurrenceType = preset.type,
                                        recurrenceIntervalDays = preset.intervalDays
                                    )
                                    problem = null
                                },
                                label = { Text(preset.labelEs) }
                            )
                        }
                    }
                }

                if (selectedPreset?.type == "custom") {
                    Text(
                        "Cada ${draft.recurrenceIntervalDays} día(s)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = draft.recurrenceIntervalDays.toFloat(),
                        onValueChange = {
                            draft = draft.copy(
                                recurrenceIntervalDays = it.toInt()
                                    .coerceIn(1, ReminderEditing.MAX_INTERVAL_DAYS)
                            )
                            problem = null
                        },
                        valueRange = 1f..ReminderEditing.MAX_INTERVAL_DAYS.toFloat(),
                        steps = ReminderEditing.MAX_INTERVAL_DAYS - 2
                    )
                }

                problem?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val rejection = ReminderEditing.validate(draft)
                    if (rejection != null) {
                        problem = rejection
                    } else {
                        onSave(ReminderEditing.applyTo(draft, reminder))
                    }
                },
                colors = accentTextButtonColors(MaterialTheme.colorScheme, accent)
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/**
 * One active reminder, with the two actions that were previously unreachable:
 * change it, or take it down for good.
 *
 * Takes no accent, deliberately. Its neighbour [ReminderQuickCard] keeps one
 * because it has an accent-filled button to spend it on; this row has only text
 * and two icon buttons, one of which is tinted `error` on purpose. The panel
 * edge is a neutral step of the surface (see `panelBorderColor`), so an accent
 * threaded in here would have had nothing to paint -- which is exactly the dead
 * argument this component used to carry.
 */
@Composable
fun ReminderRow(
    reminder: Reminder,
    plantName: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val draft = remember(reminder.id, reminder.reminderTime, reminder.recurrenceType, reminder.recurrenceIntervalDays) {
        ReminderEditing.draftOf(reminder)
    }
    val recurrence = ReminderEditing.presetFor(draft)

    SolidPanel {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("⏰ ${reminder.title}", style = MaterialTheme.typography.titleSmall)
                Text(
                    buildString {
                        append(ReminderEditing.timeLabel(draft.hour, draft.minute))
                        append(" · ")
                        append(recurrence?.labelEs ?: "Cada ${draft.recurrenceIntervalDays} día(s)")
                        if (plantName != null) append(" · $plantName")
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar recordatorio") }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    "Eliminar recordatorio",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
