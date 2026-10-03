package com.trichome.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * The VPD history: the calculator, the provenance rules, the chart and the journal write.
 *
 * ## What this test is load-bearing for
 *
 * The defect this whole file exists to prevent is a derived number rendered on the same axis
 * as a sensor reading with nothing saying which is which. That is the failure
 * `EstimatedClimate` was built to prevent in the climate card, and it is the failure that
 * would arrive again in the VPD chart if provenance were a convention instead of a column.
 *
 * So the assertions below are heavily weighted towards **origin**: that a null resolves to
 * UNKNOWN, that UNKNOWN is not writable, that a calculated value carries the offset that
 * produced it, and that the chart keeps three separate series rather than folding the
 * unknown one into "measured".
 *
 * ## The clock
 *
 * Nothing here reads the wall clock. [VpdCalculation.calculate] is pure, the planner takes
 * `nowMillis` as a parameter, and the log validator takes the `ZoneId` it resolves a typed
 * date in. That is what makes every instant in these tests a pinned constant rather than
 * something that drifts between runs.
 */
class VpdHistoryTest {

    private val madrid: ZoneId = ZoneId.of("Europe/Madrid")
    private val utc: ZoneId = ZoneId.of("UTC")

    /* ── Provenance ────────────────────────────────────────────────────── */

    @Test
    fun aNullProvenanceResolvesToUnknownAndNeverToMeasured() {
        // The one that matters most. Defaulting a pre-v5 row to MEASURED would publish every
        // derived number this project ever stored as a sensor reading.
        assertEquals(VpdProvenance.UNKNOWN, VpdProvenance.fromStorageKey(null))
        assertEquals(VpdProvenance.UNKNOWN, VpdProvenance.fromStorageKey(""))
        assertEquals(VpdProvenance.UNKNOWN, VpdProvenance.fromStorageKey("measured"))
        assertEquals(
            "a typo must not silently read as the measured series",
            VpdProvenance.UNKNOWN,
            VpdProvenance.fromStorageKey("MEASUREDD")
        )
    }

    @Test
    fun onlyTwoOfTheThreeProvenancesMayBeWritten() {
        assertTrue(VpdProvenance.MEASURED.isWritable)
        assertTrue(VpdProvenance.CALCULATED.isWritable)
        assertFalse(
            "UNKNOWN is what an absent provenance resolves to, so writing it would store " +
                "the absence of information as information",
            VpdProvenance.UNKNOWN.isWritable
        )
        assertEquals(2, VpdProvenance.entries.count { it.isWritable })
    }

    @Test
    fun everyProvenanceCarriesSpanishThatNamesWhatItIs() {
        // The labels are the whole mechanism: they are what the legend and the dialog print.
        listOf(VpdProvenance.MEASURED, VpdProvenance.CALCULATED, VpdProvenance.UNKNOWN).forEach {
            assertTrue("${it.name} needs a label", it.labelEs.isNotBlank())
            assertTrue("${it.name} needs an explanation", it.explanationEs.isNotBlank())
        }
        assertTrue(VpdProvenance.UNKNOWN.explanationEs.contains("antes"))
    }

    /* ── The calculator ────────────────────────────────────────────────── */

    @Test
    fun leafVpdIsBelowAirVpdForACoolerLeaf() {
        // es(T) rises exponentially, so a leaf below air temperature sits at a lower saturation
        // pressure than the air around it and the deficit shrinks. If this ever inverted, the
        // "leaf" reading would be the least humid thing in the room.
        val result = VpdCalculation.calculate(airTemperatureC = 25.0, relativeHumidityPercent = 55.0, offsetC = 3.0)

        assertTrue(
            "leaf ${result.leafVpdKPa} should be below air ${result.airVpdKPa}",
            result.leafVpdKPa < result.airVpdKPa
        )
        assertTrue(result.isPlausible)
        assertEquals(22.0, result.leafTemperatureC, 1e-9)
        assertEquals(result.leafVpdKPa, result.plottedKPa, 0.0)
    }

