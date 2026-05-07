package com.google.mediapipe.examples.llminference.demo

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns

/**
 * Best-effort display filename for a picked image (gallery / content / file).
 * Some providers return a numeric [lastPathSegment]; we query DISPLAY_NAME when possible.
 */
fun displayNameForImageUri(context: Context, uri: Uri): String {
    // SAF document picker (Downloads / Files)
    if (uri.scheme == "content" && DocumentsContract.isDocumentUri(context, uri)) {
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (i >= 0) {
                        val n = c.getString(i)
                        if (!n.isNullOrBlank()) return n
                    }
                }
            }
        }
    }

    if (uri.scheme == "content") {
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) {
                        val n = c.getString(i)
                        if (!n.isNullOrBlank()) return n
                    }
                }
            }
        }

        // MediaStore row id: .../media/<id> — DISPLAY_NAME may only resolve via EXTERNAL_CONTENT_URI query.
        runCatching {
            val id = ContentUris.parseId(uri)
            val projection = arrayOf(MediaStore.Images.Media.DISPLAY_NAME)
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Images.Media._ID} = ?",
                arrayOf(id.toString()),
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    if (i >= 0) {
                        val n = c.getString(i)
                        if (!n.isNullOrBlank()) return n
                    }
                }
            }
        }

        runCatching {
            val col = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.MediaColumns.DISPLAY_NAME
            } else {
                @Suppress("DEPRECATION")
                MediaStore.MediaColumns.DATA
            }
            val projection = arrayOf(col)
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(col)
                    if (i >= 0) {
                        val pathOrName = c.getString(i)
                        if (!pathOrName.isNullOrBlank()) {
                            if (pathOrName.contains('/')) {
                                return Uri.decode(pathOrName.substringAfterLast('/'))
                            }
                            return pathOrName
                        }
                    }
                }
            }
        }
    }
    uri.path?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let {
        val decoded = Uri.decode(it)
        if (!decoded.all { ch -> ch.isDigit() }) return decoded
    }
    return uri.lastPathSegment?.substringAfterLast(':') ?: ""
}
