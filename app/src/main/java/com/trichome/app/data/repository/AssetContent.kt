package com.trichome.app.data.repository

import android.content.Context
import com.trichome.app.model.BoilingPointParser
import com.trichome.app.model.CatalogVolatilityRow
import com.trichome.app.model.TerpeneFamily
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/* ── Terpenes library ─────────────────────────────────────────────────── */

/**
 * A terpene entry in the encyclopedia.
 *
 * The v1 shape (id/name/aroma/effects/strains/boilingPoint) is preserved with
 * defaults, so an older `terpenes.json` still parses; the added fields carry
 * the scientific and pharmacological depth.
 */
@Serializable
data class Terpene(
    val id: String = "",
    val name: String = "",
    val formula: String = "",
    @SerialName("molarMass") val molarMass: String = "",
    /** Monoterpeno / Sesquiterpeno / Diterpeno / Triterpeno. */
    val family: String = "",
    val aroma: String = "",
    val taste: String = "",
    val effects: List<String> = emptyList(),
    @SerialName("medicalProperties") val medicalProperties: List<String> = emptyList(),
    /** Pharmacological mechanism of action. */
    val mechanism: String = "",
    val biosynthesis: String = "",
    val toxicity: String = "",
    /** Ids of other terpenes in this catalog that act as entourage partners. */
    @SerialName("pairsWith") val pairsWith: List<String> = emptyList(),
    val strains: List<String> = emptyList(),
    /** Non-cannabis plants where this compound is also abundant. */
    @SerialName("foundIn") val foundIn: List<String> = emptyList(),
    /** How strongly it accumulates in cannabis: muy alto / alto / medio / bajo. */
    val richness: String = "",
    @SerialName("boilingPoint") val boilingPoint: String = "",
    @SerialName("isFavorite") val isFavorite: Boolean = false
) {
    /** Boiling point in °C parsed from the display string, for the chart.
     *
     * Delegates to [BoilingPointParser] rather than filtering digits inline, so
     * there is one reading of this column in the app. The inline version it
     * replaced turned `"155-156 °C"` into `155156` without complaining, which is
     * a plausible integer that is not a temperature; the parser takes the lowest
     * number instead, and `null` means the string carries no temperature at all.
     */
    val boilingPointCelsius: Int? get() = BoilingPointParser.parseCelsius(boilingPoint)

    /** Coarse aroma family used by the multi-criteria filter. */
    val aromaFamily: String
        get() {
            val haystack = "$aroma $taste".lowercase()
            return when {
                listOf("cítric", "citric", "limón", "limon", "naranja", "pomelo", "fresco").any { it in haystack } -> "Cítrico"
                listOf("floral", "lavanda", "rosa", "jazmín", "jasmin", "orquídea").any { it in haystack } -> "Floral"
                listOf("picante", "pimienta", "clavo", "especiado", "canela").any { it in haystack } -> "Picante"
                listOf("terroso", "tierra", "húmedo", "humedo", "bosque", "musgo", "madera").any { it in haystack } -> "Terroso"
                listOf("dulce", "caramelo", "miel", "vainilla", "fruta").any { it in haystack } -> "Dulce"
                listOf("herbal", "hierba", "menta", "eucalipto", "mentolado").any { it in haystack } -> "Herbal"
                listOf("resinoso", "resina", "cera", "cándido", "candido").any { it in haystack } -> "Resinoso"
                else -> "Herbal"
            }
        }

    /** Coarse effect grouping used by the effect filter. */
    val effectGroup: String
        get() {
            val haystack = (effects + medicalProperties).joinToString(" ").lowercase()
            return when {
                listOf("analgés", "analges", "dolor", "antiinflam", "articular").any { it in haystack } -> "Alivio del dolor"
                listOf("enfoque", "concentr", "claridad", "estimul", "energía", "energia").any { it in haystack } -> "Enfoque"
                listOf("creativ", "inspir").any { it in haystack } -> "Creatividad"
                listOf("eufóri", "eufori", "ánimo", "animo", "antidepres").any { it in haystack } -> "Euforia"
                listOf("relaj", "sedante", "calmante", "sueño", "sueno", "ansiol").any { it in haystack } -> "Relajación"
                else -> "Equilibrio"
            }
        }
}

@Serializable
internal data class TerpeneCatalog(
    val version: Int = 1,
    val terpenes: List<Terpene> = emptyList()
)

