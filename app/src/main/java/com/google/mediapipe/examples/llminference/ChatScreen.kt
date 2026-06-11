package com.google.mediapipe.examples.llminference

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import com.google.mediapipe.examples.llminference.MarkdownText
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mediapipe.examples.llminference.settings.LocalModelFiles
import java.io.File

@Composable
internal fun ChatRoute(
    onClose: () -> Unit,
    patientId: Long? = null
) {
    val chatViewModel: ChatViewModel = viewModel(factory = ChatViewModel.getFactory(patientId))

    val context = LocalContext.current
    val inferenceModel = try { InferenceModel.getInstance(context) } catch (_: Exception) { null }
    val visionAvailable = inferenceModel?.isVisionAvailable ?: false

    // Model is initialized internally by ViewModel factory

    val uiState by chatViewModel.uiState.collectAsStateWithLifecycle()
    val textInputEnabled by chatViewModel.isTextInputEnabled.collectAsStateWithLifecycle()
    val thinkingEnabled by chatViewModel.thinkingEnabled.collectAsStateWithLifecycle()
    val isGenerating by chatViewModel.isGenerating.collectAsStateWithLifecycle()
    val patientContext by chatViewModel.patientContext.collectAsStateWithLifecycle()

    ChatScreen(
        uiState,
        textInputEnabled,
        visionAvailable = visionAvailable,
        thinkingEnabled = thinkingEnabled,
        isGenerating = isGenerating,
        patientContext = patientContext,
        onToggleThinking = { enabled ->
            chatViewModel.setThinkingEnabled(enabled)
        },
        onSendMessage = { message, images, uris ->
            chatViewModel.sendMessage(message, images, uris)
        },
        onStopGeneration = { chatViewModel.stopGeneration() },
        onClose = onClose
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: UiState,
    textInputEnabled: Boolean,
    visionAvailable: Boolean = true,
    thinkingEnabled: Boolean = false,
    isGenerating: Boolean = false,
    patientContext: PatientChatContext? = null,
    onToggleThinking: (Boolean) -> Unit = {},
    onSendMessage: (String, List<Bitmap>, List<Uri>) -> Unit,
    onStopGeneration: () -> Unit = {},
    onClose: () -> Unit
) {
    var userMessage by rememberSaveable { mutableStateOf("") }
    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val context = LocalContext.current
    val view = LocalView.current
    val bitmaps = imageUris.mapNotNull { it.toBitmap(context) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Warn when images picked without vision encoder
    LaunchedEffect(imageUris) {
        if (imageUris.isNotEmpty() && !visionAvailable) {
            snackbarHostState.showSnackbar(
                message = "No vision encoder found — images will be ignored. Add mmproj in Model Setup.",
                duration = SnackbarDuration.Short
            )
        } else if (imageUris.isNotEmpty() && visionAvailable) {
            snackbarHostState.showSnackbar(
                message = "Image attached — vision encoder will load when you send",
                duration = SnackbarDuration.Short
            )
        }
    }

    // Auto-scroll to latest message
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (patientContext != null) MaterialTheme.colorScheme.tertiaryContainer
                                    else MaterialTheme.colorScheme.primaryContainer
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (patientContext != null) Icons.Default.Person else Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (patientContext != null) MaterialTheme.colorScheme.onTertiaryContainer
                                       else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                patientContext?.let { "Patient: ${it.patientName}" } ?: "AI Assistant",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                when {
                                    isGenerating -> "Generating…"
                                    patientContext != null -> "${patientContext.entryCount} entries loaded"
                                    textInputEnabled -> "Online"
                                    else -> "Thinking…"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (textInputEnabled && !isGenerating)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Thinking toggle inline in the title row for reliable touch
                        val scope = rememberCoroutineScope()
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                val newValue = !thinkingEnabled
                                onToggleThinking(newValue)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = if (newValue) "Thinking enabled — model will reason step-by-step"
                                                  else "Thinking disabled — faster, direct responses",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                if (thinkingEnabled) Icons.Filled.Psychology else Icons.Outlined.Psychology,
                                contentDescription = if (thinkingEnabled) "Disable thinking" else "Enable thinking",
                                tint = if (thinkingEnabled) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onClose()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close_chat)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (LocalModelFiles.getInferenceTier(context) == LocalModelFiles.TIER_GEMINI_API) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Clinical data is sent to Google Gemini via your API key.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            // Messages list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                reverseLayout = true,
                state = listState,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(uiState.messages) { chat ->
                    ChatBubble(chat)
                }

                // Welcome message when empty
                if (uiState.messages.isEmpty()) {
                    item {
                        WelcomeMessage()
                    }
                }
            }

            // Input area
            MessageInput(
                textInputEnabled = textInputEnabled,
                isGenerating = isGenerating,
                userMessage = userMessage,
                images = bitmaps,
                imageUris = imageUris,
                onSendMessage = {
                    if (userMessage.isNotBlank() || bitmaps.isNotEmpty()) {
                        onSendMessage(userMessage, bitmaps, imageUris)
                        userMessage = ""
                        imageUris = emptyList()
                    }
                },
                onStopGeneration = onStopGeneration,
                onMessageChanged = { userMessage = it },
                onImagesChanged = { imageUris = it },
            )
        }
    }
}

