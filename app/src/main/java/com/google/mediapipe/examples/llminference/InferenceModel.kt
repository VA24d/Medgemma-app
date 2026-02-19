package com.google.mediapipe.examples.llminference

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import java.io.File
import kotlin.math.max

class ModelLoadFailException :
    Exception("Failed to load model, please try again")

class ModelSessionCreateFailException :
    Exception("Failed to create model session, please try again")

class InferenceModel private constructor(context: Context) {
    private lateinit var engine: LlamaCppEngine
    private val TAG = InferenceModel::class.qualifiedName

    val uiState = UiState(model.thinking)

    private val SYSTEM_PROMPT = "You are a helpful medical data assistant. Provide accurate and concise answers."

    init {
        if (!modelExists(context)) {
            throw IllegalArgumentException("Model not found at path: ${model.path}")
        }

        createEngine(context)
    }

    fun close() {
        engine.close()
    }

    fun resetSession() {
        // llama.cpp manages context internally, nothing needed here
    }

    private fun createEngine(context: Context) {
        try {
            val modelFilePath = modelPath(context)
            Log.i(TAG, "Loading model from: $modelFilePath")
            engine = LlamaCppEngine(modelFilePath, model)
            Log.i(TAG, "Engine created successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create engine: ${e.message}", e)
            throw ModelLoadFailException()
        }
    }

    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

    /**
     * Generate response with streaming callback (used by ChatViewModel).
     */
    fun generateResponseAsync(
        prompt: String,
        images: List<Bitmap>,
        progressListener: (String, Boolean) -> Unit
    ): java.util.concurrent.Future<String> {
        Log.i(TAG, "generateResponseAsync called")
        return executor.submit(java.util.concurrent.Callable {
            val fullPrompt = formatPrompt(prompt)
            Log.i(TAG, "Submitting prompt (${fullPrompt.length} chars)")
            val result = engine.generateResponse(fullPrompt) { partial ->
                progressListener(partial, false)
            }
            progressListener("", true)
            result
        })
    }

    /**
     * Generate response synchronously (used by FhirExportManager).
     */
    suspend fun generateResponse(prompt: String, images: List<Bitmap> = emptyList()): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val fullPrompt = formatPrompt(prompt)
            engine.generateResponse(fullPrompt)
        }
    }

    private fun formatPrompt(userPrompt: String): String {
        return "<start_of_turn>user\n$SYSTEM_PROMPT\n\n$userPrompt<end_of_turn>\n<start_of_turn>model\n"
    }

    fun estimateTokensRemaining(prompt: String): Int {
        // Rough estimation: ~4 chars per token for English text
        val estimatedTokens = (SYSTEM_PROMPT.length + prompt.length) / 4
        return max(0, model.contextSize - estimatedTokens - 10)
    }

    companion object {
        var model: Model = Model.MEDGEMMA_4B
        private var instance: InferenceModel? = null

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

        /** ADB push directory */
        private const val ADB_PUSH_DIR = "/data/local/tmp/medgemma"

        private fun resolveFile(context: Context, path: String, url: String): String {
            val pathFileName = if (path.isNotEmpty()) File(path).name else null
            val urlFileName = if (url.isNotEmpty()) Uri.parse(url).lastPathSegment else null

            // Check ADB push dir first
            for (name in listOfNotNull(pathFileName, urlFileName)) {
                val adbFile = File(ADB_PUSH_DIR, name)
                if (adbFile.exists()) return adbFile.absolutePath
            }

            // Check app internal storage
            for (name in listOfNotNull(urlFileName, pathFileName)) {
                val internalFile = File(context.filesDir, name)
                if (internalFile.exists()) return internalFile.absolutePath
            }

            val preferredName = urlFileName ?: pathFileName ?: return ""
            return File(context.filesDir, preferredName).absolutePath
        }

        fun modelPathFromUrl(context: Context): String =
            resolveFile(context, model.path, model.url)

        fun modelPath(context: Context): String =
            resolveFile(context, model.path, model.url)

        fun visionModelPath(context: Context): String =
            resolveFile(context, model.visionPath, model.visionUrl)

        fun projectorModelPath(context: Context): String =
            resolveFile(context, model.projectorPath, model.projectorUrl)

        fun modelExists(context: Context): Boolean {
            return File(modelPath(context)).exists()
        }
    }
}
