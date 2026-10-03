package com.trichome.app.ui.screens.cycle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.trichome.app.model.SupercycleAnchorPicker
import com.trichome.app.model.SupercycleAnchorPreview
import com.trichome.app.model.SupercycleAnchorStep
import com.trichome.app.model.SupercycleScheduleBuilder
import com.trichome.app.ui.components.accentTextButtonColors
import com.trichome.app.ui.theme.LocalTertiaryText
import com.trichome.app.ui.theme.metricValue
import java.time.ZoneId

/**
 * "Inicio del superciclo": choose the instant the superday count is measured from.
 *
 * ## Why the anchor had to become the grower's choice
 *
 * `cycleStartAt` defaulted to `System.currentTimeMillis()`, so the superday count was
 * anchored on whenever the row was first written and nothing on the screen could move it.
 * That is fine until it is not: adopting a tent's existing photoperiod, importing a
 * photoperiod typed into another app, or restoring a backup all land on the wrong anchor
 * and silently renumber every superday. The column already existed and was already
 * persisted on all three tables, so this is a control over an existing value — not a
 * schema change, and not a migration.
 *
 * ## Why the consequence is shown before the commit
 *
 * A wrong anchor does not fail. It produces a perfectly plausible number: superday 47
 * instead of superday 5, with no indication anything is off. So the dialog prints, while
 * the grower is still choosing, what the chosen instant implies — which superday it puts
 * us in, which real day that superday began on, where that boundary falls against
 * midnight, and the phase the plant would be in right now. All of it from
 * [SupercycleSchedule.anchorPreviewFor], which calls the same engine the result card does.
 *
 * A future anchor is refused outright rather than warned about: the engine clamps a
 * negative elapsed time to zero, so every hour before the anchor would render as the
 * first light phase and the card would show a day of sunlight the cycle does not
 * describe.
 *
 * ## Cancel writes nothing
 *
 * The draft instant lives in this dialog's own snapshot state. [onConfirm] is the only
 * path out of it, and the screen's Save button remains the only thing that writes the
 * row. Cancelling, dismissing by tapping outside, and rotating the device all leave the
 * stored `cycleStartAt` exactly as it was.
 *
 * ## Why two steps
 *
 * `DatePicker` and `TimePicker` are both full panels; stacking them in one dialog body
 * needs a scroll inside an `AlertDialog`, which is the shape `BlenderLayout`'s KDoc
 * already warns about. One panel at a time plus the consequence block is a fixed-height
 * dialog that needs no scroll on either step.
 *
 * ## No second scroll owner
 *
 * The consequence block is a fixed four lines. It does not scroll, and neither does
 * anything else here: `SuperCycleScreen`'s column already owns the vertical axis, and a
 * nested vertical scroll is measured with an infinite maximum height and throws
 * (`ScrollOwnershipTest`).
 *
 * ## Strings
 *
 * Every sentence is `SupercycleAnchorPicker`'s or `SupercycleSchedule`'s. This file
 * holds no literal copy of its own — the rule `EntourageF5StructureTest` enforces on the
 * Séquito module, extended here by `SupercycleHeatmapStructureTest`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupercycleAnchorPickerDialog(
    currentAnchorMillis: Long,
    lightHours: Int,
    darkHours: Int,
    zone: ZoneId,
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var step by remember { mutableStateOf(SupercycleAnchorStep.DATE) }
    // `mutableLongStateOf`, not `mutableStateOf<Long>`: the draft is a millisecond and
    // boxing it allocates on every wheel tick. Lint says so, and on a screen that has a
    // Compose unit-test runtime nowhere to assert it, lint is the only guard there is.
    var draftMillis by remember { mutableLongStateOf(currentAnchorMillis) }
    // Captured once per dialog, for the same reason `PlantMigrationDialog` captures it:
    // a fresh `now` on every recomposition would let the resolved anchor drift under the
    // grower's finger while they drag the time wheel.
    val nowMillis = remember { System.currentTimeMillis() }

    if (step == SupercycleAnchorStep.DATE) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = SupercycleAnchorPicker.toPickerDateMillis(draftMillis, zone)
        )

        DatePickerDialog(
            onDismissRequest = {
                // Tapping outside is a cancel, not a commit. The draft dies with the
                // dialog and nothing has reached the form.
                onDismiss()
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val picked = dateState.selectedDateMillis
                        if (picked != null) {
                            // The time the grower already had is reattached to the new
                            // date. `DatePicker` reports UTC midnight, and dropping the
                            // time here would silently move the anchor to 00:00.
                            draftMillis = SupercycleAnchorPicker.fromPickerDateMillis(
                                pickerDateMillis = picked,
                                minutesOfDay = SupercycleAnchorPicker.minutesOf(draftMillis, zone),
                                zone = zone
                            )
                            step = SupercycleAnchorStep.TIME
                        }
                    },
                    colors = accentTextButtonColors(MaterialTheme.colorScheme, accent)
                ) {
                    Text(SupercycleAnchorPicker.PICK_TIME_ES)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    colors = accentTextButtonColors(MaterialTheme.colorScheme, accent)
                ) {
                    Text(SupercycleAnchorPicker.CANCEL_ES)
                }
            }
        ) {
            DatePicker(state = dateState)
        }
        return
    }

    val timeState = rememberTimePickerState(
        initialHour = (SupercycleAnchorPicker.minutesOf(draftMillis, zone) / 60) % 24,
        initialMinute = SupercycleAnchorPicker.minutesOf(draftMillis, zone) % 60,
        is24Hour = true
    )

    // Recomputed on every keystroke on the time wheel, so the consequence block tracks
    // the handle rather than the value the grower is about to commit to.
    val pendingMillis = SupercycleAnchorPicker.fromPickerDateMillis(
        pickerDateMillis = SupercycleAnchorPicker.toPickerDateMillis(draftMillis, zone),
        minutesOfDay = timeState.hour * 60 + timeState.minute,
        zone = zone
    )
    val pendingPreview: SupercycleAnchorPreview = SupercycleScheduleBuilder.anchorPreviewFor(
        cycleStartAt = pendingMillis,
        lightHours = lightHours,
        darkHours = darkHours,
        nowMillis = nowMillis,
        zone = zone
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(SupercycleAnchorPicker.TIME_STEP_ES) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = SupercycleAnchorPicker.INTRO_ES,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalTertiaryText.current
                )
                TimePicker(state = timeState)
                Spacer(Modifier.height(4.dp))
                AnchorConsequence(preview = pendingPreview)
            }
        },
        confirmButton = {
            TextButton(
                // The commit path. The only statement in this file that leaves the
                // dialog, and it hands the instant up rather than writing it — the
                // screen's Save button remains the only writer of the row.
                onClick = { onConfirm(pendingMillis) },
                enabled = pendingPreview.canConfirm,
                colors = accentTextButtonColors(MaterialTheme.colorScheme, accent)
            ) {
                Text(SupercycleAnchorPicker.CONFIRM_ES)
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { step = SupercycleAnchorStep.DATE },
                    colors = accentTextButtonColors(MaterialTheme.colorScheme, accent)
                ) {
                    Text(SupercycleAnchorPicker.PICK_DATE_ES)
                }
                TextButton(
                    onClick = onDismiss,
                    colors = accentTextButtonColors(MaterialTheme.colorScheme, accent)
                ) {
                    Text(SupercycleAnchorPicker.CANCEL_ES)
                }
            }
        }
    )
}

/**
 * What the chosen instant implies, before it is committed.
 *
 * Every line comes from [SupercycleAnchorPreview]. The warning is printed in `error`
 * because it is the one line that has to be acted on rather than read, and the confirm
 * button is disabled from the same field — so the copy cannot say "go ahead" while the
 * button says no.
 */
