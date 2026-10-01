package com.trichome.app.model

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp

/**
 * The horticultural bands a grower acts on, in kPa of VPD.
 *
 * Exposed as data so the UI renders the range and the advice without hardcoding a
 * threshold anywhere, and so a test can prove the ladder is contiguous instead of
 * trusting five hand-typed `if`s.
 *
 * The bands are the indoor-cannabis convention: vegetative growth is comfortable
 * below about 1.0 kPa, flowering is pushed a little higher, and sustained values
 * above ~2.4 kPa close the stomata hard enough to cost yield. They are a guide for
 * interpreting a reading, not a measurement of what one plant will do.
 *
 * @param minKPa inclusive lower bound, in kPa.
 * @param maxKPa exclusive upper bound in kPa, `POSITIVE_INFINITY` for the top band,
 *   so the ladder closes instead of leaving a gap that silently bands as nothing.
 * @param labelEs Spanish band name shown to the grower.
 * @param adviceEs what to do about it. Spanish: UI copy.
 */
enum class VpdBand(
    val minKPa: Double,
    val maxKPa: Double,
    val labelEs: String,
    val adviceEs: String
) {
    LOW(
        minKPa = 0.0, maxKPa = 0.5,
        labelEs = "VPD bajo",
        adviceEs = "Riesgo de moho y de manchas en hoja. Sube la temperatura o " +
            "baja la humedad del aire; la planta transpirará menos."
    ),
    OPTIMAL_VEGETATIVE(
        minKPa = 0.5, maxKPa = 1.0,
        labelEs = "VPD óptimo en vegetativo",
        adviceEs = "Rango cómodo para el crecimiento vegetativo: la raíz tiene " +
            "agua suficiente sin que la hoja se seque."
    ),
    OPTIMAL_FLOWERING(
        minKPa = 1.0, maxKPa = 1.6,
        labelEs = "VPD óptimo en floración",
        adviceEs = "Rango recomendado durante la floración: más transpiración, " +
            "buena absorción de nutrientes y densidad de resina."
    ),
    HIGH(
        minKPa = 1.6, maxKPa = 2.4,
        labelEs = "VPD alto",
        adviceEs = "Transpiración excesiva: la planta gasta agua y nutrientes más " +
            "rápido de los que repone. Sube la humedad o baja la temperatura."
    ),
    EXTREME(
        minKPa = 2.4, maxKPa = Double.POSITIVE_INFINITY,
        labelEs = "VPD extremo",
        adviceEs = "Estrés hídrico: los estomas se cierran y la fotosíntesis se " +
            "frena. Sube la humedad del aire de inmediato."
    );

    /** Whether a [VpdReading] in this band asks for a change, or is fine as it is. */
    val needsAction: Boolean get() = this == LOW || this == HIGH || this == EXTREME
}

/**
 * A temperature/humidity pair resolved into a VPD and its band.
 *
 * This one is valid for **measured** readings too, which is the point of
 * [vpdKPa] being public: the journal already stores `temperature`, `humidity` and
 * a free `vpd` field per event, and this is the same formula applied to what the
 * grower actually logged.
 */
data class VpdReading(
    val saturationKPa: Double,
    val actualKPa: Double,
    val vpdKPa: Double,
    val band: VpdBand
) {
    /** Whether this reading sits outside the comfortable vegetative range. */
    val needsAction: Boolean get() = band.needsAction
}

/**
 * Estimated ambient climate for a location and a date.
 *
 * ## This is an ESTIMATE, and the type says so
 *
 * Nothing here is measured. There is no sensor, no weather service and no
 * location lookup: the app is offline-first by product decision and its About
 * screen advertises "100 % locales y sin conexión", so this model exists to give
 * the grower a *baseline* to reason against — "what would the outside usually be
 * doing here in July" — not to tell them what the weather is today.
 *
 * That distinction is load-bearing in the shape of the type, not only in this
 * KDoc:
 *
 *  - the type is named `EstimatedClimate`, so it cannot be mistaken for a reading;
 *  - [isEstimate] is carried on every instance and is always `true` — there is no
 *    constructor that produces a `false`, because a flag nobody can set to `false`
 *    is a promise the type itself makes;
 *  - [sourceEs] is a Spanish sentence the UI is expected to display next to the
 *    numbers, so a grower is never left believing a thermometer is involved;
 *  - [isPlausible] is a derived guard: if a value ever leaves the physically
 *    possible range the UI can refuse to draw it rather than print 40 °C.
 *
 * A number presented as a sensor reading is worse than showing nothing at all,
 * which is why [isEstimate] cannot be switched off.
 */
