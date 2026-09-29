package com.trichome.app.data.repository

import android.content.Context
import com.trichome.app.R
import com.trichome.app.model.EventType
import com.trichome.app.ui.theme.ThemeIndex
import kotlin.math.roundToInt

/**
 * Pure, deterministic diagnosis rule engine (offline, no Android deps).
 *
 * A condition matches the currently visible symptoms with a weighted score.
 * Confidence derives from how specific the matched symptoms are relative to
 * the total weight the condition defines.
 *
 * The UI layer resolves human labels and the corrective action plan from
 * `assets/data/diagnostics.json`, keyed by [DiagnosisResult.condition].
 */
object DiagnosisEngine {

    data class Rule(
        val condition: String,
        val category: String,
        /** symptomId -> weight */
        val weights: Map<String, Int>
    )

    val RULES: List<Rule> = listOf(
        // ── Deficiencies ──────────────────────────────────────────────
        Rule("nitrogen_deficiency", "deficiency", mapOf(
            "yellowing_lower_leaves" to 3, "stunted_growth" to 2, "leaf_tip_burn" to 1
        )),
        Rule("phosphorus_deficiency", "deficiency", mapOf(
            "purple_stems" to 3, "dark_green_leaves" to 2, "stunted_root" to 2
        )),
        Rule("potassium_deficiency", "deficiency", mapOf(
            "brown_leaf_edges" to 3, "weak_stems" to 2, "chlorosis" to 1
        )),
        Rule("calcium_deficiency", "deficiency", mapOf(
            "new_growth_distorted" to 3, "tip_burn" to 2, "young_leaves_curled" to 2
        )),
        Rule("magnesium_deficiency", "deficiency", mapOf(
            "interveinal_chlorosis" to 3, "yellowing_between_veins" to 2
        )),
        Rule("iron_deficiency", "deficiency", mapOf(
            "young_leaves_yellow" to 3, "interveinal_chlorosis_young" to 2
        )),
        // ── Excesses / stress ──────────────────────────────────────────
        Rule("nutrient_burn", "excess", mapOf(
            "brown_tips" to 3, "leaf_tip_burn" to 2, "crooked_growing" to 1
        )),
        Rule("ph_lockout", "excess", mapOf(
            "ph_out_of_range" to 3, "locked_nutrients" to 2
        )),
        Rule("thermal_stress", "excess", mapOf(
            "wilting" to 2, "leaf_curl_heat" to 2, "hot_spots" to 1
        )),
        Rule("light_stress", "excess", mapOf(
            "bleached_leaves" to 3, "light_burn" to 2
        )),
        // ── Pests & fungi ──────────────────────────────────────────────
        Rule("powdery_mildew", "fungus", mapOf(
            "white_powdery_residue" to 3, "fuzzy_white_mold" to 3
        )),
        Rule("botrytis", "fungus", mapOf(
            "gray_mold" to 3, "rotting_buds" to 3
        )),
        Rule("spider_mite", "pest", mapOf(
            "tiny_spiders" to 3, "webbing" to 3, "stippled_leaves" to 2
        )),
        Rule("thrips", "pest", mapOf(
            "silvering" to 3, "black_dots" to 2
        )),
        Rule("fungus_gnat", "pest", mapOf(
            "tiny_black_flies" to 3, "larvae_in_soil" to 2
        ))
    )

    fun diagnose(symptomIds: Set<String>): DiagnosisResult {
        val normalized = symptomIds.map { it.trim().lowercase() }.toSet()
        if (normalized.isEmpty()) return healthyResult()

        var best: Rule? = null
        var bestScore = 0
        var bestCoverage = 0f

        RULES.forEach { rule ->
            var score = 0
            var totalWeight = 0
            rule.weights.forEach { (symptom, weight) ->
                totalWeight += weight
                if (normalized.contains(symptom)) score += weight
            }
            if (score > 0) {
                val coverage = score.toFloat() / totalWeight
                if (coverage > bestCoverage || (coverage == bestCoverage && score > bestScore)) {
                    best = rule
                    bestScore = score
                    bestCoverage = coverage
                }
            }
        }

        val rule = best ?: return healthyResult()
        val matched = rule.weights.keys.filter { normalized.contains(it) }
        // Confidence: base 0.55 + matched-specificity, capped 0.95.
        val specificity = (0.55f + bestCoverage * 0.35f + (matched.size - 1) * 0.03f).coerceIn(0f, 0.95f)
        val confidence = (specificity * 100).roundToInt() / 100f

        return DiagnosisResult(
            condition = rule.condition,
            category = rule.category,
            confidence = confidence,
            symptoms = matched.sortedByDescending { rule.weights[it] }
        )
    }

    private fun healthyResult(): DiagnosisResult = DiagnosisResult(
        condition = "healthy",
        category = "healthy",
        confidence = 0.5f,
        symptoms = emptyList()
    )
}

data class DiagnosisResult(
    val condition: String,
    val category: String,
    val confidence: Float,
    val symptoms: List<String>,
    /** Photographic evidence that supported the verdict, when a photo was used. */
    val photoEvidence: List<String> = emptyList()
) {
    val isHealthy get() = condition == "healthy"
}

/**
 * Spanish UI label for an event/metric type, resolved locally so screens do
 * not depend on resources when rendering chips.
 */
object UiLabels {
    fun eventLabel(eventType: EventType): String = when (eventType) {
        EventType.IRRIGATION -> "Riego"
        EventType.FERTILIZATION -> "Fertilización"
        EventType.PRUNING -> "Poda"
        EventType.TRANSPLANT -> "Trasplante"
        EventType.TRAINING -> "Entrenamiento"
        EventType.PEST_CONTROL -> "Control de Plagas"
        EventType.HEIGHT -> "Altura"
        EventType.LAMP_DISTANCE -> "Distancia de Lámpara"
        EventType.FLUSHING -> "Flush"
        EventType.DEFOLIATION -> "Defoliación"
        EventType.VPD -> "VPD"
        EventType.TRICHOME_CHECK -> "Revisión de Tricomas"
        EventType.TEMPERATURE_HUMIDITY -> "Temp/Humedad"
        EventType.HARVEST -> "Cosecha"
        EventType.BREEDING -> "Cría"
        EventType.DIAGNOSIS -> "Diagnóstico"
    }

    fun themeName(index: Int): String = when (index) {
        ThemeIndex.GREEN -> "🌿 Brote Verde"
        ThemeIndex.AUTUMN -> "🍂 Cosecha de Otoño"
        ThemeIndex.NIGHT -> "🌙 Cuidado Nocturno"
        ThemeIndex.SUNNY -> "☀️ Invernadero Soleado"
        else -> "🌿 Brote Verde"
    }
}

/** Placeholder referencing R for resource-based labels used by screens. */
@Suppress("unused")
private fun placeholderRes(context: Context): Int = R.string.app_name