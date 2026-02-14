package com.google.mediapipe.examples.llminference.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Settings dialog for managing Hugging Face authentication
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenSettingsDialog(
    currentToken: String?,
    onSaveToken: (String) -> Unit,
    onClearToken: () -> Unit,
    onDismiss: () -> Unit
) {
    var token by remember { mutableStateOf(currentToken ?: "") }
    var showToken by remember { mutableStateOf(false) }
    var showConfirmClear by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Hugging Face Token")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Add your Hugging Face token to download models without OAuth login.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("HF Token") },
                    placeholder = { Text("hf_xxxxxxxxxxxxx") },
                    visualTransformation = if (showToken) 
                        VisualTransformation.None 
                    else 
                        PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                if (showToken) Icons.Default.Info 
                                else Icons.Default.MoreVert,
                                contentDescription = if (showToken) "Hide token" else "Show token"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (currentToken != null) {
                    OutlinedButton(
                        onClick = { showConfirmClear = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Remove Token")
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "How to get your token:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "1. Go to huggingface.co/settings/tokens\n2. Create a new token\n3. Copy and paste it here",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (token.isNotBlank()) {
                        onSaveToken(token.trim())
                        onDismiss()
                    }
                },
                enabled = token.isNotBlank() && token.trim() != currentToken
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    // Confirmation dialog for clearing token
    if (showConfirmClear) {
        AlertDialog(
            onDismissRequest = { showConfirmClear = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Remove Token?") },
            text = { Text("This will revert to OAuth login for model downloads.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearToken()
                        showConfirmClear = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClear = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Compact token indicator button for toolbar
 */
@Composable
fun TokenIndicatorButton(
    hasToken: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.Default.Lock,
            contentDescription = "Manage HF Token",
            tint = if (hasToken) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
