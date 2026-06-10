package com.google.mediapipe.examples.llminference.cloud

import com.google.mediapipe.examples.llminference.data.MedicalEntryEntity
import com.google.mediapipe.examples.llminference.data.PatientEntity
import com.google.mediapipe.examples.llminference.data.PatientChartPrompt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CloudEntryPrompts {

    private val imagingTypes = setOf("XRAY", "HISTOPATHOLOGY", "MRI")

    fun visionPrompt(entryType: String): String = when (entryType) {
        "HISTOPATHOLOGY" -> """You are a pathology AI. Systematically describe this histopathology slide for clinical documentation.
Report: 1) Stain type, 2) Tissue architecture, 3) Cellular morphology, 4) Mitotic figures, 5) Inflammatory infiltrate, 6) Vascular/stromal changes, 7) Dysplasia or malignancy with location.
Be precise and clinically useful. No code blocks."""
        "MRI" -> """You are a radiologist AI specialising in MRI. Describe this MRI for clinical documentation.
Report: sequence/plane, region, signal characteristics, focal lesions, mass effect, enhancement, incidental findings.
Be precise. No code blocks."""
        else -> """You are a radiologist AI. Systematically describe this X-ray for clinical documentation.
Report: 1) Orientation and quality, 2) Bony structures, 3) Soft tissue, 4) Lung fields, 5) Cardiac silhouette, 6) Mediastinum, 7) Pleural spaces, 8) Abnormal findings with location.
Be precise. No code blocks."""
    }

    fun textEntryPrompt(entry: MedicalEntryEntity): String {
        val typeLabel = when (entry.entryType) {
            "RECORDING" -> "voice recording / transcription"
            "MANUAL" -> "clinical note"
            "DOCUMENT" -> "medical document"
            else -> entry.entryType.lowercase(Locale.getDefault())
        }
        return """You are a specialist AI medical assistant. Analyse this $typeLabel entry for chart documentation.
Title: ${entry.title}
Content: ${entry.content}

Provide:
1) Key clinical findings
2) Clinical significance
3) Recommended follow-up if any

Be concise. No code blocks."""
    }

    fun needsVisionProcessing(entry: MedicalEntryEntity, force: Boolean): Boolean {
        if (entry.entryType !in imagingTypes || entry.imagePaths.isBlank()) return false
        if (force) return true
        return entry.analysisResult.isBlank() || entry.cloudProcessedAt == 0L
    }

    fun needsTextProcessing(entry: MedicalEntryEntity, force: Boolean): Boolean {
        if (entry.entryType in imagingTypes) return false
        if (force) return true
        return entry.visitSummary.isBlank() || entry.cloudProcessedAt == 0L
    }

    fun buildLongitudinalPrompt(patient: PatientEntity, entries: List<MedicalEntryEntity>): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeline = entries.sortedBy { it.createdAt }.joinToString("\n") { e ->
            val line = PatientChartPrompt.effectiveVisitLine(e)
            val ai = if (e.analysisResult.isNotBlank()) "\n  Imaging/AI: ${e.analysisResult.take(800)}" else ""
            "[${fmt.format(Date(e.createdAt))}][${e.entryType}] ${e.title}\n  → $line$ai"
        }
        return """You are a specialist AI medical assistant. Generate a concise clinical prognosis from this enriched chart.

PATIENT: ${patient.name}
DOB: ${patient.dateOfBirth}  Gender: ${patient.gender}
Allergies: ${patient.allergies.ifBlank { "None" }}

ENTRIES (${entries.size}, oldest→newest):
$timeline

Provide in Markdown:
1. **Summary of findings**
2. **Diagnosis / differentials** (with confidence)
3. **Disease progression** (improving / stable / deteriorating)
4. **Recommended next steps**
5. **Red flags** to monitor

Be clinically precise. No chain-of-thought."""
    }
}
