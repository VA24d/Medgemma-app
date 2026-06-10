package com.google.mediapipe.examples.llminference.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted AI-generated diagnosis for a patient.
 *
 * [scope] = "FULL"        — generated from all entries (on-device)
 * [scope] = "INCREMENTAL" — generated from entries added since the previous diagnosis
 * [scope] = "CLOUD_FULL"  — longitudinal synthesis from edge companion GPU enrichment
 */
@Entity(
    tableName = "diagnoses",
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
data class DiagnosisEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val patientId: Long,
    val diagnosis: String,
    val generatedAt: Long = System.currentTimeMillis(),
    /** "FULL" or "INCREMENTAL" */
    val scope: String = "FULL",
    /** How many medical entries were considered */
    val entryCount: Int = 0,
    /** Human-readable model identifier shown in the UI */
    val modelName: String = "",
    /** Whether thinking mode was active during generation */
    val thinkingEnabled: Boolean = false
)
