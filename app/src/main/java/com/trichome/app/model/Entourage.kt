package com.trichome.app.model

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Séquito (entourage) module: cannabinoid x terpene synergies, target
 * pharmacological profiles, and the pure planner that scores a selection.
 *
 * Everything in this file is pure Kotlin with no Android and no I/O, so the
 * whole decision layer is assertable on the JVM. The shipped content lives in
 * `assets/data/entourage_data.json` and is parsed by
 * `EntourageContentRepository` into the domain types below; the parsing test
 * reads the real asset off disk, so a hand-typed "translation" cannot ship in
 * place of it.
 */

/* ── Cannabinoids ──────────────────────────────────────────────────────── */

/**
 * The cannabinoids the module can talk about.
 *
 * An enum, never a raw string: a new member then breaks the build at every
 * exhaustive `when` (see [LabWeights.cannabinoidLoad]) instead of silently
 * falling through and scoring zero. The same reason `Phase`, `LunarPhase` and
 * `EventType` are enums.
 *
 * [key] is the identifier the asset uses; it is the lookup and persistence
 * key, so it must stay stable.
 */
enum class Cannabinoid(
    val key: String,
    val labelEs: String,
    /** One-line mechanism, in Spanish, the same claim the asset makes. */
    val mechanismEs: String
) {
    THC("THC", "THC", "Agonista parcial de CB1 y CB2. Es el responsable del efecto psicoactivo y de la tolerancia."),
    CBD("CBD", "CBD", "Modulador negativo alostérico de CB1, agonista parcial de CB2, agonista de 5-HT1A y modulador de TRPV1. Sin efecto psicoactivo."),
    CBN("CBN", "CBN", "Agonista parcial de CB1 con afinidad entre diez y veinte veces menor que la del THC."),
    CBG("CBG", "CBG", "Agonista parcial de CB1 con afinidad muy baja; agonista de TRPV1 y TRPA1."),
    THCV("THCV", "THCV", "Antagonista de CB1 y agonista parcial de CB2. Su efecto sobre el enfoque es debatido en la literatura."),
    CBC("CBC", "CBC", "Agonista poco activo de CB1 y CB2; es un precursor en la biosíntesis de cannabinoides.");

    companion object {
        private val byKey = entries.associateBy { it.key }

        /** Null when the key is unknown, so an asset typo surfaces as a parse error. */
        fun fromKey(key: String): Cannabinoid? = byKey[key.trim().uppercase()]
    }
}

/* ── Terpenes ──────────────────────────────────────────────────────────── */

/**
 * The terpenes the module can talk about, with the encyclopedia id each one
 * resolves to in `terpenes.json`.
 *
 * Both pinene isomers are distinct members on purpose: they are different
 * compounds with different boiling points, and collapsing them would make the
 * vaporisation table wrong.
 *
 * ## `catalogId` is the join, and it is the reason the two assets cannot drift
 *
 * [catalogId] is the only thing that says which encyclopedia entry a module
 * terpene *is*, and the enumeration above is what makes it exact rather than a
 * guess. `terpenes.json` carries three distinct entries around "pineno":
 *
 * - `alpha_pinene` — 156 °C
 * - `beta_pinene` — 166 °C
 * - `pinene` — 155 °C, a generic entry that is *not* either isomer's twin
 *
 * `ALPHA_PINENE` therefore pairs with `alpha_pinene` and never with `pinene`.
 * Pairing it with the generic entry would report a 1 °C delta between
 * `entourage_data.json` (156) and the encyclopedia (155) that is an artefact of
 * the pairing, not a disagreement about a compound. The decision is deliberate
 * and it is enforced: `EntourageAssetTest.everyBoilingPointAgreesWithBothCatalogs`
 * compares the two sources through this field and fails naming both ids, and
 * `EntourageAssetTest.aModuleTerpeneNeverPairsWithAGenericEncyclopediaEntry`
 * pins the isomer-to-isomer pairing so the delta cannot be reintroduced by
 * repointing a `catalogId`.
 */
enum class EntourageTerpene(
    val key: String,
    val labelEs: String,
    /**
     * Id of the matching entry in `assets/data/terpenes.json`.
     *
     * `terpenes.json` is **canonical** for a compound's boiling point; see the
     * type's KDoc for why. `entourage_data.json` restates it and is required to
     * match.
     */
    val catalogId: String,
    val familyEs: String
) {
    MYRCENE("MYRCENE", "Mirceno", "myrcene", "Monoterpeno"),
    LIMONENE("LIMONENE", "Limoneno", "limonene", "Monoterpeno"),
    LINALOOL("LINALOOL", "Linalool", "linalool", "Monoterpeno"),
    ALPHA_PINENE("ALPHA_PINENE", "Pineno alfa", "alpha_pinene", "Monoterpeno"),
    BETA_PINENE("BETA_PINENE", "Pineno beta", "beta_pinene", "Monoterpeno"),
    OCIMENE("OCIMENE", "Ocimeno", "ocimene", "Monoterpeno"),
    TERPINOLENE("TERPINOLENE", "Terpinoleno", "terpinolene", "Monoterpeno"),
    CAMPHENE("CAMPHENE", "Camfeno", "camphene", "Monoterpeno"),
    HUMULENE("HUMULENE", "Humuleno", "humulene", "Sesquiterpeno"),
    BETA_CARYOPHYLLENE("BETA_CARYOPHYLLENE", "Cariofileno beta", "beta_caryophyllene", "Sesquiterpeno");

    /** True for the sesquiterpenes, which need far more heat than a monoterpene. */
    val isSesquiterpene: Boolean get() = familyEs == "Sesquiterpeno"

    companion object {
        private val byKey = entries.associateBy { it.key }

        fun fromKey(key: String): EntourageTerpene? = byKey[key.trim().uppercase()]
    }
}

/* ── Target profiles ───────────────────────────────────────────────────── */

