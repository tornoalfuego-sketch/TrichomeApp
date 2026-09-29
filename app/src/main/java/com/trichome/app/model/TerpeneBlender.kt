package com.trichome.app.model

import com.trichome.app.data.repository.Terpene

/**
 * How close a reading is to the reference profile. The band is derived from the
 * percentage that will actually be shown, so the label and the number can never
 * disagree.
 */
enum class BlendQuality {
    /** Nothing was set on the sliders. Not the same as scoring zero. */
    NO_SELECTION,

    /** The catalogue carries no abundance rating to score against. */
    NO_REFERENCE,

    /** Scored, but far from the reference proportions. */
    DEBIL,

    /** Some of the proportions line up, most do not. */
    PARCIAL,

    /** Close to the reference proportions. */
    FUERTE,

    /** The proportions are the reference proportions. */
    EXACTO
}

/** One line of the "why" report: a compound and how far off it is. */
data class BlendNote(
    val id: String,
    val name: String,
    val family: String,
    /** The player's share of their own mix, 0..1. */
    val userShare: Float,
    /** The reference share of the same set of compounds, 0..1. */
    val referenceShare: Float
) {
    /** Positive when the player over-weighted this compound. */
    val delta: Float get() = userShare - referenceShare
}

/**
 * The outcome of scoring a mix.
 *
 * @param percent the score, 0..100, rounded to a whole number.
 * @param quality the band, derived from [percent] so the two always agree.
 */
data class BlendResult(
    val percent: Int,
    val quality: BlendQuality,
    /** The compounds the player pushed above their reference share. */
    val overRepresented: List<BlendNote> = emptyList(),
    /** The compounds the player pushed below their reference share. */
    val underRepresented: List<BlendNote> = emptyList(),
    /** Family split of the player's mix, shares summing to 1. */
    val userFamilySplit: Map<String, Float> = emptyMap(),
    /** Family split of the reference over the same compounds. */
    val referenceFamilySplit: Map<String, Float> = emptyMap()
)

/**
 * Master Blender: scores a hand-entered terpene mix against the catalogue.
 *
 * ## Data source — and why it is not "a named strain"
 *
 * The request was a match against "the real profile of known strains". The
 * catalogue does not have one and cannot be made to have one:
 *
 * - `Terpene.strains` is co-occurrence, not composition. 34 of its 55 strain
 *   names are listed under exactly **one** terpene, and the most popular name
 *   is listed under 140 of the 158. Inverting that field would not recover a
 *   profile, it would invent one, and a hardcoded table of made-up percentages
 *   would be worse: it would look like lab data and be fiction.
 * - `Terpene.pairsWith` is the entourage graph, a statement about which
 *   compounds modulate each other. It carries no abundance.
 * - `Terpene.richness` — "muy alto" / "alto" / "medio" / "bajo" — *is* a
 *   per-compound abundance rating, and it is complete: every shipped entry has
 *   one. It is the only quantitative signal the data actually carries.
 *
 * So the reference is **the catalogue's own richness profile**: the distribution
 * the shipped encyclopedia says a cannabis terpene profile looks like. The UI
 * labels it that way and never names a cultivar. A grower moving a myrcene
 * slider learns whether their mix looks like a typical profile and which
 * compounds are off — which is a real, defensible answer. "You are 87% OG Kush"
 * would not be.
 *
 * ## The formula
 *
 * Reference, over the whole catalogue:
 * ```
 *   w(t)  = RICHNESS_WEIGHT[richness(t)]        (see [richnessWeight])
 *   r(t)  = w(t) / SUM(all t' : w(t') > 0)     shares sum to 1 over the catalog
 * ```
 * Given the player's `mix` (id to percent, any scale), let `S` be the ids in the
 * mix that are **both** above zero **and** carry a rating. If either set is
 * empty the result is [BlendQuality.NO_SELECTION] or [BlendQuality.NO_REFERENCE].
 *
 * Both sides are then renormalised over `S`, because a slider reading of 0 means
 * "not measured or not detected", not "measured and absent" — penalising a
 * grower for leaving a slider alone would make the number meaningless:
 * ```
 *   u(t)    = mix(t)  / SUM(mix  over S)
 *   rS(t)   = r(t)    / SUM(r    over S)
 *   score   = SUM(min(u, rS)) / SUM(max(u, rS))        over S
 * ```
 * That ratio is the Ruzicka similarity (a soft Jaccard): 1.0 exactly when the
 * two distributions are equal, and it degrades smoothly as a compound is
 * over- or under-weighted. `score` is strictly greater than 0 whenever `S` is
 * non-empty — both sides are positive everywhere on `S`, so every `min` term is
 * positive — which is why an empty and a zero match are distinct states rather
 * than the same number.
 *
 * The odds of two random mixes agreeing by chance fall as `S` grows, so a
 * five-compound reading is meaningfully harder to match than a two-compound one.
 * That is a property of the metric, not a bug: the UI shows how many compounds
 * were read so the number is not over-trusted.
 */
object TerpeneBlender {

