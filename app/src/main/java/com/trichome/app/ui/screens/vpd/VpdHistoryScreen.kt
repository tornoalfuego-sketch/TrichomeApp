package com.trichome.app.ui.screens.vpd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.trichome.app.model.VpdCalculatorForm
import com.trichome.app.model.VpdCalculatorOutcome
import com.trichome.app.model.VpdHistoryChart
import com.trichome.app.model.VpdHistoryPoint
import com.trichome.app.model.VpdLegendEntry
import com.trichome.app.model.VpdProvenance
import com.trichome.app.ui.components.AppTopBar
import com.trichome.app.ui.components.SolidPanel
import com.trichome.app.ui.components.SelectableChip
import com.trichome.app.ui.theme.LocalTertiaryText
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.ui.theme.metricValue
import com.trichome.app.viewmodel.VpdHistoryViewModel
import com.trichome.app.viewmodel.appViewModel

/**
 * The VPD history: a chart of every logged reading, and a calculator.
 *
 * ## Where the numbers come from, and why that is stated on the chart
 *
 * The chart reads `grow_events.vpd` and its two v5 provenance columns. Two of the three
 * series it draws are numbers the app can vouch for — the ones the grower measured, and
 * the ones the app calculated with the inputs recorded beside them — and the third is every
 * reading logged before schema v5, whose origin the database cannot say. That third series
 * is drawn, labelled and counted rather than folded into "medido", because the alternative
 * is publishing an unknown as a sensor reading.
 *
 * The legend states the rule in full, in the grower's language, on the chart rather than in
 * a help page: `provenanceEs`.
 *
 * ## The chart is a Canvas, and it is one scroll owner
 *
 * [VpdHistoryChartPanel] draws the series itself rather than reusing `NativeLineChart`,
 * because that component takes one colour and one series and this chart has three of each.
 * Nothing here declares a `verticalScroll` — the `LazyColumn` owns the vertical axis and a
 * nested scroll in a `LazyColumn` is measured with an infinite maximum height, which throws.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun VpdHistoryScreen(
    plantId: Long,
    navController: NavHostController,
    themeState: TrichomeThemeState
) {
    val vm = appViewModel { VpdHistoryViewModel(it) }
    val scheme = themeState.colorScheme()
    val accent = scheme.primary

    var form by remember { mutableStateOf(VpdCalculatorForm()) }
    var logging by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(plantId) {
        vm.load(plantId)
        vm.observeHistory(plantId)
    }

    val outcome = com.trichome.app.model.VpdCalculator.resolve(form)

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Historial de VPD",
                onNavigateBack = { navController.popBackStack() }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                VpdRangeSelector(
                    days = vm.rangeDays,
                    selectedDays = vm.selectedDays,
                    onSelect = vm::selectRange
                )
            }

            item {
                SolidPanel {
                    VpdHistoryChartPanel(
                        chart = vm.chart,
                        measuredColor = accent,
                        calculatedColor = scheme.tertiary,
                        unknownColor = LocalTertiaryText.current,
                        bandColor = scheme.outlineVariant
                    )
                }
            }

            item {
                SolidPanel {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Calculadora",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            com.trichome.app.model.VpdCalculator.CALCULATOR_SOURCE_ES,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    VpdCalculatorCard(
                        form = form,
                        outcome = outcome,
                        onAirTemperatureChange = { form = form.copy(airTemperature = it) },
                        onHumidityChange = { form = form.copy(humidity = it) },
                        onOffsetChange = { form = form.copy(offset = it) }
                    )
                }
            }

            item {
                val ready = outcome as? VpdCalculatorOutcome.Ready
                Button(
                    onClick = { logging = true },
                    enabled = ready != null,
                    colors = com.trichome.app.ui.components.accentButtonColors(accent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Registrar en Bitácora")
                }
            }

            notice?.let { message ->
                item {
                    SolidPanel {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }

    if (logging) {
        val ready = outcome as? VpdCalculatorOutcome.Ready
        VpdLogDialog(
            // Cleared before the write: the write recomposes, and a dialog still on screen
            // for that frame would be a second target for a double tap.
            calculation = ready?.calculation,
            tentName = vm.tentName,
            onDismiss = { logging = false },
            onConfirm = { formToLog ->
                vm.logReading(
                    plantId = plantId,
                    form = formToLog,
                    onLogged = { ok ->
                        logging = false
                        notice = if (ok) {
                            "Lectura registrada en la bitácora."
                        } else {
                            "No se pudo registrar la lectura. Inténtalo de nuevo."
                        }
                    }
                )
            }
        )
    }
}

/**
 * The range selector: a horizontal chip row, one scroll owner per axis.
 *
 * Horizontal, not vertical, so it does not compete with the `LazyColumn` for the vertical
 * scroll. `LazyRow` inside a `LazyColumn` is fine — they scroll on different axes — and a
 * `Row` of chips would be the shape that overflows at the largest text size.
 */
@Composable
private fun VpdRangeSelector(
    days: List<Int>,
    selectedDays: Int,
    onSelect: (Int) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(days) { option ->
            SelectableChip(
                "$option días",
                selected = option == selectedDays,
                onClick = { onSelect(option) }
            )
        }
    }
}