/**
 * The four pharmacological targets the module plans towards.
 *
 * A profile is a *terpene proportion*, not a dose and not a medical
 * indication. The asset carries the proportions and the note; the enum
 * carries the key, the label and the icon so a target can be selected before
 * the asset is loaded.
 */
enum class PharmacologicalProfile(
    val key: String,
    val labelEs: String,
    val icon: String
) {
    ANSIOLYTIC("ANSIOLITICO", "Ansiolítico", "🫧"),
    ANALGESIC("ANALGESICO", "Analgésico", "🩹"),
    SEDATIVE("SEDANTE", "Sedante", "🌙"),
    FOCUS("FOCO_CREATIVIDAD", "Foco y creatividad", "🎯");

    companion object {
        private val byKey = entries.associateBy { it.key }

        fun fromKey(key: String): PharmacologicalProfile? = byKey[key.trim().uppercase()]
    }
}

/* ── Synergies ─────────────────────────────────────────────────────────── */

/** One cannabinoid x terpene combination the module can explain. */
data class EntourageSynergy(
    val id: String,
    val cannabinoids: Set<Cannabinoid>,
    val terpenes: Set<EntourageTerpene>,
    val profiles: Set<PharmacologicalProfile>,
    /** Short name of the resulting effect, in Spanish. */
    val outcomeEs: String,
    /** Plain-language description of what the user perceives. */
    val descriptionEs: String,
    /** Named receptors and mechanisms. Not "it feels better". */
    val mechanismEs: String,
    /**
     * How well the claim is supported, stated in the content itself.
     *
     * This field is not decoration. A health-adjacent app that prints a
     * mechanistically plausible claim as established fact is worse than one
     * that says less, so every synergy ships its own limits and the UI is
     * expected to show them next to the claim.
     */
    val evidenceEs: String,
    /** Cultivars that typically carry the profile, from the shipped catalog. */
    val strainsEs: List<String>,
    /**
     * Drug-interaction relevance, in Spanish, or empty when there is none to
     * declare. Never medical advice: a warning to consult a professional.
     */
    val interactionEs: String
) {
    /** True when the content declares an interaction the user should read. */
    val hasInteractionWarning: Boolean get() = interactionEs.isNotBlank()
}

/* ── Profiles ──────────────────────────────────────────────────────────── */

/** A target profile loaded from the asset: the proportions it aims at. */
data class EntourageProfile(
    val key: PharmacologicalProfile,
    val labelEs: String,
    val descriptionEs: String,
    /** Cannabinoid to weight. Sums to 1 across the profile in the asset. */
    val cannabinoidWeights: Map<Cannabinoid, Float>,
    /** Terpene to share. Sums to 1 across the profile in the asset. */
    val terpeneShares: Map<EntourageTerpene, Float>,
    /** The limits of the claim, in Spanish, shown with the profile. */
    val noteEs: String
) {
    /** The profile's lead terpene: the largest share, or null when empty. */
    val leadTerpene: EntourageTerpene? get() = terpeneShares.maxByOrNull { it.value }?.key
}

/* ── Vaporisation ──────────────────────────────────────────────────────── */

/**
 * The temperature behaviour of one terpene.
 *
 * [boilingPointC] is the physicochemical fact and the window the UI shows is
 * derived from it. Getting this backwards destroys the very compounds the
 * module exists to preserve: heat a monoterpene to a sesquiterpene's window
 * and the monoterpene is long gone before the sesquiterpene shows up.
 *
 * ## Which source owns [boilingPointC]
 *
 * `terpenes.json` is canonical; `entourage_data.json` restates the number. The
 * argument is about ownership, not about which file looks more careful:
 *
 * - `terpenes.json` covers all 158 catalog compounds and is the field the
 *   terpene detail page already renders. A canonical source for ten rows cannot
 *   be the source for the catalog.
 * - [EntourageTerpene.catalogId] already makes the encyclopedia the *identity*
 *   source for a module terpene. The dependency arrow already points that way;
 *   making the module's asset canonical instead would mean the enum owns the
 *   number and the encyclopedia id is the derived thing, which is the opposite
 *   of how the module is wired today.
 * - The module's `Int` is better *guarded*, not better *authoritative*: the
 *   `init` below checks the window against the boiling point, and
 *   `AssetContent.boilingPointCelsius` returns null when the degree sign is
 *   missing and silently concatenates a range ("155-156 °C" reads as 155156).
 *   A guard on a duplicate does not outrank the original; it is what you add
 *   *because* the duplicate is required to equal the original.
 *
 * So the two agree by construction, and `EntourageAssetTest` holds them to it
 * rather than either being quietly preferred at read time.
 *
 * ## `minTempC == boilingPointC` is the invariant holding, not a copy-paste bug
 *
 * In 9 of the 10 shipped rows `minTempC` equals `boilingPointC` **exactly**.
 * That is `require(minTempC <= boilingPointC)` satisfied at the boundary, not a
 * duplicated column. Read it as the design statement it is: these compounds are
 * only worth stripping once the element reaches their boiling point, so the
 * useful window opens there. Do not "fix" the equality by nudging one value off
 * the other — a floor pushed above the boiling point fails the guard outright,
 * and a floor pushed below it would promise extraction the module cannot
 * substantiate.
 *
 * The 10th row, `BETA_CARYOPHYLLENE` (`boilingPointC` 262, `minTempC` 250), sits
 * 12 °C *below* the boundary instead of on it. That is still inside the
 * invariant, and it is not the same kind of number: 250 °C is an equipment
 * floor, not a physicochemical one. I could not establish a physical reading
 * for the 12 °C margin from anything in this repository — no asset, comment or
 * test claims one. What the invariant *can* say is that the row is admissible,
 * and that a window starting below the boiling point is the honest shape for a
 * compound that no consumer device reaches at its own boiling point. Whether
 * 250 °C is a measured extraction floor or a rounded consumer-device setpoint is
 * a content question, recorded as such rather than invented here.
 */