    /**
     * Ordinal richness mapped to a weight.
     *
     * The four levels are a ranking, not measurements, so the ladder is spaced
     * by judgement and documented here rather than hidden: "muy alto" is worth
     * four times a trace compound, "alto" twice, "medio" one. Any unrecognised
     * or blank level weighs **zero** — the compound is then outside the
     * reference entirely rather than being given a guessed value.
     */
    fun richnessWeight(level: String): Float = when (level.trim().lowercase()) {
        "muy alto" -> 4f
        "alto" -> 2f
        "medio" -> 1f
        "bajo" -> 0.5f
        else -> 0f
    }

    /**
     * The reference distribution over the whole catalogue, shares summing to 1.
     *
     * Unrated entries are omitted. An empty result means there is nothing to
     * score against and callers must report [BlendQuality.NO_REFERENCE].
     */
    fun referenceProfile(catalog: List<Terpene>): Map<String, Float> {
        val weights = catalog.associate { it.id to richnessWeight(it.richness) }
        val total = weights.values.filter { it > 0f }.sum()
        if (total <= 0f) return emptyMap()
        return weights.filterValues { it > 0f }.mapValues { (_, weight) -> weight / total }
    }

    /**
     * Scores [mix] against the catalogue's reference profile.
     *
     * @param mix terpene id to percentage, on any scale; only entries above zero
     *   and present in the catalogue are read.
     * @param catalog the encyclopedia, as loaded.
     */
    fun match(mix: Map<String, Float>, catalog: List<Terpene>): BlendResult {
        val weights = catalog.associate { it.id to richnessWeight(it.richness) }
        if (weights.values.none { it > 0f }) {
            return BlendResult(percent = 0, quality = BlendQuality.NO_REFERENCE)
        }

        val entries = catalog.associateBy { it.id }
        val scored = mix.filter { (id, value) -> value > 0f && (weights[id] ?: 0f) > 0f }
        if (scored.isEmpty()) {
            return BlendResult(percent = 0, quality = BlendQuality.NO_SELECTION)
        }

        val userTotal = scored.values.sum()
        val referenceTotal = scored.keys.sumOf { weights.getValue(it).toDouble() }.toFloat()

        val notes = scored.keys.sorted().map { id ->
            val userShare = scored.getValue(id) / userTotal
            val referenceShare = weights.getValue(id) / referenceTotal
            val terpene = entries[id]
            BlendNote(
                id = id,
                name = terpene?.name ?: id,
                family = terpene?.family.orEmpty(),
                userShare = userShare,
                referenceShare = referenceShare
            )
        }

        // Ruzicka similarity: see the class KDoc for the derivation.
        val shared = notes.sumOf { minOf(it.userShare, it.referenceShare).toDouble() }
        val union = notes.sumOf { maxOf(it.userShare, it.referenceShare).toDouble() }
        val percent = TerpeneProgression.percent((shared / union).toFloat()).coerceIn(0, 100)

        val families = notes.map { it.family }.filter { it.isNotBlank() }.toSet()
        return BlendResult(
            percent = percent,
            quality = qualityFor(percent),
            overRepresented = notes.filter { it.delta > 0.02f }.sortedByDescending { it.delta },
            underRepresented = notes.filter { it.delta < -0.02f }.sortedBy { it.delta },
            userFamilySplit = familySplit(notes.map { it.userShare to it.family }),
            referenceFamilySplit = familySplit(
                notes.map { it.referenceShare to it.family },
                only = families
            )
        )
    }

    /**
     * The compounds worth putting on a slider, richest first.
     *
     * Deterministic: abundance descending, then name, so the same catalogue
     * always produces the same list. Records sharing a display name are
     * collapsed — the catalogue ships both `myrcene` and `beta_myrcene` under
     * the name "Mirceno", and two sliders reading "Mirceno" would be unusable.
     */
    fun featuredCompounds(catalog: List<Terpene>, limit: Int = DEFAULT_SLIDERS): List<Terpene> {
        if (limit <= 0) return emptyList()
        return catalog
            .filter { it.name.isNotBlank() && richnessWeight(it.richness) > 0f }
            .sortedWith(
                compareByDescending<Terpene> { richnessWeight(it.richness) }
                    .thenBy { it.name }
                    .thenBy { it.id }
            )
            .distinctBy { it.name }
            .take(limit)
    }

    /** How many sliders the dialog opens with. */
    const val DEFAULT_SLIDERS = 8

    /**
     * The band for a displayed [percent].
     *
     * Derived from the rounded number rather than the raw score, so a player
     * reading "100%" is never told the match is merely "fuerte".
     */
    fun qualityFor(percent: Int): BlendQuality = when {
        percent >= 100 -> BlendQuality.EXACTO
        percent >= 80 -> BlendQuality.FUERTE
        percent >= 50 -> BlendQuality.PARCIAL
        else -> BlendQuality.DEBIL
    }

    /** Groups per-compound shares by family and normalises each side to 1. */
    private fun familySplit(
        shares: List<Pair<Float, String>>,
        only: Set<String> = shares.mapNotNull { it.second.takeIf(String::isNotBlank) }.toSet()
    ): Map<String, Float> {
        val totals = mutableMapOf<String, Float>()
        shares.forEach { (share, family) ->
            if (family.isNotBlank() && family in only) {
                totals[family] = (totals[family] ?: 0f) + share
            }
        }
        val sum = totals.values.sum()
        if (sum <= 0f) return emptyMap()
        return totals.mapValues { (_, value) -> value / sum }
    }
}
