package com.google.mediapipe.examples.llminference

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import com.google.mediapipe.examples.llminference.settings.TokenManager

private class MissingAccessTokenException :
    Exception("Please try again after sign in")

private class UnauthorizedAccessException :
    Exception("Access denied. Please try again and grant the necessary permissions.")

private class ForbiddenAccessException :
    Exception("Access to the model is forbidden. Please ensure you have accepted the model's license terms.")

private class MissingUrlException(message: String) :
    Exception(message)

private const val UNAUTHORIZED_CODE = 401
private const val FORBIDDEN_CODE = 403

@Composable
internal fun LoadingRoute(
    onModelLoaded: () -> Unit = { },
    onGoBack: () -> Unit = {}
) {
    val context = LocalContext.current.applicationContext
    var errorMessage by remember { mutableStateOf("") }

    var progress by remember { mutableStateOf(0) }
    var isDownloading by remember { mutableStateOf(false) }
    var job: Job? by remember { mutableStateOf(null) }
    val client = remember {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)   // 0 = no timeout for large downloads
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    if (errorMessage != "") {
        ErrorMessage(errorMessage, onGoBack)
    } else if (isDownloading) {
        DownloadIndicator(progress) {
            job?.cancel()
            isDownloading = false

            CoroutineScope(Dispatchers.Main).launch {
                deleteDownloadedFile(context)
                withContext(Dispatchers.Main) {
                    errorMessage = "Download Cancelled"
                }
            }
        }
    } else {
        LoadingIndicator()
    }

    LaunchedEffect(Unit) {
        job = launch(Dispatchers.IO) {
            try {
                if (!InferenceModel.modelExists(context)) {
                    if (InferenceModel.model.url.isEmpty()) {
                        throw MissingUrlException("Please manually copy the model to ${InferenceModel.model.path}")
                    }
                    isDownloading = true
                    
                    val downloads = mutableListOf<Pair<String, String>>()
                    // Text Model
                    if (InferenceModel.model.url.isNotEmpty()) {
                        downloads.add(InferenceModel.model.url to InferenceModel.modelPathFromUrl(context))
                    }
                    // Vision Model
                    if (InferenceModel.model.visionUrl.isNotEmpty()) {
                        downloads.add(InferenceModel.model.visionUrl to InferenceModel.visionModelPath(context))
                    }
                    // Projector Model
                    if (InferenceModel.model.projectorUrl.isNotEmpty()) {
                        downloads.add(InferenceModel.model.projectorUrl to InferenceModel.projectorModelPath(context))
                    }

                    downloadModels(context, downloads, InferenceModel.model.needsAuth, client) { newProgress ->
                        progress = newProgress
                    }
                }

                InferenceModel.resetInstance(context)
                // Notify the UI that the model has finished loading
                withContext(Dispatchers.Main) {
                    onModelLoaded()
                }
            } catch (e: MissingAccessTokenException) {
                errorMessage = e.localizedMessage ?: "Unknown Error"
            } catch (e: MissingUrlException) {
                errorMessage = e.localizedMessage ?: "Unknown Error"
            } catch (e: UnauthorizedAccessException) {
                errorMessage = e.localizedMessage ?: "Unknown Error"
            } catch (e: ForbiddenAccessException) {
                errorMessage = e.localizedMessage ?: "Unknown Error"
            } catch (e: ModelSessionCreateFailException) {
                errorMessage = e.localizedMessage ?: "Unknown Error"
            } catch (e: ModelLoadFailException) {
                errorMessage = e.localizedMessage ?: "Unknown Error"
                // Remove invalid model file - tricky with multiple files, user can clear data
            } catch (e: Exception) {
                val error = e.localizedMessage ?: "Unknown Error"
                errorMessage =
                    "${error}, please manually copy the model to ${InferenceModel.model.path}"
            } finally {
                isDownloading = false
            }
        }
    }
}

