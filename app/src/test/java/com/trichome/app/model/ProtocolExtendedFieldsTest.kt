package com.trichome.app.model

import com.trichome.app.data.entity.Protocol
import com.trichome.app.data.model.GrowRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What an unfilled protocol field looks like on screen.
 *
 * The contract under test: a field the grower has not filled in reads
 * "Sin definir" and carries no number. Not `0`, not `6.0 – 6.0`, not a muted
 * placeholder that looks like a measurement. A protocol with no declared pH band
 * must not be able to show one, because a number on that screen is read as a
 * target the plant is being held at.
 *
 * Pure Kotlin, so it runs without the Compose runtime this project does not have
 * on the JVM classpath.
 */
class ProtocolExtendedFieldsTest {

    private val empty = Protocol(plantId = 1L, name = "Exterior")

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

    private fun allRows(protocol: Protocol): List<ProtocolFieldRow> =
        protocolFieldGroups(protocol).flatMap { it.rows }

    private fun row(protocol: Protocol, label: String): ProtocolFieldRow =
        allRows(protocol).first { it.labelEs == label }

    /* ── The empty state ───────────────────────────────────────────────────── */

    @Test
    fun anUnfilledFieldReadsSinDefinirAndCarriesNoNumber() {
        val rows = allRows(empty)
        assertEquals(14, rows.size)
        rows.forEach { field ->
            assertNull(
                "`${field.labelEs}` must carry no number when it is unset",
                field.metricEs
            )
            assertEquals(
                "`${field.labelEs}` must read Sin definir",
                ProtocolExtendedFields.SIN_DEFINIR,
                field.detailEs
            )
        }
    }

    @Test
    fun noUnfilledFieldRendersAPlaceholderNumber() {
        // The literal form of the contract. If a formatter ever gains a default
        // branch, one of these rows starts with a digit and this fails.
        allRows(empty).forEach { field ->
            val rendered = listOfNotNull(field.metricEs, field.detailEs).joinToString(" ")
            assertTrue(
                "`${field.labelEs}` rendered '$rendered'; an unset field must not show a number",
                rendered.none { it.isDigit() }
            )
        }
    }

    @Test
    fun everyFieldIsPresentEvenWhenUnset() {
        // Rows are listed, not hidden, so the protocol states which targets it
        // has no opinion about instead of showing a sheet that is mostly guesses.
        assertEquals(
            listOf(
                ProtocolExtendedFields.ETIQUETA_VPD,
                ProtocolExtendedFields.ETIQUETA_PH,
                ProtocolExtendedFields.ETIQUETA_EC,
                ProtocolExtendedFields.ETIQUETA_TEMP_LUZ,
                ProtocolExtendedFields.ETIQUETA_HR_LUZ,
                ProtocolExtendedFields.ETIQUETA_TEMP_OSCURIDAD,
                ProtocolExtendedFields.ETIQUETA_HR_OSCURIDAD,
                ProtocolExtendedFields.ETIQUETA_PPFD,
                ProtocolExtendedFields.ETIQUETA_DLI,
                ProtocolExtendedFields.ETIQUETA_TIPO_LUZ,
                ProtocolExtendedFields.ETIQUETA_POTENCIA,
                ProtocolExtendedFields.ETIQUETA_SUSTRATO,
                ProtocolExtendedFields.ETIQUETA_RIEGO,
                ProtocolExtendedFields.ETIQUETA_OBSERVACIONES
            ),
            allRows(empty).map { it.labelEs }
        )
    }

    /* ── Filled fields ─────────────────────────────────────────────────────── */

    @Test
    fun aBandRendersBothBoundsWithASpanishDecimalComma() {
        val field = row(filled(), ProtocolExtendedFields.ETIQUETA_PH)
        assertEquals("5,8 – 6,5", field.metricEs)
        assertEquals("pH", field.detailEs)
    }

    @Test
    fun vpdKeepsTwoDecimalsBecauseThatIsHowItIsWritten() {
        assertEquals(
            "0,80 – 1,20",
            row(filled(), ProtocolExtendedFields.ETIQUETA_VPD).metricEs
        )
    }

