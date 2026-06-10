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
    private const val KEY_CLOUD_MODE = "cloud_connection_mode"
    private const val KEY_CLOUD_URL_WIFI = "cloud_server_url_wifi"
    private const val KEY_CLOUD_URL_USB = "cloud_server_url_usb"
    private const val KEY_CLOUD_MODEL = "cloud_model_name"
    private const val KEY_CLOUD_ENABLED = "cloud_enabled"
    private const val KEY_AUTO_SYNC = "auto_sync_enabled"
    private const val KEY_LAST_SYNC_CURSOR = "last_sync_cursor"
    private const val KEY_LAST_SYNC_AT = "last_sync_at"

    const val CLOUD_MODE_USB = "USB"
    const val CLOUD_MODE_WIFI = "WIFI"

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

    /** Canonical key for comparing sidebar language across prefs quirks ("off", "OFF", spaces). */
    fun normalizedLanguageExtensionKey(context: Context): String {
        val raw = getLanguageExtension(context).trim()
        return when {
            raw.equals("Telugu", ignoreCase = true) -> "telugu"
            raw.equals("Hindi", ignoreCase = true) -> "hindi"
            else -> "off"
        }
    }

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

    fun getCloudConnectionMode(context: Context): String =
        prefs(context).getString(KEY_CLOUD_MODE, CLOUD_MODE_USB) ?: CLOUD_MODE_USB

    fun setCloudConnectionMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_CLOUD_MODE, mode).apply()
    }

    fun getCloudServerUrlWifi(context: Context): String =
        prefs(context).getString(KEY_CLOUD_URL_WIFI, "http://10.163.156.58:8787") ?: "http://10.163.156.58:8787"

    fun setCloudServerUrlWifi(context: Context, url: String) {
        prefs(context).edit().putString(KEY_CLOUD_URL_WIFI, url.trim()).apply()
    }

    fun getCloudServerUrlUsb(context: Context): String =
        prefs(context).getString(KEY_CLOUD_URL_USB, "http://127.0.0.1:8787") ?: "http://127.0.0.1:8787"

    fun setCloudServerUrlUsb(context: Context, url: String) {
        prefs(context).edit().putString(KEY_CLOUD_URL_USB, url.trim()).apply()
    }

    fun getCloudModelName(context: Context): String =
        prefs(context).getString(KEY_CLOUD_MODEL, "MedGemma1.5:latest") ?: "MedGemma1.5:latest"

    fun setCloudModelName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_CLOUD_MODEL, name.trim()).apply()
    }

    fun isCloudEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CLOUD_ENABLED, true)

    fun setCloudEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CLOUD_ENABLED, enabled).apply()
    }

    fun isAutoSyncEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_SYNC, true)

    fun setAutoSyncEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
    }

    fun getLastSyncCursor(context: Context): Long =
        prefs(context).getLong(KEY_LAST_SYNC_CURSOR, 0)

    fun setLastSyncCursor(context: Context, cursor: Long) {
        prefs(context).edit().putLong(KEY_LAST_SYNC_CURSOR, cursor).apply()
    }

    fun getLastSyncAt(context: Context): Long =
        prefs(context).getLong(KEY_LAST_SYNC_AT, 0)

    fun setLastSyncAt(context: Context, at: Long) {
        prefs(context).edit().putLong(KEY_LAST_SYNC_AT, at).apply()
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
