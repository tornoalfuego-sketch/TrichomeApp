package com.trichome.app.ui.screens.breeding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trichome.app.model.Genotype
import com.trichome.app.model.Punnett
import com.trichome.app.model.PunnettResult
import com.trichome.app.model.PunnettSquare
import com.trichome.app.model.genotypeLabelEs
import com.trichome.app.model.percentLabelEs
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.theme.LocalTertiaryText
import androidx.compose.runtime.remember

/**
 * Interactive Punnett square.
 *
 * Every number on screen comes from [Punnett.cross]; this file chooses parents
 * and draws the grid. Nothing here computes a probability, so the simulator
 * cannot drift from the genetics the theory chapters teach.
 *
 * The parent pickers are chips rather than a free-text field on purpose: the
 * model has exactly three genotypes, so offering anything else would be offering
 * an input the parser can only reject. [Punnett.square] still validates, because
 * a stale or restored state must not be able to render a nonsense square.
 *
 * It does not scroll: the hosting tab owns the vertical scroll, and a second
 * scrollable nested inside one is measured with an infinite maximum height,
 * which Compose rejects at runtime. It was the same crash as the theory tab.
 */
@Composable
fun PunnettSquarePanel(
    accent: Color,
    modifier: Modifier = Modifier
) {
    var first by remember { mutableStateOf(Genotype.HETEROZYGOUS) }
    var second by remember { mutableStateOf(Genotype.HETEROZYGOUS) }

    val result = remember(first, second) { Punnett.square(first.notation, second.notation) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🧬 Simulador de herencia (cuadro de Punnett)", style = MaterialTheme.typography.titleMedium)
        Text(
            "Elige el genotipo de cada progenitor y mira cómo se reparte el rasgo en la descendencia.",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalTertiaryText.current
        )

        ParentPicker(
            label = "Progenitor A",
            selected = first,
            accent = accent,
            onSelect = { first = it }
        )
        ParentPicker(
            label = "Progenitor B",
            selected = second,
            accent = accent,
            onSelect = { second = it }
        )

        when (result) {
            is PunnettResult.Invalid -> SolidPanel(accentColor = accent) {
                Column(Modifier.padding(14.dp)) {
                    Text("No se pudo calcular el cruce", style = MaterialTheme.typography.titleSmall)
                    Text(result.reason, style = MaterialTheme.typography.bodyMedium)
                }
            }

            is PunnettResult.Computed -> SquareResult(result.square, accent)
        }
    }
}

/** One parent's genotype, as a row of mutually exclusive chips. */
@Composable
private fun ParentPicker(
    label: String,
    selected: Genotype,
    accent: Color,
    onSelect: (Genotype) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Punnett.GENOTYPES.forEach { genotype ->
                GenotypeChip(
                    text = genotype.notation,
                    selected = genotype == selected,
                    accent = accent,
                    onClick = { onSelect(genotype) }
                )
            }
        }
        Text(
            genotypeLabelEs(selected),
            style = MaterialTheme.typography.bodySmall,
            color = LocalTertiaryText.current
        )
    }
}

@Composable
private fun GenotypeChip(
    text: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .selectable(selected = selected, onClick = onClick)
            .background(
                color = if (selected) accent.copy(alpha = 0.28f) else scheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant
        )
    }
}

/** The grid, the gamete axes and the three probabilities. */
@Composable
private fun SquareResult(
    square: PunnettSquare,
    accent: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "${square.first.notation} × ${square.second.notation}",
            style = MaterialTheme.typography.titleMedium
        )

        PunnettGrid(square, accent)

        SolidPanel(accentColor = accent) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Probabilidades", style = MaterialTheme.typography.titleSmall)
                ProbabilityRow("Fenotipo dominante", square.dominantPhenotypeProbability)
                ProbabilityRow("Fenotipo recesivo", square.recessivePhenotypeProbability)
                ProbabilityRow("Portadores (heterocigotos)", square.carrierProbability)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Fenotipo ${square.phenotypeRatioEs()} · Genotipo ${square.genotypeRatioEs()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalTertiaryText.current
                )
                Text(
                    "Estas son medias de población, no un resultado garantizado para cada planta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalTertiaryText.current
                )
            }
        }
    }
}

/**
 * The square itself: one header row and one header column of gametes, then the
 * cells.
 *
 * The axes are drawn rather than implied because a Punnett square without them
 * is just a 2x2 table — the whole point is that the row and column headers show
 * the 1:1 split of a heterozygote.
 */
@Composable
private fun PunnettGrid(square: PunnettSquare, accent: Color) {
    val scheme = MaterialTheme.colorScheme
    val firstGametes = square.first.gametes()
    val secondGametes = square.second.gametes()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(28.dp))
            secondGametes.forEach { gamete ->
                Text(
                    "${gamete.allele.symbol} (${percentLabelEs(gamete.probability)})",
                    modifier = Modifier
                        .width(72.dp)
                        .padding(bottom = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
        }

        firstGametes.forEachIndexed { row, gamete ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${gamete.allele.symbol} (${percentLabelEs(gamete.probability)})",
                    modifier = Modifier.width(28.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                secondGametes.indices.forEach { column ->
                    val cell = square.cell(row, column)
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .size(72.dp)
                            .background(
                                color = accent.copy(alpha = if (cell.genotype.isCarrier) 0.22f else 0.10f),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                cell.genotype.notation,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface
                            )
                            Text(
                                percentLabelEs(cell.probability),
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalTertiaryText.current
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProbabilityRow(label: String, probability: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            percentLabelEs(probability),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
