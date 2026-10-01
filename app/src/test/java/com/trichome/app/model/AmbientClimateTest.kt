package com.trichome.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * VPD psychrometrics and the offline seasonal estimate.
 *
 * Two separate contracts live here:
 *
 *  - the **formula** (`es`, `ea`, `VPD`), which is textbook FAO-56 and must behave
 *    exactly like physics: zero at saturation, positive above freezing, strictly
 *    increasing in temperature;
 *  - the **estimate**, which is a crude climatic model and must therefore never
 *    leave a physically possible range. That second one is swept over the whole
 *    latitude x day-of-year domain instead of at a few sample points, because a
 *    clamp that only holds where someone happened to test it is not a clamp.
 *
 * Nothing here reads the clock or the network: the estimate takes a `LocalDate`
 * and a latitude, and that is the whole input surface.
 */
class AmbientClimateTest {

    private val delta = 0.0001

    /* ── VPD: zero at saturation ───────────────────────────────────────── */

    @Test
    fun vpdIsExactlyZeroAtFullHumidity() {
        listOf(-5.0, 0.0, 12.0, 20.0, 25.0, 30.0, 40.0).forEach { temperature ->
            assertEquals(
                "saturated air at $temperature °C cannot take any more water",
                0.0,
                AmbientClimate.vpdKPa(temperature, 100.0),
                delta
            )
        }
    }

    @Test
    fun vpdIsZeroAtZeroCelsiusWithSaturatedAirAndPositiveAboveIt() {
        assertEquals(
            "the 0 °C / 100 % RH corner is also a zero deficit",
            0.0,
            AmbientClimate.vpdKPa(0.0, 100.0),
            delta
        )
        assertTrue(
            "just above freezing with 99 % humidity there is a real, if tiny, deficit",
            AmbientClimate.vpdKPa(0.1, 99.0) > 0.0
        )
        assertTrue(
            "and at a normal indoor humidity, well above freezing, it is clearly positive",
            AmbientClimate.vpdKPa(20.0, 50.0) > 0.5
        )
    }

    @Test
    fun vpdRisesAsHumidityFalls() {
        val temperature = 24.0
        // Seeded from the saturated case, so the very first comparison has a real
        // previous value instead of a sentinel that every deficit beats at once.
        var previous = AmbientClimate.vpdKPa(temperature, 100.0)
        assertEquals(
            "the ladder has to start at a zero deficit",
            0.0,
            previous,
            delta
        )
        (95 downTo 10 step 5).forEach { humidity ->
            val deficit = AmbientClimate.vpdKPa(temperature, humidity.toDouble())
            assertTrue(
                "VPD must grow as humidity falls: $humidity % gave $deficit after $previous",
                deficit > previous
            )
            previous = deficit
        }
        assertTrue(
            "a dry room at 24 °C must be well outside every optimal band",
            AmbientClimate.vpdKPa(temperature, 15.0) > 2.4
        )
    }

    @Test
    fun aHumidityAboveOneHundredNeverProducesANegativeDeficit() {
        val reading = AmbientClimate.reading(25.0, 140.0)
        assertEquals(
            "a faulty 140 % sensor saturates, it does not push the deficit negative",
            0.0,
            reading.vpdKPa,
            delta
        )
        assertEquals(VpdBand.LOW, reading.band)
    }

    @Test
    fun theRealReadingForAnIndoorGrowRoomLandsInAnOptimalBand() {
        val reading = AmbientClimate.reading(25.0, 55.0)
        assertEquals(
            "es(25) = 3.168 kPa, so a 45 % deficit is 1.43 kPa",
            1.4257,
            reading.vpdKPa,
            0.001
        )
        assertEquals(VpdBand.OPTIMAL_FLOWERING, reading.band)
        assertTrue(
            "an optimal band must not tell the grower to act",
            !reading.needsAction
        )
    }

    /* ── Saturation curve ──────────────────────────────────────────────── */

