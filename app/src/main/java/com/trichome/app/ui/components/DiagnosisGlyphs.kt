package com.trichome.app.ui.components

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.unit.dp
import com.trichome.app.model.DiagnosisGlyph
import com.trichome.app.model.DiagnosisGlyphBase
import com.trichome.app.model.DiagnosisGlyphMark

/**
 * The drawn half of [DiagnosisGlyph].
 *
 * ## Why the vectors live here and not in the model
 *
 * `DiagnosisIcons` names motifs and holds the assignment table; it draws nothing,
 * so it stays pure Kotlin and `ModelPurityTest` can keep the `model/` package free
 * of `androidx` imports. `ImageVector` is a Compose type, so the construction is
 * here — which means the assignment table is testable on the JVM and the drawing
 * is a separate, replaceable concern.
 *
 * ## Why code and not a drawable resource
 *
 * `res/drawable` in this project holds three files, all launcher icons. A vector
 * drawable is one XML file per figure, and the requirement is 116 figures; the
 * authored `ImageVector` below is one file. It also means a figure is built from
 * two named parts — [motif] and [qualifier] — rather than 116 unrelated blobs
 * that have to be kept consistent by hand.
 *
 * ## The drawing rules
 *
 * Thin strokes on a 24 dp viewport, round caps and joins, nothing filled except
 * the [DiagnosisGlyphMark.DOT] qualifier. The figures are botanical and chemical
 * diagrams rather than illustrations: a midrib and a laterals, a taproot and its
 * roots, a flame with an inner flame. That restraint is the brief — a symptom
 * picker that draws cartoon beetles would be claiming a species the engine never
 * identified, and `PhotoDiagnosisEngine` only ever reports thresholds.
 *
 * Every figure is cached by its key, because `ImageVector` construction allocates
 * and a 116-chip `FlowRow` rebuilding its paths on every recomposition is a real
 * cost for no benefit. The cache is bounded by [DiagnosisIcons.capacity], which is
 * a compile-time constant, so it cannot grow without bound.
 */
object DiagnosisGlyphVectors {

    /** The viewport every figure is drawn on. Matches Material's icon grid. */
    private const val VIEWPORT = 24f

    /** Stroke width, in viewport units. Thin by intent. */
    private const val STROKE = 1.15f

    private val cache = HashMap<String, ImageVector>()

    /**
     * The vector for [glyph], built once and reused.
     *
     * Keyed on [DiagnosisGlyph.key] rather than on the enum pair so a rename of
     * either axis cannot leave a stale vector behind under an old key.
     */
    fun vectorFor(glyph: DiagnosisGlyph): ImageVector = synchronized(cache) {
        cache.getOrPut(glyph.key) { build(glyph) }
    }

    private fun build(glyph: DiagnosisGlyph): ImageVector {
        val builder = ImageVector.Builder(
            name = "diagnosis_${glyph.key}",
            defaultWidth = VIEWPORT.dp,
            defaultHeight = VIEWPORT.dp,
            viewportWidth = VIEWPORT,
            viewportHeight = VIEWPORT
        )
        addStroke(builder, glyph.base.name, motif(glyph.base))
        if (glyph.mark != DiagnosisGlyphMark.PLAIN) {
            addStroke(builder, glyph.mark.name, qualifier(glyph.mark))
        }
        return builder.build()
    }

    /**
     * Appends one stroked path.
     *
     * The stroke parameters are passed individually rather than as a
     * `Stroke(...)` object, because that overload of `addPath` has been the
     * stable one across every Compose version this project has compiled against
     * and the `Stroke`-taking overload has not. Nothing is lost: `Stroke` is
     * exactly these four numbers.
     */
    private fun addStroke(builder: ImageVector.Builder, name: String, nodes: List<PathNode>) {
        builder.addPath(
            pathData = nodes,
            pathFillType = PathFillType.NonZero,
            name = name,
            fill = null,
            fillAlpha = 0f,
            stroke = SolidColor(Color.Black),
            strokeAlpha = 1f,
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineMiter = 4f
        )
    }

