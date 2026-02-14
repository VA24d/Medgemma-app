package com.google.mediapipe.examples.llminference.vision

import com.google.mediapipe.examples.llminference.data.MedicalImageEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Vision analysis data structures
 */
class MedicalImageAnalysisTest {

    @Test
    fun medicalImageAnalysis_toPromptText_isValid() {
        val analysis = MedicalImageAnalysis(
            classifications = listOf("Normal tissue (85%)", "Slight inflammation (12%)"),
            detections = listOf("Mass at [100,200,150,250]"),
            confidenceScore = 0.72f,
            findings = "High confidence in normal tissue classification"
        )

        val promptText = analysis.toPromptText()

        assertTrue(promptText.contains("Normal tissue"))
        assertTrue(promptText.contains("Mass at"))
        assertTrue(promptText.contains("72%"))
        assertTrue(analysis.classifications.isNotEmpty() || analysis.detections.isNotEmpty())
    }

    @Test
    fun medicalImageAnalysis_emptyResults_hasNoResults() {
        val analysis = MedicalImageAnalysis()

        assertTrue(analysis.classifications.isEmpty())
        assertTrue(analysis.detections.isEmpty())
        assertTrue(analysis.toPromptText().contains("Confidence: 0%"))
    }

    @Test
    fun medicalImageAnalysis_withError_showsError() {
        val analysis = MedicalImageAnalysis(error = "Model not loaded")

        val promptText = analysis.toPromptText()
        assertTrue(promptText.contains("error"))
        assertTrue(promptText.contains("Model not loaded"))
    }
}
