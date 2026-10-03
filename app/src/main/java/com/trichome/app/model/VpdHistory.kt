package com.trichome.app.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * Where the number in a `grow_events.vpd` cell came from.
 *
 * ## Why this is a column and not a convention
 *
 * The VPD chart plots two genuinely different quantities. One is what the grower's
 * own hygrometer read, at the canopy, at a known moment. The other is what *this
 * app* derived from a temperature and a humidity the grower typed, using the
 * psychrometric relations in [AmbientClimate]. Both land in the same `REAL` column,
 * because `MIGRATION_1_2` gave `grow_events` one `vpd` column and that is the whole
 * history there is.
 *
 * Without a provenance column the chart cannot tell them apart, and the defect is
 * the one [EstimatedClimate] already exists to prevent, in a new place: a derived
 * number rendered on the same axis as a sensor reading, indistinguishable, with no
 * sentence saying which is which. The climate card's own copy — "Estimación offline
 * por latitud, altitud y estación. Sin sensores ni conexión" — is the standard this
 * project set for itself, and the VPD history has to meet it too.
 *
 * So provenance is **stored**, not inferred, and it has three states rather than two:
 *
 *  - [MEASURED] — the grower read it off an instrument they own. Only ever written
 *    by a deliberate act of logging.
 *  - [CALCULATED] — this app computed it, and the inputs that produced it are stored
 *    alongside so the number can be reproduced rather than trusted.
 *  - [UNKNOWN] — what a row written before schema v5 resolves to, because its
 *    `vpdSource` is NULL.
 *
 * The third state is the easy one to get wrong and the expensive one. A pre-v5
 * row's NULL is **not** evidence of a measurement: some of those numbers were typed
 * by the grower and some were derived by an older build, and the column cannot tell
 * which. Defaulting NULL to [MEASURED] would launder an unknown into a sensor
 * reading, which is the exact failure this type exists to prevent. `null` means "not
 * set", the same rule `Protocol`'s v4 columns follow.
 */
enum class VpdProvenance(
    val storageKey: String,
    val labelEs: String,
    val explanationEs: String
) {
    MEASURED(
        storageKey = "MEASURED",
        labelEs = "Medido",
        explanationEs = "Lectura de tu propio instrumental, tal como la anotaste."
    ),
    CALCULATED(
        storageKey = "CALCULATED",
        labelEs = "Calculado",
        explanationEs = "Calculado por la app con la temperatura y la humedad que anotaste."
    ),
    UNKNOWN(
        storageKey = "UNKNOWN",
        labelEs = "Origen desconocido",
        explanationEs = "Registrado antes de que la app guardara el origen del dato, así " +
            "que no se puede decir si era una medición o un cálculo."
    );

    /**
     * Whether this app may write this value.
     *
     * [UNKNOWN] is excluded on purpose: it is what a row with no provenance resolves
     * to, so accepting it as an input would let a caller store the absence of
     * information as if it were information.
     */
    val isWritable: Boolean get() = this != UNKNOWN

    companion object {
        /** [storageKey] for [value], or [UNKNOWN] for null and for anything unrecognised. */
        fun fromStorageKey(value: String?): VpdProvenance =
            entries.firstOrNull { it.storageKey == value } ?: UNKNOWN
    }
}

/**
 * One psychrometric evaluation: air VPD and leaf VPD from the same inputs.
 *
 * ## The two quantities, and why they are not interchangeable
 *
 * [AmbientClimate.vpdKPa] is an **air** deficit: how much more water the air at this
 * temperature could hold than it actually holds. It is what a hygrometer in the room
 * reports.
 *
 * A transpiring leaf is not at air temperature. It is cooled by its own evaporation,
 * so `T_leaf = T_air - offset`, and because saturation vapour pressure rises steeply
 * with temperature — [AmbientClimate.saturationVapourPressureKPa] is exponential —
 * the leaf sits in air that is *more* humid relative to its own saturation point.
 * Leaf VPD is therefore
 *
 * ```
 *   es(T_leaf) - ea(T_air, RH)
 * ```
 *
 * which is **smaller** than air VPD for a positive offset, and is the number that
 * actually drives stomatal response. That is why a grower uses it and why this app
 * plots it.
 *
 * Both are computed from [AmbientClimate]'s two published functions. This object
 * derives no third saturation formula: the boiling-point defect from F1 and the
 * two-temperature-models defect from F2 were both a second copy of a physically
 * fixed relation, and a leaf offset is not an excuse for one.
 *
 * ## Bounds
 *
 * With a large offset at high humidity the leaf deficit goes negative — the leaf is
 * in air that would condense on it. It is clamped to zero rather than reported
 * negative, because a negative "deficit" reads as "more than saturated" and is not a
 * quantity that exists. [offsetC] is clamped to `>= 0` for the same reason a leaf
 * cannot be *warmer* than the air it sits in while transpiring: the offset models
 * evaporative cooling, and a negative offset would be a heating term the caller has
 * no field for.
 *
 * @param offsetC how much cooler the leaf is than the air, °C. Clamped to `>= 0`.
 */
