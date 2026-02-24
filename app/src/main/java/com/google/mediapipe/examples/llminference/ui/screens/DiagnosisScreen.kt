package com.google.mediapipe.examples.llminference.ui.screens

import android.app.TimePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import com.google.mediapipe.examples.llminference.worker.ScheduledPrognosisWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "DiagnosisScreen"

// ─── Thinking-marker stripping ───────────────────────────────────────────────────
// These special tokens bracket the model's chain-of-thought. When thinking is
// disabled we strip them from every token as it arrives and do a final regex
// pass before saving, so they never appear in the UI or the database.

private const val THINK_START = "<unused94>"
private const val THINK_END   = "<unused95>"

/** Strip thinking-marker tokens from a single streamed token. */
private fun stripThinkingMarkers(token: String): String =
    token.replace(THINK_START, "").replace(THINK_END, "").replace("thought>", "")

/** Full post-pass: remove entire <unused94>…<unused95> blocks plus stray tokens. */
private fun stripThinkingMarkersFull(text: String): String {
    // Remove complete thought blocks (non-greedy)
    var result = text.replace(Regex("<unused94>thought>[\\s\\S]*?<unused95>"), "")
    // Remove any remaining stray markers
    result = result.replace(THINK_START, "").replace(THINK_END, "")
        .replace("thought>", "").trim()
    return result
}

/** Clean a stored diagnosis string before embedding it back in a prompt. */
private fun cleanDiagnosisForPrompt(text: String): String = stripThinkingMarkersFull(text)

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
        .filter { it.entryType in listOf("XRAY", "MRI", "HISTOPATHOLOGY") && it.imagePaths.isNotBlank() }
        .maxByOrNull { it.createdAt }

private fun buildFullPrompt(
    entries: List<MedicalEntryEntity>,
    hasImage: Boolean,
    pastDiagnoses: List<DiagnosisEntity> = emptyList(),
    useCachedDescriptions: Boolean = false
): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val imagingTypes = setOf("XRAY", "MRI", "HISTOPATHOLOGY")
    val summary = entries.joinToString("\n") { e ->
        val isImaging = e.entryType in imagingTypes
        val ai = when {
            useCachedDescriptions && isImaging && e.analysisResult.isNotBlank() ->
                "\n  [Cached Image Description]:\n${e.analysisResult.trim()}"
            e.analysisResult.isNotBlank() ->
                " | AI note: ${e.analysisResult.take(120)}…"
            else -> ""
        }
        "[${fmt.format(Date(e.createdAt))}][${e.entryType}] ${e.title}: ${e.content.take(120)}$ai"
    }
    val imgNote = if (hasImage && !useCachedDescriptions) "\nThe most recent imaging study is attached. Analyse it as part of the diagnosis.\n" else ""

    // Include context from past diagnoses for continuity of care
    val historyContext = if (pastDiagnoses.isNotEmpty()) {
        val recent = pastDiagnoses.take(3)
        val historyLines = recent.joinToString("\n\n") { d ->
            val dDate = fmt.format(Date(d.generatedAt))
            "--- Previous ${d.scope} diagnosis ($dDate, ${d.entryCount} entries) ---\n${cleanDiagnosisForPrompt(d.diagnosis).take(500)}"
        }
        "\n\nPREVIOUS DIAGNOSIS HISTORY (most recent first):\n$historyLines\n\nUse this history for continuity of care. Note any changes or progression.\n"
    } else ""

    return """You are a specialist AI medical assistant helping a clinician.

Patient has ${entries.size} medical record entries (oldest→newest):
$summary
$imgNote$historyContext
Provide:
1. **Summary of findings**
2. **Diagnosis / differentials** (with confidence levels)
3. **Disease progression** (improving / stable / deteriorating)
4. **Recommended next steps** (investigations, referrals, treatment)
5. **Red flags** to monitor

Format in Markdown. Be concise and clinically precise. Do not wrap your response in a code block."""
}