    @Test
    fun aZeroOffsetMakesLeafAndAirVpdIdentical() {
        // With no offset the leaf *is* the air, so the two numbers must be the same. A chart
        // that showed two different lines for zero offset would be drawing a distinction the
        // inputs do not support.
        val result = VpdCalculation.calculate(airTemperatureC = 24.0, relativeHumidityPercent = 60.0, offsetC = 0.0)
        assertEquals(result.airVpdKPa, result.leafVpdKPa, 1e-9)
    }

    @Test
    fun saturatedAirIsZeroDeficitAndNeverNegative() {
        val result = VpdCalculation.calculate(airTemperatureC = 22.0, relativeHumidityPercent = 100.0, offsetC = 4.0)

        assertEquals(0.0, result.leafVpdKPa, 1e-9)
        assertEquals(0.0, result.airVpdKPa, 1e-9)
        assertTrue("the clamp must hold", result.isPlausible)
    }

    @Test
    fun aHugeOffsetIsClampedRatherThanReportedAsANegativeDeficit() {
        // Leaf colder than the room by a lot, in saturated air: es(leaf) can fall below the
        // actual vapour pressure. The physical reading is "condensation", not "negative
        // deficit", so the value clamps at zero.
        val result = VpdCalculation.calculate(
            airTemperatureC = 20.0,
            relativeHumidityPercent = 98.0,
            offsetC = 12.0
        )

        assertEquals(0.0, result.leafVpdKPa, 1e-9)
        assertTrue(result.leafVpdKPa >= 0.0)
        assertEquals(VpdCalculation.MAX_PLAUSIBLE_OFFSET_C, result.offsetC, 1e-9)
    }

    @Test
    fun aNegativeOffsetIsCoercedToZeroBecauseALeafCannotBeWarmerThanTheAir() {
        val result = VpdCalculation.calculate(airTemperatureC = 24.0, relativeHumidityPercent = 50.0, offsetC = -8.0)
        assertEquals(0.0, result.offsetC, 1e-9)
        assertEquals(24.0, result.leafTemperatureC, 1e-9)
    }

    @Test
    fun theCalculatorReusesAmbientClimateRatherThanReimplementingIt() {
        // The F1 boiling-point and F2 temperature-model defects were both a second copy of a
        // physically fixed relation. The calculator must agree with AmbientClimate exactly, on
        // every point of a grid, rather than "closely".
        for (temperature in -10..40 step 5) {
            for (humidity in 20..95 step 5) {
                for (offset in 0..8 step 2) {
                    val result = VpdCalculation.calculate(
                        airTemperatureC = temperature.toDouble(),
                        relativeHumidityPercent = humidity.toDouble(),
                        offsetC = offset.toDouble()
                    )
                    assertEquals(
                        "air VPD at $temperature C / $humidity % must match AmbientClimate",
                        AmbientClimate.vpdKPa(temperature.toDouble(), humidity.toDouble()),
                        result.airVpdKPa,
                        1e-9
                    )
                    // The raw expression can go negative where the implementation clamps at zero, so the
                    // comparison is against the clamp — otherwise this test would fail on the
                    // saturated-air corner rather than on a reimplemented formula.
                    val rawLeaf = AmbientClimate.saturationVapourPressureKPa(
                        temperature - offset.toDouble()
                    ) - AmbientClimate.actualVapourPressureKPa(
                        temperature.toDouble(),
                        humidity.toDouble()
                    )
                    assertEquals(
                        "leaf VPD must use AmbientClimate's own saturation function",
                        rawLeaf.coerceAtLeast(0.0),
                        result.leafVpdKPa,
                        1e-9
                    )
                }
            }
        }
    }

    /* ── The form ──────────────────────────────────────────────────────── */

    @Test
    fun theDefaultsResolveAndAreLabelledAsCalculated() {
        val outcome = VpdCalculator.resolve(VpdCalculatorForm())

        assertTrue(outcome is VpdCalculatorOutcome.Ready)
        val ready = outcome as VpdCalculatorOutcome.Ready
        assertEquals(VpdProvenance.CALCULATED, ready.display.provenance)
        assertEquals("Calculado", ready.display.provenanceLabelEs)
        // The number itself must say so on the same line, not in a footnote.
        assertTrue(ready.display.provenanceExplanationEs.contains("Calculado"))
    }

