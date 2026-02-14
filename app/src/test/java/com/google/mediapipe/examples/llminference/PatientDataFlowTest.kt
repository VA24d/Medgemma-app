package com.google.mediapipe.examples.llminference

import com.google.mediapipe.examples.llminference.data.PatientEntity
import com.google.mediapipe.examples.llminference.data.MedicalEntryEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * Integration-style unit tests for patient data flow
 */
class PatientDataFlowTest {

    @Test
    fun patientWithEntries_canBePaired() {
        val patient = PatientEntity(
            id = 1,
            name = "Test Patient",
            dateOfBirth = "1990-01-01",
            gender = "Male",
            medicalRecordNumber = "MRN001"
        )

        val entries = listOf(
            MedicalEntryEntity(id = 1, patientId = 1, entryType = "XRAY", title = "Chest X-ray"),
            MedicalEntryEntity(id = 2, patientId = 1, entryType = "MANUAL", title = "Follow-up notes"),
            MedicalEntryEntity(id = 3, patientId = 1, entryType = "HISTOPATHOLOGY", title = "Biopsy")
        )

        assertEquals(3, entries.size)
        assertTrue(entries.all { it.patientId == patient.id })
    }

    @Test
    fun entries_canBeFilteredByType() {
        val entries = listOf(
            MedicalEntryEntity(id = 1, patientId = 1, entryType = "XRAY"),
            MedicalEntryEntity(id = 2, patientId = 1, entryType = "MANUAL"),
            MedicalEntryEntity(id = 3, patientId = 1, entryType = "XRAY"),
            MedicalEntryEntity(id = 4, patientId = 1, entryType = "HISTOPATHOLOGY"),
            MedicalEntryEntity(id = 5, patientId = 1, entryType = "MANUAL")
        )

        val xrays = entries.filter { it.entryType == "XRAY" }
        val manuals = entries.filter { it.entryType == "MANUAL" }
        val histos = entries.filter { it.entryType == "HISTOPATHOLOGY" }

        assertEquals(2, xrays.size)
        assertEquals(2, manuals.size)
        assertEquals(1, histos.size)
    }

    @Test
    fun entries_canBeSortedByDate() {
        val entries = listOf(
            MedicalEntryEntity(id = 1, patientId = 1, entryType = "XRAY", createdAt = 1000L),
            MedicalEntryEntity(id = 2, patientId = 1, entryType = "MANUAL", createdAt = 3000L),
            MedicalEntryEntity(id = 3, patientId = 1, entryType = "XRAY", createdAt = 2000L)
        )

        val sorted = entries.sortedByDescending { it.createdAt }
        assertEquals(2L, sorted[0].id) // most recent
        assertEquals(3L, sorted[1].id)
        assertEquals(1L, sorted[2].id) // oldest
    }

    @Test
    fun patientWithMinimalFields_isValid() {
        val patient = PatientEntity(name = "Minimal Patient")
        assertEquals("Minimal Patient", patient.name)
        assertEquals("", patient.dateOfBirth)
        assertEquals("", patient.gender)
        assertEquals("", patient.medicalRecordNumber)
    }

    @Test
    fun entryStatusWorkflow_pendingToAnalyzedToReviewed() {
        val entry = MedicalEntryEntity(patientId = 1, entryType = "XRAY")
        assertEquals("pending", entry.status)

        val analyzed = entry.copy(status = "analyzed", analysisResult = "No issues")
        assertEquals("analyzed", analyzed.status)
        assertTrue(analyzed.analysisResult.isNotBlank())

        val reviewed = analyzed.copy(status = "reviewed")
        assertEquals("reviewed", reviewed.status)
    }

    @Test
    fun diagnosisSummary_canBeComputedFromEntries() {
        val entries = listOf(
            MedicalEntryEntity(id = 1, patientId = 1, entryType = "XRAY", status = "analyzed"),
            MedicalEntryEntity(id = 2, patientId = 1, entryType = "MANUAL", status = "pending"),
            MedicalEntryEntity(id = 3, patientId = 1, entryType = "HISTOPATHOLOGY", status = "reviewed"),
            MedicalEntryEntity(id = 4, patientId = 1, entryType = "XRAY", status = "analyzed")
        )

        val totalEntries = entries.size
        val xrayCount = entries.count { it.entryType == "XRAY" }
        val analyzedCount = entries.count { it.status == "analyzed" }

        assertEquals(4, totalEntries)
        assertEquals(2, xrayCount)
        assertEquals(2, analyzedCount)

        // Build summary string like DiagnosisScreen does
        val parts = mutableListOf<String>()
        if (xrayCount > 0) parts.add("$xrayCount X-ray/MRI")
        val histoCount = entries.count { it.entryType == "HISTOPATHOLOGY" }
        if (histoCount > 0) parts.add("$histoCount Histopathology")
        val summary = parts.joinToString(", ")
        assertEquals("2 X-ray/MRI, 1 Histopathology", summary)
    }

    @Test
    fun patientAvatar_initials_areCorrect() {
        val patients = listOf(
            PatientEntity(name = "John Doe"),
            PatientEntity(name = "A"),
            PatientEntity(name = "Dr. Smith"),
            PatientEntity(name = "")
        )

        assertEquals("JO", patients[0].name.take(2).uppercase())
        assertEquals("A", patients[1].name.take(2).uppercase())
        assertEquals("DR", patients[2].name.take(2).uppercase())
        assertEquals("", patients[3].name.take(2).uppercase())
    }
}
