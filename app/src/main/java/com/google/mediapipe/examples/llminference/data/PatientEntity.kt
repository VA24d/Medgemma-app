package com.google.mediapipe.examples.llminference.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "patients")
data class PatientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dateOfBirth: String = "", // Format: YYYY-MM-DD
    val gender: String = "", // Male, Female, Other
    val medicalRecordNumber: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val address: String = "",
    val bloodGroup: String = "",
    val allergies: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
