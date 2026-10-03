package com.trichome.app.model

import com.trichome.app.data.entity.Protocol
import com.trichome.app.data.model.GrowRange
import java.util.Locale

/**
 * How a protocol's declared agronomic fields are labelled, grouped and formatted.
 *
 * This file exists because the copy is Spanish and the numbers are numbers, and
 * a Compose composable is the wrong place to reconcile the two. It is plain
 * Kotlin with no composable in it, so `ProtocolExtendedFieldsTest` can pin every
 * label, every unit and every empty state without a Compose runtime — and this
 * project has no Compose test runtime on the JVM classpath at all, so a
 * composable would have been untestable by construction.
 *
 * ## Two rules the shapes here enforce
 *
 * **An unset field has no number.** [ProtocolFieldRow.metricEs] is `null` when
 * there is nothing measured to show, and [SIN_DEFINIR] carries the sentence
 * instead. There is no code path that formats a missing value as `0`, because
 * the number and the sentence are different fields: a slot that can hold a
 * number cannot be filled with prose, and a slot that holds prose is never
 * reached by a formatter.
 *
 * **Only measured numbers get the metric register.** pH, EC, VPD, PPFD, DLI,
 * temperatures, humidity and wattage are instrumentation and go in [metricEs],
 * which the screen sets in `LocalMetricValue`. A fixture name, a substrate or
 * a grower's free-text note is not a number; those arrive in [detailEs], so the
 * monospace metric face never renders the word "Coco".
 */
object ProtocolExtendedFields {

    /** What a field reads when the grower has not filled it in. */
    const val SIN_DEFINIR: String = "Sin definir"

    /* ── Section headings ──────────────────────────────────────────────────── */

    const val GRUPO_AMBIENTE: String = "Ambiente"
    const val GRUPO_INTENSIDAD: String = "Intensidad de luz"
    const val GRUPO_INSTALACION: String = "Instalación y manejo"

    /* ── Field labels ──────────────────────────────────────────────────────── */

    const val ETIQUETA_VPD: String = "VPD objetivo"
    const val ETIQUETA_PH: String = "pH objetivo"
    const val ETIQUETA_EC: String = "CE objetivo"
    const val ETIQUETA_TEMP_LUZ: String = "Temperatura en luz"
    const val ETIQUETA_HR_LUZ: String = "Humedad en luz"
    const val ETIQUETA_TEMP_OSCURIDAD: String = "Temperatura en oscuridad"
    const val ETIQUETA_HR_OSCURIDAD: String = "Humedad en oscuridad"
    const val ETIQUETA_PPFD: String = "PPFD"
    const val ETIQUETA_DLI: String = "DLI"
    const val ETIQUETA_TIPO_LUZ: String = "Tipo de iluminación"
    const val ETIQUETA_POTENCIA: String = "Potencia instalada"
    const val ETIQUETA_SUSTRATO: String = "Sustrato"
    const val ETIQUETA_RIEGO: String = "Estrategia de riego"
    const val ETIQUETA_OBSERVACIONES: String = "Observaciones agronómicas"

    /* ── Units ─────────────────────────────────────────────────────────────── */

    const val UNIDAD_KPA: String = "kPa"
    const val UNIDAD_PH: String = "pH"
    const val UNIDAD_EC: String = "mS/cm"
    const val UNIDAD_CELSIUS: String = "°C"
    const val UNIDAD_PERCENT: String = "%"
    const val UNIDAD_PPFD: String = "µmol/m²/s"
    const val UNIDAD_DLI: String = "mol/m²/d"
    const val UNIDAD_WATTS: String = "W"

    /**
     * Separator between a band's two bounds, an en dash with a space either side.
     *
     * Not a plain hyphen: at metric sizes the difference between `-` and `–` is
     * legible, and a band set in the metric face should look like a band.
     */
    const val RANGE_SEPARATOR: String = " – "

