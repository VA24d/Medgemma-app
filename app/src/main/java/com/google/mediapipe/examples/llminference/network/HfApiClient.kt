package com.google.mediapipe.examples.llminference.network

import android.content.Context
import android.util.Log
import com.google.mediapipe.examples.llminference.settings.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Unified client for all Hugging Face Hub API operations:
 *  - Token verification via /api/whoami-v2
 *  - Gated model access check
 *  - File download with progress reporting
 */
object HfApiClient {

    private const val TAG = "HfApiClient"
    private const val WHOAMI_URL = "https://huggingface.co/api/whoami-v2"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .build()
    }

    // ── Token verification ────────────────────────────────────────────

    sealed class TokenResult {
        data class Valid(val username: String, val displayName: String?) : TokenResult()
        data class InvalidToken(val message: String) : TokenResult()
        data class NetworkError(val message: String) : TokenResult()
    }

    /**
     * Verify a Hugging Face token by hitting /api/whoami-v2.
     * Returns the username on success enough to confirm the token is live.
     */
    suspend fun verifyToken(token: String): TokenResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(WHOAMI_URL)
                .addHeader("Authorization", "Bearer $token")
                .build()

            val response = client.newCall(request).execute()
            when (response.code) {
                200 -> {
                    val body = response.body?.string() ?: "{}"
                    val json = JSONObject(body)
                    val username = json.optString("name", "unknown")
                    val displayName: String? = json.optString("fullname", null)?.ifBlank { null }
                    TokenResult.Valid(username, displayName)
                }
                401 -> TokenResult.InvalidToken("Invalid token. Please check that you copied the full token.")
                403 -> TokenResult.InvalidToken("Token does not have required permissions.")
                else -> TokenResult.InvalidToken("Unexpected response: HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token verification failed", e)
            TokenResult.NetworkError("Network error: ${e.localizedMessage ?: "Could not reach Hugging Face"}")
        }
    }

    // ── Gated model access check ──────────────────────────────────────

    sealed class ModelAccessResult {
        object Granted : ModelAccessResult()
        data class LicenseRequired(val repoUrl: String) : ModelAccessResult()
        data class Unauthorized(val message: String) : ModelAccessResult()
        data class Error(val message: String) : ModelAccessResult()
    }

    /**
     * Check whether the token has access to a specific gated HF repo.
     * Uses the HF API: GET /api/models/{repo_id} with the auth header.
     */
    suspend fun checkModelAccess(token: String, repoId: String): ModelAccessResult =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://huggingface.co/api/models/$repoId"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                val response = client.newCall(request).execute()
                when (response.code) {
                    200 -> ModelAccessResult.Granted
                    401 -> ModelAccessResult.Unauthorized("Token is invalid or expired.")
                    403 -> ModelAccessResult.LicenseRequired("https://huggingface.co/$repoId")
                    else -> ModelAccessResult.Error("Unexpected response: HTTP ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Model access check failed", e)
                ModelAccessResult.Error("Network error: ${e.localizedMessage}")
            }
        }

    // ── File download with progress ───────────────────────────────────

    sealed class DownloadResult {
        data class Success(val file: File) : DownloadResult()
        data class Unauthorized(val message: String) : DownloadResult()
        data class Forbidden(val message: String) : DownloadResult()
        data class Error(val message: String) : DownloadResult()
    }

    /**
     * Download a file from HF Hub with authorization and progress tracking.
     *
     * @param url       Full download URL (resolve/main/...)
     * @param token     HF bearer token
     * @param outputDir Directory to write the file to
     * @param fileName  Target file name
     * @param onProgress Callback with 0-100 percentage. -1 if content-length unknown.
     */
    suspend fun downloadFile(
        url: String,
        token: String,
        outputDir: File,
        fileName: String,
        onProgress: (Int) -> Unit = {}
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext when (response.code) {
                    401 -> DownloadResult.Unauthorized(
                        "Unauthorized — your token may be invalid or expired."
                    )
                    403 -> DownloadResult.Forbidden(
                        "Forbidden — please accept the model's license agreement on Hugging Face."
                    )
                    else -> DownloadResult.Error("Download failed: HTTP ${response.code}")
                }
            }

            val outputFile = File(outputDir, fileName)
            val contentLength = response.body?.contentLength() ?: -1L

            response.body?.byteStream()?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var totalRead = 0L
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            onProgress((totalRead * 100 / contentLength).toInt())
                        } else {
                            onProgress(-1)
                        }
                    }
                    output.flush()
                }
            }

            DownloadResult.Success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            DownloadResult.Error(e.localizedMessage ?: "Download failed")
        }
    }

    // ── Convenience: resolve the best token from storage ──────────────

    /**
     * Returns the best available HF token from local storage:
     * 1. Direct HF token from TokenManager
     * 2. OAuth access token from SecureStorage
     * 3. null if nothing stored
     */
    fun resolveToken(context: Context): String? {
        val tokenManager = TokenManager(context)
        val directToken = tokenManager.getToken()
        if (!directToken.isNullOrBlank()) return directToken

        val oauthToken = com.google.mediapipe.examples.llminference.SecureStorage.getToken(context)
        if (!oauthToken.isNullOrBlank()) return oauthToken

        return null
    }
}
