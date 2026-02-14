package com.google.mediapipe.examples.llminference.repository

import com.google.mediapipe.examples.llminference.data.ConsultationDao
import com.google.mediapipe.examples.llminference.data.ConsultationEntity
import kotlinx.coroutines.flow.Flow

class ConsultationRepository(
    private val consultationDao: ConsultationDao
) {
    fun getConsultationsForPatient(patientId: Long): Flow<List<ConsultationEntity>> {
        return consultationDao.getConsultationsForPatient(patientId)
    }

    suspend fun getConsultation(consultationId: Long): ConsultationEntity? {
        return consultationDao.getConsultation(consultationId)
    }

    suspend fun getLatestConsultation(patientId: Long): ConsultationEntity? {
        return consultationDao.getLatestConsultation(patientId)
    }

    suspend fun insertConsultation(consultation: ConsultationEntity): Long {
        return consultationDao.insertConsultation(consultation)
    }

    suspend fun updateConsultation(consultation: ConsultationEntity) {
        consultationDao.updateConsultation(
            consultation.copy(updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun deleteConsultation(consultation: ConsultationEntity) {
        consultationDao.deleteConsultation(consultation)
    }
}
