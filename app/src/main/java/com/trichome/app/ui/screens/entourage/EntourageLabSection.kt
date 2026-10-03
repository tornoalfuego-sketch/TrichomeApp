package com.trichome.app.ui.screens.entourage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trichome.app.data.repository.EntourageContent
import com.trichome.app.model.*
import com.trichome.app.model.EntourageHandling.EntourageLabCopy
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.components.accentButtonColors
import com.trichome.app.ui.theme.LocalTertiaryText
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.ui.theme.metricValue
import java.util.Locale

/**
 * T8.3 — the Entourage Lab, a case puzzle.
 *
 * ## F5: two modes on one screen
 *
 * The case is data and the case declares its own [LabMode]. A
 * pharmacological case moves cannabinoid dials and is scored against side-effect
 * ceilings; a [LabMode.HANDLING] case picks a processing route and is scored
 * against what that route does to the compounds. They share the case picker, the
 * verdict panel and the explanation, and nothing else — in particular the
 * pharmacological inputs are not rendered at all for a handling case, because
 * [EntourageLabUi.dialCannabinoids] returns nothing for one.
 *
 * ## What is deliberately not on screen
 *
 * [LabWeights]. Those constants are puzzle numbers, not measurements — the enum
 * says so in its own KDoc — so this section renders the verdict, each axis against
 * the ceiling the *case* declares, the compound readings the handling mode
 * produces, and the shipped notes. A dial or a bar labelled with a raw weight
 * would be a dose-response curve the app cannot support, so
 * [EntourageLabUi.feedback] has no way to reach the weights at all and the tests
 * pin that.
 *
 * The pharmacological efficacy number is the planner's percentage against the
 * case's goal, which is what that efficacy means: how close the terpene set is to
 * the target profile, and nothing about potency. The handling headline number is
 * the share of the selection the route keeps, and its label says so.
 *
 * Every sentence this screen prints comes from `model/` or from the shipped case.
 * A string authored in a composable is a string no JVM test on this classpath can
 * reach, and F3 found three that way.
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
    route: ProcessingMethod?,
    onRoute: (ProcessingMethod?) -> Unit,
    result: LabResult?,
    onEvaluate: (EntourageCase, EntourageSelection) -> Unit,
    caseQuery: String = "",
    onCaseQuery: (String) -> Unit = {},
    caseMode: LabMode? = null,
    onCaseMode: (LabMode?) -> Unit = {},
    themeState: TrichomeThemeState
) {
    val scheme = themeState.colorScheme()
    val tertiary = LocalTertiaryText.current

    if (library.cases.isEmpty()) {
        Text(
            EntourageLabCopy.NO_CASES_ES,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
        return
    }

    // Clamped rather than wrapped: a stale index after the asset changed would
    // otherwise show one case's title above another's dials.
    val requestedCase = library.cases[caseIndex.coerceIn(library.cases.indices)]

    // F11: the catalogue filter. The index is built once per library and the
    // filter runs over its folded haystacks, so a keystroke is a substring test
    // rather than twelve briefs being re-normalised.
    val caseIndexList = remember(library.cases, library.profiles) {
        EntourageCaseSearch.index(library.cases, library.profiles)
    }
    val visibleCases = remember(caseIndexList, caseQuery, caseMode) {
        EntourageCaseSearch.filter(
            EntourageCaseSearch.inMode(caseIndexList, caseMode),
            caseQuery
        )
    }
    // The list everything below works in. Narrowing here rather than inside the
    // `Column` is what makes "the selected case" unable to refer to a row the
    // filter hid: there is only one list, and the chip row is built from it.
    val playableCases = visibleCases.map { it.case }
    // `takeIf { it >= 0 }` rather than `indexOfFirst` alone: a case the filter
    // removed is not at index -1, it is absent, and the fallback pins the panel
    // to the first surviving case instead of throwing on an empty list.
    val safeIndex = playableCases.indexOfFirst { it.id == requestedCase.id }
        .takeIf { it >= 0 } ?: 0
    val case = playableCases.getOrNull(safeIndex) ?: requestedCase

    val goalProfile = remember(case.goal, library.profiles) {
        library.profiles.firstOrNull { it.key == case.goal }
    }
    val dialCannabinoids = remember(case) { EntourageLabUi.dialCannabinoids(case) }
    val handling = case.mode == LabMode.HANDLING
    val routes = remember(case) { EntourageLabUi.handlingRoutes(case) }
    val compoundOptions = remember(case, goalProfile) {
        if (handling) {
            EntourageLabUi.handlingOrder(case)
        } else {
            EntourageLabUi.terpeneOrder(goalProfile)
        }
    }
    val selection = remember(dials, labTerpenes) {
        EntourageLabUi.selectionFromDials(dials, labTerpenes)
    }
    val ceilings = case.shareCeilings()
    val evidenceLabel = remember(case) { EntourageLabUi.evidenceLabelEs(case) }
    val handlingBasis = remember(case) { EntourageLabUi.basisEs(case) }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EntourageSectionHeading(EntourageLabCopy.CASES_ES, themeState)

        OutlinedTextField(
            value = caseQuery,
            onValueChange = onCaseQuery,
            label = { Text(EntourageCaseSearch.FIELD_LABEL_ES) },
            placeholder = { Text(EntourageCaseSearch.FIELD_HINT_ES) },
            singleLine = true,
            trailingIcon = {
                if (caseQuery.isNotEmpty()) {
                    IconButton(onClick = { onCaseQuery("") }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = EntourageCaseSearch.CLEAR_LABEL_ES
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(6.dp))
        Text(
            EntourageCaseSearch.countEs(visibleCases.size, library.cases.size),
            style = MaterialTheme.typography.labelSmall,
            color = tertiary
        )

        Spacer(Modifier.height(8.dp))
        Text(
            EntourageCaseSearch.MODE_FILTER_ES,
            style = MaterialTheme.typography.labelMedium,
            color = tertiary
        )
        Spacer(Modifier.height(4.dp))
        EntourageChipFlow(
            options = listOf<LabMode?>(null) + EntourageCaseSearch.modes,
            selected = setOf(caseMode),
            labelOf = { it?.labelEs ?: EntourageCaseSearch.ALL_MODES_ES },
            onToggle = { onCaseMode(it) }
        )

        Spacer(Modifier.height(8.dp))
        if (playableCases.isEmpty()) {
            Text(
                EntourageCaseSearch.NO_RESULTS_ES,
                style = MaterialTheme.typography.bodyMedium,
                color = tertiary
            )
        } else {
            EntourageChipFlow(
                options = playableCases.indices.toList(),
                selected = setOf(safeIndex),
                labelOf = { EntourageLabCopy.caseChipEs(it + 1, playableCases[it].titleEs) },
                onToggle = onCaseIndex
            )
        }

        Spacer(Modifier.height(12.dp))

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
                    EntourageLabUi.goalLabelEs(case, goalProfile?.labelEs),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    case.mode.labelEs,
                    style = MaterialTheme.typography.labelSmall,
                    color = tertiary
                )
            }
        }

        // F5: the handling case's own evidence framing, printed above the inputs
        // and unconditionally. A claim about what a route does to a harvest with
        // no level attached is the one thing this module refuses to put on
        // screen, and the level plus the limit belong before the chips rather
        // than in a footer nobody scrolls to.
        if (evidenceLabel != null) {
            EntourageSectionHeading(EntourageLabCopy.EVIDENCE_ES, themeState)
            Text(
                evidenceLabel,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary
            )
            if (handlingBasis != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    handlingBasis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface
                )
            }
        }

        // The case's own constraints, printed before the inputs. They are the
        // rules the player is playing against, so they cannot be something the
        // player has to infer from a slider.
        if (case.forbiddenCannabinoids.isNotEmpty()) {
            EntourageSectionHeading(EntourageLabCopy.FORBIDDEN_COMPOUNDS_ES, themeState)
            Text(
                EntourageLabCopy.joinNamesEs(
                    case.forbiddenCannabinoids.sortedBy { it.key }.map { it.labelEs }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.error
            )
        }

        if (ceilings.isNotEmpty()) {
            EntourageSectionHeading(EntourageLabCopy.SHARE_CEILINGS_ES, themeState)
            Text(
                EntourageLabCopy.joinNamesEs(
                    ceilings.map { (cannabinoid, max) ->
                        EntourageLabCopy.shareCeilingEs(cannabinoid.labelEs, max)
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface
            )
        }

        if (case.ceilings.isNotEmpty()) {
            EntourageSectionHeading(EntourageLabCopy.PATIENT_CEILINGS_ES, themeState)
            Text(
                EntourageLabCopy.joinNamesEs(
                    case.ceilings.entries.sortedBy { it.key.key }.map { (axis, max) ->
                        EntourageLabCopy.patientCeilingEs(axis.labelEs, (max * 100).toInt())
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface
            )
        }

        if (case.handlingForbiddenRoutes.isNotEmpty()) {
            EntourageSectionHeading(EntourageLabCopy.FORBIDDEN_ROUTES_ES, themeState)
            Text(
                EntourageLabCopy.joinNamesEs(
                    case.handlingForbiddenRoutes.sortedBy { it.key }.map { it.labelEs }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.error
            )
        }

        EntourageSectionHeading(EntourageLabUi.inputsHeadingEs(case.mode), themeState)

        if (handling) {
            EntourageChipFlow(
                options = routes,
                selected = setOfNotNull(route),
                labelOf = { it.labelEs },
                onToggle = { candidate -> onRoute(if (candidate == route) null else candidate) }
            )
        } else {
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
                            EntourageLabCopy.dialReadoutEs(
                                percent = (value * 100).toInt(),
                                maxPercent = ceiling?.let { (it * 100).toInt() }
                            ),
                            // A number against the limit it may not cross, on a
                            // dial the reader is dragging. That is the value
                            // register: the ceiling only means something if the
                            // current reading can be lined up against it digit for
                            // digit.
                            style = metricValue(),
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
                            String.format(
                                Locale.US,
                                EntourageLabCopy.FORBIDDEN_DIAL_ES,
                                cannabinoid.labelEs
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.error
                        )
                    }
                }
            }
        }

        EntourageSectionHeading(EntourageLabUi.compoundsHeadingEs(case.mode), themeState)

        EntourageChipFlow(
            options = compoundOptions,
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
            // Disabled only when there is nothing to score, and for a handling
            // case also when no route has been picked: the route is the decision,
            // and a verdict with no route would report a result the player never
            // chose.
            enabled = !selection.isEmpty && (!handling || route != null),
            colors = accentButtonColors(scheme.primary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(EntourageLabUi.evaluateLabelEs(case.mode))
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
                // The number *and* its label come from the model, so a handling
                // feedback cannot print a pharmacological efficacy and a
                // pharmacological one cannot print a coverage figure.
                Text(
                    EntourageLabUi.headlineEs(feedback),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface
                )
                Text(
                    feedback.headlineGlossEs,
                    style = MaterialTheme.typography.labelSmall,
                    color = tertiary
                )

                if (feedback.hasCompounds) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        EntourageLabCopy.COMPOUND_READINGS_ES,
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary
                    )
                    Spacer(Modifier.height(4.dp))

                    feedback.compounds.forEach { compound ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                compound.labelEs,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                compound.outcomeEs,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (compound.kept) {
                                    scheme.onSurfaceVariant
                                } else {
                                    scheme.error
                                }
                            )
                        }
                    }
                }

                if (feedback.hasAxes) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        EntourageLabCopy.SIDE_EFFECTS_ES,
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
                                EntourageLabCopy.axisReadingEs(axis.loadPercent, axis.ceilingPercent),
                                // Two percentages in one cell, load against ceiling,
                                // one row per axis and every row read against the
                                // others. A fixed advance is what makes "35% / 60%"
                                // line up with "70% / 60%" down the list.
                                style = metricValue(),
                                color = if (axis.crossed) scheme.error else scheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (axis.crossed) EntourageLabCopy.AXIS_OVER_ES else EntourageLabCopy.AXIS_WITHIN_ES,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (axis.crossed) scheme.error else tertiary
                            )
                        }
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
                    EntourageLabCopy.WHY_ES,
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