    /**
     * Decimals per quantity, chosen so a band is read the way a grower writes
     * it: VPD to two (`0,80 – 1,20`), pH and EC to one (`6,0 – 6,5`, `1,2 – 1,6`),
     * temperatures to one, humidity, PPFD and watts to none.
     *
     * Fixed rather than trailing-zero-trimmed, so the same quantity always
     * renders the same width. A column of targets that changes glyph count per
     * row reads as noise.
     */
    const val DECIMALS_VPD: Int = 2
    const val DECIMALS_PH: Int = 1
    const val DECIMALS_EC: Int = 1
    const val DECIMALS_TEMPERATURE: Int = 1
    const val DECIMALS_DLI: Int = 1
    const val DECIMALS_WHOLE: Int = 0

    /**
     * A number in Spanish notation: comma decimal separator, fixed decimals.
     *
     * Formats under [Locale.US] and then swaps the separator, which is the
     * pattern `ClimateCardCopy` already uses. It is deliberate — formatting under
     * the device locale would print `6.0` to a Spanish-language screen in an
     * English region, and every string in this app is Spanish by contract.
     */
    fun formatDecimal(value: Float, decimals: Int): String =
        String.format(Locale.US, "%.${decimals}f", value).replace('.', ',')

    /**
     * A band's two bounds as one string, e.g. `6,0 – 6,5`.
     *
     * `null` for a band that is not set, which the callers turn into
     * [SIN_DEFINIR] rather than into a number.
     */
    fun formatRange(range: GrowRange?, decimals: Int): String? {
        if (range == null) return null
        val low = formatDecimal(range.low, decimals)
        val high = formatDecimal(range.high, decimals)
        return low + RANGE_SEPARATOR + high
    }
}

/**
 * One labelled protocol field, already resolved for display.
 *
 * [metricEs] and [detailEs] are separate because they are set in different
 * registers: the first is instrumentation and takes the metric role, the second
 * is a unit or a word. Keeping them apart in the type is what stops a
 * composable from choosing — and what makes "unset" structurally unable to
 * carry a number.
 */
data class ProtocolFieldRow(
    val labelEs: String,
    /** The number or band, metric typography. `null` whenever there is none. */
    val metricEs: String?,
    /** The unit for a metric, the grower's own text, or [SIN_DEFINIR]. */
    val detailEs: String
)

/** A titled run of [ProtocolFieldRow]s, so the card reads as a sheet. */
data class ProtocolFieldGroup(
    val titleEs: String,
    val rows: List<ProtocolFieldRow>
)

/**
 * The extended fields of [protocol], grouped, with every unset field present.
 *
 * Unset fields are listed rather than hidden. A protocol with nothing but the
 * photoperiod shows fourteen rows reading "Sin definir", and that is the point:
 * it is the protocol telling the grower which targets it has no opinion about,
 * instead of showing a filled-in sheet that is mostly the app's guesses. It also
 * means the row set does not change shape as fields are added, so the card does
 * not reflow every time a value is typed.
 */