data class VpdCalculation(
    val airTemperatureC: Double,
    val relativeHumidityPercent: Double,
    val offsetC: Double,
    val leafTemperatureC: Double,
    val saturationLeafKPa: Double,
    val saturationAirKPa: Double,
    val actualVapourPressureKPa: Double,
    val airVpdKPa: Double,
    val leafVpdKPa: Double,
    val band: VpdBand
) {
    /** The number a chart plots, in kPa: leaf VPD, what the plant responds to. */
    val plottedKPa: Double get() = leafVpdKPa

    /**
     * Whether every input is inside the physically possible range.
     *
     * Derived, so a regression in [calculate] is a failing test rather than a
     * plausible-looking number on a grower's phone — the guard [EstimatedClimate]
     * carries for the same reason.
     */
    val isPlausible: Boolean
        get() = airTemperatureC in MIN_PLAUSIBLE_AIR_TEMP_C..MAX_PLAUSIBLE_AIR_TEMP_C &&
            relativeHumidityPercent in 0.0..100.0 &&
            offsetC in 0.0..MAX_PLAUSIBLE_OFFSET_C &&
            leafTemperatureC in MIN_PLAUSIBLE_LEAF_TEMP_C..MAX_PLAUSIBLE_AIR_TEMP_C &&
            airVpdKPa >= 0.0 && leafVpdKPa >= 0.0 &&
            // Leaf VPD cannot exceed air VPD: the leaf is at a lower saturation
            // pressure than the air it sits in. A value above it means the offset was
            // applied with the wrong sign.
            leafVpdKPa <= airVpdKPa + TOLERANCE_KPA

    companion object {
        /** Air below this is not a grow room; above it the saturation term runs away. */
        const val MIN_PLAUSIBLE_AIR_TEMP_C: Double = -20.0
        const val MAX_PLAUSIBLE_AIR_TEMP_C: Double = 50.0

        /**
         * Ceiling on the leaf offset, °C.
         *
         * 12 °C is past anything a transpiring leaf reaches — published leaf-to-air
         * differences sit around 2-5 °C under transpiration and at most ~7 °C in a
         * chamber under a lamp — and it keeps the clamped result inside
         * [isPlausible].
         */
        const val MAX_PLAUSIBLE_OFFSET_C: Double = 12.0

        /** Lower bound implied by the air bound minus the offset ceiling. */
        const val MIN_PLAUSIBLE_LEAF_TEMP_C: Double =
            MIN_PLAUSIBLE_AIR_TEMP_C - MAX_PLAUSIBLE_OFFSET_C

        /** Floating-point slack for a comparison of two independently computed values. */
        const val TOLERANCE_KPA: Double = 1e-9

        /** The band a leaf deficit falls in. Delegates, so the ladder has one home. */
        fun bandFor(vpdKPa: Double): VpdBand = AmbientClimate.bandFor(vpdKPa)

        /**
         * Evaluates air and leaf VPD. Pure, and it reads no clock: a value a grower
         * can watch change on their own phone has to be reproducible from its inputs.
         *
         * @param offsetC how much cooler the leaf runs than the air. Coerced into
         *   `0..MAX_PLAUSIBLE_OFFSET_C`, because a slider cannot be told "no" and a
         *   negative cooling term has no meaning here.
         */
        fun calculate(
            airTemperatureC: Double,
            relativeHumidityPercent: Double,
            offsetC: Double = 0.0
        ): VpdCalculation {
            val humidity = relativeHumidityPercent.coerceIn(0.0, 100.0)
            val offset = offsetC.coerceIn(0.0, MAX_PLAUSIBLE_OFFSET_C)

            val saturationAir = AmbientClimate.saturationVapourPressureKPa(airTemperatureC)
            val saturationLeaf = AmbientClimate.saturationVapourPressureKPa(airTemperatureC - offset)
            val actual = AmbientClimate.actualVapourPressureKPa(airTemperatureC, humidity)

            val airVpd = (saturationAir - actual).coerceAtLeast(0.0)
            val leafVpd = (saturationLeaf - actual).coerceAtLeast(0.0)

            return VpdCalculation(
                airTemperatureC = airTemperatureC,
                relativeHumidityPercent = humidity,
                offsetC = offset,
                leafTemperatureC = airTemperatureC - offset,
                saturationLeafKPa = saturationLeaf,
                saturationAirKPa = saturationAir,
                actualVapourPressureKPa = actual,
                airVpdKPa = airVpd,
                leafVpdKPa = leafVpd,
                band = AmbientClimate.bandFor(leafVpd)
            )
        }
    }
}

