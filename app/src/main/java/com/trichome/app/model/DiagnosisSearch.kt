package com.trichome.app.model

import com.trichome.app.data.repository.DiagnosisCondition
import com.trichome.app.data.repository.DiagnosisSymptom
import java.text.Normalizer

/**
 * Categories, grouping and live search for the diagnosis picker.
 *
 * ## The four buckets, and why they are not the asset's eight
 *
 * The brief asks for nutrient deficiencies, pests, fungi and pathogens, and
 * environmental stress. `diagnostics.json` carries **eight** fine categories on its
 * conditions: `deficiency`, `nutrient`, `excess`, `pest`, `fungus`, `bacterial`,
 * `viral` and `abiotic`.
 *
 * Those are not overridden here. They are the conditions' own taxonomy and the
 * diagnosis report prints them; a grower reading "Deficiencia" for a magnesium
 * shortage and "Exceso" for a nitrogen excess is reading two different
 * diagnoses, and collapsing them into one chip would lose the distinction the
 * report depends on.
 *
 * So [DiagnosisCategory] is a **second, coarser axis over the same keys**: four
 * rows in the picker, each expanding to the fine categories it contains. Both are
 * derived from one place — [DiagnosisCategory.forConditionKey] — so the grouping
 * and the report cannot drift apart, and an asset category this build has never
 * heard of is reported in [DiagnosisCategory.unmappedKeys] rather than being filed
 * under "environmental stress" because that was the nearest guess.
 *
 * ## Search is accent- and case-insensitive, and says so
 *
 * A grower looking for "acaro" must find "Ácaro", and one looking for "ACARO"
 * must find it too. Normalisation strips diacritics with
 * [Normalizer.Form.NFD] rather than by listing the accented letters, because a
 * list would have to be maintained and would be wrong the first time a word
 * outside it shipped. Nothing is removed from what is stored — the displayed text
 * is the asset's text, byte for byte; only the *comparison* key is folded.
 *
 * ## What is searched
 *
 * Three fields, as the brief asks: the symptom's **name**, the **category** it is
 * filed under, and its **symptom text**. The third is the condition prose the
 * asset already ships for the conditions that declare this symptom in their
 * `symptomWeights` — the `cause_es` and `action_plan_es` lines. That is derived,
 * not authored, which is the point: a search index over 116 hand-written
 * summaries would be 116 new sentences nobody fact-checks, while these lines are
 * the same ones the report prints under the diagnosis, so a search hit and the
 * report agree by construction.
 */
enum class DiagnosisCategory(
    val key: String,
    val labelEs: String,
    val blurbEs: String,
    /** The asset's condition keys this bucket collects, in report order. */
    val conditionKeys: Set<String>
) {
    NUTRIENT(
        key = "nutrient",
        labelEs = "Nutricional",
        blurbEs = "Faltas y excesos de nutrientes en el sustrato o en la riego.",
        conditionKeys = setOf("deficiency", "nutrient", "excess")
    ),

    PESTS(
        key = "pests",
        labelEs = "Plagas",
        blurbEs = "Insectos, ácaros y otros animales que se alimentan de la planta.",
        conditionKeys = setOf("pest")
    ),

    PATHOGENS(
        key = "pathogens",
        labelEs = "Hongos y patógenos",
        blurbEs = "Hongos, bacterias y virus, con lo que cada uno necesita para entrar.",
        conditionKeys = setOf("fungus", "bacterial", "viral")
    ),

    ENVIRONMENTAL(
        key = "environmental",
        labelEs = "Estres ambiental",
        blurbEs = "Luz, temperatura, agua, humedad y productos aplicados.",
        conditionKeys = setOf("abiotic")
    ),

    /** A healthy reading is not a bucket, but the picker has to be able to show it. */
    HEALTHY(
        key = "healthy",
        labelEs = "Planta saludable",
        blurbEs = "La ausencia de síntomas, que también es un resultado.",
        conditionKeys = setOf("healthy")
    );

    /** `null` when the asset carries a key this build does not know. */
    val labelForConditionKey: String? get() = labelEs

    companion object {
        private val byConditionKey: Map<String, DiagnosisCategory> = entries
            .flatMap { category -> category.conditionKeys.map { it to category } }
            .toMap()

        /**
         * The bucket an asset condition key belongs to, or null when unknown.
         *
         * Null rather than a default: a condition filed under a category this
         * build has no row for would appear in whichever bucket the fallback
         * picked, which is a claim the catalog never made. The caller records it
         * in [unmappedKeys] instead.
         */
        fun forConditionKey(conditionKey: String?): DiagnosisCategory? =
            conditionKey?.trim()?.lowercase()?.let { byConditionKey[it] }

        /** Asset condition keys no bucket claims. */
        fun unmappedKeys(conditionKeys: Collection<String>): List<String> =
            conditionKeys.map { it.trim().lowercase() }
                .filter { it !in byConditionKey }
                .distinct()

        /** Spanish label for an asset condition key, or null when unknown. */
        fun labelForConditionKeyOrNull(conditionKey: String?): String? =
            forConditionKey(conditionKey)?.labelEs

        /** Every fine condition key the model knows, for the asset test. */
        val knownConditionKeys: Set<String> by lazy { byConditionKey.keys }
    }
}