    /**
     * The motif paths, in the 4..20 box.
     *
     * Read as diagrams, not illustrations. `LEAF` is a midrib between two arcs —
     * the shape of a leaf blade and nothing else, so it cannot be mistaken for a
     * particular species, a particular cultivar, or a photograph.
     */
    /**
     * The motif paths, in the 4..20 box.
     *
     * Read as diagrams, not illustrations. `LEAF` is a midrib between two arcs --
     * the shape of a leaf blade and nothing else, so it cannot be mistaken for a
     * particular species, a particular cultivar, or a photograph.
     */
    private fun motif(base: DiagnosisGlyphBase): List<PathNode> = when (base) {
        // Midrib plus the two blade edges.
        DiagnosisGlyphBase.LEAF -> draw {
            moveTo(6f, 19f); curveTo(3f, 14f, 6f, 7f, 13f, 5.5f)
            curveTo(15f, 10f, 13f, 17f, 6f, 19f); close()
            moveTo(6f, 19f); lineTo(14.5f, 6f)
        }

        // A blade whose distal third is gone, replaced by a torn zigzag.
        DiagnosisGlyphBase.SCORCH -> draw {
            moveTo(5f, 19f); curveTo(3.5f, 14f, 6f, 8f, 11.5f, 6f)
            lineTo(13f, 9f); lineTo(15.5f, 5.5f); lineTo(17f, 9f)
            lineTo(13.5f, 11f); lineTo(12f, 14f); lineTo(9f, 13f)
            lineTo(7f, 17f); close()
            moveTo(6f, 19f); lineTo(8f, 15.5f)
        }

        // A blade rolling in on itself: two tightening arcs off a petiole.
        DiagnosisGlyphBase.CURL -> draw {
            moveTo(4.5f, 19f); lineTo(4.5f, 10f)
            curveTo(4.5f, 6f, 10f, 4.5f, 11.5f, 7.5f)
            curveTo(13f, 10.5f, 9.5f, 12.5f, 8f, 10.5f)
            curveTo(6.8f, 8.8f, 8.8f, 7.2f, 10.2f, 8.4f)
            curveTo(12.5f, 10.4f, 15f, 9f, 17f, 7f)
        }

        // A petiole and a blade hanging under it.
        DiagnosisGlyphBase.DROOPING_LEAF -> draw {
            moveTo(4.5f, 4.5f); lineTo(9f, 8f)
            curveTo(15f, 10f, 16.5f, 17f, 10f, 19.5f)
            curveTo(4.5f, 20f, 3f, 13f, 8f, 9.5f)
            moveTo(8f, 9.5f); lineTo(11f, 16.5f)
        }

        // Midrib with three pairs of laterals.
        DiagnosisGlyphBase.VEINS -> draw {
            moveTo(5f, 19.5f); lineTo(18f, 5f)
            moveTo(8.5f, 16.5f); lineTo(6f, 11.5f)
            moveTo(8.5f, 16.5f); lineTo(13f, 13.5f)
            moveTo(11.5f, 13.5f); lineTo(8.5f, 8.5f)
            moveTo(11.5f, 13.5f); lineTo(16f, 10.5f)
            moveTo(14.5f, 10.5f); lineTo(12f, 6.5f)
            moveTo(14.5f, 10.5f); lineTo(18f, 8f)
        }

        // A closed ring with a small nucleus.
        DiagnosisGlyphBase.LESION -> draw {
            moveTo(11f, 4.5f); curveTo(17.5f, 5f, 20f, 11f, 17f, 17f)
            curveTo(13f, 20.5f, 6f, 19f, 5f, 13f)
            curveTo(4.2f, 8f, 6f, 4f, 11f, 4.5f); close()
            circle(11f, 12f, 2.2f)
        }

        // An irregular translucent patch, no nucleus.
        DiagnosisGlyphBase.BLOTCH -> draw {
            moveTo(7f, 6f); curveTo(14f, 3f, 20.5f, 8f, 18f, 15f)
            curveTo(15.5f, 20.5f, 6f, 20f, 4.5f, 13f)
            curveTo(3.5f, 9.5f, 4f, 7f, 7f, 6f); close()
        }

        // Three overlapping puffs.
        DiagnosisGlyphBase.POWDER -> draw {
            circle(8f, 9f, 2.6f); circle(15f, 8f, 2.6f); circle(11.5f, 15.5f, 3f)
        }

        // A droplet with a shoulder line.
        DiagnosisGlyphBase.SAP -> draw {
            moveTo(10.5f, 4f); curveTo(14.5f, 10f, 17.5f, 12.5f, 17.5f, 15.5f)
            curveTo(17.5f, 19.5f, 7.5f, 19.5f, 7.5f, 15.5f)
            curveTo(7.5f, 12.5f, 9f, 9f, 10.5f, 4f); close()
            moveTo(10f, 14f); curveTo(10.5f, 16.5f, 12f, 17.5f, 14f, 17.5f)
        }

        // An oval body and eight legs.
        DiagnosisGlyphBase.MITE -> draw {
            moveTo(10f, 9f); curveTo(13.5f, 9f, 14.5f, 11f, 14.5f, 12.5f)
            curveTo(14.5f, 14f, 13.5f, 16f, 10f, 16f)
            curveTo(6.5f, 16f, 5.5f, 14f, 5.5f, 12.5f)
            curveTo(5.5f, 11f, 6.5f, 9f, 10f, 9f); close()
            moveTo(7f, 9.5f); lineTo(5f, 6.5f)
            moveTo(10f, 9f); lineTo(10f, 5.5f)
            moveTo(13f, 9.5f); lineTo(15f, 6.5f)
            moveTo(7f, 15.5f); lineTo(5f, 18.5f)
            moveTo(10f, 16f); lineTo(10f, 19.5f)
            moveTo(13f, 15.5f); lineTo(15f, 18.5f)
        }

        // A hub, four radials and two arcs.
        DiagnosisGlyphBase.WEB -> draw {
            moveTo(5.5f, 5.5f); lineTo(17f, 5.5f)
            moveTo(5.5f, 5.5f); lineTo(18f, 12f)
            moveTo(5.5f, 5.5f); lineTo(12f, 19f)
            moveTo(5.5f, 5.5f); lineTo(5.5f, 18f)
            moveTo(17f, 5.5f); curveTo(20f, 12f, 20f, 14f, 12f, 19f)
            moveTo(18f, 12f); curveTo(13f, 13f, 9f, 15.5f, 5.5f, 18f)
        }

        // A wing over a short flight trail.
        DiagnosisGlyphBase.WING -> draw {
            moveTo(7f, 14f); curveTo(5f, 8f, 10.5f, 4f, 17f, 5f)
            curveTo(16.5f, 11f, 12.5f, 16.5f, 7f, 14f); close()
            moveTo(5.5f, 18.5f); lineTo(8.5f, 17.5f)
            moveTo(3.5f, 21f); lineTo(7f, 20f)
        }

        // Four segments along a curve.
        DiagnosisGlyphBase.LARVA -> draw {
            circle(6.5f, 15f, 2.2f); circle(10f, 11f, 2.2f)
            circle(14f, 8.5f, 2.2f); circle(17.5f, 7f, 2f)
            moveTo(8.4f, 13.6f); lineTo(8.4f, 12.4f)
            moveTo(12f, 10.4f); lineTo(12f, 9.2f)
            moveTo(15.7f, 7.8f); lineTo(15.9f, 6.8f)
        }

        // A blade edge with a bite out of it.
        DiagnosisGlyphBase.NOTCH -> draw {
            moveTo(6f, 19f); curveTo(4f, 14f, 6.5f, 8f, 13f, 5.5f)
            curveTo(16f, 9f, 16.5f, 14f, 12f, 18f); close()
            moveTo(13f, 5.5f); lineTo(11f, 8.5f); lineTo(14f, 9f)
            lineTo(12f, 12f); lineTo(14.5f, 12.5f)
        }

        // A branching mycelium.
        DiagnosisGlyphBase.HYPHA -> draw {
            moveTo(11f, 20f); lineTo(11f, 13f)
            moveTo(11f, 13f); curveTo(11f, 10f, 8f, 9f, 6f, 7.5f)
            moveTo(11f, 13f); curveTo(11f, 9.5f, 14.5f, 9f, 16.5f, 6.5f)
            moveTo(11f, 16.5f); curveTo(13.5f, 15.5f, 15f, 15f, 17.5f, 13f)
            moveTo(11f, 16.5f); curveTo(8.5f, 16f, 7f, 15f, 5f, 13f)
        }

        // A head on a stalk, and two freed spores.
        DiagnosisGlyphBase.SPORE -> draw {
            circle(11f, 8f, 3f)
            moveTo(11f, 11f); lineTo(11f, 20f)
            circle(17.5f, 13.5f, 1.6f)
            circle(5f, 15f, 1.4f)
        }

        // A short plant with three leaves over a baseline.
        DiagnosisGlyphBase.VIGOUR -> draw {
            moveTo(4f, 20f); lineTo(20f, 20f)
            moveTo(12f, 20f); lineTo(12f, 9f)
            curveTo(12f, 7f, 9.5f, 5.5f, 7f, 6.5f)
            curveTo(9f, 9f, 11f, 9.5f, 12f, 9f)
            curveTo(12f, 6.5f, 14.5f, 4.5f, 17f, 5.5f)
            curveTo(15f, 8.5f, 13f, 9.5f, 12f, 9f)
            moveTo(12f, 14f); curveTo(12f, 12f, 10.5f, 10.5f, 8.5f, 11.5f)
            curveTo(10f, 13.5f, 11.5f, 14f, 12f, 14f)
        }

        // A cut stem section with a vascular chevron.
        DiagnosisGlyphBase.STEM -> draw {
            moveTo(7.5f, 4f); lineTo(7.5f, 20f)
            moveTo(16.5f, 4f); lineTo(16.5f, 20f)
            moveTo(7.5f, 8f); curveTo(12f, 13f, 16.5f, 8f, 16.5f, 19f)
        }

        // A blade drawn twice, the inner outline offset: a double cast.
        DiagnosisGlyphBase.BLUEING -> draw {
            moveTo(6f, 18f); curveTo(4f, 13f, 6.5f, 7f, 13f, 5.5f)
            curveTo(15f, 10f, 13f, 16f, 6f, 18f); close()
            moveTo(8.5f, 16f); curveTo(7f, 12f, 9f, 8.5f, 14f, 7.5f)
            curveTo(15f, 11f, 13.5f, 14.5f, 8.5f, 16f)
        }

        // A taproot with three laterals.
        DiagnosisGlyphBase.ROOT -> draw {
            moveTo(12f, 4f); lineTo(12f, 12f)
            curveTo(12f, 16f, 10f, 18f, 11f, 20f)
            moveTo(12f, 10f); curveTo(12f, 13f, 8.5f, 13.5f, 6.5f, 16f)
            moveTo(12f, 10f); curveTo(12f, 13f, 15.5f, 13.5f, 17.5f, 16f)
            moveTo(12f, 15f); curveTo(12f, 17f, 14f, 18f, 15.5f, 20f)
        }

        // A bud on a stem, with the calyx as a pair of shoulders.
        DiagnosisGlyphBase.BUD -> draw {
            moveTo(12f, 3.5f); curveTo(15f, 6.5f, 15.5f, 10f, 12f, 13f)
            curveTo(8.5f, 10f, 9f, 6.5f, 12f, 3.5f); close()
            moveTo(9.5f, 12f); lineTo(14.5f, 12f)
            moveTo(12f, 13f); lineTo(12f, 20f)
            moveTo(12f, 16f); curveTo(10f, 15f, 8.5f, 15.5f, 7f, 17f)
            moveTo(12f, 18f); curveTo(14f, 17f, 15.5f, 17.5f, 17f, 19f)
        }

        // A flame tongue with an inner flame.
        DiagnosisGlyphBase.FLAME -> draw {
            moveTo(12f, 3f); curveTo(16f, 8f, 18.5f, 11.5f, 16.5f, 16f)
            curveTo(14.5f, 20f, 8.5f, 20f, 7f, 16f)
            curveTo(5.5f, 11.5f, 9f, 9f, 12f, 3f); close()
            moveTo(12f, 11f); curveTo(14f, 13.5f, 14f, 16f, 12f, 18f)
            curveTo(10f, 16f, 10f, 13.5f, 12f, 11f)
        }

        // A six-point frost star.
        DiagnosisGlyphBase.FROST -> draw {
            moveTo(12f, 3.5f); lineTo(12f, 20.5f)
            moveTo(4f, 8f); lineTo(20f, 16f)
            moveTo(4f, 16f); lineTo(20f, 8f)
            moveTo(12f, 7f); lineTo(9.5f, 9f); moveTo(12f, 7f); lineTo(14.5f, 9f)
            moveTo(12f, 17f); lineTo(9.5f, 15f); moveTo(12f, 17f); lineTo(14.5f, 15f)
        }
    }

