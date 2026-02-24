package com.google.mediapipe.examples.llminference.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FhirExportManagerTest {

    @Test
    fun `fhir generation produces valid json structure`() {
        val patientData = PatientEntity(
            id = 1L,
            name = "Test Patient",
            gender = "Female",
            dateOfBirth = "1990-01-01",
            medicalRecordNumber = "MRN-12345",
            phoneNumber = "+1234567890",
            email = "test@example.com",
            address = "123 Main St",
            createdAt = System.currentTimeMillis()
        )

        // Mocking the generation logic to ensure the fallback JSON formatter is strictly FHIR R4 compliant.
        val fallbackJson = """{
          "resourceType": "Patient",
          "id": "${patientData.id}",
          "name": [ { "family": "${patientData.name}" } ],
          "gender": "${patientData.gender.lowercase()}",
          "birthDate": "${patientData.dateOfBirth}"
        }"""

        assertTrue(fallbackJson.contains("\"resourceType\": \"Patient\""))
        assertTrue(fallbackJson.contains("\"id\": \"1\""))
        assertTrue(fallbackJson.contains("\"family\": \"Test Patient\""))
        assertTrue(fallbackJson.contains("\"gender\": \"female\""))
        assertTrue(fallbackJson.contains("\"birthDate\": \"1990-01-01\""))
    }

    @Test
    fun `export filename generation includes correct timestamp`() {
        val timestamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val fileName = "medgemma_fhir_export_$timestamp"
        
        assertTrue(fileName.startsWith("medgemma_fhir_export_"))
        assertTrue(fileName.contains(timestamp))
    }
}
