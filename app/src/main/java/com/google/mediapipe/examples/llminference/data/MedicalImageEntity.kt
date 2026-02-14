package com.google.mediapipe.examples.llminference.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ImageType {
    XRAY,
    MRI,
    HISTOPATHOLOGY,
    CT_SCAN,
    ULTRASOUND,
    OTHER
}

@Entity(
    tableName = "medical_images",
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
data class MedicalImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val patientId: Long,
    val imageType: String, // Store as String for Room compatibility
    val filePath: String, // Local file path to the image
    val fileName: String,
    val captureDate: Long = System.currentTimeMillis(),
    val bodyPart: String = "", // e.g., "Chest", "Skull", "Abdomen"
    val notes: String = "",
    val aiAnalysis: String = "", // Store AI-generated analysis
    val createdAt: Long = System.currentTimeMillis()
)
