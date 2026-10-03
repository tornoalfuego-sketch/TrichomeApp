package com.trichome.app.model

import com.trichome.app.data.entity.Protocol
import com.trichome.app.data.model.GrowRange

/**
 * The write path for a protocol's declared agronomic targets.
 *
 * ## Why this file exists
 *
 * Fourteen nullable columns on `protocols` and one on `protocol_stages` existed to be
 * read, exported and printed, with no way to write any of them. The editor's save
 * signature took a `List<Pair<String, Int>>` — a stage name and a duration — so there
 * was no parameter a band could travel through. Fifteen columns of storage, a migration,
 * an export and a display surface, and a grower who could look at fifteen things and
 * change none of them.
 *
 * So the decision of *what a typed field means* lives here, in plain Kotlin, and the
 * composable is layout. Compose has no unit-test runtime in this project — neither
 * Robolectric nor `compose-ui-test` is on the `test` classpath — and four separate bug
 * classes have already shipped from composable-level logic no JVM test could reach, so a
 * rule that lived in a dialog would have been a rule nobody could hold.
 *
 * ## Three rules the shapes here enforce
 *
 * **A band is two inputs and one value, and it commits as one or not at all.**
 * [GrowRange] exists because `phLow = 6.0` with `phHigh = null` is representable and
 * means nothing. [resolveBand] is the only way to build one, and it accepts exactly
 * three shapes: both bounds blank (`null` — "no opinion"), both bounds parsed and
 * ordered (a band), and everything else (a refusal, with the Spanish sentence that says
 * which of the three things went wrong). A half-typed band is not a band with a zero in
 * it.
 *
 * **An empty input writes `null`, never `0f`.** A grower who clears a field has to get
 * [ProtocolExtendedFields.SIN_DEFINIR] back. A fabricated zero would be rendered in the
 * metric register, in the same face as a number they chose, and `0` is a legitimate
 * reading for several of these quantities — so a default would be indistinguishable from
 * an answer.
 *
 * **A flat band is a real target.** `low == high` commits. A grower holding exactly
 * pH 6.0 has declared something, [GrowRange.isSingle] already exists to describe it, and
 * the only thing a `low < high` rule would add is the refusal of a legitimate answer.
 *
 * ## A target is not a reading
 *
 * Nothing in this app reads a sensor. Every VPD number it knows is an offline estimate
 * from latitude, altitude and season ([AmbientClimate]), and a pH band a grower types is
 * a decision rather than a measurement. So the copy here says *objetivo*, the field
 * labels come from [ProtocolExtendedFields] where they already say so, and
 * [GOAL_NOT_MEASUREMENT_ES] is printed on both write surfaces.
 */
object ProtocolTargetEditor {

    /* ── Chrome copy ───────────────────────────────────────────────────────── */

    /** Heading of the grow-wide write surface. */
    const val TITLE_ES: String = "Objetivos del cultivo"

    /** Label of the confirm button on both write surfaces. */
    const val CONFIRM_ES: String = "Guardar"

    /** Label of the dismiss button on both write surfaces. */
    const val CANCEL_ES: String = "Cancelar"

    /** The two bounds of a band, in order. */
    const val MIN_ES: String = "Mínimo"
    const val MAX_ES: String = "Máximo"

    /**
     * The affordance on a protocol card group.
     *
     * A group that cannot be opened is a group that reads as a fact sheet, and this
     * card was exactly that: fourteen rows of "Sin definir" with no way to change any of
     * them.
     */
    const val EDIT_HINT_ES: String = "Toca el grupo para editarlo"

    /**
     * Said on a group that still has nothing in it, so an all-"Sin definir" group says
     * why it is empty instead of leaving the grower to guess.
     */
    const val UNSET_HINT_ES: String = "Este grupo no tiene nada definido todavía"

