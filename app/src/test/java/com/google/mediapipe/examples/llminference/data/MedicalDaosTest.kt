package com.google.mediapipe.examples.llminference.data

import org.junit.Assert.assertTrue
import org.junit.Test

class MedicalDaosTest {

    @Test
    fun `patient entity correctly structures basic schema`() {
        val patient = PatientEntity(
            name = "Jane Doe",
            gender = "Female"
        )
        
        assertTrue(patient.name == "Jane Doe")
        assertTrue(patient.id == 0L) // Default auto-generate primary key
    }

    @Test
    fun `medical entry entity binds to patient id`() {
        val entry = MedicalEntryEntity(
            patientId = 42L,
            title = "Chest X-Ray",
            content = "Clear lungs",
            entryType = "Imaging"
        )
        
        assertTrue(entry.patientId == 42L)
        assertTrue(entry.entryType == "Imaging")
    }
}
