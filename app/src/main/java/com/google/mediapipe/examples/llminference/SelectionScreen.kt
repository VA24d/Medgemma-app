package com.google.mediapipe.examples.llminference

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.llminference.settings.LocalModelFiles
import com.google.mediapipe.examples.llminference.settings.TokenManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionRoute(
    onModelSelected: () -> Unit = {},
    onResumeChat: () -> Unit = {},
    onSetupToken: () -> Unit = {},
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

    // ── HF Token State (read-only here — editing happens in HfLoginScreen) ──
    val tokenManager = remember { TokenManager(context) }
    var hfToken by remember { mutableStateOf(tokenManager.getToken() ?: "") }
    val isTokenSaved = hfToken.isNotBlank()

    // ── HF Download State ──
    var selectedHfModel by remember { mutableStateOf<HfGgufFile?>(null) }
    var downloadMmproj by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var downloadFileName by remember { mutableStateOf("") }
    var downloadError by remember { mutableStateOf("") }

    // ── Tab State (0 = Download from HF, 1 = Load Local) ──
    var selectedTab by remember { mutableIntStateOf(0) }

    // Check model file exists AND if model already loaded
    val modelFileExists = remember(pickedModelPath) {
        pickedModelPath.isNotBlank() && File(pickedModelPath).exists()
    }
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

    // ── Scan for locally available GGUF files ──
    val localGgufFiles = remember {
        val dirs = listOfNotNull(
            "/storage/emulated/0/Download/medgemma",
            "/storage/emulated/0/Download/MedGemma",
            "/storage/emulated/0/Download",
            context.filesDir.absolutePath,
            context.getExternalFilesDir(null)?.absolutePath
        )
        val files = mutableListOf<File>()
        dirs.forEach { dir ->
            val d = File(dir)
            if (d.exists() && d.isDirectory) {
                d.listFiles()?.filter { it.name.endsWith(".gguf") }?.let { files.addAll(it) }
            }
        }
        files.distinctBy { it.canonicalPath }
    }

    // ── Auto-select models found in app storage ──
    LaunchedEffect(Unit) {
        val extDir = context.getExternalFilesDir(null)
        val currentModelValid = pickedModelPath.isNotBlank() && File(pickedModelPath).exists()
        val currentMmprojValid = pickedMmprojPath.isNotBlank() && File(pickedMmprojPath).exists()

        // Only auto-select from getExternalFilesDir (safe, accessible); skip Download/ paths
        val safeFiles = localGgufFiles.filter { file ->
            file.absolutePath.startsWith(context.filesDir.absolutePath) ||
            (extDir != null && file.absolutePath.startsWith(extDir.absolutePath))
        }

        if (!currentModelValid) {
            safeFiles.firstOrNull { !it.name.contains("mmproj", ignoreCase = true) }?.let { file ->
                LocalModelFiles.setModelPath(context, file.absolutePath)
                pickedModelPath = file.absolutePath
                pickerStatus = "Auto-selected: ${file.name}"
            }
        }
        if (!currentMmprojValid) {
            safeFiles.firstOrNull { it.name.contains("mmproj", ignoreCase = true) }?.let { file ->
                LocalModelFiles.setMmprojPath(context, file.absolutePath)
                pickedMmprojPath = file.absolutePath
                visionEnabled = true
                LocalModelFiles.setVisionEnabled(context, true)
            }
        }
        // Switch to Local tab if we have safe files ready
        if (safeFiles.isNotEmpty()) {
            selectedTab = 1
        }
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
                    if (!directPath.isNullOrBlank() && File(directPath).exists() && File(directPath).canRead()) {
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
                    if (!directPath.isNullOrBlank() && File(directPath).exists() && File(directPath).canRead()) {
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

    val isImporting = isPickingModel || isPickingMmproj || isDownloading

    // ── Download helper (uses unified HfApiClient) ──
    fun downloadHfFile(hfFile: HfGgufFile, isMmproj: Boolean) {
        if (hfToken.isBlank()) {
            downloadError = "No token configured. Tap the auth banner above to set up your Hugging Face token."
            return
        }
        scope.launch {
            isDownloading = true
            downloadProgress = 0
            downloadFileName = hfFile.fileName
            downloadError = ""
            try {
                val result = com.google.mediapipe.examples.llminference.network.HfApiClient.downloadFile(
                    url = hfFile.url,
                    token = hfToken,
                    outputDir = context.filesDir,
                    fileName = hfFile.fileName,
                    onProgress = { downloadProgress = it }
                )
                when (result) {
                    is com.google.mediapipe.examples.llminference.network.HfApiClient.DownloadResult.Success -> {
                        if (isMmproj) {
                            LocalModelFiles.setMmprojPath(context, result.file.absolutePath)
                            pickedMmprojPath = result.file.absolutePath
                            visionEnabled = true
                            LocalModelFiles.setVisionEnabled(context, true)
                            pickerStatus = "Vision encoder downloaded: ${hfFile.fileName}"
                        } else {
                            LocalModelFiles.setModelPath(context, result.file.absolutePath)
                            pickedModelPath = result.file.absolutePath
                            pickerStatus = "Model downloaded: ${hfFile.fileName}"
                        }
                    }
                    is com.google.mediapipe.examples.llminference.network.HfApiClient.DownloadResult.Unauthorized -> {
                        downloadError = result.message
                    }
                    is com.google.mediapipe.examples.llminference.network.HfApiClient.DownloadResult.Forbidden -> {
                        downloadError = "${result.message}\nVisit ${HfModelRepository.REPO_URL} to accept the license."
                    }
                    is com.google.mediapipe.examples.llminference.network.HfApiClient.DownloadResult.Error -> {
                        downloadError = result.message
                    }
                }
            } catch (e: Exception) {
                downloadError = e.message ?: "Download failed"
            } finally {
                isDownloading = false
                downloadProgress = 0
                downloadFileName = ""
            }
        }
    }

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
            // 2. AUTHENTICATION STATUS BANNER
            // ═══════════════════════════════════════════
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isTokenSaved)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .clickable { onSetupToken() }
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        if (isTokenSaved) Icons.Default.CheckCircle else Icons.Default.Key,
                        contentDescription = null,
                        tint = if (isTokenSaved)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isTokenSaved) {
                                val username = tokenManager.getUsername()
                                if (username != null) "Logged in as @$username"
                                else "Hugging Face Token Saved"
                            } else {
                                "Hugging Face Login Required"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isTokenSaved)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = if (isTokenSaved) "Tap to manage token"
                            else "Tap to set up your access token",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isTokenSaved)
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = if (isTokenSaved)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f)
                    )
                }
            }

            // ═══════════════════════════════════════════
            // 3. TABS: Download from HF / Load Local
            // ═══════════════════════════════════════════
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(18.dp))
                        Text("Pull from HF")
                    }
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                        Text("Load Local")
                    }
                }
            }

            if (selectedTab == 0) {
                // ═════════════════════════════════════
                // TAB 0: Download from HuggingFace
                // ═════════════════════════════════════

                // Download progress indicator
                if (isDownloading) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Downloading: $downloadFileName",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "$downloadProgress%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (downloadError.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            downloadError,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Model quantization selection
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Select GGUF Quantization",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "From ${HfModelRepository.REPO_ID}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        HfModelRepository.availableModels.forEach { hfFile ->
                            val isSelected = selectedHfModel?.fileName == hfFile.fileName
                            val isAlreadyDownloaded = File(context.filesDir, hfFile.fileName).exists()

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        selectedHfModel = if (isSelected) null else hfFile
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                    isAlreadyDownloaded -> MaterialTheme.colorScheme.surfaceVariant
                                    else -> MaterialTheme.colorScheme.surface
                                },
                                tonalElevation = if (isSelected) 4.dp else 1.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            hfFile.displayName,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            hfFile.sizeLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isAlreadyDownloaded) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Downloaded",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Download Model Button
                if (selectedHfModel != null) {
                    val alreadyExists = File(context.filesDir, selectedHfModel!!.fileName).exists()
                    Button(
                        onClick = {
                            if (alreadyExists) {
                                // Use existing downloaded file
                                val path = File(context.filesDir, selectedHfModel!!.fileName).absolutePath
                                LocalModelFiles.setModelPath(context, path)
                                pickedModelPath = path
                                pickerStatus = "Using existing: ${selectedHfModel!!.fileName}"
                            } else {
                                downloadHfFile(selectedHfModel!!, isMmproj = false)
                            }
                        },
                        enabled = !isDownloading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            if (alreadyExists) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                            null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (alreadyExists) "Use ${selectedHfModel!!.displayName}" else "Download ${selectedHfModel!!.displayName}"
                        )
                    }
                }

                // Vision encoder download
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Vision Encoder", style = MaterialTheme.typography.titleMedium)
                            Switch(
                                checked = downloadMmproj || visionEnabled,
                                onCheckedChange = {
                                    downloadMmproj = it
                                    visionEnabled = it
                                    LocalModelFiles.setVisionEnabled(context, it)
                                }
                            )
                        }
                        val mmprojFile = File(context.filesDir, HfModelRepository.visionEncoder.fileName)
                        val mmprojExists = mmprojFile.exists() || (pickedMmprojPath.isNotBlank() && File(pickedMmprojPath).exists())

                        if (mmprojExists) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Vision encoder available",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else if (downloadMmproj || visionEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    downloadHfFile(HfModelRepository.visionEncoder, isMmproj = true)
                                },
                                enabled = !isDownloading && hfToken.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download Vision Encoder (${HfModelRepository.visionEncoder.sizeLabel})")
                            }
                        }
                    }
                }

            } else {
                // ═════════════════════════════════════
                // TAB 1: Load Local Models
                // ═════════════════════════════════════

                // Auto-discovered local GGUF files
                if (localGgufFiles.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Found on Device",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "GGUF files found in Download/medgemma & app storage",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            localGgufFiles.forEach { file ->
                                val isCurrentModel = file.absolutePath == effectiveModelPath
                                val isMmproj = file.name.contains("mmproj", ignoreCase = true)
                                val sizeMb = file.length() / (1024 * 1024)

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clickable {
                                            scope.launch {
                                                val extDir = context.getExternalFilesDir(null)
                                                val isDirectlyAccessible =
                                                    file.absolutePath.startsWith(context.filesDir.absolutePath) ||
                                                    (extDir != null && file.absolutePath.startsWith(extDir.absolutePath))
                                                val finalPath = if (isDirectlyAccessible) {
                                                    file.absolutePath
                                                } else {
                                                    pickerStatus = "Copying ${file.name} to app storage…"
                                                    withContext(Dispatchers.IO) {
                                                        val dest = File(context.filesDir, file.name)
                                                        file.copyTo(dest, overwrite = true)
                                                        dest.absolutePath
                                                    }
                                                }
                                                if (isMmproj) {
                                                    LocalModelFiles.setMmprojPath(context, finalPath)
                                                    pickedMmprojPath = finalPath
                                                    visionEnabled = true
                                                    LocalModelFiles.setVisionEnabled(context, true)
                                                    pickerStatus = "Vision encoder set: ${file.name}"
                                                } else {
                                                    LocalModelFiles.setModelPath(context, finalPath)
                                                    pickedModelPath = finalPath
                                                    pickerStatus = "Model set: ${file.name}"
                                                }
                                            }
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isCurrentModel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    tonalElevation = if (isCurrentModel) 4.dp else 1.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                file.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "${if (isMmproj) "Vision Encoder" else "Model"} · ${sizeMb} MB",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isCurrentModel) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Manual file pickers
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Pick from Storage", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))

                        if (hasModel) {
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
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                            Text(
                                "Pick a .gguf model from your phone, or push files to /storage/emulated/0/Download/medgemma",
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

                // Vision encoder loader
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
            }

            // ═══════════════════════════════════════════
            // GPU TOGGLE
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
            // STATUS
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
            // MAIN ACTION BUTTON
            // ═══════════════════════════════════════════
            Button(
                onClick = {
                    if (isImporting) {
                        pickerStatus = "Please wait for file import to finish."
                        return@Button
                    }
                    InferenceModel.model = if (useGpu) Model.MEDGEMMA_4B_IT_GPU else Model.MEDGEMMA_4B_IT_CPU
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
                    "Download or select a model above to enable this button",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
