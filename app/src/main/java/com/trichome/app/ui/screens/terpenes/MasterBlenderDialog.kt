package com.trichome.app.ui.screens.terpenes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trichome.app.data.repository.Terpene
import com.trichome.app.model.BlendQuality
import com.trichome.app.model.BlendResult
import com.trichome.app.model.TerpeneBlender
import com.trichome.app.model.TerpeneProgression
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.theme.TrichomeThemeState

/**
 * Master Blender: mix terpene percentages and see how the result compares to the
 * catalogue's reference profile.
 *
 * The scoring is [TerpeneBlender.match] — a pure function of the sliders and the
 * catalogue, which is the only reason it is testable at all.
 *
 * The wording matters as much as the number. The catalogue has no per-strain
 * composition (see [TerpeneBlender] for the data), so this screen compares the
 * mix against the encyclopedia's own abundance ratings and says so. It never
 * names a cultivar, and it never claims a number the data does not support.
 */
@Composable
fun MasterBlenderDialog(
    catalog: List<Terpene>,
    themeState: TrichomeThemeState,
    onDismiss: () -> Unit
) {
    val scheme = themeState.colorScheme()
    val featured = remember(catalog) { TerpeneBlender.featuredCompounds(catalog) }

    var readings by remember(catalog) { mutableStateOf(emptyMap<String, Float>()) }
    val result = remember(readings, catalog) { TerpeneBlender.match(readings, catalog) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🧪 Master Blender") },
        text = {
            if (featured.isEmpty()) {
                Text("La enciclopedia todavía no tiene compuestos con los que mezclar.")
            } else {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(max = 420.dp)
                ) {
                    Text(
                        "Ajusta los porcentajes de tu análisis y compáralo con el " +
                            "perfil de referencia de la enciclopedia, construido a " +
                            "partir de la abundancia de cada compuesto en cannabis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    featured.forEach { terpene ->
                        BlendSlider(
                            terpene = terpene,
                            value = readings[terpene.id] ?: 0f,
                            onChange = { value ->
                                readings = if (value <= 0f) readings - terpene.id
                                else readings + (terpene.id to value)
                            },
                            themeState = themeState
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    if (readings.isEmpty()) {
                        TextButton(onClick = { readings = defaultReading(featured) }) {
                            Text("Cargar un perfil de ejemplo")
                        }
                    } else {
                        TextButton(onClick = { readings = emptyMap() }) {
                            Text("Limpiar")
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    BlendReport(result, readings.size, themeState)

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "El catálogo no guarda la composición de ninguna cepa, así que " +
                            "la comparación es contra la enciclopedia y no contra una " +
                            "cepa concreta.",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

/** A plausible starting mix so the screen is not a wall of zeroes on first open. */
private fun defaultReading(featured: List<Terpene>): Map<String, Float> =
    featured.take(3).mapIndexed { index, terpene -> terpene.id to (40f - index * 15f) }.toMap()

@Composable
private fun BlendSlider(
    terpene: Terpene,
    value: Float,
    onChange: (Float) -> Unit,
    themeState: TrichomeThemeState
) {
    val scheme = themeState.colorScheme()
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(terpene.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "${TerpeneProgression.percent(value / 100f)}%",
                style = MaterialTheme.typography.labelLarge,
                color = if (value > 0f) scheme.primary else scheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..100f,
            steps = 19,
            modifier = Modifier.fillMaxWidth()
        )
        if (terpene.family.isNotBlank()) {
            Text(
                terpene.family,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

/** The score, the band, and which compounds are off — the "why", not just the number. */
@Composable
private fun BlendReport(
    result: BlendResult,
    compoundsRead: Int,
    themeState: TrichomeThemeState
) {
    val scheme = themeState.colorScheme()
    SolidPanel(
        accentColor = scheme.primary,
        contentColor = scheme.onSurface
    ) {
        Column(Modifier.padding(14.dp)) {
            when (result.quality) {
                BlendQuality.NO_SELECTION -> Text(
                    "Mueve al menos un compuesto para comparar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )

                BlendQuality.NO_REFERENCE -> Text(
                    "La enciclopedia no tiene datos de abundancia para comparar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )

                else -> {
                    Text(
                        "${result.percent}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (result.quality) {
                            BlendQuality.EXACTO, BlendQuality.FUERTE -> scheme.primary
                            BlendQuality.PARCIAL -> scheme.tertiary
                            else -> scheme.error
                        }
                    )
                    Text(
                        bandLabel(result.quality),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        coincidence(result.quality, compoundsRead),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            if (result.userFamilySplit.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FamilySplitRow("Tu mezcla", result.userFamilySplit, scheme)
                Spacer(Modifier.height(4.dp))
                FamilySplitRow("Referencia", result.referenceFamilySplit, scheme)
            }

            if (result.overRepresented.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "⬆ Por encima de la referencia",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.tertiary
                )
                result.overRepresented.take(3).forEach { note ->
                    Text(
                        "${note.name}: ${TerpeneProgression.percent(note.userShare)}% " +
                            "frente a ${TerpeneProgression.percent(note.referenceShare)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            if (result.underRepresented.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "⬇ Por debajo de la referencia",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant
                )
                result.underRepresented.take(3).forEach { note ->
                    Text(
                        "${note.name}: ${TerpeneProgression.percent(note.userShare)}% " +
                            "frente a ${TerpeneProgression.percent(note.referenceShare)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FamilySplitRow(label: String, split: Map<String, Float>, scheme: ColorScheme) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp)
        )
        Text(
            split.entries.joinToString(" · ") { "${it.key} ${TerpeneProgression.percent(it.value)}%" },
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurface
        )
    }
}

private fun bandLabel(quality: BlendQuality): String = when (quality) {
    BlendQuality.EXACTO -> "Coincide con el perfil de referencia"
    BlendQuality.FUERTE -> "Muy cerca del perfil de referencia"
    BlendQuality.PARCIAL -> "Coincide en parte"
    BlendQuality.DEBIL -> "No coincide con el perfil de referencia"
    BlendQuality.NO_SELECTION, BlendQuality.NO_REFERENCE -> ""
}

/**
 * The honesty line under the score.
 *
 * Chance agreement between two random mixes falls as more compounds are read,
 * so a match over two compounds is much weaker evidence than one over eight.
 * Saying so is the difference between a number and a claim.
 */
private fun coincidence(quality: BlendQuality, compoundsRead: Int): String = when {
    compoundsRead < 3 -> "Poca evidencia: con menos de 3 compuestos el resultado puede ser casualidad."
    quality == BlendQuality.DEBIL -> "Revisa las proporciones: el perfil está lejos del de referencia."
    compoundsRead >= 6 -> "Basado en $compoundsRead compuestos, así que el resultado es bastante sólido."
    else -> "Basado en $compoundsRead compuestos."
}
