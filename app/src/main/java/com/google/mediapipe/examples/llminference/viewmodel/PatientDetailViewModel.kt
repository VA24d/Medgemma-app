package com.google.mediapipe.examples.llminference.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.examples.llminference.data.*
import com.google.mediapipe.examples.llminference.repository.MedicalImageRepository
import com.google.mediapipe.examples.llminference.repository.PatientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PatientDetailViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = MedicalDatabase.getDatabase(application)
    private val patientRepository = PatientRepository(
        database.patientDao(),
        database.medicalImageDao(),
        database.consultationDao()
    )
    private val imageRepository = MedicalImageRepository(database.medicalImageDao(), application)

    private val _patientWithHistory = MutableStateFlow<PatientWithHistory?>(null)
    val patientWithHistory: StateFlow<PatientWithHistory?> = _patientWithHistory.asStateFlow()

    private val _selectedImageId = MutableStateFlow<Long?>(null)
    val selectedImageId: StateFlow<Long?> = _selectedImageId.asStateFlow()

    fun loadPatient(patientId: Long) {
        viewModelScope.launch {
            patientRepository.getPatientWithHistory(patientId).collect { history ->
                _patientWithHistory.value = history
            }
        }
    }

    fun addImage(
        patientId: Long,
        imageType: String,
        bitmap: Bitmap,
        bodyPart: String = "",
        notes: String = ""
    ) {
        viewModelScope.launch {
            imageRepository.saveImage(patientId, imageType, bitmap, bodyPart, notes)
        }
    }

    fun deleteImage(image: MedicalImageEntity) {
        viewModelScope.launch {
            imageRepository.deleteImage(image)
        }
    }

    fun selectImage(imageId: Long?) {
        _selectedImageId.value = imageId
    }

    suspend fun loadImageBitmap(imagePath: String): Bitmap? {
        return imageRepository.loadBitmap(imagePath)
    }
}
