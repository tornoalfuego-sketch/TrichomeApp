package com.trichome.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A band cannot be half filled — that is the property this type exists for.
 *
 * The failure it rules out is specific: a grower types the lower bound of a pH
 * band and stops. With two float columns that state is representable, and every
 * consumer has to guess what the missing half meant. Here it is not
 * representable, so the question never arises.
 */
class GrowRangeTest {

    @Test
    fun aBandCarriesBothBounds() {
        val band = GrowRange(5.8f, 6.5f)
        assertEquals(5.8f, band.low, 0f)
        assertEquals(6.5f, band.high, 0f)
    }

    @Test
    fun aBandWhoseBoundsAreEqualIsASingleValue() {
        assertTrue(GrowRange(6.0f, 6.0f).isSingle)
        assertTrue(!GrowRange(6.0f, 6.5f).isSingle)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invertedBoundsAreRejectedAtConstruction() {
        GrowRange(6.5f, 6.0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aNaNBoundIsRejectedRatherThanRendered() {
        GrowRange(Float.NaN, 6.5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun anInfiniteBoundIsRejectedRatherThanRendered() {
        GrowRange(0.8f, Float.POSITIVE_INFINITY)
    }

    /* ── Encoding ──────────────────────────────────────────────────────────── */

    @Test
    fun anUnsetBandEncodesToNothingRatherThanToZero() {
        assertNull(GrowRange.encode(null))
        assertNull(GrowRange.decode(null))
    }

    @Test
    fun encodingRoundTripsExactly() {
        listOf(
            GrowRange(5.8f, 6.5f),
            GrowRange(6.0f, 6.0f),
            GrowRange(0.05f, 0.15f),
            GrowRange(1.2f, 1.6f),
            GrowRange(18f, 28f),
            GrowRange(0f, 0f)
        ).forEach { band ->
            assertEquals(band, GrowRange.decode(GrowRange.encode(band)))
        }
    }

    @Test
    fun theStoredFormIsTwoBoundsInOneColumn() {
        // One column is the point. Two REAL columns is exactly the shape that
        // lets a half-filled band exist, so this assertion is the shape contract.
        val stored = GrowRange.encode(GrowRange(5.8f, 6.5f))!!
        assertEquals("5.8:6.5", stored)
        assertEquals(2, stored.split(GrowRange.SEPARATOR).size)
    }

    @Test
    fun encodingIsIndependentOfTheDeviceLocale() {
        // `5.8` must never come back as `5,8`: the stored form is parsed by the
        // codec, and a locale-dependent decimal point would corrupt every band
        // on a device configured in a comma-decimal region.
        assertEquals("5.8:6.5", GrowRange.encode(GrowRange(5.8f, 6.5f)))
    }

    /* ── Decoding ──────────────────────────────────────────────────────────── */

    @Test
    fun aStoredBandDecodesToBothBounds() {
        val band = GrowRange.decode("5.8:6.5")!!
        assertEquals(5.8f, band.low, 0f)
        assertEquals(6.5f, band.high, 0f)
    }

    @Test
    fun aHalfWrittenBandDoesNotDecode() {
        // The states two loose columns would happily store. All of them read as
        // unset, so none of them can reach the screen as a number.
        assertNull(GrowRange.decode("6.0"))
        assertNull(GrowRange.decode("6.0:"))
        assertNull(GrowRange.decode(":6.5"))
        assertNull(GrowRange.decode("6.0:6.5:6.6"))
        assertNull(GrowRange.decode(""))
        assertNull(GrowRange.decode("   "))
    }

    @Test
    fun textThatIsNotABandDoesNotDecode() {
        // A row written by a future build, or damaged on disk. Decoding happens
        // inside a Room row mapper: throwing here would crash the list that was
        // only trying to render a protocol.
        assertNull(GrowRange.decode("sin definir"))
        assertNull(GrowRange.decode("abc:def"))
        // `toFloatOrNull` accepts these, and every comparison against them is
        // false — so they slip past an inverted-bounds check and used to reach
        // the constructor, which throws. Inside a Room row mapper that is a
        // crash on a list that was only trying to render a protocol.
        assertNull(GrowRange.decode("NaN:NaN"))
        assertNull(GrowRange.decode("Infinity:Infinity"))
        assertNull(GrowRange.decode("-Infinity:Infinity"))
    }

    @Test
    fun anInvertedStoredBandDoesNotDecode() {
        // The constructor would throw on this; decode reports instead.
        assertNull(GrowRange.decode("6.5:6.0"))
    }

    @Test
    fun surroundingWhitespaceIsTolerated() {
        val band = GrowRange.decode("  5.8 : 6.5  ")!!
        assertEquals(5.8f, band.low, 0f)
        assertEquals(6.5f, band.high, 0f)
    }

    @Test
    fun aCorruptColumnDegradesToUnsetRatherThanThrowing() {
        // The whole reason decode returns null: this is the value a row mapper
        // would be handed, and the UI's answer to it is "Sin definir".
        val stored: String? = "corrupted"
        val band = GrowRange.decode(stored)
        assertNull(band)
    }
}