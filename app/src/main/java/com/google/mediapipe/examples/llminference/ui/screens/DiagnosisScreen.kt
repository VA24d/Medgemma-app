package com.google.mediapipe.examples.llminference.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.llminference.InferenceModel
import com.google.mediapipe.examples.llminference.MarkdownText
import com.google.mediapipe.examples.llminference.data.DiagnosisEntity
import com.google.mediapipe.examples.llminference.data.MedicalDatabase
import com.google.mediapipe.examples.llminference.data.MedicalEntryEntity
import com.google.mediapipe.examples.llminference.settings.LocalModelFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "DiagnosisScreen"

// ─── Helpers ────────────────────────────────────────────────────────────────────

private fun modelDisplayName(): String {
    val path = InferenceModel.model.path
    return File(path).nameWithoutExtension.replace('-', ' ').replace('_', ' ')
}

private suspend fun loadBitmapFromEntry(
    context: android.content.Context,
    entry: MedicalEntryEntity
): Bitmap? {
    if (entry.imagePaths.isBlank()) return null
    val first = entry.imagePaths.split(',').firstOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(first)
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
                else -> BitmapFactory.decodeFile(first)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot load bitmap from $first: ${e.message}")
            null
        }
    }
}

private fun pickVisionEntry(entries: List<MedicalEntryEntity>): MedicalEntryEntity? =
    entries
        .filter { it.entryType in listOf("XRAY", "HISTOPATHOLOGY") && it.imagePaths.isNotBlank() }
        .maxByOrNull { it.createdAt }

private fun buildFullPrompt(entries: List<MedicalEntryEntity>, hasImage: Boolean): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val summary = entries.joinToString("\n") { e ->
        val ai = if (e.analysisResult.isNotBlank()) " | AI note: ${e.analysisResult.take(120)}…" else ""
        "[${fmt.format(Date(e.createdAt))}][${e.entryType}] ${e.title}: ${e.content.take(120)}$ai"
    }
    val imgNote = if (hasImage) "\nThe most recent imaging study is attached. Analyse it as part of the diagnosis.\n" else ""
    return """You are a specialist AI medical assistant helping a clinician.

Patient has ${entries.size} medical record entries (oldest→newest):
$summary
$imgNote
Provide:
1. **Summary of findings**
2. **Diagnosis / differentials** (with confidence levels)
3. **Disease progression** (improving / stable / deteriorating)
4. **Recommended next steps** (investigations, referrals, treatment)
5. **Red flags** to monitor

Format in Markdown. Be concise and clinically precise."""
}

private fun buildIncrementalPrompt(
    newEntries: List<MedicalEntryEntity>,
    prior: DiagnosisEntity,
    hasImage: Boolean
): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val priorDate = fmt.format(Date(prior.generatedAt))
    val summary = newEntries.joinToString("\n") { e ->
        val ai = if (e.analysisResult.isNotBlank()) " | AI: ${e.analysisResult.take(120)}…" else ""
        "[${fmt.format(Date(e.createdAt))}][${e.entryType}] ${e.title}: ${e.content.take(120)}$ai"
    }
    val imgNote = if (hasImage) "\nThe most recent new imaging study is attached. Analyse it.\n" else ""
    return """You are a specialist AI medical assistant helping a clinician.

PREVIOUS DIAGNOSIS (generated $priorDate):
${prior.diagnosis.take(700)}

NEW entries since then (${newEntries.size} entries):
$summary
$imgNote
Provide an UPDATED assessment:
1. **What has changed** since the last diagnosis
2. **Updated diagnosis / differentials**
3. **Progression** (improving / stable / deteriorating)
4. **Recommended next steps**
5. **Red flags added or resolved**

Format in Markdown. Reference changes explicitly."""
}