    /**
     * The sentence both write surfaces carry.
     *
     * Says what the number is before the grower types it, because the column name and
     * the unit alone are what turn a declared band into an implied measurement. This is
     * the same correction [ProtocolExtendedFields.ETAPA_VPD_NOTA_ES] makes on the read
     * side, and it is repeated here on purpose: the read side is not on screen while
     * this one is.
     */
    const val GOAL_NOT_MEASUREMENT_ES: String =
        "Son objetivos: los valores que quieres mantener. No son mediciones. Esta app " +
            "no lee ningún sensor, y sus propias estimaciones son offline."

    /** Heading of the per-stage write surface, with the stage's name. */
    fun stageTitleEs(stageName: String): String = "Objetivo por etapa · ${stageName.trim()}"

    /** Title of the grow-wide surface for one card group. */
    fun titleEs(groupTitleEs: String): String = "$TITLE_ES · $groupTitleEs"

    /* ── Refusals ──────────────────────────────────────────────────────────── */

    /**
     * The number could not be read.
     *
     * Names the field so the grower knows which of the seven fields the dialog is
     * complaining about, rather than making them find it by trial.
     */
    fun notANumberEs(field: ProtocolTargetField): String =
        "«${field.labelEs}» no es un número. Usa un punto o una coma para los decimales."

    /**
     * One bound of a band is filled and the other is not.
     *
     * The one refusal that matters most in this file, because it is the state the
     * [GrowRange] type was built to make unrepresentable: a grower who types `6.0` and
     * walks away has not declared `6.0 – 6.0`.
     */
    const val BAND_INCOMPLETE_ES: String =
        "Un rango necesita los dos extremos: rellena el mínimo y el máximo, o vacía " +
            "los dos para dejarlo sin definir."

    /** The bounds are the wrong way round. */
    const val BAND_INVERTED_ES: String =
        "El mínimo no puede ser mayor que el máximo."

    /** A quantity that cannot physically be negative was typed negative. */
    fun notANegativeEs(field: ProtocolTargetField): String =
        "«${field.labelEs}» no puede ser un valor negativo."

    /** A quantity above its physical ceiling was typed. Only humidity has one. */
    fun aboveTheCeilingEs(field: ProtocolTargetField, ceiling: Float): String =
        "«${field.labelEs}» no puede pasar de " +
            "${ProtocolExtendedFields.formatDecimal(ceiling, field.decimals)} ${field.unitEs}."

    /* ── Parsing ───────────────────────────────────────────────────────────── */

    /**
     * A typed number, or null when there is not one there.
     *
     * `null` means "no number in this box" and covers both an empty box and a box that
     * says `abc`; the caller decides whether that is an unset field or a refusal, and
     * the two are different answers with different copy.
     *
     * Both separators are accepted because both are typed on real keyboards:
     * `KeyboardType.Decimal` offers a comma on a Spanish device and a dot on an English
     * one, and refusing either would make the dialog's own seed un-typeable on half the
     * phones it ships to. Everything else — a second separator, a letter, a sign in the
     * middle — fails to parse and is refused by the caller.
     */
    fun parseDecimalEs(raw: String): Float? {
        val text = raw.trim().replace(',', '.')
        if (text.isEmpty()) return null
        val value = text.toFloatOrNull() ?: return null
        // `toFloatOrNull` accepts "NaN" and "Infinity", and every comparison against
        // them is false — including `low <= high`, which is the only guard `GrowRange`
        // relies on. GrowRange.decode refuses them for the same reason.
        return if (value.isFinite()) value else null
    }

    /** A stored value as the text the input opens with, in Spanish notation. */
    fun numberEs(value: Float?, decimals: Int): String =
        value?.let { ProtocolExtendedFields.formatDecimal(it, decimals) }.orEmpty()

    /**
     * The heading one field's inputs sit under, e.g. `VPD objetivo · kPa`.
     *
     * A band's two boxes share one heading, so the unit has to be on the heading: inside
     * either box's floating label it would be the third thing in a 156 dp label, and
     * "pH objetivo · pH" is noise, so a label that already carries its unit is not
     * repeated.
     */
    fun fieldHeadingEs(field: ProtocolTargetField): String =
        if (field.showsUnit) "${field.labelEs} · ${field.unitEs}" else field.labelEs