internal fun downloadModels(
    context: Context,
    downloads: List<Pair<String, String>>,
    needsAuth: Boolean,
    client: OkHttpClient,
    triggerAuth: Boolean = true,
    onProgressUpdate: (Int) -> Unit
) {
    val totalFiles = downloads.size
    if (totalFiles == 0) return

    downloads.forEachIndexed { index, (url, path) ->
        val outputTarget = File(path)
        if (outputTarget.exists()) {
            // Skip if already exists? Or separate check? 
            // Better to assume if we are here, we need to download (or re-download)
            // But if checking integrity is hard, we overwrite.
        }

        downloadSingleFile(
            context, 
            url, 
            outputTarget, 
            needsAuth, 
            client, 
            triggerAuth
        ) { fileProgress ->
            // scale progress: current file contribution + previous files
            // Simple approach: each file is equal weight
            val totalProgress = ((index * 100) + fileProgress) / totalFiles
            onProgressUpdate(totalProgress)
        }
    }
}

internal fun downloadSingleFile(
    context: Context,
    url: String,
    outputFile: File,
    needsAuth: Boolean,
    client: OkHttpClient,
    triggerAuth: Boolean = true,
    onProgressUpdate: (Int) -> Unit
) {
    val requestBuilder = Request.Builder().url(url)

    if (needsAuth) {
        val tokenManager = TokenManager(context)
        val savedToken = tokenManager.getToken()

        val accessToken = if (!savedToken.isNullOrBlank()) {
            // Use saved token
            savedToken
        } else {
            // Fall back to OAuth
            SecureStorage.getToken(context)
        }

        if (accessToken.isNullOrBlank()) {
            if (triggerAuth) {
                // Trigger LoginActivity if no access token is found
                val intent = Intent(context, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
            throw MissingAccessTokenException()
        } else {
            requestBuilder.addHeader("Authorization", "Bearer $accessToken")
        }
    }

    val response = client.newCall(requestBuilder.build()).execute()
    if (!response.isSuccessful) {
        if (response.code == UNAUTHORIZED_CODE) {
            val accessToken = SecureStorage.getToken(context)
            if (!accessToken.isNullOrEmpty()) {
                // Remove invalid or expired token
                SecureStorage.removeToken(context)
            }
            throw UnauthorizedAccessException()
        } else if (response.code == FORBIDDEN_CODE) {
            throw ForbiddenAccessException()
        }
        throw Exception("Download failed: ${response.code} for $url")
    }

    response.body?.byteStream()?.use { inputStream ->
        FileOutputStream(outputFile).use { outputStream ->
            val buffer = ByteArray(4096)
            var bytesRead: Int
            var totalBytesRead = 0L
            val contentLength = response.body?.contentLength() ?: -1

            var lastProgress = -1
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                val progress = if (contentLength > 0) {
                    (totalBytesRead * 100 / contentLength).toInt()
                } else {
                    -1
                }
                if (progress != lastProgress) {
                    lastProgress = progress
                    onProgressUpdate(progress)
                }
            }
            outputStream.flush()
        }
    }
}

private suspend fun deleteDownloadedFile(context: Context) {
    withContext(Dispatchers.IO) {
        val outputFile = File(InferenceModel.modelPathFromUrl(context))
        if (outputFile.exists()) {
            outputFile.delete()
        }
    }
}

@Composable
fun DownloadIndicator(progress: Int, onCancel: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Icon(
            Icons.Default.CloudDownload,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Downloading Model",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This may take a few minutes depending on your connection",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Progress card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Progress",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$progress%",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(onClick = onCancel) {
            Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cancel Download")
        }
    }
}

@Composable
fun LoadingIndicator() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.loading_model),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Preparing the AI model for analysis…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            strokeWidth = 4.dp
        )
    }
}

@Composable
fun ErrorMessage(
    errorMessage: String,
    onGoBack: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onGoBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Go Back")
        }
    }
}
