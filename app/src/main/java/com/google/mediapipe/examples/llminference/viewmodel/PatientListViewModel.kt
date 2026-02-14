package com.google.mediapipe.examples.llminference.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.examples.llminference.data.MedicalDatabase
import com.google.mediapipe.examples.llminference.data.PatientEntity
import com.google.mediapipe.examples.llminference.repository.PatientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PatientListViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = MedicalDatabase.getDatabase(application)
    private val repository = PatientRepository(
        database.patientDao(),
        database.medicalImageDao(),
        database.consultationDao()
    )

    private val _patients = MutableStateFlow<List<PatientEntity>>(emptyList())
    val patients: StateFlow<List<PatientEntity>> = _patients.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        loadPatients()
    }

    private fun loadPatients() {
        viewModelScope.launch {
            repository.getAllPatients().collect { patientList ->
                _patients.value = patientList
            }
        }
    }

    fun searchPatients(query: String) {
        _searchQuery.value = query
        if (query.isEmpty()) {
            loadPatients()
        } else {
            viewModelScope.launch {
                repository.searchPatients(query).collect { results ->
                    _patients.value = results
                }
            }
        }
    }

    fun addPatient(patient: PatientEntity) {
        viewModelScope.launch {
            repository.insertPatient(patient)
        }
    }

    fun deletePatient(patient: PatientEntity) {
        viewModelScope.launch {
            repository.deletePatient(patient)
        }
    }
}
