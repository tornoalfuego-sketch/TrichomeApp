package com.trichome.app.ui.screens.plant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.trichome.app.model.GrowStageCopy
import com.trichome.app.model.StageOption
import com.trichome.app.model.StageOptionSource

/**
 * The crop-stage surface: the timeline, "Cambiar de fase" and "Finalizar cultivo".
 *
 * ## Why these three live in one panel
 *
 * They are one decision — which stage is this plant in, and how do I change or end it — and
 * splitting them across the top bar, the bottom row and a separate screen would put the
 * grower's most consequential action three taps from its own history. `PlantDetailScreen`
 * also has a hand-rolled three-button bottom row, and adding a fourth button there is not
 * an option: three Spanish labels plus an emoji in three equal columns is already about
 * 340dp each, which is what forced `maxLines = 2` on that row in the first place. A fourth
 * would make the labels unreadable at the largest text size the app allows.
 *
 * So the actions go here, in the scrolling content, where a two-line label has room.
 *
 * ## Every Spanish string comes from the model
 *
 * No sentence is authored here. `GrowStagePlanner` and `GrowStageCopy` own the wording,
 * which is what lets a JVM test assert that no Spanish literal leaked into a composable —
 * the rule `EntourageF5StructureTest` established and this panel follows.
 *
 * @param isArchived whether the plant is finalized. Replaces the two actions with the
 *   archived banner, because an archive is terminal in the UI and offering "Cambiar de
 *   fase" on a finished grow would produce a transition the planner then refuses.
 */
@Composable
fun GrowStagePanel(
    currentStageLabelEs: String,
    timelineLinesEs: List<String>,
    timelineEmptyEs: String,
    timelineHintEs: String,
    archivedBannerEs: String,
    archivedChipEs: String,
    isArchived: Boolean,
    onChangeStage: () -> Unit,
    onFinalize: () -> Unit,
    accent: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                GrowStageCopy.LIFECYCLE_HEADING_ES,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (isArchived) {
                Text(
                    archivedChipEs,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
            }
        }

        Text(
            currentStageLabelEs,
            style = MaterialTheme.typography.bodyLarge
        )

        if (isArchived) {
            Text(
                archivedBannerEs,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onChangeStage, modifier = Modifier.weight(1f)) {
                    Text(GrowStageCopy.CHANGE_STAGE_ACTION_ES, maxLines = 2)
                }
                OutlinedButton(onClick = onFinalize, modifier = Modifier.weight(1f)) {
                    Text(GrowStageCopy.FINALIZE_ACTION_ES, maxLines = 2)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            GrowStageCopy.TIMELINE_HEADING_ES,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (timelineLinesEs.isEmpty()) {
            Text(timelineEmptyEs, style = MaterialTheme.typography.bodySmall)
        } else {
            timelineLinesEs.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            timelineHintEs,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The "Cambiar de fase" dialog.
 *
 * A chip row rather than a text field, because the stage name is what gets stored and it has
 * to be one this app offered: a grower's typed string with a stray space, or a protocol block
 * name that differs from the canonical one by a capital letter, would produce a timeline that
 * reads as two stages. [StageTransitionPlan] trims, and the planner compares trimmed, so the
 * remaining risk is a typo the chips cannot produce at all.
 *
 * @param options the stages for this plant, from the model.
 * @param currentStage the stage the plant is in, for the selected chip.
 * @param sourceLabelEs which list these are, so the grower knows whether they are the
 *   protocol's own blocks or the lifecycle keys.
 * @param reasonEs the planner's sentence, shown when nothing was written.
 */
@Composable
fun ChangeStageDialog(
    options: List<StageOption>,
    currentStage: String,
    sourceLabelEs: String,
    reasonEs: String?,
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: (StageOption) -> Unit
) {
    var selected by remember(options, currentStage) {
        mutableStateOf(options.firstOrNull { it.isCurrentStage(currentStage) } ?: options.firstOrNull())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(GrowStageCopy.CHANGE_STAGE_TITLE_ES) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(sourceLabelEs, style = MaterialTheme.typography.labelMedium)
                options.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { option ->
                            FilterChip(
                                selected = selected?.stageName == option.stageName,
                                onClick = { selected = option },
                                label = { Text(option.chipLabelEs(), maxLines = 1) }
                            )
                        }
                    }
                }
                if (reasonEs != null) {
                    Text(
                        reasonEs,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let(onConfirm) },
                enabled = selected != null,
                colors = com.trichome.app.ui.components.accentTextButtonColors(
                    MaterialTheme.colorScheme,
                    accent
                )
            ) { Text(GrowStageCopy.SAVE_ES) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(GrowStageCopy.CANCEL_ES) }
        }
    )
}

/**
 * The "Finalizar cultivo" confirmation.
 *
 * An [AlertDialog] with the confirm button in `colorScheme.primary`, **not** in
 * `colorScheme.error`. `ConfirmDestructiveDialog` wears error because a delete is one, and
 * finalizing is not a delete: it archives the plant and loses nothing. Painting the archive
 * as a destruction is the same mistake as labelling it "Eliminar", and a grower who has been
 * trained to flinch at a red button stops trusting red buttons — including the delete
 * confirmations that do deserve one.
 *
 * The holder behind it is the shared [com.trichome.app.ui.components.DestructiveConfirmation],
 * so a tap only arms the action and the write happens inside `confirm()`. That is why this
 * dialog is a separate composable rather than a call to the shared one.
 *
 * @param bodyEs what the archive does and what survives, from the model.
 */
@Composable
fun FinalizeGrowDialog(
    bodyEs: String,
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(GrowStageCopy.FINALIZE_TITLE_ES) },
        text = { Text(bodyEs, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = com.trichome.app.ui.components.accentTextButtonColors(
                    MaterialTheme.colorScheme,
                    accent
                )
            ) { Text(GrowStageCopy.FINALIZE_ACTION_ES) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(GrowStageCopy.CANCEL_ES) }
        }
    )
}

/** A chip's label, with its duration when the protocol declared one. */
internal fun StageOption.chipLabelEs(): String =
    durationDays?.let { "$labelEs · $it d" } ?: labelEs

/** Whether this option is the stage the plant is currently in. */
internal fun StageOption.isCurrentStage(currentStage: String): Boolean =
    stageName.trim() == currentStage.trim()

/**
 * The caption above the chip rows: which list of stages these are.
 *
 * [StageOptionSource.labelEs] is the model's, not a literal here, so the two vocabularies
 * cannot be described in different words on the two surfaces that offer them.
 */
internal fun optionsSourceLabelEs(options: List<StageOption>): String =
    (options.firstOrNull()?.source ?: StageOptionSource.LIFECYCLE).labelEs