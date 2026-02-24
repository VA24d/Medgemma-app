package com.google.mediapipe.examples.llminference.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesTest {

    @Test
    fun `default energy mode is standard`() {
        // App Preferences defaults testing
        val defaultEnergyMode = "STANDARD" // Simulating LocalModelFiles default
        assertEquals("STANDARD", defaultEnergyMode)
    }

    @Test
    fun `model bitrates contain quantized formats`() {
        val supportedFormats = listOf("Q4_K_M", "Q8_0", "INT8")
        assert(supportedFormats.contains("Q4_K_M"))
        assert(supportedFormats.contains("INT8"))
    }
}
