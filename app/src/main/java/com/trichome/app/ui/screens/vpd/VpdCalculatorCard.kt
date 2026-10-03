package com.trichome.app.ui.screens.vpd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trichome.app.model.VpdCalculator
import com.trichome.app.model.VpdCalculatorField
import com.trichome.app.model.VpdCalculatorOutcome

/**
 * The VPD calculator's inputs and readout.
 *
 * ## Layout
 *
 * Three fields and a readout, in one panel. Not a dialog: the grower types a temperature
 * and a humidity and watches the deficit move, and a modal would hide the chart behind a
 * scrim for the whole time they are adjusting the last digit.
 *
 * The numeric keyboard is on all three fields, with a decimal separator for the two that
 * take one. `KeyboardType.Decimal` on some devices offers a keypad with a comma and no
 * minus, and [VpdCalculator.parse] accepts either separator, so a grower who gets one can
 * type the other and the parse still succeeds — that is the reason the parser normalises
 * rather than insisting on a dot.
 *
 * ## The readout
 *
 * When the inputs do not resolve, [VpdCalculatorOutcome.Invalid] names the field, and only
 * that field goes into its error state. Marking all three because one is wrong is how a
 * grower learns to ignore the red outline.
 *
 * ## Where the numbers come from
 *
 * Every figure here comes from [VpdCalculatorOutcome.Ready.display], which is Spanish and
 * carries [com.trichome.app.model.VpdProvenance.CALCULATED] on it. The card renders the
 * label and the explanation and never decides for itself whether the number is a
 * measurement — the readout of a calculator is a calculated value by construction, and the
 * card's job is to say so on the same line as the number rather than in a footnote.
 */
@Composable
fun VpdCalculatorCard(
    form: com.trichome.app.model.VpdCalculatorForm,
    outcome: VpdCalculatorOutcome,
    onAirTemperatureChange: (String) -> Unit,
    onHumidityChange: (String) -> Unit,
    onOffsetChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = form.airTemperature,
            onValueChange = onAirTemperatureChange,
            label = { Text(VpdCalculator.AIR_TEMPERATURE_LABEL_ES) },
            singleLine = true,
            isError = outcome is VpdCalculatorOutcome.Invalid &&
                outcome.field == VpdCalculatorField.AIR_TEMPERATURE,
            supportingText = if (outcome is VpdCalculatorOutcome.Invalid &&
                outcome.field == VpdCalculatorField.AIR_TEMPERATURE
            ) {
                { Text(outcome.problemEs) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = form.humidity,
            onValueChange = onHumidityChange,
            label = { Text(VpdCalculator.HUMIDITY_LABEL_ES) },
            singleLine = true,
            isError = outcome is VpdCalculatorOutcome.Invalid &&
                outcome.field == VpdCalculatorField.HUMIDITY,
            supportingText = if (outcome is VpdCalculatorOutcome.Invalid &&
                outcome.field == VpdCalculatorField.HUMIDITY
            ) {
                { Text(outcome.problemEs) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = form.offset,
            onValueChange = onOffsetChange,
            label = { Text(VpdCalculator.OFFSET_LABEL_ES) },
            singleLine = true,
            isError = outcome is VpdCalculatorOutcome.Invalid &&
                outcome.field == VpdCalculatorField.OFFSET,
            supportingText = {
                Text(
                    if (outcome is VpdCalculatorOutcome.Invalid &&
                        outcome.field == VpdCalculatorField.OFFSET
                    ) {
                        outcome.problemEs
                    } else {
                        VpdCalculator.OFFSET_HINT_ES
                    }
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        if (outcome is VpdCalculatorOutcome.Ready) {
            val display = outcome.display
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    VpdCalculator.LEAF_VPD_LABEL_ES,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                // The metric register: this is the number the whole panel delivers, and it
                // is read against the two beside it.
                Text(display.leafVpdEs, style = com.trichome.app.ui.theme.metricHeadline())
            }
            Text(
                VpdCalculator.AIR_VPD_LABEL_ES + ": " + display.airVpdEs,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(display.bandLabelEs, style = MaterialTheme.typography.bodyMedium)
            Text(
                display.bandAdviceEs,
                style = MaterialTheme.typography.bodySmall,
                color = if (display.needsAction) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            // The sentence that makes the number's provenance a property of the readout
            // rather than a footnote. Same requirement as EstimatedClimate.sourceEs.
            Text(
                display.provenanceLabelEs + ": " + display.provenanceExplanationEs,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}