package com.trichome.app.ui.screens.terpenes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trichome.app.data.repository.Terpene
import com.trichome.app.model.BlendQuality
import com.trichome.app.model.BlendResult
import com.trichome.app.model.TerpeneBlender
import com.trichome.app.model.TerpeneProgression
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.components.accentTextButtonColors
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
 *
 * ## Reachability
 *
 * The body is the only scroll on this screen, and its height cap comes from
 * [BlenderLayout.bodyMaxHeightDp] -- the height the dialog's own chrome leaves,
 * not a share of the screen. The cap is applied to the scroll *container* and
 * the scroll is applied after it, which is the only order in which the content
 * is measured unbounded and can actually travel. The previous order clamped the
 * content and shipped a dialog that could not be scrolled at all; that was
 * verified on the device, not inferred.
 */
@Composable
fun MasterBlenderDialog(
    catalog: List<Terpene>,
    themeState: TrichomeThemeState,
    onDismiss: () -> Unit
) {
    val scheme = themeState.colorScheme()
    val featured = remember(catalog) { TerpeneBlender.featuredCompounds(catalog) }
    // Keyed on the height so a rotation or a fold re-derives it; the arithmetic
    // itself is in `BlenderLayout` and is covered by tests that need no device.
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val bodyMaxHeight = remember(screenHeightDp) {
        BlenderLayout.bodyMaxHeightDp(screenHeightDp).dp
    }

    var readings by remember(catalog) { mutableStateOf(emptyMap<String, Float>()) }
    val result = remember(readings, catalog) { TerpeneBlender.match(readings, catalog) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🧪 Master Blender") },
        text = {
            if (featured.isEmpty()) {
                Text("La enciclopedia todavía no tiene compuestos con los que mezclar.")
            } else {
                // The scroll owns the axis; the cap is a property of the *viewport*,
                // applied to the scroll container and never to the content.
                //
                // The order here is the whole fix. `heightIn` before `verticalScroll`
                // constrains what the scroll is allowed to occupy, so the content is
                // still measured unbounded and can travel. The reverse order -- which
                // is what this file shipped -- clamped the content itself, so the
                // scroll had a viewport exactly as tall as its content and no range
                // to move over. Verified on the device: a 500px swipe produced a
                // byte-identical screenshot. See [BlenderLayout].
                Column(
                    modifier = Modifier
                        .heightIn(max = bodyMaxHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    // What this is for, before what it does. The complaint was
                    // "no se entiende para qué esa funcionalidad": five unlabelled
                    // sliders under a title gave no answer. The purpose is one
                    // sentence, and it is first.
                    Text(
                        "Compara el perfil de terpenos de tu planta con el de referencia " +
                            "de la enciclopedia.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface
                    )
                    Text(
                        "Mueve cada control al porcentaje que registró tu análisis de " +
                            "laboratorio. El resultado se recalcula solo mientras mueves.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    // Named as the *input*. It is the sliders that are being set,
                    // not a result, and a heading that said "resultado" above a row
                    // of empty controls would be the same "no se entiende" the
                    // complaint describes -- in a new place.
                    SectionLabel("1 · Tu análisis", scheme)

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
                        TextButton(
                            onClick = { readings = defaultReading(featured) },
                            colors = accentTextButtonColors(scheme, scheme.primary)
                        ) {
                            Text("Cargar un perfil de ejemplo")
                        }
                    } else {
                        TextButton(
                            onClick = { readings = emptyMap() },
                            colors = accentTextButtonColors(scheme, scheme.primary)
                        ) {
                            Text("Limpiar")
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    SectionLabel("2 · Coincidencia con la referencia", scheme)
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
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = accentTextButtonColors(scheme, scheme.primary)
            ) { Text("Cerrar") }
        }
    )
}

/**
 * A heading inside the dialog body.
 *
 * Split out so the two section labels are the same size and the same colour
 * tier: a section that outranks the report it introduces would be worse than no
 * section at all.
 */
@Composable
private fun SectionLabel(text: String, scheme: ColorScheme) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
    Spacer(Modifier.height(4.dp))
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