/** Which calculator input a rejection is about, so the dialog can mark one box. */
enum class VpdCalculatorField(val labelEs: String) {
    AIR_TEMPERATURE("temperatura del aire"),
    HUMIDITY("humedad relativa"),
    OFFSET("diferencia hoja-aire")
}

/**
 * The calculator's editable state.
 *
 * Not a [VpdCalculation]: these fields are text a grower is still typing and half of
 * them are not numbers yet. The screen holds this, asks [VpdCalculator] for the
 * outcome, and renders either a number or the Spanish sentence saying what is wrong.
 */
data class VpdCalculatorForm(
    /** Air temperature as typed, °C. */
    val airTemperature: String = DEFAULT_AIR_TEMPERATURE,
    /** Relative humidity as typed, percent. */
    val humidity: String = DEFAULT_HUMIDITY,
    /** Leaf-to-air offset as typed, °C. */
    val offset: String = DEFAULT_OFFSET
) {
    companion object {
        /**
         * Starting values: 24 °C, 60 %, 2 °C of leaf offset.
         *
         * That is the middle of a well-run indoor tent. It is only the calculator's
         * *input* default — nothing derived from it is stored anywhere until the
         * grower presses "Registrar en Bitácora", and what is stored then is labelled
         * [VpdProvenance.CALCULATED].
         */
        const val DEFAULT_AIR_TEMPERATURE: String = "24"
        const val DEFAULT_HUMIDITY: String = "60"
        const val DEFAULT_OFFSET: String = "2"
    }
}

/** The resolved calculator: either a number, or the Spanish reason there is none. */
sealed interface VpdCalculatorOutcome {

    /** Every input parsed and every bound held. */
    data class Ready(
        val calculation: VpdCalculation,
        val display: VpdCalculatorDisplay
    ) : VpdCalculatorOutcome

    /** Something is wrong with one input. */
    data class Invalid(
        val field: VpdCalculatorField,
        val problemEs: String
    ) : VpdCalculatorOutcome
}

/**
 * The calculator's readout, already in Spanish.
 *
 * Every number here is a **calculated** number. There is no instrument behind this
 * screen, and the readout says so on every line rather than in a footnote — the same
 * decision [ClimateCardContent] made with its `≈` marker, for the same reason. Here
 * the marker is the word: `calculado`.
 */
data class VpdCalculatorDisplay(
    val leafVpdEs: String,
    val airVpdEs: String,
    val leafTemperatureEs: String,
    val bandLabelEs: String,
    val bandAdviceEs: String,
    val needsAction: Boolean,
    val provenance: VpdProvenance,
    val provenanceLabelEs: String,
    val provenanceExplanationEs: String
)

/**
 * The VPD calculator, as a pure function of a [VpdCalculatorForm].
 *
 * ## The validation rules, and why each exists
 *
 * A field that fails to parse is **rejected**, not defaulted. A calculator that
 * quietly substitutes 24 °C for "abc" answers a question nobody asked, and the VPD it
 * produces looks exactly like a real one — which is the [EstimatedClimate] defect one
 * level down, at a text box.
 *
 * The accepted ranges are wider than a grower's likely values on purpose: refusing 60 %
 * humidity for being a round number would only teach the grower that the app is
 * broken. What is refused is the physically impossible — a negative humidity, an
 * offset that would make the leaf hotter than the room it sits in.
 */
object VpdCalculator {

    /** Label of the air temperature input. */
    const val AIR_TEMPERATURE_LABEL_ES: String = "Temperatura del aire"

    /** Label of the relative humidity input. */
    const val HUMIDITY_LABEL_ES: String = "Humedad relativa"

    /** Label of the leaf offset input. */
    const val OFFSET_LABEL_ES: String = "Diferencia hoja-aire"

    /** Sentence under the offset field, so "leaf temperature" is never a surprise. */
    const val OFFSET_HINT_ES: String =
        "Cuántos grados tiene la hoja por debajo del aire. Suele ser entre 2 y 5 °C " +
            "cuando la planta transpira. Con 0 obtienes el déficit del aire, no el de la hoja."

    /**
     * The sentence that states the calculator is not an instrument.
     *
     * Deliberately parallel to [EstimatedClimate.SOURCE_NOTE_ES]: both say the app
     * derived the number and neither involves a sensor, so a grower who has learned
     * to read one can read the other.
     */
    const val CALCULATOR_SOURCE_ES: String =
        "Calculado por la app con la temperatura y la humedad que escribiste. " +
            "No hay ningún sensor detrás de este valor."

    /** Label of the air VPD readout. */
    const val AIR_VPD_LABEL_ES: String = "Déficit del aire"

