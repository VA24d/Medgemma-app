package com.google.mediapipe.examples.llminference.debug

import android.content.Context
import android.util.Log
import com.google.mediapipe.examples.llminference.BuildConfig
import com.google.mediapipe.examples.llminference.SecureStorage
import com.google.mediapipe.examples.llminference.settings.LocalModelFiles
import java.io.File

/**
 * Debug-only: import GEMINI_API_KEY from filesDir/gemini_key_import (pushed via adb).
 * File is deleted after successful import.
 */
object GeminiKeyImporter {

    private const val TAG = "GeminiKeyImporter"
    private const val IMPORT_FILE = "gemini_key_import"

    fun tryImportFromFilesDir(context: Context): Boolean {
        if (!BuildConfig.DEBUG) return false
        val file = File(context.filesDir, IMPORT_FILE)
        if (!file.exists()) return false
        return try {
            val raw = file.readText().trim { it.isWhitespace() || it == '\u0000' }
            val key = raw.removePrefix("GEMINI_API_KEY=").trim { it.isWhitespace() || it == '\u0000' }
            if (key.isBlank()) {
                Log.w(TAG, "Import file empty")
                false
            } else {
                SecureStorage.saveGeminiApiKey(context, key)
                LocalModelFiles.setInferenceTier(context, LocalModelFiles.TIER_GEMINI_API)
                file.delete()
                Log.i(TAG, "Gemini key imported (${key.length} chars), tier=GEMINI_API")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            false
        }
    }
}