/**
 * Loads the terpenes bible from `assets/data/terpenes.json`.
 *
 * The catalog is parsed once and cached for the process lifetime; with 150+
 * entries re-parsing on every keystroke of the search box was the bottleneck.
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

    /** Single lookup by id, for the detail screen. */
    suspend fun getTerpene(id: String): Terpene? =
        getTerpenes().firstOrNull { it.id == id }

    /** Resolves the ids in [pairsWith] to full entries. */
    suspend fun resolve(list: List<String>): List<Terpene> {
        if (list.isEmpty()) return emptyList()
        val byId = getTerpenes().associateBy { it.id }
        return list.mapNotNull { byId[it] }
    }

    suspend fun getFavorites(): List<Terpene> = getTerpenes().filter { it.isFavorite }

    suspend fun toggleFavorite(terpene: Terpene) {
        if (!favorites.add(terpene.id)) favorites.remove(terpene.id)
    }

    /** Distinct chemical families present in the catalog, for the filter chips. */
    suspend fun families(): List<String> =
        getTerpenes().map { it.family }.filter { it.isNotBlank() }.distinct().sorted()

    /**
     * Free-text search across every searchable field.
     *
     * An empty or whitespace-only query returns the whole catalog rather than an
     * empty list, which is what the screen expects.
     */
    suspend fun search(query: String): List<Terpene> {
        val q = query.trim()
        if (q.isEmpty()) return getTerpenes()
        val needle = q.lowercase()
        return getTerpenes().filter { t ->
            t.name.lowercase().contains(needle) ||
                t.aroma.lowercase().contains(needle) ||
                t.taste.lowercase().contains(needle) ||
                t.family.lowercase().contains(needle) ||
                t.formula.lowercase().contains(needle) ||
                t.mechanism.lowercase().contains(needle) ||
                t.effects.any { it.lowercase().contains(needle) } ||
                t.medicalProperties.any { it.lowercase().contains(needle) } ||
                t.strains.any { it.lowercase().contains(needle) } ||
                t.foundIn.any { it.lowercase().contains(needle) }
        }
    }

    /**
     * The catalog reduced to what [com.trichome.app.model.TerpeneVolatilityIndex]
     * needs, so the volatility model never has to know about this class.
     *
     * [Terpene] lives in a file that imports `android.content.Context`, and the
     * derivation has to stay assertable from the JVM with no Android on the
     * classpath. One mapping here, rather than a `Terpene` parameter on a pure
     * function.
     */
    suspend fun volatilityRows(): List<CatalogVolatilityRow> =
        getTerpenes().map { entry ->
            CatalogVolatilityRow(
                catalogId = entry.id,
                labelEs = entry.name,
                family = TerpeneFamily.fromFamilyEs(entry.family),
                molarMassEs = entry.molarMass,
                boilingPointEs = entry.boilingPoint
            )
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
    val icon: String = "🟢",
    val category: String = "general"
)

/**
 * Photographic evidence thresholds for a condition.
 *
 * Each `*Min` is a lower bound the measurement must clear for the condition to
 * be plausible; `greenMax` is an upper bound (the condition needs tissue to
 * *lack*). Absent keys are simply not evaluated, so a condition can be
 * described with only the evidence that actually distinguishes it.
 */
@Serializable
data class PhotoEvidence(
    @SerialName("chlorosisMin") val chlorosisMin: Float? = null,
    @SerialName("necrosisMin") val necrosisMin: Float? = null,
    @SerialName("spotDensityMin") val spotDensityMin: Float? = null,
    @SerialName("trichomeMin") val trichomeMin: Float? = null,
    @SerialName("webbingMin") val webbingMin: Float? = null,
    @SerialName("greenMin") val greenMin: Float? = null,
    @SerialName("greenMax") val greenMax: Float? = null,
    @SerialName("hueMin") val hueMin: Float? = null,
    @SerialName("hueMax") val hueMax: Float? = null,
    @SerialName("saturationMax") val saturationMax: Float? = null,
    @SerialName("valueMin") val valueMin: Float? = null,
    /** How much this evidence counts against the symptom score, 1..10. */
    val weight: Int = 1
) {
    val isEmpty: Boolean
        get() = chlorosisMin == null && necrosisMin == null && spotDensityMin == null &&
            trichomeMin == null && webbingMin == null && greenMin == null &&
            greenMax == null && hueMin == null && hueMax == null &&
            saturationMax == null && valueMin == null
}

@Serializable
data class DiagnosisCondition(
    val id: String = "",
    val category: String = "",
    @SerialName("label_es") val labelEs: String = "",
    @SerialName("short_es") val shortEs: String = "",
    val severity: String = "moderada",
    @SerialName("symptomWeights") val symptomWeights: Map<String, Int> = emptyMap(),
    @SerialName("photoEvidence") val photoEvidence: PhotoEvidence? = null,
    @SerialName("photoNotes_es") val photoNotesEs: String = "",
    @SerialName("cause_es") val causeEs: String = "",
    @SerialName("actionPlan_es") val actionPlanEs: List<String> = emptyList(),
    @SerialName("prevention_es") val preventionEs: List<String> = emptyList(),
    @SerialName("affectedPlants") val affectedPlants: List<String> = emptyList()
)

@Serializable
internal data class DiagnosisCatalog(
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

    /** Every condition, ordered by category then name, for the encyclopedia. */
    suspend fun getConditions(): List<DiagnosisCondition> =
        loadNow().conditions.sortedWith(compareBy({ it.category }, { it.labelEs }))

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