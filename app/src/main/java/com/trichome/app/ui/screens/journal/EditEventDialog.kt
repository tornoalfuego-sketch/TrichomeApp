package com.trichome.app.ui.screens.journal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trichome.app.data.entity.GrowEvent
import com.trichome.app.data.entity.Plant
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.components.accentTextButtonColors
// Explicit import, not the `ui.components.*` wildcard: the `material3.*` above
// brings in a `SelectableChip` that is private to the Material3 file, so the
// app's own chip has to be named to win. `CalendarScreen` resolves the same
// collision by importing the two Material3 pieces it needs individually.
import com.trichome.app.ui.components.SelectableChip
import java.time.ZoneId

/**
 * Editor for a journal event.
 *
 * The counterpart to [EditReminderDialog], and it exists for the same reason:
 * `EventDao.updateEvent` had no caller, so an event could be recorded and
 * deleted but never corrected. A grower who logged the wrong pH, the wrong day
 * or the wrong plant had no way to fix the row except deleting it and losing the
 * rest of it with it.
 *
 * ## What this deliberately does not show
 *
 * `GrowEvent` has twenty-four columns. The form exposes the thirteen the journal
 * itself records through [DynamicEventForm], and carries the other eleven
 * untouched through [EventEditing.applyTo] -- the `id` and `groupId` that give
 * the row its identity, `imagePath`, `nutrientN/P/K`, `defoliationLevel`,
 * `diagnosisResult`, `diagnosisCertainty` and `isActive`. Exposing the nutrient
 * and defoliation columns would be inventing UI for values no create path in
 * the app ever writes; clearing them silently would be data loss. They stay on
 * the row, which is the only option that is neither.
 *
 * ## Scroll discipline
 *
 * One scroll owner per axis. The body column scrolls, and none of the rows inside
 * it does -- the type and plant pickers are `LazyRow`s, which scroll sideways on
 * their own axis and never contribute a vertical one. A `verticalScroll` inside
 * this body would be measured with an infinite maximum height and throw, the
 * failure [com.trichome.app.ui.screens.breeding.ScrollOwnershipTest] exists for.
 */
