package com.trichome.app.data.repository

import com.trichome.app.domain.vision.PhotoAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the ranking step that turns pixel features into a verdict.
 *
 * The regression that matters most here: a condition with no `photoEvidence`
 * used to be scored on nothing and could win, which is why the photo produced
 * a verdict identical to the one produced with no photo at all.
 */
class PhotoDiagnosisEngineTest {

    private val width = 40
    private val height = 40

    private fun features(color: Int) =
        PhotoAnalyzer.analyze(IntArray(width * height) { color }, width, height)

    private val green = features(0xFF3E8E41.toInt())
    private val yellow = features(0xFFD9C64A.toInt())

    private val noEvidence = DiagnosisCondition(
        id = "sin_evidencia",
        category = "fungus",
        labelEs = "Mancha sin evidencia fotografica"
    )

    private val withEvidence = DiagnosisCondition(
        id = "clorosis_fuerte",
        category = "deficiency",
        labelEs = "Clorosis marcada",
        shortEs = "Amarilleo",
        photoEvidence = PhotoEvidence(chlorosisMin = 0.30f, greenMin = 0.60f, weight = 2)
    )

    @Test
    fun rankExcludesConditionsWithoutPhotoEvidence() {
        val matches = PhotoDiagnosisEngine.rank(
            features = green,
            conditions = listOf(noEvidence, withEvidence)
        )

        assertTrue(
            "a condition with no photoEvidence can never be matched by a photo",
            matches.none { it.condition.id == noEvidence.id }
        )
    }

    @Test
    fun unusablePhotoProducesNoMatchesAtAll() {
        val blank = features(0xFF141414.toInt())

        val matches = PhotoDiagnosisEngine.rank(
            features = blank,
            conditions = listOf(withEvidence)
        )

        assertTrue("an unusable frame must not be diagnosed", matches.isEmpty())
    }

    @Test
    fun conditionAboveItsThresholdsIsReturnedWithAFitInRange() {
        val matches = PhotoDiagnosisEngine.rank(
            features = yellow,
            conditions = listOf(withEvidence)
        )

        val match = matches.firstOrNull { it.condition.id == withEvidence.id }
        assertNotNull("yellow tissue should satisfy the chlorosis threshold", match)
        val fit = match!!.evidenceFit
        assertTrue("evidenceFit must be 0..1, was $fit", fit in 0f..1f)
    }

    @Test
    fun photoDrivesTheVerdictWhenSymptomsSayNothingIsWrong() {
        // This is the path the user exercises: take a photo, tick no symptoms.
        val noSymptomVerdict = DiagnosisResult(
            condition = "healthy",
            category = "none",
            confidence = 0.85f,
            symptoms = emptyList()
        )
        val photoMatches = PhotoDiagnosisEngine.rank(yellow, listOf(withEvidence))
        assertTrue("the yellow frame should produce a match to work with", photoMatches.isNotEmpty())

        val combined = PhotoDiagnosisEngine.combine(photoMatches, noSymptomVerdict, listOf(withEvidence))

        assertEquals("the photo must drive the verdict", withEvidence.id, combined.condition)
        assertTrue("confidence must be 0..1, was ${combined.confidence}", combined.confidence in 0f..1f)
    }

    @Test
    fun agreementBetweenPhotoAndSymptomsCarriesThePhotoEvidence() {
        val agreeing = DiagnosisResult(
            condition = withEvidence.id,
            category = withEvidence.category,
            confidence = 0.5f,
            symptoms = listOf("amarilleo")
        )
        val photoMatches = PhotoDiagnosisEngine.rank(yellow, listOf(withEvidence))

        val combined = PhotoDiagnosisEngine.combine(photoMatches, agreeing, listOf(withEvidence))

        assertEquals("both engines agree, the verdict stands", withEvidence.id, combined.condition)
        assertTrue(
            "the corroborating evidence should reach the result, was ${combined.photoEvidence}",
            combined.photoEvidence.isNotEmpty()
        )
    }

    @Test
    fun strongPhotoAgreementRaisesConfidence() {
        // Confidence is a blend (symptomConfidence * 0.6 + evidenceFit * 0.4),
        // so corroboration only lifts it when the photo genuinely fits. A
        // threshold the frame clearly exceeds is what makes that testable.
        val strong = DiagnosisCondition(
            id = "clorosis",
            category = "deficiency",
            labelEs = "Clorosis",
            photoEvidence = PhotoEvidence(chlorosisMin = 0.05f, weight = 2)
        )
        val agreeing = DiagnosisResult(
            condition = strong.id,
            category = strong.category,
            confidence = 0.5f,
            symptoms = listOf("amarilleo")
        )
        val photoMatches = PhotoDiagnosisEngine.rank(yellow, listOf(strong))
        assertTrue("the frame should fit the strong evidence", photoMatches.isNotEmpty())

        val combined = PhotoDiagnosisEngine.combine(photoMatches, agreeing, listOf(strong))

        assertEquals("both engines agree, the verdict stands", strong.id, combined.condition)
        assertTrue(
            "a strong corroboration should beat the symptom-only 0.5, was ${combined.confidence}",
            combined.confidence > 0.5f
        )
    }

    @Test
    fun aSpecificSymptomSelectionIsNotOverriddenByThePhoto() {
        // Symptoms are what the grower deliberately ticked, so they outrank a
        // photo hint; the photo only dampens the confidence it disagrees with.
        val pestVerdict = DiagnosisResult(
            condition = "solo_sintomas",
            category = "pest",
            confidence = 0.5f,
            symptoms = listOf("manchas")
        )
        val photoMatches = PhotoDiagnosisEngine.rank(yellow, listOf(withEvidence))

        val combined = PhotoDiagnosisEngine.combine(photoMatches, pestVerdict, listOf(withEvidence))

        assertEquals("a ticked symptom must survive a disagreeing photo", "solo_sintomas", combined.condition)
        assertTrue(
            "disagreement should reduce confidence, was ${combined.confidence}",
            combined.confidence < 0.5f
        )
    }

    @Test
    fun combineFallsBackToSymptomsWhenThereIsNoPhotoEvidence() {
        val healthy = DiagnosisResult(
            condition = "healthy",
            category = "none",
            confidence = 0.9f,
            symptoms = emptyList()
        )
        val symptomResult = DiagnosisResult(
            condition = "solo_sintomas",
            category = "pest",
            confidence = 0.4f,
            symptoms = listOf("manchas")
        )

        val combined = PhotoDiagnosisEngine.combine(emptyList(), symptomResult, listOf(withEvidence))

        assertEquals("with no photo the symptom verdict must survive", "solo_sintomas", combined.condition)
        assertEquals("and its confidence must be untouched", 0.4f, combined.confidence, 0.001f)
        assertTrue(combined.confidence in 0f..1f)
        assertTrue("healthy must be reported as healthy", healthy.isHealthy)
    }
}