    @Test
    fun spanishAndUSDecimalSpellingsBothParse() {
        // A grower whose keyboard offers a comma must not be told the field is invalid.
        assertEquals(23.5, VpdCalculator.parse("23,5")!!, 1e-9)
        assertEquals(23.5, VpdCalculator.parse("23.5")!!, 1e-9)
        assertEquals(24.0, VpdCalculator.parse(" 24 ")!!, 1e-9)
        assertEquals(-1.5, VpdCalculator.parse("-1.5")!!, 1e-9)
    }

    @Test
    fun anUninterpretableFieldIsRejectedRatherThanDefaulted() {
        // The EstimatedClimate defect one level down: a calculator that substitutes 24 C for
        // "abc" answers a question nobody asked and produces a plausible-looking VPD.
        listOf("abc", "", "NaN", "Infinity", "1.2.3", "12%", "1e5").forEach { input ->
            assertEquals(
                "`$input` must not parse",
                null,
                VpdCalculator.parse(input)
            )
        }

        val outcome = VpdCalculator.resolve(
            VpdCalculatorForm(airTemperature = "abc", humidity = "60", offset = "2")
        )
        assertTrue(outcome is VpdCalculatorOutcome.Invalid)
        assertEquals(
            VpdCalculatorField.AIR_TEMPERATURE,
            (outcome as VpdCalculatorOutcome.Invalid).field
        )
        assertTrue(outcome.problemEs.isNotBlank())
    }

    @Test
    fun eachBadFieldIsNamedSoOnlyThatFieldIsMarked() {
        val badTemperature = VpdCalculator.resolve(
            VpdCalculatorForm(airTemperature = "99", humidity = "60", offset = "2")
        )
        assertEquals(
            VpdCalculatorField.AIR_TEMPERATURE,
            (badTemperature as VpdCalculatorOutcome.Invalid).field
        )

        val badHumidity = VpdCalculator.resolve(
            VpdCalculatorForm(airTemperature = "24", humidity = "140", offset = "2")
        )
        assertEquals(
            VpdCalculatorField.HUMIDITY,
            (badHumidity as VpdCalculatorOutcome.Invalid).field
        )

        val badOffset = VpdCalculator.resolve(
            VpdCalculatorForm(airTemperature = "24", humidity = "60", offset = "-1")
        )
        assertEquals(
            VpdCalculatorField.OFFSET,
            (badOffset as VpdCalculatorOutcome.Invalid).field
        )
    }

    @Test
    fun theRejectionCopyNamesTheAcceptedRange() {
        val outcome = VpdCalculator.resolve(
            VpdCalculatorForm(airTemperature = "24", humidity = "140", offset = "2")
        ) as VpdCalculatorOutcome.Invalid
        assertTrue("the grower needs the bound, not just 'no'", outcome.problemEs.contains("100"))
    }

    @Test
    fun theReadoutSpeaksSpanishWithAComma() {
        val ready = VpdCalculator.resolve(
            VpdCalculatorForm(airTemperature = "24", humidity = "60", offset = "2")
        ) as VpdCalculatorOutcome.Ready

        assertTrue(
            "Spanish writes 0,84 not 0.84: got ${ready.display.leafVpdEs}",
            ready.display.leafVpdEs.contains(",")
        )
        assertTrue(ready.display.leafVpdEs.endsWith("kPa"))
        assertTrue(ready.display.bandLabelEs.isNotBlank())
    }

    /* ── The chart ─────────────────────────────────────────────────────── */

    @Test
    fun theChartKeepsThreeSeparateSeries() {
        val chart = VpdHistoryBuilder.build(
            listOf(
                row(1, 1_000L, 0.9f, VpdProvenance.MEASURED.storageKey),
                row(2, 2_000L, 1.2f, VpdProvenance.CALCULATED.storageKey),
                row(3, 3_000L, 1.4f, null),
                row(4, 4_000L, 1.1f, null)
            )
        )

        assertEquals(4, chart.totalCount)
        assertEquals(2, chart.unknownCount)
        assertEquals(
            listOf("Medido" to 1, "Calculado" to 1, "Origen desconocido" to 2),
            chart.legend.map { it.labelEs to it.count }
        )
    }

