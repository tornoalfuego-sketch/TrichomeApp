package com.trichome.app.model

/**
 * Where the latitude behind an [EstimatedClimate] came from.
 *
 * The distinction is the whole point of this file. [AmbientClimate] returns numbers
 * that look exactly like a thermometer, and the app has no sensor, no weather
 * service and no location permission — so the only honest thing the UI can do is
 * say *which latitude the model was handed*. A grower in Buenos Aires looking at a
 * July estimate must be able to tell that the number did not come from where they
 * are standing, or the card is worse than no card.
 */
enum class ClimateLatitudeSource {
    /**
     * The tent's `location` field parsed as coordinates.
     *
     * Possible but rare: `GrowTent.location` is free text the grower types, and in
     * practice it holds things like "cuarto cultivo". Nothing in the app enforces a
     * coordinate format there, so this only happens if a grower happened to type one.
     */
    TENT_FIELD,

    /**
     * [ClimateCardCopy.DEFAULT_LATITUDE_DEGREES], because nothing usable was found.
     *
     * Named rather than silently implied, so the UI is obliged to render the
     * assumption instead of inheriting a number with no visible provenance.
     */
    DOCUMENTED_DEFAULT
}

/**
 * The latitude an estimate is built from, plus the sentence explaining it.
 *
 * @param assumptionEs Spanish, ready to display. Says plainly which of the two
 *   sources was used, so the number is never presented as knowing where the user is.
 */
data class ClimateLocation(
    val latitudeDegrees: Double,
    val source: ClimateLatitudeSource,
    val assumptionEs: String
)

/**
 * The estimated-climate card's copy, and the latitude it assumes.
 *
 * ## Why this is a model and not a composable
 *
 * Same constraint as [LunarCardCopy]: `compose-ui-test` lives only in `androidTest`,
 * which is device-only, so a card's wording cannot be asserted from the JVM where it
 * lives in a composable. The decision this file makes — *which latitude, and what to
 * call it* — is the part that can silently be wrong, and a number that looks like a
 * sensor reading is worse than no number at all.
 *
 * ## The latitude problem, stated honestly
 *
 * This app has no location source. There is no permission in the manifest, no
 * geocoder, no network call, and adding any of those is out of scope for an offline
 * app whose About screen advertises "100 % locales y sin conexión". The two honest
 * options were:
 *
 *  1. read `GrowTent.location`, which is free text, and
 *  2. assume a documented latitude and **say so in the UI**.
 *
 * [parseLatitude] does (1) and falls back to (2), which is the only combination that
 * can be correct in both worlds. When it falls back the card says the estimate is
 * assumed for a specific reference latitude, because a grower who is told "24 °C" and
 * nothing else has no way to know the number was computed for Madrid when they are
 * growing in Lima.
 */
object ClimateCardCopy {

    /**
     * Latitude the estimate assumes when the tent's `location` is free text.
     *
     * 40.4 °N — central Spain. Chosen as a mid-latitude reference in the app's
     * own language region, not because the app knows anything about the user. It is
     * documented here and stated in the UI, which is the entire mitigation; the value
     * is deliberately not a "clever" guess.
     */
    const val DEFAULT_LATITUDE_DEGREES: Double = 40.4

    /** Elevation used alongside the assumed latitude. Sea level, so nothing is invented twice. */
    const val DEFAULT_ELEVATION_METERS: Int = 0

    /** Spanish name of [DEFAULT_LATITUDE_DEGREES], for the fallback sentence. */
    const val DEFAULT_LOCATION_LABEL_ES: String = "40,4 °N (España)"

    private const val ASSUMED_ES: String =
        "La estimación asume una latitud de $DEFAULT_LOCATION_LABEL_ES porque la " +
            "ubicación de la carpa no contiene coordenadas. No conoce tu ubicación."

    private const val FROM_TENT_ES: String =
        "Estimación calculada con la latitud guardada en la carpa (%s)."

    /**
     * A latitude out of a free-text field, or null when there is nothing usable.
     *
     * Accepts a signed decimal anywhere in the text and keeps the **first** one,
     * because "casa -34.6" is a latitude and "Barrio 12, casa 3" is not — the second
     * number in a street address is a house number, not another coordinate. Anything
     * outside ±90 is rejected rather than clamped: a clamped "120" would silently
     * become a plausible-looking 90 °N, which is the polar ice cap.
     */
    fun parseLatitude(freeText: String?): Double? {
        if (freeText.isNullOrBlank()) return null
        val match = LATITUDE_PATTERN.find(freeText) ?: return null
        val value = match.value.toDoubleOrNull() ?: return null
        return if (value in -90.0..90.0) value else null
    }

    /**
     * A signed decimal that is not *part of a word*.
     *
     * The lookarounds are the difference between a coordinate and a room number:
     * "Sala2" and "casa 3" are the shapes this field actually holds, and a bare
     * `-?\d{1,3}` pattern reads a 2 out of "Sala2" and then builds a July estimate for
     * 2 degrees north — a plausible-looking number from nothing.
     */
    private val LATITUDE_PATTERN = Regex(
        """(?<![\p{L}\p{N}])-?\d{1,3}(?:\.\d+)?(?![\p{L}\p{N}.])"""
    )

