package com.google.mediapipe.examples.llminference.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
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
import kotlinx.coroutines.Job
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
    var streamingAnalysis by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var generationJob by remember { mutableStateOf<Job?>(null) }
    var generationFuture by remember { mutableStateOf<java.util.concurrent.Future<*>?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    val typeName = if (analysisType == "HISTOPATHOLOGY") "Histopathology" else "X-ray / MRI"

    // Camera logic
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            selectedImageUri = tempCameraUri
        }
    }

    fun createImageUri(): Uri {
        val storageDir = java.io.File(context.filesDir, "medical_images").apply { mkdirs() }
        val file = java.io.File(storageDir, "camera_capture_${System.currentTimeMillis()}.jpg")
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
    }

    fun stopAnalysis() {
        generationJob?.cancel()
        generationFuture?.cancel(true)
        generationJob = null
        generationFuture = null
        if (streamingAnalysis.isNotBlank()) analysisResult = streamingAnalysis
        isAnalyzing = false
    }

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
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                TextButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                                    Text("Gallery")
                                }
                                TextButton(onClick = {
                                    val uri = createImageUri()
                                    tempCameraUri = uri
                                    cameraLauncher.launch(uri)
                                }) {
                                    Text("Camera")
                                }
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
                                "Add $typeName Image",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                                    Icon(Icons.Default.PhotoLibrary, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Gallery")
                                }
                                OutlinedButton(onClick = {
                                    val uri = createImageUri()
                                    tempCameraUri = uri
                                    cameraLauncher.launch(uri)
                                }) {
                                    Icon(Icons.Default.PhotoCamera, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Camera")
                                }
                            }
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
                        streamingAnalysis = ""
                        scope.launch {
                            try {
                                val inferenceModel = com.google.mediapipe.examples.llminference.InferenceModel.getInstance(context)
                                val prompt = "You are a specialist AI medical assistant. Analyse this $typeName image.\nTitle: $title. Body part / tissue: $bodyPart. Clinical context: $clinicalContext.\nProvide: 1) Key findings, 2) Abnormalities if any, 3) Differential diagnoses, 4) Recommended next steps."

                                // Load bitmap for vision analysis
                                val bitmap: Bitmap? = withContext(Dispatchers.IO) {
                                    try {
                                        val uri = selectedImageUri!!
                                        @Suppress("DEPRECATION")
                                        when {
                                            uri.scheme == "file" -> BitmapFactory.decodeFile(uri.path)
                                            uri.scheme == "content" -> {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                                    android.graphics.ImageDecoder.decodeBitmap(
                                                        android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                                                    ) { decoder, _, _ -> decoder.isMutableRequired = true }
                                                } else {
                                                    @Suppress("DEPRECATION")
                                                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                                                }
                                            }
                                            else -> null
                                        }
                                    } catch (e: Exception) {
                                        Log.w("XrayAnalysis", "Could not decode image: ${e.message}")
                                        null
                                    }
                                }

                                val images = if (bitmap != null) {
                                    Log.i("XrayAnalysis", "Vision encoder: passing image to MedGemma (${bitmap.width}x${bitmap.height})")
                                    listOf(bitmap)
                                } else {
                                    Log.w("XrayAnalysis", "Vision encoder: bitmap null, falling back to text-only")
                                    emptyList()
                                }

                                val future = inferenceModel.generateResponseAsync(prompt, images) { token, done ->
                                    if (!done && token.isNotEmpty()) streamingAnalysis += token
                                }
                                withContext(Dispatchers.IO) { future.get() }
                                analysisResult = streamingAnalysis
                            } catch (e: Exception) {
                                analysisResult = "Error generating analysis: ${e.message}"
                            } finally {
                                isAnalyzing = false
                            }
                        }
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

            // Streaming result (while analyzing)
            if (isAnalyzing && streamingAnalysis.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generating…", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(streamingAnalysis, style = MaterialTheme.typography.bodySmall)
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