    /* ── The one range resolver ────────────────────────────────────────────── */

    /**
     * The only way a [GrowRange] is built from typed text.
     *
     * Three outcomes, and the middle one is the whole point:
     *
     * - both blank → [RangeInput.Unset], which the caller writes as `null`
     * - both parse, ordered → [RangeInput.Band], including `low == high`
     * - anything else → [RangeInput.Refused] with the Spanish sentence
     *
     * The negatives are checked before the bounds, so a band whose lower bound is
     * negative reports the negative rather than a comparison that happened to pass.
     */
    fun resolveBand(
        lowRaw: String,
        highRaw: String,
        field: ProtocolTargetField
    ): RangeInput {
        val lowText = lowRaw.trim()
        val highText = highRaw.trim()
        if (lowText.isEmpty() && highText.isEmpty()) return RangeInput.Unset
        if (lowText.isEmpty() || highText.isEmpty()) return RangeInput.Refused(BAND_INCOMPLETE_ES)

        val low = parseDecimalEs(lowText)
            ?: return RangeInput.Refused(notANumberEs(field))
        val high = parseDecimalEs(highText)
            ?: return RangeInput.Refused(notANumberEs(field))

        if (!field.allowsNegative && (low < 0f || high < 0f)) {
            return RangeInput.Refused(notANegativeEs(field))
        }
        if (low > high) return RangeInput.Refused(BAND_INVERTED_ES)

        // `low <= high` is what GrowRange's constructor requires, and it holds here.
        return RangeInput.Band(GrowRange(low, high))
    }

    /** One bound's refusal against the band's physical ceiling, when it has one. */
    fun bandAboveCeilingEs(field: ProtocolTargetField, range: GrowRange): String? {
        val ceiling = field.upperBound ?: return null
        return if (range.high > ceiling) aboveTheCeilingEs(field, ceiling) else null
    }

    /* ── Form seeding ──────────────────────────────────────────────────────── */

    /**
     * The form a write surface opens on for [fields] of [protocol].
     *
     * Seeded from the stored row and nowhere else, so what the grower sees on opening
     * is what the card was printing a moment earlier. A field with no stored value
     * opens empty rather than on a placeholder: a box holding `--` reads as an error the
     * grower did not make.
     */
    fun formFor(
        protocol: Protocol,
        fields: List<ProtocolTargetField>
    ): ProtocolTargetForm = ProtocolTargetForm(
        fields.associateWith { seedInput(it, protocol.valueOf(it)) }
    )

    /** What a single field's box opens with, from the stored value or nothing. */
    fun seedInput(field: ProtocolTargetField, value: Any?): TargetInput = when (field.kind) {
        ProtocolTargetKind.BAND -> {
            val range = value as? GrowRange
            TargetInput.Band(
                low = numberEs(range?.low, field.decimals),
                high = numberEs(range?.high, field.decimals)
            )
        }
        ProtocolTargetKind.METRIC ->
            TargetInput.Metric(numberEs(value as? Float, field.decimals))
        ProtocolTargetKind.TEXT ->
            TargetInput.Free((value as? String).orEmpty())
    }

    /* ── Resolution ────────────────────────────────────────────────────────── */

