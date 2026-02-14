package com.google.mediapipe.examples.llminference.data

/**
 * Data class combining patient with their medical history
 */
data class PatientWithHistory(
    val patient: PatientEntity,
    val images: List<MedicalImageEntity> = emptyList(),
    val consultations: List<ConsultationEntity> = emptyList()
)

/**
 * Data class for consultation with associated images
 */
data class ConsultationWithImages(
    val consultation: ConsultationEntity,
    val images: List<MedicalImageEntity> = emptyList()
)
