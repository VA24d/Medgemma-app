package com.google.mediapipe.examples.llminference

import com.google.mediapipe.examples.llminference.data.MedicalEntryEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for MedicalEntryEntity data model
 */
class MedicalEntryEntityTest {

    @Test
    fun medicalEntry_creation_isCorrect() {
        val entry = MedicalEntryEntity(
            id = 1,
            patientId = 42,
            entryType = "XRAY",
            title = "Chest X-ray",
            content = "Body Part: Chest\nContext: Persistent cough",
            imagePaths = "/path/to/image.jpg",
            analysisResult = "No abnormalities detected",
            status = "analyzed"
        )

        assertEquals(1L, entry.id)
        assertEquals(42L, entry.patientId)
        assertEquals("XRAY", entry.entryType)
        assertEquals("Chest X-ray", entry.title)
        assertEquals("No abnormalities detected", entry.analysisResult)
        assertEquals("analyzed", entry.status)
    }

    @Test
    fun medicalEntry_defaultValues_areCorrect() {
        val entry = MedicalEntryEntity(
            patientId = 1,
            entryType = "MANUAL"
        )

        assertEquals(0L, entry.id)
        assertEquals("", entry.title)
        assertEquals("", entry.content)
        assertEquals("", entry.imagePaths)
        assertEquals("", entry.analysisResult)
        assertEquals("pending", entry.status)
        assertTrue(entry.createdAt > 0)
        assertTrue(entry.updatedAt > 0)
    }

    @Test
    fun medicalEntry_timestampsAreSet() {
        val beforeCreation = System.currentTimeMillis()
        val entry = MedicalEntryEntity(
            patientId = 1,
            entryType = "HISTOPATHOLOGY"
        )
        val afterCreation = System.currentTimeMillis()

        assertTrue(entry.createdAt >= beforeCreation)
        assertTrue(entry.createdAt <= afterCreation)
        assertTrue(entry.updatedAt >= beforeCreation)
        assertTrue(entry.updatedAt <= afterCreation)
    }

    @Test
    fun medicalEntry_allEntryTypes_areValid() {
        val validTypes = listOf("XRAY", "HISTOPATHOLOGY", "RECORDING", "DOCUMENT", "MANUAL")

        for (type in validTypes) {
            val entry = MedicalEntryEntity(patientId = 1, entryType = type)
            assertEquals(type, entry.entryType)
        }
    }

    @Test
    fun medicalEntry_allStatuses_areValid() {
        val validStatuses = listOf("pending", "analyzed", "reviewed")

        for (status in validStatuses) {
            val entry = MedicalEntryEntity(patientId = 1, entryType = "MANUAL", status = status)
            assertEquals(status, entry.status)
        }
    }

    @Test
    fun medicalEntry_multipleImagePaths_areSerialized() {
        val paths = "/path/1.jpg,/path/2.jpg,/path/3.jpg"
        val entry = MedicalEntryEntity(
            patientId = 1,
            entryType = "XRAY",
            imagePaths = paths
        )

        val parsedPaths = entry.imagePaths.split(",")
        assertEquals(3, parsedPaths.size)
        assertEquals("/path/1.jpg", parsedPaths[0])
        assertEquals("/path/2.jpg", parsedPaths[1])
        assertEquals("/path/3.jpg", parsedPaths[2])
    }

    @Test
    fun medicalEntry_emptyImagePaths_handledGracefully() {
        val entry = MedicalEntryEntity(patientId = 1, entryType = "MANUAL")
        assertTrue(entry.imagePaths.isEmpty())

        val parsedPaths = entry.imagePaths.split(",").filter { it.isNotBlank() }
        assertEquals(0, parsedPaths.size)
    }

    @Test
    fun medicalEntry_longContent_isPreserved() {
        val longContent = "A".repeat(10000)
        val entry = MedicalEntryEntity(
            patientId = 1,
            entryType = "MANUAL",
            content = longContent
        )
        assertEquals(10000, entry.content.length)
    }

    @Test
    fun medicalEntry_copy_worksCorrectly() {
        val original = MedicalEntryEntity(
            id = 5,
            patientId = 10,
            entryType = "XRAY",
            title = "Original",
            status = "pending"
        )
        val updated = original.copy(
            status = "analyzed",
            analysisResult = "Fracture detected"
        )

        assertEquals(5L, updated.id)
        assertEquals(10L, updated.patientId)
        assertEquals("analyzed", updated.status)
        assertEquals("Fracture detected", updated.analysisResult)
        assertEquals("Original", updated.title)
    }
}