fun protocolFieldGroups(protocol: Protocol): List<ProtocolFieldGroup> = listOf(
    ProtocolFieldGroup(
        ProtocolExtendedFields.GRUPO_AMBIENTE,
        listOf(
            bandRow(
                ProtocolExtendedFields.ETIQUETA_VPD,
                protocol.vpdBand,
                ProtocolExtendedFields.UNIDAD_KPA,
                ProtocolExtendedFields.DECIMALS_VPD
            ),
            bandRow(
                ProtocolExtendedFields.ETIQUETA_PH,
                protocol.phRange,
                ProtocolExtendedFields.UNIDAD_PH,
                ProtocolExtendedFields.DECIMALS_PH
            ),
            bandRow(
                ProtocolExtendedFields.ETIQUETA_EC,
                protocol.ecRange,
                ProtocolExtendedFields.UNIDAD_EC,
                ProtocolExtendedFields.DECIMALS_EC
            ),
            metricRow(
                ProtocolExtendedFields.ETIQUETA_TEMP_LUZ,
                protocol.lightTempCelsius,
                ProtocolExtendedFields.UNIDAD_CELSIUS,
                ProtocolExtendedFields.DECIMALS_TEMPERATURE
            ),
            metricRow(
                ProtocolExtendedFields.ETIQUETA_HR_LUZ,
                protocol.lightHumidityPercent,
                ProtocolExtendedFields.UNIDAD_PERCENT,
                ProtocolExtendedFields.DECIMALS_WHOLE
            ),
            metricRow(
                ProtocolExtendedFields.ETIQUETA_TEMP_OSCURIDAD,
                protocol.darkTempCelsius,
                ProtocolExtendedFields.UNIDAD_CELSIUS,
                ProtocolExtendedFields.DECIMALS_TEMPERATURE
            ),
            metricRow(
                ProtocolExtendedFields.ETIQUETA_HR_OSCURIDAD,
                protocol.darkHumidityPercent,
                ProtocolExtendedFields.UNIDAD_PERCENT,
                ProtocolExtendedFields.DECIMALS_WHOLE
            )
        )
    ),
    ProtocolFieldGroup(
        ProtocolExtendedFields.GRUPO_INTENSIDAD,
        listOf(
            metricRow(
                ProtocolExtendedFields.ETIQUETA_PPFD,
                protocol.ppfd,
                ProtocolExtendedFields.UNIDAD_PPFD,
                ProtocolExtendedFields.DECIMALS_WHOLE
            ),
            metricRow(
                ProtocolExtendedFields.ETIQUETA_DLI,
                protocol.dli,
                ProtocolExtendedFields.UNIDAD_DLI,
                ProtocolExtendedFields.DECIMALS_DLI
            )
        )
    ),
    ProtocolFieldGroup(
        ProtocolExtendedFields.GRUPO_INSTALACION,
        listOf(
            textRow(ProtocolExtendedFields.ETIQUETA_TIPO_LUZ, protocol.lightType),
            metricRow(
                ProtocolExtendedFields.ETIQUETA_POTENCIA,
                protocol.lampPowerWatts,
                ProtocolExtendedFields.UNIDAD_WATTS,
                ProtocolExtendedFields.DECIMALS_WHOLE
            ),
            textRow(ProtocolExtendedFields.ETIQUETA_SUSTRATO, protocol.substrateType),
            textRow(
                ProtocolExtendedFields.ETIQUETA_RIEGO,
                protocol.wateringStrategy
            ),
            textRow(ProtocolExtendedFields.ETIQUETA_OBSERVACIONES, protocol.observations)
        )
    )
)

/**
 * A declared band, e.g. `pH objetivo · 6,0 – 6,5`.
 *
 * Both bounds or neither: the underlying [GrowRange] cannot be half-filled, so
 * there is no state in which one bound renders and the other is invented.
 */
private fun bandRow(
    labelEs: String,
    range: GrowRange?,
    unitEs: String,
    decimals: Int
): ProtocolFieldRow {
    val formatted = ProtocolExtendedFields.formatRange(range, decimals)
    return if (formatted == null) {
        ProtocolFieldRow(labelEs, null, ProtocolExtendedFields.SIN_DEFINIR)
    } else {
        ProtocolFieldRow(labelEs, formatted, unitEs)
    }
}

/** A single measured value with its unit, e.g. `DLI · 24,3 mol/m²/d`. */
private fun metricRow(
    labelEs: String,
    value: Float?,
    unitEs: String,
    decimals: Int
): ProtocolFieldRow {
    if (value == null) {
        return ProtocolFieldRow(labelEs, null, ProtocolExtendedFields.SIN_DEFINIR)
    }
    return ProtocolFieldRow(
        labelEs,
        ProtocolExtendedFields.formatDecimal(value, decimals),
        unitEs
    )
}

/**
 * The grower's own words. Never the metric register: "LED" is not a number and
 * setting it in the metric face would claim it was one.
 *
 * Blank is treated as unset, because an empty text field and a whitespace-only
 * one are the same answer to "did the grower fill this in?".
 */
private fun textRow(labelEs: String, value: String?): ProtocolFieldRow {
    val trimmed = value?.trim()
    return if (trimmed.isNullOrEmpty()) {
        ProtocolFieldRow(labelEs, null, ProtocolExtendedFields.SIN_DEFINIR)
    } else {
        ProtocolFieldRow(labelEs, null, trimmed)
    }
}