    @Test
    fun theSaturationCurveIsStrictlyMonotonicInTemperature() {
        var previous = -1.0
        var temperature = -40.0
        while (temperature <= 60.0) {
            val saturation = AmbientClimate.saturationVapourPressureKPa(temperature)
            assertTrue(
                "saturation pressure must rise with temperature: $temperature °C gave $saturation after $previous",
                saturation > previous
            )
            previous = saturation
            temperature += 0.25
        }
    }

    @Test
    fun theSaturationCurveMatchesThePublishedValues() {
        assertEquals(0.611, AmbientClimate.saturationVapourPressureKPa(0.0), 0.001)
        assertEquals(1.228, AmbientClimate.saturationVapourPressureKPa(10.0), 0.001)
        assertEquals(2.338, AmbientClimate.saturationVapourPressureKPa(20.0), 0.001)
        assertEquals(3.168, AmbientClimate.saturationVapourPressureKPa(25.0), 0.001)
        assertEquals(4.243, AmbientClimate.saturationVapourPressureKPa(30.0), 0.001)
        assertEquals(7.376, AmbientClimate.saturationVapourPressureKPa(40.0), 0.001)
    }

    @Test
    fun theActualPressureIsTheSaturationTimesTheHumidity() {
        val temperature = 22.0
        val saturation = AmbientClimate.saturationVapourPressureKPa(temperature)
        listOf(0.0, 25.0, 50.0, 75.0, 100.0).forEach { humidity ->
            assertEquals(
                "ea must be a straight fraction of es at $humidity %",
                saturation * humidity / 100.0,
                AmbientClimate.actualVapourPressureKPa(temperature, humidity),
                delta
            )
        }
    }

    /* ── The bands ─────────────────────────────────────────────────────── */

    @Test
    fun theBandsAreContiguousAndNonOverlapping() {
        val ladder = VpdBand.entries.sortedBy { it.minKPa }

        assertEquals(
            "the ladder must open at zero, or a saturated room has no band",
            0.0,
            ladder.first().minKPa,
            delta
        )
        assertEquals(
            "the ladder must close at infinity, or a desert has no band",
            Double.POSITIVE_INFINITY,
            ladder.last().maxKPa,
            0.0
        )
        ladder.zipWithNext().forEach { (lower, higher) ->
            assertEquals(
                "${higher.name} must start exactly where ${lower.name} ends",
                lower.maxKPa,
                higher.minKPa,
                delta
            )
            assertTrue(
                "${higher.name} must be strictly above ${lower.name}",
                higher.minKPa > lower.minKPa
            )
            assertTrue(
                "${higher.name} must not overlap ${lower.name}",
                higher.minKPa >= lower.maxKPa
            )
        }
    }

    @Test
    fun everyDeficitValueBelongsToExactlyOneBand() {
        val ladder = VpdBand.entries.sortedBy { it.minKPa }
        var value = 0.0
        while (value <= 6.0) {
            val band = AmbientClimate.bandFor(value)
            assertTrue(
                "$value kPa fell outside every band",
                value >= band.minKPa && value < band.maxKPa
            )
            assertEquals(
                "$value kPa must resolve to the band whose range holds it",
                ladder.count { value >= it.minKPa && value < it.maxKPa },
                1
            )
            value += 0.001
        }
    }

    @Test
    fun theBandBoundariesLandOnTheEdgeTheyClaim() {
        assertEquals(VpdBand.LOW, AmbientClimate.bandFor(0.0))
        assertEquals(VpdBand.LOW, AmbientClimate.bandFor(0.499))
        assertEquals(VpdBand.OPTIMAL_VEGETATIVE, AmbientClimate.bandFor(0.5))
        assertEquals(VpdBand.OPTIMAL_FLOWERING, AmbientClimate.bandFor(1.0))
        assertEquals(VpdBand.HIGH, AmbientClimate.bandFor(1.6))
        assertEquals(VpdBand.EXTREME, AmbientClimate.bandFor(2.4))
        assertEquals(VpdBand.EXTREME, AmbientClimate.bandFor(99.0))
    }

