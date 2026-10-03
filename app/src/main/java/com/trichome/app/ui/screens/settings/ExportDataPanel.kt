package com.trichome.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.trichome.app.data.export.ExportFileWriter
import com.trichome.app.data.export.ExportResult
import com.trichome.app.model.DataExportCopy
import com.trichome.app.model.ExportScope
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.components.accentButtonColors
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.viewmodel.DataExportViewModel
import com.trichome.app.viewmodel.appViewModel
import kotlinx.coroutines.launch

/**
 * Settings → Exportar Datos.
 *
 * ## The panel states the format rather than implying it
 *
 * [DataExportCopy.NO_PDF_ES] says, in the grower's language, that there is no PDF and why. A
 * grower who asked for an export and received JSON would otherwise assume the app could not
 * produce a document — and the honest answer is that this phase chose not to add a PDF library
 * without the owner's decision, which is a different thing entirely. Saying so here is cheaper
 * than saying it in a support conversation.
 *
 * ## The directory is shown before and after
 *
 * The export lands in the app's own external documents folder, which is not the Downloads
 * folder and is not somewhere a grower looks by default. So the path is printed both before
 * the write and after it, with the full path on success — a file in a directory nobody is told
 * about is a file nobody finds, and an export nobody can find is not a backup.
 *
 * ## The confirmation
 *
 * A dialog before the write, not a snackbar after it, because the write is the irreversible
 * part in the grower's mental model even though it only writes to a file: after it, the file
 * exists on shared storage and the grower has to go and delete it. The dialog names the scope
 * and the subject, so the grower confirms the export they meant and not whichever row was
 * nearest the tap.
 */
