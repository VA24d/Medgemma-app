package com.google.mediapipe.examples.llminference.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.llminference.cloud.CloudChartProcessor
import com.google.mediapipe.examples.llminference.cloud.CloudProgress
import com.google.mediapipe.examples.llminference.settings.LocalModelFiles
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudAnalysisScreen(
    patientId: Long,
    onBack: () -> Unit,
    onComplete: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isAllPatients = patientId == CloudChartProcessor.ALL_PATIENTS_ID

    var status by remember { mutableStateOf("Ready") }
    var detail by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(0f) }
    var isRunning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var forceReprocess by remember { mutableStateOf(false) }

    val inferenceTier = remember { LocalModelFiles.getInferenceTier(context) }
    val connectionHint = remember {
        when (LocalModelFiles.getCloudConnectionMode(context)) {
            LocalModelFiles.CLOUD_MODE_WIFI ->
                "Wi-Fi → ${LocalModelFiles.getCloudServerUrlWifi(context)}"
            else -> "USB → ${LocalModelFiles.getCloudServerUrlUsb(context)}"
        }
    }
    val backendLabel = when (inferenceTier) {
        LocalModelFiles.TIER_GEMINI_API -> "Gemini API (via laptop companion)"
        LocalModelFiles.TIER_EDGE_OLLAMA -> "Edge GPU (Ollama on laptop)"
        else -> "On-device only"
    }

    fun start() {
        error = null
        isRunning = true
        status = "Connecting…"
        progress = 0f
        job = scope.launch {
            val processor = CloudChartProcessor(context)
            val onProg: (CloudProgress) -> Unit = { p ->
                status = p.message.ifBlank { p.phase }
                detail = when {
                    p.total > 0 -> "${p.patientName}: ${p.current}/${p.total} — ${p.entryTitle}"
                    p.patientName.isNotBlank() -> p.patientName
                    else -> ""
                }
                if (p.total > 0) progress = p.current.toFloat() / p.total
            }
            val result = if (isAllPatients) {
                processor.processAllPatients(forceReprocess, onProg)
            } else {
                processor.processPatient(patientId, forceReprocess, onProg).map { 1 }
            }
            result.fold(
                onSuccess = {
                    status = "Complete"
                    progress = 1f
                    isRunning = false
                    onComplete()
                },
                onFailure = { e ->
                    error = e.localizedMessage ?: "Failed"
                    status = "Error"
                    isRunning = false
                },
            )
        }
    }

    LaunchedEffect(Unit) {
        if (!isRunning) start()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isAllPatients) "Cloud: All Patients" else "Cloud Analysis")
                },
                navigationIcon = {
                    IconButton(onClick = {
                        job?.cancel()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (inferenceTier == LocalModelFiles.TIER_GEMINI_API) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        "Clinical data is sent to Google Gemini via your API key on the laptop server.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(backendLabel, style = MaterialTheme.typography.titleMedium)
                        Text(connectionHint, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Open http://localhost:8787 on laptop for dashboard",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            Text(status, style = MaterialTheme.typography.titleMedium)
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodyMedium)
            }
            if (isRunning) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = forceReprocess, onCheckedChange = { forceReprocess = it }, enabled = !isRunning)
                Text("Force reprocess all entries")
            }

            if (!isRunning) {
                Button(
                    onClick = { start() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (error != null) "Retry" else "Run again")
                }
            } else {
                OutlinedButton(
                    onClick = { job?.cancel(); isRunning = false; status = "Cancelled" },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cancel")
                }
            }
        }
    }
}