// ─── Screen ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosisScreen(
    patientId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val db = remember { MedicalDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    var entries by remember { mutableStateOf<List<MedicalEntryEntity>>(emptyList()) }
    var pastDiagnoses by remember { mutableStateOf<List<DiagnosisEntity>>(emptyList()) }

    var isGenerating by remember { mutableStateOf(false) }
    var streamingText by remember { mutableStateOf("") }
    var tokenCount by remember { mutableStateOf(0) }
    var currentModelName by remember { mutableStateOf("") }
    var usingVision by remember { mutableStateOf(false) }

    LaunchedEffect(patientId) {
        launch { db.medicalEntryDao().getEntriesForPatient(patientId).collect { entries = it } }
        launch { db.diagnosisDao().getDiagnosesForPatient(patientId).collect { pastDiagnoses = it } }
    }

    fun runGeneration(incremental: Boolean) {
        scope.launch {
            isGenerating = true
            streamingText = ""
            tokenCount = 0
            usingVision = false
            currentModelName = modelDisplayName()
            val thinkingOn = LocalModelFiles.isThinkingEnabled(context)

            try {
                val inferenceModel = withContext(Dispatchers.IO) { InferenceModel.getInstance(context) }
                val sorted = entries.sortedBy { it.createdAt }

                val latestDiag = withContext(Dispatchers.IO) { db.diagnosisDao().getLatestDiagnosis(patientId) }

                val targetEntries = if (incremental && latestDiag != null) {
                    sorted.filter { it.createdAt > latestDiag.generatedAt }.takeIf { it.isNotEmpty() } ?: sorted
                } else sorted

                val visionEntry = pickVisionEntry(targetEntries)
                val bitmap: Bitmap? = visionEntry?.let { loadBitmapFromEntry(context, it) }
                usingVision = bitmap != null
                if (bitmap != null) {
                    Log.i(TAG, "Vision encoder: using image from entry id=${visionEntry?.id} type=${visionEntry?.entryType}")
                } else {
                    Log.i(TAG, "Vision encoder: no readable image found (entries checked: ${targetEntries.size})")
                }

                val isReallyIncremental = incremental && latestDiag != null && targetEntries != sorted
                val prompt = if (isReallyIncremental) {
                    buildIncrementalPrompt(targetEntries, latestDiag!!, bitmap != null)
                } else {
                    buildFullPrompt(sorted, bitmap != null)
                }

                Log.i(TAG, "Generating: scope=${if (isReallyIncremental) "INCREMENTAL" else "FULL"} entries=${targetEntries.size} vision=$usingVision model=${currentModelName} thinking=$thinkingOn")

                val images = if (bitmap != null) listOf(bitmap) else emptyList()
                val future = inferenceModel.generateResponseAsync(prompt, images) { token, done ->
                    if (!done && token.isNotEmpty()) {
                        streamingText += token
                        tokenCount++
                    }
                }
                withContext(Dispatchers.IO) { future.get() }

                val diagnosisText = streamingText
                withContext(Dispatchers.IO) {
                    db.diagnosisDao().insertDiagnosis(
                        DiagnosisEntity(
                            patientId = patientId,
                            diagnosis = diagnosisText,
                            scope = if (isReallyIncremental) "INCREMENTAL" else "FULL",
                            entryCount = targetEntries.size,
                            modelName = currentModelName,
                            thinkingEnabled = thinkingOn
                        )
                    )
                }
                Log.i(TAG, "Diagnosis saved (${diagnosisText.length} chars)")
            } catch (e: Exception) {
                Log.e(TAG, "Generation failed", e)
                streamingText += "\n\n⚠️ Error: ${e.message}"
            } finally {
                isGenerating = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnosis & Prognosis") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { PatientOverviewCard(entries) }
            item {
                ModelInfoBanner(
                    modelName = modelDisplayName(),
                    thinkingEnabled = LocalModelFiles.isThinkingEnabled(context),
                    visionAvailable = try { InferenceModel.getInstance(context).isVisionAvailable } catch (_: Exception) { false }
                )
            }
            if (isGenerating) {
                item {
                    GeneratingCard(streamingText, tokenCount, currentModelName, usingVision)
                }
            }
            if (!isGenerating) {
                item {
                    GenerateActionsCard(
                        entriesCount = entries.size,
                        latestDiagnosis = pastDiagnoses.firstOrNull(),
                        onGenerateFull = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            runGeneration(false)
                        },
                        onGenerateIncremental = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            runGeneration(true)
                        }
                    )
                }
            }
            if (pastDiagnoses.isNotEmpty()) {
                item {
                    Text(
                        "Saved Diagnoses (${pastDiagnoses.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(pastDiagnoses, key = { it.id }) { diag ->
                    DiagnosisHistoryCard(diag) {
                        scope.launch(Dispatchers.IO) { db.diagnosisDao().deleteDiagnosis(diag) }
                    }
                }
            }
        }
    }
}