data class TerpeneVaporisation(
    val terpene: EntourageTerpene,
    val boilingPointC: Int,
    /** Lowest useful working temperature, °C. */
    val minTempC: Int,
    /** Highest temperature before the aroma is lost or turns harsh, °C. */
    val maxTempC: Int,
    val noteEs: String
) {
    init {
        // A window that does not contain the boiling point is not a window, and
        // it would silently teach the user the wrong temperature.
        require(minTempC <= boilingPointC) {
            "${terpene.key}: minTempC ($minTempC) is above the boiling point ($boilingPointC)"
        }
        require(maxTempC > boilingPointC) {
            "${terpene.key}: maxTempC ($maxTempC) is not above the boiling point ($boilingPointC)"
        }
    }
}

/* ── Planner ───────────────────────────────────────────────────────────── */

/**
 * What the user picked.
 *
 * [cannabinoidShares] is what makes the Lab's ceilings enforceable: a plain
 * set cannot express "THC, but only up to a fifth of the profile". An empty
 * map means uniform shares, which is the right reading of a bare list of
 * checkboxes.
 */
data class EntourageSelection(
    val cannabinoids: Set<Cannabinoid> = emptySet(),
    val terpenes: Set<EntourageTerpene> = emptySet(),
    val cannabinoidShares: Map<Cannabinoid, Float> = emptyMap()
) {
    val isEmpty: Boolean get() = cannabinoids.isEmpty() && terpenes.isEmpty()

    /**
     * This cannabinoid's share of [cannabinoids], 0..1.
     *
     * Falls back to a uniform split when the caller gave no shares, so a
     * checkbox list is scored as "all of them equally".
     */
    fun shareOf(cannabinoid: Cannabinoid): Float {
        if (cannabinoid !in cannabinoids) return 0f
        val explicit = cannabinoidShares[cannabinoid]
        if (explicit != null) return explicit.coerceIn(0f, 1f)
        return 1f / cannabinoids.size.coerceAtLeast(1)
    }
}

/** The band a match falls into. Derived from the score, never set separately. */
enum class EntourageMatchQuality {
    /** Nothing selected. Not the same as scoring zero. */
    NO_SELECTION,

    /** The catalog carries no reference for this target. */
    NO_REFERENCE,

    /** Scored, and far from the target proportions. */
    LEJANO,

    /** Some of the terpenes line up. */
    PARCIAL,

    /** Close to the target proportions. */
    AJUSTADO
}

/** One terpene the target wants and the selection does not have. */
data class EntourageGap(
    val terpene: EntourageTerpene,
    /** Share the profile asks for, 0..1. */
    val targetShare: Float,
    val labelEs: String
)

/**
 * The outcome of planning a selection against a target.
 *
 * Read [quality] next to [percent]: a poor match is a *report*, not a verdict
 * about the user. The gaps say what the target asks for, and the interaction
 * notes say what has to be read before acting on it.
 */
data class EntouragePlan(
    val profileKey: PharmacologicalProfile?,
    val percent: Int,
    val quality: EntourageMatchQuality,
    /** Terpenes the target wants that the selection lacks, richest first. */
    val gaps: List<EntourageGap> = emptyList(),
    /** Selected terpenes the target does not use, so the UI can flag them. */
    val offTarget: List<EntourageTerpene> = emptyList(),
    /** Cannabinoids the target wants and the selection lacks. */
    val missingCannabinoids: List<Cannabinoid> = emptyList(),
    /** Interaction notes from every shipped synergy this selection triggers. */
    val interactionWarningsEs: List<String> = emptyList(),
    /** The shipped synergy that best describes this selection, if any. */
    val matchedSynergy: EntourageSynergy? = null,
    /** How many compounds were compared, so the number is not over-trusted. */
    val compoundsCompared: Int = 0,
    /** Player-facing summary of what the number means. */
    val guidanceEs: String = ""
)

/**
 * Scores a cannabinoid x terpene selection against a target profile.
 *
 * ## What the score is
 *
 * It is a **distance from the target profile's terpene proportions**, in
 * percent. It is not a potency estimate, not a safety score and not a
 * prediction of what the user will feel. The UI must say so.
 *
 * ## The formula
 *
 * Reference, over the target profile:
 * ```
 *   r(t) = profile.terpeneShares[t]                  shares sum to 1
 * ```
 * A selection is a **set of compounds**, not a measured mixture, so the score
 * has two terms and only two:
 * ```
 *   coverage = SUM(r(t) for t in selected)                     0..1
 *   focus    = |selected INTERSECT profile.terpeneShares| / |selected|   0..1
 *   score    = 100 * coverage * focus
 * ```
 * `coverage` is how much of the target's terpene mass is present, so the
 * profile's proportions are what gets measured: holding the five compounds of
 * a profile whose shares are 35/25/15/15/10 scores 100, and holding one of
 * them scores 0.35. `focus` is the share of the user's own picks that the
 * profile actually uses, so adding compounds the target does not ask for
 * cannot raise the score.
 *
 * A distribution-similarity metric (Ruzicka, cosine) was tried here first and
 * rejected: it needs proportions on both sides, and a checkbox list has none,
 * so a *uniform* user vector was being compared against the profile's
 * unequal one. That made a selection identical to the profile score 78.
 * Reporting 78 for a perfect match is worse than reporting nothing, so the
 * metric went.
 *
 * The cannabinoid set does **not** enter the score. A selection carries
 * presence, not proportion, and the profiles ship cannabinoid weights only to
 * report a missing one, never to be summed into a potency. Cannabinoids are
 * used to look up [matchedSynergy] and [interactionWarningsEs] instead.
 *
 * ## What a poor match means
 *
 * It means the terpene set is far from the profile. It does **not** mean the
 * product is weak, dangerous or wrong, and the guidance never says so. The
 * gaps are the useful part: they say what the target asks for, and whether
 * those terpenes are actually detectable in a given flower is a lab question
 * this offline app cannot answer.
 */
