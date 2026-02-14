package com.google.mediapipe.examples.llminference

import com.google.mediapipe.examples.llminference.data.PatientEntity
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*

/**
 * Unit tests for Patient data models
 */
class PatientEntityTest {

    @Test
    fun patientEntity_creation_isCorrect() {
        val patient = PatientEntity(
            id = 1,
            name = "John Doe",
            dateOfBirth = "1990-05-15",
            gender = "Male",
            medicalRecordNumber = "MRN001",
            phoneNumber = "+1234567890",
            email = "john@example.com",
            bloodGroup = "O+",
            allergies = "Penicillin"
        )

        assertEquals("John Doe", patient.name)
        assertEquals("1990-05-15", patient.dateOfBirth)
        assertEquals("Male", patient.gender)
        assertEquals("MRN001", patient.medicalRecordNumber)
        assertEquals("O+", patient.bloodGroup)
        assertEquals("Penicillin", patient.allergies)
    }

    @Test
    fun patientEntity_defaultValues_areCorrect() {
        val patient = PatientEntity(
            name = "Jane Smith",
            dateOfBirth = "1985-03-20",
            gender = "Female",
            medicalRecordNumber = "MRN002"
        )

        assertEquals(0L, patient.id)
        assertEquals("", patient.phoneNumber)
        assertEquals("", patient.email)
        assertEquals("", patient.address)
        assertEquals("", patient.bloodGroup)
        assertEquals("", patient.allergies)
        assertEquals("", patient.notes)
        assertTrue(patient.createdAt > 0)
        assertTrue(patient.updatedAt > 0)
    }

    @Test
    fun patientEntity_timestampsAreSet() {
        val beforeCreation = System.currentTimeMillis()
        val patient = PatientEntity(
            name = "Test Patient",
            dateOfBirth = "2000-01-01",
            gender = "Other",
            medicalRecordNumber = "TEST001"
        )
        val afterCreation = System.currentTimeMillis()

        assertTrue(patient.createdAt >= beforeCreation)
        assertTrue(patient.createdAt <= afterCreation)
        assertTrue(patient.updatedAt >= beforeCreation)
        assertTrue(patient.updatedAt <= afterCreation)
    }
}
