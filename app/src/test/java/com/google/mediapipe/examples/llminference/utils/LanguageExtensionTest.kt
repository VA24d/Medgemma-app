package com.google.mediapipe.examples.llminference.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageExtensionTest {

    @Test
    fun `applyVernacular appends translation to simple symptom`() {
        // "Headache" -> "Headache (తలనొప్పి)"
        val input = "The patient reports a headache."
        val expected = "The patient reports a headache (తలనొప్పి)."
        val result = LanguageExtension.applyVernacular(input, true)
        assertEquals(expected, result)
    }

    @Test
    fun `applyVernacular is case insensitive and preserves original casing`() {
        val input = "SEVERE HEADACHE and Asthma."
        val expected = "SEVERE HEADACHE (తీవ్రమైన తలనొప్పి) and Asthma (ఉబ్బసం)."
        val result = LanguageExtension.applyVernacular(input, true)
        assertEquals(expected, result)
    }

    @Test
    fun `applyVernacular does not double translate strings already containing brackets`() {
        // Should not become "Headache (తలనొప్పి) (తలనొప్పి)"
        val input = "Headache (తలనొప్పి) is severe."
        val expected = "Headache (తలనొప్పి) is severe."
        val result = LanguageExtension.applyVernacular(input, true)
        assertEquals(expected, result)
    }

    @Test
    fun `applyVernacular returns original string if disabled`() {
        val input = "The patient reports a headache."
        val result = LanguageExtension.applyVernacular(input, false)
        assertEquals(input, result)
    }

    @Test
    fun `applyVernacular uses word boundaries to prevent partial matches`() {
        // "he" is inside "headache", so "he" shouldn't trigger anything unless it's a distinct word
        val input = "His headache is bad."
        
        // Let's pretend we had a mapping for "he". In this specific test, we just ensure 
        // "headache" translates properly and doesn't get messed up.
        val expected = "His headache (తలనొప్పి) is bad."
        val result = LanguageExtension.applyVernacular(input, true)
        assertEquals(expected, result)
    }
}