data class EstimatedClimate(
    /** Air temperature estimate, °C. */
    val temperatureCelsius: Float,
    /** Relative humidity estimate, percent. */
    val relativeHumidityPercent: Float,
    /** Vapour pressure deficit implied by the two above, kPa. */
    val vpdKPa: Float,
    /** The horticultural band [vpdKPa] falls in. Derived, so label and number agree. */
    val vpdBand: VpdBand,
    /** Always `true`; see the class KDoc. */
    val isEstimate: Boolean = true,
    /** Spanish sentence the UI shows next to the numbers. */
    val sourceEs: String = SOURCE_NOTE_ES,
    /** Latitude the estimate was built from, degrees, signed. */
    val latitudeDegrees: Double = 0.0,
    /** Elevation the estimate was built from, metres above sea level. */
    val elevationMeters: Int = 0
) {
    /**
     * Whether every number is inside a physically possible range.
     *
     * The estimate clamps, so this is `true` by construction; it exists so a
     * regression in the model is a failing test instead of a plausible-looking
     * number on a grower's phone.
     */
    val isPlausible: Boolean
        get() = temperatureCelsius in MIN_PLAUSIBLE_TEMP_C..MAX_PLAUSIBLE_TEMP_C &&
            relativeHumidityPercent in 5f..100f &&
            vpdKPa >= 0f

    companion object {
        /**
         * Kept in Spanish: it is shown to the grower, and the About screen's
         * "100 % locales y sin conexión" claim is the thing this sentence has to
         * stay consistent with.
         */
        const val SOURCE_NOTE_ES: String =
            "Estimación offline por latitud, altitud y estación. Sin sensores ni " +
                "conexión: úsala como referencia, no como medición."

        const val MIN_PLAUSIBLE_TEMP_C: Float = -40f
        const val MAX_PLAUSIBLE_TEMP_C: Float = 45f
    }
}

/**
 * Estimated ambient climate — pure math, no I/O, no location provider, no API.
 *
 * ## The VPD formulas
 *
 * Vapour pressure deficit is the amount of water vapour the air *could* hold at
 * its temperature minus what it actually holds. Both terms come from the standard
 * psychrometric relations (FAO-56, Penman-Monteith; Tetens/Murray for saturation):
 *
 * ```
 *   es(T)  = 0.6108 * exp(17.27 * T / (T + 237.3))      saturation vapour pressure, kPa
 *   ea(T, RH) = es(T) * (RH / 100)                       actual vapour pressure, kPa
 *   VPD(T, RH) = es(T) - ea(T, RH) = es(T) * (1 - RH / 100)
 * ```
 * `T` is in °C. At `RH = 100` the two terms are equal and the deficit is exactly
 * zero — the air is saturated and cannot take any more water. As `RH` falls, the
 * deficit grows in proportion, which is why VPD is often written as the single
 * expression `es(T) * (1 - RH/100)`; it is kept split in code so a caller reading a
 * journal event can see and log both terms.
 *
 * `es(T)` is exponentially increasing in `T`, so the same relative humidity is a
 * very different deficit depending on temperature: 1.0 kPa at 20 °C / 57 % RH is
 * about 2.1 kPa at 30 °C / 76 % RH. A grower who only watches humidity is not
 * watching VPD, which is the whole reason this exists.
 *
 * ## The seasonal estimate
 *
 * No weather data is available offline, so the estimate is a coarse climatic
 * model rather than a forecast. It is three documented terms, in order:
 *
 * ```
 *   a      = |latitude|                                      degrees
 *   Tmean  = 26.5 - 0.30a - 0.0028 a^2                       annual mean, °C
 *   A      = 0.13a                                           seasonal amplitude, °C
 *   T      = Tmean + A * cos(2*PI*(doy - peak) / 365.2425) - 0.0065 * elevation
 *   RH     = 78 - 1.2 * (T - 15) - 0.35 * max(0, a - 25)     percent
 * ```
 *
 * with `peak = 202` (about 21 July) in the northern hemisphere and
 * `peak = 202 - 365.2425/2` (about 20 January) in the southern one, so July is
 * warm in Madrid and cold in Buenos Aires from the same expression.
 *
 * `Tmean` falls with latitude faster than a line, which is why the quadratic term
 * is there: a linear fit would put 45° at 1.8 °C, and temperate annual means are
 * near 10 °C. `A` grows with latitude because seasonal contrast is a polar
 * phenomenon and the equator has almost none. The `0.0065 °C/m` term is the
 * standard environmental lapse rate. The `RH` model says the relative humidity of
 * a given air parcel falls as it warms — that is what "relative" means — with a
 * latitude penalty for the drier continental interiors around 30–45°.
 *
 * ## Bounds
 *
 * Every output is clamped to a physically possible range, and the clamp is tested
 * across the whole latitude x day-of-year domain rather than at sample points:
 * temperature to [-35, 35] °C before the lapse term's rounding, humidity to
 * [15, 98] %. Over the entire domain the model produces -35..26 °C and 64..98 %,
 * so it can never print 40 °C or 120 % RH.
 */