object EntouragePlanner {

    /**
     * Scores [selection] against [profile].
     *
     * A null [profile] is a different state from an empty selection: the first
     * is a missing catalog, the second is an unanswered screen.
     *
     * @param synergies the shipped synergy library, used only to look up the
     *   matching synergy and the interaction notes. Both are empty when the
     *   content has not been loaded, and the score is unaffected either way.
     */
    fun plan(
        selection: EntourageSelection,
        profile: EntourageProfile?,
        synergies: List<EntourageSynergy> = emptyList()
    ): EntouragePlan {
        if (profile == null || profile.terpeneShares.isEmpty()) {
            return EntouragePlan(
                profileKey = null,
                percent = 0,
                quality = EntourageMatchQuality.NO_REFERENCE,
                guidanceEs = "No hay perfil de referencia cargado para este objetivo."
            )
        }
        if (selection.terpenes.isEmpty()) {
            return EntouragePlan(
                profileKey = profile.key,
                percent = 0,
                quality = EntourageMatchQuality.NO_SELECTION,
                missingCannabinoids = profile.cannabinoidWeights.keys.toList(),
                interactionWarningsEs = interactionWarnings(selection, synergies),
                matchedSynergy = matchedSynergy(selection, synergies),
                guidanceEs = "Elegí terpenos para comparar el perfil."
            )
        }

        val selected = selection.terpenes
        val union = selected + profile.terpeneShares.keys
        val referenceTotal = profile.terpeneShares.values.filter { it > 0f }.sum()
        if (referenceTotal <= 0f) {
            return EntouragePlan(
                profileKey = profile.key,
                percent = 0,
                quality = EntourageMatchQuality.NO_REFERENCE,
                interactionWarningsEs = interactionWarnings(selection, synergies),
                matchedSynergy = matchedSynergy(selection, synergies),
                guidanceEs = "No hay perfil de referencia cargado para este objetivo."
            )
        }

        // Normalise the profile so a content file that ships shares summing to
        // something other than 1 still scores on a 0..1 scale. The asset test
        // asserts the sum is 1, so in practice this is a no-op.
        val shares = profile.terpeneShares.mapValues { (_, share) -> share / referenceTotal }

        val coverage = selected.sumOf { terpene ->
            (shares[terpene] ?: 0f).toDouble()
        }.toFloat().coerceIn(0f, 1f)
        val focus = selected.count { it in shares } / selected.size.toFloat()

        val percent = (coverage * focus * 100).roundToInt().coerceIn(0, 100)

        val gaps = shares
            .filterKeys { it !in selected }
            .entries
            .sortedByDescending { it.value }
            .map { EntourageGap(it.key, it.value, it.key.labelEs) }

        return EntouragePlan(
            profileKey = profile.key,
            percent = percent,
            quality = qualityFor(percent),
            gaps = gaps,
            offTarget = selected.filter { it !in profile.terpeneShares },
            missingCannabinoids = profile.cannabinoidWeights.keys.filter { it !in selection.cannabinoids },
            interactionWarningsEs = interactionWarnings(selection, synergies),
            matchedSynergy = matchedSynergy(selection, synergies),
            compoundsCompared = union.size,
            guidanceEs = guidanceFor(percent, gaps.size)
        )
    }

    /**
     * The band for a displayed [percent].
     *
     * Derived from the rounded number, so a player reading "100%" is never
     * told the match is only "parcial".
     */
    fun qualityFor(percent: Int): EntourageMatchQuality = when {
        percent >= 70 -> EntourageMatchQuality.AJUSTADO
        percent >= 40 -> EntourageMatchQuality.PARCIAL
        else -> EntourageMatchQuality.LEJANO
    }

    /**
     * Every interaction note the selection triggers.
     *
     * Collected from the synergies whose cannabinoids are all present, so a
     * THC selection surfaces the THC notes and a CBD one the CBD note.
     * Deduplicated by text, order preserved: the list is short and the order
     * is the reading order.
     */
    fun interactionWarnings(
        selection: EntourageSelection,
        synergies: List<EntourageSynergy>
    ): List<String> = synergies
        .filter { synergy ->
            synergy.cannabinoids.isNotEmpty() &&
                synergy.cannabinoids.all { it in selection.cannabinoids }
        }
        .map { it.interactionEs }
        .filter { it.isNotBlank() }
        .distinct()

    /**
     * The shipped synergy that best describes [selection].
     *
     * Prefers the most specific match: a synergy whose whole terpene set is
     * present beats a partial one, and among equals the one with the most
     * cannabinoids wins, because that is the more specific claim. Ties break on
     * the id, so the answer never depends on list order.
     */
    fun matchedSynergy(
        selection: EntourageSelection,
        synergies: List<EntourageSynergy>
    ): EntourageSynergy? = synergies
        .filter { it.cannabinoids.all { c -> c in selection.cannabinoids } }
        .filter { it.terpenes.any { t -> t in selection.terpenes } }
        .sortedWith(
            compareByDescending<EntourageSynergy> { it.terpenes.count { t -> t in selection.terpenes } }
                .thenByDescending { it.terpenes.size }
                .thenByDescending { it.cannabinoids.size }
                .thenBy { it.id }
        )
        .firstOrNull()

    /**
     * Player-facing reading of a score.
     *
     * Deliberately never says the selection is bad, unsafe or ineffective. A
     * low number is a distance from one profile's proportions, and a profile
     * is a guide, not a specification.
     */
    fun guidanceFor(percent: Int, gapCount: Int): String = when {
        percent >= 100 -> "Coincide con las proporciones del perfil objetivo."
        percent >= 60 -> "Se acerca a las proporciones del perfil objetivo."
        gapCount == 0 -> "Los terpenos coinciden con el perfil, pero hay compuestos de más."
        percent >= 35 -> if (gapCount == 1) {
            "Coincide con parte del perfil. Falta 1 terpeno del objetivo."
        } else {
            "Coincide con parte del perfil. Faltan $gapCount terpenos del objetivo."
        }
        else -> "La selección de terpenos se aleja del perfil objetivo. Revisá los terpenos que faltan."
    }

