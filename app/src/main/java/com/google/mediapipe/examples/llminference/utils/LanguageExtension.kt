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

    private val hindiDictionary = mapOf(
        "common cold" to "सामान्य सर्दी",
        "malaria" to "मलेरिया",
        "diabetes" to "मधुमेह",
        "asthma" to "दमा",
        "tuberculosis" to "तपेदिक",
        "hypertension" to "उच्च रक्तचाप",
        "migraine" to "माइग्रेन",
        "anemia" to "खून की कमी",
        "food poisoning" to "खाद्य विषाक्तता",
        "arthritis" to "गठिया",
        "cholera" to "हैजा",
        "dengue" to "डेंगू",
        "typhoid" to "टाइफाइड",
        "jaundice" to "पीलिया",
        "measles" to "खसरा",
        "chickenpox" to "चेचक",
        "diarrhea" to "दस्त",
        "pneumonia" to "निमोनिया",
        "stroke" to "लकवा",
        "cancer" to "कैंसर",
        "cough" to "खांसी",
        "runny nose" to "बहती नाक",
        "fever" to "बुखार",
        "chills" to "ठंड लगना",
        "frequent urination" to "बार-बार पेशाब आना",
        "increased thirst" to "अधिक प्यास लगना",
        "shortness of breath" to "सांस लेने में तकलीफ",
        "wheezing" to "घरघराहट",
        "chronic cough" to "पुरानी खांसी",
        "weight loss" to "वजन कम होना",
        "headache" to "सिरदर्द",
        "dizziness" to "चक्कर आना",
        "severe headache" to "तेज सिरदर्द",
        "nausea" to "जी मिचलाना",
        "fatigue" to "थकान",
        "pale skin" to "पीली त्वचा",
        "vomiting" to "उल्टी",
        "stomach ache" to "पेट दर्द",
        "joint pain" to "जोड़ों का दर्द",
        "swelling" to "सूजन",
        "body ache" to "बदन दर्द",
        "sore throat" to "गले में खराश",
        "sneezing" to "छींक आना",
        "chest pain" to "सीने में दर्द",
        "itching" to "खुजली",
        "constipation" to "कब्ज",
        "bleeding" to "खून बहना",
        "weakness" to "कमजोरी",
        "loss of appetite" to "भूख न लगना",
        "sweating" to "पसीना आना"
    )

    /**
     * Scans the provided text and appends the selected vernacular translation in brackets 
     * immediately following any recognized English symptom or disease.
     * 
     * E.g., "The patient has asthma." (Telugu) -> "The patient has asthma (ఉబ్బసం)."
     */
    fun applyVernacular(text: String, language: String): String {
        if (language == "Off" || text.isBlank()) return text

        val activeDictionary = when (language) {
            "Telugu" -> teluguDictionary
            "Hindi" -> hindiDictionary
            else -> return text
        }

        var processedText = text

        // Iterate through the dictionary and replace occurrences.
        // We use a regex with word boundaries (\b) to ensure we don't partially match words 
        // We also use negative lookahead (?!\s*\() to prevent infinite trailing loops.
        for ((englishTerm, vernacularTerm) in activeDictionary) {
            val pattern = "(?i)\\b(${Pattern.quote(englishTerm)})\\b(?!\\s*\\()"
            val regex = Regex(pattern)
            
            processedText = regex.replace(processedText) { matchResult ->
                val originalCasing = matchResult.groupValues[1]
                "$originalCasing ($vernacularTerm)"
            }
        }

        return processedText
    }
}