    /**
     * Resolves the form into the protocol to write.
     *
     * Only the fields of the group being edited are read, and the answer is built with
     * [Protocol.copy] from the row the screen already had. That is what makes a
     * targets edit non-destructive by construction: `plantId`, `cycleStartAt`,
     * `isActive`, the name, the photoperiod and every field outside this group are
     * carried by the copy, so there is no column this function can forget to preserve.
     */
    fun resolve(
        form: ProtocolTargetForm,
        protocol: Protocol,
        fields: List<ProtocolTargetField>
    ): ProtocolTargetOutcome {
        var resolved = protocol
        fields.forEach { field ->
            val input = form.inputFor(field)
            when (field.kind) {
                ProtocolTargetKind.BAND -> {
                    val outcome = resolveBand(
                        (input as TargetInput.Band).low,
                        (input as TargetInput.Band).high,
                        field
                    )
                    when (outcome) {
                        is RangeInput.Refused ->
                            return ProtocolTargetOutcome.Invalid(outcome.problemEs)
                        is RangeInput.Band -> {
                            bandAboveCeilingEs(field, outcome.range)?.let {
                                return ProtocolTargetOutcome.Invalid(it)
                            }
                            resolved = resolved.withBand(field, outcome.range)
                        }
                        RangeInput.Unset -> resolved = resolved.withBand(field, null)
                    }
                }
                ProtocolTargetKind.METRIC -> {
                    val text = (input as TargetInput.Metric).text.trim()
                    if (text.isEmpty()) {
                        resolved = resolved.withMetric(field, null)
                    } else {
                        val value = parseDecimalEs(text)
                            ?: return ProtocolTargetOutcome.Invalid(notANumberEs(field))
                        if (!field.allowsNegative && value < 0f) {
                            return ProtocolTargetOutcome.Invalid(notANegativeEs(field))
                        }
                        val ceiling = field.upperBound
                        if (ceiling != null && value > ceiling) {
                            return ProtocolTargetOutcome.Invalid(aboveTheCeilingEs(field, ceiling))
                        }
                        resolved = resolved.withMetric(field, value)
                    }
                }
                ProtocolTargetKind.TEXT -> {
                    // Blank is unset, and an empty text field and a whitespace-only one
                    // are the same answer to "did the grower fill this in?".
                    val text = (input as TargetInput.Free).text.trim()
                    resolved = resolved.withText(field, text.ifEmpty { null })
                }
            }
        }
        return ProtocolTargetOutcome.Ready(resolved)
    }

    /**
     * Resolves one stage's band into the value to write on its row.
     *
     * Separate from [resolve] because it is a different entity: this lands on
     * `protocol_stages.vpdTarget`, one column of one row, and it is the only path that
     * may write it. That is deliberate — see `ProtocolRepository.setStageVpdTarget`.
     */
    fun resolveStageTarget(
        input: TargetInput.Band,
        field: ProtocolTargetField = ProtocolTargetField.VPD_BAND
    ): StageTargetOutcome = when (val outcome = resolveBand(input.low, input.high, field)) {
        is RangeInput.Band -> {
            val ceilingRefusal = bandAboveCeilingEs(field, outcome.range)
            if (ceilingRefusal == null) {
                StageTargetOutcome.Ready(outcome.range)
            } else {
                StageTargetOutcome.Invalid(ceilingRefusal)
            }
        }
        is RangeInput.Refused -> StageTargetOutcome.Invalid(outcome.problemEs)
        RangeInput.Unset -> StageTargetOutcome.Ready(null)
    }
}

/** The three outcomes of typing into a pair of bounds. */
sealed interface RangeInput {
    /** Both boxes are empty: the field has no opinion. Written as `null`. */
    data object Unset : RangeInput

    /** Both boxes hold a number and it is ordered. `low == high` included. */
    data class Band(val range: GrowRange) : RangeInput

    /** Something is wrong, and [problemEs] says what, in Spanish. */
    data class Refused(val problemEs: String) : RangeInput
}

/** What one field's boxes hold, before anything has been parsed. */
sealed interface TargetInput {
    /** One number box. Empty means unset, not zero. */
    data class Metric(val text: String) : TargetInput

    /** The two bounds of a band. Two boxes, one value, and they commit together. */
    data class Band(val low: String, val high: String) : TargetInput

    /** The grower's own words. */
    data class Free(val text: String) : TargetInput
}

