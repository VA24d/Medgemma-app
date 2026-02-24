package com.google.mediapipe.examples.llminference.ui.screens

import android.Manifest
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.mediapipe.examples.llminference.data.MedicalDatabase
import com.google.mediapipe.examples.llminference.data.MedicalEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var isSaving by remember { mutableStateOf(false) }

    // Date picker state
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    // Background observation extraction state
    var autoDescText by remember { mutableStateOf("") }
    var isAutoDescribing by remember { mutableStateOf(false) }
    var autoDescJob by remember { mutableStateOf<Job?>(null) }

    // Whenever a new image is selected, kick off a background description.
    LaunchedEffect(selectedImageUri) {
        val uri = selectedImageUri ?: return@LaunchedEffect
        autoDescJob?.cancel()
        autoDescText = ""
        isAutoDescribing = true
        autoDescJob = scope.launch {
            try {
                val inferenceModel = withContext(Dispatchers.IO) {
                    com.google.mediapipe.examples.llminference.InferenceModel.getInstance(context)
                }
                val bitmap: Bitmap? = withContext(Dispatchers.IO) {
                    try {
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
                        Log.w("XrayAutoDesc", "Could not decode image: ${e.message}")
                        null
                    }
                }
                val descPrompt = when (analysisType) {
                    "HISTOPATHOLOGY" -> """You are a pathology AI. Systematically describe this histopathology slide for clinical documentation.
Report: 1) Stain type (H&E / IHC / other), 2) Tissue type and architecture, 3) Cellular morphology — size, shape, nuclear:cytoplasmic ratio, 4) Mitotic figures per HPF, 5) Inflammatory infiltrate, 6) Vascular and stromal changes, 7) Any dysplasia, atypia, or malignant features with location.
Be precise, structured, and clinically useful. Do not wrap in a code block."""
                    "MRI" -> """You are a radiologist AI specialising in MRI. Systematically describe this MRI image for clinical documentation.
Report: 1) Likely MRI sequence (T1/T2/FLAIR/DWI/GRE/other) and imaging plane, 2) Body region and laterality, 3) Signal characteristics of major structures, 4) Any focal lesions — location, size, signal intensity, margins, surrounding oedema, 5) Mass effect or midline shift, 6) Enhancement patterns if contrast visible, 7) Incidental findings.
Be precise, structured, and clinically useful. Do not wrap in a code block."""
                    else -> """You are a radiologist AI. Systematically describe this X-ray for clinical documentation.
Report: 1) Image orientation and quality, 2) Bony structures — cortex, density, trabeculation, 3) Soft tissue shadows, 4) Lung fields — opacities, consolidations, hyperinflation, vascularity, 5) Cardiac silhouette size and borders, 6) Mediastinum width, 7) Pleural spaces, 8) Any abnormal findings with precise location and character.
Be precise, structured, and clinically useful. Do not wrap in a code block."""
                }
                val imgList = if (bitmap != null) listOf(bitmap) else emptyList()
                var desc = ""
                val future = inferenceModel.generateResponseAsync(descPrompt, imgList) { token, _ ->
                    if (token.isNotEmpty()) desc += token
                }
                withContext(Dispatchers.IO) { future.get() }
                autoDescText = desc.trim()
            } catch (e: Exception) {
                Log.w("XrayAutoDesc", "Auto-description failed: ${e.message}")
                autoDescText = ""
            } finally {
                isAutoDescribing = false
            }
        }
    }

    // Camera permission state
    var pendingCameraLaunch by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    val typeName = when (analysisType) {
        "HISTOPATHOLOGY" -> "Histopathology"
        "MRI" -> "MRI Scan"
        else -> "X-ray"
    }

    // Camera logic
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            selectedImageUri = tempCameraUri
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingCameraLaunch) {
            pendingCameraLaunch = false
            val uri = run {
                val storageDir = java.io.File(context.filesDir, "medical_images").apply { mkdirs() }
                val file = java.io.File(storageDir, "camera_capture_${System.currentTimeMillis()}.jpg")
                androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            }
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            pendingCameraLaunch = false
        }
    }

    fun launchCamera() {
        val storageDir = java.io.File(context.filesDir, "medical_images").apply { mkdirs() }
        val file = java.io.File(storageDir, "camera_capture_${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val permission = Manifest.permission.CAMERA
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            pendingCameraLaunch = true
            cameraPermissionLauncher.launch(permission)
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
                                    // Save immediately — background observation will be ready in longitudinal view
                                    val finalAnalysis = autoDescText.ifBlank { "" }
                                    db.medicalEntryDao().insertEntry(
                                        MedicalEntryEntity(
                                            patientId = patientId,
                                            entryType = analysisType,
                                            title = title.ifBlank { "$typeName Analysis" },
                                            content = content,
                                            imagePaths = selectedImageUri?.toString() ?: "",
                                            analysisResult = finalAnalysis,
                                            createdAt = selectedDateMillis,
                                            updatedAt = selectedDateMillis
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
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "Selected image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Overlay controls
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Gallery")
                                }
                                FilledTonalButton(
                                    onClick = { launchCamera() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Camera")
                                }
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                when (analysisType) {
                                    "HISTOPATHOLOGY" -> Icons.Default.Biotech
                                    "MRI" -> Icons.Default.BlurOn
                                    else -> Icons.Default.Image
                                },
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
                                OutlinedButton(onClick = { launchCamera() }) {
                                    Icon(Icons.Default.PhotoCamera, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Camera")
                                }
                            }
                        }
                    }
                }
            }

            // Auto-description status chip
            if (selectedImageUri != null) {
                if (isAutoDescribing) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Generating image description…", style = MaterialTheme.typography.labelSmall) },
                        icon = {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                } else if (autoDescText.isNotBlank()) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Image observations extracted — will be used in Diagnosis", style = MaterialTheme.typography.labelSmall) },
                        icon = { Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp)) }
                    )
                }
            }

            // Date picker field
            OutlinedTextField(
                value = dateFormatter.format(Date(selectedDateMillis)),
                onValueChange = {},
                label = { Text("Date") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date")
                    }
                }
            )

            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { selectedDateMillis = it }
                            showDatePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                    }
                ) {
                    DatePicker(state = datePickerState)
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
                label = { Text(when (analysisType) {
                    "HISTOPATHOLOGY" -> "Tissue Type"
                    "MRI" -> "Region / Sequence"
                    else -> "Body Part"
                }) },
                placeholder = { Text(when (analysisType) {
                    "HISTOPATHOLOGY" -> "e.g., Liver biopsy"
                    "MRI" -> "e.g., Brain T2, Lumbar spine"
                    else -> "e.g., Chest, Left hand"
                }) },
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

            // Observation extraction status
            if (selectedImageUri != null && isAutoDescribing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Extracting image observations in background…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }

        }
    }
}
