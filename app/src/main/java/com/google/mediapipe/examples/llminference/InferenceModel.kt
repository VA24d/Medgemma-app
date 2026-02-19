package com.google.mediapipe.examples.llminference

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.Backend
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import java.io.File
import kotlin.math.max
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage

/** The maximum number of tokens the model can process. */
var MAX_TOKENS = 1024

/**
 * An offset in tokens that we use to ensure that the model always has the ability to respond when
 * we compute the remaining context length.
 */
var DECODE_TOKEN_OFFSET = 256

class ModelLoadFailException :
    Exception("Failed to load model, please try again")

class ModelSessionCreateFailException :
    Exception("Failed to create model session, please try again")

class InferenceModel private constructor(context: Context) {
    private lateinit var llmInference: LlmInference
    private lateinit var llmInferenceSession: LlmInferenceSession
    private val TAG = InferenceModel::class.qualifiedName

    val uiState = UiState(model.thinking)

    init {
        if (!modelExists(context)) {
            throw IllegalArgumentException("Model not found at path: ${model.path}")
        }

        createEngine(context)
        createSession()
    }

    fun close() {
        llmInferenceSession.close()
        llmInference.close()
    }

    fun resetSession() {
        llmInferenceSession.close()
        createSession()
    }

    private fun createEngine(context: Context) {
        val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath(context))
            .setMaxTokens(MAX_TOKENS)
            .apply { model.preferredBackend?.let { setPreferredBackend(it) } }
            .apply {
                val visionPath = visionModelPath(context)
                val projectorPath = projectorModelPath(context)
                if (visionPath.isNotEmpty() && File(visionPath).exists()) {
                    // Enable vision input for multimodal models like MedGemma
                    setMaxNumImages(1)
                }
            }
            .build()

        try {
            llmInference = LlmInference.createFromOptions(context, inferenceOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Load model error (${model.preferredBackend}): ${e.message}", e)

            // Fallback: if GPU/NPU fails, retry with CPU
            if (model.preferredBackend != Backend.CPU) {
                Log.w(TAG, "Retrying with CPU backend as fallback…")
                val cpuOptions = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath(context))
                    .setMaxTokens(MAX_TOKENS)
                    .setPreferredBackend(Backend.CPU)
                    .build()
                try {
                    llmInference = LlmInference.createFromOptions(context, cpuOptions)
                    Log.i(TAG, "Successfully loaded model with CPU fallback")
                    return
                } catch (cpuError: Exception) {
                    Log.e(TAG, "CPU fallback also failed: ${cpuError.message}", cpuError)
                }
            }
            throw ModelLoadFailException()
        }
    }

    private val SYSTEM_PROMPT = "You are a helpful medical data assistant. Provide accurate and concise answers."

    private fun createSession() {
        val sessionOptions =  LlmInferenceSessionOptions.builder()
            .setTemperature(model.temperature)
            .setTopK(model.topK)
            .setTopP(model.topP)
            .build()

        try {
            llmInferenceSession =
                LlmInferenceSession.createFromOptions(llmInference, sessionOptions)
            // Inject system prompt at start of session
            llmInferenceSession.addQueryChunk(SYSTEM_PROMPT)
        } catch (e: Exception) {
            Log.e(TAG, "LlmInferenceSession create error: ${e.message}", e)
            throw ModelSessionCreateFailException()
        }
    }

    fun generateResponseAsync(
        prompt: String,
        images: List<Bitmap>,
        progressListener: ProgressListener<String>
    ) : ListenableFuture<String> {
        llmInferenceSession.addQueryChunk(prompt)
        images.forEach { bitmap ->
            val mpImage = BitmapImageBuilder(bitmap).build()
            llmInferenceSession.addImage(mpImage)
        }
        return llmInferenceSession.generateResponseAsync(progressListener)
    }

    suspend fun generateResponse(prompt: String, images: List<Bitmap> = emptyList()): String = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        llmInferenceSession.addQueryChunk(prompt)
        images.forEach { bitmap ->
            val mpImage = BitmapImageBuilder(bitmap).build()
            llmInferenceSession.addImage(mpImage)
        }
        val future = llmInferenceSession.generateResponseAsync { _, _ -> /* progress ignored for sync */ }
        
        future.addListener(
            {
                try {
                    continuation.resumeWith(Result.success(future.get()))
                } catch (e: Exception) {
                    continuation.resumeWith(Result.failure(e))
                }
            },
            { command -> command.run() } // Direct executor
        )
        
        continuation.invokeOnCancellation { future.cancel(true) }
    }

    fun estimateTokensRemaining(prompt: String): Int {
        val context = SYSTEM_PROMPT + uiState.messages.joinToString { it.message } + prompt
        if (context.isEmpty()) return -1 // Special marker if no content has been added

        val sizeOfAllMessages = llmInferenceSession.sizeInTokens(context)
        val approximateControlTokens = uiState.messages.size * 3
        val remainingTokens = MAX_TOKENS - sizeOfAllMessages - approximateControlTokens -  DECODE_TOKEN_OFFSET
        // Token size is approximate so, let's not return anything below 0
        return max(0, remainingTokens)
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

        /** ADB push directory: adb push model_file /data/local/tmp/medgemma/ */
        private const val ADB_PUSH_DIR = "/data/local/tmp/medgemma"

        /**
         * Resolve a model file path by checking locations in priority order:
         * 1. /data/local/tmp/medgemma/ (ADB push from laptop)
         * 2. App internal storage (downloaded files)
         */
        private fun resolveFile(context: Context, path: String, url: String): String {
            // Derive filename from path or URL
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

            // Return expected internal path (for download target)
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
            val mainExists = File(modelPath(context)).exists()
            val visionExists = if (model.visionUrl.isNotEmpty()) File(visionModelPath(context)).exists() else true
            val projectorExists = if (model.projectorUrl.isNotEmpty()) File(projectorModelPath(context)).exists() else true
            return mainExists && visionExists && projectorExists
        }
    }
}
