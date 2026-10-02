package com.trichome.app.data.repository

import android.content.Context
import com.trichome.app.data.entity.Achievement
import com.trichome.app.model.AgronomyEvidence
import com.trichome.app.model.AgronomyLever
import com.trichome.app.model.AgronomyLeverKind
import com.trichome.app.model.Cannabinoid
import com.trichome.app.model.EntourageAchievement
import com.trichome.app.model.EntourageAgronomy
import com.trichome.app.model.EntourageAgronomyIndex
import com.trichome.app.model.EntourageCase
import com.trichome.app.model.EntourageProfile
import com.trichome.app.model.EntourageProcessing
import com.trichome.app.model.EntourageProcessingIndex
import com.trichome.app.model.EntourageQuizQuestion
import com.trichome.app.model.EntourageSynergy
import com.trichome.app.model.EntourageTerpene
import com.trichome.app.model.LabAxis
import com.trichome.app.model.PharmacologicalProfile
import com.trichome.app.model.PreservationFactorKind
import com.trichome.app.model.PreservationFactorNote
import com.trichome.app.model.ProcessingEvidence
import com.trichome.app.model.ProcessingMethod
import com.trichome.app.model.ProcessingMethodNote
import com.trichome.app.model.TerpeneVaporisation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/* ── Entourage (Séquito) content ───────────────────────────────────────── */

/**
 * One cannabinoid x terpene synergy, as it sits in the asset.
 *
 * Every field is defaulted so an older asset still parses, following the same
 * rule as [Terpene]: a shipped content file is a long-lived thing and a
 * missing key must not take the screen down. What the module needs is checked
 * by the asset test, which reads the real file off disk.
 */
@Serializable
internal data class EntourageSynergyAsset(
    val id: String = "",
    val cannabinoids: List<String> = emptyList(),
    val terpenes: List<String> = emptyList(),
    val profiles: List<String> = emptyList(),
    @SerialName("outcome_es") val outcomeEs: String = "",
    @SerialName("description_es") val descriptionEs: String = "",
    @SerialName("mechanism_es") val mechanismEs: String = "",
    /** How well supported the claim is. Shown next to it, never hidden. */
    @SerialName("evidence_es") val evidenceEs: String = "",
    @SerialName("strains_es") val strainsEs: List<String> = emptyList(),
    /** Drug-interaction relevance, or empty. Never medical advice. */
    @SerialName("interaction_es") val interactionEs: String = ""
)

@Serializable
internal data class EntourageCannabinoidWeightAsset(
    val cannabinoid: String = "",
    val weight: Float = 0f
)

@Serializable
internal data class EntourageTerpeneShareAsset(
    val terpene: String = "",
    val share: Float = 0f
)

@Serializable
internal data class EntourageProfileAsset(
    val key: String = "",
    @SerialName("label_es") val labelEs: String = "",
    @SerialName("description_es") val descriptionEs: String = "",
    val cannabinoids: List<EntourageCannabinoidWeightAsset> = emptyList(),
    val terpenes: List<EntourageTerpeneShareAsset> = emptyList(),
    @SerialName("note_es") val noteEs: String = ""
)

@Serializable
internal data class EntourageVaporisationAsset(
    val terpene: String = "",
    @SerialName("boilingPointC") val boilingPointC: Int = 0,
    @SerialName("minTempC") val minTempC: Int = 0,
    @SerialName("maxTempC") val maxTempC: Int = 0,
    @SerialName("note_es") val noteEs: String = ""
)

/**
 * F3: one compound's agronomy, as shipped.
 *
 * `kind` and `evidence` are strings here and enums in the model, exactly like the
 * cannabinoid / terpene / profile keys. [EntourageBible.toContent] resolves them
 * through `fromKey` and drops the ones it cannot, so a typo in a lever key cannot
 * become a card that says something with no lever behind it.
 */
@Serializable
internal data class EntourageAgronomyLeverAsset(
    val kind: String = "",
    @SerialName("detail_es") val detailEs: String = "",
    val evidence: String = "",
    @SerialName("basis_es") val basisEs: String = ""
)

