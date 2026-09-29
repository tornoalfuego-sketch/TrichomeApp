package com.trichome.app.domain.vision

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pixel classifier that every photographic diagnosis depends on.
 *
 * The analyser runs on a plain ARGB array, so these tests need no Android
 * runtime and no device.
 */
class PhotoAnalyzerTest {

    private val width = 40
    private val height = 40

    private fun frame(color: Int): IntArray =
        IntArray(width * height) { color }

    private fun features(color: Int) =
        PhotoAnalyzer.analyze(frame(color), width, height)

    @Test
    fun healthyGreenLeafReadsAsGreenAndNotChlorotic() {
        val f = features(0xFF3E8E41.toInt())

        assertTrue(
            "greenRatio should dominate a healthy leaf, was ${f.greenRatio}",
            f.greenRatio > 0.7f
        )
        assertTrue(
            "a healthy leaf must not read as chlorotic, was ${f.chlorosisRatio}",
            f.chlorosisRatio < 0.1f
        )
        assertTrue(
            "a healthy leaf must not read as necrotic, was ${f.necrosisRatio}",
            f.necrosisRatio < 0.05f
        )
        assertTrue("a full green frame is usable", f.isUsable)
    }

    @Test
    fun yellowLeafRaisesChlorosisAboveTheHealthyCase() {
        val healthy = features(0xFF3E8E41.toInt())
        val yellow = features(0xFFD9C64A.toInt())

        assertTrue(
            "chlorosis ${yellow.chlorosisRatio} should exceed healthy ${healthy.chlorosisRatio}",
            yellow.chlorosisRatio > healthy.chlorosisRatio
        )
        assertTrue(
            "yellow tissue is not healthy green, was ${yellow.greenHealth}",
            yellow.greenHealth < healthy.greenHealth
        )
    }

    @Test
    fun brownLeafReadsAsNecrosisWithLowGreenHealth() {
        val f = features(0xFF7A4A24.toInt())

        assertTrue("brown tissue is necrotic, was ${f.necrosisRatio}", f.necrosisRatio > 0.5f)
        assertTrue("brown tissue is not healthy, was ${f.greenHealth}", f.greenHealth < 0.3f)
    }

    @Test
    fun bareFrameIsRejectedSoAWallIsNeverDiagnosed() {
        // A near-black frame carries almost no plant tissue.
        val f = features(0xFF141414.toInt())

        assertFalse("a frame with no plant must not be usable", f.isUsable)
    }

    @Test
    fun greenHealthStaysNormalisedForAnyFrame() {
        val blank = features(0xFF000000.toInt())
        val green = features(0xFF2F7D32.toInt())
        val mixed = IntArray(width * height) { i ->
            if (i % 2 == 0) 0xFF3E8E41.toInt() else 0xFF7A4A24.toInt()
        }
        val half = PhotoAnalyzer.analyze(mixed, width, height)

        listOf(blank.greenHealth, green.greenHealth, half.greenHealth).forEach {
            assertTrue("greenHealth must stay in 0..1, was $it", it in 0f..1f)
        }
    }

    @Test
    fun describeReportsEveryMeasuredFeature() {
        val rows = PhotoAnalyzer.describe(features(0xFF3E8E41.toInt()))

        assertTrue("describe should not be empty", rows.isNotEmpty())
        assertTrue("every row needs a label and a value", rows.all { it.first.isNotBlank() && it.second.isNotBlank() })
    }
}
