package com.trichome.app.data.repository

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/* ── Terpenes library ─────────────────────────────────────────────────── */

@Serializable
data class Terpene(
    val id: String = "",
    val name: String = "",
    val aroma: String = "",
    val effects: List<String> = emptyList(),
    val strains: List<String> = emptyList(),
    @SerialName("boilingPoint") val boilingPoint: String = "",
    @SerialName("isFavorite") val isFavorite: Boolean = false
)

@Serializable
private data class TerpeneCatalog(
    val version: Int = 1,
    val terpenes: List<Terpene> = emptyList()
)

/**
 * Loads the terpenes bible from `assets/data/terpenes.json`.
 */
class TerpenesRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private var cached: List<Terpene>? = null
    private var favorites: MutableSet<String> = mutableSetOf()

    suspend fun getTerpenes(): List<Terpene> {
        val list = cached ?: run {
            val raw = context.assets.open("data/terpenes.json").bufferedReader().use { it.readText() }
            val catalog = json.decodeFromString<TerpeneCatalog>(raw)
            cached = catalog.terpenes
            catalog.terpenes
        }
        return list.map { if (favorites.contains(it.id)) it.copy(isFavorite = true) else it }
    }

    suspend fun getFavorites(): List<Terpene> = getTerpenes().filter { it.isFavorite }

    suspend fun toggleFavorite(terpene: Terpene) {
        if (!favorites.add(terpene.id)) favorites.remove(terpene.id)
    }

    suspend fun search(query: String): List<Terpene> {
        if (query.isBlank()) return getTerpenes()
        return getTerpenes().filter {
            it.name.contains(query, ignoreCase = true) ||
                it.aroma.contains(query, ignoreCase = true) ||
                it.effects.any { e -> e.contains(query, ignoreCase = true) }
        }
    }
}

/* ── Breeding theory library ──────────────────────────────────────────── */

@Serializable
data class BreedingGeneration(
    val id: String = "",
    val label: String = "",
    @SerialName("title_es") val titleEs: String = "",
    @SerialName("description_es") val descriptionEs: String = "",
    @SerialName("steps_es") val stepsEs: List<String> = emptyList(),
    @SerialName("pros_es") val prosEs: List<String> = emptyList(),
    @SerialName("cons_es") val consEs: List<String> = emptyList()
)

@Serializable
data class BreedingMethod(
    val name: String = "",
    @SerialName("detail_es") val detailEs: String = ""
)

@Serializable
data class BreedingTechnique(
    val id: String = "",
    @SerialName("label_es") val labelEs: String = "",
    @SerialName("description_es") val descriptionEs: String = "",
    @SerialName("methods_es") val methodsEs: List<BreedingMethod> = emptyList()
)

@Serializable
data class BreedingTerm(
    val term: String = "",
    @SerialName("definition_es") val definitionEs: String = ""
)

@Serializable
private data class BreedingBible(
    val version: Int = 1,
    val generations: List<BreedingGeneration> = emptyList(),
    val techniques: List<BreedingTechnique> = emptyList(),
    val glossary: List<BreedingTerm> = emptyList()
)

class BreedingContentRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private var cached: BreedingBible? = null

    private fun load(): BreedingBible {
        return cached ?: run {
            val raw = context.assets.open("data/breeding.json").bufferedReader().use { it.readText() }
            val bible = json.decodeFromString<BreedingBible>(raw)
            cached = bible
            bible
        }
    }

    suspend fun getGenerations(): List<BreedingGeneration> = load().generations
    suspend fun getTechniques(): List<BreedingTechnique> = load().techniques
    suspend fun getGlossary(): List<BreedingTerm> = load().glossary
}

/* ── Diagnosis content (labels + action plans from assets) ─────────────── */

@Serializable
data class DiagnosisSymptom(
    val id: String = "",
    @SerialName("label_es") val labelEs: String = "",
    val icon: String = "🟢"
)

@Serializable
data class DiagnosisCondition(
    val id: String = "",
    val category: String = "",
    @SerialName("label_es") val labelEs: String = "",
    @SerialName("symptomWeights") val symptomWeights: Map<String, Int> = emptyMap(),
    @SerialName("cause_es") val causeEs: String = "",
    @SerialName("actionPlan_es") val actionPlanEs: List<String> = emptyList(),
    @SerialName("prevention_es") val preventionEs: List<String> = emptyList()
)

@Serializable
private data class DiagnosisCatalog(
    val version: Int = 1,
    val symptoms: List<DiagnosisSymptom> = emptyList(),
    val conditions: List<DiagnosisCondition> = emptyList()
)

class DiagnosisContentRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private var cached: DiagnosisCatalog? = null

    private fun loadNow(): DiagnosisCatalog {
        return cached ?: run {
            val raw = context.assets.open("data/diagnostics.json").bufferedReader().use { it.readText() }
            val catalog = json.decodeFromString<DiagnosisCatalog>(raw)
            cached = catalog
            catalog
        }
    }

    suspend fun getSymptoms(): List<DiagnosisSymptom> = loadNow().symptoms
    suspend fun getCondition(conditionId: String): DiagnosisCondition? =
        loadNow().conditions.firstOrNull { it.id == conditionId }

    suspend fun getConditionOrHealthy(conditionId: String): DiagnosisCondition =
        getCondition(conditionId) ?: DiagnosisCondition(
            id = "healthy",
            category = "healthy",
            labelEs = "Planta Saludable",
            causeEs = "No se detectaron anomalías visibles significativas.",
            actionPlanEs = listOf(
                "Continuar con el régimen de cuidado actual.",
                "Monitorear la planta regularmente.",
                "Mantener un diario de observaciones."
            ),
            preventionEs = emptyList()
        )
}