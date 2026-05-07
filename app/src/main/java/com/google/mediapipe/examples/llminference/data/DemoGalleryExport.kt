package com.google.mediapipe.examples.llminference.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi

/**
 * Puts demo CXR placeholders into the public **Pictures/MedgemmaDemo** album so they appear in
 * the normal gallery / Google Photos pickers (best-effort; no-op on failure).
 */
object DemoGalleryExport {

    private const val PREFS = "medgemma_demo_seed"
    private const val KEY_EXPORTED = "demo_gallery_export_v1"
    private const val ALBUM = "MedgemmaDemo"

    private data class DemoAsset(
        val assetPath: String,
        val fileName: String,
        val mime: String
    )

    private val FILES = listOf(
        DemoAsset("demo_xrays/appa_rao_cxr_2024_10_baseline.jpg", "appa_rao_cxr_2024_10_baseline.jpg", "image/jpeg"),
        DemoAsset("demo_xrays/appa_rao_cxr_2024_11_followup.png", "appa_rao_cxr_2024_11_followup.png", "image/png")
    )

    fun exportOnceIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_EXPORTED, false)) return

        val appContext = context.applicationContext
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                FILES.all { mediaStoreAlreadyHas(appContext, it.fileName) }
            ) {
                prefs.edit().putBoolean(KEY_EXPORTED, true).apply()
                return
            }
            for (f in FILES) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    mediaStoreAlreadyHas(appContext, f.fileName)
                ) {
                    continue
                }
                exportOne(appContext, f)
            }
            prefs.edit().putBoolean(KEY_EXPORTED, true).apply()
        } catch (_: Exception) {
            // Best-effort only
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun mediaStoreAlreadyHas(context: Context, displayName: String): Boolean {
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND " +
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(displayName, "%${Environment.DIRECTORY_PICTURES}/$ALBUM%")
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            selection,
            args,
            null
        )?.use { return it.count > 0 }
        return false
    }

    private fun exportOne(context: Context, file: DemoAsset) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            insertViaMediaStore(context, file)
        } else {
            insertViaLegacyPublicDir(context, file)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun insertViaMediaStore(context: Context, file: DemoAsset) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.fileName)
            put(MediaStore.Images.Media.MIME_TYPE, file.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return
        resolver.openOutputStream(uri)?.use { out ->
            context.assets.open(file.assetPath).use { input -> input.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    private fun insertViaLegacyPublicDir(context: Context, file: DemoAsset) {
        val dir = java.io.File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM
        )
        dir.mkdirs()
        val outFile = java.io.File(dir, file.fileName)
        context.assets.open(file.assetPath).use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        MediaScannerConnection.scanFile(
            context,
            arrayOf(outFile.absolutePath),
            arrayOf(file.mime),
            null
        )
    }
}