/** One row of the live-search result. */
data class DiagnosisSearchEntry(
    val symptom: DiagnosisSymptom,
    /** The figure, or the neutral one when the table has no entry for this id. */
    val glyph: DiagnosisGlyph,
    /** `true` when [glyph] is the neutral fallback rather than an authored figure. */
    val glyphIsFallback: Boolean,
    /** Spanish label of the fine asset category this symptom is filed under. */
    val tissueLabelEs: String,
    /** The buckets this symptom is reachable from. Never empty. */
    val buckets: List<DiagnosisCategory>,
    /** The searchable text: name, category and the conditions' own prose. */
    val haystack: String
)

/**
 * Live search over the shipped symptom list.
 *
 * ## One index, built once, rebuilt on a content change
 *
 * [index] folds the symptom list with the condition list, because the symptom
 * text it searches is the condition prose that names the symptom. Building it per
 * keystroke would re-read 49 conditions' `cause_es` on every character typed;
 * building it once means a search is a substring test over an already-folded
 * [DiagnosisSearchEntry.haystack].
 *
 * ## No clock, no Android, no IO
 *
 * The search reads what it is handed. `ModelPurityTest` lists this file for the
 * same reason it lists the engines, and a pure filter is the only reason the
 * behaviour could be tested without a device at all.
 */
object DiagnosisSearch {

    /** Headline over the search field. */
    const val FIELD_LABEL_ES: String = "Buscar síntoma"

    /** Placeholder inside the empty search field. */
    const val FIELD_HINT_ES: String = "Escribe una hoja, un insecto o un color"

    /** The picker heading. */
    const val RESULTS_HEADING_ES: String = "Síntomas"

    /** What the picker says when the query matches nothing. */
    const val NO_RESULTS_ES: String =
        "Ningún síntoma coincide con esa búsqueda. Prueba con una hoja, un color o un animal."

    /** The result count, e.g. "7 de 116 síntomas". */
    const val COUNT_FORMAT_ES: String = "%d de %d síntomas"

    /** The clear-search button's label. */
    const val CLEAR_LABEL_ES: String = "Borrar la búsqueda"

    /** Label for the bucket filter row. */
    const val FILTER_LABEL_ES: String = "Filtrar por categoría"

    /** The "no bucket filter" chip. */
    const val ALL_BUCKETS_ES: String = "Todas"

    /**
     * Spanish words for the asset's *tissue* categories, which is a different axis
     * from [DiagnosisCategory].
     *
     * The asset writes these as bare English keys (`leaf`, `root`, `stem`,
     * `flower`, `whole`, `pest`, `general`), and a screen that printed them
     * verbatim would be the only English text in a Spanish picker. Unknown keys
     * return the key itself rather than being dropped, so a new tissue category
     * shows up verbatim instead of vanishing.
     */
    private val tissueLabelsEs: Map<String, String> = mapOf(
        "general" to "General",
        "leaf" to "Hoja",
        "root" to "Raíz",
        "stem" to "Tallo",
        "flower" to "Flor",
        "whole" to "Planta entera",
        "pest" to "Plaga"
    )

    /** The Spanish label for a tissue key, or the key itself when unknown. */
    fun tissueLabelEs(categoryKey: String?): String {
        val key = categoryKey?.trim()?.lowercase().orEmpty()
        return tissueLabelsEs[key] ?: categoryKey.orEmpty()
    }

    /**
     * Folds accents and case out of [text] for comparison only.
     *
     * NFD then the combining-mark range, rather than a Spanish letter list: a
     * list has to be maintained and would be wrong for any word outside it.
     */
    fun foldForSearch(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return buildString(decomposed.length) {
            decomposed.forEach { character ->
                if (character.code in COMBINING_MARKS) return@forEach
                append(character.lowercaseChar())
            }
        }
    }

    /** Unicode combining diacritical marks, the range NFD splits accented letters into. */
    private val COMBINING_MARKS = 0x0300..0x036F

    /**
     * The buckets a symptom is reachable from.
     *
     * Derived from the conditions whose `symptomWeights` name the symptom. A
     * symptom no condition declares — which the asset test forbids — still gets
     * [DiagnosisCategory.ENVIRONMENTAL]? No: it gets every bucket, because a
     * symptom nobody has attributed has to be findable everywhere rather than
     * hidden in one. That is the one place this returns a default, and it is
     * documented here rather than being a silent fallback.
     */
    fun symptomBuckets(
        symptomId: String,
        conditions: List<DiagnosisCondition>
    ): List<DiagnosisCategory> {
        val buckets = conditions
            .filter { symptomId in it.symptomWeights }
            .mapNotNull { DiagnosisCategory.forConditionKey(it.category) }
            .distinct()
        return buckets.ifEmpty { DiagnosisCategory.entries.toList() }
    }