/** A resolved group of target inputs. */
data class ProtocolTargetForm(
    private val inputs: Map<ProtocolTargetField, TargetInput> = emptyMap()
) {
    /** The boxes of [field]. An unseeded field is two empty boxes, never a zero. */
    fun inputFor(field: ProtocolTargetField): TargetInput =
        inputs[field] ?: when (field.kind) {
            ProtocolTargetKind.BAND -> TargetInput.Band("", "")
            ProtocolTargetKind.METRIC -> TargetInput.Metric("")
            ProtocolTargetKind.TEXT -> TargetInput.Free("")
        }

    /** The same form with one field's boxes replaced. */
    fun withInput(field: ProtocolTargetField, input: TargetInput): ProtocolTargetForm =
        copy(inputs = inputs + (field to input))
}

/** A resolved set of target inputs. */
sealed interface ProtocolTargetOutcome {
    /** The protocol to write, with every field of the group applied. */
    data class Ready(val protocol: Protocol) : ProtocolTargetOutcome

    /** Something is wrong, and [problemEs] says what, in Spanish. */
    data class Invalid(val problemEs: String) : ProtocolTargetOutcome
}

/** A resolved stage band. */
sealed interface StageTargetOutcome {
    /** The band to write, or null when the grower cleared both boxes. */
    data class Ready(val band: GrowRange?) : StageTargetOutcome

    /** Something is wrong, and [problemEs] says what, in Spanish. */
    data class Invalid(val problemEs: String) : StageTargetOutcome
}

/** What shape a field's inputs take. Decided once, here, and nowhere in a composable. */
enum class ProtocolTargetKind { BAND, METRIC, TEXT }

/**
 * One of the fourteen declared targets on a protocol, and everything the input for it
 * has to know.
 *
 * The table is the reason a composable never has to ask whether a field is a band: the
 * kind, the unit, the decimals, whether a negative value is physically possible and
 * whether there is a ceiling are properties of the *quantity*, not of the widget. A
 * composable that had to decide them would decide them per call site, and one call
 * site would eventually decide humidity as if it were a temperature.
 *
 * @param allowsNegative true only for the two air temperatures. A room at 4 °C is a
 *   real target; -5 % relative humidity and -450 µmol/m²/s are typos.
 * @param upperBound the physical ceiling, where one exists. Only relative humidity has
 *   one, and it is 100 because that is what the unit means.
 */
