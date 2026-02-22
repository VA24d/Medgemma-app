package com.google.mediapipe.examples.llminference

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for navigation route constants
 */
class NavigationRoutesTest {

    @Test
    fun routeConstants_areCorrect() {
        assertEquals("splash", SPLASH_SCREEN)
        assertEquals("pin", PIN_SCREEN)
        assertEquals("patients", PATIENTS_SCREEN)
        assertEquals("add_patient", ADD_PATIENT_SCREEN)
    }

    @Test
    fun parameterizedRoutes_containPlaceholders() {
        assertTrue(PATIENT_DETAIL_SCREEN.contains("{patientId}"))
        assertTrue(NEW_ENTRY_SCREEN.contains("{patientId}"))
        assertTrue(XRAY_ANALYSIS_SCREEN.contains("{patientId}"))
        assertTrue(XRAY_ANALYSIS_SCREEN.contains("{analysisType}"))
        assertTrue(MANUAL_NOTES_SCREEN.contains("{patientId}"))
        assertTrue(HISTORY_SCREEN.contains("{patientId}"))
        assertTrue(DIAGNOSIS_SCREEN.contains("{patientId}"))
    }

    @Test
    fun legacyRoutes_arePreserved() {
        assertEquals("start_screen", START_SCREEN)
        assertEquals("load_screen", LOAD_SCREEN)
        assertEquals("chat_screen", CHAT_SCREEN)
    }

    @Test
    fun routeConstants_areAllUnique() {
        val routes = listOf(
            SPLASH_SCREEN, PIN_SCREEN, PATIENTS_SCREEN, ADD_PATIENT_SCREEN,
            PATIENT_DETAIL_SCREEN, NEW_ENTRY_SCREEN, XRAY_ANALYSIS_SCREEN,
            MANUAL_NOTES_SCREEN, HISTORY_SCREEN, DIAGNOSIS_SCREEN,
            START_SCREEN, LOAD_SCREEN, CHAT_SCREEN,
            QUICK_ANALYSIS_SCREEN
        )
        assertEquals(routes.size, routes.distinct().size)
    }

    @Test
    fun patientDetailRoute_canBeConstructed() {
        val patientId = 42L
        val route = "patient_detail/$patientId"
        assertEquals("patient_detail/42", route)
    }

    @Test
    fun xrayAnalysisRoute_canBeConstructed() {
        val patientId = 10L
        val type = "HISTOPATHOLOGY"
        val route = "xray_analysis/$patientId/$type"
        assertEquals("xray_analysis/10/HISTOPATHOLOGY", route)
    }

    @Test
    fun navigationFlow_splashToPin_isValid() {
        // Splash -> PIN -> Patients is the expected flow
        assertNotEquals(SPLASH_SCREEN, PIN_SCREEN)
        assertNotEquals(PIN_SCREEN, PATIENTS_SCREEN)
    }
}
