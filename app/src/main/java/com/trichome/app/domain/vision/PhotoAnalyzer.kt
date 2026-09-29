package com.trichome.app.domain.vision

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic, offline image feature extraction for plant diagnosis.
 *
 * The engine does not use a neural network: no TFLite model file ships with the
 * app, and faking one would be dishonest. Instead the analyser measures
 * objective, reproducible quantities from the pixels (HSV class ratios, lesion
 * density, filament proxy) and the knowledge base in `diagnostics.json` scores
 * those quantities against each condition. Every number below is derived from
 * the image, never from a random or hard-coded constant.
 *
 * The class is pure Kotlin on purpose: it takes a pixel buffer rather than a
 * `Bitmap`, which makes the whole pipeline unit-testable on the JVM.
 */
object PhotoAnalyzer {

    /** Upper bound on sampled pixels; keeps a 12 MP capture from stalling the UI. */
    const val MAX_SAMPLES = 40_000

    /**
     * Observable measurements extracted from a plant photo.
     *
     * Every `*Ratio` is expressed over the total sampled pixel count, so the
     * values are comparable across different capture sizes.
     */
    data class Features(
        /** Fraction of pixels that look like plant tissue (green + yellow + brown). */
        val leafCoverage: Float,
        /** Healthy green tissue. */
        val greenRatio: Float,
        /** Yellowing tissue (chlorosis). */
        val chlorosisRatio: Float,
        /** Brown / dead tissue (necrosis). */
        val necrosisRatio: Float,
        /** Bright, low-saturation specks: resin, powdery mildew, dust. */
        val trichomeRatio: Float,
        /** Near-black pixels: shadow, wet soil, dead tissue. */
        val darkRatio: Float,
        /**
         * Isolated necrotic specks surrounded by healthy tissue. This is what
         * separates leaf-spot disease from a uniformly scorched leaf.
         */
        val spotDensity: Float,
        /**
         * Thin bright filaments: spider-mite webbing. Measured as bright specks
         * whose surroundings are background, i.e. structure with no mass.
         */
        val webbingRatio: Float,
        val averageHue: Float,
        val averageSaturation: Float,
        val averageValue: Float,
        /** How many pixels were actually inspected. */
        val sampledPixels: Int
    ) {
        /**
         * Overall greenness of the visible tissue, 0..1.
         *
         * Dividing by coverage makes the index meaningful for a tightly framed
         * leaf and for a wide garden shot alike.
         */
        val greenHealth: Float
            get() = if (leafCoverage <= 0.01f) 0f else (greenRatio / leafCoverage).coerceIn(0f, 1f)

        /** True when the frame contains too little plant to draw conclusions. */
        val isUsable: Boolean get() = leafCoverage >= 0.08f && sampledPixels >= 500
    }

    /**
     * Extracts [Features] from a raw ARGB pixel buffer laid out row-major.
     *
     * The buffer is sub-sampled with a stride so that at most [MAX_SAMPLES]
     * pixels are visited, which bounds the cost to a few milliseconds on any
     * device.
     */
    fun analyze(pixels: IntArray, width: Int, height: Int): Features {
        if (width <= 0 || height <= 0 || pixels.isEmpty()) return EMPTY

        val total = width * height
        val stride = max(1, total / MAX_SAMPLES)

        var sampled = 0
        var green = 0
        var chlorotic = 0
        var necrotic = 0
        var bright = 0
        var dark = 0
        var leaf = 0
        var spots = 0
        var filaments = 0
        var hueSum = 0.0
        var satSum = 0.0
        var valSum = 0.0

        // Stride-aligned neighbours are used for the lesion/filament measures.
        val cols = (width + stride - 1) / stride
        val rows = (height + stride - 1) / stride
        val grid = IntArray(cols * rows) { CLASS_BACKGROUND }
        val gridHue = FloatArray(cols * rows)

        for (y in 0 until height step stride) {
            for (x in 0 until width step stride) {
                val index = y * width + x
                if (index >= pixels.size) break
                val argb = pixels[index]
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF

                val hsv = rgbToHsv(r, g, b)
                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]

                val klass = classify(hue, sat, value)

                val gi = (y / stride) * cols + (x / stride)
                if (gi in grid.indices) {
                    grid[gi] = klass
                    gridHue[gi] = hue
                }

                sampled++
                when (klass) {
                    CLASS_GREEN -> { green++; leaf++; hueSum += hue; satSum += sat; valSum += value }
                    CLASS_CHLOROTIC -> { chlorotic++; leaf++; hueSum += hue; satSum += sat; valSum += value }
                    CLASS_NECROTIC -> { necrotic++; leaf++; hueSum += hue; satSum += sat; valSum += value }
                    CLASS_BRIGHT -> bright++
                    CLASS_DARK -> dark++
                    else -> Unit
                }
            }
        }

        if (sampled == 0) return EMPTY

        // Second pass over the coarse grid: isolation and filament measures.
        for (gy in 1 until rows - 1) {
            for (gx in 1 until cols - 1) {
                val gi = gy * cols + gx
                when (grid[gi]) {
                    CLASS_NECROTIC -> if (isIsolated(grid, cols, gx, gy, CLASS_NECROTIC)) spots++
                    CLASS_BRIGHT -> if (isThinStructure(grid, cols, gx, gy)) filaments++
                    else -> Unit
                }
            }
        }

