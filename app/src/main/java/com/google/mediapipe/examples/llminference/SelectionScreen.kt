package com.google.mediapipe.examples.llminference

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
internal fun SelectionRoute(
    onModelSelected: () -> Unit = {},
) {
    val context = LocalContext.current
    val tokenManager = remember { com.google.mediapipe.examples.llminference.settings.TokenManager(context) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var hasToken by remember { mutableStateOf(tokenManager.hasToken()) }

    // Check model existence
    val modelExists = remember { InferenceModel.modelExists(context.applicationContext) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LLM Inference") }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            // Model info card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("MedGemma 4B (GGUF)", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Quantized Q4_K_M · llama.cpp inference",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Model status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (modelExists)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (modelExists) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = if (modelExists)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (modelExists) "Model Ready" else "Model Not Found",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (modelExists)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                        if (!modelExists) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Push via ADB or tap Continue to download",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "adb push <model>.gguf /data/local/tmp/medgemma/",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Authentication Section
            Text(
                "Authentication (for downloads)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            // Token Authentication Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showTokenDialog = true },
                colors = CardDefaults.cardColors(
                    containerColor = if (hasToken) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                                tint = if (hasToken) 
                                    MaterialTheme.colorScheme.onPrimaryContainer 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Use HF Token",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (hasToken) 
                                    MaterialTheme.colorScheme.onPrimaryContainer 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (hasToken) "✓ Token saved - Ready to use" else "Tap to add your token",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasToken) 
                                MaterialTheme.colorScheme.onPrimaryContainer 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = if (hasToken) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Text(
                "OR",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // OAuth Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            "Browser OAuth Login",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Will open browser to sign in",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val licenseUrl = Model.MEDGEMMA_4B.licenseUrl
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(licenseUrl))
                context.startActivity(intent)
            }) {
                Text("View model license")
            }

            Button(
                onClick = {
                    InferenceModel.model = Model.MEDGEMMA_4B
                    onModelSelected()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (modelExists) "Continue" else "Download & Continue")
            }
        }
    }
    
    // Token settings dialog
    if (showTokenDialog) {
        com.google.mediapipe.examples.llminference.ui.TokenSettingsDialog(
            currentToken = tokenManager.getToken(),
            onSaveToken = { token ->
                tokenManager.saveToken(token)
                hasToken = true
            },
            onClearToken = {
                tokenManager.clearToken()
                hasToken = false
            },
            onDismiss = { showTokenDialog = false }
        )
    }
}
