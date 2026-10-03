package com.trichome.app.model

import com.trichome.app.data.entity.Protocol
import com.trichome.app.data.model.GrowRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a typed protocol target means, and what a cleared one writes.
 *
 * ## The contract under test
 *
 * Fifteen columns existed to be read and could not be written, so this file pins the
 * three decisions the new write path makes about a grower's keystrokes:
 *
 * 1. **A band commits as one value or not at all.** Two boxes, `low <= high`, and a
 *    half-typed band is refused rather than becoming `6,0 – 6,0`.
 * 2. **An empty input writes `null`, never `0f`.** A grower who clears a field must get
 *    `Sin definir` back, not a fabricated zero in the metric register.
 * 3. **A flat band is a real target.** `low == high` commits, because a grower holding
 *    exactly one value has declared something.
 *
 * Plus the property that makes the write path safe to have at all: resolving one group's
 * fields must leave every other column of the row exactly as it was.
 */
class ProtocolTargetEditorTest {

    private val empty = Protocol(id = 5L, plantId = 1L, name = "Exterior")

    private fun filled() = empty.copy(
        vpdBand = GrowRange(0.8f, 1.2f),
        phRange = GrowRange(5.8f, 6.5f),
        ecRange = GrowRange(1.2f, 1.6f),
        lightTempCelsius = 26f,
        lightHumidityPercent = 55f,
        darkTempCelsius = 22f,
        darkHumidityPercent = 60f,
        ppfd = 450f,
        dli = 24.3f,
        lightType = "CMH",
        lampPowerWatts = 600f,
        substrateType = "Coco",
        wateringStrategy = "Run to drain",
        observations = "Podar al entrar en floración"
    )

    private fun formOf(
        protocol: Protocol,
        group: ProtocolTargetGroup
    ): ProtocolTargetForm = ProtocolTargetEditor.formFor(protocol, group.fields)

    private fun formWith(
        protocol: Protocol,
        group: ProtocolTargetGroup,
        edits: Map<ProtocolTargetField, TargetInput>
    ): ProtocolTargetForm = edits.entries.fold(formOf(protocol, group)) { form, (field, input) ->
        form.withInput(field, input)
    }

    private fun resolve(
        form: ProtocolTargetForm,
        protocol: Protocol,
        group: ProtocolTargetGroup
    ) = ProtocolTargetEditor.resolve(form, protocol, group.fields)

    private fun ready(
        form: ProtocolTargetForm,
        protocol: Protocol,
        group: ProtocolTargetGroup
    ): Protocol {
        val outcome = resolve(form, protocol, group)
        assertTrue(
            "`${outcome}` was refused: ${(outcome as? ProtocolTargetOutcome.Invalid)?.problemEs}",
            outcome is ProtocolTargetOutcome.Ready
        )
        return (outcome as ProtocolTargetOutcome.Ready).protocol
    }

    /* ── Typing ────────────────────────────────────────────────────────────── */

    @Test
    fun bothDecimalSeparatorsParse() {
        // `KeyboardType.Decimal` offers a comma on a Spanish device and a dot on an
        // English one. Refusing either would make the dialog's own seed untypeable.
        assertEquals(6.5f, ProtocolTargetEditor.parseDecimalEs("6,5")!!, 0f)
        assertEquals(6.5f, ProtocolTargetEditor.parseDecimalEs("6.5")!!, 0f)
        assertEquals(6.5f, ProtocolTargetEditor.parseDecimalEs("  6.5  ")!!, 0f)
    }

    @Test
    fun somethingThatIsNotANumberParsesToNothing() {
        listOf("", "   ", "abc", "6,5,5", "1.2.3", "--5", "6,5 kPa", "NaN", "Infinity")
            .forEach { raw ->
                assertNull("'$raw' must not parse as a number", ProtocolTargetEditor.parseDecimalEs(raw))
            }
    }

    @Test
    fun aNegativeNumberParsesBecauseTwoTemperaturesAreAllowedToBeNegative() {
        // The parser does not decide; the field does. A room at -2 °C is a real target.
        assertEquals(-2f, ProtocolTargetEditor.parseDecimalEs("-2")!!, 0f)
    }

    /* ── A band: two inputs, one value ─────────────────────────────────────── */

