package com.google.mediapipe.examples.llminference

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mediapipe.examples.llminference.demo.DemoXraySummaries
import com.google.mediapipe.examples.llminference.demo.displayNameForImageUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.mediapipe.examples.llminference.data.PatientChartPrompt
import com.google.mediapipe.examples.llminference.settings.LocalModelFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val DEMO_CXR_LOG = "MedgemmaDemoCXR"

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

                val ctx = PatientChatContext(
                    patientId = patientId,
                    patientName = patient.name,
                    entryCount = entries.size
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
        diagnoses: List<com.google.mediapipe.examples.llminference.data.DiagnosisEntity>,
    ): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val patientInfo = buildString {
            appendLine("${patient.name}")
            if (patient.dateOfBirth.isNotBlank()) appendLine("DOB ${patient.dateOfBirth}")
            if (patient.gender.isNotBlank()) appendLine("Gender ${patient.gender}")
            if (patient.bloodGroup.isNotBlank()) appendLine("Blood ${patient.bloodGroup}")
            if (patient.allergies.isNotBlank()) appendLine("Allergies ${patient.allergies}")
            if (patient.notes.isNotBlank()) {
                val n = patient.notes
                val cap = PATIENT_NOTES_IN_PROMPT_MAX
                appendLine("Notes ${n.take(cap)}${if (n.length > cap) "…" else ""}")
            }
        }

        val diagSummary = if (diagnoses.isNotEmpty()) {
            diagnoses.take(2).joinToString("\n") { d ->
                val cleanDiag = d.diagnosis
                    .replace(Regex("<unused94>thought>[\\s\\S]*?<unused95>"), "")
                    .replace("<unused94>", "").replace("<unused95>", "")
                    .replace("thought>", "").trim()
                "${fmt.format(java.util.Date(d.generatedAt))} ${d.scope}: ${cleanDiag.take(DIAG_IN_PROMPT_MAX)}${if (cleanDiag.length > DIAG_IN_PROMPT_MAX) "…" else ""}"
            }
        } else "None."

        val fullEntryBlock = if (entries.isNotEmpty()) {
            entries.sortedBy { it.createdAt }.joinToString("\n\n---\n\n") { e ->
                val ai = if (e.analysisResult.isNotBlank()) {
                    "\nImaging/AI: ${e.analysisResult.take(ENTRY_FIELD_MAX)}"
                } else ""
                val headline = if (e.visitSummary.isNotBlank()) "\nHeadline: ${e.visitSummary}\n" else ""
                "${fmt.format(java.util.Date(e.createdAt))} [${e.entryType}] ${e.title}$headline${e.content.take(ENTRY_FIELD_MAX)}$ai"
            }
        } else "(No entries.)"

        return """Clinical assistant. Chart below is the only source; no filler intros. Markdown OK.

For progress/course/timeline: write a thorough narrative (multiple paragraphs if needed), cite dates and entry types in prose—avoid a separate boilerplate section titled "Each visit" unless the user explicitly asks for visit-by-visit bullets.

PATIENT
$patientInfo

ENTRIES (${entries.size}, oldest→newest)
$fullEntryBlock

SAVED IMPRESSIONS (may be incomplete vs entries)
$diagSummary"""
    }

    /**
     * Tiny questions answered from Room only. Progress/course always goes through the model with full chart.
     */
    private suspend fun maybeInstantPatientReply(
        patientId: Long,
        cachedEntryCount: Int,
        userMessage: String,
    ): String? {
        val n = userMessage.lowercase().trim()
        if (n.length > 160) return null

        val needsReasoning = listOf(
            "progress", "summary", "diagnosis", "diagnose", "analyze", "analysis", "compare", "trend",
            "prognosis", "recommend", "should ", "x-ray", "xray", "image", "finding", "interpret",
            "worse", "better", "improving", "treatment plan", "what happened", "timeline", "course",
            "condition", "symptom", "why ", "how did", "explain"
        )
        if (needsReasoning.any { n.contains(it) }) return null

        if (Regex("""\b(how many entries|how many notes|number of entries|entry count|how many visits)\b""").containsMatchIn(n)) {
            return "### Chart snapshot\n- **Medical entries on file:** $cachedEntryCount"
        }

        val identityCue = Regex(
            """\b(who is|who's|what patient|which patient|patient name|identify the patient|patient id|patient details?|demographics|tell me about (this )?patient|describe (this )?patient|who am i looking at|what patient is this)\b"""
        )
        if (!identityCue.containsMatchIn(n)) return null

        val db = com.google.mediapipe.examples.llminference.data.MedicalDatabase.getDatabase(appContext)
        val p = db.patientDao().getPatientSync(patientId) ?: return null
        return formatPatientSnapshotMarkdown(p)
    }

    private fun formatPatientSnapshotMarkdown(
        p: com.google.mediapipe.examples.llminference.data.PatientEntity
    ): String {
        return buildString {
            appendLine("### Patient profile *(from records)*")
            appendLine("- **Name:** ${p.name}")
            if (p.medicalRecordNumber.isNotBlank()) appendLine("- **MRN:** ${p.medicalRecordNumber}")
            if (p.dateOfBirth.isNotBlank()) appendLine("- **DOB:** ${p.dateOfBirth}")
            if (p.gender.isNotBlank()) appendLine("- **Gender:** ${p.gender}")
            if (p.bloodGroup.isNotBlank()) appendLine("- **Blood group:** ${p.bloodGroup}")
            if (p.phoneNumber.isNotBlank()) appendLine("- **Phone:** ${p.phoneNumber}")
            if (p.email.isNotBlank()) appendLine("- **Email:** ${p.email}")
            if (p.address.isNotBlank()) appendLine("- **Address:** ${p.address}")
            if (p.allergies.isNotBlank()) appendLine("- **Allergies:** ${p.allergies}")
            if (p.notes.isNotBlank()) appendLine("- **Notes:** ${p.notes}")
            appendLine()
            appendLine("*For imaging detail or non-English replies, use Language Extension or ask a focused question — on-device AI may take longer.*")
        }
    }

    /** Strong output-language hint read fresh each send (sidebar preference). */
    private fun buildReplyLanguageInstruction(context: Context): String {
        return when (LocalModelFiles.normalizedLanguageExtensionKey(context)) {
            "telugu" -> """

OUTPUT LANGUAGE (mandatory): Write the entire reply in Telugu script. Use Telugu for all explanations, headings, lists, and summaries. Do not write narrative sentences in English. International drug names or standard abbreviations (e.g. mg, BP) may stay in Latin script when conventional.
"""
            "hindi" -> """

OUTPUT LANGUAGE (mandatory): Write the entire reply in Hindi using Devanagari script for all narrative, headings, and bullets. Do not write explanations in English. Drug names may remain in Latin when standard.
"""
            else -> ""
        }
    }

    fun sendMessage(userMessage: String, userImages: List<Bitmap>, imageUriHints: List<Uri> = emptyList()) {
        currentJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.value.addMessage(userMessage, USER_PREFIX, userImages)
            _uiState.value.createLoadingMessage()
            setInputEnabled(false)
            _isGenerating.value = true
            try {
                val ctx = _patientContext.value
                val thinkingOn = _thinkingEnabled.value
                val firstUri = imageUriHints.firstOrNull()
                val imageDisplayName = firstUri
                    ?.let { displayNameForImageUri(appContext, it) }
                    .orEmpty()
                val firstBmp = userImages.firstOrNull()
                val demoSummary = if (firstBmp != null) {
                    DemoXraySummaries.summaryForKnownDemoFilename(imageDisplayName)
                        ?: DemoXraySummaries.summaryForKnownDemoBitmapSize(firstBmp.width, firstBmp.height)
                } else {
                    null
                }
                val demoBlock = if (demoSummary != null) {
                    DemoXraySummaries.demoRadiologyPrefix(demoSummary)
                } else {
                    ""
                }
                val inferenceImages = if (demoSummary != null) emptyList() else userImages
                android.util.Log.i(
                    DEMO_CXR_LOG,
                    "uris=${imageUriHints.size} uri=$firstUri resolvedName='$imageDisplayName' " +
                        "bitmap=${firstBmp?.let { "${it.width}x${it.height}" } ?: "-"} " +
                        "demoFastPath=${demoSummary != null} inferImages=${inferenceImages.size}",
                )
                val skipThinkForThisSend = !thinkingOn || demoSummary != null
                if (ctx != null && userImages.isEmpty()) {
                    val db = com.google.mediapipe.examples.llminference.data.MedicalDatabase.getDatabase(appContext)
                    val entries =
                        db.medicalEntryDao().getEntriesForPatientSync(ctx.patientId)
                    val instant = maybeInstantPatientReply(
                        ctx.patientId,
                        ctx.entryCount,
                        userMessage
                    )
                    if (instant != null) {
                        android.util.Log.i("ChatViewModel", "Instant DB reply (no LLM), chars=${instant.length}")
                        _uiState.value.appendMessage(instant)
                        return@launch
                    }

                    val patient = db.patientDao().getPatientSync(ctx.patientId)
                    if (patient != null) {
                        val langBlock = buildReplyLanguageInstruction(appContext)
                        val diagnoses = db.diagnosisDao().getLatestDiagnoses(ctx.patientId, 3)
                        val longitudinal =
                            PatientChartPrompt.wantsLongitudinalQuestion(userMessage)
                        val body = buildPatientSystemPrompt(patient, entries, diagnoses)
                        val contextualPrompt = "$body$langBlock\n\nCurrent request: $userMessage"
                        val maxOut =
                            if (longitudinal) PATIENT_LONG_FORM_MAX_TOKENS else PATIENT_TEXT_MAX_TOKENS
                        val future = inferenceModel.generateResponseAsync(
                            contextualPrompt,
                            userImages,
                            maxPredictTokens = maxOut,
                            forceSkipThinkingForRequest = !longitudinal,
                        ) { partialResult, isDone ->
                            if (!isDone && partialResult.isNotEmpty()) {
                                _uiState.value.appendMessage(partialResult)
                            }
                        }
                        currentFuture = future
                        future.get()
                        return@launch
                    }
                }

                val langBlock = buildReplyLanguageInstruction(appContext)
                val contextualPrompt =
                    if (ctx != null && userImages.isNotEmpty()) {
                        val db = com.google.mediapipe.examples.llminference.data.MedicalDatabase.getDatabase(appContext)
                        val patient = db.patientDao().getPatientSync(ctx.patientId)
                        val entries = db.medicalEntryDao().getEntriesForPatientSync(ctx.patientId)
                        val diagnoses = db.diagnosisDao().getLatestDiagnoses(ctx.patientId, 3)
                        if (patient != null) {
                            val body = buildPatientSystemPrompt(patient, entries, diagnoses)
                            "$body$langBlock\n\n${demoBlock}Current request: $userMessage"
                        } else {
                            buildString {
                                if (langBlock.isNotBlank()) append(langBlock).append("\n\n")
                                if (!thinkingOn) append(GENERAL_DIRECT_ONLY_PREFIX)
                                append(demoBlock)
                                append(userMessage)
                            }.toString()
                        }
                    } else {
                        buildString {
                            if (langBlock.isNotBlank()) append(langBlock).append("\n\n")
                            if (ctx == null && !thinkingOn) append(GENERAL_DIRECT_ONLY_PREFIX)
                            append(demoBlock)
                            append(userMessage)
                        }.toString()
                    }

                val patientTextOnly = ctx != null && userImages.isEmpty()
                val longitudinal =
                    patientTextOnly && PatientChartPrompt.wantsLongitudinalQuestion(userMessage)
                val maxOut = when {
                    longitudinal -> PATIENT_LONG_FORM_MAX_TOKENS
                    patientTextOnly -> PATIENT_TEXT_MAX_TOKENS
                    else -> DEFAULT_CHAT_MAX_TOKENS
                }

                val future = inferenceModel.generateResponseAsync(
                    contextualPrompt,
                    inferenceImages,
                    maxPredictTokens = maxOut,
                    forceSkipThinkingForRequest = skipThinkForThisSend,
                ) { partialResult, isDone ->
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
        /** Enough text per entry field for CXR narratives. */
        private const val ENTRY_FIELD_MAX = 3500

        /** Cap patient problem list in system prompt to save context for entries + answer. */
        private const val PATIENT_NOTES_IN_PROMPT_MAX = 900

        /** Cap each saved diagnosis line in the prompt. */
        private const val DIAG_IN_PROMPT_MAX = 650

        /** Short answers (quick facts). */
        private const val PATIENT_TEXT_MAX_TOKENS = 512

        /** Progress / timeline / summary questions — room for a full narrative. */
        private const val PATIENT_LONG_FORM_MAX_TOKENS = 896

        /**
         * Applied on every general-chat turn when thinking is off (including follow-ups like "explain more").
         */
        private const val GENERAL_DIRECT_ONLY_PREFIX =
            "Instructions: Write only the user-facing answer. Do not start with \"Okay,\", \"I need to\", \"I should cover\", \"Let me\", planning outlines, or numbered meta steps before the content—even on follow-up questions. Begin with the explanation itself (paragraphs or bullets).\n\n"

        /** Default max output tokens when user attaches images or general chat. */
        private const val DEFAULT_CHAT_MAX_TOKENS = 1024

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
)
