package com.google.mediapipe.examples.llminference.vision

import android.graphics.Bitmap
import com.google.mediapipe.examples.llminference.ai.MedicalPromptTemplates
import com.google.mediapipe.examples.llminference.data.PatientEntity

/**
 * Enhanced medical prompt templates that integrate MediaPipe Vision analysis
 */
object MultimodalMedicalPrompts {

    /**
     * Generate comprehensive image analysis prompt with Vision findings
     */
    fun imageAnalysisWithVision(
        visionAnalysis: MedicalImageAnalysis,
        imageType: String,
        bodyPart: String,
        patientAge: Int,
        patientGender: String,
        clinicalContext: String = ""
    ): String {
        return buildString {
            appendLine("# Medical Image Analysis Request")
            appendLine()
            appendLine("## Patient Information")
            appendLine("- Age: $patientAge years")
            appendLine("- Gender: $patientGender")
            appendLine()
            appendLine("## Image Details")
            appendLine("- Type: $imageType")
            appendLine("- Body Part: $bodyPart")
            if (clinicalContext.isNotEmpty()) {
                appendLine("- Clinical Context: $clinicalContext")
            }
            appendLine()
            appendLine("## AI Vision Analysis Results")
            appendLine(visionAnalysis.toPromptText())
            appendLine()
            appendLine("## Request")
            appendLine("Based on the AI vision analysis above, please provide:")
            appendLine("1. Clinical interpretation of the detected findings")
            appendLine("2. Differential diagnosis considerations")
            appendLine("3. Recommended follow-up imaging or tests")
            appendLine("4. Clinical correlation with patient demographics")
            appendLine()
            appendLine("Please provide a concise, medically accurate assessment.")
        }
    }

    /**
     * Generate prognosis prompt enhanced with image analysis
     */
    fun prognosisWithImaging(
        patient: PatientEntity,
        chiefComplaint: String,
        symptoms: String,
        vitalSigns: String,
        imagingAnalysis: MedicalImageAnalysis
    ): String {
        return buildString {
            appendLine("# Medical Prognosis Request")
            appendLine()
            appendLine("## Patient Demographics")
            appendLine("- Gender: ${patient.gender}")
            appendLine("- Date of Birth: ${patient.dateOfBirth}")
            if (patient.allergies.isNotEmpty()) {
                appendLine("- Known Allergies: ${patient.allergies}")
            }
            if (patient.notes.isNotEmpty()) {
                appendLine("- Medical History: ${patient.notes}")
            }
            appendLine()
            appendLine("## Presenting Complaint")
            appendLine("- Chief Complaint: $chiefComplaint")
            appendLine("- Symptoms: $symptoms")
            appendLine("- Vital Signs: $vitalSigns")
            appendLine()
            
            if (imagingAnalysis.hasResults) {
                appendLine("## Imaging Findings (AI Analysis)")
                appendLine(imagingAnalysis.toPromptText())
                appendLine()
            }
            
            appendLine("## Required Assessment")
            appendLine("Please provide:")
            appendLine("1. Differential diagnosis (ranked by likelihood)")
            appendLine("2. Recommended diagnostic workup")
            appendLine("3. Suggested treatment plan")
            appendLine("4. Expected prognosis")
            appendLine("5. When to follow up")
            appendLine()
            appendLine("Consider all available information including imaging findings.")
        }
    }

    /**
     * Compare multiple images longitudinally with Vision analysis
     */
    fun longitudinalComparisonWithVision(
        analyses: List<Pair<String, MedicalImageAnalysis>>, // (timestamp, analysis)
        imageType: String,
        bodyPart: String
    ): String {
        return buildString {
            appendLine("# Longitudinal Medical Image Comparison")
            appendLine()
            appendLine("## Study Details")
            appendLine("- Modality: $imageType")
            appendLine("- Anatomical Region: $bodyPart")
            appendLine("- Number of Studies: ${analyses.size}")
            appendLine()
            
            analyses.forEachIndexed { index, (timestamp, analysis) ->
                appendLine("## Study ${index + 1} ($timestamp)")
                appendLine(analysis.toPromptText())
                appendLine()
            }
            
            appendLine("## Analysis Request")
            appendLine("Please analyze the progression across these ${analyses.size} studies:")
            appendLine("1. Identify changes over time (progression/regression)")
            appendLine("2. Assess treatment response if applicable")
            appendLine("3. Predict likely trajectory")
            appendLine("4. Recommend next imaging interval")
        }
    }

    /**
     * Emergency triage with rapid image assessment
     */
    fun emergencyTriageWithVision(
        visionAnalysis: MedicalImageAnalysis,
        chiefComplaint: String,
        vitalSigns: String,
        imageType: String
    ): String {
        return buildString {
            appendLine("# EMERGENCY TRIAGE ASSESSMENT")
            appendLine()
            appendLine("## Presenting Complaint")
            appendLine(chiefComplaint)
            appendLine()
            appendLine("## Vital Signs")
            appendLine(vitalSigns)
            appendLine()
            appendLine("## Urgent Imaging ($imageType)")
            appendLine(visionAnalysis.toPromptText())
            appendLine()
            appendLine("## URGENT REQUEST")
            appendLine("Provide immediate triage assessment:")
            appendLine("1. Severity level (Critical/Urgent/Standard)")
            appendLine("2. Life-threatening findings requiring immediate intervention")
            appendLine("3. Immediate treatment recommendations")
            appendLine("4. Safe to discharge or admit?")
            appendLine()
            appendLine("TIME-SENSITIVE: Respond concisely with most critical information first.")
        }
    }
}
