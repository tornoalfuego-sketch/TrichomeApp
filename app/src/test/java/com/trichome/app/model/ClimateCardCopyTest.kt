package com.trichome.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The estimated-climate card: which latitude it assumes, and how loudly it says so.
 *
 * ## Why this test exists at all
 *
 * `AmbientClimate` returns a temperature, a humidity and a VPD from a cosine of the day
 * of year. Printed bare, `24 °C / 62 % / 0,84 kPa` is indistinguishable from a
 * $20 thermometer, and nothing in the compiler, in lint or in a device screenshot
 * distinguishes them. So the two things that can silently go wrong are both here:
 *
 *  1. **the latitude is invented silently.** There is no location permission and no
 *     geocoder in this app, so the latitude comes either from the tent's free-text
 *     `location` or from a documented default — and the second case has to say so on
 *     the card or the app is claiming to know where the user is.
 *  2. **the estimate marker is dropped.** Every value carries `≈` so no call site can
 *     print a bare number.
 *
 * ## What this does NOT cover
 *
 * That the card renders, that the text is legible on all four palettes, and that the
 * numbers reach the screen at all. Compose is not unit-testable in this module.
 */
class ClimateCardCopyTest {

    private val winterDay = LocalDate.of(2026, 1, 15)
    private val summerDay = LocalDate.of(2026, 7, 15)

    /* ── Latitude resolution ──────────────────────────────────────── */

    @Test
    fun freeTextWithNoDigitsFallsBackToTheDocumentedDefault() {
        // This is the real shape of `GrowTent.location` today: "cuarto cultivo".
        val location = ClimateCardCopy.resolveLocation("cuarto cultivo")

        assertEquals(
            ClimateLatitudeSource.DOCUMENTED_DEFAULT,
            location.source
        )
        assertEquals(
            ClimateCardCopy.DEFAULT_LATITUDE_DEGREES,
            location.latitudeDegrees,
            0.0001
        )
    }

    @Test
    fun aBlankOrNullLocationFallsBackToo() {
        listOf(null, "", "   ").forEach { raw ->
            val location = ClimateCardCopy.resolveLocation(raw)
            assertEquals(
                "a null or blank location must fall back, not crash",
                ClimateLatitudeSource.DOCUMENTED_DEFAULT,
                location.source
            )
        }
    }

    @Test
    fun theFallbackSentenceSaysTheAppDoesNotKnowThePosition() {
        // The whole mitigation for having no location source. Without this sentence the
        // card is a number with invented provenance.
        val location = ClimateCardCopy.resolveLocation("cuarto cultivo")

        assertTrue(
            "the assumed-latitude sentence must name the assumption",
            location.assumptionEs.contains(ClimateCardCopy.DEFAULT_LOCATION_LABEL_ES)
        )
        assertTrue(
            "the assumed-latitude sentence must say the app does not know the position",
            location.assumptionEs.contains("No conoce tu ubicación")
        )
    }

    @Test
    fun coordinatesInTheLocationFieldAreUsedWhenPresent() {
        val location = ClimateCardCopy.resolveLocation("-33.4")

        assertEquals(ClimateLatitudeSource.TENT_FIELD, location.source)
        assertEquals(-33.4, location.latitudeDegrees, 0.0001)
        assertTrue(
            "the tent-field sentence must state the latitude it used",
            location.assumptionEs.contains("33,4") && location.assumptionEs.contains("S")
        )
    }

    @Test
    fun onlyTheFirstNumberIsTreatedAsTheLatitude() {
        // "Barrio 12, casa 3" — the second number is a house number, not another
        // coordinate, and taking the last one would invert the hemisphere.
        assertEquals(12.0, ClimateCardCopy.parseLatitude("Barrio 12, casa 3")!!, 0.0001)
    }

    @Test
    fun anImpossibleLatitudeIsRejectedRatherThanClamped() {
        // Clamping "120" would produce a plausible-looking 90 °N, which is the ice cap.
        assertNull(ClimateCardCopy.parseLatitude("120"))
        assertNull(ClimateCardCopy.parseLatitude("-120.5"))
        assertEquals(90.0, ClimateCardCopy.parseLatitude("90")!!, 0.0001)
    }

    @Test
    fun aNumberInsideALongerWordIsNotAParse() {
        // "Sala2" is a room, not 2 degrees.
        assertNull(ClimateCardCopy.parseLatitude("Sala2"))
    }

    @Test
    fun aSouthernAndANorthernLatitudeAreToldApartInSpanish() {
        assertTrue(ClimateCardCopy.formatLatitudeEs(40.4).endsWith("N"))
        assertTrue(ClimateCardCopy.formatLatitudeEs(-33.4).endsWith("S"))
        // Spanish decimal separator, not a dot.
        assertTrue(
            "Spanish formatting needs a comma: ${ClimateCardCopy.formatLatitudeEs(40.4)}",
            ClimateCardCopy.formatLatitudeEs(40.4).contains("40,4")
        )
    }

    /* ── Estimate labelling ────────────────────────────────────────── */

    @Test
    fun everyNumberCarriesTheApproximationMarker() {
        val (climate, location) = ClimateCardCopy.estimateFor(
            date = summerDay,
            tentLocation = "cuarto cultivo"
        )
        val content = ClimateCardCopyFormatter.contentOf(climate, location)

        listOf(content.temperatureEs, content.humidityEs, content.vpdEs).forEach { line ->
            assertTrue(
                "\"$line\" would read as a sensor reading without the estimate marker",
                line.startsWith("≈")
            )
        }
        assertTrue("the climate is not labelled as an estimate", content.isEstimate)
        assertTrue("an estimate must be drawable", content.isDrawable)
    }

