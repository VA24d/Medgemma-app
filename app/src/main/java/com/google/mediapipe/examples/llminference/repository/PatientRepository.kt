package com.google.mediapipe.examples.llminference.repository

import com.google.mediapipe.examples.llminference.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class PatientRepository(
    private val patientDao: PatientDao,
    private val imageDao: MedicalImageDao,
    private val consultationDao: ConsultationDao
) {
    // Patient operations
    fun getAllPatients(): Flow<List<PatientEntity>> = patientDao.getAllPatients()

    fun getPatient(patientId: Long): Flow<PatientEntity?> = patientDao.getPatient(patientId)

    suspend fun getPatientSync(patientId: Long): PatientEntity? = patientDao.getPatientSync(patientId)

    fun searchPatients(query: String): Flow<List<PatientEntity>> = patientDao.searchPatients(query)

    suspend fun insertPatient(patient: PatientEntity): Long = patientDao.insertPatient(patient)

    suspend fun updatePatient(patient: PatientEntity) {
        patientDao.updatePatient(patient.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deletePatient(patient: PatientEntity) {
        patientDao.deletePatient(patient)
    }

    // Get patient with complete history
    fun getPatientWithHistory(patientId: Long): Flow<PatientWithHistory?> {
        return combine(
            patientDao.getPatient(patientId),
            imageDao.getImagesForPatient(patientId),
            consultationDao.getConsultationsForPatient(patientId)
        ) { patient, images, consultations ->
            patient?.let {
                PatientWithHistory(
                    patient = it,
                    images = images,
                    consultations = consultations
                )
            }
        }
    }
}
