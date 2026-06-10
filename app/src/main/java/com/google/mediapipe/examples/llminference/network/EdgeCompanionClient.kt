package com.google.mediapipe.examples.llminference.network

import android.content.Context
import android.util.Log
import com.google.mediapipe.examples.llminference.settings.LocalModelFiles
import com.google.mediapipe.examples.llminference.sync.ChartSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object EdgeCompanionClient {

    private const val TAG = "EdgeCompanionClient"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    sealed class HealthResult {
        data class Ok(
            val ollamaOk: Boolean,
            val models: List<String>,
            val phoneUrlWifi: String,
            val phoneUrlUsb: String,
        ) : HealthResult()

        data class Error(val message: String) : HealthResult()
    }

    data class EntryResult(
        val analysisResult: String,
        val visitSummary: String,
        val durationMs: Int,
    )

    data class LongitudinalResult(
        val diagnosis: String,
        val durationMs: Int,
    )

    fun baseUrl(context: Context): String {
        val url = when (LocalModelFiles.getCloudConnectionMode(context)) {
            LocalModelFiles.CLOUD_MODE_WIFI -> LocalModelFiles.getCloudServerUrlWifi(context)
            else -> LocalModelFiles.getCloudServerUrlUsb(context)
        }.trim().trimEnd('/')
        return url
    }

    suspend fun health(context: Context): HealthResult = withContext(Dispatchers.IO) {
        val base = baseUrl(context)
        if (base.isBlank()) {
            return@withContext HealthResult.Error("Set cloud server URL in Settings")
        }
        try {
            val req = Request.Builder().url("$base/health").get().build()
            val resp = http.newCall(req).execute()
            val body = resp.body?.string() ?: "{}"
            if (!resp.isSuccessful) {
                return@withContext HealthResult.Error("HTTP ${resp.code}: $body")
            }
            val json = JSONObject(body)
            val models = mutableListOf<String>()
            json.optJSONArray("models")?.let { arr ->
                for (i in 0 until arr.length()) models.add(arr.getString(i))
            }
            HealthResult.Ok(
                ollamaOk = json.optBoolean("ollama_ok", false),
                models = models,
                phoneUrlWifi = json.optString("phone_url_wifi", ""),
                phoneUrlUsb = json.optString("phone_url_usb", ""),
            )
        } catch (e: Exception) {
            Log.e(TAG, "health failed", e)
            HealthResult.Error(e.localizedMessage ?: "Cannot reach edge companion")
        }
    }

    suspend fun ping(context: Context) = withContext(Dispatchers.IO) {
        val base = baseUrl(context)
        if (base.isBlank()) return@withContext
        try {
            val body = JSONObject().put("device_label", "medveda-android").toString()
            val req = Request.Builder()
                .url("$base/v1/ping")
                .post(body.toRequestBody(JSON))
                .build()
            http.newCall(req).execute().close()
        } catch (e: Exception) {
            Log.w(TAG, "ping failed: ${e.message}")
        }
    }

    suspend fun processEntry(
        context: Context,
        patientName: String,
        entryId: Long,
        entryType: String,
        title: String,
        content: String,
        prompt: String,
        imageBase64: String = "",
        numPredict: Int = 1024,
    ): Result<EntryResult> = withContext(Dispatchers.IO) {
        val base = baseUrl(context)
        val model = LocalModelFiles.getCloudModelName(context)
        try {
            val payload = JSONObject()
                .put("model", model)
                .put("patient_name", patientName)
                .put("entry_id", entryId)
                .put("entry_type", entryType)
                .put("title", title)
                .put("content", content)
                .put("prompt", prompt)
                .put("num_predict", numPredict)
            if (imageBase64.isNotBlank()) payload.put("image_base64", imageBase64)

            val req = Request.Builder()
                .url("$base/v1/process/entry")
                .post(payload.toString().toRequestBody(JSON))
                .build()
            val resp = http.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${resp.code}: $body"))
            }
            val json = JSONObject(body)
            Result.success(
                EntryResult(
                    analysisResult = json.getString("analysis_result"),
                    visitSummary = json.getString("visit_summary"),
                    durationMs = json.optInt("duration_ms", 0),
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncPush(context: Context, payload: org.json.JSONObject): Result<Long> =
        withContext(Dispatchers.IO) {
            val base = baseUrl(context)
            try {
                val req = Request.Builder()
                    .url("$base/v1/sync/push")
                    .post(payload.toString().toRequestBody(JSON))
                    .build()
                val resp = http.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${resp.code}: $body"))
                }
                val json = org.json.JSONObject(body)
                Result.success(json.optLong("cursor", 0))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun syncPull(context: Context, since: Long): Result<org.json.JSONObject> =
        withContext(Dispatchers.IO) {
            val base = baseUrl(context)
            try {
                val req = Request.Builder()
                    .url("$base/v1/sync/pull?since=$since&device_id=${ChartSyncManager.DEVICE_ID}")
                    .get()
                    .build()
                val resp = http.newCall(req).execute()
                val body = resp.body?.string() ?: "{}"
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${resp.code}: $body"))
                }
                Result.success(org.json.JSONObject(body))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    data class CompanionSettings(
        val nightBatchEnabled: Boolean,
        val nightStartHour: Int,
        val nightEndHour: Int,
        val statusMessage: String,
    )

    suspend fun getSettings(context: Context): Result<CompanionSettings> = withContext(Dispatchers.IO) {
        val base = baseUrl(context)
        if (base.isBlank()) return@withContext Result.failure(Exception("Set cloud server URL"))
        try {
            val req = Request.Builder().url("$base/v1/settings").get().build()
            val resp = http.newCall(req).execute()
            val body = resp.body?.string() ?: "{}"
            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${resp.code}: $body"))
            }
            val json = JSONObject(body)
            Result.success(
                CompanionSettings(
                    nightBatchEnabled = json.optBoolean("night_batch_enabled", true),
                    nightStartHour = json.optInt("night_start_hour", 0),
                    nightEndHour = json.optInt("night_end_hour", 2),
                    statusMessage = json.optString("nightBatchStatus", ""),
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setNightBatchEnabled(context: Context, enabled: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            val base = baseUrl(context)
            if (base.isBlank()) return@withContext Result.failure(Exception("Set cloud server URL"))
            try {
                val payload = JSONObject().put("night_batch_enabled", enabled).toString()
                val req = Request.Builder()
                    .url("$base/v1/settings")
                    .put(payload.toRequestBody(JSON))
                    .build()
                val resp = http.newCall(req).execute()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${resp.code}: ${resp.body?.string()}"))
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun triggerServerProcessPatient(context: Context, patientId: Long, force: Boolean = false): Result<Unit> =
        withContext(Dispatchers.IO) {
            val base = baseUrl(context)
            try {
                val req = Request.Builder()
                    .url("$base/v1/process/patient/$patientId?force=$force")
                    .post("{}".toRequestBody(JSON))
                    .build()
                val resp = http.newCall(req).execute()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${resp.code}: ${resp.body?.string()}"))
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun processLongitudinal(
        context: Context,
        patientName: String,
        prompt: String,
        numPredict: Int = 1536,
    ): Result<LongitudinalResult> = withContext(Dispatchers.IO) {
        val base = baseUrl(context)
        val model = LocalModelFiles.getCloudModelName(context)
        try {
            val payload = JSONObject()
                .put("model", model)
                .put("patient_name", patientName)
                .put("prompt", prompt)
                .put("num_predict", numPredict)
            val req = Request.Builder()
                .url("$base/v1/process/longitudinal")
                .post(payload.toString().toRequestBody(JSON))
                .build()
            val resp = http.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${resp.code}: $body"))
            }
            val json = JSONObject(body)
            Result.success(
                LongitudinalResult(
                    diagnosis = json.getString("diagnosis"),
                    durationMs = json.optInt("duration_ms", 0),
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
