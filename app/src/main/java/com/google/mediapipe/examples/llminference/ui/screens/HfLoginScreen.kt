package com.google.mediapipe.examples.llminference.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.llminference.HfModelRepository
import com.google.mediapipe.examples.llminference.network.HfApiClient
import com.google.mediapipe.examples.llminference.settings.TokenManager
import kotlinx.coroutines.launch

/**
 * Dedicated setup screen for Hugging Face authentication.
 *
 *  1. Step-by-step instructions (create token, accept license)
 *  2. Secure token entry
 *  3. Live verification (whoami + model access)
 *  4. Stored encrypted via TokenManager
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HfLoginScreen(
    onBack: () -> Unit,
    onAuthenticated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val tokenManager = remember { TokenManager(context) }

    // State
    var token by remember { mutableStateOf(tokenManager.getToken() ?: "") }
    var showToken by remember { mutableStateOf(false) }

    // Verification state
    var isVerifying by remember { mutableStateOf(false) }
    var verifiedUsername by remember { mutableStateOf(tokenManager.getUsername()) }
    var verificationError by remember { mutableStateOf<String?>(null) }
    var modelAccessGranted by remember { mutableStateOf<Boolean?>(null) }
    var modelAccessMessage by remember { mutableStateOf<String?>(null) }

    // (verification state is derived from verifiedUsername / verificationError below)

    fun verifyAndSave() {
        if (token.isBlank()) return
        isVerifying = true
        verificationError = null
        modelAccessGranted = null
        modelAccessMessage = null
        focusManager.clearFocus()

        scope.launch {
            // Step 1: Verify token
            when (val result = HfApiClient.verifyToken(token.trim())) {
                is HfApiClient.TokenResult.Valid -> {
                    // Save token + user info
                    tokenManager.saveToken(token.trim())
                    tokenManager.saveVerifiedUser(result.username, result.displayName)
                    verifiedUsername = result.username
                    verificationError = null

                    // Step 2: Check gated model access
                    when (val access = HfApiClient.checkModelAccess(
                        token.trim(), HfModelRepository.REPO_ID
                    )) {
                        is HfApiClient.ModelAccessResult.Granted -> {
                            modelAccessGranted = true
                            modelAccessMessage = "Access to ${HfModelRepository.REPO_ID} confirmed"
                        }
                        is HfApiClient.ModelAccessResult.LicenseRequired -> {
                            modelAccessGranted = false
                            modelAccessMessage =
                                "You need to accept the model license first. Tap the link below, accept, then verify again."
                        }
                        is HfApiClient.ModelAccessResult.Unauthorized -> {
                            modelAccessGranted = false
                            modelAccessMessage = access.message
                        }
                        is HfApiClient.ModelAccessResult.Error -> {
                            // Token is valid, just can't check model — still okay
                            modelAccessGranted = null
                            modelAccessMessage = "Could not check model access: ${access.message}"
                        }
                    }
                }
                is HfApiClient.TokenResult.InvalidToken -> {
                    verifiedUsername = null
                    verificationError = result.message
                }
                is HfApiClient.TokenResult.NetworkError -> {
                    verifiedUsername = null
                    verificationError = result.message
                }
            }
            isVerifying = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hugging Face Login") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ═══════════════════════════════════════════
            // HEADER
            // ═══════════════════════════════════════════
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Authentication Required",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "MedGemma is a gated model. You need a Hugging Face account and a personal access token to download it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ═══════════════════════════════════════════
            // STEP-BY-STEP INSTRUCTIONS
            // ═══════════════════════════════════════════
            Card {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Setup Instructions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Step 1
                    InstructionStep(
                        number = 1,
                        title = "Create a Hugging Face Token",
                        description = "Go to your Hugging Face settings and create a new access token. A \"Read\" token is sufficient — it only needs permission to download model weights.",
                        linkText = "Open Token Settings",
                        linkUrl = "https://huggingface.co/settings/tokens"
                    )

                    HorizontalDivider()

                    // Step 2
                    InstructionStep(
                        number = 2,
                        title = "Accept the Model License",
                        description = "Visit the MedGemma model page on Hugging Face and accept the gated repository agreement. This is a one-time step required by Google/DeepMind.",
                        linkText = "Open Model Page",
                        linkUrl = HfModelRepository.REPO_URL
                    )

                    HorizontalDivider()

                    // Step 3
                    InstructionStep(
                        number = 3,
                        title = "Paste Your Token Below",
                        description = "Copy the token (starts with hf_...) and paste it in the field below. It will be stored securely using AES-256 encryption on your device.",
                        linkText = null,
                        linkUrl = null
                    )
                }
            }

            // ═══════════════════════════════════════════
            // TOKEN INPUT
            // ═══════════════════════════════════════════
            Card {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Your Access Token",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = token,
                        onValueChange = {
                            token = it
                            // Reset verification state when token changes
                            if (verifiedUsername != null || verificationError != null) {
                                verifiedUsername = null
                                verificationError = null
                                modelAccessGranted = null
                                modelAccessMessage = null
                            }
                        },
                        label = { Text("Hugging Face Token") },
                        placeholder = { Text("hf_xxxxxxxxxxxxxxxxxxxx") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showToken)
                            VisualTransformation.None
                        else
                            PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { verifyAndSave() }
                        ),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    if (showToken) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                    contentDescription = if (showToken) "Hide" else "Show"
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Key, contentDescription = null)
                        },
                        isError = verificationError != null
                    )

                    // Verify + Save button
                    Button(
                        onClick = { verifyAndSave() },
                        enabled = token.isNotBlank() && !isVerifying,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verifying…")
                        } else {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verify & Save Token")
                        }
                    }

                    // Clear button (only if token saved)
                    if (tokenManager.hasToken()) {
                        OutlinedButton(
                            onClick = {
                                tokenManager.clearToken()
                                token = ""
                                verifiedUsername = null
                                verificationError = null
                                modelAccessGranted = null
                                modelAccessMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Remove Saved Token")
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════
            // VERIFICATION STATUS
            // ═══════════════════════════════════════════
            AnimatedVisibility(
                visible = verifiedUsername != null || verificationError != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (verifiedUsername != null) {
                    // Success
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "Token Verified",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        "Logged in as @$verifiedUsername",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            // Model access status
                            when (modelAccessGranted) {
                                true -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Lock,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            modelAccessMessage ?: "Model access confirmed",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                false -> {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                modelAccessMessage ?: "Model access denied",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            val ctx = LocalContext.current
                                            OutlinedButton(
                                                onClick = {
                                                    ctx.startActivity(
                                                        Intent(
                                                            Intent.ACTION_VIEW,
                                                            Uri.parse(HfModelRepository.REPO_URL)
                                                        )
                                                    )
                                                }
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.OpenInNew,
                                                    null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Accept License on Hugging Face")
                                            }
                                        }
                                    }
                                }
                                null -> {
                                    if (modelAccessMessage != null) {
                                        Text(
                                            modelAccessMessage!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (verificationError != null) {
                    // Error
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                verificationError!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════
            // CONTINUE BUTTON
            // ═══════════════════════════════════════════
            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onAuthenticated,
                enabled = tokenManager.hasToken(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (modelAccessGranted == true) "Continue to Model Setup"
                    else "Continue (token saved)",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            if (!tokenManager.hasToken()) {
                Text(
                    "Enter and verify your token above to continue",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Security note
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Shield,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Your token is encrypted with AES-256 and stored in the Android Keystore. It never leaves your device and is only used to authenticate downloads from Hugging Face.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ─── Helper composables ───────────────────────────────────────────────

@Composable
private fun InstructionStep(
    number: Int,
    title: String,
    description: String,
    linkText: String?,
    linkUrl: String?
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Number badge
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "$number",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (linkText != null && linkUrl != null) {
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl))
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(linkText, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