    @Test
    fun theLegendAlwaysCarriesAllThreeKeysEvenWhenTwoSeriesAreEmpty() {
        // A legend that hides the unknown-origin key is the legend that lets a grower read
        // those points as measurements.
        val chart = VpdHistoryBuilder.build(
            listOf(row(1, 1_000L, 0.9f, VpdProvenance.MEASURED.storageKey))
        )

        assertEquals(3, chart.legend.size)
        assertEquals(
            listOf(VpdProvenance.MEASURED, VpdProvenance.CALCULATED, VpdProvenance.UNKNOWN),
            chart.legend.map { it.provenance }
        )
        assertTrue(chart.legend.all { it.countEs.isNotBlank() })
        assertEquals("0 puntos", chart.legend.first { it.provenance == VpdProvenance.CALCULATED }.countEs)
    }

    @Test
    fun onePointIsARreadoutAndNotASeries() {
        assertFalse(
            "a single point cannot be a line",
            VpdHistoryBuilder.build(listOf(row(1, 1_000L, 0.9f, null))).isDrawable
        )
        assertTrue(
            VpdHistoryBuilder.build(
                listOf(row(1, 1_000L, 0.9f, null), row(2, 2_000L, 0.8f, null))
            ).isDrawable
        )
    }

    @Test
    fun aRowWithNoVpdIsNotAPointAndNotAZero() {
        // Most journal rows are irrigation notes. Plotting one as 0.0 would draw a flat line
        // along the floor that no grower ever measured.
        val chart = VpdHistoryBuilder.build(
            listOf(
                row(1, 1_000L, 0.9f, null),
                row(2, 1_100L, null, null),
                row(3, 2_000L, 0.8f, null)
            )
        )

        assertEquals(2, chart.points.size)
        assertEquals(listOf(1_000L, 2_000L), chart.points.map { it.timestamp })
    }

    @Test
    fun theBandIsDerivedFromThePlottedValueAndNotStored() {
        val chart = VpdHistoryBuilder.build(
            listOf(
                row(1, 1_000L, 0.8f, null),
                row(2, 2_000L, 1.2f, null),
                row(3, 3_000L, 2.0f, null)
            )
        )

        assertEquals(
            listOf(VpdBand.OPTIMAL_VEGETATIVE, VpdBand.OPTIMAL_FLOWERING, VpdBand.HIGH),
            chart.points.map { it.band }
        )
    }

    @Test
    fun theChartSortsByTimestampSoAnUnsortedQueryStillReadsLeftToRight() {
        val chart = VpdHistoryBuilder.build(
            listOf(
                row(3, 3_000L, 1.4f, null),
                row(1, 1_000L, 0.9f, null),
                row(2, 2_000L, 1.1f, null)
            )
        )

        assertEquals(listOf(1_000L, 2_000L, 3_000L), chart.points.map { it.timestamp })
    }

    @Test
    fun twoRowsInTheSameMillisecondStillHaveADefinedOrder() {
        val chart = VpdHistoryBuilder.build(
            listOf(row(9, 1_000L, 1.5f, null), row(2, 1_000L, 0.7f, null))
        )
        // The tie is broken by the row id: id 2 before id 9, at the same timestamp.
        val ordered = chart.points.map { it.vpdKPa.toFloat() }
        assertEquals(2, ordered.size)
        assertEquals(0.7f, ordered[0], 1e-6f)
        assertEquals(1.5f, ordered[1], 1e-6f)
    }

    @Test
    fun anEmptyHistorySaysSoInSpanishAndPlotsNothing() {
        val chart = VpdHistoryBuilder.build(emptyList())

        assertTrue(chart.emptyEs.contains("no hay ningún registro"))
        assertFalse(chart.isDrawable)
        assertEquals(0, chart.points.size)
        assertEquals(0.0, chart.minKPa, 0.0)
    }

    @Test
    fun theProvenanceSentenceStatesTheRuleRatherThanAssumingItWasNoticed() {
        val chart = VpdHistoryBuilder.build(emptyList())
        assertTrue(chart.provenanceEs.contains("origen"))
        assertTrue(chart.provenanceEs.contains("versión 5"))
    }

