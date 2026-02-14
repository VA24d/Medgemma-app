package com.google.mediapipe.examples.llminference.ai

import com.google.mediapipe.examples.llminference.data.PatientEntity

/**
 * Pre-built medical prompt templates for AI analysis
 */
object MedicalPromptTemplates {

    /**
     * Generate a prompt for analyzing medical images
     */
    fun imageAnalysisPrompt(
        imageType: String,
        bodyPart: String,
        patientAge: Int?,
        patientGender: String?,
        clinicalContext: String = ""
    ): String {
        val demographics = buildString {
            if (patientAge != null) append("Patient age: $patientAge years. ")
            if (!patientGender.isNullOrEmpty()) append("Gender: $patientGender. ")
        }

        return """
            You are an AI medical assistant helping doctors analyze medical images.
            
            Image type: $imageType
            Body part: $bodyPart
            $demographics
            ${if (clinicalContext.isNotEmpty()) "Clinical context: $clinicalContext" else ""}
            
            Please analyze this medical image and provide:
            1. Observable findings
            2. Potential abnormalities or areas of concern
            3. Comparison with normal anatomy
            4. Recommendations for further investigation if needed
            
            Remember: This is for clinical assistance only. All findings should be verified by a qualified medical professional.
        """.trimIndent()
    }

    /**
     * Generate a prompt for longitudinal image comparison
     */
    fun longitudinalComparisonPrompt(
        imageType: String,
        bodyPart: String,
        numberOfImages: Int,
        timeSpan: String
    ): String {
        return """
            You are an AI medical assistant analyzing a series of $numberOfImages $imageType images of the $bodyPart taken over $timeSpan.
            
            Please provide:
            1. Progression or regression of any findings
            2. Changes in size, density, or characteristics of any abnormalities
            3. New findings compared to earlier images
            4. Overall trend assessment (improving, stable, deteriorating)
            5. Clinical significance of observed changes
            
            Remember: This analysis is for clinical assistance. Final interpretation requires a qualified medical professional.
        """.trimIndent()
    }

    /**
     * Generate a prognosis prompt based on symptoms and findings
     */
    fun prognosisPrompt(
        patient: PatientEntity,
        chiefComplaint: String,
        symptoms: String,
        vitalSigns: String,
        imagingFindings: String = ""
    ): String {
        return """
            You are an AI medical assistant helping with prognosis analysis.
            
            Patient Information:
            - Age: ${calculateAge(patient.dateOfBirth)} years
            - Gender: ${patient.gender}
            - Allergies: ${patient.allergies.ifEmpty { "None reported" }}
            - Medical History: ${patient.notes.ifEmpty { "No significant history" }}
            
            Current Presentation:
            - Chief Complaint: $chiefComplaint
            - Symptoms: $symptoms
            - Vital Signs: $vitalSigns
            ${if (imagingFindings.isNotEmpty()) "- Imaging Findings: $imagingFindings" else ""}
            
            Please provide:
            1. Differential diagnosis (top 3-5 most likely conditions)
            2. Recommended diagnostic tests or procedures
            3. Potential treatment approaches
            4. Risk factors and prognostic indicators
            5. Follow-up recommendations
            
            IMPORTANT: This is clinical decision support only. All recommendations must be reviewed and approved by the attending physician. Never delay emergency care.
        """.trimIndent()
    }

    /**
     * Generate a prompt for medical transcription correction
     */
    fun transcriptionRefinementPrompt(rawTranscription: String): String {
        return """
            You are an AI assistant helping to refine medical dictation transcription.
            
            Raw transcription: "$rawTranscription"
            
            Please:
            1. Correct any obvious speech recognition errors
            2. Add appropriate medical terminology
            3. Structure the text into proper sections (if applicable)
            4. Maintain the original medical meaning
            5. Format appropriately for medical documentation
            
            Return only the corrected transcription without additional comments.
        """.trimIndent()
    }

    /**
     * Generate a consultation summary prompt
     */
    fun consultationSummaryPrompt(
        chiefComplaint: String,
        symptoms: String,
        examination: String,
        diagnosis: String,
        plan: String
    ): String {
        return """
            You are an AI medical scribe. Create a concise consultation summary in SOAP format:
            
            Raw information:
            - Chief Complaint: $chiefComplaint
            - Symptoms: $symptoms
            - Examination: $examination
            - Diagnosis: $diagnosis
            - Plan: $plan
            
            Format the output as:
            S (Subjective): [Patient's reported symptoms and complaints]
            O (Objective): [Physical examination findings and vital signs]
            A (Assessment): [Diagnosis and clinical impression]
            P (Plan): [Treatment plan and follow-up]
            
            Keep it professional and concise.
        """.trimIndent()
    }

    /**
     * Calculate age from date of birth string (YYYY-MM-DD)
     */
    private fun calculateAge(dateOfBirth: String): Int {
        return try {
            val parts = dateOfBirth.split("-")
            val birthYear = parts[0].toInt()
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            currentYear - birthYear
        } catch (e: Exception) {
            0
        }
    }
}
