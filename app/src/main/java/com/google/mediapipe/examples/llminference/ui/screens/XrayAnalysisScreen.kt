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
    var analysisResult by remember { mutableStateOf<String?>(null) }
    var streamingAnalysis by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var generationJob by remember { mutableStateOf<Job?>(null) }
    var generationFuture by remember { mutableStateOf<java.util.concurrent.Future<*>?>(null) }

    // Date picker state
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    // Sub-agent state
    var autoDescText by remember { mutableStateOf("") }   // Stage 1 — Observation
    var isAutoDescribing by remember { mutableStateOf(false) }
    var autoDescJob by remember { mutableStateOf<Job?>(null) }
    var stage1Expanded by remember { mutableStateOf(false) }
    var stage2Text by remember { mutableStateOf("") }      // Stage 2 — Interpretation
    var isRunningStage2 by remember { mutableStateOf(false) }
    var stage2StreamingText by remember { mutableStateOf("") }

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
                                    // Wait for background auto-description if still running
                                    autoDescJob?.join()
                                    val finalAnalysis = stage2Text.ifBlank { null }
                                        ?: analysisResult
                                        ?: autoDescText.ifBlank { null }
                                        ?: ""
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
                } else if (autoDescText.isNotBlank() && analysisResult == null) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Image description ready — will be used in Diagnosis", style = MaterialTheme.typography.labelSmall) },
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

            // ─── Sub-agent pipeline ────────────────────────────────────────────
            if (selectedImageUri != null) {

                // Stage 1 card — Observation Agent (auto-desc)
                if (isAutoDescribing || autoDescText.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isAutoDescribing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Search, null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Stage 1 — Observation Agent",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                if (autoDescText.isNotBlank()) {
                                    TextButton(
                                        onClick = { stage1Expanded = !stage1Expanded },
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            if (stage1Expanded) "Collapse" else "Expand",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                            if (isAutoDescribing) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Extracting structured observations from image…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            } else if (autoDescText.isNotBlank()) {
                                if (stage1Expanded) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        autoDescText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        autoDescText.take(120) + if (autoDescText.length > 120) "…" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Analysis action buttons
                val canRunSubAgent = autoDescText.isNotBlank() && !isAutoDescribing && !isRunningStage2 && stage2Text.isBlank()
                val canRunQuick = !isAnalyzing && !isRunningStage2 && analysisResult == null && stage2Text.isBlank()

                if (canRunSubAgent || canRunQuick) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Sub-agent Analysis button
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                isRunningStage2 = true
                                stage2StreamingText = ""
                                scope.launch {
                                    try {
                                        val inferenceModel = com.google.mediapipe.examples.llminference.InferenceModel.getInstance(context)
                                        // Stage 2: clinical interpretation — text only, uses Stage 1 output
                                        val stage2Prompt = when (analysisType) {
                                            "HISTOPATHOLOGY" -> """You are a senior pathologist AI. A junior agent produced these histopathological observations:

$autoDescText

Clinical context — Title: $title. Tissue: $bodyPart. Context: $clinicalContext.

Provide a clinical interpretation:
1. **Pathological Diagnosis** (primary + differentials ranked by likelihood)
2. **WHO Classification / Grade** (if applicable)
3. **Tumour/Lesion Characteristics** (size estimate, margins, invasion, necrosis)
4. **IHC Markers** recommended for confirmation
5. **Staging Implications**
6. **Clinical Significance & Urgency**

Format in Markdown. Do not wrap in a code block."""
                                            "MRI" -> """You are a consultant neuroradiologist / MSK radiologist AI. A junior agent produced these MRI observations:

$autoDescText

Clinical context — Title: $title. Region/Sequence: $bodyPart. Context: $clinicalContext.

Provide a clinical interpretation:
1. **Primary Impression** (most likely diagnosis)
2. **Differential Diagnoses** (ranked, with reasoning)
3. **Structured Scoring** (BIRADS / PIRADS / ACR / ASPECTS as appropriate)
4. **Key Abnormalities** (with anatomical significance)
5. **Recommended Follow-up** (additional sequences, contrast, biopsy, MDT)
6. **Urgency Level** (routine / urgent / emergency)

Format in Markdown. Do not wrap in a code block."""
                                            else -> """You are a consultant radiologist AI. A junior agent produced these radiographic observations:

$autoDescText

Clinical context — Title: $title. Body part: $bodyPart. Context: $clinicalContext.

Provide a clinical interpretation:
1. **Primary Radiological Diagnosis**
2. **Differential Diagnoses** (ranked, with confidence levels)
3. **Severity / Extent Assessment**
4. **Recommended Additional Views or Imaging**
5. **Clinical Correlation Recommendations**
6. **Urgency Level** (routine / urgent / emergency)

Format in Markdown. Do not wrap in a code block."""
                                        }
                                        val future = inferenceModel.generateResponseAsync(stage2Prompt, emptyList()) { token, _ ->
                                            if (token.isNotEmpty()) stage2StreamingText += token
                                        }
                                        withContext(Dispatchers.IO) { future.get() }
                                        stage2Text = stage2StreamingText.trim()
                                    } catch (e: Exception) {
                                        stage2Text = "Error in Stage 2: ${e.message}"
                                    } finally {
                                        isRunningStage2 = false
                                    }
                                }
                            },
                            enabled = canRunSubAgent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AccountTree, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sub-agent", style = MaterialTheme.typography.labelMedium)
                        }

                        // Quick Analysis button
                        OutlinedButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                isAnalyzing = true
                                streamingAnalysis = ""
                                scope.launch {
                                    try {
                                        val inferenceModel = com.google.mediapipe.examples.llminference.InferenceModel.getInstance(context)
                                        val prompt = when (analysisType) {
                                            "HISTOPATHOLOGY" -> "You are a specialist pathology AI. Analyse this histopathology image.\nTissue: $bodyPart. Clinical context: $clinicalContext.\nProvide: 1) Key histopathological findings, 2) Diagnosis / differentials, 3) Grade and staging implications, 4) Recommended IHC markers."
                                            "MRI" -> "You are a specialist radiologist AI. Analyse this MRI scan.\nRegion/Sequence: $bodyPart. Clinical context: $clinicalContext.\nProvide: 1) Key MRI findings, 2) Primary impression and differentials, 3) Relevant scoring (BIRADS/PIRADS if applicable), 4) Recommended next steps."
                                            else -> "You are a specialist radiologist AI. Analyse this X-ray.\nBody part: $bodyPart. Clinical context: $clinicalContext.\nProvide: 1) Key radiographic findings, 2) Diagnosis / differentials, 3) Severity, 4) Recommended next steps."
                                        }
                                        val bitmap: Bitmap? = withContext(Dispatchers.IO) {
                                            try {
                                                val uri = selectedImageUri!!
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
                                            } catch (e: Exception) { null }
                                        }
                                        val images = if (bitmap != null) listOf(bitmap) else emptyList()
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
                            enabled = canRunQuick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Quick", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Stage 2 — streaming
                if (isRunningStage2 && stage2StreamingText.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Stage 2 — Clinical Interpretation…", style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stage2StreamingText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Stage 2 — final result
                if (stage2Text.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AccountTree, null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Stage 2 — Clinical Interpretation",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stage2Text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                // Quick analysis streaming
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

                // Quick analysis result
                if (analysisResult != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
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
                                    "Quick Analysis",
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
}
