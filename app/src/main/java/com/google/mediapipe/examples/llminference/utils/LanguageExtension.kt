package com.google.mediapipe.examples.llminference.utils

import java.util.regex.Pattern

object LanguageExtension {

    // A dictionary mapping English clinical terms to their Telugu equivalents.
    // The keys are normalized to lowercase for case-insensitive matching.
    private val teluguDictionary = mapOf(
        "cough" to "దగ్గు",
        "runny nose" to "ముక్కు కారడం",
        "fever" to "జ్వరం",
        "chills" to "చలి",
        "frequent urination" to "తరచుగా మూత్రవిసర్జన",
        "increased thirst" to "అధిక దాహం",
        "shortness of breath" to "శ్వాస తీసుకోవడంలో ఇబ్బంది",
        "wheezing" to "గురక",
        "chronic cough" to "దీర్ఘకాలిక దగ్గు",
        "weight loss" to "బరువు తగ్గడం",
        "headache" to "తలనొప్పి",
        "dizziness" to "తలతిరగడం",
        "severe headache" to "తీవ్రమైన తలనొప్పి",
        "nausea" to "వికారం",
        "fatigue" to "అలసట",
        "pale skin" to "పాలిపోయిన చర్మం",
        "vomiting" to "వాంతులు",
        "stomach ache" to "కడుపు నొప్పి",
        "joint pain" to "కీళ్ల నొప్పి",
        "swelling" to "వాపు",
        "common cold" to "సాధారణ జలుబు",
        "malaria" to "మలేరియా",
        "diabetes" to "మధుమేహం",
        "asthma" to "ఉబ్బసం",
        "tuberculosis" to "క్షయ వ్యాధి",
        "hypertension" to "అధిక రక్తపోటు",
        "migraine" to "పార్శ్వపు నొప్పి",
        "anemia" to "రక్తహీనత",
        "food poisoning" to "ఫుడ్ పాయిజనింగ్",
        "arthritis" to "కీళ్లవాతం"
    )

    /**
     * Scans the provided text and appends the Telugu translation in brackets 
     * immediately following any recognized English symptom or disease.
     * 
     * E.g., "The patient has asthma." -> "The patient has asthma (ఉబ్బసం)."
     */
    fun applyVernacular(text: String, isEnabled: Boolean): String {
        if (!isEnabled || text.isBlank()) return text

        var processedText = text

        // Iterate through the dictionary and replace occurrences.
        // We use a regex with word boundaries (\b) to ensure we don't partially match words 
        // (like matching "he" inside "headache"). 
        // We also use negative lookahead (?!\s*\() to ensure we don't append the translation 
        // if it already exists (preventing infinite loops when streaming chunks).
        for ((englishTerm, teluguTerm) in teluguDictionary) {
            // Pattern: match the english term, case insensitive, ignoring if it is immediately followed by ' ('
            val pattern = "(?i)\\b(${Pattern.quote(englishTerm)})\\b(?!\\s*\\()"
            val regex = Regex(pattern)
            
            processedText = regex.replace(processedText) { matchResult ->
                val originalCasing = matchResult.groupValues[1]
                "$originalCasing ($teluguTerm)"
            }
        }

        return processedText
    }
}