@Composable
fun ExportDataPanel(themeState: TrichomeThemeState) {
    val scheme = themeState.colorScheme()
    val accent = scheme.primary
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm = appViewModel { DataExportViewModel(it) }

    // Read once per composition, not observed: the picker is a modal the grower opens, reads
    // and dismisses, so a subscription that outlived it would collect for a dialog that is
    // already gone. `loadTargets` re-reads every time the picker is about to open, which is
    // when a plant created ten seconds ago has to appear.
    LaunchedEffect(Unit) { vm.loadTargets() }

    var pending by remember { mutableStateOf<PendingExport?>(null) }
    var result by remember { mutableStateOf<ExportResult?>(null) }
    var working by remember { mutableStateOf(false) }

    SolidPanel(contentColor = scheme.onSurface) {
        Column(Modifier.padding(16.dp)) {
            Text(DataExportCopy.PANEL_TITLE_ES, style = MaterialTheme.typography.titleMedium)
            Spacer8()
            Text(
                DataExportCopy.FORMAT_ES,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Spacer8()
            Text(
                DataExportCopy.NO_PDF_ES,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Spacer8()
            Text(
                DataExportCopy.PRIVACY_ES,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Spacer12()

            Text(DataExportCopy.SCOPE_HEADING_ES, style = MaterialTheme.typography.titleSmall)
            Spacer8()

            val directory = remember { ExportFileWriter.exportDirectory(context) }
            Text(
                DataExportCopy.DESTINATION_LABEL_ES + " " + (directory?.absolutePath ?: "—"),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Spacer8()

            OutlinedButton(
                onClick = {
                    vm.loadTargets()
                    pending = PendingExport(scope = ExportScope.PLANT)
                },
                enabled = vm.isReady,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(DataExportCopy.EXPORT_PLANT_ES, maxLines = 2)
            }
            Spacer8()
            OutlinedButton(
                onClick = {
                    vm.loadTargets()
                    pending = PendingExport(scope = ExportScope.TENT)
                },
                enabled = vm.isReady && directory != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(DataExportCopy.EXPORT_TENT_ES, maxLines = 2)
            }

            // Working notice, so a slow write on a slow card does not look like a dead button.
            if (working) {
                Spacer12()
                Text(
                    DataExportCopy.WORKING_ES,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }

            result?.let { outcome ->
                Spacer12()
                Text(
                    when (outcome) {
                        is ExportResult.Success ->
                            DataExportCopy.successEs(outcome.path, outcome.totalsEs)
                        is ExportResult.Failure -> outcome.reasonEs
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (outcome is ExportResult.Failure) {
                        scheme.error
                    } else {
                        scheme.onSurface
                    }
                )
            }
        }
    }

    pending?.let { request ->
        ExportTargetDialog(
            scope = request.scope,
            tents = vm.tents.map { it.id to it.name },
            plants = vm.plants.map { it.id to it.name },
            onDismiss = { pending = null },
            onConfirm = { subjectId, subjectName ->
                val directory = ExportFileWriter.exportDirectory(context)
                if (directory == null) {
                    result = ExportResult.Failure(DataExportCopy.NOTHING_TO_EXPORT_ES)
                    pending = null
                    return@ExportTargetDialog
                }
                // Cleared before the write: the write suspends and the dialog would otherwise
                // stay on screen as a second target for a double tap.
                pending = null
                working = true
                scope.launch {
                    val outcome = vm.export(request.scope, subjectId, subjectName, directory)
                    result = outcome
                    working = false
                }
            }
        )
    }
}

/** The scope the grower asked for, before they have picked a subject. */
private data class PendingExport(val scope: ExportScope)

/**
 * The subject picker: a plant list, or a tent list, and nothing else.
 *
 * A `Selectable` row rather than a dropdown: an export is a deliberate act with a name
 * attached to it, and a dropdown that collapses after one tap hides what was chosen. The list
 * is inside the dialog's own scroll, so it does not compete with the settings screen's
 * `verticalScroll` — the dialog is a separate window and owns its axis.
 */
@Composable
private fun ExportTargetDialog(
    scope: ExportScope,
    tents: List<Pair<Long, String>>,
    plants: List<Pair<Long, String>>,
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit
) {
    var selected by remember(scope) { mutableStateOf<Long?>(null) }

    val options = when (scope) {
        ExportScope.PLANT -> plants
        ExportScope.TENT -> tents
    }
    val label = if (scope == ExportScope.PLANT) {
        DataExportCopy.PLANT_PICKER_LABEL_ES
    } else {
        DataExportCopy.TENT_PICKER_LABEL_ES
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(scope.labelEs) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    scope.descriptionEs,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(label, style = MaterialTheme.typography.labelLarge)
                if (options.isEmpty()) {
                    Text(
                        DataExportCopy.NOTHING_TO_EXPORT_ES,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    options.forEach { option ->
                        val isSelected = selected == option.first
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = { selected = option.first }
                                )
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = isSelected,
                                // `null`, not `false`: the row itself carries the click handler, so
                                // a second one here would give the grower two targets for one
                                // selection and fire the change twice.
                                onClick = null
                            )
                            Text(
                                option.second,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val id = selected ?: return@TextButton
                    val name = options.first { it.first == id }.second
                    onConfirm(id, name)
                },
                enabled = options.isNotEmpty() && selected != null,
                colors = com.trichome.app.ui.components.accentTextButtonColors(
                    MaterialTheme.colorScheme,
                    accentFor()
                )
            ) { Text("Exportar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/**
 * The accent the dialog's confirm button wears.
 *
 * Read from the scheme rather than passed in: the dialog is inside a `Scaffold`-less helper
 * that has no `themeState`, and threading an `accent: Color` through two composables to
 * arrive at the same value is the kind of parameter that goes stale when a theme is added.
 */
@Composable
private fun accentFor() = MaterialTheme.colorScheme.primary

/** A themed vertical gap. Sized by padding rather than by `Spacer(height = …)` so the
 *  spacing scales with the user's chosen layout density, as every other gap here does. */
@Composable
private fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))

/** A themed vertical gap, larger. See [Spacer8]. */
@Composable
private fun Spacer12() = androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))