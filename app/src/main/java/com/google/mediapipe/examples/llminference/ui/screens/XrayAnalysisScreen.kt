package com.google.mediapipe.examples.llminference.ui.screens

import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.llminference.data.MedicalDatabase
import com.google.mediapipe.examples.llminference.data.MedicalEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XrayAnalysisScreen(
    patientId: Long,
    analysisType: String = "XRAY", // XRAY or HISTOPATHOLOGY
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val db = remember { MedicalDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var title by remember { mutableStateOf("") }
    var bodyPart by remember { mutableStateOf("") }
    var clinicalContext by remember { mutableStateOf("") }
    var analysisResult by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    val typeName = if (analysisType == "HISTOPATHOLOGY") "Histopathology" else "X-ray / MRI"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$typeName Analysis") },
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
                                withContext(Dispatchers.IO) {
                                    val content = buildString {
                                        if (bodyPart.isNotBlank()) appendLine("Body Part: $bodyPart")
                                        if (clinicalContext.isNotBlank()) appendLine("Context: $clinicalContext")
                                    }
                                    db.medicalEntryDao().insertEntry(
                                        MedicalEntryEntity(
                                            patientId = patientId,
                                            entryType = analysisType,
                                            title = title.ifBlank { "$typeName Analysis" },
                                            content = content,
                                            imagePaths = selectedImageUri?.toString() ?: "",
                                            analysisResult = analysisResult ?: ""
                                        )
                                    )
                                }
                                isSaving = false
                                onSaved()
                            }
                        },
                        enabled = !isSaving && selectedImageUri != null
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Image upload area
            Card(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedImageUri != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Image selected", style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                                Text("Change image")
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                if (analysisType == "HISTOPATHOLOGY") Icons.Default.Biotech
                                else Icons.Default.Image,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Tap to upload $typeName image",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = bodyPart,
                onValueChange = { bodyPart = it },
                label = { Text(if (analysisType == "HISTOPATHOLOGY") "Tissue Type" else "Body Part") },
                placeholder = { Text(if (analysisType == "HISTOPATHOLOGY") "e.g., Liver biopsy" else "e.g., Chest, Left hand") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = clinicalContext,
                onValueChange = { clinicalContext = it },
                label = { Text("Clinical Context") },
                placeholder = { Text("Relevant clinical information…") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            // Analyze button
            if (selectedImageUri != null && analysisResult == null) {
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        isAnalyzing = true
                        // TODO: Integrate with MedGemma inference
                        analysisResult = "AI analysis will be generated when MedGemma model is loaded. " +
                                "Upload saved for future analysis."
                        isAnalyzing = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isAnalyzing
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyzing…")
                    } else {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyze with MedGemma")
                    }
                }
            }

            // Analysis result
            if (analysisResult != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome, null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "AI Analysis",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            analysisResult!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}
