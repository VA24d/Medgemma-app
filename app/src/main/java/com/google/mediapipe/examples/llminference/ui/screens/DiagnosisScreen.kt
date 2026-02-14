package com.google.mediapipe.examples.llminference.ui.screens

import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.llminference.data.MedicalDatabase
import com.google.mediapipe.examples.llminference.data.MedicalEntryEntity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosisScreen(
    patientId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val db = remember { MedicalDatabase.getDatabase(context) }
    var entries by remember { mutableStateOf<List<MedicalEntryEntity>>(emptyList()) }
    var diagnosisResult by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(patientId) {
        db.medicalEntryDao().getEntriesForPatient(patientId).collect { list ->
            entries = list
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
                },
                actions = {
                    if (diagnosisResult != null) {
                        IconButton(onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showQrDialog = true
                        }) {
                            Icon(Icons.Default.QrCode, contentDescription = "Share via QR")
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
            // Summary card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Assessment,
                            null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Patient Overview",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Based on ${entries.size} medical entries",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    val xrayCount = entries.count { it.entryType == "XRAY" }
                    val histoCount = entries.count { it.entryType == "HISTOPATHOLOGY" }
                    val recordingCount = entries.count { it.entryType == "RECORDING" }
                    val manualCount = entries.count { it.entryType == "MANUAL" }
                    if (entries.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            buildString {
                                val parts = mutableListOf<String>()
                                if (xrayCount > 0) parts.add("$xrayCount X-ray/MRI")
                                if (histoCount > 0) parts.add("$histoCount Histopathology")
                                if (recordingCount > 0) parts.add("$recordingCount Recording")
                                if (manualCount > 0) parts.add("$manualCount Notes")
                                append(parts.joinToString(", "))
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Generate button
            if (diagnosisResult == null) {
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        isGenerating = true
                        scope.launch {
                            try {
                                val inferenceModel = com.google.mediapipe.examples.llminference.InferenceModel.getInstance(context)
                                val patientSummary = entries.joinToString("\n") { 
                                    "- [${it.entryType}] ${it.title}: ${it.content.take(100)}..." 
                                }
                                val prompt = "Patient ID: $patientId. Medical Entries:\n$patientSummary\n\nBased on these entries, provide a comprehensive diagnosis summary, key findings, and suggested next steps. Format in Markdown."
                                diagnosisResult = inferenceModel.generateResponse(prompt)
                            } catch (e: Exception) {
                                diagnosisResult = "Error generating diagnosis: ${e.message}"
                            } finally {
                                isGenerating = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isGenerating
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generating…")
                    } else {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate Diagnosis")
                    }
                }
            }

            // Diagnosis result
            if (diagnosisResult != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "AI Diagnosis",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            diagnosisResult!!,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showQrDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCode, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share QR")
                    }
                    OutlinedButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            diagnosisResult = null // Clear previous result
                            isGenerating = true // Immediately show loading state
                            scope.launch {
                                try {
                                    val inferenceModel = com.google.mediapipe.examples.llminference.InferenceModel.getInstance(context)
                                    val patientSummary = entries.joinToString("\n") { 
                                        "- [${it.entryType}] ${it.title}: ${it.content.take(100)}..." 
                                    }
                                    val prompt = "Patient ID: $patientId. Medical Entries:\n$patientSummary\n\nBased on these entries, provide a comprehensive diagnosis summary, key findings, and suggested next steps. Format in Markdown."
                                    diagnosisResult = inferenceModel.generateResponse(prompt)
                                } catch (e: Exception) {
                                    diagnosisResult = "Error generating diagnosis: ${e.message}"
                                } finally {
                                    isGenerating = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Regenerate")
                    }
                }
            }
        }
    }

    // QR Code Dialog
    if (showQrDialog && diagnosisResult != null) {
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCode, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Diagnosis")
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Scan this QR code to view the diagnosis summary",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    val qrBitmap = remember(diagnosisResult) {
                        try {
                            val writer = QRCodeWriter()
                            // QR limit around 2-3k chars for large versions, but safe limit for legibility is lower (800-1000)
                            val text = if (diagnosisResult!!.length > 800) diagnosisResult!!.take(800) + "..." else diagnosisResult!!
                            val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512)
                            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
                            for (x in 0 until 512) {
                                for (y in 0 until 512) {
                                    bitmap.setPixel(x, y,
                                        if (bitMatrix.get(x, y)) android.graphics.Color.BLACK
                                        else android.graphics.Color.WHITE
                                    )
                                }
                            }
                            bitmap
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(256.dp)
                        )
                    } else {
                        Text("Unable to generate QR code")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQrDialog = false }) { Text("Close") }
            }
        )
    }
}