    @Test
    fun anEmptyPairOfBoundsIsUnsetRatherThanAZeroBand() {
        listOf("", "   ").forEach { blank ->
            val outcome = ProtocolTargetEditor.resolveBand(blank, blank, ProtocolTargetField.PH_RANGE)
            assertEquals(
                "an emptied band must resolve to no band at all, not 0:0",
                RangeInput.Unset,
                outcome
            )
        }
    }

    @Test
    fun halfFilledBandIsRefusedAndSaysWhy() {
        listOf("6,0" to "", "" to "6,5", "6,0" to "  ").forEach { (low, high) ->
            val outcome = ProtocolTargetEditor.resolveBand(low, high, ProtocolTargetField.PH_RANGE)

            assertTrue(
                "a band with one bound filled is the state GrowRange exists to refuse",
                outcome is RangeInput.Refused
            )
            assertEquals(
                ProtocolTargetEditor.BAND_INCOMPLETE_ES,
                (outcome as RangeInput.Refused).problemEs
            )
        }
    }

    @Test
    fun invertedBoundsAreRefusedAndNameTheMistake() {
        val outcome = ProtocolTargetEditor.resolveBand("6,5", "6,0", ProtocolTargetField.PH_RANGE)

        assertTrue(outcome is RangeInput.Refused)
        assertEquals(
            ProtocolTargetEditor.BAND_INVERTED_ES,
            (outcome as RangeInput.Refused).problemEs
        )
    }

    @Test
    fun aFlatBandCommitsBecauseAFlatTargetIsLegitimate() {
        val outcome = ProtocolTargetEditor.resolveBand("6,0", "6,0", ProtocolTargetField.PH_RANGE)

        assertTrue("a grower holding exactly one pH has declared something", outcome is RangeInput.Band)
        assertEquals(GrowRange(6.0f, 6.0f), (outcome as RangeInput.Band).range)
        assertTrue(
            "and GrowRange already has a name for it, which is why nothing here refuses it",
            (outcome as RangeInput.Band).range.isSingle
        )
    }

    @Test
    fun aNegatedBoundIsRefusedOnABandThatCannotBeNegative() {
        val outcome = ProtocolTargetEditor.resolveBand("-1", "1", ProtocolTargetField.PH_RANGE)

        assertTrue(outcome is RangeInput.Refused)
        assertTrue(
            "the refusal has to name the field",
            (outcome as RangeInput.Refused).problemEs.contains(ProtocolTargetField.PH_RANGE.labelEs)
        )
    }

    @Test
    fun aStoredBandRoundTripsThroughItsOwnInputs() {
        // Open the editor, save without touching it, get the same band back. A format
        // mismatch between the read side and the input side would quietly move a target.
        ProtocolTargetGroup.entries.forEach { group ->
            group.fields.filter { it.kind == ProtocolTargetKind.BAND }.forEach { field ->
                val stored = ready(
                    formWith(
                        filled(),
                        group,
                        mapOf(field to TargetInput.Band("0,80", "1,20"))
                    ),
                    filled(),
                    group
                )
                val band = stored.valueOf(field) as GrowRange

                val reopened = formOf(stored, group).inputFor(field) as TargetInput.Band
                val outcome = ProtocolTargetEditor.resolveBand(
                    reopened.low,
                    reopened.high,
                    field
                )

                assertTrue("`${field.name}` did not reopen as a band", outcome is RangeInput.Band)
                assertEquals(
                    "`${field.name}` changed value by being read and written back",
                    GrowRange(0.8f, 1.2f),
                    (outcome as RangeInput.Band).range
                )
            }
        }
    }

    /* ── The write: what a cleared field stores ────────────────────────────── */

    @Test
    fun aClearedBandStoresNullAndNotZero() {
        val cleared = ready(
            formWith(
                filled(),
                ProtocolTargetGroup.AMBIENTE,
                mapOf(
                    ProtocolTargetField.PH_RANGE to TargetInput.Band("", ""),
                    ProtocolTargetField.VPD_BAND to TargetInput.Band(" ", " ")
                )
            ),
            filled(),
            ProtocolTargetGroup.AMBIENTE
        )

        assertNull(
            "an emptied pH band must store null, or the card prints 0,00 – 0,00 in the " +
                "metric face as a target nobody chose",
            cleared.phRange
        )
        assertNull(cleared.vpdBand)
        // And the display side says so.
        val row = protocolFieldGroups(cleared).flatMap { it.rows }
            .first { it.labelEs == ProtocolExtendedFields.ETIQUETA_PH }
        assertEquals(ProtocolExtendedFields.SIN_DEFINIR, row.detailEs)
        assertNull(row.metricEs)
    }

