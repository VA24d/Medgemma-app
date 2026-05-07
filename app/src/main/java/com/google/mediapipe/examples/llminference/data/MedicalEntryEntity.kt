package com.google.mediapipe.examples.llminference.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Unified medical entry entity for all analysis types.
 */
@Entity(
    tableName = "medical_entries",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("patientId")]
)
data class MedicalEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val patientId: Long,
    val entryType: String,          // XRAY, HISTOPATHOLOGY, RECORDING, DOCUMENT, MANUAL
    val title: String = "",
    val content: String = "",       // Notes or transcription
    val imagePaths: String = "",    // Comma-separated image file paths
    val analysisResult: String = "", // AI analysis output
    /** One or two sentences for fast longitudinal prompts (hand-written or synced). */
    val visitSummary: String = "",
    val status: String = "pending", // pending, analyzed, reviewed
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