@Serializable
internal data class EntourageAgronomyAsset(
    val terpene: String = "",
    @SerialName("response_es") val responseEs: String = "",
    val levers: List<EntourageAgronomyLeverAsset> = emptyList()
)

/**
 * F4: one note of one named method for one compound, as shipped.
 *
 * `method` and `evidence` are strings here and enums in the model, exactly like
 * F3's `EntourageAgronomyLeverAsset`. Note what is **absent**: there is no
 * `safety_es`. The safety framing is not shipped content, it is
 * `ProcessingMethodGuide.safetyEs` in `model/`, which no asset edit can blank
 * and no missing key can remove.
 */
@Serializable
internal data class EntourageProcessingMethodAsset(
    val method: String = "",
    @SerialName("detail_es") val detailEs: String = "",
    val evidence: String = "",
    @SerialName("basis_es") val basisEs: String = ""
)

/** F4: one note of one preservation factor for one compound, as shipped. */
@Serializable
internal data class EntourageProcessingFactorAsset(
    val factor: String = "",
    @SerialName("detail_es") val detailEs: String = "",
    val evidence: String = "",
    @SerialName("basis_es") val basisEs: String = ""
)

/**
 * F4: one compound's processing behaviour, as shipped.
 *
 * The entry-level `evidence` / `basis_es` pair qualifies `response_es`, the same
 * discipline F3 applied per lever. Both are required: a processing claim with no
 * level is dropped rather than shown unqualified.
 */
@Serializable
internal data class EntourageProcessingAsset(
    val terpene: String = "",
    @SerialName("response_es") val responseEs: String = "",
    val evidence: String = "",
    @SerialName("basis_es") val basisEs: String = "",
    val methods: List<EntourageProcessingMethodAsset> = emptyList(),
    val preservation: List<EntourageProcessingFactorAsset> = emptyList()
)

@Serializable
internal data class EntourageCaseAsset(
    val id: String = "",
    @SerialName("title_es") val titleEs: String = "",
    @SerialName("brief_es") val briefEs: String = "",
    @SerialName("goalProfile") val goalProfile: String = "",
    @SerialName("forbiddenCannabinoids") val forbiddenCannabinoids: List<String> = emptyList(),
    @SerialName("maxCannabinoidShare") val maxCannabinoidShare: Map<String, Float> = emptyMap(),
    @SerialName("sideEffectCeilings") val sideEffectCeilings: Map<String, Float> = emptyMap(),
    @SerialName("explanation_es") val explanationEs: String = ""
)

@Serializable
internal data class EntourageQuizQuestionAsset(
    val id: String = "",
    @SerialName("prompt_es") val promptEs: String = "",
    @SerialName("options_es") val optionsEs: List<String> = emptyList(),
    @SerialName("correctIndex") val correctIndex: Int = 0,
    @SerialName("explanation_es") val explanationEs: String = ""
)

@Serializable
internal data class EntourageBible(
    val version: Int = 1,
    @SerialName("disclaimer_es") val disclaimerEs: String = "",
    val synergies: List<EntourageSynergyAsset> = emptyList(),
    val profiles: List<EntourageProfileAsset> = emptyList(),
    val vaporisation: List<EntourageVaporisationAsset> = emptyList(),
    val cases: List<EntourageCaseAsset> = emptyList(),
    val quiz: List<EntourageQuizQuestionAsset> = emptyList(),
    val agronomy: List<EntourageAgronomyAsset> = emptyList(),
    val processing: List<EntourageProcessingAsset> = emptyList()
)

/**
 * The parsed entourage content.
 *
 * [unresolvedReferences] is the honest half of the parse: an entry whose
 * cannabinoid, terpene, profile or axis key is not in the corresponding enum
 * is **dropped** and named here, instead of being coerced to a default that
 * would score as if the compound were absent. The shipped asset must produce
 * an empty list, and the asset test asserts it.
 */