/**
 * The chart panel: the series, the band ladder, the legend and the empty state.
 *
 * ## The three series
 *
 * The line is one colour for all readings — it is the same quantity on the same axis, and
 * pretending the shape differs would be a claim the data does not support. What differs is
 * the **marker**: a filled circle for measured, a hollow ring for calculated, and a small
 * square for unknown origin. Shape rather than colour alone, because two of the three
 * markers are drawn in colours a theme can make nearly identical, and colour-blind growers
 * are a real fraction of the audience for a chart.
 *
 * The band ladder behind the series is drawn only inside the plotted range, which is what
 * `VpdHistoryChart.visibleBandEdgesKPa` computes — so a stable tent is not carrying four
 * empty bands above its line.
 */
@Composable
private fun VpdHistoryChartPanel(
    chart: VpdHistoryChart,
    measuredColor: Color,
    calculatedColor: Color,
    unknownColor: Color,
    bandColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("VPD a lo largo del cultivo", style = MaterialTheme.typography.titleMedium)

        if (!chart.isDrawable) {
            Text(chart.emptyEs, style = MaterialTheme.typography.bodyMedium)
        } else {
            VpdSeriesCanvas(
                points = chart.points,
                minKPa = chart.minKPa,
                maxKPa = chart.maxKPa,
                bandEdgesKPa = chart.visibleBandEdgesKPa,
                measuredColor = measuredColor,
                calculatedColor = calculatedColor,
                unknownColor = unknownColor,
                bandColor = bandColor
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "min ${com.trichome.app.model.VpdCalculator.es(chart.minKPa, 2)} kPa",
                    style = metricValue(),
                    color = measuredColor,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "max ${com.trichome.app.model.VpdCalculator.es(chart.maxKPa, 2)} kPa",
                    style = metricValue(),
                    color = measuredColor
                )
            }
        }

        // The legend carries all three series even when one is empty: a legend that hides
        // the unknown-origin key is the legend that lets a grower read those points as
        // measurements.
        VpdLegend(chart.legend, measuredColor, calculatedColor, unknownColor)

        if (chart.points.isNotEmpty()) {
            Text(
                chart.provenanceEs,
                style = MaterialTheme.typography.bodySmall,
                color = LocalTertiaryText.current
            )
        }
    }
}

/**
 * The Canvas.
 *
 * @param points already sorted by timestamp; [VpdHistoryBuilder] guarantees the order so
 *   this does not re-sort and cannot disagree with the legend's counts.
 */
@Composable
private fun VpdSeriesCanvas(
    points: List<VpdHistoryPoint>,
    minKPa: Double,
    maxKPa: Double,
    bandEdgesKPa: List<Double>,
    measuredColor: Color,
    calculatedColor: Color,
    unknownColor: Color,
    bandColor: Color
) {
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
    ) {
        val span = (maxKPa - minKPa).takeIf { it > 0.0 } ?: 1.0
        val low = minKPa - span * 0.1
        val high = maxKPa + span * 0.1
        val plotted = (high - low).takeIf { it > 0.0 } ?: 1.0

        fun yFor(value: Double): Float =
            (size.height * (1.0 - ((value - low) / plotted))).toFloat()

        // The band ladder, drawn first so the series sits on top of it.
        bandEdgesKPa.forEach { edge ->
            val y = yFor(edge)
            if (y in 0f..size.height) {
                drawLine(
                    color = bandColor,
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                    strokeWidth = 1f
                )
            }
        }

        val stepX = size.width / (points.size - 1).toFloat()
        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { index, point ->
            val x = index * stepX
            val y = yFor(point.vpdKPa)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = measuredColor.copy(alpha = 0.65f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
        )

        points.forEachIndexed { index, point ->
            val center = androidx.compose.ui.geometry.Offset(index * stepX, yFor(point.vpdKPa))
            when (point.provenance) {
                VpdProvenance.MEASURED -> drawCircle(
                    color = measuredColor,
                    radius = 5f,
                    center = center
                )

                VpdProvenance.CALCULATED -> {
                    // A hollow ring rather than a second filled dot: the outline is
                    // distinguishable from a measurement without relying on hue.
                    drawCircle(
                        color = calculatedColor,
                        radius = 5f,
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                    )
                }

                VpdProvenance.UNKNOWN -> {
                    val half = 3.5f
                    drawRect(
                        color = unknownColor,
                        topLeft = androidx.compose.ui.geometry.Offset(center.x - half, center.y - half),
                        size = androidx.compose.ui.geometry.Size(half * 2, half * 2)
                    )
                }
            }
        }
    }
}

/**
 * The legend.
 *
 * Each entry prints its own label and its own count, and every series is listed even at
 * zero — an empty series that is listed as `0 puntos` tells the grower the app is looking
 * for it, which is the information a hidden series throws away.
 */
@Composable
private fun VpdLegend(
    legend: List<VpdLegendEntry>,
    measuredColor: Color,
    calculatedColor: Color,
    unknownColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        legend.forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Canvas(modifier = Modifier.height(14.dp)) {
                    val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                    when (entry.provenance) {
                        VpdProvenance.MEASURED -> drawCircle(measuredColor, 5f, center)
                        VpdProvenance.CALCULATED -> drawCircle(
                            color = calculatedColor,
                            radius = 5f,
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                        )
                        VpdProvenance.UNKNOWN -> {
                            val half = 3.5f
                            drawRect(
                                color = unknownColor,
                                topLeft = androidx.compose.ui.geometry.Offset(center.x - half, center.y - half),
                                size = androidx.compose.ui.geometry.Size(half * 2, half * 2)
                            )
                        }
                    }
                }
                Text(
                    "${entry.labelEs} · ${entry.countEs}",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalTertiaryText.current,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

