package com.trichome.app.data.repository

import com.trichome.app.domain.vision.PhotoAnalyzer
import kotlin.math.roundToInt

/**
 * Scores a photo against the condition knowledge base.
 *
 * The symptom engine ([DiagnosisEngine]) answers "what does the grower see?".
 * This engine answers "what does the camera see?", and the two are combined in
 * the view model. Every threshold comes from `photoEvidence` in
 * `assets/data/diagnostics.json`; nothing is hard-coded here.
 */
object PhotoDiagnosisEngine {

    /** A single condition's photographic verdict. */
    data class Match(
        val condition: DiagnosisCondition,
        /** 0..1. Fraction of the evidence that the photo satisfies. */
        val evidenceFit: Float,
        /** Evidence points contributed, already scaled by the condition weight. */
        val weightedScore: Float,
        /** Human-readable list of the thresholds that were met. */
        val matchedEvidence: List<String>
    )

    /**
     * Returns every condition whose photographic evidence is at least partially
     * satisfied, best first.
     *
     * A condition with no `photoEvidence` is never returned: it is not something
     * a photograph can establish (pH lockout, a cold spell, a nutrient ratio).
     */
    fun rank(
        features: PhotoAnalyzer.Features,
        conditions: List<DiagnosisCondition>,
        limit: Int = 6
    ): List<Match> {
        if (!features.isUsable) return emptyList()

        return conditions.mapNotNull { condition ->
            val evidence = condition.photoEvidence
            if (evidence == null || evidence.isEmpty) return@mapNotNull null

            val checks = evaluate(evidence, features)
            if (checks.isEmpty()) return@mapNotNull null

            val satisfied = checks.count { it.passed }
            if (satisfied == 0) return@mapNotNull null

            val fit = satisfied.toFloat() / checks.size
            // A partially matching condition is only interesting when it clears
            // the noise floor; requiring the majority keeps weak single-threshold
            // matches from flooding the ranking.
            if (fit < 0.5f) return@mapNotNull null

            Match(
                condition = condition,
                evidenceFit = fit,
                weightedScore = fit * evidence.weight,
                matchedEvidence = checks.filter { it.passed }.map { it.description }
            )
        }
            .sortedWith(compareByDescending<Match> { it.weightedScore }.thenByDescending { it.evidenceFit })
            .take(limit)
    }

    /**
     * Folds the photo ranking and the symptom ranking into a single result.
     *
     * Symptoms keep priority when they agree with the photo, because a
     * human-confirmed observation is stronger evidence than a threshold match.
     */
    fun combine(
        photoMatches: List<Match>,
        symptomResult: DiagnosisResult,
        conditions: List<DiagnosisCondition>
    ): DiagnosisResult {
        if (photoMatches.isEmpty()) return symptomResult
        if (symptomResult.isHealthy) {
            val best = photoMatches.first()
            return DiagnosisResult(
                condition = best.condition.id,
                category = best.condition.category,
                confidence = (0.45f + best.evidenceFit * 0.45f).coerceAtMost(0.92f),
                symptoms = emptyList()
            )
        }

        val photoTop = photoMatches.firstOrNull { it.condition.id == symptomResult.condition }
        val combined = if (photoTop != null) {
            // Agreement between both engines is the strongest signal available.
            (symptomResult.confidence * 0.6f + photoTop.evidenceFit * 0.4f).coerceAtMost(0.97f)
        } else {
            symptomResult.confidence * 0.75f
        }

        val resolved = conditions.firstOrNull { it.id == symptomResult.condition }
        return DiagnosisResult(
            condition = symptomResult.condition,
            category = resolved?.category ?: symptomResult.category,
            confidence = (combined * 100).roundToInt() / 100f,
            symptoms = symptomResult.symptoms,
            photoEvidence = photoMatches.firstOrNull { it.condition.id == symptomResult.condition }
                ?.matchedEvidence.orEmpty()
        )
    }

    private data class Check(
        val description: String,
        val passed: Boolean
    )

    private fun evaluate(
        e: PhotoEvidence,
        f: PhotoAnalyzer.Features
    ): List<Check> = buildList {
        e.chlorosisMin?.let { add(Check("amarillamiento ≥ ${pct(it)}", f.chlorosisRatio >= it)) }
        e.necrosisMin?.let { add(Check("tejido muerto ≥ ${pct(it)}", f.necrosisRatio >= it)) }
        e.spotDensityMin?.let { add(Check("manchas aisladas ≥ ${pct(it)}", f.spotDensity >= it)) }
        e.trichomeMin?.let {
            add(Check("estructuras brillantes ≥ ${pct(it)}", f.trichomeRatio >= it))
        }
        e.webbingMin?.let { add(Check("filamentos ≥ ${pct(it)}", f.webbingRatio >= it)) }
        e.greenMin?.let { add(Check("tejido verde ≥ ${pct(it)}", f.greenRatio >= it)) }
        e.greenMax?.let { add(Check("tejido verde ≤ ${pct(it)}", f.greenRatio <= it)) }
        e.hueMin?.let { add(Check("tono ≥ ${it.toInt()}°", f.averageHue >= it)) }
        e.hueMax?.let { add(Check("tono ≤ ${it.toInt()}°", f.averageHue <= it)) }
        e.saturationMax?.let { add(Check("saturación ≤ ${pct(it)}", f.averageSaturation <= it)) }
        e.valueMin?.let { add(Check("brillo ≥ ${pct(it)}", f.averageValue >= it)) }
    }

    private fun pct(value: Float): String = "${(value * 100).roundToInt()} %"
}
