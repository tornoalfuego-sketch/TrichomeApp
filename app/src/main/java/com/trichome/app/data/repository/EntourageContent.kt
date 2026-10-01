package com.trichome.app.data.repository

import android.content.Context
import com.trichome.app.data.entity.Achievement
import com.trichome.app.model.Cannabinoid
import com.trichome.app.model.EntourageAchievement
import com.trichome.app.model.EntourageCase
import com.trichome.app.model.EntourageProfile
import com.trichome.app.model.EntourageQuizQuestion
import com.trichome.app.model.EntourageSynergy
import com.trichome.app.model.EntourageTerpene
import com.trichome.app.model.LabAxis
import com.trichome.app.model.PharmacologicalProfile
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
    val quiz: List<EntourageQuizQuestionAsset> = emptyList()
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
    /** Keys the enums do not know, as `"collection.id -> key"`. */
    val unresolvedReferences: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = synergies.isEmpty() && profiles.isEmpty()

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

    return EntourageContent(
        disclaimerEs = disclaimerEs,
        synergies = synergies,
        profiles = profiles,
        vaporisation = vaporisation,
        cases = cases,
        questions = questions,
        unresolvedReferences = unresolved
    )
}

/**
 * The `Achievement` row for an entourage achievement.
 *
 * The module has no XP table of its own: it writes into the existing
 * `achievements` table, whose `xpReward` the app already sums into the
 * player's total. A second progression system here would be a second source of
 * truth for the same number.
 */
internal fun EntourageAchievement.toAchievementRow(): Achievement = Achievement(
    name = labelEs,
    description = description,
    icon = icon,
    xpReward = xpReward,
    isUnlocked = false
)
