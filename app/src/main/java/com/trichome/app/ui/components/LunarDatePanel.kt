package com.trichome.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trichome.app.R
import com.trichome.app.model.LunarDateFormatter
import com.trichome.app.model.LunarTimeline
import com.trichome.app.ui.theme.LocalMetricValue
import com.trichome.app.ui.theme.LocalTertiaryText

/**
 * The lunar engine, reachable for a date the grower picks rather than only for
 * tonight.
 *
 * ## Why a dialog and not a panel
 *
 * The calendar screen's body is a single `verticalScroll` and its top bar is
 * explicitly unscrollable — a comment in `CalendarScreen` records that a
 * scrollable child of a scrollable is the shape that shipped as a crash once.
 * A date selector with a stepper, a phase card, a tip and a drift paragraph is
 * far taller than the space above that scroll, so putting it inline would mean
 * either a second scroll owner or a permanent column that pushes the month grid
 * off screen. A dialog owns its own composition, so the body below is the only
 * scroll owner in it.
 *
 * ## No copy here
 *
 * Every sentence on this panel comes out of [LunarTimeline] or a
 * [com.trichome.app.model.LunarReading]. Compose has no unit-test runtime on
 * this project's `test` classpath, so a sentence typed into a `Text` is a
 * sentence no JVM test can hold to the honesty rules this module runs on — in
 * particular to the rule that a calculated phase announces that it is
 * calculated.
 *
 * ## Scroll order
 *
 * `heightIn` **before** `verticalScroll`, for the reason
 * `MasterBlenderDialog` documents at length: the cap has to constrain what the
 * scroll may occupy, not what the content may be. The reverse order gives the
 * scroll a viewport exactly as tall as its content and no range to move over.
 */
@Composable
fun LunarDatePanel(
    referenceMillis: Long,
    onDismiss: () -> Unit
) {
    // The offset is the only state, and it is a plain signed day count. It
    // starts at zero every time the dialog opens, so a grower who closed the
    // panel and came back gets today rather than whatever they left behind.
    var offsetDays by remember { mutableIntStateOf(0) }

    val reading = remember(referenceMillis, offsetDays) {
        LunarTimeline.readingAt(referenceMillis, offsetDays)
    }
    val bodyMaxHeight = remember { LunarPanelLayout.MAX_BODY_HEIGHT_DP.dp }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LunarTimeline.TITLE_ES) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = bodyMaxHeight)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { offsetDays -= 1 },
                        enabled = LunarTimeline.canStepFurther(offsetDays, back = true)
                    ) {
                        Icon(
                            Icons.Filled.ChevronLeft,
                            contentDescription = LunarTimeline.PREVIOUS_DAY_ES
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = reading.dateLabelEs,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = LunarDateFormatter.localDateEs(
                                reading.epochMillis,
                                java.time.ZoneId.systemDefault()
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalTertiaryText.current
                        )
                    }
                    IconButton(
                        onClick = { offsetDays += 1 },
                        enabled = LunarTimeline.canStepFurther(offsetDays, back = false)
                    ) {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = LunarTimeline.NEXT_DAY_ES
                        )
                    }
                }

                if (offsetDays != 0) {
                    TextButton(onClick = { offsetDays = 0 }) {
                        Icon(Icons.Filled.Today, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(LunarTimeline.BACK_TO_TODAY_ES)
                    }
                }

                Spacer(Modifier.height(4.dp))

                SolidPanel {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = reading.snapshot.phase.labelEs,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = reading.trendLabelEs,
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalTertiaryText.current
                            )
                        }
                        // Its own line rather than inside the trend sentence, and
                        // in the instrumented value role: the illumination is a
                        // reading, and `LunarPhaseBar`'s prose treatment is a
                        // documented exception that this panel does not inherit.
                        Text(
                            text = reading.illuminationEs,
                            style = LocalMetricValue.current
                        )
                        Text(
                            text = LunarTimeline.offsetLabelEs(reading.offsetDays),
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalTertiaryText.current
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // The tip is the point of the screen, so it renders
                // unconditionally. A panel whose only advice sits behind a
                // disclosure is a phase table.
                SolidPanel {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = reading.tipHeadlineEs,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = reading.tipAdviceEs,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = LunarTimeline.nextPhaseLineEs(
                        reading.nextPhase.labelEs,
                        reading.daysUntilNextPhase
                    ),
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(10.dp))

                // What is measured, what is calculated, and the `≈`-marked
                // bound. All three on the open panel: the drift is the reason
                // this screen can answer for a date at all, and burying it would
                // make a mean-month model read as an ephemeris.
                Text(
                    text = reading.measuredLabelEs,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalTertiaryText.current
                )
                Text(
                    text = reading.calculatedLabelEs,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalTertiaryText.current
                )
                Text(
                    text = reading.driftEs,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalTertiaryText.current
                )
                Text(
                    text = LunarTimeline.DISCLAIMER_ES,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalTertiaryText.current
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

/**
 * The dialog body's height cap, as a number.
 *
 * Extractable so the arithmetic is a JVM test rather than a `LocalConfiguration`
 * read nothing can pin — the same split as `BlenderLayout.bodyMaxHeightDp`.
 */
object LunarPanelLayout {

    /**
     * Cap on the body, in dp.
     *
     * 420dp is roughly two thirds of a typical phone's height: enough for the
     * stepper, the phase card, the tip and the drift paragraph to all be
     * reachable, and short enough that the dialog's confirm button is not pushed
     * off the bottom of a small screen.
     */
    const val MAX_BODY_HEIGHT_DP: Int = 420
}