    /**
     * The terpenes worth putting on a selector, least volatile first.
     *
     * Driven by [vaporisation] rather than a hand-written order in the enum,
     * so the list cannot drift away from the shipped boiling points. Ties and
     * gaps fall back to the enum name, keeping the result deterministic.
     */
    fun byVolatility(
        vaporisation: List<TerpeneVaporisation>,
        limit: Int = Int.MAX_VALUE
    ): List<EntourageTerpene> {
        if (limit <= 0) return emptyList()
        val boilingPoints = vaporisation.associate { it.terpene to it.boilingPointC }
        return EntourageTerpene.entries
            .sortedWith(
                compareBy<EntourageTerpene> { boilingPoints[it] ?: Int.MAX_VALUE }
                    .thenBy { it.key }
            )
            .take(limit)
    }

    /**
     * The single temperature band that keeps every selected terpene in play.
     *
     * [TerpeneWindow.isViable] false means the selection asks for two things
     * that cannot both hold: the highest minimum is above the lowest maximum.
     * Reporting that honestly is the point; showing the arithmetic mean would
     * be a lie.
     */
    fun windowFor(
        terpenes: Set<EntourageTerpene>,
        vaporisation: List<TerpeneVaporisation>
    ): TerpeneWindow? {
        if (terpenes.isEmpty()) return null
        val rows = vaporisation.filter { it.terpene in terpenes }
        if (rows.isEmpty()) return null
        val highestMinimum = rows.maxOf { it.minTempC }
        val lowestMaximum = rows.minOf { it.maxTempC }
        return TerpeneWindow(
            minTempC = highestMinimum,
            maxTempC = lowestMaximum,
            terpenes = rows.map { it.terpene },
            isViable = highestMinimum <= lowestMaximum
        )
    }
}

/**
 * The temperature band that covers every selected terpene.
 *
 * [isViable] false means the selection cannot be vaporised in one pass without
 * losing a compound, which is exactly the contradiction the data exists to
 * expose.
 */
data class TerpeneWindow(
    val minTempC: Int,
    val maxTempC: Int,
    val terpenes: List<EntourageTerpene>,
    val isViable: Boolean
)

/* ── Quiz ──────────────────────────────────────────────────────────────── */

/** One question of the Séquito quiz, with its answer inside the prompt. */
data class EntourageQuizQuestion(
    val id: String,
    val promptEs: String,
    val optionsEs: List<String>,
    val correctIndex: Int,
    val explanationEs: String
)

/** Every state the Séquito quiz can be in. Mirrors [TerpeneQuizState]. */
sealed interface EntourageQuizState {

    /** The content cannot support a question; [reason] is player-facing. */
    data class Unavailable(val reason: String) : EntourageQuizState

    /** A question is on screen and nothing has been picked yet. */
    data class Asking(
        val question: EntourageQuizQuestion,
        val round: Int,
        val totalRounds: Int,
        val score: Int
    ) : EntourageQuizState

    /**
     * The player picked and the round is being explained.
     *
     * [question] is the question that was asked, never the next one, so the
     * explanation always renders above the question it belongs to.
     */
    data class Revealed(
        val question: EntourageQuizQuestion,
        val chosenIndex: Int,
        val round: Int,
        val totalRounds: Int,
        val score: Int
    ) : EntourageQuizState {
        val isCorrect: Boolean get() = chosenIndex == question.correctIndex
        val isLastRound: Boolean get() = round >= totalRounds
    }

    /** Every round is consumed. */
    data class Finished(val score: Int, val totalRounds: Int) : EntourageQuizState
}

/**
 * The Séquito trivia, as a pure state machine.
 *
 * Same lifecycle as [TerpeneQuiz] and for the same reason: a transition is a
 * function of the current state, so the whole game, including the terminal
 * round, is assertable on the JVM and a double tap cannot corrupt it.
 *
 * ```
 *   (start)  ──▶ Unavailable        no questions loaded
 *            ──▶ Asking
 *   Asking   ──answer(i)──▶ Revealed        tap records, does not advance
 *   Revealed ──next()─────▶ Asking          explicit "Siguiente"
 *                         ─▶ Finished        explicit, and only after the last round
 *   Finished ──answer/next─▶ Finished        terminal states are stable
 *   any      ──restart()───▶ Asking
 * ```
 */
class EntourageQuiz(
    private val questions: List<EntourageQuizQuestion>,
    private val random: kotlin.random.Random = kotlin.random.Random.Default
) {

    /** The current state. Read-only; every change goes through a transition. */
    var state: EntourageQuizState = initialState()
        private set

    /**
     * Records the option at [chosenIndex]. Does **not** advance.
     *
     * Out-of-range indices and states that are not [EntourageQuizState.Asking]
     * are rejected, returning the same instance.
     */
    fun answer(chosenIndex: Int): EntourageQuizState {
        val current = state as? EntourageQuizState.Asking ?: return state
        if (chosenIndex !in current.question.optionsEs.indices) return state
        state = EntourageQuizState.Revealed(
            question = current.question,
            chosenIndex = chosenIndex,
            round = current.round,
            totalRounds = current.totalRounds,
            score = current.score + if (chosenIndex == current.question.correctIndex) 1 else 0
        )
        return state
    }

    /**
     * Advances past the reveal. Rejected while a question is still unanswered,
     * so the game cannot be skipped. The last round lands on
     * [EntourageQuizState.Finished] synchronously, with no empty frame.
     */
    fun next(): EntourageQuizState {
        val current = state as? EntourageQuizState.Revealed ?: return state
        state = if (current.isLastRound) {
            EntourageQuizState.Finished(score = current.score, totalRounds = current.totalRounds)
        } else {
            EntourageQuizState.Asking(
                question = questionAt(current.round),
                round = current.round + 1,
                totalRounds = current.totalRounds,
                score = current.score
            )
        }
        return state
    }

    /** Starts a fresh run on round one with a zero score. */
    fun restart(): EntourageQuizState {
        state = initialState()
        return state
    }

    private fun initialState(): EntourageQuizState = when {
        questions.isEmpty() -> EntourageQuizState.Unavailable("No hay preguntas de Séquito cargadas.")
        else -> EntourageQuizState.Asking(
            question = questionAt(0),
            round = 1,
            totalRounds = questions.size,
            score = 0
        )
    }

    /**
     * The question for a zero-based [index], with its options shuffled.
     *
     * The correct answer's position is randomised because the shipped order
     * always has it first, and a fixed order would be a pattern the player
     * learns instead of the content. [random] is injected so the shuffle is
     * reproducible in tests.
     */
    private fun questionAt(index: Int): EntourageQuizQuestion {
        val question = questions[index.coerceIn(questions.indices)]
        val order = question.optionsEs.indices.shuffled(random)
        return question.copy(
            optionsEs = order.map { question.optionsEs[it] },
            correctIndex = order.indexOf(question.correctIndex)
        )
    }
}

