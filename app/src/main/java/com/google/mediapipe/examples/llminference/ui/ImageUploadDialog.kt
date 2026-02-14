package com.google.mediapipe.examples.llminference.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Image upload dialog with camera, gallery, and file picker options
 */
@Composable
fun ImageUploadDialog(
    onDismiss: () -> Unit,
    onImageSelected: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var showSourceDialog by remember { mutableStateOf(true) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && capturedImageUri != null) {
            try {
                val bitmap = BitmapFactory.decodeStream(
                    context.contentResolver.openInputStream(capturedImageUri!!)
                )
                bitmap?.let { onImageSelected(it) }
                onDismiss()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = BitmapFactory.decodeStream(
                    context.contentResolver.openInputStream(it)
                )
                bitmap?.let { bmp -> onImageSelected(bmp) }
                onDismiss()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = BitmapFactory.decodeStream(
                    context.contentResolver.openInputStream(it)
                )
                bitmap?.let { bmp -> onImageSelected(bmp) }
                onDismiss()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Select Image Source") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Camera option
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Create temp file for camera
                                val photoFile = File(
                                    context.cacheDir,
                                    "temp_photo_${System.currentTimeMillis()}.jpg"
                                )
                                capturedImageUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    photoFile
                                )
                                cameraLauncher.launch(capturedImageUri!!)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                "Take Photo",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Gallery option
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                galleryLauncher.launch("image/*")
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                "Choose from Gallery",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    // File picker option
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                filePickerLauncher.launch(arrayOf("image/*"))
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                "Browse Files",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Composable to display a list of selected images with delete option
 */
@Composable
fun SelectedImagesRow(
    images: List<Bitmap>,
    onDeleteImage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (images.isNotEmpty()) {
        LazyRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(images.size) { index ->
                Box {
                    Card(
                        modifier = Modifier.size(100.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Image(
                            bitmap = images[index].asImageBitmap(),
                            contentDescription = "Selected image ${index + 1}",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    // Delete button
                    IconButton(
                        onClick = { onDeleteImage(index) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.error
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