data class EntourageContent(
    val disclaimerEs: String = "",
    val synergies: List<EntourageSynergy> = emptyList(),
    val profiles: List<EntourageProfile> = emptyList(),
    val vaporisation: List<TerpeneVaporisation> = emptyList(),
    val cases: List<EntourageCase> = emptyList(),
    val questions: List<EntourageQuizQuestion> = emptyList(),
    /**
     * F3: the agronomy block, keyed by terpene.
     *
     * Not one entry per module compound and that is the decision, not an
     * omission: see [com.trichome.app.model.EntourageAgronomy] for why it is
     * keyed by compound rather than by cannabinoid x terpene pair. A compound
     * with no documented lever has no entry and no default.
     */
    val agronomy: List<EntourageAgronomy> = emptyList(),
    /**
     * F4: the processing block, keyed by terpene.
     *
     * Same keying decision as [agronomy] and for the same reason: `LIMONENE`
     * appears in two shipped synergies, and a pair-keyed note would write the
     * same paragraph twice with two chances to drift. The asymmetry with
     * [agronomy] is deliberate and is stated on `EntourageProcessing`: F3's
     * question was what the *plant* responds to, F4's is what happens to the
     * *compound* under a named method, and that covers all ten.
     */
    val processing: List<EntourageProcessing> = emptyList(),
    /** Keys the enums do not know, as `"collection.id -> key"`. */
    val unresolvedReferences: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = synergies.isEmpty() && profiles.isEmpty()

    /**
     * F3: the agronomy index, built once per content load.
     *
     * Derived rather than stored so the index and the list cannot be two
     * sources of truth, the same way `TerpeneVolatilityIndex.from` works for F2.
     */
    fun agronomyIndex(): EntourageAgronomyIndex = EntourageAgronomyIndex(agronomy)

    /**
     * F4: the processing index, built once per content load.
     *
     * Derived rather than stored for the reason `agronomyIndex` is: the index and
     * the list must not be two sources of truth.
     */
    fun processingIndex(): EntourageProcessingIndex = EntourageProcessingIndex(processing)

    /** The shipped profile for [key], or null when the asset omits it. */
    fun profile(key: PharmacologicalProfile): EntourageProfile? =
        profiles.firstOrNull { it.key == key }

    /** The case with [id], or null. */
    fun caseById(id: String): EntourageCase? = cases.firstOrNull { it.id == id }

    /** The synergy with [id], or null. */
    fun synergyById(id: String): EntourageSynergy? = synergies.firstOrNull { it.id == id }
}

/**
 * Loads the Séquito library from `assets/data/entourage_data.json`.
 *
 * Parsed once and cached for the process lifetime, like [TerpenesRepository].
 */
class EntourageContentRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: EntourageContent? = null

    /** The whole library, or an empty one when the asset is unreadable. */
    suspend fun getContent(): EntourageContent = cached ?: synchronized(this) {
        cached ?: run {
            val raw = context.assets.open("data/entourage_data.json").bufferedReader().use { it.readText() }
            val parsed = json.decodeFromString<EntourageBible>(raw).toContent()
            cached = parsed
            parsed
        }
    }

    /** The four target profiles, ready for the planner. */
    suspend fun getProfiles(): List<EntourageProfile> = getContent().profiles

    /** The synergy library, for the planner's lookup and the interaction notes. */
    suspend fun getSynergies(): List<EntourageSynergy> = getContent().synergies

    /** The temperature table, for the preservation tips. */
    suspend fun getVaporisation(): List<TerpeneVaporisation> = getContent().vaporisation

    /** The Lab minigame's cases. */
    suspend fun getCases(): List<EntourageCase> = getContent().cases

    /** The quiz questions, in shipped order. */
    suspend fun getQuestions(): List<EntourageQuizQuestion> = getContent().questions

    /** F3: the agronomy block, keyed by terpene. */
    suspend fun getAgronomy(): List<EntourageAgronomy> = getContent().agronomy

    /** F3: the agronomy index, for the synergy card and the terpene detail page. */
    suspend fun getAgronomyIndex(): EntourageAgronomyIndex = getContent().agronomyIndex()

    /** F4: the processing block, keyed by terpene. */
    suspend fun getProcessing(): List<EntourageProcessing> = getContent().processing

    /** F4: the processing index, for the synergy card and the terpene detail page. */
    suspend fun getProcessingIndex(): EntourageProcessingIndex = getContent().processingIndex()
}

