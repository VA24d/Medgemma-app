package com.google.mediapipe.examples.llminference

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.mediapipe.examples.llminference.settings.LocalModelFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val inferenceModel: InferenceModel,
    private val appContext: Context
) : ViewModel() {

    private val _thinkingEnabled = MutableStateFlow(LocalModelFiles.isThinkingEnabled(appContext))
    val thinkingEnabled: StateFlow<Boolean> = _thinkingEnabled.asStateFlow()

    private val _uiState = MutableStateFlow(
        UiState(supportsThinking = _thinkingEnabled.value)
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _textInputEnabled = MutableStateFlow(true)
    val isTextInputEnabled: StateFlow<Boolean> = _textInputEnabled.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private var currentJob: kotlinx.coroutines.Job? = null
    private var currentFuture: java.util.concurrent.Future<*>? = null

    // ── Patient interaction mode ──
    private val _patientContext = MutableStateFlow<PatientChatContext?>(null)
    val patientContext: StateFlow<PatientChatContext?> = _patientContext.asStateFlow()

    /** Toggle thinking mode. Saves to prefs and updates native layer (preserves chat history). */
    fun setThinkingEnabled(enabled: Boolean) {
        LocalModelFiles.setThinkingEnabled(appContext, enabled)
        _thinkingEnabled.value = enabled
        inferenceModel.updateThinkingMode(enabled)
        // Update flag on the existing UiState without clearing messages
        _uiState.value.supportsThinking = enabled
    }

    /** Load patient context for interaction mode. */
    fun loadPatientContext(patientId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = com.google.mediapipe.examples.llminference.data.MedicalDatabase.getDatabase(appContext)
                val patient = db.patientDao().getPatientSync(patientId) ?: return@launch
                val entries = db.medicalEntryDao().getEntriesForPatientSync(patientId)
                val diagnoses = db.diagnosisDao().getLatestDiagnoses(patientId, 3)

                val ctx = PatientChatContext(
                    patientId = patientId,
                    patientName = patient.name,
                    entryCount = entries.size,
                    systemPrompt = buildPatientSystemPrompt(patient, entries, diagnoses)
                )
                _patientContext.value = ctx

                // Inject patient context as the first "system" message in the chat
                _uiState.value.addMessage(
                    "📋 Patient loaded: **${patient.name}** (${entries.size} entries). Ask me anything about this patient.",
                    MODEL_PREFIX
                )
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to load patient context", e)
            }
        }
    }

    private fun buildPatientSystemPrompt(
        patient: com.google.mediapipe.examples.llminference.data.PatientEntity,
        entries: List<com.google.mediapipe.examples.llminference.data.MedicalEntryEntity>,
        diagnoses: List<com.google.mediapipe.examples.llminference.data.DiagnosisEntity>
    ): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val patientInfo = buildString {
            appendLine("Patient: ${patient.name}")
            if (patient.dateOfBirth.isNotBlank()) appendLine("Date of Birth: ${patient.dateOfBirth}")
            if (patient.gender.isNotBlank()) appendLine("Gender: ${patient.gender}")
            if (patient.bloodGroup.isNotBlank()) appendLine("Blood Group: ${patient.bloodGroup}")
            if (patient.allergies.isNotBlank()) appendLine("Allergies: ${patient.allergies}")
            if (patient.notes.isNotBlank()) appendLine("Notes: ${patient.notes}")
            if (patient.address.isNotBlank()) appendLine("Address: ${patient.address}")
        }

        val entrySummary = if (entries.isNotEmpty()) {
            entries.sortedBy { it.createdAt }.joinToString("\n") { e ->
                val ai = if (e.analysisResult.isNotBlank()) " | AI: ${e.analysisResult.take(200)}" else ""
                "[${fmt.format(java.util.Date(e.createdAt))}][${e.entryType}] ${e.title}: ${e.content.take(200)}$ai"
            }
        } else "No medical entries."

        val diagSummary = if (diagnoses.isNotEmpty()) {
            diagnoses.joinToString("\n\n") { d ->
                val cleanDiag = d.diagnosis
                    .replace(Regex("<unused94>thought>[\\s\\S]*?<unused95>"), "")
                    .replace("<unused94>", "").replace("<unused95>", "")
                    .replace("thought>", "").trim()
                "[${fmt.format(java.util.Date(d.generatedAt))}][${d.scope}] ${cleanDiag.take(500)}"
            }
        } else "No prior diagnoses."

        return """You are a specialist AI medical assistant. A clinician is consulting you about the following patient. Answer their questions using the patient's medical records.

PATIENT INFORMATION:
$patientInfo

MEDICAL ENTRIES (${entries.size} entries, oldest→newest):
$entrySummary

RECENT DIAGNOSES:
$diagSummary

Instructions:
- Answer questions specifically about this patient using their records
- Cite specific entries/dates when referencing data
- If asked about something not in the records, say so
- Be clinically precise and concise
- Format responses in Markdown. Do not wrap your response in a code block."""
    }

    fun sendMessage(userMessage: String, userImages: List<Bitmap>) {
        currentJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.value.addMessage(userMessage, USER_PREFIX, userImages)
            _uiState.value.createLoadingMessage()
            setInputEnabled(false)
            _isGenerating.value = true
            try {
                // Prepend patient context to the prompt if available
                val contextualPrompt = _patientContext.value?.let { ctx ->
                    "${ctx.systemPrompt}\n\nDoctor's question: $userMessage"
                } ?: userMessage

                val future = inferenceModel.generateResponseAsync(contextualPrompt, userImages) { partialResult, isDone ->
                    if (!isDone && partialResult.isNotEmpty()) {
                        _uiState.value.appendMessage(partialResult)
                    }
                }
                currentFuture = future
                future.get()
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException || e is kotlinx.coroutines.CancellationException) {
                    // User stopped generation
                } else {
                    _uiState.value.addMessage(e.localizedMessage ?: "Unknown Error", MODEL_PREFIX)
                }
            } finally {
                _isGenerating.value = false
                setInputEnabled(true)
                currentJob = null
                currentFuture = null
            }
        }
    }

    /** Stop the current generation. */
    fun stopGeneration() {
        currentFuture?.cancel(true)
        currentJob?.cancel()
        currentFuture = null
        currentJob = null
        _isGenerating.value = false
        setInputEnabled(true)
    }

    private fun setInputEnabled(isEnabled: Boolean) {
        _textInputEnabled.value = isEnabled
    }

    companion object {
        fun getFactory(patientId: Long? = null) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                val ctx = application.applicationContext
                val inferenceModel = InferenceModel.getInstance(ctx)
                val vm = ChatViewModel(inferenceModel, ctx)
                if (patientId != null && patientId > 0) {
                    vm.loadPatientContext(patientId)
                }
                return vm as T
            }
        }
    }
}

/** Context for patient-specific chat interaction */
data class PatientChatContext(
    val patientId: Long,
    val patientName: String,
    val entryCount: Int,
    val systemPrompt: String
)