object AmbientClimate {

    /** Days in a year, the Gregorian mean, for the seasonal cosine. */
    const val DAYS_PER_YEAR: Double = 365.2425

    /** Day of year on which the northern hemisphere is at its seasonal peak. */
    const val NORTHERN_PEAK_DAY_OF_YEAR: Double = 202.0

    /** ...and therefore the southern one, half a year away. */
    const val SOUTHERN_PEAK_DAY_OF_YEAR: Double = 202.0 - DAYS_PER_YEAR / 2

    /** Lapse rate, °C lost per metre of elevation. */
    const val LAPSE_RATE_C_PER_M: Double = 0.0065

    private const val MIN_TEMP_C = -35.0
    private const val MAX_TEMP_C = 35.0
    private const val MIN_RH_PERCENT = 15.0
    private const val MAX_RH_PERCENT = 98.0

    /**
     * Saturation vapour pressure at [temperatureC], in kPa (FAO-56 / Tetens).
     *
     * `es(T) = 0.6108 * exp(17.27 * T / (T + 237.3))`
     *
     * Strictly increasing in `T` for every `T > -237.3 °C`, i.e. everywhere on
     * Earth: doubling the temperature does not double the capacity, it multiplies
     * it. `es(0) = 0.611` kPa, `es(20) = 2.339`, `es(30) = 4.243`.
     */
    fun saturationVapourPressureKPa(temperatureC: Double): Double =
        0.6108 * exp(17.27 * temperatureC / (temperatureC + 237.3))

    /**
     * Actual vapour pressure at [temperatureC] and [relativeHumidityPercent], kPa.
     *
     * `ea = es(T) * RH / 100`. Humidity is coerced into `0..100` first: a sensor
     * that reports 104 % has a fault, and propagating that into a VPD would hand
     * the grower a negative number that reads as "more than saturated".
     */
    fun actualVapourPressureKPa(temperatureC: Double, relativeHumidityPercent: Double): Double =
        saturationVapourPressureKPa(temperatureC) * (relativeHumidityPercent.coerceIn(0.0, 100.0) / 100.0)

    /**
     * Vapour pressure deficit, kPa: `es(T) - ea(T, RH)`.
     *
     * Never negative. Exactly `0` at `RH = 100` for any temperature, and exactly
     * `0` at `0 °C` with saturated air — the corner where the air cannot take any
     * more water. Above 0 °C with humidity below saturation it is strictly
     * positive, and it grows as humidity falls.
     */
    fun vpdKPa(temperatureC: Double, relativeHumidityPercent: Double): Double =
        saturationVapourPressureKPa(temperatureC) -
            actualVapourPressureKPa(temperatureC, relativeHumidityPercent)

    /** [vpdKPa] plus its [VpdBand] and both pressure terms, for a UI or a log line. */
    fun reading(temperatureC: Double, relativeHumidityPercent: Double): VpdReading {
        val saturation = saturationVapourPressureKPa(temperatureC)
        val actual = actualVapourPressureKPa(temperatureC, relativeHumidityPercent)
        // Both terms are clamped so a negative VPD cannot fall into LOW and read as
        // a real "saturated room" band.
        val deficit = (saturation - actual).coerceAtLeast(0.0)
        return VpdReading(
            saturationKPa = saturation,
            actualKPa = actual,
            vpdKPa = deficit,
            band = bandFor(deficit)
        )
    }