// ─── Sub-composables ────────────────────────────────────────────────────────────

@Composable
private fun PatientOverviewCard(entries: List<MedicalEntryEntity>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Assessment, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(8.dp))
                Text("Patient Overview", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(8.dp))
            Text("${entries.size} medical entries", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (entries.isNotEmpty()) {
                val counts = entries.groupingBy { it.entryType }.eachCount()
                Text(
                    counts.entries.joinToString(" · ") { "${it.value} ${it.key.lowercase()}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@Composable
private fun ModelInfoBanner(modelName: String, thinkingEnabled: Boolean, visionAvailable: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Memory, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(modelName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (thinkingEnabled) Badge(containerColor = MaterialTheme.colorScheme.tertiary) { Text("thinking", style = MaterialTheme.typography.labelSmall) }
                    if (visionAvailable) Badge(containerColor = MaterialTheme.colorScheme.secondary) { Text("vision ready", style = MaterialTheme.typography.labelSmall) }
                    else Badge(containerColor = MaterialTheme.colorScheme.errorContainer) { Text("no vision", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer) }
                }
            }
        }
    }
}

@Composable
private fun GeneratingCard(streamingText: String, tokenCount: Int, modelName: String, usingVision: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Generating…", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        "$modelName · $tokenCount tokens" + if (usingVision) " · 👁 vision active" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            if (streamingText.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))
                Text(
                    streamingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.animateContentSize()
                )
            }
        }
    }
}

@Composable
private fun GenerateActionsCard(
    entriesCount: Int,
    latestDiagnosis: DiagnosisEntity?,
    onGenerateFull: () -> Unit,
    onGenerateIncremental: () -> Unit
) {
    val latestDate = latestDiagnosis?.let {
        SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(it.generatedAt))
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Generate Diagnosis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Button(
                onClick = onGenerateFull,
                modifier = Modifier.fillMaxWidth(),
                enabled = entriesCount > 0
            ) {
                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (latestDiagnosis == null) "Generate Full Analysis" else "Regenerate (all entries)")
            }
            if (latestDiagnosis != null) {
                OutlinedButton(
                    onClick = onGenerateIncremental,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Update, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Update from last diagnosis")
                        if (latestDate != null) {
                            Text("Since $latestDate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
                Text(
                    "Uses only new entries added since the last saved diagnosis, with the previous diagnosis as context.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosisHistoryCard(diagnosis: DiagnosisEntity, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val dateStr = remember(diagnosis.generatedAt) {
        SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(Date(diagnosis.generatedAt))
    }

    Card(modifier = Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (diagnosis.scope == "FULL") MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        if (diagnosis.scope == "FULL") "FULL" else "UPDATE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (diagnosis.scope == "FULL") MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(dateStr, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (diagnosis.modelName.isNotBlank()) {
                    Text(diagnosis.modelName.take(28), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f))
                }
                if (diagnosis.thinkingEnabled) Text("· thinking", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                Text("· ${diagnosis.entryCount} entries", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f))
            }

            AnimatedVisibility(!expanded) {
                Text(
                    diagnosis.diagnosis.take(180).let { if (diagnosis.diagnosis.length > 180) "$it…" else it },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            AnimatedVisibility(expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    MarkdownText(markdown = diagnosis.diagnosis, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete diagnosis?") },
            text = { Text("This will permanently remove this diagnosis from the record.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}