enum class ProtocolTargetField(
    val labelEs: String,
    val unitEs: String?,
    val decimals: Int,
    val kind: ProtocolTargetKind,
    val allowsNegative: Boolean = false,
    val upperBound: Float? = null
) {
    VPD_BAND(
        ProtocolExtendedFields.ETIQUETA_VPD,
        ProtocolExtendedFields.UNIDAD_KPA,
        ProtocolExtendedFields.DECIMALS_VPD,
        ProtocolTargetKind.BAND
    ),
    PH_RANGE(
        ProtocolExtendedFields.ETIQUETA_PH,
        ProtocolExtendedFields.UNIDAD_PH,
        ProtocolExtendedFields.DECIMALS_PH,
        ProtocolTargetKind.BAND
    ),
    EC_RANGE(
        ProtocolExtendedFields.ETIQUETA_EC,
        ProtocolExtendedFields.UNIDAD_EC,
        ProtocolExtendedFields.DECIMALS_EC,
        ProtocolTargetKind.BAND
    ),
    LIGHT_TEMP_CELSIUS(
        ProtocolExtendedFields.ETIQUETA_TEMP_LUZ,
        ProtocolExtendedFields.UNIDAD_CELSIUS,
        ProtocolExtendedFields.DECIMALS_TEMPERATURE,
        ProtocolTargetKind.METRIC,
        allowsNegative = true
    ),
    LIGHT_HUMIDITY_PERCENT(
        ProtocolExtendedFields.ETIQUETA_HR_LUZ,
        ProtocolExtendedFields.UNIDAD_PERCENT,
        ProtocolExtendedFields.DECIMALS_WHOLE,
        ProtocolTargetKind.METRIC,
        upperBound = 100f
    ),
    DARK_TEMP_CELSIUS(
        ProtocolExtendedFields.ETIQUETA_TEMP_OSCURIDAD,
        ProtocolExtendedFields.UNIDAD_CELSIUS,
        ProtocolExtendedFields.DECIMALS_TEMPERATURE,
        ProtocolTargetKind.METRIC,
        allowsNegative = true
    ),
    DARK_HUMIDITY_PERCENT(
        ProtocolExtendedFields.ETIQUETA_HR_OSCURIDAD,
        ProtocolExtendedFields.UNIDAD_PERCENT,
        ProtocolExtendedFields.DECIMALS_WHOLE,
        ProtocolTargetKind.METRIC,
        upperBound = 100f
    ),
    PPFD(
        ProtocolExtendedFields.ETIQUETA_PPFD,
        ProtocolExtendedFields.UNIDAD_PPFD,
        ProtocolExtendedFields.DECIMALS_WHOLE,
        ProtocolTargetKind.METRIC
    ),
    DLI(
        ProtocolExtendedFields.ETIQUETA_DLI,
        ProtocolExtendedFields.UNIDAD_DLI,
        ProtocolExtendedFields.DECIMALS_DLI,
        ProtocolTargetKind.METRIC
    ),
    LIGHT_TYPE(
        ProtocolExtendedFields.ETIQUETA_TIPO_LUZ,
        null,
        0,
        ProtocolTargetKind.TEXT
    ),
    LAMP_POWER_WATTS(
        ProtocolExtendedFields.ETIQUETA_POTENCIA,
        ProtocolExtendedFields.UNIDAD_WATTS,
        ProtocolExtendedFields.DECIMALS_WHOLE,
        ProtocolTargetKind.METRIC
    ),
    SUBSTRATE_TYPE(
        ProtocolExtendedFields.ETIQUETA_SUSTRATO,
        null,
        0,
        ProtocolTargetKind.TEXT
    ),
    WATERING_STRATEGY(
        ProtocolExtendedFields.ETIQUETA_RIEGO,
        null,
        0,
        ProtocolTargetKind.TEXT
    ),
    OBSERVATIONS(
        ProtocolExtendedFields.ETIQUETA_OBSERVACIONES,
        null,
        0,
        ProtocolTargetKind.TEXT
    );

    /**
     * Whether the unit has to be printed next to the label.
     *
     * "pH objetivo" already carries its unit and "pH objetivo · pH" is noise, while
     * "VPD objetivo" does not carry "kPa" and a bare VPD number is a number without a
     * scale. So the label decides, once, here.
     */
    val showsUnit: Boolean
        get() = unitEs != null && !labelEs.contains(unitEs)
}

/**
 * One titled run of target fields, so the write surface and the card agree on which
 * fields belong together.
 *
 * The [titleEs] values are [ProtocolExtendedFields]' own, which is what lets the card
 * open the right editor from a group it was handed as a title. [forTitle] is the
 * reverse lookup and it returns null for anything it does not own — including the
 * per-stage group, which is written on a different surface because it is a different
 * entity.
 */
enum class ProtocolTargetGroup(val titleEs: String, val fields: List<ProtocolTargetField>) {
    AMBIENTE(
        ProtocolExtendedFields.GRUPO_AMBIENTE,
        listOf(
            ProtocolTargetField.VPD_BAND,
            ProtocolTargetField.PH_RANGE,
            ProtocolTargetField.EC_RANGE,
            ProtocolTargetField.LIGHT_TEMP_CELSIUS,
            ProtocolTargetField.LIGHT_HUMIDITY_PERCENT,
            ProtocolTargetField.DARK_TEMP_CELSIUS,
            ProtocolTargetField.DARK_HUMIDITY_PERCENT
        )
    ),
    INTENSIDAD(
        ProtocolExtendedFields.GRUPO_INTENSIDAD,
        listOf(ProtocolTargetField.PPFD, ProtocolTargetField.DLI)
    ),
    INSTALACION(
        ProtocolExtendedFields.GRUPO_INSTALACION,
        listOf(
            ProtocolTargetField.LIGHT_TYPE,
            ProtocolTargetField.LAMP_POWER_WATTS,
            ProtocolTargetField.SUBSTRATE_TYPE,
            ProtocolTargetField.WATERING_STRATEGY,
            ProtocolTargetField.OBSERVATIONS
        )
    );

