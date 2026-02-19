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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("MedGemma", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select a backend to run the model on.", style = MaterialTheme.typography.bodyMedium)
                }
            }


            
            // Authentication Section
            Text(
                "Authentication Method",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Text(
                "Choose how to authenticate for model downloads:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                        Icons.Default.ArrowForward,
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
                Text("Continue")
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
