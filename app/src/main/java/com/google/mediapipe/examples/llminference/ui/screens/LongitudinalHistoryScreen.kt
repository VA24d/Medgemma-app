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
                    } else {
                        OutlinedButton(
                            onClick = {
                                if (!isAnalyzing) {
                                    isAnalyzing = true
                                    scope.launch {
                                        try {
                                            val inferenceModel = withContext(Dispatchers.IO) {
                                                InferenceModel.getInstance(context)
                                            }
                                            val typeLabel = when (entry.entryType) {
                                                "RECORDING" -> "voice recording / transcription"
                                                "MANUAL" -> "clinical note"
                                                "XRAY" -> "X-ray / MRI imaging entry"
                                                "HISTOPATHOLOGY" -> "histopathology entry"
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
                                                if (token.isNotEmpty()) result += token
                                            }
                                            withContext(Dispatchers.IO) { future.get() }
                                            localAnalysis = result.trim()
                                            onAnalyze(entry.copy(analysisResult = localAnalysis))
                                        } catch (e: Exception) {
                                            Log.e("LongitudinalHistory", "Inline analysis failed", e)
                                            localAnalysis = "Error: ${e.message}"
                                        } finally {
                                            isAnalyzing = false
                                        }
                                    }
                                }
                            },
                            enabled = !isAnalyzing
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Analysing…")
                            } else {
                                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
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