    /**
     * The qualifier paths, in the 17..22 box.
     *
     * Drawn clear of the motif so the two never overlap into an unreadable
     * tangle: the eye reads the motif and the mark independently, which is what
     * makes 116 figures legible at 20 dp.
     */
    private fun qualifier(mark: DiagnosisGlyphMark): List<PathNode> = when (mark) {
        DiagnosisGlyphMark.PLAIN -> emptyList()
        DiagnosisGlyphMark.DOT -> draw { circle(19.5f, 12f, 1.15f) }
        DiagnosisGlyphMark.RING -> draw { circle(19.5f, 12f, 2.6f) }
        DiagnosisGlyphMark.ARC -> draw {
            moveTo(17.2f, 8.6f); curveTo(21.8f, 10.5f, 21.8f, 13.5f, 17.2f, 15.4f)
        }
        DiagnosisGlyphMark.CROSS -> draw {
            moveTo(17.2f, 9.4f); lineTo(21.8f, 14.6f)
            moveTo(21.8f, 9.4f); lineTo(17.2f, 14.6f)
        }
        DiagnosisGlyphMark.BAR -> draw { moveTo(17f, 12f); lineTo(22f, 12f) }
        DiagnosisGlyphMark.CHEVRON -> draw {
            moveTo(17.4f, 9.6f); lineTo(19.5f, 12f); lineTo(17.4f, 14.4f)
            moveTo(20f, 9.6f); lineTo(22.1f, 12f); lineTo(20f, 14.4f)
        }
        DiagnosisGlyphMark.WAVE -> draw {
            moveTo(16.9f, 12f); curveTo(18f, 10f, 19f, 10f, 19.6f, 11.4f)
            curveTo(20.2f, 12.8f, 20.6f, 13.6f, 22.1f, 14f)
        }
    }
}