/* ── Mini-game: Entourage Lab ──────────────────────────────────────────── */

/** An axis the case constrains: the more of it, the worse for the patient. */
enum class LabAxis(val key: String, val labelEs: String) {
    ANXIETY("ANSIEDAD", "Ansiedad"),
    TACHYCARDIA("TACHYCARDIA", "Taquicardia"),
    SEDATION("SEDACION", "Sedación"),
    COGNITIVE("IMPAIRMENT_COGNITIVO", "Deterioro cognitivo");

    companion object {
        private val byKey = entries.associateBy { it.key }

        fun fromKey(key: String): LabAxis? = byKey[key.trim().uppercase()]
    }
}

/**
 * A clinical case to solve: a goal, a hard constraint, and four axes with a
 * ceiling each.
 *
 * The case is **data**, not a branch in the UI, so a new case is a new asset
 * entry rather than new Kotlin.
 */
data class EntourageCase(
    val id: String,
    val titleEs: String,
    val briefEs: String,
    val goal: PharmacologicalProfile,
    /** Cannabinoids that disqualify the selection outright. */
    val forbiddenCannabinoids: Set<Cannabinoid> = emptySet(),
    /**
     * Ceiling per cannabinoid, as a share of the selection, 0..1.
     *
     * The plan's insomnia case is this rule: a high tolerance to THC means the
     * patient can take a lot, but the tachycardio ceiling means they cannot
     * take a lot of *this*, so the efficacy has to come from somewhere else.
     */
    val maxCannabinoidShare: Map<Cannabinoid, Float> = emptyMap(),
    /** How much of each axis the patient tolerates, 0..1. */
    val ceilings: Map<LabAxis, Float> = emptyMap(),
    val explanationEs: String
) {
    /** The share ceilings this case imposes, as percentages for display. */
    fun shareCeilings(): List<Pair<Cannabinoid, Int>> =
        maxCannabinoidShare.entries
            .sortedBy { it.key.key }
            .map { it.key to sharePercent(it.value) }
}

/** How good a selection is for a case. */
enum class LabVerdict {
    /** The selection solves the case without crossing a ceiling. */
    OPTIMO,

    /** Useful, but it leaves efficacy or comfort on the table. */
    VIABLE,

    /** It works on the goal and crosses at least one ceiling. */
    RIESGO,

    /** It does nothing for the goal, or the case cannot be scored. */
    INEFICAZ
}

/** Why a selection scored the way it did, in Spanish. */
data class LabAxisReading(
    val axis: LabAxis,
    val labelEs: String,
    /** Load the selection puts on the axis, 0..1. */
    val load: Float,
    /** The case's ceiling for that axis, 0..1. */
    val ceiling: Float,
    val crosses: Boolean
)

/** The outcome of solving a case. */
data class LabResult(
    val caseId: String,
    val verdict: LabVerdict,
    /** 0..100 efficacy against the case goal. */
    val efficacy: Int,
    val readings: List<LabAxisReading>,
    /** Player-facing lines explaining the verdict. */
    val notesEs: List<String>
)

/**
 * The decision layer of the Entourage Lab minigame.
 *
 * A clinical-case puzzle: given a patient and a selection, say whether it
 * solves the case. The scoring is a pure function of a selection, a case and
 * the shipped profiles, so the minigame is testable without a device and a new
 * case is a new asset entry.
 *
 * ## The tension the game is about
 *
 * Efficacy and safety pull in opposite directions. THC loads anxiety and
 * tachycardia hard, so the interesting answer is never "as much THC as
 * possible": it is the largest load that stays under every ceiling, with
 * [EntouragePlanner.plan] deciding whether the terpene set can carry the goal
 * inside that budget.
 *
 * ## The formula
 *
 * ```
 *   load(axis) = SUM over selected ( LabWeights.cannabinoidLoad(c, axis) * share(c) )
 *              + SUM over selected   LabWeights.terpeneLoad(t, axis)
 *   crosses    = load(axis) > ceiling(axis)
 *   efficacy   = EntouragePlanner.percent of the terpene set against the goal
 *   verdict    = INEFICAZ when a forbidden cannabinoid is present,
 *                             when a share ceiling is crossed,
 *                             or when efficacy < EFFICACY_FLOOR
 *               RIESGO   when any axis crosses
 *               OPTIMO   when efficacy >= EFFICACY_EXCELLENT
 *               VIABLE   otherwise
 * ```
 * Terpenes contribute an *absolute* load, not a share: a terpene is either in
 * the profile or it is not, and dividing it by a sum would make adding a
 * harmless terpene look like it reduced the load of a harmful one.
 *
 * ## Why efficacy reads the terpenes only
 *
 * [EntouragePlanner] deliberately refuses to score cannabinoid presence as
 * potency, and the Lab does not override that. Efficacy here is "how close is
 * the terpene set to the target profile", which is a claim the app can
 * actually support; the cannabinoid side of the answer is expressed as the
 * ceilings it has to fit inside.
 */
