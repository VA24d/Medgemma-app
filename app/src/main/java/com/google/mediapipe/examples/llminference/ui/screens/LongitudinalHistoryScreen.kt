package com.google.mediapipe.examples.llminference.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.mediapipe.examples.llminference.InferenceModel
import com.google.mediapipe.examples.llminference.data.MedicalDatabase
import com.google.mediapipe.examples.llminference.data.MedicalEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LongitudinalHistoryScreen(
    patientId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { MedicalDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<MedicalEntryEntity>>(emptyList()) }
    var expandedEntryId by remember { mutableStateOf<Long?>(null) }
    var filterType by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(patientId) {
        // Collect descending from DB, reverse to show oldest→newest in timeline
        db.medicalEntryDao().getEntriesForPatient(patientId).collect { list ->
            entries = list.sortedBy { it.createdAt }
        }
    }

    val filteredEntries = if (filterType == null) entries
    else entries.filter { it.entryType == filterType }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterType == null,
                    onClick = { filterType = null },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = filterType == "XRAY",
                    onClick = { filterType = if (filterType == "XRAY") null else "XRAY" },
                    label = { Text("X-ray") },
                    leadingIcon = { Icon(Icons.Default.Image, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = filterType == "MRI",
                    onClick = { filterType = if (filterType == "MRI") null else "MRI" },
                    label = { Text("MRI") },
                    leadingIcon = { Icon(Icons.Default.BlurOn, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = filterType == "HISTOPATHOLOGY",
                    onClick = { filterType = if (filterType == "HISTOPATHOLOGY") null else "HISTOPATHOLOGY" },
                    label = { Text("Histo") },
                    leadingIcon = { Icon(Icons.Default.Biotech, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = filterType == "RECORDING",
                    onClick = { filterType = if (filterType == "RECORDING") null else "RECORDING" },
                    label = { Text("Recording") },
                    leadingIcon = { Icon(Icons.Default.Mic, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = filterType == "MANUAL",
                    onClick = { filterType = if (filterType == "MANUAL") null else "MANUAL" },
                    label = { Text("Notes") },
                    leadingIcon = { Icon(Icons.Default.EditNote, null, Modifier.size(16.dp)) }
                )
            }

            if (filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.History,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No entries found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        TimelineEntryCard(
                            entry = entry,
                            isExpanded = expandedEntryId == entry.id,
                            onToggleExpand = {
                                expandedEntryId = if (expandedEntryId == entry.id) null else entry.id
                            },
                            onAnalyze = { updatedEntry ->
                                scope.launch(Dispatchers.IO) {
                                    db.medicalEntryDao().updateEntry(updatedEntry)
                                }
                            },
                            context = context
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineEntryCard(
    entry: MedicalEntryEntity,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAnalyze: (MedicalEntryEntity) -> Unit,
    context: android.content.Context
) {
    var localAnalysis by remember(entry.id) { mutableStateOf(entry.analysisResult) }
    var isAnalyzing by remember { mutableStateOf(false) }
    // Sub-agent state
    var subAgentStage by remember { mutableStateOf(0) }  // 0=idle 1=stage1 2=stage2
    var streamingText by remember { mutableStateOf("") }
    var stage1Result by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val icon = when (entry.entryType) {
        "XRAY" -> Icons.Default.Image
        "MRI" -> Icons.Default.BlurOn
        "HISTOPATHOLOGY" -> Icons.Default.Biotech
        "RECORDING" -> Icons.Default.Mic
        "DOCUMENT" -> Icons.Default.Description
        "MANUAL" -> Icons.Default.EditNote
        else -> Icons.AutoMirrored.Filled.Article
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Timeline dot + connector line that stretches to match card height
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight()
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Entry card
        Card(
            onClick = onToggleExpand,
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        entry.title.ifBlank { entry.entryType.lowercase().replaceFirstChar { it.uppercase() } },
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        dateFormat.format(Date(entry.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isExpanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    // Image thumbnail
                    if (entry.imagePaths.isNotBlank()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                        ) {
                            AsyncImage(
                                model = entry.imagePaths,
                                contentDescription = "Medical image",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (entry.content.isNotBlank()) {
                        Text(
                            entry.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (localAnalysis.isNotBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "AI Analysis",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    localAnalysis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    } else if (subAgentStage > 0 || streamingText.isNotBlank()) {
                        // Running sub-agent — show streaming card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        when (subAgentStage) {
                                            1 -> "Stage 1 — Observing image…"
                                            2 -> "Stage 2 — Clinical interpretation…"
                                            else -> "Analysing…"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                if (streamingText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        streamingText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    } else {
                        val isImagingEntry = entry.entryType in listOf("XRAY", "MRI", "HISTOPATHOLOGY") &&
                            entry.imagePaths.isNotBlank()
                        if (isImagingEntry) {
                            // Sub-agent + quick analysis buttons for imaging entries
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (!isAnalyzing) {
                                            isAnalyzing = true
                                            subAgentStage = 1
                                            streamingText = ""
                                            stage1Result = ""
                                            scope.launch {
                                                try {
                                                    val inferenceModel = withContext(Dispatchers.IO) {
                                                        InferenceModel.getInstance(context)
                                                    }
                                                    // Load bitmap for Stage 1
                                                    val bitmap: android.graphics.Bitmap? = withContext(Dispatchers.IO) {
                                                        try {
                                                            val uri = android.net.Uri.parse(entry.imagePaths)
                                                            when {
                                                                uri.scheme == "file" -> android.graphics.BitmapFactory.decodeFile(uri.path)
                                                                uri.scheme == "content" -> {
                                                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                                                        android.graphics.ImageDecoder.decodeBitmap(
                                                                            android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                                                                        ) { dec, _, _ -> dec.isMutableRequired = true }
                                                                    } else {
                                                                        @Suppress("DEPRECATION")
                                                                        android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                                                                    }
                                                                }
                                                                else -> null
                                                            }
                                                        } catch (e: Exception) { null }
                                                    }
                                                    // == Stage 1: vision observation ==
                                                    val stage1Prompt = when (entry.entryType) {
                                                        "HISTOPATHOLOGY" -> """You are a pathology AI. Systematically describe this histopathology slide for clinical documentation.
Report: 1) Stain type, 2) Tissue type and architecture, 3) Cellular morphology, 4) Mitotic figures per HPF, 5) Inflammatory infiltrate, 6) Vascular/stromal changes, 7) Dysplasia/atypia/malignant features.
Be precise, structured, and clinically useful. Do not wrap in a code block."""
                                                        "MRI" -> """You are a radiologist AI specialising in MRI. Systematically describe this MRI image for clinical documentation.
Report: 1) Likely sequence and plane, 2) Body region and laterality, 3) Signal characteristics, 4) Focal lesions (location, size, signal, margins, oedema), 5) Mass effect, 6) Enhancement if visible, 7) Incidental findings.
Be precise. Do not wrap in a code block."""
                                                        else -> """You are a radiologist AI. Systematically describe this X-ray for clinical documentation.
Report: 1) Image quality, 2) Bony structures, 3) Soft tissue, 4) Lung fields, 5) Cardiac silhouette, 6) Mediastinum, 7) Pleural spaces, 8) Any abnormal findings with location.
Be precise. Do not wrap in a code block."""
                                                    }
                                                    val imgList = if (bitmap != null) listOf(bitmap) else emptyList()
                                                    var s1 = ""
                                                    val fut1 = inferenceModel.generateResponseAsync(stage1Prompt, imgList) { token, _ ->
                                                        if (token.isNotEmpty()) { s1 += token; streamingText = s1 }
                                                    }
                                                    withContext(Dispatchers.IO) { fut1.get() }
                                                    stage1Result = s1.trim()

                                                    // == Stage 2: clinical interpretation (text only) ==
                                                    subAgentStage = 2
                                                    streamingText = ""
                                                    val stage2Prompt = when (entry.entryType) {
                                                        "HISTOPATHOLOGY" -> """You are a senior pathologist AI. A junior agent produced these histopathological observations:
${stage1Result}
Clinical context — ${entry.title}. Tissue: ${entry.content}.
Provide: 1) **Pathological Diagnosis** (primary + differentials), 2) **WHO Classification/Grade**, 3) **Tumour/Lesion Characteristics**, 4) **IHC Markers** recommended, 5) **Staging Implications**, 6) **Urgency**.
Format in Markdown. Do not wrap in a code block."""
                                                        "MRI" -> """You are a consultant radiologist AI. A junior agent produced these MRI observations:
${stage1Result}
Clinical context — ${entry.title}. Region: ${entry.content}.
Provide: 1) **Primary Impression**, 2) **Differential Diagnoses** (ranked), 3) **Structured Scoring** (BIRADS/PIRADS/ACR as applicable), 4) **Key Abnormalities**, 5) **Recommended Follow-up**, 6) **Urgency Level**.
Format in Markdown. Do not wrap in a code block."""
                                                        else -> """You are a consultant radiologist AI. A junior agent produced these radiographic observations:
${stage1Result}
Clinical context — ${entry.title}. ${entry.content}.
Provide: 1) **Primary Diagnosis**, 2) **Differentials** (ranked), 3) **Severity**, 4) **Recommended Additional Imaging**, 5) **Clinical Correlation**, 6) **Urgency Level**.
Format in Markdown. Do not wrap in a code block."""
                                                    }
                                                    var s2 = ""
                                                    val fut2 = inferenceModel.generateResponseAsync(stage2Prompt, emptyList()) { token, _ ->
                                                        if (token.isNotEmpty()) { s2 += token; streamingText = s2 }
                                                    }
                                                    withContext(Dispatchers.IO) { fut2.get() }

                                                    // Combine and save
                                                    val combined = buildString {
                                                        appendLine("**Observations (Stage 1):**")
                                                        appendLine(stage1Result)
                                                        appendLine()
                                                        appendLine("**Clinical Interpretation (Stage 2):**")
                                                        append(s2.trim())
                                                    }
                                                    localAnalysis = combined
                                                    onAnalyze(entry.copy(analysisResult = combined))
                                                } catch (e: Exception) {
                                                    Log.e("LongitudinalHistory", "Sub-agent failed", e)
                                                    localAnalysis = "Error: ${e.message}"
                                                } finally {
                                                    isAnalyzing = false
                                                    subAgentStage = 0
                                                    streamingText = ""
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isAnalyzing,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.AccountTree, null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sub-agent", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (!isAnalyzing) {
                                            isAnalyzing = true
                                            subAgentStage = 1
                                            streamingText = ""
                                            scope.launch {
                                                try {
                                                    val inferenceModel = withContext(Dispatchers.IO) {
                                                        InferenceModel.getInstance(context)
                                                    }
                                                    // Load bitmap for Quick vision analysis
                                                    val bitmap: android.graphics.Bitmap? = withContext(Dispatchers.IO) {
                                                        try {
                                                            val uri = android.net.Uri.parse(entry.imagePaths)
                                                            when {
                                                                uri.scheme == "file" -> android.graphics.BitmapFactory.decodeFile(uri.path)
                                                                uri.scheme == "content" -> {
                                                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                                                        android.graphics.ImageDecoder.decodeBitmap(
                                                                            android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                                                                        ) { dec, _, _ -> dec.isMutableRequired = true }
                                                                    } else {
                                                                        @Suppress("DEPRECATION")
                                                                        android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                                                                    }
                                                                }
                                                                else -> null
                                                            }
                                                        } catch (e: Exception) { null }
                                                    }
                                                    val typeLabel = when (entry.entryType) {
                                                        "HISTOPATHOLOGY" -> "histopathology slide"
                                                        "MRI" -> "MRI scan"
                                                        else -> "X-ray"
                                                    }
                                                    val prompt = if (bitmap != null)
                                                        "You are a specialist AI. Analyse this $typeLabel image.\nTitle: ${entry.title}\nContext: ${entry.content}\nProvide: 1) Key imaging findings, 2) Diagnosis/differentials, 3) Urgency. Be concise."
                                                    else
                                                        "You are a specialist AI. Analyse this $typeLabel entry.\nTitle: ${entry.title}\nContext: ${entry.content}\nProvide: 1) Key findings, 2) Diagnosis/differentials, 3) Urgency. Be concise."
                                                    val imgList = if (bitmap != null) listOf(bitmap) else emptyList()
                                                    var result = ""
                                                    val future = inferenceModel.generateResponseAsync(prompt, imgList) { token, _ ->
                                                        if (token.isNotEmpty()) { result += token; streamingText = result }
                                                    }
                                                    withContext(Dispatchers.IO) { future.get() }
                                                    localAnalysis = result.trim()
                                                    onAnalyze(entry.copy(analysisResult = localAnalysis))
                                                } catch (e: Exception) {
                                                    localAnalysis = "Error: ${e.message}"
                                                } finally {
                                                    isAnalyzing = false
                                                    subAgentStage = 0
                                                    streamingText = ""
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isAnalyzing,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Quick", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        } else {
                            // Non-imaging entry: single-stage text analysis
                            OutlinedButton(
                                onClick = {
                                    if (!isAnalyzing) {
                                        isAnalyzing = true
                                        subAgentStage = 1
                                        streamingText = ""
                                        scope.launch {
                                            try {
                                                val inferenceModel = withContext(Dispatchers.IO) {
                                                    InferenceModel.getInstance(context)
                                                }
                                                val typeLabel = when (entry.entryType) {
                                                    "RECORDING" -> "voice recording / transcription"
                                                    "MANUAL" -> "clinical note"
                                                    "DOCUMENT" -> "medical document"
                                                    else -> entry.entryType.lowercase()
                                                }
                                                val prompt = """You are a specialist AI medical assistant.
Analyse this $typeLabel entry and provide a concise clinical summary.
Title: ${entry.title}
Content: ${entry.content}
Provide: 1) Key clinical findings, 2) Significance, 3) Recommended follow-up.
Be concise. Do not wrap in a code block."""
                                                var result = ""
                                                val future = inferenceModel.generateResponseAsync(prompt, emptyList()) { token, _ ->
                                                    if (token.isNotEmpty()) { result += token; streamingText = result }
                                                }
                                                withContext(Dispatchers.IO) { future.get() }
                                                localAnalysis = result.trim()
                                                onAnalyze(entry.copy(analysisResult = localAnalysis))
                                            } catch (e: Exception) {
                                                Log.e("LongitudinalHistory", "Inline analysis failed", e)
                                                localAnalysis = "Error: ${e.message}"
                                            } finally {
                                                isAnalyzing = false
                                                subAgentStage = 0
                                                streamingText = ""
                                            }
                                        }
                                    }
                                },
                                enabled = !isAnalyzing,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Generate Analysis")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Created at ${timeFormat.format(Date(entry.createdAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