@Composable
private fun WelcomeMessage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "How can I help?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Ask me about medical analysis, imaging, or patient data",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInput(
    textInputEnabled: Boolean,
    isGenerating: Boolean = false,
    userMessage: String,
    images: List<Bitmap>,
    imageUris: List<Uri>,
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit = {},
    onMessageChanged: (String) -> Unit,
    onImagesChanged: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // ── Attachment bottom-sheet state ──
    val sheetState = rememberModalBottomSheetState()
    var showAttachSheet by remember { mutableStateOf(false) }

    // ── Gallery picker ──
    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) onImagesChanged(uris) }

    // ── Camera capture ──
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            onImagesChanged(listOf(cameraImageUri!!))
        }
    }

    // ── Camera permission ──
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createCameraImageUri(context)
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        }
    }

    fun launchCamera() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            val uri = createCameraImageUri(context)
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ── Attachment bottom sheet ──
    if (showAttachSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachSheet = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    "Add Image",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                // Gallery option
                ListItem(
                    headlineContent = { Text("Choose from Gallery") },
                    leadingContent = {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null,
                             tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable {
                        showAttachSheet = false
                        galleryPicker.launch("image/*")
                    }
                )
                // Camera option
                ListItem(
                    headlineContent = { Text("Take Photo") },
                    leadingContent = {
                        Icon(Icons.Default.CameraAlt, contentDescription = null,
                             tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable {
                        showAttachSheet = false
                        launchCamera()
                    }
                )
            }
        }
    }

    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Image preview row
            AnimatedVisibility(visible = images.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(images) { bitmap ->
                        Box {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Attached image",
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            // Remove badge
                            FilledIconButton(
                                onClick = { onImagesChanged(emptyList()) },
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.TopEnd),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // Input row
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Attach image button — opens bottom sheet with gallery/camera choices
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        showAttachSheet = true
                    },
                    enabled = textInputEnabled && !isGenerating,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.AddPhotoAlternate,
                        contentDescription = stringResource(R.string.add_image),
                        modifier = Modifier.size(24.dp),
                        tint = if (textInputEnabled && !isGenerating)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Text field — always enabled so user can type next prompt while waiting
                OutlinedTextField(
                    value = userMessage,
                    onValueChange = onMessageChanged,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = if (isGenerating) ImeAction.Done else ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (!isGenerating && (userMessage.isNotBlank() || images.isNotEmpty())) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onSendMessage()
                            }
                        },
                        onDone = { /* dismiss keyboard while generating */ }
                    ),
                    placeholder = {
                        Text(
                            if (isGenerating) "Type your next message…"
                            else stringResource(R.string.chat_label),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = textInputEnabled,
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Send or Stop button
                if (isGenerating) {
                    // Stop button
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onStopGeneration()
                        },
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Stop generation",
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                } else {
                    val canSend = textInputEnabled && (userMessage.isNotBlank() || images.isNotEmpty())
                    IconButton(
                        onClick = {
                            if (canSend) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onSendMessage()
                            }
                        },
                        enabled = canSend,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (canSend) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.action_send),
                                modifier = Modifier.size(20.dp),
                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(chatMessage: ChatMessage) {
    val isUser = chatMessage.isFromUser

    val bubbleColor = when {
        isUser -> MaterialTheme.colorScheme.primary
        chatMessage.isThinking -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    val textColor = when {
        isUser -> MaterialTheme.colorScheme.onPrimary
        chatMessage.isThinking -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    val bubbleShape = if (isUser) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    // Thinking bubbles are collapsed by default
    var isThinkingExpanded by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
    ) {
        // Author label — hidden for thinking bubbles to keep them minimal
        if (!chatMessage.isThinking) {
            Text(
                text = if (chatMessage.isFromUser) stringResource(R.string.user_label)
                       else stringResource(R.string.model_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = if (!isUser) 8.dp else 0.dp,
                    end = if (isUser) 8.dp else 0.dp,
                    bottom = 2.dp
                )
            )
        }

        // If this is a thinking bubble, show a collapsed summary that can be expanded
        if (chatMessage.isThinking && !chatMessage.isLoading) {
            Surface(
                color = bubbleColor,
                shape = bubbleShape,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clickable { isThinkingExpanded = !isThinkingExpanded }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = textColor.copy(alpha = 0.7f)
                        )
                        Text(
                            text = if (isThinkingExpanded) "Thinking (tap to hide)" else "Thought process (tap to show)",
                            style = MaterialTheme.typography.labelMedium,
                            color = textColor.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            if (isThinkingExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isThinkingExpanded) "Collapse" else "Expand",
                            modifier = Modifier.size(18.dp),
                            tint = textColor.copy(alpha = 0.7f)
                        )
                    }
                    AnimatedVisibility(
                        visible = isThinkingExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = chatMessage.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        } else {
            // Normal message bubble (user, model response, or still-loading thinking)
            Surface(
                color = bubbleColor,
                shape = bubbleShape,
                tonalElevation = if (isUser) 0.dp else 1.dp,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                if (chatMessage.isLoading) {
                    // Typing indicator
                    TypingIndicator()
                } else {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (chatMessage.images.isNotEmpty()) {
                            chatMessage.images.forEach { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "",
                                    modifier = Modifier
                                        .padding(bottom = 8.dp)
                                        .heightIn(max = 200.dp)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                        if (isUser) {
                            Text(
                                text = chatMessage.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor
                            )
                        } else {
                            MarkdownText(
                                markdown = chatMessage.message,
                                textColor = textColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha)
                    )
            )
        }
    }
}

private fun Uri.toBitmap(context: Context): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, this)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, this)
        }
    } catch (e: Exception) {
        android.util.Log.e("ChatScreen", "Failed to load image: $this", e)
        null
    }
}

/** Create a temporary file URI for camera capture via FileProvider. */
private fun createCameraImageUri(context: Context): Uri {
    val imageFile = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        imageFile
    )
}
