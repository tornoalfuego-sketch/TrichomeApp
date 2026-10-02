package com.trichome.app.model

/**
 * The numbers behind the app's instrumented type register.
 *
 * ## What the register is for
 *
 * Prose and instrumentation are two registers, not fourteen sizes. A sentence in
 * Spanish is read as language; a number is read as a number, and a number is
 * judged against the number beside it — the tent's VPD against the outside VPD,
 * today's pH against the last ten rows, `24 h` against `18 h + 6 h`. Those two
 * readings want different voices, and the app had no way to say so: the single
 * monospace readout in the whole codebase was written inline at one call site in
 * `TerpeneDetailScreen`, so every other measured value in the app was rendered in
 * whatever prose face the user happened to pick.
 *
 * That is not only a matter of taste. On the device this was verified on, the
 * system sans-serif resolves to a handwriting face, so prose is already hard to
 * read there while `FontFamily.Monospace` resolves correctly. A metric in the
 * prose face inherits the handwriting. See `metricFontFamily` in
 * `ui/theme/TrichomeTheme.kt` for the rule that keeps the two registers apart.
 *
 * ## Why these numbers live here
 *
 * Compose has no unit-test runtime in this project — neither Robolectric nor
 * `compose-ui-test` is on the `test` classpath — and four separate bug classes
 * have already come from a decision a JVM test could not reach. So the decision
 * is written down as numbers a test can read, and `buildTypography` only consumes
 * them. Changing a metric's size is an edit here, and the test that pins it fails
 * loudly when someone changes it without meaning to.
 *
 * ## Two sizes, and why not one
 *
 * The app shows metrics at two genuinely different scales, and one size cannot
 * serve both:
 *
 *  - **Headline.** The one number a screen leads with — the profile match
 *    percentage on the Séquito booster. It was `headlineMedium` (28sp), and a
 *    single role sized for rows would have demoted it to 14sp, a four-fold drop
 *    on the number the whole panel exists to deliver.
 *  - **Value.** Everything else: a `DataRow` value, a stat under a caption, a
 *    chart's min/max. 14sp is what the one shipped monospace readout already used
 *    (`bodyMedium`'s metrics), so applying the role to `DataRow` changes the
 *    family and nothing else — verified on device before and after.
 *
 * The boundary is functional, not per screen: **headline** is a value the screen
 * leads with, **value** is a value the screen reads against something else.
 *
 * ## Tracking
 *
 * [LETTER_SPACING_SP] is zero, and it is zero because every prose slot's tracking
 * is deliberately *not* multiplied by the user's size scale — only `fontSize` and
 * `lineHeight` are. Scaling tracking alongside the size is the usual reflex and it
 * is wrong here: proportional sans already needs its positive tracking to stay
 * legible, and the scale slider exists for people who need *larger* text, not
 * looser text. The metric register follows the rule the prose slots already
 * follow rather than introducing a second one.
 */
object MetricType {

    /* ── The value register: a number in a row, or under a caption ────────── */

    /**
     * 14sp, which is `bodyMedium`'s size.
     *
     * Anchored to a size the app already shipped rather than a new number: the
     * inline override this role replaces was `bodyMedium.copy(...)`, so the terpene
     * identity card changes typeface and keeps its metrics exactly.
     */
    const val VALUE_FONT_SIZE_SP: Int = 14

    /** `bodyMedium`'s line height, 20sp — a 1.43 ratio, which clears Spanish accents. */
    const val VALUE_LINE_HEIGHT_SP: Int = 20

    /* ── The headline register: the one number a screen leads with ─────────── */

    /** 28sp, which is `headlineMedium`'s size — the Séquito booster's percentage. */
    const val HEADLINE_FONT_SIZE_SP: Int = 28

    /**
     * 34sp, a 1.21 ratio against 28sp.
     *
     * Tighter than [VALUE_LINE_HEIGHT_SP]'s 1.43 because a lead number is set on one
     * or two lines, never justified to a measure, so the leading a paragraph needs
     * is space this register does not spend. It is still 1.21: a `values_es` string
     * can carry an accent at the top of the line — `Índice`, `Más`, `Nivel` — and a
     * line box that clips its own ascender reports itself as a rendering bug long
     * before anyone notices the number.
     */
    const val HEADLINE_LINE_HEIGHT_SP: Int = 34

    /* ── Tracking ─────────────────────────────────────────────────────────── */

    /** Zero, for the reason in the class KDoc. Never multiplied by the scale. */
    const val LETTER_SPACING_SP: Double = 0.0

    /* ── Weight ───────────────────────────────────────────────────────────── */

    /**
     * The weight both metric roles land on at the default preference, in
     * hundredths.
     *
     * One constant for both sizes, deliberately. The headline does not get a
     * heavier weight than the value: it gets a larger one, which is the whole
     * reason it is a separate size. A lead number that is heavy *and* large reads
     * as a shout, and 28sp at 500 already reads as a headline next to 14sp at 500.
     *
     * Neither role fixes its own weight. They run the reader's pick through the
     * same one-step `lift()` the `title*` and `label*` slots use, so someone who
     * chose *Ligera* because the default was too heavy gets that here too. 500 is
     * `NORMAL.lift()` -- `FontWeight.Medium` -- and is the weight the inline
     * override this register replaces already used on the terpene detail row.
     *
     * The theme test asserts this constant against what `buildTypography` actually
     * produces, so the documented weight and the built one cannot drift.
     */
    const val LIFTED_WEIGHT: Int = 500

    /* ── The scale arithmetic, kept here so it can be tested ──────────────── */

    /**
     * [sizeSp] multiplied by the user's size scale, in sp.
     *
     * `fontSize` and `lineHeight` are scaled, and tracking is not — see the class
     * KDoc. Extracted rather than inlined in `buildTypography` so the test can pin
     * the arithmetic without a Compose `TextStyle` in the way.
     */
    fun fontSizeSp(sizeSp: Int, scale: Float): Float = sizeSp * scale

    /** [lineHeightSp] multiplied by the user's size scale, in sp. Same rule. */
    fun lineHeightSp(lineHeightSp: Int, scale: Float): Float = lineHeightSp * scale
}