    @Test
    fun theBandLadderOnlyShowsEdgesInsideThePlottedRange() {
        val stable = VpdHistoryBuilder.build(
            listOf(
                row(1, 1_000L, 0.7f, null),
                row(2, 2_000L, 0.9f, null)
            )
        )
        // A stable tent should not carry four empty bands above its line.
        assertTrue(
            "expected few or no edges for a 0.7-0.9 range, got ${stable.visibleBandEdgesKPa}",
            stable.visibleBandEdgesKPa.size <= 1
        )

        val wide = VpdHistoryBuilder.build(
            listOf(
                row(1, 1_000L, 0.1f, null),
                row(2, 2_000L, 3.0f, null)
            )
        )
        assertTrue(wide.visibleBandEdgesKPa.size >= 2)
    }

    @Test
    fun aLegendCountIsSpanishPluralised() {
        assertEquals("1 punto", VpdLegendEntry(VpdProvenance.MEASURED, "Medido", 1).countEs)
        assertEquals("0 puntos", VpdLegendEntry(VpdProvenance.MEASURED, "Medido", 0).countEs)
        assertEquals("3 puntos", VpdLegendEntry(VpdProvenance.MEASURED, "Medido", 3).countEs)
    }

    /* ── The journal write ─────────────────────────────────────────────── */

    private val goodLogForm = VpdLogForm(
        date = "15/07/2026",
        time = "18:30",
        vpdKPa = "0,84",
        offsetC = "2,0",
        airTemperatureC = "24,0",
        humidityPercent = "60,0",
        notes = "  lectura de la tarde  ",
        provenance = VpdProvenance.CALCULATED
    )

    @Test
    fun aCalculatedReadingIsStoredWithItsOffsetSoItCanBeReproduced() {
        val outcome = VpdLogFormValidator.validate(goodLogForm, madrid)

        assertTrue(outcome is VpdLogOutcome.Ready)
        val ready = outcome as VpdLogOutcome.Ready
        assertEquals(VpdProvenance.CALCULATED, ready.provenance)
        assertEquals(2.0f, ready.leafOffsetC!!, 1e-6f)
        assertEquals(0.84f, ready.vpdKPa, 1e-6f)
        assertEquals("lectura de la tarde", ready.notes)
    }

    @Test
    fun aMeasuredReadingCarriesNoOffset() {
        // A measured reading came off an instrument. Attaching an offset would imply a
        // derivation that never happened.
        val outcome = VpdLogFormValidator.validate(
            goodLogForm.copy(provenance = VpdProvenance.MEASURED, offsetC = ""),
            madrid
        )

        assertTrue(outcome is VpdLogOutcome.Ready)
        assertEquals(null, (outcome as VpdLogOutcome.Ready).leafOffsetC)
    }

    @Test
    fun aCalculatedReadingWithoutItsOffsetIsRefused() {
        val outcome = VpdLogFormValidator.validate(goodLogForm.copy(offsetC = ""), madrid)

        assertTrue(outcome is VpdLogOutcome.Invalid)
        assertTrue(
            "the message has to explain why, or it reads as a broken field",
            (outcome as VpdLogOutcome.Invalid).problemEs.contains("reproducirlo")
        )
    }

    @Test
    fun anUnknownProvenanceCannotBeLogged() {
        val outcome = VpdLogFormValidator.validate(
            goodLogForm.copy(provenance = VpdProvenance.UNKNOWN),
            madrid
        )

        assertTrue(outcome is VpdLogOutcome.Invalid)
        assertTrue((outcome as VpdLogOutcome.Invalid).problemEs.contains("inventar el origen"))
    }

    @Test
    fun theInstantIsResolvedInTheCallersZone() {
        // A reading typed at 00:30 local belongs to the local day. Resolving in UTC would put
        // it on the previous one, which is exactly the sort of silent off-by-one-day a chart
        // then makes invisible.
        val form = goodLogForm.copy(date = "15/07/2026", time = "00:30")
        val inMadrid = VpdLogFormValidator.validate(form, madrid) as VpdLogOutcome.Ready
        val inUtc = VpdLogFormValidator.validate(form, utc) as VpdLogOutcome.Ready

        // The same typed wall clock, resolved in two zones, is two instants. Madrid in July is
        // UTC+2, so 00:30 in Madrid is 22:30 on the *previous* day in UTC — the epoch value is
        // therefore two hours *earlier*, which is the sign an assertion most easily gets
        // backwards. Both the sign and the resolved dates are asserted.
        assertEquals(
            "00:30 in Madrid is 22:30 UTC the day before",
            -2 * 3_600_000L,
            inMadrid.timestamp - inUtc.timestamp
        )
        assertEquals("15/07/2026", dateOf(inUtc.timestamp, madrid))
        assertEquals("15/07/2026", dateOf(inMadrid.timestamp, madrid))
        assertEquals(
            "and the Madrid reading falls on the previous UTC day",
            "14/07/2026",
            dateOf(inMadrid.timestamp, utc)
        )
        assertEquals("15/07/2026", dateOf(inUtc.timestamp, utc))
    }