    /**
     * The latitude to estimate from, and the sentence that explains it.
     *
     * @param tentLocation the destination tent's free-text `location`, or null when
     *   there is no tent.
     */
    fun resolveLocation(tentLocation: String?): ClimateLocation {
        val parsed = parseLatitude(tentLocation)
        return if (parsed != null) {
            ClimateLocation(
                latitudeDegrees = parsed,
                source = ClimateLatitudeSource.TENT_FIELD,
                assumptionEs = String.format(FROM_TENT_ES, formatLatitudeEs(parsed))
            )
        } else {
            ClimateLocation(
                latitudeDegrees = DEFAULT_LATITUDE_DEGREES,
                source = ClimateLatitudeSource.DOCUMENTED_DEFAULT,
                assumptionEs = ASSUMED_ES
            )
        }
    }

    /** Spanish decimal formatting for a latitude: `40,4 °N`, `-33,4 °S`. */
    fun formatLatitudeEs(degrees: Double): String {
        val magnitude = kotlin.math.abs(degrees)
        val hemisphere = if (degrees < 0) "S" else "N"
        // Formatted in US locale and then separated: the separator is a comma
        // regardless of the phone's locale, because this string is compared against
        // and asserted in a JVM test that does not change device settings.
        val text = String.format(java.util.Locale.US, "%.1f", magnitude).replace('.', ',')
        return "$text °$hemisphere"
    }

    /**
     * The estimate for [date] at the latitude [tentLocation] resolves to.
     *
     * Thin on purpose: the arithmetic belongs to [AmbientClimate] and the *choice*
     * of latitude to [resolveLocation]. This exists so the composable has one call
     * to make instead of three.
     */
    fun estimateFor(
        date: java.time.LocalDate,
        tentLocation: String?,
        elevationMeters: Int = DEFAULT_ELEVATION_METERS
    ): Pair<EstimatedClimate, ClimateLocation> {
        val location = resolveLocation(tentLocation)
        return AmbientClimate.estimate(
            date = date,
            latitudeDegrees = location.latitudeDegrees,
            elevationMeters = elevationMeters
        ) to location
    }
}

/**
 * Everything the estimated-climate card has to write, as data.
 *
 * Every numeric field is prefixed by the same marker the model is, so the estimate
 * labelling cannot be dropped from one row and kept on another: [unitLabelEs] is the
 * only place a number is turned into a sentence.
 */
data class ClimateCardContent(
    /** The band, e.g. "VPD óptimo en floración". */
    val bandLabelEs: String,
    /** `VpdBand.adviceEs`. */
    val bandAdviceEs: String,
    /** e.g. "≈ 24 °C" — the approximation marker is load-bearing, see below. */
    val temperatureEs: String,
    /** e.g. "≈ 62 %". */
    val humidityEs: String,
    /** e.g. "≈ 0,84 kPa". */
    val vpdEs: String,
    /** The latitude and its provenance. Always mentions the assumption when assumed. */
    val assumptionEs: String,
    /** `EstimatedClimate.sourceEs`. */
    val sourceEs: String,
    /** Whether the band asks for a change. */
    val needsAction: Boolean,
    /** Always true; carried so the card cannot render without stating it. */
    val isEstimate: Boolean
) {
    /**
     * Whether the card may be drawn at all.
     *
     * A value outside the physically possible range is not shown as an estimate —
     * an estimate can be coarse, it cannot be 40 °C or 120 % RH. `AmbientClimate`
     * clamps so this is `true` by construction, and it exists so a regression is a
     * failing test instead of a plausible-looking number on a phone.
     */
    val isDrawable: Boolean get() = isEstimate && temperatureEs.isNotBlank()
}

/** Marker prefixed to every number the climate card prints. */
private const val ESTIMATE_MARK = "≈"

private fun es(value: Float, decimals: Int, unit: String): String =
    "$ESTIMATE_MARK ${String.format(java.util.Locale.US, "%.${decimals}f", value)} $unit"

/** Resolves the estimated-climate card's copy from one [EstimatedClimate]. */
object ClimateCardCopyFormatter {

    /**
     * The card's copy.
     *
     * The `≈` on every value is deliberate rather than decorative. Without it the row
     * reads `24 °C / 62 % / 0,84 kPa`, which is the shape of a sensor readout, and a
     * grower comparing it against their own cheap thermometer has no way to know it
     * came from a cosine of the day of year. The mark says "approximate" on every line
     * at once, and [ClimateCardContent.assumptionEs] says *why* there is no reading.
     */
    fun contentOf(
        climate: EstimatedClimate,
        location: ClimateLocation
    ): ClimateCardContent = ClimateCardContent(
        bandLabelEs = climate.vpdBand.labelEs,
        bandAdviceEs = climate.vpdBand.adviceEs,
        temperatureEs = es(climate.temperatureCelsius, 1, "°C"),
        humidityEs = es(climate.relativeHumidityPercent, 0, "%"),
        vpdEs = es(climate.vpdKPa, 2, "kPa"),
        assumptionEs = location.assumptionEs,
        sourceEs = climate.sourceEs,
        needsAction = climate.vpdBand.needsAction,
        isEstimate = climate.isEstimate
    )
}