object EntourageLab {

    /** Below this the selection does nothing for the goal. */
    const val EFFICACY_FLOOR = 30

    /**
     * At or above this, with no ceiling crossed, the selection is optimal.
     *
     * Set above the planner's own `PARCIAL` band so the Lab does not call a
     * partial terpene set "optimal": holding two of a profile's five compounds
     * scores 60 on the planner and must not read as a solved case.
     */
    const val EFFICACY_EXCELLENT = 75

    /**
     * The ceiling an axis gets when the case declares none: no limit.
     *
     * An omitted axis is an axis the case does not talk about, so it must not
     * be read as an axis the patient cannot tolerate at all.
     */
    const val NO_CEILING = 1f

    /**
     * Scores [selection] as an answer to [case].
     *
     * @param profiles the shipped profile library, so the Lab and the planner
     *   can never disagree about what a target means. A case whose goal has no
     *   shipped profile is reported as unscorable rather than scored against an
     *   invented one.
     */
    fun solve(
        case: EntourageCase,
        selection: EntourageSelection,
        profiles: List<EntourageProfile>
    ): LabResult {
        val forbiddenHit = selection.cannabinoids intersect case.forbiddenCannabinoids
        if (forbiddenHit.isNotEmpty()) {
            return LabResult(
                caseId = case.id,
                verdict = LabVerdict.INEFICAZ,
                efficacy = 0,
                readings = emptyList(),
                notesEs = listOf(
                    "El caso descarta " + forbiddenHit.joinToString(", ") { it.labelEs } +
                        ". Ninguna cantidad de ese perfil compensa la contraindicación."
                )
            )
        }

        val goalProfile = profiles.firstOrNull { it.key == case.goal }
        if (goalProfile == null) {
            return LabResult(
                caseId = case.id,
                verdict = LabVerdict.INEFICAZ,
                efficacy = 0,
                readings = emptyList(),
                notesEs = listOf("No hay perfil cargado para el objetivo de este caso.")
            )
        }

        val notes = mutableListOf<String>()

        val overShare = case.maxCannabinoidShare
            .filter { (cannabinoid, max) -> selection.shareOf(cannabinoid) > max }
            .toList()
        if (overShare.isNotEmpty()) {
            notes += overShare.joinToString(" ") { (cannabinoid, max) ->
                "El aporte de ${cannabinoid.labelEs} supera el límite del caso " +
                    "(${sharePercent(selection.shareOf(cannabinoid))}% sobre un máximo de " +
                    "${sharePercent(max)}%)."
            }
        }

        val readings = LabAxis.entries.map { axis ->
            // A ceiling the case does not declare is no ceiling, not a zero
            // ceiling. Defaulting to 0f would make an omitted axis
            // maximally intolerant and fail every selection for a typo nobody
            // can see.
            val ceiling = case.ceilings[axis] ?: NO_CEILING
            val load = LabWeights.load(
                axis = axis,
                cannabinoids = selection.cannabinoids,
                terpenes = selection.terpenes,
                shareOf = selection::shareOf
            )
            LabAxisReading(
                axis = axis,
                labelEs = axis.labelEs,
                load = load,
                ceiling = ceiling,
                crosses = load > ceiling
            )
        }
        readings.filter { it.crosses }.forEach { reading ->
            notes += "Supera el techo de ${reading.labelEs.lowercase()} " +
                "(${sharePercent(reading.load)}% sobre un máximo de " +
                "${sharePercent(reading.ceiling)}%)."
        }

        val efficacy = EntouragePlanner.plan(selection, goalProfile).percent

        val verdict = when {
            overShare.isNotEmpty() -> LabVerdict.RIESGO
            efficacy < EFFICACY_FLOOR -> LabVerdict.INEFICAZ
            readings.any { it.crosses } -> LabVerdict.RIESGO
            efficacy >= EFFICACY_EXCELLENT -> LabVerdict.OPTIMO
            else -> LabVerdict.VIABLE
        }

        if (notes.isEmpty()) {
            notes += when (verdict) {
                LabVerdict.OPTIMO -> "Cumple el objetivo del caso sin cruzar ningún techo."
                LabVerdict.VIABLE -> "Ayuda al objetivo, pero con margen de mejora."
                LabVerdict.RIESGO -> "Ayuda al objetivo, pero a un coste que el caso no tolera."
                LabVerdict.INEFICAZ -> "No aporta casi nada al objetivo del caso."
            }
        }

        return LabResult(
            caseId = case.id,
            verdict = verdict,
            efficacy = efficacy,
            readings = readings,
            notesEs = notes
        )
    }

    /**
     * The largest cannabinoid share the case tolerates for [cannabinoid].
     *
     * 1f when the case sets no ceiling, so the absence of a rule reads as "no
     * limit" rather than as a silent zero.
     */
    fun toleratedShare(case: EntourageCase, cannabinoid: Cannabinoid): Float =
        case.maxCannabinoidShare[cannabinoid] ?: 1f
}

/**
 * The Lab's own weighting: how much each compound loads each axis.
 *
 * **These are puzzle numbers, not measurements.** They exist so the minigame's
 * cases are solvable and the tension between efficacy and tolerance is real;
 * they are not derived from a pharmacokinetic study and must never be shown to
 * the user as a dose-response curve. The UI shows the verdict, the axes and
 * the ceilings, not these constants.
 *
 * The relative ordering is the part that carries meaning: THC loads anxiety,
 * tachycardia and cognitive impairment harder than anything else here, CBD
 * barely loads anything, CBN loads sedation hardest, and myrcene and linalool
 * load sedation. That is what makes "less THC, more terpene" the answer to the
 * insomnia case.
 */
