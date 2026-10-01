package com.trichome.app.model

/**
 * Everything the calendar's lunar bar has to write, as data.
 *
 * Compose cannot be unit-tested in this module — `compose-ui-test` only exists in
 * `androidTest`, which needs a device — so the copy that decides what a grower
 * reads about the moon lives here instead of inside a composable. That is the same
 * split [SuperCycleForm] already uses for the photoperiod screen: the decision is
 * pure and JVM-testable, and the composable only renders what it is handed.
 *
 * The split is not cosmetic. A phase name on its own teaches nobody anything, so
 * the card carries [LunarCardContent.tipHeadlineEs] and [LunarCardContent.adviceEs]
 * from [LunarEngine.tipFor]. Getting that wiring wrong is invisible to the
 * compiler: the card still builds, still shows a phase and still looks finished.
 * A test that asserts every phase resolves to non-blank advice is what catches it.
 *
 * No Android types: [ModelPurityTest]'s rule is followed here by construction, and
 * the copy cannot silently acquire a `Context` that would need Robolectric.
 */
data class LunarCardContent(
    /** Stable key the composable turns into a glyph. Never a raw enum name. */
    val iconKey: String,
    /** Emoji rendering of [iconKey]; a fallback glyph, never a resource name. */
    val glyph: String,
    /** `LunarPhase.labelEs`, verbatim from the engine. */
    val phaseLabelEs: String,
    /** Whole percent of the disc lit, e.g. "62 %". */
    val illuminationEs: String,
    /** "creciente" or "menguante". */
    val trendLabelEs: String,
    /** `tipFor(phase).headlineEs`, e.g. "Luna llena · floración". */
    val tipHeadlineEs: String,
    /** `tipFor(phase).adviceEs` — the sentence that is the point of the feature. */
    val adviceEs: String,
    /** `LunarEngine.DISCLAIMER_ES`, shown under the advice. */
    val disclaimerEs: String,
    /**
     * Label for the toggle button.
     *
     * Describes the *action*, not the state, and names what the press reveals —
     * an icon button announcing only "lunar" leaves a screen-reader user with no
     * idea that a whole advice panel is behind it. Different when collapsed and
     * expanded so the user always knows which way the press goes.
     */
    val toggleDescriptionEs: String
)

/**
 * The lunar bar's copy, resolved from a [LunarSnapshot].
 *
 * Pure and exhaustive. The glyph is resolved through [iconKey] rather than matched
 * on the phase enum, because the engine's contract is that `iconKey` never changes:
 * a rename of `labelEs` cannot orphan a glyph, and an unknown key degrades to a
 * plain moon instead of throwing or rendering nothing.
 */
object LunarCardCopy {

    /** Plain moon, used for a key this version does not know about. */
    const val FALLBACK_GLYPH: String = "🌙"

    /** Prefix of the collapsed toggle's `contentDescription`. */
    const val EXPAND_PREFIX_ES: String = "Ver fase lunar y su consejo de cultivo"

    /** Prefix of the expanded toggle's `contentDescription`. */
    const val COLLAPSE_PREFIX_ES: String = "Ocultar la fase lunar"

    private val glyphsByIconKey: Map<String, String> = mapOf(
        "lunar_new_moon" to "🌑",
        "lunar_waxing_crescent" to "🌒",
        "lunar_first_quarter" to "🌓",
        "lunar_waxing_gibbous" to "🌔",
        "lunar_full_moon" to "🌕",
        "lunar_waning_gibbous" to "🌖",
        "lunar_last_quarter" to "🌗",
        "lunar_waning_crescent" to "🌘"
    )

    /** The glyph for an engine icon key, or [FALLBACK_GLYPH] for an unknown one. */
    fun glyphFor(iconKey: String): String = glyphsByIconKey[iconKey] ?: FALLBACK_GLYPH

    /**
     * Spanish trend word.
     *
     * Exhaustive so a ninth [LunarTrend] is a build break here rather than a
     * grower reading a blank where "creciente" should be.
     */
    fun trendLabelEs(trend: LunarTrend): String = when (trend) {
        LunarTrend.WAXING -> "creciente"
        LunarTrend.WANING -> "menguante"
    }

    /**
     * Whole percent of the lit disc, Spanish spacing.
     *
     * Rounded rather than truncated: an illuminated fraction of 0.623 must not
     * advertise "62 %" when it is nearer 62.3, and `Math.round` is the one rule
     * that keeps the number the closest integer to what the engine computed.
     */
    fun illuminationEs(illumination: Float): String =
        "${Math.round(illumination.coerceIn(0f, 1f) * 100f)} %"

    /** The collapsed button's `contentDescription`. */
    fun expandDescriptionEs(phaseLabelEs: String): String =
        "$EXPAND_PREFIX_ES: $phaseLabelEs"

    /** The expanded button's `contentDescription`. */
    fun collapseDescriptionEs(): String = COLLAPSE_PREFIX_ES

    /**
     * Resolves the whole card from one snapshot.
     *
     * @param expanded the toggle's current state; changes only the
     *   `contentDescription`, so the visible copy cannot differ from the announced
     *   one.
     */
    fun contentOf(snapshot: LunarSnapshot, expanded: Boolean = false): LunarCardContent =
        LunarCardContent(
            iconKey = snapshot.phase.iconKey,
            glyph = glyphFor(snapshot.phase.iconKey),
            phaseLabelEs = snapshot.phase.labelEs,
            illuminationEs = illuminationEs(snapshot.illumination),
            trendLabelEs = trendLabelEs(snapshot.trend),
            tipHeadlineEs = snapshot.tip.headlineEs,
            adviceEs = snapshot.tip.adviceEs,
            disclaimerEs = LunarEngine.DISCLAIMER_ES,
            toggleDescriptionEs = if (expanded) {
                collapseDescriptionEs()
            } else {
                expandDescriptionEs(snapshot.phase.labelEs)
            }
        )
}