    /** Label of the leaf VPD readout, the number the chart plots. */
    const val LEAF_VPD_LABEL_ES: String = "Déficit de la hoja"

    /** Label of the resulting band. */
    const val BAND_LABEL_ES: String = "Zona"

    private const val TEMPERATURE_UNIT_ES = "°C"
    private const val HUMIDITY_UNIT_ES = "%"
    private const val VPD_UNIT_ES = "kPa"

    /**
     * Largest VPD a grow row may carry, kPa.
     *
     * 12 kPa is far past any indoor grow room — [VpdBand.EXTREME] already starts at
     * 2.4 — and it exists so a mistyped `120` is refused instead of being stored as
     * a 12 kPa reading that reads as "extreme" rather than as a typo.
     */
    const val MAX_PLAUSIBLE_VPD_KPA: Double = 12.0

    /** Accepted spelling of a decimal: `24`, `24,5`, `-1.5`, `+3`. */
    private val DECIMAL = Regex("""^[+-]?\d{1,3}(?:[.,]\d{1,2})?$""")

    /**
     * Parses a typed decimal written in Spanish or US notation.
     *
     * Null for anything the app is not prepared to interpret, including `NaN` and
     * `Infinity` — `toDoubleOrNull` accepts both, and every comparison against them is
     * false, which is how an unrepresentable value reaches a UI instead of a
     * rejection. Same trap [GrowRange.decode] documents.
     */
    fun parse(input: String): Double? {
        val text = input.trim().replace(',', '.')
        if (!DECIMAL.matches(text)) return null
        return text.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /**
     * Validates and evaluates [form].
     *
     * The first failing field is returned, in screen order, so the dialog marks the
     * topmost problem rather than an arbitrary one.
     */
    fun resolve(form: VpdCalculatorForm): VpdCalculatorOutcome {
        val temperature = parse(form.airTemperature) ?: return invalid(
            VpdCalculatorField.AIR_TEMPERATURE,
            "Escribe un número de grados, por ejemplo 24 o 23,5."
        )
        val humidity = parse(form.humidity) ?: return invalid(
            VpdCalculatorField.HUMIDITY,
            "Escribe un porcentaje, por ejemplo 60 o 62,5."
        )
        val offset = parse(form.offset) ?: return invalid(
            VpdCalculatorField.OFFSET,
            "Escribe un número de grados, por ejemplo 2."
        )

        if (temperature < VpdCalculation.MIN_PLAUSIBLE_AIR_TEMP_C ||
            temperature > VpdCalculation.MAX_PLAUSIBLE_AIR_TEMP_C
        ) {
            return invalid(
                VpdCalculatorField.AIR_TEMPERATURE,
                "Fuera de rango: se aceptan entre " +
                    "${es(VpdCalculation.MIN_PLAUSIBLE_AIR_TEMP_C, 0)} y " +
                    "${es(VpdCalculation.MAX_PLAUSIBLE_AIR_TEMP_C, 0)} $TEMPERATURE_UNIT_ES."
            )
        }
        if (humidity < 0.0 || humidity > 100.0) {
            return invalid(
                VpdCalculatorField.HUMIDITY,
                "La humedad relativa va de 0 a 100 $HUMIDITY_UNIT_ES."
            )
        }
        if (offset < 0.0 || offset > VpdCalculation.MAX_PLAUSIBLE_OFFSET_C) {
            return invalid(
                VpdCalculatorField.OFFSET,
                "La diferencia hoja-aire va de 0 a " +
                    "${VpdCalculation.MAX_PLAUSIBLE_OFFSET_C.toInt()} $TEMPERATURE_UNIT_ES."
            )
        }

        val calculation = VpdCalculation.calculate(temperature, humidity, offset)
        if (!calculation.isPlausible) {
            // Unreachable with the bounds above. Kept anyway, because the alternative
            // is a regression in [VpdCalculation] shipping as a plausible number.
            return invalid(
                VpdCalculatorField.AIR_TEMPERATURE,
                "Ese conjunto de valores no da un déficit de vapor creíble. Revisa la " +
                    "temperatura, la humedad y la diferencia hoja-aire."
            )
        }
        return VpdCalculatorOutcome.Ready(calculation, displayOf(calculation))
    }

    /**
     * The readout for a resolved [calculation].
     *
     * Public so a value read back out of the journal can be described in the same
     * words as one just computed.
     */
    fun displayOf(calculation: VpdCalculation): VpdCalculatorDisplay {
        val band = calculation.band
        return VpdCalculatorDisplay(
            leafVpdEs = "${es(calculation.leafVpdKPa, 2)} $VPD_UNIT_ES",
            airVpdEs = "${es(calculation.airVpdKPa, 2)} $VPD_UNIT_ES",
            leafTemperatureEs = "${es(calculation.leafTemperatureC, 1)} $TEMPERATURE_UNIT_ES",
            bandLabelEs = band.labelEs,
            bandAdviceEs = band.adviceEs,
            needsAction = band.needsAction,
            provenance = VpdProvenance.CALCULATED,
            provenanceLabelEs = VpdProvenance.CALCULATED.labelEs,
            provenanceExplanationEs = VpdProvenance.CALCULATED.explanationEs
        )
    }

    private fun invalid(field: VpdCalculatorField, problemEs: String): VpdCalculatorOutcome.Invalid =
        VpdCalculatorOutcome.Invalid(field, problemEs)

    /**
     * A Spanish decimal: comma separator, US digits underneath.
     *
     * Formatted in US locale and then swapped, because the separator must not depend
     * on the phone's locale — these strings are asserted by JVM tests that never
     * change device settings, and a Spanish comma inside a number is a JSON syntax
     * error in the export. Same rule as `ClimateCardCopy.formatLatitudeEs`.
     */
    fun es(value: Double, decimals: Int): String =
        String.format(Locale.US, "%.${decimals}f", value).replace('.', ',')
}

/**
 * One plotted VPD reading.
 *
 * @param vpdKPa the number on the axis, kPa.
 * @param provenance what produced it. Never inferred: a row with no stored
 *   provenance is [VpdProvenance.UNKNOWN], and the chart draws it as its own series
 *   rather than folding it into one of the two it can vouch for.
 */
data class VpdHistoryPoint(
    val timestamp: Long,
    val vpdKPa: Double,
    val provenance: VpdProvenance,
    val band: VpdBand,
    /** Air temperature on the same row, °C, or null when nothing was logged. */
    val airTemperatureC: Float?,
    /** Relative humidity on the same row, percent, or null. */
    val relativeHumidityPercent: Float?,
    /** Leaf offset on the same row, °C, or null for a row that carries none. */
    val offsetC: Float?
) {
    /** Whether the row has enough context to be worth listing under the chart. */
    val hasContext: Boolean get() = airTemperatureC != null || relativeHumidityPercent != null
}

/** One legend row: which series, what it is called, how many points it holds. */
data class VpdLegendEntry(
    val provenance: VpdProvenance,
    val labelEs: String,
    val count: Int
) {
    /** `3 puntos` / `1 punto`, so a legend of zero reads correctly in Spanish. */
    val countEs: String get() = if (count == 1) "1 punto" else "$count puntos"
}

/**
 * The whole VPD history, resolved into something a chart can draw without deciding
 * anything.
 *
 * The chart's honesty rules live here rather than in the composable, because they are
 * the part that can be silently wrong and the part no JVM test on this classpath can
 * reach:
 *
 *  1. **Two provable series and one honest hole.** Measured and calculated are counted
 *     separately, and rows of unknown origin keep their own count. The hole is never
 *     merged into "measured" — see [VpdProvenance].
 *  2. **No point without a provenance.** Every row goes through
 *     [VpdProvenance.fromStorageKey], so there is no path by which the chart receives
 *     a bare number and has to guess where it came from.
 *  3. **The band is derived, never stored.** [VpdHistoryPoint.band] comes from
 *     [AmbientClimate.bandFor] on the plotted value, so a number and its label cannot
 *     disagree.
 *  4. **A row with no VPD is not a zero.** `grow_events.vpd` is nullable and most
 *     journal rows are irrigation notes; those are not points, and they are not drawn
 *     at zero either.
 */
data class VpdHistoryChart(
    val points: List<VpdHistoryPoint>,
    val legend: List<VpdLegendEntry>,
    val minKPa: Double,
    val maxKPa: Double,
    val emptyEs: String,
    val provenanceEs: String
) {
    /** Whether there is enough to draw a line. One point is a readout, not a series. */
    val isDrawable: Boolean get() = points.size >= 2

    /** Total rows carrying a VPD, whatever their provenance. */
    val totalCount: Int get() = points.size

    /** Rows whose origin the app cannot vouch for. */
    val unknownCount: Int get() = points.count { it.provenance == VpdProvenance.UNKNOWN }

    /**
     * The band boundaries drawn behind the series.
     *
     * Only the bands that actually fall inside the plotted range, so a chart of a
     * stable tent does not carry four empty bands above it. [VpdBand] remains the one
     * ladder; this does not re-derive it.
     */
    val visibleBandEdgesKPa: List<Double>
        get() {
            val span = (maxKPa - minKPa).takeIf { it > 0.0 } ?: 1.0
            val lowEdge = minKPa - span * 0.1
            val highEdge = maxKPa + span * 0.1
            return listOf(VpdBand.LOW, VpdBand.OPTIMAL_VEGETATIVE, VpdBand.OPTIMAL_FLOWERING, VpdBand.HIGH)
                .map { it.maxKPa }
                .filter { it in lowEdge..highEdge }
        }
}

/**
 * A journal row reduced to the columns the VPD history reads.
 *
 * Deliberately not `com.trichome.app.data.entity.GrowEvent`: this package stays free
 * of Room entities, and the four columns a point needs plus its id are enough. The
 * ViewModel maps the entity onto it.
 */
data class VpdHistoryRow(
    val id: Long,
    val timestamp: Long,
    val vpdKPa: Float?,
    val vpdSource: String?,
    val temperatureC: Float?,
    val humidityPercent: Float?,
    val leafOffsetC: Float?
)

/**
 * Builds a [VpdHistoryChart] from stored journal rows.
 *
 * The order of [rows] is not trusted: `ORDER BY timestamp` is applied here as well, so
 * a chart drawn from an unsorted query still reads left to right.
 */
object VpdHistoryBuilder {