    @Test
    fun theEstimateNoteSaysThereIsNoSensorAndNoConnection() {
        val (climate, location) = ClimateCardCopy.estimateFor(summerDay, null)
        val content = ClimateCardCopyFormatter.contentOf(climate, location)

        assertEquals(EstimatedClimate.SOURCE_NOTE_ES, content.sourceEs)
        assertTrue(
            "the note must disclaim the sensor: ${content.sourceEs}",
            content.sourceEs.contains("Sin sensores")
        )
    }

    @Test
    fun theCardStatesTheLatitudeItAssumedAndTheBandItLandedIn() {
        val (climate, location) = ClimateCardCopy.estimateFor(
            date = summerDay,
            tentLocation = "cuarto cultivo"
        )
        val content = ClimateCardCopyFormatter.contentOf(climate, location)

        assertEquals(location.assumptionEs, content.assumptionEs)
        assertTrue(
            "the assumption must reach the card, not just the resolver",
            content.assumptionEs.contains(ClimateCardCopy.DEFAULT_LOCATION_LABEL_ES)
        )
        assertEquals(climate.vpdBand.labelEs, content.bandLabelEs)
        assertEquals(climate.vpdBand.adviceEs, content.bandAdviceEs)
        assertEquals(climate.vpdBand.needsAction, content.needsAction)
    }

    @Test
    fun theBandsLabelAndAdviceAreCarriedForEveryBand() {
        // A band added without a sentence would render as a bare word.
        VpdBand.entries.forEach { band ->
            assertTrue("${band.name} has no Spanish label", band.labelEs.isNotBlank())
            assertTrue("${band.name} has no advice", band.adviceEs.isNotBlank())
        }
    }

    @Test
    fun theDefaultLatitudeChangesTheEstimateAndTheCardSaysSo() {
        val (assumed, assumedLocation) = ClimateCardCopy.estimateFor(summerDay, "cuarto cultivo")
        val (fromField, fieldLocation) = ClimateCardCopy.estimateFor(summerDay, "60.2")

        assertEquals(ClimateLatitudeSource.DOCUMENTED_DEFAULT, assumedLocation.source)
        assertEquals(ClimateLatitudeSource.TENT_FIELD, fieldLocation.source)
        // A ~20 degree difference in latitude is a real difference in the seasonal
        // model; if it were identical the latitude would not be reaching the engine.
        assertTrue(
            "the latitude must actually reach the estimate",
            kotlin.math.abs(assumed.temperatureCelsius - fromField.temperatureCelsius) > 0.5f
        )
        assertTrue(
            "the tent-field sentence must NOT claim the app guessed",
            fieldLocation.assumptionEs.contains(ClimateCardCopy.DEFAULT_LOCATION_LABEL_ES).not()
        )
    }

    @Test
    fun theSeasonalEstimateIsCoarserThanItsOwnDisclaimerAllows() {
        // A sanity bound, not a physics test: `AmbientClimateTest` owns the model.
        // This only asserts the card never receives a value it cannot show.
        val (climate, _) = ClimateCardCopy.estimateFor(summerDay, "cuarto cultivo")
        assertTrue(climate.isPlausible)
        assertTrue(
            "an estimated temperature must never be presentable as a reading outside " +
                "the plausible range",
            climate.temperatureCelsius in -35f..35f
        )
    }

    @Test
    fun anImplausibleEstimateIsStillCarriedAsAnEstimate() {
        // The flag is a promise the type makes, so the card must render it whatever
        // the numbers are; refusing to show a value is `isDrawable`, not `isEstimate`.
        val fabricated = EstimatedClimate(
            temperatureCelsius = 40f,
            relativeHumidityPercent = 120f,
            vpdKPa = -1f,
            vpdBand = VpdBand.EXTREME
        )
        val content = ClimateCardCopyFormatter.contentOf(
            climate = fabricated,
            location = ClimateCardCopy.resolveLocation("cuarto cultivo")
        )
        assertTrue(content.isEstimate)
        assertTrue(
            "even a bad value must keep the approximation marker",
            content.temperatureEs.startsWith("≈")
        )
    }

    @Test
    fun theTwoHemispeheresPeakOnOppositeSidesOfTheYear() {
        // Not the engine's test either. This is here because the card's *numbers*
        // depend on it and a grower in the southern hemisphere would otherwise read a
        // January estimate as a midsummer one.
        val (north, _) = ClimateCardCopy.estimateFor(LocalDate.of(2026, 7, 15), "40.4")
        val (south, _) = ClimateCardCopy.estimateFor(LocalDate.of(2026, 7, 15), "-33.4")

        assertTrue(
            "July must be warmer at 40.4 N than at 33.4 S",
            north.temperatureCelsius > south.temperatureCelsius
        )
    }

    @Test
    fun theElevationUsedAlongsideTheAssumedLatitudeIsSeaLevel() {
        // Nothing invents two things at once: the fallback latitude comes with an
        // explicit sea level, because an invented elevation would be a second hidden
        // assumption on top of the first.
        assertEquals(0, ClimateCardCopy.DEFAULT_ELEVATION_METERS)
        val (climate, _) = ClimateCardCopy.estimateFor(summerDay, "cuarto cultivo")
        assertEquals(0, climate.elevationMeters)
        assertFalse(
            "no elevation may be smuggled in through the default",
            climate.elevationMeters != 0
        )
    }
}