    @Test
    fun aClearedMetricStoresNullAndNotZero() {
        val cleared = ready(
            formWith(
                filled(),
                ProtocolTargetGroup.INTENSIDAD,
                mapOf(
                    ProtocolTargetField.DLI to TargetInput.Metric(""),
                    ProtocolTargetField.PPFD to TargetInput.Metric("   ")
                )
            ),
            filled(),
            ProtocolTargetGroup.INTENSIDAD
        )

        assertNull(cleared.dli)
        assertNull(cleared.ppfd)
    }

    @Test
    fun aTypedValueIsStored() {
        val written = ready(
            formWith(
                empty,
                ProtocolTargetGroup.AMBIENTE,
                mapOf(
                    ProtocolTargetField.VPD_BAND to TargetInput.Band("0,80", "1,20"),
                    ProtocolTargetField.PH_RANGE to TargetInput.Band("5,8", "6,5"),
                    ProtocolTargetField.EC_RANGE to TargetInput.Band("1,2", "1,6"),
                    ProtocolTargetField.LIGHT_TEMP_CELSIUS to TargetInput.Metric("26,0"),
                    ProtocolTargetField.LIGHT_HUMIDITY_PERCENT to TargetInput.Metric("55"),
                    ProtocolTargetField.DARK_TEMP_CELSIUS to TargetInput.Metric("22"),
                    ProtocolTargetField.DARK_HUMIDITY_PERCENT to TargetInput.Metric("60")
                )
            ),
            empty,
            ProtocolTargetGroup.AMBIENTE
        )

        assertEquals(GrowRange(0.8f, 1.2f), written.vpdBand)
        assertEquals(GrowRange(5.8f, 6.5f), written.phRange)
        assertEquals(GrowRange(1.2f, 1.6f), written.ecRange)
        assertEquals(26f, written.lightTempCelsius!!, 0f)
        assertEquals(55f, written.lightHumidityPercent!!, 0f)
        assertEquals(22f, written.darkTempCelsius!!, 0f)
        assertEquals(60f, written.darkHumidityPercent!!, 0f)
    }

    @Test
    fun aZeroTheGrowerTypedIsStoredAsZero() {
        // The other direction: `null` means unset and `0f` means zero, so a field the
        // grower cleared to empty and a field they set to zero must not converge.
        val written = ready(
            formWith(
                empty,
                ProtocolTargetGroup.INTENSIDAD,
                mapOf(ProtocolTargetField.DLI to TargetInput.Metric("0"))
            ),
            empty,
            ProtocolTargetGroup.INTENSIDAD
        )

        assertEquals(0f, written.dli!!, 0f)
    }

    @Test
    fun whitespaceOnlyTextIsStoredAsUnset() {
        val written = ready(
            formWith(
                filled(),
                ProtocolTargetGroup.INSTALACION,
                mapOf(
                    ProtocolTargetField.SUBSTRATE_TYPE to TargetInput.Free("   "),
                    ProtocolTargetField.LIGHT_TYPE to TargetInput.Free("  HPS  ")
                )
            ),
            filled(),
            ProtocolTargetGroup.INSTALACION
        )

        assertNull(written.substrateType)
        assertEquals("HPS", written.lightType)
    }

    /* ── Refusals ──────────────────────────────────────────────────────────── */

    @Test
    fun aNegativeHumidityIsRefusedAndANegativeTemperatureIsNot() {
        val group = ProtocolTargetGroup.AMBIENTE

        val humidity = resolve(
            formWith(
                empty,
                group,
                mapOf(ProtocolTargetField.LIGHT_HUMIDITY_PERCENT to TargetInput.Metric("-5"))
            ),
            empty,
            group
        )
        assertTrue(humidity is ProtocolTargetOutcome.Invalid)
        assertTrue(
            (humidity as ProtocolTargetOutcome.Invalid).problemEs
                .contains(ProtocolTargetField.LIGHT_HUMIDITY_PERCENT.labelEs)
        )

        val temperature = resolve(
            formWith(
                empty,
                group,
                mapOf(ProtocolTargetField.LIGHT_TEMP_CELSIUS to TargetInput.Metric("-2,5"))
            ),
            empty,
            group
        )
        assertTrue(
            "a cold room is a real target and must be storable",
            temperature is ProtocolTargetOutcome.Ready
        )
        assertEquals(
            -2.5f,
            (temperature as ProtocolTargetOutcome.Ready).protocol.lightTempCelsius!!,
            0f
        )
    }