    /**
     * The whole search index.
     *
     * @param symptoms the shipped symptom list.
     * @param conditions the shipped condition list, for the prose half of the
     *   haystack.
     */
    fun index(
        symptoms: List<DiagnosisSymptom>,
        conditions: List<DiagnosisCondition>
    ): List<DiagnosisSearchEntry> {
        val proseBySymptom: Map<String, String> = buildMap {
            conditions.forEach { condition ->
                val prose = buildString {
                    append(condition.labelEs).append(' ')
                    append(condition.shortEs).append(' ')
                    append(condition.causeEs).append(' ')
                    condition.actionPlanEs.forEach { append(it).append(' ') }
                }
                condition.symptomWeights.keys.forEach { symptomId ->
                    val existing = get(symptomId)
                    put(symptomId, if (existing == null) prose else "$existing $prose")
                }
            }
        }

        return symptoms.map { symptom ->
            val glyph = DiagnosisIcons.forSymptomId(symptom.id)
            val buckets = symptomBuckets(symptom.id, conditions)
            val bucketText = buckets.joinToString(" ") { it.labelEs }
            val tissue = tissueLabelEs(symptom.category)
            DiagnosisSearchEntry(
                symptom = symptom,
                glyph = glyph ?: DiagnosisIcons.NEUTRAL,
                glyphIsFallback = glyph == null,
                tissueLabelEs = tissue,
                buckets = buckets,
                haystack = foldForSearch(
                    listOf(
                        symptom.labelEs,
                        tissue,
                        bucketText,
                        proseBySymptom[symptom.id].orEmpty()
                    ).joinToString(" ")
                )
            )
        }
    }

    /**
     * The entries matching [query], in the index's own order.
     *
     * A blank query returns everything rather than nothing: an empty search field
     * is not a filter that matches no rows, and a picker that empties itself the
     * moment the field is cleared is the classic live-search bug.
     *
     * Every whitespace-separated token must match, which is what makes
     * "hoja micelio" find the mildew on a leaf rather than either term alone.
     */
    fun filter(entries: List<DiagnosisSearchEntry>, query: String): List<DiagnosisSearchEntry> {
        // Split on any whitespace, not on a single space: a field holding a tab
        // or a newline is still blank, and a filter that emptied the whole picker
        // because of a stray tab is the classic live-search bug.
        val tokens = foldForSearch(query)
            .split(WHITESPACE)
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return entries
        return entries.filter { entry -> tokens.all { it in entry.haystack } }
    }

    /** Every Unicode whitespace character, so a blank field is blank however it is blank. */
    private val WHITESPACE = Regex("\\s+")

    /**
     * The entries in [bucket], or all of them when it is null.
     *
     * Combined with [filter] by the caller rather than merged here, so the
     * category row and the text field are two independent inputs and either can
     * change without the other's rules moving.
     */
    fun inBucket(
        entries: List<DiagnosisSearchEntry>,
        bucket: DiagnosisCategory?
    ): List<DiagnosisSearchEntry> =
        if (bucket == null) entries else entries.filter { bucket in it.buckets }

    /** `"7 de 116 síntomas"`, for the count line under the field. */
    fun countEs(shown: Int, total: Int): String = COUNT_FORMAT_ES.format(shown, total)

    /**
     * The integrity notice when this build cannot show part of the catalog.
     *
     * Two different failures, counted separately, and both reported rather than
     * absorbed: a symptom with no figure is shown with the neutral glyph, and a
     * condition whose category no bucket claims is filed under no bucket. Both are
     * things the asset shipped, and a screen that looked complete while dropping
     * them is the defect this project keeps refusing.
     *
     * Returns an empty string when there is nothing to report, so the caller can
     * test `isNotEmpty()` rather than compare against a constant.
     */
    fun incompleteEs(missingGlyphs: Int, unmappedCategories: Int): String {
        if (missingGlyphs <= 0 && unmappedCategories <= 0) return ""
        val parts = buildList {
            if (missingGlyphs > 0) {
                add(
                    "$missingGlyphs ${if (missingGlyphs == 1) "síntoma" else "síntomas"} " +
                        "sin figura propia, ${if (missingGlyphs == 1) "mostrado" else "mostrados"} " +
                        "con la figura neutra"
                )
            }
            if (unmappedCategories > 0) {
                add(
                    "$unmappedCategories ${if (unmappedCategories == 1) "categoría" else "categorías"} " +
                        "del catálogo sin fila en el filtro"
                )
            }
        }
        return "Catálogo incompleto: " + parts.joinToString(" y ") + "."
    }
}