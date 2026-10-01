package com.trichome.app.ui.screens.plant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.trichome.app.data.entity.GrowTent
import com.trichome.app.model.PhotoperiodConfig
import com.trichome.app.model.PlantCycleState
import com.trichome.app.model.PlantMigrationDialogContent
import com.trichome.app.model.PlantMigrationDialogCopy
import com.trichome.app.model.PlantMigrationDialogState
import com.trichome.app.model.PlantMigrationPlan
import com.trichome.app.model.PlantMigrationPolicyOption
import com.trichome.app.model.TentCycleState
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.components.accentTextButtonColors
import com.trichome.app.ui.theme.LocalTertiaryText

/**
 * "Cambiar de carpa": pick a destination tent, then decide what the plant keeps.
 *
 * ## The dialog resolves nothing itself
 *
 * The outcome is [PlantMigrationPlanner.plan], a pure function, called through
 * [PlantMigrationDialogState.planFor]. Every choice change re-plans and re-renders the
 * *actual* [PlantMigrationPlan.reasonEs] rather than describing the rule itself. That
 * is the point: the dialog this replaces had a checkbox whose unchecked state had no
 * defined outcome, so the write landed on whichever branch came first. Here the copy
 * cannot disagree with the write because both read the same object.
 *
 * ## The journal is stated, not offered
 *
 * `GrowEvent` rows carry a `plantId` with a `CASCADE` foreign key to `plants`; the tent
 * is not in that relationship at all, so `assignPlantToTent()` — which writes only
 * `plants.tentId` — cannot drop, hide or invalidate a journal row. The dialog therefore
 * states that as a fact ([PlantMigrationDialogContent.journalNoticeEs]) rather than
 * offering a checkbox that could not do anything. `PlantMigrationChoices` documents the
 * other half: a request to drop them resolves to `REJECTED` instead of being honoured.
 *
 * ## State follows `DestructiveConfirmation`
 *
 * The dialog's state is snapshot state, and the confirm path clears it **before** the
 * write, exactly as `DestructiveConfirmation.confirm` does. A plain `var` produced two
 * device bugs there — the dialog not appearing until something else recomposed, and not
 * closing after confirming — so this borrows that discipline rather than inventing a
 * second mechanism. The difference: that holder is for *destructive* actions and this is
 * not one, so only the state discipline is shared; the dialog style is an ordinary
 * `AlertDialog`.
 *
 * ## Scroll ownership
 *
 * Exactly **one** scroll on this screen: the dialog's `text` column. The tent list plus
 * the three policy rows plus the plan preview can exceed a short landscape window, so
 * the whole body scrolls and nothing inside it does. A scrollable nested in another
 * scrollable measures its child with an infinite maximum height and throws — the crash
 * `ScrollOwnershipTest` documents.
 */
@Composable
fun PlantMigrationDialog(
    plantId: Long,
    plantName: String,
    current: PlantCycleState,
    /** The tent the plant is in now, or null when it sits in "Sin carpa". */
    currentTentId: Long?,
    tents: List<GrowTent>,
    tentPhotoperiods: Map<Long, PhotoperiodConfig>,
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: (destinationTent: GrowTent, plan: PlantMigrationPlan) -> Unit
) {
    // Snapshot state, and this composable is the only reader of it: written by the row
    // taps below and by the confirm handler, so every path invalidates the composition.
    var state by remember(plantId) { mutableStateOf(PlantMigrationDialogState()) }
    var destinationId by remember(plantId) { mutableStateOf<Long?>(null) }

    // Captured once per dialog, not read from the clock on every recomposition. The
    // plan is recomputed whenever a choice changes, and a fresh `now` each time would
    // make the resolved anchor drift while the grower is still looking at the dialog.
    val nowMillis = remember(plantId) { System.currentTimeMillis() }

    val candidates = tents.filter { it.id != currentTentId }
    val destination = candidates.firstOrNull { it.id == destinationId }
    val plan: PlantMigrationPlan? = destination?.let { tent ->
        state.planFor(
            current = current,
            destination = TentCycleState(
                tentId = tent.id,
                tentName = tent.name,
                photoperiod = tentPhotoperiods[tent.id]
            ),
            nowMillis = nowMillis
        )
    }
    val content: PlantMigrationDialogContent? = plan?.let {
        PlantMigrationDialogCopy.contentFor(
            plan = it,
            plantName = plantName,
            tentName = destination?.name.orEmpty()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(content?.titleEs ?: "Cambiar de carpa") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Elige la carpa destino. Moverse no toca la bitácora: " +
                        "sus registros están ligados a la planta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalTertiaryText.current
                )

                if (candidates.isEmpty()) {
                    Text(
                        "No hay otra carpa disponible. Crea una desde la pantalla de carpas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    candidates.forEach { tent ->
                        SelectableRow(
                            title = tent.name,
                            subtitle = tent.location.takeIf { it.isNotBlank() },
                            selected = tent.id == destinationId,
                            onSelect = { destinationId = tent.id }
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("¿Qué conserva la planta?", style = MaterialTheme.typography.labelLarge)
                    // Re-planning on every tap is what makes the preview below
                    // describe what will actually be written.
                    PlantMigrationPolicyOption.ALL.forEach { option ->
                        SelectableRow(
                            title = option.labelEs,
                            subtitle = option.descriptionEs,
                            selected = state.selected == option,
                            onSelect = { state = state.withPolicy(option.policy) }
                        )
                    }
                }

                if (content != null) {
                    Spacer(Modifier.height(4.dp))
                    PlanPreview(content = content)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val target = destination ?: return@TextButton
                    val resolved = plan ?: return@TextButton
                    // Cleared before the write, as `DestructiveConfirmation.confirm`
                    // does: the write recomposes, and a still-set flag would put the
                    // dialog back on screen for the frame in between — with a second
                    // target for a double tap to fire at.
                    destinationId = null
                    onConfirm(target, resolved)
                },
                enabled = destination != null && content?.canApply == true,
                colors = accentTextButtonColors(MaterialTheme.colorScheme, accent)
            ) {
                Text(content?.confirmLabelEs ?: "Mover")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    destinationId = null
                    onDismiss()
                },
                colors = accentTextButtonColors(MaterialTheme.colorScheme, accent)
            ) {
                Text(content?.cancelLabelEs ?: "Cancelar")
            }
        }
    )
}

/** One selectable row: a radio button plus a title and an optional subtitle. */
@Composable
private fun SelectableRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // `selectable` on the whole row, not just the button: a 24dp radio is a
            // 24dp tap target, and the row is what the grower aimed at.
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalTertiaryText.current
                )
            }
        }
    }
}

/**
 * What the move will do, rendered from the resolved plan.
 *
 * The destination-config warning appears whenever `requiresDestinationConfigWrite` is
 * true, and it distinguishes the two cases: a photoperiod carried over from the plant,
 * versus the 18/6 safe default that nobody typed. Silently writing 18/6 into a tent
 * would make that tent drive every future plant added to it with a number the grower
 * never chose.
 */
@Composable
private fun PlanPreview(content: PlantMigrationDialogContent) {
    SolidPanel(cornerRadius = 12) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(content.photoperiodChangeEs, style = MaterialTheme.typography.bodyMedium)
            Text(content.reasonEs, style = MaterialTheme.typography.bodySmall)
            content.destinationConfigWarningEs?.let { warning ->
                SolidPanel(cornerRadius = 10, borderEnabled = false) {
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            Text(
                text = content.journalNoticeEs,
                style = MaterialTheme.typography.labelSmall,
                color = LocalTertiaryText.current
            )
        }
    }
}