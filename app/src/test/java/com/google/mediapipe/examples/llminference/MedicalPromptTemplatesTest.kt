package com.google.mediapipe.examples.llminference

import com.google.mediapipe.examples.llminference.ai.MedicalPromptTemplates
import com.google.mediapipe.examples.llminference.data.PatientEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for medical prompt generation
 */
class MedicalPromptTemplatesTest {

    @Test
    fun imageAnalysisPrompt_containsRequiredInfo() {
        val prompt = MedicalPromptTemplates.imageAnalysisPrompt(
            imageType = "X-Ray",
            bodyPart = "Chest",
            patientAge = 45,
            patientGender = "Male",
            clinicalContext = "Persistent cough for 2 weeks"
        )

        assertTrue(prompt.contains("X-Ray"))
        assertTrue(prompt.contains("Chest"))
        assertTrue(prompt.contains("45"))
        assertTrue(prompt.contains("Male"))
        assertTrue(prompt.contains("Persistent cough"))
        assertTrue(prompt.contains("Observable findings"))
    }

    @Test
    fun longitudinalComparisonPrompt_isGenerated() {
        val prompt = MedicalPromptTemplates.longitudinalComparisonPrompt(
            imageType = "MRI",
            bodyPart = "Brain",
            numberOfImages = 3,
            timeSpan = "6 months"
        )

        assertTrue(prompt.contains("3"))
        assertTrue(prompt.contains("MRI"))
        assertTrue(prompt.contains("Brain"))
        assertTrue(prompt.contains("6 months"))
        assertTrue(prompt.contains("Progression or regression"))
    }

    @Test
    fun prognosisPrompt_includesPatientData() {
        val patient = PatientEntity(
            name = "Test Patient",
            dateOfBirth = "1980-01-15",
            gender = "Female",
            medicalRecordNumber = "TEST001",
            allergies = "Aspirin",
            notes = "Diabetes Type 2"
        )

        val prompt = MedicalPromptTemplates.prognosisPrompt(
            patient = patient,
            chiefComplaint = "Headache",
            symptoms = "Severe headache with nausea",
            vitalSigns = "BP: 140/90, Temp: 98.6F",
            imagingFindings = "CT scan shows no abnormalities"
        )

        assertTrue(prompt.contains("Female"))
        assertTrue(prompt.contains("Headache"))
        assertTrue(prompt.contains("Aspirin"))
        assertTrue(prompt.contains("Diabetes"))
        assertTrue(prompt.contains("140/90"))
        assertTrue(prompt.contains("Differential diagnosis"))
    }

    @Test
    fun transcriptionRefinementPrompt_isGenerated() {
        val prompt = MedicalPromptTemplates.transcriptionRefinementPrompt(
            "patient has hypertension and diabetes"
        )

        assertTrue(prompt.contains("patient has hypertension and diabetes"))
        assertTrue(prompt.contains("transcription"))
    }

    @Test
    fun consultationSummaryPrompt_containsSOAPFormat() {
        val prompt = MedicalPromptTemplates.consultationSummaryPrompt(
            chiefComplaint = "Fever",
            symptoms = "High fever, chills",
            examination = "Temperature 102F",
            diagnosis = "Viral infection",
            plan = "Rest, fluids, antipyretics"
        )

        assertTrue(prompt.contains("SOAP"))
        assertTrue(prompt.contains("Subjective"))
        assertTrue(prompt.contains("Objective"))
        assertTrue(prompt.contains("Assessment"))
        assertTrue(prompt.contains("Plan"))
        assertTrue(prompt.contains("Fever"))
    }
}