    /** Spanish sentence when the plant has no VPD at all. */
    const val EMPTY_ES: String =
        "Todavía no hay ningún registro de VPD para esta planta. Anota una temperatura " +
            "y una humedad en la bitácora, o registra una lectura con el botón de abajo."

    /**
     * Spanish sentence shown above the series.
     *
     * States the rule rather than assuming it was noticed: each point keeps its origin,
     * the two the app can vouch for are drawn differently, and the ones from before
     * schema v5 are marked as unknown rather than quietly counted as measurements.
     */
    const val PROVENANCE_ES: String =
        "Cada punto conserva su origen: los medidos y los calculados por la app se " +
            "dibujan de forma distinta, y los anotados antes de la versión 5 quedan " +
            "marcados como de origen desconocido."

    fun build(rows: List<VpdHistoryRow>): VpdHistoryChart {
        val points = rows
            .filter { row -> row.vpdKPa != null && row.vpdKPa.isFinite() }
            .sortedWith(compareBy({ it.timestamp }, { it.id }))
            .map { row ->
                val raw = row.vpdKPa!!.toDouble()
                val value = raw.coerceAtLeast(0.0)
                VpdHistoryPoint(
                    timestamp = row.timestamp,
                    vpdKPa = value,
                    provenance = VpdProvenance.fromStorageKey(row.vpdSource),
                    band = AmbientClimate.bandFor(value),
                    airTemperatureC = row.temperatureC,
                    relativeHumidityPercent = row.humidityPercent,
                    offsetC = row.leafOffsetC
                )
            }

        val legend = listOf(
            VpdProvenance.MEASURED,
            VpdProvenance.CALCULATED,
            VpdProvenance.UNKNOWN
        ).map { provenance ->
            VpdLegendEntry(
                provenance = provenance,
                labelEs = provenance.labelEs,
                count = points.count { it.provenance == provenance }
            )
        }

        return VpdHistoryChart(
            points = points,
            legend = legend,
            minKPa = points.minOfOrNull { it.vpdKPa } ?: 0.0,
            maxKPa = points.maxOfOrNull { it.vpdKPa } ?: 0.0,
            emptyEs = EMPTY_ES,
            provenanceEs = PROVENANCE_ES
        )
    }
}

/**
 * The editable fields of the "Registrar en Bitácora" dialog.
 *
 * Date and time are typed rather than picked from a `DatePicker` because the dialog has
 * to fit a phone at the largest text size the app allows, and two pickers plus three
 * number fields plus a notes box do not. The parsing and every Spanish error live in
 * [VpdLogFormValidator], so the composable is layout and nothing else.
 *
 * @param vpdKPa the value to log. What it *is* depends on [provenance]: a measured
 *   reading is whatever the grower's own instrument said, a calculated one is this
 *   app's leaf VPD and therefore travels with its offset and its inputs.
 */
data class VpdLogForm(
    val date: String,
    val time: String,
    val vpdKPa: String = "",
    val offsetC: String = "",
    val airTemperatureC: String = "",
    val humidityPercent: String = "",
    val notes: String = "",
    val provenance: VpdProvenance = VpdProvenance.CALCULATED
) {
    companion object {
        /** `dd/MM/yyyy`, the format `dateShort` already prints. */
        const val DATE_PATTERN_ES: String = "dd/MM/yyyy"

        /** 24-hour clock, unambiguous next to a Spanish date. */
        const val TIME_PATTERN_ES: String = "HH:mm"

        /* ── Format patterns, not sentences ───────────────────────────────────
         *
         * These are the *machine* shape of a date, a time and a decimal. No amount of test
         * coverage makes `"%02d/%02d/%04d"` Spanish or not, so they are not UI copy — but they
         * are declared here anyway so the log dialog holds no string literal at all, which is
         * what lets `VpdHistoryStructureTest` assert that dialog with no exception carved out
         * for this file. The separator is `,` because `DecimalFormat` follows the device locale
         * and [VpdCalculator.parse] accepts either.
         */

        /** Two decimals, as the VPD value carries. */
        const val VPD_FORMAT: String = "%.2f"

        /** One decimal, as temperature, humidity and the leaf offset carry. */
        const val ONE_DECIMAL_FORMAT: String = "%.1f"

        /** The decimal separator this app writes in Spanish copy. */
        const val DECIMAL_SEPARATOR_ES: String = ","

        /** The character `String.format` writes as the decimal separator in US locale. */
        const val DECIMAL_SEPARATOR_SOURCE: String = "."

        /**
         * Shape of an accepted date field.
         *
         * Shape only. Whether `31/02/2026` is a real day is [VpdLogFormValidator]'s question,
         * decided by building the `LocalDate` and catching the exception — a regex cannot
         * know how long February is, and writing one that tries would accept 30 February.
         */
        val DATE: Regex = Regex("""^\d{2}/\d{2}/\d{4}$""")

        /**
         * Shape of an accepted time field: two digits, a colon, two digits.
         *
         * `24:00` and `25:61` match this and are refused by [VpdLogFormValidator], which is
         * the division of labour between the two.
         */
        val TIME: Regex = Regex("""^\d{2}:\d{2}$""")

        /**
         * `dd/MM/yyyy` from the three parts the dialog seeds itself with.
         *
         * The pattern lives in [DATE_PATTERN_ES] and only the substitution is here, so the date
         * the dialog opens on is the same format [DATE] accepts — a mismatch would make the
         * dialog reject its own seed on the first validation.
         */
        fun dateLabelEs(dayOfMonth: Int, monthValue: Int, year: Int): String =
            String.format(Locale.US, DATE_PATTERN_ES, dayOfMonth, monthValue, year)

        /** `HH:mm` from the two parts the dialog seeds itself with. */
        fun timeLabelEs(hour: Int, minute: Int): String =
            String.format(Locale.US, TIME_PATTERN_ES, hour, minute)

        /**
         * A value as a Spanish decimal, or empty when there is no value.
         *
         * Formatted in US locale and then swapped, because the separator must not depend on the
         * phone's locale — [VpdCalculator.parse] accepts either, but a grower reading the field
         * expects one. Empty rather than a placeholder when the calculation is absent: a field
         * pre-filled with `--` reads as an error the grower did not make, and the dialog's own
         * `Invalid` state already says which field is wrong.
         *
         * Takes a [Double] because every value it formats comes out of [VpdCalculation], which
         * is [Double] throughout — the calculator's own arithmetic, not the `Float` a journal row
         * carries, so no rounding happens on the way to the field.
         */
        fun decimalEs(value: Double?, pattern: String): String =
            value?.let {
                String.format(Locale.US, pattern, it)
                    .replace(DECIMAL_SEPARATOR_SOURCE, DECIMAL_SEPARATOR_ES)
            }.orEmpty()
    }
}

/** A resolved "Registrar en Bitácora" form. */
sealed interface VpdLogOutcome {