    @Test
    fun aHumidityOverOneHundredIsRefused() {
        val outcome = resolve(
            formWith(
                empty,
                ProtocolTargetGroup.AMBIENTE,
                mapOf(ProtocolTargetField.DARK_HUMIDITY_PERCENT to TargetInput.Metric("140"))
            ),
            empty,
            ProtocolTargetGroup.AMBIENTE
        )

        assertTrue(
            "140 % is a typo and 140 is what it would render as",
            outcome is ProtocolTargetOutcome.Invalid
        )
    }

    @Test
    fun aBandIsCheckedAgainstItsCeilingWhenItHasOne() {
        // No band field has a ceiling today — pH, EC and VPD have no upper bound a
        // percentage does — so this pins the guard rather than a behaviour: the check
        // exists for the day a band field does, and it fires from the same resolver.
        assertNull(
            ProtocolTargetEditor.bandAboveCeilingEs(
                ProtocolTargetField.PH_RANGE,
                GrowRange(0f, 99f)
            )
        )
        assertEquals(
            100f,
            ProtocolTargetField.LIGHT_HUMIDITY_PERCENT.upperBound!!,
            0f
        )
        ProtocolTargetGroup.entries.forEach { group ->
            group.fields.filter { it.kind == ProtocolTargetKind.BAND }.forEach { field ->
                assertNull("`${field.name}` is a band with no physical ceiling", field.upperBound)
            }
        }
    }

    @Test
    fun theStageBandResolvesAndCanBeCleared() {
        val set = ProtocolTargetEditor.resolveStageTarget(TargetInput.Band("1,00", "1,40"))
        assertTrue(set is StageTargetOutcome.Ready)
        assertEquals(GrowRange(1.0f, 1.4f), (set as StageTargetOutcome.Ready).band)

        val cleared = ProtocolTargetEditor.resolveStageTarget(TargetInput.Band("", ""))
        assertTrue(cleared is StageTargetOutcome.Ready)
        assertNull(
            "clearing a stage band is a real answer: three stages hold null after migration",
            (cleared as StageTargetOutcome.Ready).band
        )

        val half = ProtocolTargetEditor.resolveStageTarget(TargetInput.Band("1,00", ""))
        assertTrue(half is StageTargetOutcome.Invalid)
        assertEquals(
            ProtocolTargetEditor.BAND_INCOMPLETE_ES,
            (half as StageTargetOutcome.Invalid).problemEs
        )
    }

    /* ── The part that makes the write path safe ───────────────────────────── */

    @Test
    fun resolvingOneGroupLeavesEveryOtherColumnOfTheRowUntouched() {
        // The reason this function builds its answer with `copy` from the row it was
        // handed. A targets edit that rebuilt a Protocol would zero the columns it never
        // mentioned, and `insertProtocol` is a REPLACE on the whole row.
        val group = ProtocolTargetGroup.AMBIENTE
        val written = ready(
            formWith(
                filled(),
                group,
                mapOf(ProtocolTargetField.PH_RANGE to TargetInput.Band("6,0", "6,5"))
            ),
            filled(),
            group
        )

        assertEquals(GrowRange(6.0f, 6.5f), written.phRange)
        // Explicitly, by column, so a failure names the field rather than "diff":
        assertEquals(GrowRange(0.8f, 1.2f), written.vpdBand)
        assertEquals(GrowRange(1.2f, 1.6f), written.ecRange)
        assertEquals(filled().lightTempCelsius, written.lightTempCelsius)
        assertEquals(filled().lightHumidityPercent, written.lightHumidityPercent)
        assertEquals(filled().darkTempCelsius, written.darkTempCelsius)
        assertEquals(filled().darkHumidityPercent, written.darkHumidityPercent)
        assertEquals(filled().ppfd, written.ppfd)
        assertEquals(filled().dli, written.dli)
        assertEquals(filled().lightType, written.lightType)
        assertEquals(filled().lampPowerWatts, written.lampPowerWatts)
        assertEquals(filled().substrateType, written.substrateType)
        assertEquals(filled().wateringStrategy, written.wateringStrategy)
        assertEquals(filled().observations, written.observations)
    }