/**
 * A closed figure, or an empty node list for a qualifier that draws nothing.
 *
 * `PathBuilder.nodes` rather than a `PathData` wrapper: the node list is the form
 * `ImageVector.Builder.addPath` takes, so nothing has to be converted afterwards,
 * and `PathData` no longer exists in the Compose version this project builds
 * against. The four-cubic circle below is drawn by hand for the same reason — it
 * removes the `arcTo` boolean flags, which are the easiest thing in a path
 * description to get subtly wrong.
 */
private fun draw(block: PathBuilder.() -> Unit): List<PathNode> =
    PathBuilder().apply(block).nodes

/** A circle as four cubics, so nothing here needs an arc command. */
private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    val k = r * 0.5523f
    moveTo(cx + r, cy)
    curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
    curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
    curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
    curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
    close()
}

/**
 * A symptom's glyph on screen.
 *
 * The [DiagnosisGlyph.contentDescriptionEs] is passed as the content description
 * so the figure is announced rather than skipped. `null` is the right answer only
 * where the label beside it already carries the name; a `FilterChip` does not, so
 * this takes one.
 *
 * The tint comes from the caller. The stroke is drawn in black and recoloured by
 * `Icon`, which is what lets the figure follow the theme instead of fighting it —
 * one of the three reasons the emoji went.
 */
@Composable
fun DiagnosisGlyphIcon(
    glyph: DiagnosisGlyph,
    contentDescription: String? = glyph.contentDescriptionEs,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    Icon(
        imageVector = DiagnosisGlyphVectors.vectorFor(glyph),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}
