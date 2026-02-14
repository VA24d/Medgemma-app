package com.google.mediapipe.examples.llminference.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
    @Query("SELECT * FROM patients ORDER BY name ASC")
    fun getAllPatients(): Flow<List<PatientEntity>>

    @Query("SELECT * FROM patients WHERE id = :patientId")
    fun getPatient(patientId: Long): Flow<PatientEntity?>

    @Query("SELECT * FROM patients WHERE id = :patientId")
    suspend fun getPatientSync(patientId: Long): PatientEntity?

    @Query("SELECT * FROM patients WHERE name LIKE '%' || :query || '%' OR medicalRecordNumber LIKE '%' || :query || '%'")
    fun searchPatients(query: String): Flow<List<PatientEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatient(patient: PatientEntity): Long

    @Update
    suspend fun updatePatient(patient: PatientEntity)

    @Delete
    suspend fun deletePatient(patient: PatientEntity)

    @Query("DELETE FROM patients")
    suspend fun deleteAllPatients()
}

@Dao
interface MedicalImageDao {
    @Query("SELECT * FROM medical_images WHERE patientId = :patientId ORDER BY captureDate DESC")
    fun getImagesForPatient(patientId: Long): Flow<List<MedicalImageEntity>>

    @Query("SELECT * FROM medical_images WHERE patientId = :patientId AND imageType = :imageType ORDER BY captureDate DESC")
    fun getImagesByType(patientId: Long, imageType: String): Flow<List<MedicalImageEntity>>

    @Query("SELECT * FROM medical_images WHERE id = :imageId")
    suspend fun getImage(imageId: Long): MedicalImageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: MedicalImageEntity): Long

    @Update
    suspend fun updateImage(image: MedicalImageEntity)

    @Delete
    suspend fun deleteImage(image: MedicalImageEntity)

    @Query("DELETE FROM medical_images WHERE patientId = :patientId")
    suspend fun deleteAllImagesForPatient(patientId: Long)

    @Query("DELETE FROM medical_images")
    suspend fun deleteAllImages()
}

@Dao
interface ConsultationDao {
    @Query("SELECT * FROM consultations WHERE patientId = :patientId ORDER BY consultationDate DESC")
    fun getConsultationsForPatient(patientId: Long): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE id = :consultationId")
    suspend fun getConsultation(consultationId: Long): ConsultationEntity?

    @Query("SELECT * FROM consultations WHERE patientId = :patientId ORDER BY consultationDate DESC LIMIT 1")
    suspend fun getLatestConsultation(patientId: Long): ConsultationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsultation(consultation: ConsultationEntity): Long

    @Update
    suspend fun updateConsultation(consultation: ConsultationEntity)

    @Delete
    suspend fun deleteConsultation(consultation: ConsultationEntity)

    @Query("DELETE FROM consultations WHERE patientId = :patientId")
    suspend fun deleteConsultationsForPatient(patientId: Long)

    @Query("DELETE FROM consultations")
    suspend fun deleteAllConsultations()
}

@Dao
interface MedicalEntryDao {
    @Query("SELECT * FROM medical_entries WHERE patientId = :patientId ORDER BY createdAt DESC")
    fun getEntriesForPatient(patientId: Long): Flow<List<MedicalEntryEntity>>

    @Query("SELECT * FROM medical_entries WHERE id = :entryId")
    suspend fun getEntry(entryId: Long): MedicalEntryEntity?

    @Query("SELECT * FROM medical_entries WHERE patientId = :patientId AND entryType = :type ORDER BY createdAt DESC")
    fun getEntriesByType(patientId: Long, type: String): Flow<List<MedicalEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: MedicalEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: MedicalEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: MedicalEntryEntity)

    @Query("DELETE FROM medical_entries WHERE patientId = :patientId")
    suspend fun deleteAllEntriesForPatient(patientId: Long)

    @Query("SELECT COUNT(*) FROM medical_entries WHERE patientId = :patientId")
    suspend fun getEntryCount(patientId: Long): Int

    @Query("DELETE FROM medical_entries")
    suspend fun deleteAllEntries()
}
