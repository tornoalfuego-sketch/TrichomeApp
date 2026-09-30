package com.trichome.app.ui.screens.plant

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.trichome.app.ui.components.accentTextButtonColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.trichome.app.data.entity.Plant

/**
 * Editor for a plant: name, strain, growth stage and notes.
 *
 * Seeded from the row through [PlantEditForm], keyed on [Plant.id] so reusing
 * this dialog across plants cannot keep the previous one's values — the
 * `remember`-seeding bug that already bit `ProtocolScreen` in v1.1.0.
 *
 * The fields that the grower does not see — tent, position and grow start — are
 * carried over untouched by [PlantEditForm.applyTo]. Resetting the grow start
 * would rewind "Día de Crecimiento" to one, which is why the create form's
 * `System.currentTimeMillis()` default must not be reused here.
 */
@Composable
fun PlantEditDialog(
    plant: Plant,
    accent: Color,
    onDismiss: () -> Unit,
    onSave: (Plant) -> Unit
) {
    var form by remember(plant.id) { mutableStateOf(PlantEditForm.formOf(plant)) }
    var problem by remember(plant.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar planta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { form = form.copy(name = it); problem = null },
                    label = { Text("Nombre de la planta *") },
                    isError = problem != null,
                    supportingText = problem?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = form.strain,
                    onValueChange = { form = form.copy(strain = it) },
                    label = { Text("Cepa / genotipo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Etapa", style = MaterialTheme.typography.labelLarge)
                // A row saved before this list existed, or with a stage typed
                // elsewhere, still shows as selected instead of silently
                // jumping to the first chip.
                (PlantEditForm.STAGES + listOf(form.stage))
                    .distinct()
                    .chunked(3)
                    .forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { stage ->
                                FilterChip(
                                    selected = form.stage == stage,
                                    onClick = { form = form.copy(stage = stage) },
                                    label = { Text(stageLabel(stage)) }
                                )
                            }
                        }
                    }

                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { form = form.copy(notes = it) },
                    label = { Text("Notas") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val rejection = PlantEditForm.validate(form)
                    if (rejection != null) {
                        problem = rejection
                    } else {
                        onSave(PlantEditForm.applyTo(form, plant))
                    }
                },
                colors = accentTextButtonColors(MaterialTheme.colorScheme, accent)
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/**
 * Stage chip label.
 *
 * The stored value is a raw English key, because that is what the rest of the
 * app queries on; the grower reads Spanish.
 */
private fun stageLabel(stage: String): String = when (stage) {
    "seedling" -> "Plántula"
    "vegetative", "vegetativo" -> "Vegetativo"
    "floracion" -> "Floración"
    "lavado" -> "Lavado"
    "cosecha" -> "Cosecha"
    else -> stage
}