object LabWeights {

    /** Cannabinoid load per axis, 0..1, before the case's ceilings. */
    fun cannabinoidLoad(cannabinoid: Cannabinoid, axis: LabAxis): Float = when (cannabinoid) {
        Cannabinoid.THC -> when (axis) {
            LabAxis.ANXIETY -> 0.55f
            LabAxis.TACHYCARDIA -> 0.50f
            LabAxis.SEDATION -> 0.35f
            LabAxis.COGNITIVE -> 0.55f
        }
        Cannabinoid.CBD -> when (axis) {
            LabAxis.ANXIETY -> 0.05f
            LabAxis.TACHYCARDIA -> 0.05f
            LabAxis.SEDATION -> 0.05f
            LabAxis.COGNITIVE -> 0.02f
        }
        Cannabinoid.CBN -> when (axis) {
            LabAxis.ANXIETY -> 0.05f
            LabAxis.TACHYCARDIA -> 0.05f
            LabAxis.SEDATION -> 0.55f
            LabAxis.COGNITIVE -> 0.25f
        }
        Cannabinoid.CBG -> when (axis) {
            LabAxis.ANXIETY -> 0.02f
            LabAxis.TACHYCARDIA -> 0.02f
            LabAxis.SEDATION -> 0.05f
            LabAxis.COGNITIVE -> 0.02f
        }
        Cannabinoid.THCV -> when (axis) {
            LabAxis.ANXIETY -> 0.15f
            LabAxis.TACHYCARDIA -> 0.15f
            LabAxis.SEDATION -> 0.05f
            LabAxis.COGNITIVE -> 0.10f
        }
        Cannabinoid.CBC -> when (axis) {
            LabAxis.ANXIETY -> 0.02f
            LabAxis.TACHYCARDIA -> 0.02f
            LabAxis.SEDATION -> 0.05f
            LabAxis.COGNITIVE -> 0.02f
        }
    }

    /** Terpene load per axis, 0..1, absolute rather than a share. */
    fun terpeneLoad(terpene: EntourageTerpene, axis: LabAxis): Float = when (terpene) {
        EntourageTerpene.MYRCENE -> when (axis) {
            LabAxis.ANXIETY -> 0.02f
            LabAxis.TACHYCARDIA -> 0.02f
            LabAxis.SEDATION -> 0.22f
            LabAxis.COGNITIVE -> 0.10f
        }
        EntourageTerpene.LINALOOL -> when (axis) {
            LabAxis.ANXIETY -> 0.02f
            LabAxis.TACHYCARDIA -> 0.02f
            LabAxis.SEDATION -> 0.20f
            LabAxis.COGNITIVE -> 0.08f
        }
        EntourageTerpene.LIMONENE -> when (axis) {
            LabAxis.ANXIETY -> 0.02f
            LabAxis.TACHYCARDIA -> 0.02f
            LabAxis.SEDATION -> 0.04f
            LabAxis.COGNITIVE -> 0.02f
        }
        EntourageTerpene.BETA_CARYOPHYLLENE -> when (axis) {
            LabAxis.ANXIETY -> 0.01f
            LabAxis.TACHYCARDIA -> 0.01f
            LabAxis.SEDATION -> 0.05f
            LabAxis.COGNITIVE -> 0.02f
        }
        EntourageTerpene.HUMULENE -> when (axis) {
            LabAxis.ANXIETY -> 0.01f
            LabAxis.TACHYCARDIA -> 0.01f
            LabAxis.SEDATION -> 0.04f
            LabAxis.COGNITIVE -> 0.02f
        }
        EntourageTerpene.ALPHA_PINENE, EntourageTerpene.BETA_PINENE -> when (axis) {
            LabAxis.ANXIETY -> 0.02f
            LabAxis.TACHYCARDIA -> 0.02f
            LabAxis.SEDATION -> 0.05f
            LabAxis.COGNITIVE -> 0.03f
        }
        EntourageTerpene.OCIMENE -> when (axis) {
            LabAxis.ANXIETY -> 0.01f
            LabAxis.TACHYCARDIA -> 0.01f
            LabAxis.SEDATION -> 0.03f
            LabAxis.COGNITIVE -> 0.02f
        }
        EntourageTerpene.TERPINOLENE -> when (axis) {
            LabAxis.ANXIETY -> 0.01f
            LabAxis.TACHYCARDIA -> 0.01f
            LabAxis.SEDATION -> 0.05f
            LabAxis.COGNITIVE -> 0.02f
        }
        EntourageTerpene.CAMPHENE -> when (axis) {
            LabAxis.ANXIETY -> 0.02f
            LabAxis.TACHYCARDIA -> 0.02f
            LabAxis.SEDATION -> 0.06f
            LabAxis.COGNITIVE -> 0.03f
        }
    }

    /**
     * Total load on [axis] for a selection.
     *
     * Cannabinoids are weighted by their share of the selection; terpenes
     * contribute absolutely, for the reason documented on [EntourageLab].
     */
    fun load(
        axis: LabAxis,
        cannabinoids: Set<Cannabinoid>,
        terpenes: Set<EntourageTerpene>,
        shareOf: (Cannabinoid) -> Float
    ): Float {
        val fromCannabinoids = cannabinoids.sumOf {
            (cannabinoidLoad(it, axis) * shareOf(it)).toDouble()
        }.toFloat()
        val fromTerpenes = terpenes.sumOf { terpeneLoad(it, axis).toDouble() }.toFloat()
        return (fromCannabinoids + fromTerpenes).coerceIn(0f, 1f)
    }
}

/* ── Small helpers ─────────────────────────────────────────────────────── */

/** Rounds a 0..1 share to a whole percent for display. */
internal fun sharePercent(share: Float): Int = (share * 100).roundToInt()

/** True when two shares are equal within display precision. */
internal fun sharesMatch(a: Float, b: Float, epsilon: Float = 0.005f): Boolean =
    abs(a - b) < epsilon
