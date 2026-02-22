package com.google.mediapipe.examples.llminference

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.llminference.settings.LocalModelFiles
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionRoute(
    onModelSelected: () -> Unit = {},
    onResumeChat: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── State ──
    var pickedModelPath by remember { mutableStateOf(LocalModelFiles.getModelPath(context)) }
    var pickedMmprojPath by remember { mutableStateOf(LocalModelFiles.getMmprojPath(context)) }
    var visionEnabled by remember { mutableStateOf(LocalModelFiles.isVisionEnabled(context)) }
    var pickerStatus by remember { mutableStateOf("") }
    var isPickingModel by remember { mutableStateOf(false) }
    var isPickingMmproj by remember { mutableStateOf(false) }
    var useGpu by remember { mutableStateOf(false) }

    // Check if the model file actually exists AND if model is already loaded in memory
    val modelFileExists = remember(pickedModelPath) {
        pickedModelPath.isNotBlank() && File(pickedModelPath).exists()
    }
    // Also check known folders for auto-discovered models
    val autoModelPath = remember {
        try { InferenceModel.modelPath(context).let { p -> if (File(p).exists()) p else "" } }
        catch (_: Exception) { "" }
    }
    val hasModel = modelFileExists || autoModelPath.isNotBlank()
    val effectiveModelPath = if (modelFileExists) pickedModelPath else autoModelPath
    val modelAlreadyLoaded = remember { InferenceModel.isLoaded() }

    val modelName = remember(effectiveModelPath) {
        if (effectiveModelPath.isBlank()) "No model selected" else File(effectiveModelPath).name
    }
    val mmprojName = remember(pickedMmprojPath) {
        if (pickedMmprojPath.isBlank()) "Not selected" else File(pickedMmprojPath).name
    }

    // ── File Pickers ──
    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            isPickingModel = true
            try {
                val path = withContext(Dispatchers.IO) {
                    val directPath = LocalModelFiles.resolveUriToFilePath(uri)
                    if (!directPath.isNullOrBlank() && File(directPath).exists()) {
                        directPath
                    } else {
                        LocalModelFiles.copyUriToInternalFile(context, uri)
                    }
                }
                LocalModelFiles.setModelPath(context, path)
                pickedModelPath = path
                pickerStatus = "Model ready: ${File(path).name}"
            } catch (e: Exception) {
                pickerStatus = "Failed to read model file: ${e.message ?: "Unknown error"}"
            } finally {
                isPickingModel = false
            }
        }
    }

    val mmprojPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            isPickingMmproj = true
            try {
                val path = withContext(Dispatchers.IO) {
                    val directPath = LocalModelFiles.resolveUriToFilePath(uri)
                    if (!directPath.isNullOrBlank() && File(directPath).exists()) {
                        directPath
                    } else {
                        LocalModelFiles.copyUriToInternalFile(context, uri)
                    }
                }
                LocalModelFiles.setMmprojPath(context, path)
                pickedMmprojPath = path
                pickerStatus = "Vision encoder ready: ${File(path).name}"
            } catch (e: Exception) {
                pickerStatus = "Failed to read vision encoder: ${e.message ?: "Unknown error"}"
            } finally {
                isPickingMmproj = false
            }
        }
    }

    val isImporting = isPickingModel || isPickingMmproj

    // ── UI ──
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Model Setup") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ═══════════════════════════════════════════
            // 1. RESUME CHAT (only when model already loaded)
            // ═══════════════════════════════════════════
            if (modelAlreadyLoaded) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Model is loaded and ready",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            modelName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onResumeChat() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Resume Chat")
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    "Or change model settings below:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ═══════════════════════════════════════════
            // 2. MODEL FILE SECTION
            // ═══════════════════════════════════════════
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("GGUF Model File", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (hasModel) {
                        // Model is available — show name + change/remove
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                modelName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    pickerStatus = "Selecting new model file..."
                                    modelPickerLauncher.launch(arrayOf("*/*"))
                                },
                                enabled = !isImporting,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isPickingModel) "Importing..." else "Change Model")
                            }
                            OutlinedButton(
                                onClick = {
                                    LocalModelFiles.clearModelPath(context)
                                    pickedModelPath = ""
                                    pickerStatus = "Model removed"
                                },
                                enabled = !isImporting && pickedModelPath.isNotBlank(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Remove")
                            }
                        }
                    } else {
                        // No model — show picker
                        Text(
                            "Pick a .gguf model from your phone, or place files in /storage/emulated/0/Download/medgemma",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                pickerStatus = "Selecting model file..."
                                modelPickerLauncher.launch(arrayOf("*/*"))
                            },
                            enabled = !isImporting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isPickingModel) "Importing model..." else "Pick GGUF Model")
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════
            // 3. VISION ENCODER (OPTIONAL)
            // ═══════════════════════════════════════════
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Vision Encoder (optional)", style = MaterialTheme.typography.titleMedium)
                        Switch(
                            checked = visionEnabled,
                            onCheckedChange = {
                                visionEnabled = it
                                LocalModelFiles.setVisionEnabled(context, it)
                            }
                        )
                    }
                    if (!visionEnabled) {
                        Text(
                            "Text-only mode. Enable to analyze images.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (visionEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (pickedMmprojPath.isNotBlank() && File(pickedMmprojPath).exists()) {
                            // Mmproj selected — show name + change/remove
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    mmprojName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        pickerStatus = "Selecting vision encoder..."
                                        mmprojPickerLauncher.launch(arrayOf("*/*"))
                                    },
                                    enabled = !isImporting,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (isPickingMmproj) "Importing..." else "Change Encoder")
                                }
                                OutlinedButton(
                                    onClick = {
                                        LocalModelFiles.clearMmprojPath(context)
                                        pickedMmprojPath = ""
                                        visionEnabled = false
                                        LocalModelFiles.setVisionEnabled(context, false)
                                        pickerStatus = "Vision encoder removed"
                                    },
                                    enabled = !isImporting,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Remove")
                                }
                            }
                        } else {
                            // No mmproj — show picker
                            Text(
                                "Pick the mmproj file for image analysis",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    pickerStatus = "Selecting vision encoder..."
                                    mmprojPickerLauncher.launch(arrayOf("*/*"))
                                },
                                enabled = !isImporting,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isPickingMmproj) "Importing..." else "Pick Vision Encoder (mmproj)")
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════
            // 4. GPU TOGGLE
            // ═══════════════════════════════════════════
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Use GPU backend", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = useGpu, onCheckedChange = { useGpu = it })
                }
            }

            // ═══════════════════════════════════════════
            // 5. STATUS
            // ═══════════════════════════════════════════
            if (pickerStatus.isNotBlank()) {
                Text(
                    pickerStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ═══════════════════════════════════════════
            // 6. MAIN ACTION BUTTON
            // ═══════════════════════════════════════════
            Button(
                onClick = {
                    if (isImporting) {
                        pickerStatus = "Please wait for file import to finish."
                        return@Button
                    }
                    InferenceModel.model = if (useGpu) Model.GEMMA_3_1B_IT_GPU else Model.GEMMA3_1B_IT_CPU
                    onModelSelected()
                },
                enabled = hasModel && !isImporting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (modelAlreadyLoaded) "Reload Model & Chat" else "Load Model & Start Chat",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            if (!hasModel) {
                Text(
                    "Select a model file above to enable this button",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
