package com.google.mediapipe.examples.llminference.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "consultations",
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
data class ConsultationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val patientId: Long,
    val consultationDate: Long = System.currentTimeMillis(),
    val chiefComplaint: String,
    val symptoms: String,
    val vitalSigns: String = "", // JSON or formatted string with BP, temp, pulse, etc.
    val diagnosis: String = "",
    val prognosis: String = "",
    val aiSuggestions: String = "", // AI-generated prognosis and suggestions
    val prescriptions: String = "",
    val followUpDate: Long? = null,
    val notes: String = "",
    val voiceNotes: String = "", // Path to voice recording if any
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