    @Test
    fun aDateThatDoesNotExistIsRefused() {
        // 31 February matches the shape; only building the LocalDate knows it is not a day.
        listOf("31/02/2026", "00/01/2026", "15/13/2026", "29/02/2025").forEach { date ->
            val outcome = VpdLogFormValidator.validate(goodLogForm.copy(date = date), madrid)
            assertTrue("$date must be refused", outcome is VpdLogOutcome.Invalid)
        }
        // And a real leap day is accepted, so the rule is not just "refuse February".
        assertTrue(
            VpdLogFormValidator.validate(goodLogForm.copy(date = "29/02/2024"), madrid)
                is VpdLogOutcome.Ready
        )
    }

    @Test
    fun aTimeThatDoesNotExistIsRefused() {
        listOf("24:00", "25:61", "12:75").forEach { time ->
            assertTrue(
                "$time must be refused",
                VpdLogFormValidator.validate(goodLogForm.copy(time = time), madrid)
                    is VpdLogOutcome.Invalid
            )
        }
    }

    @Test
    fun aMistypedVpdIsRefusedRatherThanStoredAsAValidNumber() {
        // "120" would parse as 120 kPa and read as the EXTREME band rather than as a typo.
        val outcome = VpdLogFormValidator.validate(
            goodLogForm.copy(vpdKPa = "120"),
            madrid
        )
        assertTrue(outcome is VpdLogOutcome.Invalid)
        assertTrue((outcome as VpdLogOutcome.Invalid).problemEs.contains("12"))
    }

    @Test
    fun blankNotesBecomeNullRatherThanAnEmptyString() {
        val outcome = VpdLogFormValidator.validate(goodLogForm.copy(notes = "   "), madrid)
        assertEquals(null, (outcome as VpdLogOutcome.Ready).notes)
    }

    @Test
    fun theTentSentenceNamesTheTentOrSaysThereIsNone() {
        assertTrue(VpdLogFormValidator.tentSentenceEs("Carpa 4").contains("Carpa 4"))
        assertTrue(
            "a plant with no tent must say so rather than showing a blank line",
            VpdLogFormValidator.tentSentenceEs(null).contains("ninguna carpa")
        )
        assertTrue(VpdLogFormValidator.tentSentenceEs("  ").contains("ninguna carpa"))
    }

    @Test
    fun theCalculatorStatesItHasNoSensorBehindIt() {
        // The parallel with EstimatedClimate.SOURCE_NOTE_ES: both say the number was derived
        // and neither involves a sensor.
        assertTrue(VpdCalculator.CALCULATOR_SOURCE_ES.contains("No hay ningún sensor"))
        assertTrue(
            "and it must be about this app, not about the weather",
            VpdCalculator.CALCULATOR_SOURCE_ES.contains("app")
        )
    }

    /* ── Helpers ───────────────────────────────────────────────────────── */

    private fun row(
        id: Long,
        timestamp: Long,
        vpd: Float?,
        source: String?
    ) = VpdHistoryRow(
        id = id,
        timestamp = timestamp,
        vpdKPa = vpd,
        vpdSource = source,
        temperatureC = 24f,
        humidityPercent = 60f,
        leafOffsetC = if (source == VpdProvenance.CALCULATED.storageKey) 2f else null
    )

    /** `dd/MM/yyyy` of [epochMillis] as seen from [zone]. */
    private fun dateOf(epochMillis: Long, zone: ZoneId): String {
        val date = java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        return "%02d/%02d/%04d".format(date.dayOfMonth, date.monthValue, date.year)
    }
}

