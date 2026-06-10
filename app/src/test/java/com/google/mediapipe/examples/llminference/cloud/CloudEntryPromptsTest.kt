package com.google.mediapipe.examples.llminference.cloud

import com.google.mediapipe.examples.llminference.data.MedicalEntryEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudEntryPromptsTest {

    @Test
    fun needsVisionProcessing_whenNoAnalysis() {
        val e = MedicalEntryEntity(
            patientId = 1,
            entryType = "XRAY",
            imagePaths = "/files/x.jpg",
            analysisResult = "",
        )
        assertTrue(CloudEntryPrompts.needsVisionProcessing(e, force = false))
    }

    @Test
    fun skipsVision_whenAlreadyCloudProcessed() {
        val e = MedicalEntryEntity(
            patientId = 1,
            entryType = "XRAY",
            imagePaths = "/files/x.jpg",
            analysisResult = "done",
            cloudProcessedAt = 1000L,
        )
        assertFalse(CloudEntryPrompts.needsVisionProcessing(e, force = false))
    }
}
