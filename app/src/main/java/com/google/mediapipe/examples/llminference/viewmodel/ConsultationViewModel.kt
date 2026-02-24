package com.google.mediapipe.examples.llminference.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.examples.llminference.InferenceModel
import com.google.mediapipe.examples.llminference.ai.MedicalPromptTemplates
import com.google.mediapipe.examples.llminference.data.*
import com.google.mediapipe.examples.llminference.repository.ConsultationRepository
import com.google.mediapipe.examples.llminference.repository.PatientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConsultationViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = MedicalDatabase.getDatabase(application)
    private val consultationRepository = ConsultationRepository(database.consultationDao())
    private val patientRepository = PatientRepository(
        database.patientDao(),
        database.medicalImageDao(),
        database.consultationDao()
    )
    
    private val visionAnalyzer = com.google.mediapipe.examples.llminference.vision.MedicalImageAnalyzer(application)
    private var inferenceModel: InferenceModel? = null
    
    init {
        // Initialize Vision models (these would be downloaded or bundled with app)
        // Note: You'll need to add actual model files to assets
        try {
            visionAnalyzer.initializeClassifier()
            visionAnalyzer.initializeObjectDetector()
        } catch (e: Exception) {
            // Models not available, will use text-only analysis
        }
    }

    private val _patient = MutableStateFlow<PatientEntity?>(null)
    val patient: StateFlow<PatientEntity?> = _patient.asStateFlow()

    private val _chiefComplaint = MutableStateFlow("")
    val chiefComplaint: StateFlow<String> = _chiefComplaint.asStateFlow()

    private val _symptoms = MutableStateFlow("")
    val symptoms: StateFlow<String> = _symptoms.asStateFlow()

    private val _vitalSigns = MutableStateFlow("")
    val vitalSigns: StateFlow<String> = _vitalSigns.asStateFlow()

    private val _diagnosis = MutableStateFlow("")
    val diagnosis: StateFlow<String> = _diagnosis.asStateFlow()

    private val _prognosis = MutableStateFlow("")
    val prognosis: StateFlow<String> = _prognosis.asStateFlow()

    private val _aiSuggestions = MutableStateFlow("")
    val aiSuggestions: StateFlow<String> = _aiSuggestions.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _isGeneratingAI = MutableStateFlow(false)
    val isGeneratingAI: StateFlow<Boolean> = _isGeneratingAI.asStateFlow()

    private val _selectedImages = MutableStateFlow<List<MedicalImageEntity>>(emptyList())
    val selectedImages: StateFlow<List<MedicalImageEntity>> = _selectedImages.asStateFlow()

    fun setInferenceModel(model: InferenceModel) {
        inferenceModel = model
    }

    fun loadPatient(patientId: Long) {
        viewModelScope.launch {
            val patientEntity = patientRepository.getPatientSync(patientId)
            _patient.value = patientEntity
        }
    }

    fun updateChiefComplaint(text: String) {
        _chiefComplaint.value = text
    }

    fun updateSymptoms(text: String) {
        _symptoms.value = text
    }

    fun updateVitalSigns(text: String) {
        _vitalSigns.value = text
    }

    fun updateDiagnosis(text: String) {
        _diagnosis.value = text
    }

    fun updatePrognosis(text: String) {
        _prognosis.value = text
    }

    fun updateNotes(text: String) {
        _notes.value = text
    }

    fun addSelectedImage(image: MedicalImageEntity) {
        _selectedImages.value = _selectedImages.value + image
    }

    fun removeSelectedImage(image: MedicalImageEntity) {
        _selectedImages.value = _selectedImages.value.filter { it.id != image.id }
    }

    /**
     * Generate AI prognosis suggestions with multimodal analysis
     * Combines MediaPipe Vision image analysis with MedGemma 1.5 4B text generation
     */
    fun generateAIPrognosis() {
        val currentPatient = _patient.value ?: return
        val model = inferenceModel ?: return

        viewModelScope.launch {
            _isGeneratingAI.value = true
            _aiSuggestions.value = ""
            
            try {
                // Step 1: Analyze medical images with MediaPipe Vision
                val visionAnalyses = mutableListOf<com.google.mediapipe.examples.llminference.vision.MedicalImageAnalysis>()
                
                for (imageEntity in _selectedImages.value) {
                    try {
                        // Load bitmap from file path
                        val bitmap = android.graphics.BitmapFactory.decodeFile(imageEntity.filePath)
                        if (bitmap != null) {
                            val analysis = visionAnalyzer.analyzeImage(bitmap)
                            visionAnalyses.add(analysis)
                        }
                    } catch (e: Exception) {
                        // Skip this image if loading fails
                    }
                }
                
                // Step 2: Create enhanced prompt with Vision findings
                val prompt = if (visionAnalyses.isNotEmpty()) {
                    // Use multimodal prompt with Vision analysis
                    val combinedAnalysis = visionAnalyses.first() // Use first image for now
                    com.google.mediapipe.examples.llminference.vision.MultimodalMedicalPrompts.prognosisWithImaging(
                        patient = currentPatient,
                        chiefComplaint = _chiefComplaint.value,
                        symptoms = _symptoms.value,
                        vitalSigns = _vitalSigns.value,
                        imagingAnalysis = combinedAnalysis
                    )
                } else {
                    // Fallback to text-only prompt
                    MedicalPromptTemplates.prognosisPrompt(
                        patient = currentPatient,
                        chiefComplaint = _chiefComplaint.value,
                        symptoms = _symptoms.value,
                        vitalSigns = _vitalSigns.value,
                        imagingFindings = _selectedImages.value.joinToString("\n") { 
                            "${it.imageType} - ${it.bodyPart}: ${it.notes}" 
                        }
                    )
                }

                // Step 3: Generate AI text response with MedGemma 1.5 4B
                model.generateResponseAsync(prompt, emptyList()) { partialResult, isDone ->
                    _aiSuggestions.value += partialResult
                }.get()
                
            } catch (e: Exception) {
                _aiSuggestions.value = "Error generating AI suggestions: ${e.message}"
            } finally {
                _isGeneratingAI.value = false
            }
        }
    }
    
    /**
     * Analyze a single medical image and get Vision findings
     */
    fun analyzeImage(bitmap: Bitmap, callback: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val analysis = visionAnalyzer.analyzeImage(bitmap)
                callback(analysis.toPromptText())
            } catch (e: Exception) {
                callback("Image analysis error: ${e.message}")
            }
        }
    }

    /**
     * Save consultation to database
     */
    fun saveConsultation() {
        val currentPatient = _patient.value ?: return

        viewModelScope.launch {
            val consultation = ConsultationEntity(
                patientId = currentPatient.id,
                chiefComplaint = _chiefComplaint.value,
                symptoms = _symptoms.value,
                vitalSigns = _vitalSigns.value,
                diagnosis = _diagnosis.value,
                prognosis = _prognosis.value,
                aiSuggestions = _aiSuggestions.value,
                notes = _notes.value
            )
            
            consultationRepository.insertConsultation(consultation)
        }
    }

    override fun onCleared() {
        super.onCleared()
        visionAnalyzer.close()
    }
}
