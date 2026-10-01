package com.trichome.app.ui.screens.entourage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trichome.app.data.repository.EntourageContent
import com.trichome.app.model.*
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.components.accentButtonColors
import com.trichome.app.ui.theme.LocalTertiaryText
import com.trichome.app.ui.theme.TrichomeThemeState

/**
 * T8.3 — the Entourage Lab, a clinical-case puzzle.
 *
 * The case is data: a goal, a set of forbidden compounds, a ceiling per
 * cannabinoid share and a ceiling per side-effect axis. The player moves dials
 * and picks terpenes, presses **Evaluar**, and [EntourageLab.solve] returns the
 * verdict.
 *
 * ## What is deliberately not on screen
 *
 * [LabWeights]. Those constants are puzzle numbers, not measurements — the enum
 * says so in its own KDoc — so this section renders the verdict, each axis
 * against the ceiling the *case* declares, and the shipped notes. A dial or a
 * bar labelled with a raw weight would be a dose-response curve the app cannot
 * support, so [EntourageLabUi.feedback] has no way to reach the weights at all
 * and the tests pin that.
 *
 * The efficacy number is the planner's percentage against the case's goal, which
 * is what the efficacy means: how close the terpene set is to the target
 * profile, and nothing about potency.
 *
 * A plain [Column] — the module's single `LazyColumn` owns the scroll. See
 * [EntourageNetworkSection] for why a nested vertical scroll is not an option.
 */
@Composable
fun EntourageLabSection(
    library: EntourageContent,
    caseIndex: Int,
    onCaseIndex: (Int) -> Unit,
    dials: Map<Cannabinoid, Float>,
    onDials: (Map<Cannabinoid, Float>) -> Unit,
    labTerpenes: Set<EntourageTerpene>,
    onLabTerpenes: (Set<EntourageTerpene>) -> Unit,
    result: LabResult?,
    onEvaluate: (EntourageCase, EntourageSelection) -> Unit,
    themeState: TrichomeThemeState
) {
    val scheme = themeState.colorScheme()
    val tertiary = LocalTertiaryText.current

    if (library.cases.isEmpty()) {
        Text(
            "El catálogo no trae casos clínicos para el Laboratorio.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
        return
    }

    // Clamped rather than wrapped: a stale index after the asset changed would
    // otherwise show one case's title above another's dials.
    val safeIndex = caseIndex.coerceIn(library.cases.indices)
    val case = library.cases[safeIndex]
    val goalProfile = remember(case.goal, library.profiles) {
        library.profiles.firstOrNull { it.key == case.goal }
    }
    val dialCannabinoids = remember(case) { EntourageLabUi.dialCannabinoids(case) }
    val terpeneOptions = remember(goalProfile) { EntourageLabUi.terpeneOrder(goalProfile) }
    val selection = remember(dials, labTerpenes) {
        EntourageLabUi.selectionFromDials(dials, labTerpenes)
    }
    val ceilings = case.shareCeilings()

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EntourageSectionHeading("Caso", themeState)

        EntourageChipFlow(
            options = library.cases.indices.toList(),
            selected = setOf(safeIndex),
            labelOf = { "${it + 1}. ${library.cases[it].titleEs}" },
            onToggle = onCaseIndex
        )

        SolidPanel(contentColor = scheme.onSurface) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    case.titleEs,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    case.briefEs,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Objetivo del caso: ${goalProfile?.labelEs ?: case.goal.labelEs}",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary
                )
            }
        }

        // The case's own constraints, printed before the dials. They are the
        // rules the player is playing against, so they cannot be something the
        // player has to infer from a slider.
        if (case.forbiddenCannabinoids.isNotEmpty()) {
            EntourageSectionHeading("Compuestos que el caso descarta", themeState)
            Text(
                case.forbiddenCannabinoids.sortedBy { it.key }
                    .joinToString(" · ") { it.labelEs },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.error
            )
        }

        if (ceilings.isNotEmpty()) {
            EntourageSectionHeading("Límites de aporte", themeState)
            Text(
                ceilings.joinToString(" · ") { (cannabinoid, max) ->
                    "${cannabinoid.labelEs} hasta $max%"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface
            )
        }

        if (case.ceilings.isNotEmpty()) {
            EntourageSectionHeading("Techos del paciente", themeState)
            Text(
                case.ceilings.entries.sortedBy { it.key.key }
                    .joinToString(" · ") { (axis, max) ->
                        "${axis.labelEs} hasta ${(max * 100).toInt()}%"
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface
            )
        }

        EntourageSectionHeading("Aportes", themeState)

        dialCannabinoids.forEach { cannabinoid ->
            val value = dials[cannabinoid] ?: 0f
            val forbidden = cannabinoid in case.forbiddenCannabinoids
            val ceiling = case.maxCannabinoidShare[cannabinoid]
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        cannabinoid.labelEs,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${(value * 100).toInt()}%" +
                            if (ceiling != null) " · máx ${(ceiling * 100).toInt()}%" else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (forbidden) scheme.error else scheme.primary
                    )
                }
                Slider(
                    value = value,
                    onValueChange = { moved ->
                        onDials(dials + (cannabinoid to EntourageLabUi.clampDial(moved)))
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                if (forbidden) {
                    Text(
                        "Este caso descarta ${cannabinoid.labelEs}: cualquier aporte " +
                            "da por perdido el caso.",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.error
                    )
                }
            }
        }

        EntourageSectionHeading("Terpenos", themeState)
        EntourageChipFlow(
            options = terpeneOptions,
            selected = labTerpenes,
            labelOf = { it.labelEs },
            onToggle = { candidate ->
                onLabTerpenes(
                    if (candidate in labTerpenes) labTerpenes - candidate
                    else labTerpenes + candidate
                )
            }
        )

        Button(
            onClick = { onEvaluate(case, selection) },
            // Disabled only when there is nothing to score. A case with no
            // compound selected at all is an unanswered puzzle, not a
            // submission, and scoring it would report a result the player never
            // chose.
            enabled = !selection.isEmpty,
            colors = accentButtonColors(scheme.primary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Evaluar la combinación")
        }

        if (result == null) return@Column

        val feedback = remember(result) { EntourageLabUi.feedback(result) }

        SolidPanel(contentColor = scheme.onSurface) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    feedback.verdictEs,
                    style = MaterialTheme.typography.titleMedium,
                    color = when (feedback.verdict) {
                        LabVerdict.OPTIMO -> scheme.primary
                        LabVerdict.VIABLE -> scheme.onSurface
                        else -> scheme.error
                    },
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Eficacia contra el objetivo: ${feedback.efficacyPercent}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface
                )
                Text(
                    "Mide qué tan cerca están tus terpenos del perfil objetivo del caso.",
                    style = MaterialTheme.typography.labelSmall,
                    color = tertiary
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    "Efectos secundarios",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary
                )
                Spacer(Modifier.height(4.dp))

                feedback.axes.forEach { axis ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            axis.labelEs,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${axis.loadPercent}% / ${axis.ceilingPercent}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (axis.crossed) scheme.error else scheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (axis.crossed) "supera" else "dentro",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (axis.crossed) scheme.error else tertiary
                        )
                    }
                }
            }
        }

        feedback.notesEs.forEach { note ->
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface
            )
        }

        Surface(
            color = scheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "Por qué el caso es así",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    case.explanationEs,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface
                )
            }
        }
    }
}
