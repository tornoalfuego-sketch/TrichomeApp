package com.trichome.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.trichome.app.model.HeatmapHourState
import com.trichome.app.model.HeatmapRow
import com.trichome.app.model.SupercycleSchedule
import com.trichome.app.ui.theme.LocalTertiaryText
import com.trichome.app.ui.theme.metricValue

/**
 * The photoperiod of one supercycle, drawn as twenty-four cells of the real day.
 *
 * ## Every number on this card is resolved in `model/`
 *
 * [SupercycleScheduleBuilder] decides which hour is lit, where the phase boundaries fall, how
 * the twenty-four hours break into two rows, and every Spanish sentence the card prints.
 * This file is layout: it takes a state and a colour and puts them in a rectangle. That
 * split is not tidiness — this project has no Compose unit-test runtime, and three
 * separate shipped bugs came from logic authored inside a composable, including a
 * `weight(0f)` that threw at compose time and took every terpene page down.
 *
 * ## The layout
 *
 * **Two rows of twelve, not one row of twenty-four, and not a horizontal scroll.**
 * Twenty-four cells across this screen's width is about 13 dp each: at that size "lit"
 * and "dark" stop being distinguishable at a glance and the hour ticks collapse into
 * noise. Twelve per row is about 26 dp — wide enough to read as filled or empty without
 * effort. A horizontal scroll was the other candidate and loses on both counts that
 * matter: it hides half the day behind a discoverability problem, and it adds a second
 * scroll axis to a screen that already owns the vertical one
 * (`ScrollOwnershipTest` documents what happens when two owners meet).
 *
 * ## The colours, and why not the obvious ones
 *
 * The encoding is **fill density, not hue.** A lit cell is `secondaryContainer` at full
 * strength; a dark cell is `surfaceVariant`; a transition cell is `surfaceVariant` with an
 * `outline` border, because it is a cell whose answer is "both"; a pending cell is
 * `surfaceVariant` at reduced alpha, because "the cycle has not reached here" is an
 * absence, not a state.
 *
 * `secondaryContainer` and not `primaryContainer`, and that is the whole colour argument.
 * `primaryContainer` is derived from the user's accent — `containerFor(accent, surface)` —
 * so a grid drawn with it repaints every time the accent changes, which is the defect
 * `AccentRoleSeparationTest` exists to hold the line on. `secondaryContainer` comes from
 * the theme's own `secondaryBase` and is the app's established "a distinct surface that
 * is not the accent" role. It is also not a hue literal: green, amber and blue would be
 * colours no theme's contrast checks ever see, and the four shipped palettes genuinely
 * differ — the same `secondaryBase` is mint on Brote Verde and burnt orange on
 * Cosecha de Otoño.
 *
 * `primary` — the accent — is **deliberately not used anywhere on this card.** Beyond the
 * repaint, it is not the accent's meaning: the accent means "the thing you chose", not
 * "on". The "now" marker uses `onSurface` for the same reason.
 *
 * ## What the card admits to
 *
 * The cycle is 26 h or 32 h and the day is 24 h, so a fixed wall-clock grid cannot show
 * a repeating daily pattern. The card says so in
 * [SupercycleSchedule.driftExplanationEs], which changes with the drift: a 24 h cycle
 * genuinely does repeat, and a card that said otherwise would be lying in the other
 * direction. A picture that implies a 24-hour period when the cycle is 26 is a lie told
 * with a graphic.
 *
 * ## Scroll
 *
 * None. The card is mounted inside `SuperCycleScreen`'s column, which already owns the
 * vertical axis.
 */