    @Test
    fun aMeasuredValueRendersAsANumberWithItsUnit() {
        assertEquals("24,3", row(filled(), ProtocolExtendedFields.ETIQUETA_DLI).metricEs)
        assertEquals(
            "mol/m²/d",
            row(filled(), ProtocolExtendedFields.ETIQUETA_DLI).detailEs
        )
        assertEquals("450", row(filled(), ProtocolExtendedFields.ETIQUETA_PPFD).metricEs)
        assertEquals("26,0", row(filled(), ProtocolExtendedFields.ETIQUETA_TEMP_LUZ).metricEs)
        assertEquals(
            "°C",
            row(filled(), ProtocolExtendedFields.ETIQUETA_TEMP_LUZ).detailEs
        )
    }

    @Test
    fun instrumentationCarriesTheNumberAndTheGrowersWordsCarryTheProse() {
        // The metric register must never render "Coco" or "CMH": those are not
        // numbers, and the slot that takes a number is the one the number goes in.
        val fixture = row(filled(), ProtocolExtendedFields.ETIQUETA_TIPO_LUZ)
        assertNull(fixture.metricEs)
        assertEquals("CMH", fixture.detailEs)

        val substrate = row(filled(), ProtocolExtendedFields.ETIQUETA_SUSTRATO)
        assertNull(substrate.metricEs)
        assertEquals("Coco", substrate.detailEs)

        val note = row(filled(), ProtocolExtendedFields.ETIQUETA_OBSERVACIONES)
        assertNull(note.metricEs)
        assertEquals("Podar al entrar en floración", note.detailEs)
    }

    @Test
    fun everyFilledFieldShowsAValueAndNoneFallsBackToSinDefinir() {
        allRows(filled()).forEach { field ->
            assertTrue("`${field.labelEs}` has no value", field.detailEs.isNotBlank())
            assertTrue(
                "`${field.labelEs}` fell back to Sin definir on a filled protocol",
                field.detailEs != ProtocolExtendedFields.SIN_DEFINIR
            )
        }
    }

    @Test
    fun aWhitespaceOnlyTextValueReadsAsUnset() {
        // An empty field and a whitespace-only one are the same answer to
        // "did the grower fill this in?".
        val field = row(
            empty.copy(substrateType = "   "),
            ProtocolExtendedFields.ETIQUETA_SUSTRATO
        )
        assertNull(field.metricEs)
        assertEquals(ProtocolExtendedFields.SIN_DEFINIR, field.detailEs)
    }

    @Test
    fun aZeroIsAValueAndIsRenderedAsOne() {
        // Zero is what "defined and measured as zero" looks like, and it must not
        // be mistaken for the unset state it is not.
        val field = row(
            empty.copy(dli = 0f, lampPowerWatts = 0f),
            ProtocolExtendedFields.ETIQUETA_DLI
        )
        assertEquals("0,0", field.metricEs)
    }

    /* ── Grouping ──────────────────────────────────────────────────────────── */

    @Test
    fun theFieldsAreGroupedInThreeTitledBlocks() {
        val groups = protocolFieldGroups(filled())
        assertEquals(3, groups.size)
        assertEquals(
            listOf(
                ProtocolExtendedFields.GRUPO_AMBIENTE,
                ProtocolExtendedFields.GRUPO_INTENSIDAD,
                ProtocolExtendedFields.GRUPO_INSTALACION
            ),
            groups.map { it.titleEs }
        )
        assertEquals(listOf(7, 2, 5), groups.map { it.rows.size })
    }

    @Test
    fun theGroupSetDoesNotChangeAsFieldsAreFilled() {
        // The row set is fixed, so the card does not reflow every time a value
        // is typed.
        assertEquals(
            allRows(empty).map { it.labelEs },
            allRows(filled()).map { it.labelEs }
        )
    }

    /* ── Number formatting ─────────────────────────────────────────────────── */

    @Test
    fun decimalsUseACommaWhateverTheDeviceLocaleIs() {
        assertEquals("6,0", ProtocolExtendedFields.formatDecimal(6.0f, 1))
        assertEquals("0,80", ProtocolExtendedFields.formatDecimal(0.8f, 2))
        assertEquals("450", ProtocolExtendedFields.formatDecimal(450f, 0))
        assertEquals("-3,5", ProtocolExtendedFields.formatDecimal(-3.5f, 1))
    }

    @Test
    fun anUnsetBandFormatsToNothingRatherThanToZero() {
        assertNull(ProtocolExtendedFields.formatRange(null, 1))
        assertNull(ProtocolExtendedFields.formatRange(null, 0))
    }
}