        val n = sampled.toFloat()
        return Features(
            leafCoverage = leaf / n,
            greenRatio = green / n,
            chlorosisRatio = chlorotic / n,
            necrosisRatio = necrotic / n,
            trichomeRatio = bright / n,
            darkRatio = dark / n,
            spotDensity = spots / n,
            webbingRatio = filaments / n,
            averageHue = if (leaf > 0) (hueSum / leaf).toFloat() else 0f,
            averageSaturation = if (leaf > 0) (satSum / leaf).toFloat() else 0f,
            averageValue = if (leaf > 0) (valSum / leaf).toFloat() else 0f,
            sampledPixels = sampled
        )
    }

    /* ── Pixel classification ────────────────────────────────────────────── */

    private const val CLASS_BACKGROUND = 0
    private const val CLASS_GREEN = 1
    private const val CLASS_CHLOROTIC = 2
    private const val CLASS_NECROTIC = 3
    private const val CLASS_BRIGHT = 4
    private const val CLASS_DARK = 5

    private fun classify(hue: Float, sat: Float, value: Float): Int = when {
        value < 0.10f -> CLASS_DARK
        // Bright and almost colourless: trichomes, powdery mildew, specular glare.
        sat < 0.16f && value > 0.68f -> CLASS_BRIGHT
        sat < 0.16f -> CLASS_BACKGROUND
        // Chlorosis: yellow to yellow-green, bright enough to be living tissue.
        hue in 33f..65f && sat >= 0.16f && value > 0.22f -> CLASS_CHLOROTIC
        // Necrosis: brown, orange-brown or dark red tissue.
        hue < 33f && sat >= 0.16f && value in 0.10f..0.72f -> CLASS_NECROTIC
        // Healthy green, from deep green through yellow-green.
        hue in 65f..175f && value > 0.12f -> CLASS_GREEN
        else -> CLASS_BACKGROUND
    }

    /** True when the 3x3 neighbourhood around (x, y) is mostly *other* than [self]. */
    private fun isIsolated(grid: IntArray, cols: Int, x: Int, y: Int, self: Int): Boolean {
        var same = 0
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            if (grid[(y + dy) * cols + (x + dx)] == self) same++
        }
        // Fewer than 4 of 8 neighbours means a speck, not a contiguous region.
        return same <= 3
    }

    /**
     * True when a bright speck is surrounded by background, i.e. it has no mass.
     * Webbing and fungal hyphae look like this; a powdery coating does not.
     */
    private fun isThinStructure(grid: IntArray, cols: Int, x: Int, y: Int): Boolean {
        var background = 0
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val value = grid[(y + dy) * cols + (x + dx)]
            if (value == CLASS_BACKGROUND || value == CLASS_BRIGHT) background++
        }
        return background >= 4
    }

    /** Standard RGB → HSV. Returns `[hueDegrees 0..360, saturation 0..1, value 0..1]`. */
    fun rgbToHsv(r: Int, g: Int, b: Int): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val maxC = max(rf, max(gf, bf))
        val minC = min(rf, min(gf, bf))
        val delta = maxC - minC

        val hue = when {
            delta <= 0.0001f -> 0f
            maxC == rf -> 60f * (((gf - bf) / delta) % 6f)
            maxC == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }
        val sat = if (maxC <= 0f) 0f else delta / maxC
        return floatArrayOf(if (hue < 0f) hue + 360f else hue, sat, maxC)
    }

    /**
     * Variance of the Laplacian of the luminance plane: the standard focus
     * measure. A sharp image has a high value; a blurred one collapses towards
     * zero, so the trichome screen can tell the user when the shot is usable.
     *
     * [pixels] is ARGB row-major over [width] x [height].
     */
    fun laplacianVariance(pixels: IntArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3 || pixels.isEmpty()) return 0.0

        val luma = FloatArray(width * height)
        for (i in luma.indices) {
            if (i >= pixels.size) break
            val argb = pixels[i]
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            luma[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                if (i >= luma.size) break
                val lap = 4f * luma[i] - luma[i - 1] - luma[i + 1] - luma[i - width] - luma[i + width]
                sum += lap
                sumSq += lap.toDouble() * lap
                n++
            }
        }
        if (n == 0) return 0.0
        val mean = sum / n
        return sumSq / n - mean * mean
    }

    /** Focus verdict thresholds for the trichome screen. */
    const val FOCUS_MIN = 60.0
    const val FOCUS_GOOD = 220.0

    fun focusLabel(variance: Double): String = when {
        variance >= FOCUS_GOOD -> "Nítido · listo para fotografiar"
        variance >= FOCUS_MIN -> "Aceptable · se puede tomar"
        else -> "Borroso · acerca la cámara al tricoma"
    }

    /** Human-readable summary of the measurements, shown under the photo. */
    fun describe(f: Features): List<Pair<String, String>> = buildList {
        add("Cobertura vegetal" to "${(f.leafCoverage * 100).toInt()} %")
        add("Tejido verde sano" to "${(f.greenRatio * 100).toInt()} %")
        add("Clorosis (amarillamiento)" to "${(f.chlorosisRatio * 100).toInt()} %")
        add("Necrosis (tejido muerto)" to "${(f.necrosisRatio * 100).toInt()} %")
        add("Manchas aisladas" to "${(f.spotDensity * 100).toInt()} %")
        add("Estructuras brillantes" to "${(f.trichomeRatio * 100).toInt()} %")
        add("Filamentos" to "${(f.webbingRatio * 100).toInt()} %")
        add("Saturación media" to "${(f.averageSaturation * 100).toInt()} %")
    }

    private val EMPTY = Features(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0)
}