@Composable
private fun AnchorConsequence(preview: SupercycleAnchorPreview) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = SupercycleAnchorPicker.CONSEQUENCE_ES,
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            text = preview.anchorLabelEs,
            // The value register: it is read against the engine's own arithmetic
            // underneath it, and the two numbers are what the grower is deciding
            // between.
            style = metricValue()
        )
        Text(
            text = preview.superdayLabelEs,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = preview.superdayStartLabelEs,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = preview.offsetLabelEs,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = preview.phaseNowLabelEs,
            style = MaterialTheme.typography.bodySmall,
            color = LocalTertiaryText.current
        )
        preview.warningEs?.let { warning ->
            Text(
                text = warning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * The summary row above the "cambiar" control: the stored anchor and how far it sits
 * from today.
 *
 * Rendered on the form rather than only inside the dialog, so the value is readable
 * without opening anything — the dialog is for changing it, not for discovering it.
 */
@Composable
fun SupercycleAnchorSummary(
    preview: SupercycleAnchorPreview,
    daysFromNowLabelEs: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(preview.superdayLabelEs, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = preview.anchorLabelEs,
            style = metricValue()
        )
        Text(
            text = daysFromNowLabelEs,
            style = MaterialTheme.typography.labelSmall,
            color = LocalTertiaryText.current
        )
    }
}