    /**
     * The form is loggable.
     *
     * [timestamp] is resolved in the caller's zone, so a reading typed at 00:30 local
     * lands on the local day rather than on the previous one in UTC.
     */
    data class Ready(
        val timestamp: Long,
        val vpdKPa: Float,
        val provenance: VpdProvenance,
        val leafOffsetC: Float?,
        val airTemperatureC: Float?,
        val humidityPercent: Float?,
        val notes: String?
    ) : VpdLogOutcome

    /** Something is wrong. [problemEs] says what, in Spanish. */
    data class Invalid(val problemEs: String) : VpdLogOutcome
}

/**
 * The journal-logging decision for one VPD reading.
 *
 * ## Why the tent is read, not written
 *
 * A `grow_events` row belongs to a **plant**, and the plant belongs to a tent. The
 * dialog therefore shows the tent the reading is assigned to and takes it from
 * `plants.tentId`. It is not an input: writing a `tentId` onto the event would be a
 * second source of truth for a fact that already has one, and this project has paid for
 * two of those already — F1's boiling point, F2's temperature models.
 *
 * ## Why provenance cannot be UNKNOWN here
 *
 * This is the one place a new row is created, and a row created here is a row whose
 * origin the app knows. Accepting [VpdProvenance.UNKNOWN] would store the absence of
 * provenance into a row that could have had it.
 */
object VpdLogFormValidator {