    @Test
    fun everyBandCarriesItsOwnSpanishNameAndAdvice() {
        val advice = VpdBand.entries.map { it.adviceEs }
        assertEquals(
            "each band must explain itself, not share one sentence",
            VpdBand.entries.size,
            advice.toSet().size
        )
        VpdBand.entries.forEach { band ->
            assertTrue("${band.name} has no Spanish label", band.labelEs.isNotBlank())
            assertTrue("${band.name} has no advice", band.adviceEs.isNotBlank())
        }
        assertTrue(
            "the two optimal bands are the only ones that need no action",
            VpdBand.entries.filterNot { it.needsAction }
                .toSet() == setOf(VpdBand.OPTIMAL_VEGETATIVE, VpdBand.OPTIMAL_FLOWERING)
        )
    }

    /* ── The seasonal estimate stays plausible ─────────────────────────── */

    @Test
    fun theEstimateNeverLeavesAPhysicallyPossibleRange() {
        var checked = 0
        for (latitude in -90..90) {
            for (dayOfYear in 1..365) {
                val estimate = AmbientClimate.estimate(
                    date = dateOf(dayOfYear),
                    latitudeDegrees = latitude.toDouble(),
                    elevationMeters = 0
                )
                checked++
                assertTrue(
                    "latitude $latitude day $dayOfYear produced ${estimate.temperatureCelsius} °C",
                    estimate.temperatureCelsius <= 35f
                )
                assertTrue(
                    "latitude $latitude day $dayOfYear produced ${estimate.temperatureCelsius} °C",
                    estimate.temperatureCelsius >= -35f
                )
                assertTrue(
                    "humidity of ${estimate.relativeHumidityPercent} % is not physical",
                    estimate.relativeHumidityPercent in 5f..100f
                )
                assertTrue(
                    "a negative deficit is not physical",
                    estimate.vpdKPa >= 0f
                )
                assertTrue(
                    "the plausibility guard must hold, or the UI would draw it",
                    estimate.isPlausible
                )
            }
        }
        assertEquals("the sweep must actually cover the domain", 181 * 365, checked)
    }

    @Test
    fun anEstimateCanNeverClaimFortyDegreesOrImpossibleHumidity() {
        for (latitude in -90..90 step 3) {
            for (dayOfYear in intArrayOf(1, 80, 172, 202, 266, 355)) {
                val estimate = AmbientClimate.estimate(
                    date = dateOf(dayOfYear),
                    latitudeDegrees = latitude.toDouble(),
                    elevationMeters = 4_000
                )
                assertTrue(
                    "40 °C at latitude $latitude",
                    estimate.temperatureCelsius < 40f
                )
                assertTrue(
                    "120 % RH at latitude $latitude",
                    estimate.relativeHumidityPercent <= 100f
                )
            }
        }
    }

    /* ── The estimate behaves like a climate ───────────────────────────── */

    @Test
    fun theSeasonsRunInOppositeDirectionsInTheTwoHemispheres() {
        val july = 202
        val january = 20

        val madrid = 40.4
        assertTrue(
            "Madrid is warm in July",
            AmbientClimate.estimate(dateOf(july), madrid).temperatureCelsius >
                AmbientClimate.estimate(dateOf(january), madrid).temperatureCelsius
        )

        val buenosAires = -34.6
        assertTrue(
            "Buenos Aires is warm in January",
            AmbientClimate.estimate(dateOf(january), buenosAires).temperatureCelsius >
                AmbientClimate.estimate(dateOf(july), buenosAires).temperatureCelsius
        )
    }

    @Test
    fun theEquatorHasAlmostNoSeasonalSwing() {
        val summer = AmbientClimate.estimate(dateOf(202), 0.0).temperatureCelsius
        val winter = AmbientClimate.estimate(dateOf(20), 0.0).temperatureCelsius
        assertEquals(
            "the model has no annual amplitude at the equator",
            summer,
            winter,
            0.0001f
        )
    }

