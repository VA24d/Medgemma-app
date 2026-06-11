package com.google.mediapipe.examples.llminference.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object GeminiClient {

    private const val TAG = "GeminiClient"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun chat(
        apiKey: String,
        prompt: String,
        imagesBase64: List<String> = emptyList(),
        model: String = "gemini-2.5-flash",
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("Gemini API key not set"))
        }
        try {
            val parts = JSONArray()
            parts.put(JSONObject().put("text", prompt))
            for (b64 in imagesBase64) {
                if (b64.isBlank()) continue
                parts.put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", "image/jpeg")
                            .put("data", b64),
                    ),
                )
            }
            val body = JSONObject()
                .put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put("parts", parts),
                    ),
                )
            val encodedKey = URLEncoder.encode(apiKey.trim(), StandardCharsets.UTF_8.name())
            val url =
                "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$encodedKey"
            val req = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody(JSON))
                .build()
            val resp = http.newCall(req).execute()
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("Gemini HTTP ${resp.code}: $text"))
            }
            val json = JSONObject(text)
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext Result.failure(Exception("No response from Gemini"))
            }
            val content = candidates.getJSONObject(0).optJSONObject("content")
            val outParts = content?.optJSONArray("parts")
            val reply = buildString {
                if (outParts != null) {
                    for (i in 0 until outParts.length()) {
                        val part = outParts.optJSONObject(i)
                        val t = part?.optString("text", "") ?: ""
                        if (t.isNotBlank()) append(t)
                    }
                }
            }.trim()
            if (reply.isBlank()) {
                return@withContext Result.failure(Exception("Empty Gemini response"))
            }
            Result.success(reply)
        } catch (e: Exception) {
            Log.e(TAG, "chat failed", e)
            Result.failure(e)
        }
    }
}