    /** Title of the dialog. */
    const val TITLE_ES: String = "Registrar en Bitácora"

    /** Label of the confirm button. */
    const val CONFIRM_LABEL_ES: String = "Registrar"

    /** Label of the notes field. */
    const val NOTES_LABEL_ES: String = "Notas"

    /** Label of the VPD value field. */
    const val VALUE_LABEL_ES: String = "VPD (kPa)"

    /** Label of the date field. */
    const val DATE_LABEL_ES: String = "Fecha"

    /** Label of the time field. */
    const val TIME_LABEL_ES: String = "Hora"

    /** Label of the air temperature field. */
    const val TEMPERATURE_LABEL_ES: String = "Temperatura del aire"

    /** Label of the humidity field. */
    const val HUMIDITY_LABEL_ES: String = "Humedad relativa"

    /** Heading of the provenance choice. */
    const val PROVENANCE_HEADING_ES: String = "Origen del dato"

    /** Label of the leaf offset field, only shown for a calculated value. */
    const val OFFSET_LABEL_ES: String = "Diferencia hoja-aire (°C)"

    /** Label of the dialog's dismiss button. */
    const val CANCEL_ES: String = "Cancelar"

    /**
     * The sentence that names the tent the reading is assigned to.
     *
     * A plant with no tent says so rather than showing an empty line: "sin carpa" is a
     * real state this app has to render honestly elsewhere too, and a blank here
     * would read as a rendering fault.
     */
    fun tentSentenceEs(tentName: String?): String =
        if (tentName.isNullOrBlank()) {
            "Esta planta no está en ninguna carpa. El registro queda asociado a la planta."
        } else {
            "El registro va a la bitácora de la planta, dentro de la carpa $tentName."
        }

