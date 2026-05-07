package com.google.mediapipe.examples.llminference.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Visit headlines for workers and fallbacks; chart chat uses full entry text in [ChatViewModel].
 */
object PatientChartPrompt {

    private val longitudinalPhrases = listOf(
        "progress", "summary", "summarize", "timeline", "longitudinal", "trajectory",
        "clinical course", "disease course", "course of illness",
        "overall picture", "overall summary",
        "how is the patient", "how's the patient", "hows the patient",
        "visit by visit",
        "interval change", "compared to prior", "before and after",
        "getting better", "getting worse", "improving", "deteriorat",
    )

    /** Course / trajectory questions — allow longer decode and non-forced thinking when applicable. */
    fun wantsLongitudinalQuestion(userMessage: String): Boolean {
        val n = userMessage.lowercase()
        return longitudinalPhrases.any { n.contains(it) }
    }

    fun compactTimelineSection(entries: List<MedicalEntryEntity>): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return entries.sortedBy { it.createdAt }.joinToString("\n") { e ->
            val line = effectiveVisitLine(e)
            "[${fmt.format(Date(e.createdAt))}] ${e.entryType} · ${e.title}\n  → $line"
        }
    }

    /** Single-line fallback when [MedicalEntryEntity.visitSummary] is empty (legacy rows). */
    fun effectiveVisitLine(e: MedicalEntryEntity): String {
        val v = e.visitSummary.trim()
        if (v.isNotEmpty()) return v
        val fromNotes = e.content.trim().replace("\n", " ").trim()
        if (fromNotes.length > 280) return fromNotes.take(277) + "…"
        if (fromNotes.isNotEmpty()) return fromNotes
        val ai = e.analysisResult.trim()
        if (ai.isNotEmpty()) return "AI/imaging note: ${ai.take(220)}${if (ai.length > 220) "…" else ""}"
        return "(No summary line — see full chart.)"
    }
}