    @Test
    fun aTargetsEditCannotDisturbTheNameOrThePhotoperiodOrTheHeaderItself() {
        val written = ready(
            formWith(
                filled(),
                ProtocolTargetGroup.INTENSIDAD,
                mapOf(ProtocolTargetField.PPFD to TargetInput.Metric("500"))
            ),
            filled(),
            ProtocolTargetGroup.INTENSIDAD
        )

        assertEquals(filled().id, written.id)
        assertEquals(filled().plantId, written.plantId)
        assertEquals(filled().name, written.name)
        assertEquals(filled().lightHours, written.lightHours)
        assertEquals(filled().darkHours, written.darkHours)
        assertEquals(filled().presetType, written.presetType)
        assertEquals(filled().cycleStartAt, written.cycleStartAt)
        assertEquals(filled().isActive, written.isActive)
        assertEquals(500f, written.ppfd!!, 0f)
    }

    @Test
    fun openingTheEditorAndSavingWithoutTouchingItChangesNothing() {
        ProtocolTargetGroup.entries.forEach { group ->
            assertEquals(
                "`${group.name}` was altered by being opened and saved",
                filled(),
                ready(formOf(filled(), group), filled(), group)
            )
        }
    }

    @Test
    fun anUnsetProtocolOpensWithEmptyBoxesRatherThanZeroes() {
        val form = formOf(empty, ProtocolTargetGroup.AMBIENTE)

        assertEquals(
            TargetInput.Band("", ""),
            form.inputFor(ProtocolTargetField.VPD_BAND)
        )
        assertEquals(
            TargetInput.Metric(""),
            form.inputFor(ProtocolTargetField.LIGHT_TEMP_CELSIUS)
        )
        assertEquals(
            TargetInput.Free(""),
            form.inputFor(ProtocolTargetField.SUBSTRATE_TYPE)
        )
    }

    @Test
    fun everyFieldOfAFilledProtocolSeedsWithItsStoredValue() {
        val form = formOf(filled(), ProtocolTargetGroup.AMBIENTE)

        assertEquals(
            TargetInput.Band("0,80", "1,20"),
            form.inputFor(ProtocolTargetField.VPD_BAND)
        )
        assertEquals(
            TargetInput.Band("5,8", "6,5"),
            form.inputFor(ProtocolTargetField.PH_RANGE)
        )
        assertEquals(
            TargetInput.Metric("26,0"),
            form.inputFor(ProtocolTargetField.LIGHT_TEMP_CELSIUS)
        )
        assertEquals(
            TargetInput.Metric("55"),
            form.inputFor(ProtocolTargetField.LIGHT_HUMIDITY_PERCENT)
        )
    }

    /* ── The field table ───────────────────────────────────────────────────── */

    @Test
    fun theTableCoversFourteenFieldsAndNothingIsInTwoGroups() {
        assertEquals(
            "the four v4 target columns, the v6 stage band aside",
            14,
            ProtocolTargetGroup.ALL_FIELDS.size
        )
        assertEquals(
            "a field in two groups would be written by two surfaces",
            ProtocolTargetGroup.ALL_FIELDS.size,
            ProtocolTargetGroup.ALL_FIELDS.toSet().size
        )
        assertEquals(14, ProtocolTargetField.entries.size)
    }

    @Test
    fun everyGroupIsReachableFromItsOwnCardTitle() {
        assertEquals(
            ProtocolTargetGroup.AMBIENTE,
            ProtocolTargetGroup.forTitle(ProtocolExtendedFields.GRUPO_AMBIENTE)
        )
        assertEquals(
            ProtocolTargetGroup.INTENSIDAD,
            ProtocolTargetGroup.forTitle(ProtocolExtendedFields.GRUPO_INTENSIDAD)
        )
        assertEquals(
            ProtocolTargetGroup.INSTALACION,
            ProtocolTargetGroup.forTitle(ProtocolExtendedFields.GRUPO_INSTALACION)
        )
        assertNull(
            "the per-stage block is written on its own surface, so it must resolve to no " +
                "grow-wide group",
            ProtocolTargetGroup.forTitle(ProtocolExtendedFields.GRUPO_ETAPAS)
        )
    }

