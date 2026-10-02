package com.trichome.app.model

/**
 * The geometry of the detail page's label/value row, the composable
 * `DataRow` in `ui/screens/terpenes/TerpeneDetailScreen.kt`.
 *
 * ## Why these numbers are not in the composable
 *
 * Compose has no unit-test runtime in this project — only device-only
 * `androidTest` — so anything a composable does with a number is untested
 * until someone opens the app. That is not academic: `Modifier.weight(0f)`
 * throws `IllegalArgumentException: invalid weight 0.0; must be greater than
 * zero` while composing, it shipped that way inside the volatility curve, and
 * every terpene detail page crashed on open. The bug was invisible to 981 JVM
 * tests because the number lived in the renderer.
 *
 * The same rule applies to a collision, only quieter: two texts laid out with
 * no width split do not throw, they simply touch, and a screenshot at 3 AM is
 * the only thing that catches it. So the numbers that decide how the row
 * divides its width live here, where a JVM test can read them.
 *
 * ## The layout in words
 *
 * The label is measured first, at its natural width — `Row` measures
 * unweighted children before weighted ones — so it keeps one line however long
 * the value is. The value then takes **everything that is left**, minus
 * [GAP_DP], and is right-aligned inside it, so a short value still lands flush
 * against the right edge exactly as it did before this row was constrained.
 *
 * The guarantee that matters: a value of any length wraps inside its own
 * column and can never overlap the label, because the label no longer competes
 * for the value's pixels. [remainingForValueDp] is that arithmetic.
 *
 * ## The one failure this cannot prevent
 *
 * A label long enough to fill the row on its own leaves the value nothing. It
 * is left unfixed rather than papered over: every label on the page is a short
 * literal, capping the label would need another magic fraction, and a cap that
 * guessed the wrong share would reintroduce the "no gap" defect in the common
 * case. If a label that long ever ships, it is a new problem with its own
 * answer.
 */
object DataRowLayout {

    /**
     * Horizontal air between the label and the value, dp.
     *
     * Never zero. The defect this fixes was a row with no gap at all, and a gap
     * is the whole reason the label and the value cannot touch.
     */
    const val GAP_DP: Int = 12

    /**
     * The share of the remaining width the value takes.
     *
     * `1f` — all of it — and pinned here because `RowScope.weight` **throws on
     * zero**. A second weight of `0f` in this row would close the page the same
     * way the first one did.
     */
    const val VALUE_WEIGHT: Float = 1f

    /**
     * Width left for the value in a row [rowWidthDp] wide whose label measured
     * [labelWidthDp].
     *
     * The arithmetic `Row` performs: the label is measured first, [GAP_DP] is
     * consumed, and [VALUE_WEIGHT] — being `1f` — claims the rest. Never
     * negative: a label wider than the row yields a value of zero rather than an
     * arithmetic that could push a child off the row.
     */
    fun remainingForValueDp(rowWidthDp: Int, labelWidthDp: Int): Int =
        (rowWidthDp - labelWidthDp - GAP_DP).coerceAtLeast(0)
}