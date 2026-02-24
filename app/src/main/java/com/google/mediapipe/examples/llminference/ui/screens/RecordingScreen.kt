package com.google.mediapipe.examples.llminference.ui.screens

import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.llminference.InferenceModel
import com.google.mediapipe.examples.llminference.data.MedicalDatabase
import com.google.mediapipe.examples.llminference.data.MedicalEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    patientId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val db = remember { MedicalDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var fullText by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var isExtracting by remember { mutableStateOf(false) }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val results = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0)
            if (!spokenText.isNullOrBlank()) {
                fullText = if (fullText.isBlank()) spokenText else "$fullText $spokenText"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Note") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            isSaving = true
                            scope.launch {
                                val entryId = withContext(Dispatchers.IO) {
                                    db.medicalEntryDao().insertEntry(
                                        MedicalEntryEntity(
                                            patientId = patientId,
                                            entryType = "RECORDING",
                                            title = title.ifBlank { "Voice Note" },
                                            content = fullText,
                                            imagePaths = "",
                                            analysisResult = ""
                                        )
                                    )
                                }
                                isSaving = false
                                onSaved()
                                // Background clinical summary
                                isExtracting = true
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val inferenceModel = InferenceModel.getInstance(context)
                                        val prompt = """You are a specialist AI medical assistant.
Analyse this voice recording transcription and provide a concise clinical summary.
Title: ${title.ifBlank { "Voice Note" }}
Transcription: $fullText
Provide: 1) Key clinical findings, 2) Significance, 3) Recommended follow-up.
Be concise. Do not wrap in a code block."""
                                        var result = ""
                                        val future = inferenceModel.generateResponseAsync(prompt, emptyList()) { token, _ ->
                                            if (token.isNotEmpty()) result += token
                                        }
                                        future.get()
                                        val entry = db.medicalEntryDao().getEntry(entryId)
                                        if (entry != null) {
                                            db.medicalEntryDao().updateEntry(entry.copy(analysisResult = result.trim()))
                                        }
                                    } catch (_: Exception) {
                                    } finally {
                                        withContext(Dispatchers.Main) { isExtracting = false }
                                    }
                                }
                            }
                        },
                        enabled = !isSaving && fullText.isNotBlank()
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Recording Controls (System Intent)
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak now...")
                        }
                        try {
                            speechRecognizerLauncher.launch(intent)
                        } catch (e: Exception) {
                            // Handle case where no voice recognizer is present
                            // Toast.makeText(context, "No speech recognizer found", Toast.LENGTH_SHORT).show()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Record",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                "Tap to Dictate",
                style = MaterialTheme.typography.labelLarge
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = fullText,
                onValueChange = { fullText = it },
                label = { Text("Transcribed Text") },
                placeholder = { Text("Dictation will appear here...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                minLines = 10
            )
        }
    }
}