    /**
     * Validates [form] and resolves its instant.
     *
     * @param zone the zone the typed date and time are local to. Injected, so the
     *   result is deterministic in a test and so this package never reaches for the
     *   device clock.
     */
    fun validate(form: VpdLogForm, zone: ZoneId): VpdLogOutcome {
        if (!VpdLogForm.DATE.matches(form.date.trim())) {
            return VpdLogOutcome.Invalid("La fecha va en formato ${VpdLogForm.DATE_PATTERN_ES}.")
        }
        if (!VpdLogForm.TIME.matches(form.time.trim())) {
            return VpdLogOutcome.Invalid("La hora va en formato ${VpdLogForm.TIME_PATTERN_ES}.")
        }

        val date = parseDate(form.date.trim())
            ?: return VpdLogOutcome.Invalid("Esa fecha no existe en el calendario.")
        val time = parseTime(form.time.trim())
            ?: return VpdLogOutcome.Invalid("Esa hora no existe en un reloj de 24 horas.")

        if (!form.provenance.isWritable) {
            return VpdLogOutcome.Invalid(
                "Elige si la lectura es medida o calculada: la app no puede inventar el " +
                    "origen de un dato."
            )
        }

        val vpd = VpdCalculator.parse(form.vpdKPa)
            ?.takeIf { it >= 0.0 && it <= VpdCalculator.MAX_PLAUSIBLE_VPD_KPA }
            ?: return VpdLogOutcome.Invalid(
                "El VPD va de 0 a ${VpdCalculator.MAX_PLAUSIBLE_VPD_KPA.toInt()} kPa. " +
                    "Escríbelo con dos decimales."
            )

        // A calculated reading must be reproducible, so the offset that produced it is
        // stored beside it. A measured one does not need one: the grower's instrument
        // reports air VPD and there is no leaf offset behind it.
        val offset = if (form.provenance == VpdProvenance.CALCULATED) {
            VpdCalculator.parse(form.offsetC)
                ?.takeIf { it >= 0.0 && it <= VpdCalculation.MAX_PLAUSIBLE_OFFSET_C }
                ?: return VpdLogOutcome.Invalid(
                    "Un valor calculado guarda la diferencia hoja-aire con la que se " +
                        "obtuvo, para poder reproducirlo. Escribe un número entre 0 y " +
                        "${VpdCalculation.MAX_PLAUSIBLE_OFFSET_C.toInt()} °C."
                )
        } else {
            null
        }

        val temperature = VpdCalculator.parse(form.airTemperatureC)
        val humidity = VpdCalculator.parse(form.humidityPercent)

        return VpdLogOutcome.Ready(
            timestamp = LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli(),
            vpdKPa = vpd.toFloat(),
            provenance = form.provenance,
            leafOffsetC = offset?.toFloat(),
            airTemperatureC = temperature?.toFloat(),
            humidityPercent = humidity?.toFloat(),
            notes = form.notes.trim().ifBlank { null }
        )
    }

    private fun parseDate(text: String): LocalDate? = try {
        val parts = text.split('/')
        LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
    } catch (e: RuntimeException) {
        // `DateTimeException` and `NumberFormatException` both land here, and a
        // malformed date is a rejected field rather than a crash.
        null
    }

    private fun parseTime(text: String): LocalTime? = try {
        val parts = text.split(':')
        LocalTime.of(parts[0].toInt(), parts[1].toInt())
    } catch (e: RuntimeException) {
        null
    }
}