private fun buildIncrementalPrompt(
    newEntries: List<MedicalEntryEntity>,
    prior: DiagnosisEntity,
    hasImage: Boolean,
    olderDiagnoses: List<DiagnosisEntity> = emptyList(),
    useCachedDescriptions: Boolean = false
): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val priorDate = fmt.format(Date(prior.generatedAt))
    val imagingTypes = setOf("XRAY", "MRI", "HISTOPATHOLOGY")
    val summary = newEntries.joinToString("\n") { e ->
        val isImaging = e.entryType in imagingTypes
        val ai = when {
            useCachedDescriptions && isImaging && e.analysisResult.isNotBlank() ->
                "\n  [Cached Image Description]:\n${e.analysisResult.trim()}"
            e.analysisResult.isNotBlank() ->
                " | AI: ${e.analysisResult.take(120)}…"
            else -> ""
        }
        "[${fmt.format(Date(e.createdAt))}][${e.entryType}] ${e.title}: ${e.content.take(120)}$ai"
    }
    val imgNote = if (hasImage && !useCachedDescriptions) "\nThe most recent new imaging study is attached. Analyse it.\n" else ""

    // Include older diagnosis history for progression tracking
    val olderContext = if (olderDiagnoses.isNotEmpty()) {
        val lines = olderDiagnoses.take(2).joinToString("\n\n") { d ->
            "--- ${d.scope} diagnosis (${fmt.format(Date(d.generatedAt))}) ---\n${cleanDiagnosisForPrompt(d.diagnosis).take(400)}"
        }
        "\n\nOLDER DIAGNOSIS HISTORY (for progression context):\n$lines\n"
    } else ""

    return """You are a specialist AI medical assistant helping a clinician.

MOST RECENT DIAGNOSIS (generated $priorDate):
${cleanDiagnosisForPrompt(prior.diagnosis).take(700)}
$olderContext
NEW entries since then (${newEntries.size} entries):
$summary
$imgNote
Provide an UPDATED assessment:
1. **What has changed** since the last diagnosis
2. **Updated diagnosis / differentials**
3. **Progression** (improving / stable / deteriorating)
4. **Recommended next steps**
5. **Red flags added or resolved**

Format in Markdown. Reference changes explicitly. Do not wrap your response in a code block."""
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
    var generationJob by remember { mutableStateOf<Job?>(null) }
    var generationFuture by remember { mutableStateOf<java.util.concurrent.Future<*>?>(null) }

    // Per-generation toggles (initialized from global settings)
    var thinkingToggle by remember { mutableStateOf(LocalModelFiles.isThinkingEnabled(context)) }
    var visionToggle by remember { mutableStateOf(LocalModelFiles.isVisionEnabled(context)) }
    // When ON: embed full cached image descriptions as text; no bitmap sent to encoder
    var useDescriptionsToggle by remember { mutableStateOf(false) }

    // Schedule state
    var scheduleEnabled by remember { mutableStateOf(LocalModelFiles.isScheduledPrognosisEnabled(context)) }
    var scheduleHour by remember { mutableStateOf(LocalModelFiles.getScheduleHour(context)) }
    var scheduleMinute by remember { mutableStateOf(LocalModelFiles.getScheduleMinute(context)) }

    LaunchedEffect(patientId) {
        launch { db.medicalEntryDao().getEntriesForPatient(patientId).collect { entries = it } }
        launch { db.diagnosisDao().getDiagnosesForPatient(patientId).collect { pastDiagnoses = it } }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationFuture?.cancel(true)
        generationJob = null
        generationFuture = null
        isGenerating = false
    }

    fun runGeneration(incremental: Boolean) {
        generationJob = scope.launch {
            isGenerating = true
            streamingText = ""
            tokenCount = 0
            usingVision = false
            currentModelName = modelDisplayName()
            val thinkingOn = thinkingToggle

            // State machine to suppress thinking blocks when thinking is disabled.
            // The model surrounds chain-of-thought with <unused94>thought>…<unused95>.
            var insideThinkingBlock = false

            try {
                val inferenceModel = withContext(Dispatchers.IO) { InferenceModel.getInstance(context) }
                // Apply per-generation thinking mode
                inferenceModel.updateThinkingMode(thinkingOn)

                val sorted = entries.sortedBy { it.createdAt }

                val latestDiag = withContext(Dispatchers.IO) { db.diagnosisDao().getLatestDiagnosis(patientId) }

                val targetEntries = if (incremental && latestDiag != null) {
                    sorted.filter { it.createdAt > latestDiag.generatedAt }.takeIf { it.isNotEmpty() } ?: sorted
                } else sorted

                // Vision: only attempt if toggle is ON AND we're not using text descriptions
                val useDescriptions = useDescriptionsToggle
                val visionEntry = if (visionToggle && !useDescriptions) pickVisionEntry(targetEntries) else null
                val bitmap: Bitmap? = visionEntry?.let { loadBitmapFromEntry(context, it) }
                usingVision = bitmap != null
                if (useDescriptions) {
                    Log.i(TAG, "Vision encoder: skipped — using cached image descriptions")
                } else if (bitmap != null) {
                    Log.i(TAG, "Vision encoder: using image from entry id=${visionEntry?.id} type=${visionEntry?.entryType}")
                } else {
                    Log.i(TAG, "Vision encoder: ${if (!visionToggle) "disabled by toggle" else "no readable image (entries: ${targetEntries.size})"}")
                }

                val isReallyIncremental = incremental && latestDiag != null && targetEntries != sorted
                val prompt = if (isReallyIncremental) {
                    val olderDiags = pastDiagnoses.drop(1) // skip latest (already used as "prior")
                    buildIncrementalPrompt(targetEntries, latestDiag!!, bitmap != null, olderDiags, useDescriptions)
                } else {
                    buildFullPrompt(sorted, bitmap != null, pastDiagnoses, useDescriptions)
                }

                Log.i(TAG, "Generating: scope=${if (isReallyIncremental) "INCREMENTAL" else "FULL"} entries=${targetEntries.size} vision=$usingVision useDescriptions=$useDescriptions model=${currentModelName} thinking=$thinkingOn")

                val images = if (bitmap != null) listOf(bitmap) else emptyList()
                val future = inferenceModel.generateResponseAsync(prompt, images) { token, done ->
                    if (!done && token.isNotEmpty()) {
                        if (!thinkingOn) {
                            // State-machine: suppress everything between start and end markers.
                            // The model wraps chain-of-thought in <unused94>thought>…<unused95>.
                            when {
                                token.contains(THINK_START) || token.contains("thought>") -> {
                                    insideThinkingBlock = true
                                }
                                token.contains(THINK_END) -> {
                                    insideThinkingBlock = false
                                    // End marker discarded; don't append
                                }
                                !insideThinkingBlock -> {
                                    streamingText += token
                                    tokenCount++
                                }
                                // else: inside thinking block, silently discard token
                            }
                        } else {
                            streamingText += token
                            tokenCount++
                        }
                    }
                }
                generationFuture = future
                withContext(Dispatchers.IO) { future.get() }

                // Final strip pass — catches any multi-token marker fragments
                val diagnosisText = if (!thinkingOn) stripThinkingMarkersFull(streamingText) else streamingText
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
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Generation failed", e)
                    streamingText += "\n\n\u26a0\ufe0f Error: ${e.message}"
                }
            } finally {
                isGenerating = false
                generationJob = null
                generationFuture = null
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
                    thinkingEnabled = thinkingToggle,
                    visionAvailable = try { InferenceModel.getInstance(context).isVisionAvailable } catch (_: Exception) { false }
                )
            }
            // Per-generation toggles
            if (!isGenerating) {
                item {
                    GenerationTogglesCard(
                        thinkingEnabled = thinkingToggle,
                        onThinkingChange = { thinkingToggle = it },
                        visionEnabled = visionToggle,
                        onVisionChange = { visionToggle = it },
                        visionAvailable = try { InferenceModel.getInstance(context).isVisionAvailable } catch (_: Exception) { false },
                        useDescriptions = useDescriptionsToggle,
                        onUseDescriptionsChange = { useDescriptionsToggle = it }
                    )
                }
            }
            if (isGenerating) {
                item {
                    GeneratingCard(streamingText, tokenCount, currentModelName, usingVision, thinkingToggle) {
                        stopGeneration()
                    }
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
            // Scheduled prognosis
            item {
                ScheduleCard(
                    enabled = scheduleEnabled,
                    hour = scheduleHour,
                    minute = scheduleMinute,
                    onToggle = { enabled ->
                        scheduleEnabled = enabled
                        LocalModelFiles.setScheduledPrognosisEnabled(context, enabled)
                        ScheduledPrognosisWorker.syncSchedule(context)
                    },
                    onTimeChange = { h, m ->
                        scheduleHour = h
                        scheduleMinute = m
                        LocalModelFiles.setScheduleTime(context, h, m)
                        if (scheduleEnabled) ScheduledPrognosisWorker.syncSchedule(context)
                    }
                )
            }
            if (pastDiagnoses.isNotEmpty()) {
                item {
                    Text(
                        "Latest Diagnosis",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Show only the most recent diagnosis on this screen
                item {
                    val latest = pastDiagnoses.first()
                    DiagnosisHistoryCard(latest) {
                        scope.launch(Dispatchers.IO) { db.diagnosisDao().deleteDiagnosis(latest) }
                    }
                }
                if (pastDiagnoses.size > 1) {
                    item {
                        var showOlder by remember { mutableStateOf(false) }
                        Column {
                            TextButton(
                                onClick = { showOlder = !showOlder },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    if (showOlder) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (showOlder) "Hide older (${pastDiagnoses.size - 1})" else "Show older diagnoses (${pastDiagnoses.size - 1})")
                            }
                            if (showOlder) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    pastDiagnoses.drop(1).forEach { diag ->
                                        DiagnosisHistoryCard(diag) {
                                            scope.launch(Dispatchers.IO) { db.diagnosisDao().deleteDiagnosis(diag) }
                                        }
                                    }
                                }
                            }
                        }
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
private fun GeneratingCard(streamingText: String, tokenCount: Int, modelName: String, usingVision: Boolean, thinkingEnabled: Boolean, onStop: () -> Unit) {
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
                        buildString {
                            append("$modelName · $tokenCount tokens")
                            if (thinkingEnabled) append(" · \uD83E\uDDE0")
                            if (usingVision) append(" · \uD83D\uDC41 vision")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onStop,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stop", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (streamingText.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))
                MarkdownText(
                    markdown = streamingText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    textColor = MaterialTheme.colorScheme.onSecondaryContainer
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
                val preview = remember(diagnosis.id) { stripThinkingMarkersFull(diagnosis.diagnosis) }
                Text(
                    preview.take(180).let { if (preview.length > 180) "$it…" else it },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            AnimatedVisibility(expanded) {
                val cleanedDiagnosis = remember(diagnosis.id) { stripThinkingMarkersFull(diagnosis.diagnosis) }
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    MarkdownText(markdown = cleanedDiagnosis, modifier = Modifier.fillMaxWidth())
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

@Composable
private fun GenerationTogglesCard(
    thinkingEnabled: Boolean,
    onThinkingChange: (Boolean) -> Unit,
    visionEnabled: Boolean,
    onVisionChange: (Boolean) -> Unit,
    visionAvailable: Boolean,
    useDescriptions: Boolean,
    onUseDescriptionsChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Generation Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Psychology, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Thinking Mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("Extended reasoning chain", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = thinkingEnabled,
                    onCheckedChange = onThinkingChange,
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.tertiary)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Visibility, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Vision Analysis", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        if (visionAvailable) "Analyze medical images" else "Vision encoder not available",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (visionAvailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                    )
                }
                Switch(
                    checked = visionEnabled && visionAvailable && !useDescriptions,
                    onCheckedChange = onVisionChange,
                    enabled = visionAvailable && !useDescriptions,
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.secondary)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Description, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Use Image Descriptions", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "Embed cached imaging descriptions as text — faster, no vision encoder, works even without mmproj",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = useDescriptions,
                    onCheckedChange = onUseDescriptionsChange,
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    enabled: Boolean,
    hour: Int,
    minute: Int,
    onToggle: (Boolean) -> Unit,
    onTimeChange: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    val amPmStr = remember(hour, minute) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Scheduled Prognosis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                )
            }
            Text(
                "Auto-generate a new prognosis daily using all patient records.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedVisibility(enabled) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Daily at", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(amPmStr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, h, m -> onTimeChange(h, m) },
                                    hour,
                                    minute,
                                    false
                                ).show()
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text("Change", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