    companion object {
        /** Every declared target, in the order the card lists them. */
        val ALL_FIELDS: List<ProtocolTargetField> = entries.flatMap { it.fields }

        /** The group a card group title belongs to, or null when it belongs to none. */
        fun forTitle(titleEs: String): ProtocolTargetGroup? =
            entries.firstOrNull { it.titleEs == titleEs }
    }
}

/* ── Column access ──────────────────────────────────────────────────────── */

/**
 * The stored value of one field, as the plain type the column holds.
 *
 * Deliberately untyped at the boundary and typed at the two ends: [withBand],
 * [withMetric] and [withText] are exhaustive `when`s over the enum, so a fifteenth
 * column added to [ProtocolTargetField] without a mapping is a compile error rather than
 * a field that silently never persists.
 */
fun Protocol.valueOf(field: ProtocolTargetField): Any? = when (field) {
    ProtocolTargetField.VPD_BAND -> vpdBand
    ProtocolTargetField.PH_RANGE -> phRange
    ProtocolTargetField.EC_RANGE -> ecRange
    ProtocolTargetField.LIGHT_TEMP_CELSIUS -> lightTempCelsius
    ProtocolTargetField.LIGHT_HUMIDITY_PERCENT -> lightHumidityPercent
    ProtocolTargetField.DARK_TEMP_CELSIUS -> darkTempCelsius
    ProtocolTargetField.DARK_HUMIDITY_PERCENT -> darkHumidityPercent
    ProtocolTargetField.PPFD -> ppfd
    ProtocolTargetField.DLI -> dli
    ProtocolTargetField.LIGHT_TYPE -> lightType
    ProtocolTargetField.LAMP_POWER_WATTS -> lampPowerWatts
    ProtocolTargetField.SUBSTRATE_TYPE -> substrateType
    ProtocolTargetField.WATERING_STRATEGY -> wateringStrategy
    ProtocolTargetField.OBSERVATIONS -> observations
}

internal fun Protocol.withBand(field: ProtocolTargetField, range: GrowRange?): Protocol = when (field) {
    ProtocolTargetField.VPD_BAND -> copy(vpdBand = range)
    ProtocolTargetField.PH_RANGE -> copy(phRange = range)
    ProtocolTargetField.EC_RANGE -> copy(ecRange = range)
    else -> error("withBand was called on ${field.name}, which is not a band")
}

internal fun Protocol.withMetric(field: ProtocolTargetField, value: Float?): Protocol =
    when (field) {
        ProtocolTargetField.LIGHT_TEMP_CELSIUS -> copy(lightTempCelsius = value)
        ProtocolTargetField.LIGHT_HUMIDITY_PERCENT -> copy(lightHumidityPercent = value)
        ProtocolTargetField.DARK_TEMP_CELSIUS -> copy(darkTempCelsius = value)
        ProtocolTargetField.DARK_HUMIDITY_PERCENT -> copy(darkHumidityPercent = value)
        ProtocolTargetField.PPFD -> copy(ppfd = value)
        ProtocolTargetField.DLI -> copy(dli = value)
        ProtocolTargetField.LAMP_POWER_WATTS -> copy(lampPowerWatts = value)
        else -> error("withMetric was called on ${field.name}, which is not a metric")
    }

internal fun Protocol.withText(field: ProtocolTargetField, value: String?): Protocol =
    when (field) {
        ProtocolTargetField.LIGHT_TYPE -> copy(lightType = value)
        ProtocolTargetField.SUBSTRATE_TYPE -> copy(substrateType = value)
        ProtocolTargetField.WATERING_STRATEGY -> copy(wateringStrategy = value)
        ProtocolTargetField.OBSERVATIONS -> copy(observations = value)
        else -> error("withText was called on ${field.name}, which is not text")
    }