@Composable
fun EditEventDialog(
    event: GrowEvent,
    plants: List<Plant>,
    accent: Color,
    onDismiss: () -> Unit,
    onSave: (GrowEvent) -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    // Keyed on the id, exactly like `EditReminderDialog`: this composable is
    // reused across rows, and a bare `remember { }` would keep the first event's
    // values for the rest of the session.
    var draft by remember(event.id) { mutableStateOf(EventEditing.draftOf(event, zone)) }
    var problem by remember(event.id) { mutableStateOf<String?>(null) }
    // The day the row was stored on, held separately so the day slider can measure
    // a shift from it instead of from today.
    val storedEpochDay = remember(event.id) { EventEditing.draftOf(event, zone).epochDay }
    // The numeric fields are text while the user types and floats on the way out,
    // so "6." and "6,5" are not silently discarded mid-keystroke. Seeded from the
    // stored row, because an editor that opened blank would write nulls over real
    // measurements the first time the grower pressed Save without touching them.
    var temperatureText by remember(event.id) { mutableStateOf(event.temperature.orEmptyText()) }
    var humidityText by remember(event.id) { mutableStateOf(event.humidity.orEmptyText()) }
    var phText by remember(event.id) { mutableStateOf(event.ph.orEmptyText()) }
    var ecText by remember(event.id) { mutableStateOf(event.ec.orEmptyText()) }
    var amountText by remember(event.id) { mutableStateOf(event.amount.orEmptyText()) }
    var heightText by remember(event.id) { mutableStateOf(event.height.orEmptyText()) }
    var lampDistanceText by remember(event.id) { mutableStateOf(event.lampDistance.orEmptyText()) }
    var vpdText by remember(event.id) { mutableStateOf(event.vpd.orEmptyText()) }

    val knownPlantIds = remember(plants) { plants.map { it.id }.toSet() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar evento") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ── Type ────────────────────────────────────────────
                Text("Tipo de evento", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(EventTypeUi.ALL, key = { it.storageKey }) { type ->
                        SelectableChip(
                            EventTypeUi.label(type),
                            selected = draft.eventType == type.storageKey,
                            onClick = { draft = draft.copy(eventType = type.storageKey); problem = null }
                        )
                    }
                }

                // ── Plant ───────────────────────────────────────────
                Text("Planta", style = MaterialTheme.typography.titleSmall)
                if (plants.isEmpty()) {
                    Text(
                        "No hay plantas registradas, así que este evento no se puede " +
                            "reasignar. Se guardará en la planta que ya tenía.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(plants, key = { it.id }) { plant ->
                            SelectableChip(
                                plant.name,
                                selected = draft.plantId == plant.id,
                                onClick = { draft = draft.copy(plantId = plant.id); problem = null }
                            )
                        }
                    }
                }

                // ── When ────────────────────────────────────────────
                Text("Fecha y hora", style = MaterialTheme.typography.titleSmall)
                SolidPanel(cornerRadius = 12) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            "${EventEditing.dateLabel(draft.epochDay)} · " +
                                EventEditing.timeLabel(draft.minuteOfDay),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Día (máx. ${EventEditing.MAX_DAY_SHIFT} días)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // The shift is measured from the day the row was stored on,
                        // not from today: an entry from three weeks ago opens with
                        // the slider at 0, not at -21. The destination goes through
                        // `withDayShift` so the bound is the helper's, not the
                        // composable's.
                        Slider(
                            value = EventEditing.dayShiftFor(draft, storedEpochDay).toFloat(),
                            onValueChange = {
                                draft = EventEditing.withDayShift(draft, storedEpochDay, it.toInt())
                                problem = null
                            },
                            valueRange = -EventEditing.MAX_DAY_SHIFT.toFloat()..
                                EventEditing.MAX_DAY_SHIFT.toFloat(),
                            steps = EventEditing.MAX_DAY_SHIFT * 2 - 1
                        )
                        Text("Hora", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = draft.minuteOfDay.toFloat(),
                            onValueChange = {
                                draft = draft.copy(
                                    minuteOfDay = it.toInt().coerceIn(0, EventEditing.MAX_MINUTE_OF_DAY)
                                )
                                problem = null
                            },
                            valueRange = 0f..EventEditing.MAX_MINUTE_OF_DAY.toFloat(),
                            steps = EventEditing.MAX_MINUTE_OF_DAY - 1
                        )
                    }
                }

                // ── Notes ───────────────────────────────────────────
                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = { draft = draft.copy(notes = it); problem = null },
                    label = { Text("Notas") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                // ── Measurements ────────────────────────────────────
                // Every value the journal itself records for this row. Seeding is
                // the point: an editor that opened with 6.0 pH on a row that read
                // 6.4 would write 6.0 the moment the grower pressed Save. The text
                // is seeded from the row once, above, and then belongs to the user.
                Text("Mediciones", style = MaterialTheme.typography.titleSmall)
                MeasuredField("Temperatura (°C)", temperatureText) {
                    temperatureText = it; draft = draft.copy(temperature = it.toFloatOrNull())
                }
                MeasuredField("Humedad (%)", humidityText) {
                    humidityText = it; draft = draft.copy(humidity = it.toFloatOrNull())
                }
                MeasuredField("pH", phText) {
                    phText = it; draft = draft.copy(ph = it.toFloatOrNull())
                }
                MeasuredField("EC (mS/cm)", ecText) {
                    ecText = it; draft = draft.copy(ec = it.toFloatOrNull())
                }
                MeasuredField("Cantidad", amountText) {
                    amountText = it; draft = draft.copy(amount = it.toFloatOrNull())
                }
                MeasuredField("Altura (cm)", heightText) {
                    heightText = it; draft = draft.copy(height = it.toFloatOrNull())
                }
                MeasuredField("Distancia a la lámpara (cm)", lampDistanceText) {
                    lampDistanceText = it; draft = draft.copy(lampDistance = it.toFloatOrNull())
                }
                MeasuredField("VPD (kPa)", vpdText) {
                    vpdText = it; draft = draft.copy(vpd = it.toFloatOrNull())
                }

                OutlinedTextField(
                    value = draft.trainingType.orEmpty(),
                    onValueChange = { draft = draft.copy(trainingType = it); problem = null },
                    label = { Text("Tipo de entrenamiento") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.trichomeMaturity.orEmpty(),
                    onValueChange = { draft = draft.copy(trichomeMaturity = it); problem = null },
                    label = { Text("Madurez de tricomas") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // The same discipline as the calendar's add sheet: the reason is
                // shown, not thrown and not swallowed.
                problem?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val rejection = EventEditing.validate(draft, knownPlantIds)
                    if (rejection != null) {
                        problem = rejection
                    } else {
                        onSave(EventEditing.applyTo(draft, event, zone))
                    }
                },
                colors = accentTextButtonColors(MaterialTheme.colorScheme, accent)
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/**
 * A measurement field that shows the stored value and can be typed into.
 *
 * A half-typed "6," has to survive a keystroke, so the text is the state and the
 * float is derived on the way into the draft. Seeding happens once, in the
 * caller's `remember(event.id)`, and never here: a field that fell back to the
 * stored value whenever its own text was blank would resurrect 6.4 the instant
 * the grower cleared the box, which is the same half-populated overwrite this
 * dialog exists to prevent.
 */
@Composable
private fun MeasuredField(
    label: String,
    text: String,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

/** A stored `6.0` reads as `6`; a stored `6.42` keeps its decimals. */
private fun formatMeasurement(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()

/** The initial text of a measurement field: blank when the row never recorded one. */
private fun Float?.orEmptyText(): String = this?.let(::formatMeasurement).orEmpty()

/**
 * One event row, with the edit action that was missing.
 *
 * Takes no accent: the row has one action and one destructive action, and the
 * delete glyph is tinted `error` on purpose. See [ReminderRow] for the same
 * argument about not carrying a colour this row has nothing to paint.
 */
@Composable
fun EventRow(
    event: GrowEvent,
    plantName: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    SolidPanel(cornerRadius = 14) {
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
                        if (plantName != null) append(" · $plantName")
                        if (event.groupId != null) append(" · grupo")
                        event.notes?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                // The measurements, so the row says what was recorded and the
                // editor is reached for the right reason.
                val measurements = listOfNotNull(
                    event.temperature?.let { "%.1f °C".format(it) },
                    event.humidity?.let { "%.0f %%".format(it) },
                    event.ph?.let { "pH %.2f".format(it) },
                    event.ec?.let { "EC %.2f".format(it) },
                    event.amount?.let { formatMeasurement(it) },
                    event.height?.let { "${formatMeasurement(it)} cm" }
                )
                if (measurements.isNotEmpty()) {
                    Text(
                        measurements.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // `Icons.Filled.x`, not the bare extension: the `material3.*` wildcard
            // also exposes an `Icons`, so the fully qualified property form is
            // ambiguous here. `JournalScreen` and `ReminderComponents` reach these
            // two glyphs the same way.
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Editar evento") }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    "Eliminar evento",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