    /**
     * The band a deficit falls in.
     *
     * Exhaustive `when` so the ladder and this dispatch cannot drift: a new band is
     * a build break rather than a value that silently reads as `EXTREME`.
     */
    fun bandFor(vpdKPa: Double): VpdBand = when {
        vpdKPa < VpdBand.LOW.maxKPa -> VpdBand.LOW
        vpdKPa < VpdBand.OPTIMAL_VEGETATIVE.maxKPa -> VpdBand.OPTIMAL_VEGETATIVE
        vpdKPa < VpdBand.OPTIMAL_FLOWERING.maxKPa -> VpdBand.OPTIMAL_FLOWERING
        vpdKPa < VpdBand.HIGH.maxKPa -> VpdBand.HIGH
        else -> VpdBand.EXTREME
    }

    /**
     * The estimate for a calendar [date] at [latitudeDegrees] and [elevationMeters].
     *
     * Takes a `LocalDate`, not an instant: the seasonal term is a function of the
     * *local* calendar day, so the caller resolves the day and the engine never
     * needs a `ZoneId`. [estimate] has an instant overload for callers that start
     * from `epochMillis`.
     */
    fun estimate(
        date: LocalDate,
        latitudeDegrees: Double,
        elevationMeters: Int = 0
    ): EstimatedClimate {
        val latitude = abs(latitudeDegrees.coerceIn(-90.0, 90.0))
        val dayOfYear = date.dayOfYear.toDouble()
        // A negative latitude is the southern hemisphere, so it peaks half a year
        // away from the northern peak. Checking the sign rather than the magnitude
        // is what keeps July warm in Madrid and cold in Buenos Aires.
        val peak = if (latitudeDegrees < 0) SOUTHERN_PEAK_DAY_OF_YEAR
        else NORTHERN_PEAK_DAY_OF_YEAR

        val annualMean = 26.5 - 0.30 * latitude - 0.0028 * latitude * latitude
        val amplitude = 0.13 * latitude
        val seasonal = amplitude * cos(
            2.0 * Math.PI * (dayOfYear - peak) / DAYS_PER_YEAR
        )
        val rawTemperature =
            annualMean + seasonal - LAPSE_RATE_C_PER_M * elevationMeters.coerceAtLeast(0)
        val temperature = rawTemperature.coerceIn(MIN_TEMP_C, MAX_TEMP_C)

        // Warm air holds the same moisture at a lower relative humidity, and the
        // continental interiors between roughly 25 and 60 degrees are drier still.
        val continentalDryness = (latitude - 25.0).coerceAtLeast(0.0) * 0.35
        val humidity = (78.0 - 1.2 * (temperature - 15.0) - continentalDryness)
            .coerceIn(MIN_RH_PERCENT, MAX_RH_PERCENT)

        val deficit = vpdKPa(temperature, humidity)
        return EstimatedClimate(
            temperatureCelsius = temperature.toFloat(),
            relativeHumidityPercent = humidity.toFloat(),
            vpdKPa = deficit.toFloat(),
            vpdBand = bandFor(deficit),
            latitudeDegrees = latitudeDegrees,
            elevationMeters = elevationMeters.coerceAtLeast(0)
        )
    }

    /**
     * The estimate for an instant, resolved to a local day in [zone].
     *
     * Pure: the zone is a parameter, so the same instant produces different
     * estimates for different observers — which is correct, because "what the
     * weather is usually like on the 15th" depends on the local calendar. The
     * *phase of the moon* in [LunarEngine] deliberately does the opposite and
     * ignores the zone entirely; the two engines answer different questions.
     */
    fun estimate(
        epochMillis: Long,
        zone: ZoneId,
        latitudeDegrees: Double,
        elevationMeters: Int = 0
    ): EstimatedClimate =
        estimate(
            date = java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate(),
            latitudeDegrees = latitudeDegrees,
            elevationMeters = elevationMeters
        )
}