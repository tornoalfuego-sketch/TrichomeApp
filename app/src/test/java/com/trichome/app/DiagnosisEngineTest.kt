package com.trichome.app

import com.trichome.app.data.repository.DiagnosisEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure rule-engine diagnosis. Deterministic: the condition with the highest
 * weighted coverage wins; empty symptoms always yield "healthy".
 */
class DiagnosisEngineTest {

    @Test
    fun `no symptoms means healthy`() {
        val result = DiagnosisEngine.diagnose(emptySet())
        assertTrue(result.isHealthy)
        assertEquals("healthy", result.condition)
    }

    @Test
    fun `yellowing lower leaves maps to nitrogen deficiency`() {
        val result = DiagnosisEngine.diagnose(setOf("yellowing_lower_leaves"))
        assertEquals("nitrogen_deficiency", result.condition)
        assertEquals("deficiency", result.category)
    }

    @Test
    fun `white powdery residue maps to powdery mildew`() {
        val result = DiagnosisEngine.diagnose(setOf("white_powdery_residue", "fuzzy_white_mold"))
        assertEquals("powdery_mildew", result.condition)
        assertEquals("fungus", result.category)
        assertTrue(result.confidence >= 0.5f)
    }

    @Test
    fun `spider signals map to spider mite`() {
        val result = DiagnosisEngine.diagnose(setOf("webbing", "tiny_spiders"))
        assertEquals("spider_mite", result.condition)
        assertEquals("pest", result.category)
    }

    @Test
    fun `confidence rises with specificity`() {
        val single = DiagnosisEngine.diagnose(setOf("white_powdery_residue")).confidence
        val double = DiagnosisEngine.diagnose(setOf("white_powdery_residue", "fuzzy_white_mold")).confidence
        assertTrue(double >= single)
    }

    @Test
    fun `symptom matching is case and space insensitive`() {
        val result = DiagnosisEngine.diagnose(setOf("  YELLOWING_LOWER_LEAVES "))
        assertEquals("nitrogen_deficiency", result.condition)
    }

    @Test
    fun `mixed symptoms pick best weighted rule`() {
        // brown tips belong to nutrient burn (3 + leaf_tip_burn 2), potassium edges (3) etc.
        val result = DiagnosisEngine.diagnose(setOf("brown_tips", "leaf_tip_burn", "crooked_growing"))
        assertEquals("nutrient_burn", result.condition)
        assertEquals("excess", result.category)
    }
}