/* ── Mapping ───────────────────────────────────────────────────────────── */

/**
 * Maps the asset into domain types.
 *
 * Every reference is resolved through the enum's `fromKey`, so an entry naming
 * a compound the app has no model for is dropped and reported rather than
 * silently reinterpreted: a missing enum member is a compile error in the
 * model, and a missing asset key is a data error, and neither may turn into a
 * score of zero presented to the user as a real result.
 */
internal fun EntourageBible.toContent(): EntourageContent {
    val unresolved = mutableListOf<String>()

    fun <T> resolve(
        key: String,
        values: List<String>,
        fromKey: (String) -> T?,
        collection: String
    ): Set<T> = values.mapNotNull { raw ->
        fromKey(raw) ?: run {
            unresolved += "$collection.$key -> $raw"
            null
        }
    }.toSet()

    val synergies = synergies.mapNotNull { asset ->
        val cannabinoids = resolve(asset.id, asset.cannabinoids, Cannabinoid::fromKey, "synergies")
        val terpenes = resolve(asset.id, asset.terpenes, EntourageTerpene::fromKey, "synergies")
        if (cannabinoids.isEmpty() || terpenes.isEmpty()) {
            unresolved += "synergies.${asset.id} -> no resolvable cannabinoid or terpene"
            null
        } else {
            EntourageSynergy(
                id = asset.id,
                cannabinoids = cannabinoids,
                terpenes = terpenes,
                profiles = resolve(asset.id, asset.profiles, PharmacologicalProfile::fromKey, "synergies"),
                outcomeEs = asset.outcomeEs,
                descriptionEs = asset.descriptionEs,
                mechanismEs = asset.mechanismEs,
                evidenceEs = asset.evidenceEs,
                strainsEs = asset.strainsEs,
                interactionEs = asset.interactionEs
            )
        }
    }

    val profiles = profiles.mapNotNull { asset ->
        val key = PharmacologicalProfile.fromKey(asset.key)
        if (key == null) {
            unresolved += "profiles.${asset.key} -> unknown profile key"
            null
        } else {
            val weights = asset.cannabinoids.mapNotNull { entry ->
                Cannabinoid.fromKey(entry.cannabinoid)?.let { it to entry.weight }
                    ?: run { unresolved += "profiles.${asset.key} -> ${entry.cannabinoid}"; null }
            }.toMap()
            val shares = asset.terpenes.mapNotNull { entry ->
                EntourageTerpene.fromKey(entry.terpene)?.let { it to entry.share }
                    ?: run { unresolved += "profiles.${asset.key} -> ${entry.terpene}"; null }
            }.toMap()
            EntourageProfile(
                key = key,
                labelEs = asset.labelEs,
                descriptionEs = asset.descriptionEs,
                cannabinoidWeights = weights,
                terpeneShares = shares,
                noteEs = asset.noteEs
            )
        }
    }

    val vaporisation = vaporisation.mapNotNull { asset ->
        val terpene = EntourageTerpene.fromKey(asset.terpene)
        if (terpene == null) {
            unresolved += "vaporisation.${asset.terpene} -> unknown terpene"
            null
        } else {
            // TerpeneVaporisation's own guard rejects a window that cannot
            // contain the boiling point, so a typo here fails loudly at parse
            // time instead of teaching the wrong temperature.
            runCatching {
                TerpeneVaporisation(
                    terpene = terpene,
                    boilingPointC = asset.boilingPointC,
                    minTempC = asset.minTempC,
                    maxTempC = asset.maxTempC,
                    noteEs = asset.noteEs
                )
            }.getOrElse {
                unresolved += "vaporisation.${asset.terpene} -> $it"
                null
            }
        }
    }

    val cases = cases.mapNotNull { asset ->
        val goal = PharmacologicalProfile.fromKey(asset.goalProfile)
        if (goal == null) {
            unresolved += "cases.${asset.id} -> ${asset.goalProfile}"
            null
        } else {
            val maxShare = asset.maxCannabinoidShare.mapNotNull { (raw, value) ->
                Cannabinoid.fromKey(raw)?.let { it to value }
                    ?: run { unresolved += "cases.${asset.id}.maxCannabinoidShare -> $raw"; null }
            }.toMap()
            val ceilings = asset.sideEffectCeilings.mapNotNull { (raw, value) ->
                LabAxis.fromKey(raw)?.let { it to value }
                    ?: run { unresolved += "cases.${asset.id}.sideEffectCeilings -> $raw"; null }
            }.toMap()
            EntourageCase(
                id = asset.id,
                titleEs = asset.titleEs,
                briefEs = asset.briefEs,
                goal = goal,
                forbiddenCannabinoids = resolve(
                    asset.id,
                    asset.forbiddenCannabinoids,
                    Cannabinoid::fromKey,
                    "cases"
                ),
                maxCannabinoidShare = maxShare,
                ceilings = ceilings,
                explanationEs = asset.explanationEs
            )
        }
    }

    val questions = quiz
        .filter { it.optionsEs.isNotEmpty() && it.correctIndex in it.optionsEs.indices }
        .map { asset ->
            EntourageQuizQuestion(
                id = asset.id,
                promptEs = asset.promptEs,
                optionsEs = asset.optionsEs,
                correctIndex = asset.correctIndex,
                explanationEs = asset.explanationEs
            )
        }
    quiz.filter { it.optionsEs.isEmpty() || it.correctIndex !in it.optionsEs.indices }
        .forEach { unresolved += "quiz.${it.id} -> unanswerable" }

    // F3. Three separate ways this can drop a row, and all three are recorded
    // rather than defaulted:
    //
    // - an unknown terpene key, the same as every other collection;
    // - an unknown lever kind or evidence level, so a typo cannot render a
    //   label with nothing behind it;
    // - **a lever whose `basis_es` is blank**. This is the one that matters for
    //   the honesty standard: an agronomy claim without its evidence level is
    //   exactly what this module exists to not ship, so it is dropped instead of
    //   shown unqualified. The `AGRONOMY_BASIS_REQUIRED_ES` line is what makes
    //   that decision legible in the integrity notice when it happens.
    val agronomy = agronomy.mapNotNull { asset ->
        val terpene = EntourageTerpene.fromKey(asset.terpene)
        if (terpene == null) {
            unresolved += "agronomy.${asset.terpene} -> unknown terpene"
            null
        } else {
            val levers = asset.levers.mapNotNull { leverAsset ->
                val kind = AgronomyLeverKind.entries
                    .firstOrNull { it.key == leverAsset.kind.trim().uppercase() }
                val evidence = AgronomyEvidence.entries
                    .firstOrNull { it.key == leverAsset.evidence.trim().uppercase() }
                when {
                    kind == null -> {
                        unresolved += "agronomy.${terpene.key}.levers -> ${leverAsset.kind}"
                        null
                    }
                    evidence == null -> {
                        unresolved += "agronomy.${terpene.key}.${kind.key} -> ${leverAsset.evidence}"
                        null
                    }
                    leverAsset.basisEs.isBlank() -> {
                        unresolved += "agronomy.${terpene.key}.${kind.key} -> $AGRONOMY_BASIS_REQUIRED_ES"
                        null
                    }
                    leverAsset.detailEs.isBlank() -> {
                        unresolved += "agronomy.${terpene.key}.${kind.key} -> no detail declared"
                        null
                    }
                    else -> AgronomyLever(
                        kind = kind,
                        detailEs = leverAsset.detailEs,
                        evidence = evidence,
                        basisEs = leverAsset.basisEs
                    )
                }
            }
            if (asset.responseEs.isBlank()) {
                unresolved += "agronomy.${terpene.key} -> no response declared"
                null
            } else {
                EntourageAgronomy(
                    terpene = terpene,
                    responseEs = asset.responseEs,
                    levers = levers
                )
            }
        }
    }

    // F4. Seven ways this can drop a row, and all seven are recorded rather than
    // defaulted:
    //
    // - an unknown terpene key, the same as every other collection;
    // - an unknown method key, so a typo cannot render a method label with
    //   nothing behind it — and, more importantly, cannot turn a solvent-based
    //   method into one that silently reads as solvent-free;
    // - an unknown preservation factor key;
    // - an unknown evidence level, at the entry or at the note;
    // - a **blank entry-level `basis_es`**, which drops the whole row: the
    //   compound's own summary claim has nowhere to state what it cannot
    //   establish;
    // - a blank note-level `basis_es` or `detail_es`, which drops the note;
    // - a blank `response_es`.
    //
    // What is deliberately **not** droppable is the safety framing. It is not
    // asset content: `ProcessingMethodGuide.safetyEs` is a required constructor
    // argument in `model/`, so a missing key here costs a note and can never cost
    // the residual-solvent sentence.
    val processing = processing.mapNotNull { asset ->
        val terpene = EntourageTerpene.fromKey(asset.terpene)
        if (terpene == null) {
            unresolved += "processing.${asset.terpene} -> unknown terpene"
            null
        } else {
            fun evidenceOf(raw: String): ProcessingEvidence? =
                AgronomyEvidence.entries.firstOrNull { it.key == raw.trim().uppercase() }

            val methods = asset.methods.mapNotNull { noteAsset ->
                val method = ProcessingMethod.entries
                    .firstOrNull { it.key == noteAsset.method.trim().uppercase() }
                val evidence = evidenceOf(noteAsset.evidence)
                when {
                    method == null -> {
                        unresolved += "processing.${terpene.key}.methods -> ${noteAsset.method}"
                        null
                    }
                    evidence == null -> {
                        unresolved += "processing.${terpene.key}.${method.key} -> ${noteAsset.evidence}"
                        null
                    }
                    noteAsset.basisEs.isBlank() -> {
                        unresolved += "processing.${terpene.key}.${method.key} -> $PROCESSING_BASIS_REQUIRED_ES"
                        null
                    }
                    noteAsset.detailEs.isBlank() -> {
                        unresolved += "processing.${terpene.key}.${method.key} -> no detail declared"
                        null
                    }
                    else -> ProcessingMethodNote(
                        method = method,
                        detailEs = noteAsset.detailEs,
                        evidence = evidence,
                        basisEs = noteAsset.basisEs
                    )
                }
            }
            val preservation = asset.preservation.mapNotNull { noteAsset ->
                val factor = PreservationFactorKind.entries
                    .firstOrNull { it.key == noteAsset.factor.trim().uppercase() }
                val evidence = evidenceOf(noteAsset.evidence)
                when {
                    factor == null -> {
                        unresolved += "processing.${terpene.key}.preservation -> ${noteAsset.factor}"
                        null
                    }
                    evidence == null -> {
                        unresolved += "processing.${terpene.key}.${factor.key} -> ${noteAsset.evidence}"
                        null
                    }
                    noteAsset.basisEs.isBlank() -> {
                        unresolved += "processing.${terpene.key}.${factor.key} -> $PROCESSING_BASIS_REQUIRED_ES"
                        null
                    }
                    noteAsset.detailEs.isBlank() -> {
                        unresolved += "processing.${terpene.key}.${factor.key} -> no detail declared"
                        null
                    }
                    else -> PreservationFactorNote(
                        factor = factor,
                        detailEs = noteAsset.detailEs,
                        evidence = evidence,
                        basisEs = noteAsset.basisEs
                    )
                }
            }
            val evidence = evidenceOf(asset.evidence)
            when {
                asset.responseEs.isBlank() -> {
                    unresolved += "processing.${terpene.key} -> no response declared"
                    null
                }
                evidence == null -> {
                    unresolved += "processing.${terpene.key} -> ${asset.evidence}"
                    null
                }
                asset.basisEs.isBlank() -> {
                    unresolved += "processing.${terpene.key} -> $PROCESSING_ENTRY_BASIS_REQUIRED_ES"
                    null
                }
                else -> EntourageProcessing(
                    terpene = terpene,
                    responseEs = asset.responseEs,
                    evidence = evidence,
                    basisEs = asset.basisEs,
                    methods = methods,
                    preservation = preservation
                )
            }
        }
    }

    return EntourageContent(
        disclaimerEs = disclaimerEs,
        synergies = synergies,
        profiles = profiles,
        vaporisation = vaporisation,
        cases = cases,
        questions = questions,
        agronomy = agronomy,
        processing = processing,
        unresolvedReferences = unresolved
    )
}

