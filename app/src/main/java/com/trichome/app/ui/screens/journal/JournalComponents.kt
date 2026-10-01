package com.trichome.app.ui.screens.journal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trichome.app.data.entity.Plant
import com.trichome.app.data.entity.Reminder
import com.trichome.app.model.EventType
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.components.accentButtonColors
import com.trichome.app.worker.ReminderSchedulerWorker

@Composable
fun DynamicEventForm(
    eventType: EventType,
    accent: Color,
    onSave: (EventFormData) -> Unit
) {
    var data by remember(eventType) { mutableStateOf(EventFormData().apply { this.eventType = eventType }) }
    var showError by remember(eventType) { mutableStateOf(false) }

    // Reset internal holder when the type changes.
    LaunchedEffect(eventType) {
        data = EventFormData().apply { this.eventType = eventType }
        showError = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (eventType) {
            EventType.IRRIGATION, EventType.FERTILIZATION, EventType.FLUSHING -> {
                NumberField("Cantidad (ml/L)", data.amount) { data = data.copy(amount = it) }
                NumberField("pH", data.ph) { data = data.copy(ph = it) }
                NumberField("EC (mS/cm)", data.ec) { data = data.copy(ec = it) }
                NotesField(data.notes) { data = data.copy(notes = it) }
            }
            EventType.TEMPERATURE_HUMIDITY -> {
                NumberField("Temperatura (°C)", data.temperature) { data = data.copy(temperature = it) }
                NumberField("Humedad (%)", data.humidity) { data = data.copy(humidity = it) }
                NumberField("VPD (kPa)", data.vpd) { data = data.copy(vpd = it) }
                NotesField(data.notes) { data = data.copy(notes = it) }
            }
            EventType.HEIGHT -> {
                NumberField("Altura (cm)", data.height) { data = data.copy(height = it) }
                NotesField(data.notes) { data = data.copy(notes = it) }
            }
            EventType.LAMP_DISTANCE -> {
                NumberField("Distancia de Lámpara (cm)", data.lampDistance) { data = data.copy(lampDistance = it) }
                NotesField(data.notes) { data = data.copy(notes = it) }
            }
            EventType.VPD -> {
                NumberField("VPD (kPa)", data.vpd) { data = data.copy(vpd = it) }
                NotesField(data.notes) { data = data.copy(notes = it) }
            }
            EventType.PRUNING, EventType.TRAINING, EventType.DEFOLIATION, EventType.TRANSPLANT -> {
                if (eventType == EventType.TRAINING) {
                    TextField(
                        value = data.trainingType.orEmpty(),
                        onValueChange = { data = data.copy(trainingType = it.ifBlank { null }) },
                        label = { Text("Tipo de entrenamiento") }
                    )
                }
                NotesField(data.notes) { data = data.copy(notes = it) }
            }
            EventType.PEST_CONTROL -> {
                NotesField(data.notes) { data = data.copy(notes = it) }
            }
            EventType.TRICHOME_CHECK -> {
                TextField(
                    value = data.trichomeMaturity.orEmpty(),
                    onValueChange = { data = data.copy(trichomeMaturity = it.ifBlank { null }) },
                    label = { Text("Madurez (transparente/lechoso/ámbar)") }
                )
                NotesField(data.notes) { data = data.copy(notes = it) }
            }
            EventType.HARVEST -> {
                NumberField("Cantidad (g)", data.amount) { data = data.copy(amount = it) }
                NotesField(data.notes) { data = data.copy(notes = it) }
            }
            EventType.BREEDING -> {
                NotesField(data.notes) { data = data.copy(notes = it) }
            }
            EventType.DIAGNOSIS -> {
                NotesField(data.notes) { data = data.copy(notes = it) }
            }
        }

        if (showError) {
            Text(
                "Selecciona al menos una planta destino",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Button(
            onClick = {
                showError = !data.hasAnyValue()
                if (data.hasAnyValue()) onSave(data)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = accentButtonColors(accent)
        ) {
            Text("Guardar evento")
        }
    }
}

private fun EventFormData.hasAnyValue(): Boolean =
    notes.isNotBlank() ||
        amount != null || ph != null || ec != null || temperature != null ||
        humidity != null || height != null || lampDistance != null || vpd != null ||
        trainingType != null || trichomeMaturity != null

@Composable
private fun NumberField(
    label: String,
    value: Float?,
    onChange: (Float?) -> Unit
) {
    var text by remember(value) { mutableStateOf(value?.let { if (it == it.toInt().toFloat()) it.toInt().toString() else it.toString() }.orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            onChange(raw.toFloatOrNull())
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
private fun NotesField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Notas") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2
    )
}

@Composable
private fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

/**
 * Quick reminder creator bound to the currently selected plants.
 * After creation the reminder (WorkManager) chain is re-synced.
 */
@Composable
fun ReminderQuickCard(
    accent: Color,
    targetPlants: List<Plant>,
    onCreated: () -> Unit,
    onPersist: (Reminder) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf(7) }
    var hour by remember { mutableStateOf(9) }
    var minute by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf(false) }

    SolidPanel {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("⏰ Crear recordatorio recurrente", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; error = false },
                label = { Text("Título (ej. Riego semanal)") },
                isError = error,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Intervalo: cada $interval día(s)", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = interval.toFloat(),
                        onValueChange = { interval = it.toInt().coerceIn(1, 60) },
                        valueRange = 1f..60f,
                        steps = 58
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hora: %02d:%02d".format(hour, minute), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = hour.toFloat(),
                        onValueChange = { hour = it.toInt().coerceIn(0, 23) },
                        valueRange = 0f..23f,
                        steps = 23
                    )
                }
                // `minute` used to be declared and printed but never bound to a
                // control, so every reminder this card created fired on the hour
                // no matter what the grower believed they had picked.
                Column(modifier = Modifier.weight(1f)) {
                    Text("Minuto: %02d".format(minute), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = minute.toFloat(),
                        onValueChange = { minute = it.toInt().coerceIn(0, 59) },
                        valueRange = 0f..59f,
                        steps = 58
                    )
                }
            }
            Text(
                "Plantas: " + targetPlants.joinToString { it.name }.ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = {
                    if (title.isBlank()) { error = true; return@Button }
                    val plantId = targetPlants.firstOrNull()?.id
                    val reminder = Reminder(
                        plantId = plantId,
                        title = title.trim(),
                        message = "Recordatorio: ${title.trim()}",
                        recurrenceType = "custom",
                        recurrenceIntervalDays = interval,
                        reminderTime = (hour * 3600_000L + minute * 60_000L).coerceIn(0L, 86_399_999L)
                    )
                    onPersist(reminder)
                    onCreated() // resync WorkManager chain
                    title = ""
                },
                modifier = Modifier.fillMaxWidth(),
                colors = accentButtonColors(accent)
            ) {
                Text("Crear recordatorio")
            }
        }
    }
}