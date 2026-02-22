package com.google.mediapipe.examples.llminference

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.google.mediapipe.examples.llminference.settings.LocalModelFiles
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** The maximum number of tokens the model can process. */
var MAX_TOKENS = 1024

/**
 * An offset in tokens that we use to ensure that the model always has the ability to respond when
 * we compute the remaining context length.
 */
var DECODE_TOKEN_OFFSET = 256
private const val DEFAULT_PREDICT_LENGTH = 250

class ModelLoadFailException :
    Exception("Failed to load model, please try again")

class ModelSessionCreateFailException :
    Exception("Failed to create model session, please try again")

class InferenceModel private constructor(context: Context) {
    private val appContext = context.applicationContext
    private lateinit var engine: InferenceEngine
    private var mmprojLoaded = false
    private val TAG = InferenceModel::class.qualifiedName

    val uiState = UiState(model.thinking)

    init {
        if (!modelExists(context)) {
            throw IllegalArgumentException("Model not found at path: ${model.path}")
        }

        createEngine(context)
    }

    fun close() {
        runBlocking {
            try {
                engine.freeMMProj()
            } catch (_: Exception) {
            }
        }
    }

    fun resetSession() {
        runBlocking {
            try {
                engine.cleanUp()
            } catch (_: Exception) {
            }
            createEngine(appContext)
        }
    }

    private fun createEngine(context: Context) {
        try {
            engine = AiChat.getInferenceEngine(context)
            val localModelPath = modelPath(context)
            if (!File(localModelPath).exists()) {
                throw ModelLoadFailException()
            }

            runBlocking(Dispatchers.IO) {
                try {
                    engine.cleanUp()
                } catch (_: Exception) {
                }

                engine.loadModel(localModelPath)

                val mmproj = mmprojPath(context)
                mmprojLoaded = if (mmproj.isNotBlank() && File(mmproj).exists()) {
                    engine.loadMMProj(mmproj)
                } else {
                    false
                }
            }
            Log.i(TAG, "GGUF backend initialized. mmprojLoaded=$mmprojLoaded")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GGUF backend: ${e.message}", e)
            throw ModelLoadFailException()
        }
    }

    fun generateResponseAsync(
        prompt: String,
        images: List<Bitmap>,
        progressListener: (String, Boolean) -> Unit
    ): java.util.concurrent.Future<String> {
        return executor.submit(java.util.concurrent.Callable {
            val response = runBlocking(Dispatchers.IO) {
                if (images.isNotEmpty() && mmprojLoaded) {
                    generateMultimodalResponse(prompt, images.first(), progressListener)
                } else {
                    generateTextResponse(prompt, progressListener)
                }
            }
            progressListener("", true)
            response
        })
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        generateTextResponse(prompt) { _, _ -> }
    }

    fun estimateTokensRemaining(prompt: String): Int {
        val estimatedTokens = (prompt.length / 4) + 64
        val remainingTokens = MAX_TOKENS - estimatedTokens - DECODE_TOKEN_OFFSET
        return max(0, remainingTokens)
    }

    private fun writeBitmapToTemp(bitmap: Bitmap): File {
        val temp = File(appContext.cacheDir, "chat_image_${System.currentTimeMillis()}.jpg")
        FileOutputStream(temp).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        return temp
    }

    private suspend fun generateTextResponse(
        prompt: String,
        progressListener: (String, Boolean) -> Unit
    ): String {
        val output = StringBuilder()
        engine.sendUserPrompt(prompt, DEFAULT_PREDICT_LENGTH).collect { token ->
            output.append(token)
            progressListener(token, false)
        }
        return output.toString()
    }

    private suspend fun generateMultimodalResponse(
        prompt: String,
        image: Bitmap,
        progressListener: (String, Boolean) -> Unit
    ): String {
        val tempImage = writeBitmapToTemp(image)
        try {
            val loaded = engine.loadImage(tempImage.absolutePath)
            if (!loaded) {
                throw RuntimeException("Failed to load image for multimodal inference")
            }

            val output = StringBuilder()
            engine.sendImagePrompt(prompt, DEFAULT_PREDICT_LENGTH).collect { token ->
                output.append(token)
                progressListener(token, false)
            }
            return output.toString()
        } finally {
            tempImage.delete()
        }
    }

    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

    companion object {
        var model: Model = Model.GEMMA_3_1B_IT_GPU
        private var instance: InferenceModel? = null

        /** True when engine is already loaded and ready for chat. */
        fun isLoaded(): Boolean = instance != null

        fun getInstance(context: Context): InferenceModel {
            return if (instance != null) {
                instance!!
            } else {
                InferenceModel(context).also { instance = it }
            }
        }

        fun resetInstance(context: Context): InferenceModel {
            return InferenceModel(context).also { instance = it }
        }

        fun modelPathFromUrl(context: Context): String {
            if (model.url.isNotEmpty()) {
                val urlFileName = Uri.parse(model.url).lastPathSegment
                if (!urlFileName.isNullOrEmpty()) {
                    return File(context.filesDir, urlFileName).absolutePath
                }
            }

            return ""
        }

        fun modelPath(context: Context): String {
            val customModelPath = LocalModelFiles.getModelPath(context)
            if (customModelPath.isNotBlank() && File(customModelPath).exists()) {
                return customModelPath
            }

            val modelFile = File(model.path)
            if (modelFile.exists()) {
                return model.path
            }

            val localFromPath = findFromKnownFolders(File(model.path).name)
            if (localFromPath != null) {
                return localFromPath
            }

            val localFromUrl = model.url.substringAfterLast('/', "")
            if (localFromUrl.isNotBlank()) {
                val fromUrlOnDevice = findFromKnownFolders(localFromUrl)
                if (fromUrlOnDevice != null) {
                    return fromUrlOnDevice
                }
            }

            return modelPathFromUrl(context)
        }

        fun mmprojPath(context: Context): String {
            if (!LocalModelFiles.isVisionEnabled(context)) {
                return ""
            }

            val customMmprojPath = LocalModelFiles.getMmprojPath(context)
            if (customMmprojPath.isNotBlank() && File(customMmprojPath).exists()) {
                return customMmprojPath
            }

            val modelFile = File(model.mmprojPath)
            if (modelFile.exists()) {
                return model.mmprojPath
            }

            val localFromPath = findFromKnownFolders(File(model.mmprojPath).name)
            if (localFromPath != null) {
                return localFromPath
            }

            val fileNameFromUrl = model.mmprojUrl.substringAfterLast('/', "")
            if (fileNameFromUrl.isNotBlank()) {
                val localFromUrl = findFromKnownFolders(fileNameFromUrl)
                if (localFromUrl != null) {
                    return localFromUrl
                }
            }

            if (fileNameFromUrl.isNotBlank()) {
                val internal = File(context.filesDir, fileNameFromUrl)
                if (internal.exists()) {
                    return internal.absolutePath
                }
            }

            return ""
        }

        fun modelExists(context: Context): Boolean {
            return File(modelPath(context)).exists()
        }

        private fun findFromKnownFolders(fileName: String): String? {
            val candidateDirs = listOf(
                "/storage/emulated/0/Download/medgemma",
                "/storage/emulated/0/Download/MedGemma",
                "/sdcard/Download/medgemma",
                "/sdcard/Download/MedGemma"
            )

            for (dir in candidateDirs) {
                val candidate = File(dir, fileName)
                if (candidate.exists()) {
                    return candidate.absolutePath
                }
            }
            return null
        }
    }
}