@Composable
fun SupercycleHeatmap(
    schedule: SupercycleSchedule,
    modifier: Modifier = Modifier
) {
    // A zero-length cycle or a missing anchor has no phases to draw. Returning is the
    // honest move: twenty-four identical cells would read as "always dark", which is a
    // claim the engine refused to make.
    if (!schedule.isRenderable) return

    val scheme = MaterialTheme.colorScheme

    SolidPanel(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(schedule.titleEs, style = MaterialTheme.typography.titleMedium)

            Text(
                text = schedule.photoperiodLabelEs,
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = schedule.phaseLabelEs,
                        // The headline register: this is the answer the twenty-four cells
                        // below exist to illustrate, so it is judged on its own rather
                        // than against a neighbouring number.
                        style = metricValue()
                    )
                    Text(
                        text = schedule.phaseDetailEs,
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalTertiaryText.current
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = schedule.driftValueEs,
                        style = metricValue()
                    )
                    Text(
                        text = schedule.driftLabelEs,
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalTertiaryText.current
                    )
                }
            }

            schedule.rows.forEach { row ->
                HourRow(
                    row = row,
                    lightColor = scheme.secondaryContainer,
                    darkColor = scheme.surfaceVariant,
                    borderColor = scheme.outline
                )
            }

            HeatmapLegend(schedule = schedule)
            Spacer(Modifier.height(2.dp))
            // The "now" rule gets its own key. A 3 dp bar under one cell with nothing
            // explaining it reads as a rendering artefact, and the hour the card is
            // about is the one hour a grower looks for first.
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = schedule.nowMarkerLabelEs,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalTertiaryText.current
                )
            }

            // Two load-bearing sentences: what the map is, and what it is not. Both
            // come from the model because their content changes with the drift.
            Text(
                text = schedule.driftExplanationEs,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * One row of twelve cells with its hour ticks underneath.
 *
 * The ticks share the cells' column geometry rather than being a second four-item row,
 * because a separate row of four labels positions itself against its own arrangement
 * and a `00` lands over `01`.
 */
@Composable
private fun HourRow(
    row: HeatmapRow,
    lightColor: Color,
    darkColor: Color,
    borderColor: Color
) {
    val nowLabel = MaterialTheme.colorScheme.onSurface

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // The row's own span, from the model. Two rows of twelve otherwise read as two
        // twelve-hour periods, and a grower checking whether the dark period starts at
        // night would read the wrong row.
        Text(
            text = row.rangeEs,
            style = MaterialTheme.typography.labelSmall,
            color = LocalTertiaryText.current
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            row.cells.forEach { cell ->
                val fill = when (cell.state) {
                    HeatmapHourState.LIT -> lightColor
                    // Reduced alpha rather than a fourth role: "the cycle has not
                    // reached here" is the absence of a phase, and an absent phase is
                    // the absence of paint.
                    HeatmapHourState.PENDING -> darkColor.copy(alpha = 0.35f)
                    HeatmapHourState.DARK -> darkColor
                    // It is both, so it gets the dark fill and an explicit edge: the
                    // boundary is inside the cell and cannot be drawn across it.
                    HeatmapHourState.TRANSITION -> darkColor
                }
                val edge = when (cell.state) {
                    HeatmapHourState.LIT -> BorderStroke(0.dp, Color.Transparent)
                    HeatmapHourState.TRANSITION -> BorderStroke(1.dp, borderColor)
                    else -> BorderStroke(0.dp, Color.Transparent)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        // Read aloud as one cell, so a screen-reader user is told the
                        // state of an hour instead of hearing a list of hour numbers.
                        .clearAndSetSemantics { contentDescription = cell.descriptionEs },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .background(fill, RoundedCornerShape(4.dp))
                            .border(edge, RoundedCornerShape(4.dp))
                    )
                    // The "now" rule sits under its own cell rather than as a border on
                    // it, because a border would be confused with the transition edge.
                    // A cell that is not "now" still reserves the 3dp, so the rows stay
                    // aligned whether or not any cell carries the marker.
                    if (cell.isNow) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(nowLabel, RoundedCornerShape(2.dp))
                        )
                    } else {
                        Spacer(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .fillMaxWidth()
                                .height(3.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            row.ticks.forEach { tick ->
                Text(
                    text = tick.orEmpty(),
                    // The value register: hour labels are a measured scale read against
                    // each other, not prose. `labelSmall` would be 11sp proportional and
                    // the two-digit labels would not line up with the cells above them.
                    style = metricValue(),
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/**
 * The four fills, named.
 *
 * Without it the card is a picture nobody can decode, and the transition swatch in
 * particular looks like a rendering artefact rather than a statement about the hour.
 */
@Composable
private fun HeatmapLegend(schedule: SupercycleSchedule) {
    val scheme = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        schedule.legend.forEach { entry ->
            val swatch = when (entry.state) {
                HeatmapHourState.LIT -> scheme.secondaryContainer
                HeatmapHourState.PENDING -> scheme.surfaceVariant.copy(alpha = 0.35f)
                HeatmapHourState.DARK -> scheme.surfaceVariant
                HeatmapHourState.TRANSITION -> scheme.surfaceVariant
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(swatch, RoundedCornerShape(3.dp))
                        .border(
                            if (entry.state == HeatmapHourState.TRANSITION) {
                                BorderStroke(1.dp, scheme.outline)
                            } else {
                                BorderStroke(0.dp, Color.Transparent)
                            },
                            RoundedCornerShape(3.dp)
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.labelEs,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalTertiaryText.current
                )
            }
        }
    }
}