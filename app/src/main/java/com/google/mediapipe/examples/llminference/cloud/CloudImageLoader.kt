package com.google.mediapipe.examples.llminference.cloud

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File

object CloudImageLoader {
    private const val TAG = "CloudImageLoader"

    fun loadBitmap(context: Context, imagePath: String): Bitmap? {
        return try {
            val uri = Uri.parse(imagePath)
            @Suppress("DEPRECATION")
            when {
                uri.scheme == "file" -> BitmapFactory.decodeFile(uri.path)
                uri.scheme == "content" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        android.graphics.ImageDecoder.decodeBitmap(
                            android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                        ) { decoder, _, _ -> decoder.isMutableRequired = true }
                    } else {
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                }
                else -> BitmapFactory.decodeFile(imagePath)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load $imagePath: ${e.message}")
            null
        }
    }

    fun firstImagePath(entry: com.google.mediapipe.examples.llminference.data.MedicalEntryEntity): String? {
        if (entry.imagePaths.isBlank()) return null
        return entry.imagePaths.split(',').firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }

    fun bitmapToJpegBase64(bitmap: Bitmap, maxDim: Int = 1536, quality: Int = 85): String {
        val scaled = scaleDown(bitmap, maxDim)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled !== bitmap) scaled.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val scale = maxDim.toFloat() / maxOf(w, h)
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, nw, nh, true)
    }

    fun filePathFromEntry(path: String): String? {
        return try {
            val uri = Uri.parse(path)
            when {
                uri.scheme == "file" -> uri.path
                else -> if (File(path).exists()) path else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