    @Test
    fun everyFieldHasAUnitExactlyWhenItsLabelDoesNotAlreadyCarryIt() {
        ProtocolTargetField.entries.forEach { field ->
            if (field.unitEs == null) {
                assertFalse(field.showsUnit)
            } else if (field.labelEs.contains(field.unitEs!!)) {
                assertFalse(
                    "`${field.name}` would print its unit twice",
                    field.showsUnit
                )
            } else {
                assertTrue("`${field.name}` loses its unit", field.showsUnit)
            }
        }
        assertFalse(ProtocolTargetField.PH_RANGE.showsUnit)
        assertTrue(ProtocolTargetField.VPD_BAND.showsUnit)
    }

    @Test
    fun aFieldHeadingCarriesTheUnitAndOnlyOnce() {
        assertEquals(
            "${ProtocolExtendedFields.ETIQUETA_VPD} · ${ProtocolExtendedFields.UNIDAD_KPA}",
            ProtocolTargetEditor.fieldHeadingEs(ProtocolTargetField.VPD_BAND)
        )
        assertEquals(
            ProtocolExtendedFields.ETIQUETA_SUSTRATO,
            ProtocolTargetEditor.fieldHeadingEs(ProtocolTargetField.SUBSTRATE_TYPE)
        )
        assertEquals(
            "pH objetivo · pH is noise",
            ProtocolExtendedFields.ETIQUETA_PH,
            ProtocolTargetEditor.fieldHeadingEs(ProtocolTargetField.PH_RANGE)
        )
    }

    /* ── The copy ──────────────────────────────────────────────────────────── */

    @Test
    fun everyRefusalIsASentenceAndNamesTheFieldOrTheMistake() {
        val refusals = listOf(
            ProtocolTargetEditor.notANumberEs(ProtocolTargetField.PPFD),
            ProtocolTargetEditor.notANegativeEs(ProtocolTargetField.DLI),
            ProtocolTargetEditor.aboveTheCeilingEs(
                ProtocolTargetField.LIGHT_HUMIDITY_PERCENT,
                100f
            ),
            ProtocolTargetEditor.BAND_INCOMPLETE_ES,
            ProtocolTargetEditor.BAND_INVERTED_ES
        )

        refusals.forEach { sentence ->
            assertTrue("a refusal reads as '$sentence'", sentence.isNotBlank())
            assertTrue(
                "a refusal has to end as a sentence so it reads as one: '$sentence'",
                sentence.trim().endsWith(".")
            )
        }
        assertTrue(refusals[0].contains(ProtocolTargetField.PPFD.labelEs))
        assertTrue(refusals[1].contains(ProtocolTargetField.DLI.labelEs))
        assertTrue(refusals[2].contains(ProtocolExtendedFields.UNIDAD_PERCENT))
    }

    @Test
    fun theWriteSurfaceSaysTheseAreGoalsAndNotReadings() {
        val sentence = ProtocolTargetEditor.GOAL_NOT_MEASUREMENT_ES

        assertTrue(
            "the sentence has to name what the numbers are, or a band reads as a measurement",
            sentence.contains("objetivos")
        )
        assertTrue(sentence.contains("No son mediciones"))
        assertTrue(
            "and it has to deny the sensor rather than merely avoid mentioning one",
            sentence.contains("no lee ningún sensor")
        )
    }

    @Test
    fun theStageSurfaceKeepsTheStageNameInItsTitle() {
        assertEquals(
            ProtocolTargetEditor.TITLE_ES + " · " + ProtocolExtendedFields.GRUPO_AMBIENTE,
            ProtocolTargetEditor.titleEs(ProtocolExtendedFields.GRUPO_AMBIENTE)
        )
        assertTrue(
            ProtocolTargetEditor.stageTitleEs("  Floración ").contains("Floración")
        )
        assertNotNull(ProtocolTargetEditor.EDIT_HINT_ES)
        assertNotNull(ProtocolTargetEditor.UNSET_HINT_ES)
    }

    @Test
    fun anEmptyInputNeverBecomesAZeroBandInTheStoredForm() {
        // The literal form of the "empty writes null" rule, checked on what reaches
        // `GrowRange.encode` rather than on the resolver's intent.
        val cleared = ready(
            formWith(
                filled(),
                ProtocolTargetGroup.AMBIENTE,
                mapOf(ProtocolTargetField.EC_RANGE to TargetInput.Band("", ""))
            ),
            filled(),
            ProtocolTargetGroup.AMBIENTE
        )

        assertNull(cleared.ecRange)
        assertNull(
            "and the stored form is NULL rather than a text zero",
            GrowRange.encode(cleared.ecRange)
        )
    }
}