    @Test
    fun goingUpInAltitudeIsColder() {
        val seaLevel = AmbientClimate.estimate(dateOf(172), -34.6, elevationMeters = 0)
        val andes = AmbientClimate.estimate(dateOf(172), -34.6, elevationMeters = 3_000)

        assertEquals(
            "the lapse rate is 6.5 °C per 1000 m",
            19.5f,
            seaLevel.temperatureCelsius - andes.temperatureCelsius,
            0.0001f
        )
    }

    @Test
    fun aLatitudeOutsideTheGlobeIsClampedRatherThanExploding() {
        val beyondNorth = AmbientClimate.estimate(dateOf(172), 900.0)
        val atNorth = AmbientClimate.estimate(dateOf(172), 90.0)
        assertEquals(atNorth.temperatureCelsius, beyondNorth.temperatureCelsius, 0.0001f)

        val negativeElevation = AmbientClimate.estimate(dateOf(172), -34.6, elevationMeters = -5_000)
        val seaLevel = AmbientClimate.estimate(dateOf(172), -34.6, elevationMeters = 0)
        assertEquals(
            "a negative elevation cannot warm the room",
            seaLevel.temperatureCelsius,
            negativeElevation.temperatureCelsius,
            0.0001f
        )
        assertEquals(0, negativeElevation.elevationMeters)
    }

    /* ── It is an estimate, and the type has to say so ─────────────────── */

    @Test
    fun everyEstimateIsFlaggedAndCarriesItsDisclaimer() {
        listOf(0.0, -34.6, 40.4, 68.0).forEach { latitude ->
            listOf(1, 172, 355).forEach { dayOfYear ->
                val estimate = AmbientClimate.estimate(dateOf(dayOfYear), latitude)
                assertTrue(
                    "the estimate flag must always be set",
                    estimate.isEstimate
                )
                assertTrue(
                    "the UI must have something to label this with",
                    estimate.sourceEs.isNotBlank()
                )
                assertTrue(
                    "the note must deny being a measurement",
                    estimate.sourceEs.contains("no como medición", true)
                )
            }
        }
    }

    @Test
    fun theInstantOverloadResolvesTheLocalDayAndIsDeterministic() {
        val instant = LocalDate.of(2026, 7, 15)
            .atTime(2, 0)
            .atZone(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()

        val inUtc = AmbientClimate.estimate(instant, ZoneId.of("UTC"), -34.6)
        val inAuckland = AmbientClimate.estimate(instant, ZoneId.of("Pacific/Auckland"), -34.6)

        assertEquals(
            "02:00 UTC is 14:00 the same day in Auckland: July is winter here either way",
            LocalDate.of(2026, 7, 15),
            java.time.Instant.ofEpochMilli(instant)
                .atZone(ZoneId.of("Pacific/Auckland")).toLocalDate()
        )
        assertEquals(
            "the same zone must give the same estimate every time",
            inUtc.temperatureCelsius,
            AmbientClimate.estimate(instant, ZoneId.of("UTC"), -34.6).temperatureCelsius,
            0f
        )
        // A zone far enough ahead crosses midnight, and then the estimate differs —
        // because "the weather on the 15th" is a local question, unlike the moon.
        val nextDay = LocalDate.of(2026, 7, 15).atTime(20, 0)
            .atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
        val aucklandDay = AmbientClimate.estimate(nextDay, ZoneId.of("Pacific/Auckland"), -34.6)
        assertEquals(
            LocalDate.of(2026, 7, 16),
            java.time.Instant.ofEpochMilli(nextDay)
                .atZone(ZoneId.of("Pacific/Auckland")).toLocalDate()
        )
        assertNotEquals(
            "two different local days must produce two different seasonal estimates",
            inUtc.temperatureCelsius,
            aucklandDay.temperatureCelsius
        )
    }

    /** A leap-year date whose day-of-year is [dayOfYear], so summer is reachable. */
    private fun dateOf(dayOfYear: Int): LocalDate =
        LocalDate.ofYearDay(2024, dayOfYear.coerceIn(1, 366))
}