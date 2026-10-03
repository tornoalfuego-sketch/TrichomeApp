package com.trichome.app.ui.screens.vpd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trichome.app.model.VpdCalculation
import com.trichome.app.model.VpdLogForm
import com.trichome.app.model.VpdLogFormValidator
import com.trichome.app.model.VpdLogOutcome
import com.trichome.app.model.VpdProvenance
import com.trichome.app.ui.components.accentTextButtonColors
import java.time.ZoneId

/**
 * "Registrar en Bitácora".
 *
 * ## What the dialog decides, and what it does not
 *
 * It decides **when**, **which plant**, **what the number is** and **where the number came
 * from**. It does not decide anything about the tent: a `grow_events` row belongs to a
 * plant and the plant belongs to a tent, so the tent is read and shown rather than asked
 * for and written. Writing a `tentId` onto the event would be a second source of truth for
 * a fact that already has one.
 *
 * ## The two provenances are a choice, not a detail
 *
 * The dialog opens on `CALCULATED`, because that is where the value came from — the grower
 * pressed a button on a calculator. Switching to `MEASURED` is the deliberate act of saying
 * "this number came off my own instrument", and it does two things: it changes the label, and
 * it makes the leaf-offset field irrelevant, because a measured reading has no offset behind
 * it.
 *
 * That is the whole reason the field is conditional. A measured reading with an offset
 * attached would imply a derivation that did not happen.
 *
 * ## Closing on confirm
 *
 * The caller clears the dialog before the write runs. `AGENTS.md` requires it and the repo
 * has the pattern twice already (`PlantMigrationDialog`'s migrate, `PlantEditDialog`'s save):
 * a dialog still on screen for the frame between the tap and the recomposition is a second
 * target for a double tap, and a double tap writes two journal rows.
 */
@Composable
fun VpdLogDialog(
    calculation: VpdCalculation?,
    tentName: String?,
    onDismiss: () -> Unit,
    onConfirm: (VpdLogForm) -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    val now = remember { java.time.Instant.now().atZone(zone) }

    var form by remember {
        mutableStateOf(
            VpdLogForm(
                // The date, the time and the values are all seeded from the calculation so the dialog
                // opens on what the grower just computed. Every pattern comes from
                // `VpdLogForm`, which is what keeps this composable free of literals.
                date = VpdLogForm.dateLabelEs(
                    dayOfMonth = now.dayOfMonth,
                    monthValue = now.monthValue,
                    year = now.year
                ),
                time = VpdLogForm.timeLabelEs(hour = now.hour, minute = now.minute),
                vpdKPa = VpdLogForm.decimalEs(
                    calculation?.leafVpdKPa,
                    VpdLogForm.VPD_FORMAT
                ),
                offsetC = VpdLogForm.decimalEs(
                    calculation?.offsetC,
                    VpdLogForm.ONE_DECIMAL_FORMAT
                ),
                airTemperatureC = VpdLogForm.decimalEs(
                    calculation?.airTemperatureC,
                    VpdLogForm.ONE_DECIMAL_FORMAT
                ),
                humidityPercent = VpdLogForm.decimalEs(
                    calculation?.relativeHumidityPercent,
                    VpdLogForm.ONE_DECIMAL_FORMAT
                ),
                provenance = VpdProvenance.CALCULATED
            )
        )
    }

    val outcome = VpdLogFormValidator.validate(form, zone)
    val accent = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(VpdLogFormValidator.TITLE_ES) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    VpdLogFormValidator.tentSentenceEs(tentName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                VpdLogField(
                    label = VpdLogFormValidator.DATE_LABEL_ES,
                    value = form.date,
                    onChange = { form = form.copy(date = it) },
                    keyboard = KeyboardType.Number
                )
                VpdLogField(
                    label = VpdLogFormValidator.TIME_LABEL_ES,
                    value = form.time,
                    onChange = { form = form.copy(time = it) },
                    keyboard = KeyboardType.Number
                )

                Text(
                    VpdLogFormValidator.PROVENANCE_HEADING_ES,
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(VpdProvenance.MEASURED, VpdProvenance.CALCULATED).forEach { option ->
                        FilterChip(
                            selected = form.provenance == option,
                            onClick = { form = form.copy(provenance = option) },
                            label = { Text(option.labelEs) }
                        )
                    }
                }
                Text(
                    form.provenance.explanationEs,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                VpdLogField(
                    label = VpdLogFormValidator.VALUE_LABEL_ES,
                    value = form.vpdKPa,
                    onChange = { form = form.copy(vpdKPa = it) },
                    keyboard = KeyboardType.Decimal
                )

                // Only a calculated value has an offset behind it. A measured reading shown
                // with one would imply a derivation that never happened.
                if (form.provenance == VpdProvenance.CALCULATED) {
                    VpdLogField(
                        label = VpdLogFormValidator.OFFSET_LABEL_ES,
                        value = form.offsetC,
                        onChange = { form = form.copy(offsetC = it) },
                        keyboard = KeyboardType.Decimal
                    )
                }

                VpdLogField(
                    label = VpdLogFormValidator.TEMPERATURE_LABEL_ES,
                    value = form.airTemperatureC,
                    onChange = { form = form.copy(airTemperatureC = it) },
                    keyboard = KeyboardType.Decimal
                )
                VpdLogField(
                    label = VpdLogFormValidator.HUMIDITY_LABEL_ES,
                    value = form.humidityPercent,
                    onChange = { form = form.copy(humidityPercent = it) },
                    keyboard = KeyboardType.Decimal
                )

                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { form = form.copy(notes = it) },
                    label = { Text(VpdLogFormValidator.NOTES_LABEL_ES) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (outcome is VpdLogOutcome.Invalid) {
                    Text(
                        outcome.problemEs,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(form) },
                enabled = outcome is VpdLogOutcome.Ready,
                colors = accentTextButtonColors(MaterialTheme.colorScheme, accent)
            ) { Text(VpdLogFormValidator.CONFIRM_LABEL_ES) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(VpdLogFormValidator.CANCEL_ES) }
        }
    )
}

/** One field of the dialog, with its label above the value. */
@Composable
private fun VpdLogField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth()
    )
}