/**
 * Why an agronomy lever was dropped, recorded in `unresolvedReferences`.
 *
 * A lever without this sentence would reach the screen as a bare instruction
 * with no evidence level attached — which is the one thing this module's card
 * rules exist to prevent.
 */
private const val AGRONOMY_BASIS_REQUIRED_ES =
    "the lever ships no basis_es and was dropped rather than shown unqualified"

/**
 * Why a processing note was dropped, recorded in `unresolvedReferences`.
 *
 * Same rule F3 applied to a lever, for the same reason: a processing claim whose
 * evidence level is missing would reach the screen as a bare description of what
 * a method does to a harvest.
 */
private const val PROCESSING_BASIS_REQUIRED_ES =
    "the note ships no basis_es and was dropped rather than shown unqualified"

/**
 * Why a whole processing row was dropped, recorded in `unresolvedReferences`.
 *
 * A row missing its entry-level basis is worse than a missing row: the
 * compound's own summary sentence would still be on screen with nothing behind
 * it, so the row goes rather than degrading to an unqualified claim.
 */
private const val PROCESSING_ENTRY_BASIS_REQUIRED_ES =
    "the row ships no entry basis_es and was dropped rather than shown unqualified"

/**
 * The `Achievement` row for an entourage achievement.
 *
 * The module has no XP table of its own: it writes into the existing
 * `achievements` table, whose `xpReward` the app already sums into the
 * player's total. A second progression system here would be a second source of
 * truth for the same number.
 *
 * [rounds] is the run the row is written for, because the badge's own text
 * states how many correct answers a run needs and that number is a fraction of
 * the rounds. Writing the row from the shipped-quiz default instead would put a
 * claim in the achievements table that the player's run may not match.
 *
 * This overload exists for [EntourageAchievement.ENTOURAGE_MASTER] alone, and it
 * does not compile for [EntourageAchievement.RESIN_ENGINEER]: that badge's text
 * is derived from the shipped processing block rather than from a round count, so
 * a caller holding it has to be handed the catalog's compound count explicitly
 * ([EntourageAchievement.RESIN_ENGINEER.descriptionForProcessing]).
 * Making the two
 * incompatible is the point — a shared signature would let the resin row be
 * written from the wrong data and nobody would notice until a player read a
 * sentence that disagreed with the content they had read.
 *
 * ## Why `IllegalStateException` and not `error(...)`
 *
 * Both branches are unreachable today, and the owner settled which one this is
 * when they saw it: a **thrown exception**, not an `error()` sentinel. `error()`
 * on a live path means a crash when the user next claims a badge, and the
 * `AchievementDao` has no unique index on `name`, so a client that caught it and
 * carried on could insert the quiz sentence into the resin row and write a
 * 300 XP row that reads wrong forever. A crash costs one session; a swallowed
 * `error()` costs permanent data. Revert to `error(...)` if a test ever needs to
 * assert this branch rather than only document it.
 */
internal fun EntourageAchievement.toAchievementRow(rounds: Int): Achievement =
    when (this) {
        EntourageAchievement.ENTOURAGE_MASTER -> Achievement(
            name = labelEs,
            description = descriptionFor(rounds),
            icon = icon,
            xpReward = xpReward,
            isUnlocked = false
        )
        EntourageAchievement.RESIN_ENGINEER -> throw IllegalStateException(
            "the resin engineer's text is derived from the shipped processing " +
                "block, not from a round count: write it through " +
                "EntourageReward.toAchievementRow() with a description built " +
                "from EntourageAchievement.descriptionForProcessing(documented)"
        )
    }
