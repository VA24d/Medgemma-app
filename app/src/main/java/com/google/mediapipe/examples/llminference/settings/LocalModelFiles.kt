package com.google.mediapipe.examples.llminference.settings

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File

object LocalModelFiles {
    private const val PREFS = "local_model_files"
    private const val KEY_MODEL_PATH = "model_path"
    private const val KEY_MMPROJ_PATH = "mmproj_path"
    private const val KEY_VISION_ENABLED = "vision_enabled"
    private const val KEY_THINKING_ENABLED = "thinking_enabled"
    private const val KEY_LANGUAGE_EXTENSION = "language_extension"
    private const val KEY_SCHEDULED_PROGNOSIS = "scheduled_prognosis_enabled"
    private const val KEY_SCHEDULE_HOUR = "schedule_hour"
    private const val KEY_SCHEDULE_MINUTE = "schedule_minute"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getModelPath(context: Context): String =
        prefs(context).getString(KEY_MODEL_PATH, "") ?: ""

    fun setModelPath(context: Context, path: String) {
        prefs(context).edit().putString(KEY_MODEL_PATH, path).apply()
    }

    fun hasCustomModel(context: Context): Boolean = getModelPath(context).isNotBlank()

    fun clearModelPath(context: Context) {
        prefs(context).edit().remove(KEY_MODEL_PATH).apply()
    }

    fun getMmprojPath(context: Context): String =
        prefs(context).getString(KEY_MMPROJ_PATH, "") ?: ""

    fun clearMmprojPath(context: Context) {
        prefs(context).edit().remove(KEY_MMPROJ_PATH).apply()
    }

    fun setMmprojPath(context: Context, path: String) {
        prefs(context).edit().putString(KEY_MMPROJ_PATH, path).apply()
    }

    fun isVisionEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VISION_ENABLED, true)

    fun setVisionEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VISION_ENABLED, enabled).apply()
    }

    fun isThinkingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_THINKING_ENABLED, false)

    fun setThinkingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_THINKING_ENABLED, enabled).apply()
    }

    fun getLanguageExtension(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE_EXTENSION, "Off") ?: "Off"

    fun setLanguageExtension(context: Context, language: String) {
        prefs(context).edit().putString(KEY_LANGUAGE_EXTENSION, language).apply()
    }

    fun isScheduledPrognosisEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SCHEDULED_PROGNOSIS, false)

    fun setScheduledPrognosisEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SCHEDULED_PROGNOSIS, enabled).apply()
    }

    fun getScheduleHour(context: Context): Int =
        prefs(context).getInt(KEY_SCHEDULE_HOUR, 2) // default 2 AM

    fun getScheduleMinute(context: Context): Int =
        prefs(context).getInt(KEY_SCHEDULE_MINUTE, 0)

    fun setScheduleTime(context: Context, hour: Int, minute: Int) {
        prefs(context).edit().putInt(KEY_SCHEDULE_HOUR, hour).putInt(KEY_SCHEDULE_MINUTE, minute).apply()
    }

    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0 && cursor.moveToFirst()) {
                val value = cursor.getString(nameIdx)
                if (!value.isNullOrBlank()) return value
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "selected_file"
    }

    fun resolveUriToFilePath(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.path
        }

        if (uri.scheme != "content") {
            return null
        }

        val authority = uri.authority ?: return null
        if (authority == "com.android.externalstorage.documents") {
            val docId = DocumentsContract.getDocumentId(uri)
            val parts = docId.split(':', limit = 2)
            if (parts.size == 2) {
                val type = parts[0]
                val relative = parts[1]
                if (type.equals("primary", ignoreCase = true)) {
                    return File(Environment.getExternalStorageDirectory(), relative).absolutePath
                }
            }
        }

        if (authority == "com.android.providers.downloads.documents") {
            val docId = DocumentsContract.getDocumentId(uri)
            if (docId.startsWith("raw:")) {
                return docId.removePrefix("raw:")
            }
        }

        return null
    }

    fun copyUriToInternalFile(context: Context, uri: Uri, fileName: String? = null): String {
        val safeName = sanitizeName(fileName ?: displayName(context, uri))
        val outputFile = File(context.filesDir, safeName)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected file" }
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return outputFile.absolutePath
    }

    private fun sanitizeName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
