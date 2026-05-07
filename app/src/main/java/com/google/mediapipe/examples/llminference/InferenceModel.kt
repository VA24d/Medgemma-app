package com.google.mediapipe.examples.llminference

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
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
private const val DEFAULT_PREDICT_LENGTH = 1024

class ModelLoadFailException :
    Exception("Failed to load model, please try again")

class ModelSessionCreateFailException :
    Exception("Failed to create model session, please try again")

class InferenceModel private constructor(context: Context) {
    private val appContext = context.applicationContext
    private lateinit var engine: InferenceEngine
    private var mmprojLoaded = false
    val isVisionAvailable: Boolean get() = mmprojLoaded || isMmprojFileAvailable()
    private val TAG = InferenceModel::class.qualifiedName

    // In-memory snapshot of the thinking state last applied to the engine.
    // Avoids enforceThinkingState() stomping on values set by updateThinkingMode().
    @Volatile private var currentThinkingEnabled: Boolean = false

    /** Last language-extension key applied to the native chat/KV state (singleton engine). */
    @Volatile private var lastConversationLangKey: String? = null

    /** Check if mmproj file exists on disk (even if not yet loaded into engine). */
    private fun isMmprojFileAvailable(): Boolean {
        val mmproj = mmprojPath(appContext)
        return mmproj.isNotBlank() && File(mmproj).exists()
    }

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

    fun updateThinkingMode(thinkingEnabled: Boolean) {
        currentThinkingEnabled = thinkingEnabled
        engine.setSkipThinking(!thinkingEnabled)
        Log.i(TAG, "Thinking mode updated: ${if (thinkingEnabled) "enabled" else "disabled"}")
    }

    /** Whether thinking is currently active on the engine. */
    fun isThinkingCurrentlyEnabled(): Boolean = currentThinkingEnabled

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

                // Skip loading mmproj at init — it will be loaded lazily when an image is first sent
                mmprojLoaded = false
                val mmproj = mmprojPath(context)
                if (mmproj.isNotBlank() && File(mmproj).exists()) {
                    Log.i(TAG, "Vision encoder available at $mmproj (will load on first image)")
                }

                // Control thinking behavior via native prefill skip
                val thinkingEnabled = LocalModelFiles.isThinkingEnabled(context)
                currentThinkingEnabled = thinkingEnabled
                engine.setSkipThinking(!thinkingEnabled)
                Log.i(TAG, "Thinking mode: ${if (thinkingEnabled) "enabled" else "disabled (prefill skip)"}")
            }
            Log.i(TAG, "GGUF backend initialized. mmprojLoaded=$mmprojLoaded")
            lastConversationLangKey = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GGUF backend: ${e.message}", e)
            throw ModelLoadFailException()
        }
    }

    /**
     * Lazily loads the vision encoder (mmproj) on first use.
     * Returns true if mmproj is now loaded and ready.
     */
    suspend fun ensureMmprojLoaded(): Boolean {
        if (mmprojLoaded) return true
        val mmproj = mmprojPath(appContext)
        if (mmproj.isBlank() || !File(mmproj).exists()) return false
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Lazy-loading vision encoder: $mmproj")
                mmprojLoaded = engine.loadMMProj(mmproj)
                Log.i(TAG, "Vision encoder loaded: $mmprojLoaded")
                mmprojLoaded
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load vision encoder: ${e.message}", e)
                false
            }
        }
    }

    /**
     * Re-enforce thinking mode state right before generation.
     * Uses the in-memory [currentThinkingEnabled] so that callers who set a specific
     * mode via [updateThinkingMode] are not overridden by a stale prefs read.
     */
    private fun enforceThinkingState() {
        engine.setSkipThinking(!currentThinkingEnabled)
        Log.d(TAG, "enforceThinkingState: thinking=${currentThinkingEnabled} skipThinking=${!currentThinkingEnabled}")
    }

    /**
     * Native llama chat state accumulates user/assistant turns. When Language Extension changes,
     * clear KV + chat_msgs so the model is not steered by prior Telugu/Hindi replies.
     */
    private fun syncNativeConversationWithLanguagePref() {
        val key = LocalModelFiles.normalizedLanguageExtensionKey(appContext)
        val prev = lastConversationLangKey
        lastConversationLangKey = key
        if (prev != null && prev != key) {
            try {
                engine.resetConversation()
                Log.i(TAG, "Reset native chat for language change: $prev -> $key")
            } catch (e: Exception) {
                Log.e(TAG, "resetConversation failed", e)
            }
        }
    }

    /**
     * @param maxPredictTokens Hard cap on generated tokens (native decode loop).
     * @param forceSkipThinkingForRequest When true, skips model "thinking" prefill for this call only
     * (faster for chart Q&A). User preference is restored afterward.
     */
    fun generateResponseAsync(
        prompt: String,
        images: List<Bitmap>,
        maxPredictTokens: Int = DEFAULT_PREDICT_LENGTH,
        forceSkipThinkingForRequest: Boolean = false,
        progressListener: (String, Boolean) -> Unit,
    ): java.util.concurrent.Future<String> {
        return executor.submit(java.util.concurrent.Callable {
            syncNativeConversationWithLanguagePref()
            val response = runBlocking(Dispatchers.IO) {
                val fast = forceSkipThinkingForRequest
                if (fast) {
                    Log.i(TAG, "Perf: fast chart reply (skip-thinking override), promptChars=${prompt.length}, maxOut=$maxPredictTokens")
                    engine.setSkipThinking(true)
                } else {
                    enforceThinkingState()
                }
                try {
                    if (images.isNotEmpty()) {
                        Log.i(TAG, "chat_inference=VISION images=${images.size} (multimodal / slow path)")
                        val visionReady = ensureMmprojLoaded()
                        if (visionReady) {
                            generateMultimodalResponse(prompt, images.first(), progressListener, maxPredictTokens)
                        } else {
                            Log.w(TAG, "mmproj not ready; falling back to text-only (no image)")
                            generateTextResponse(prompt, progressListener, maxPredictTokens)
                        }
                    } else {
                        if (!fast) {
                            Log.i(TAG, "chat_inference=TEXT_ONLY promptChars=${prompt.length}, maxOut=$maxPredictTokens")
                        } else {
                            Log.i(TAG, "chat_inference=TEXT_ONLY (fast) promptChars=${prompt.length}, maxOut=$maxPredictTokens")
                        }
                        generateTextResponse(prompt, progressListener, maxPredictTokens)
                    }
                } finally {
                    if (fast) {
                        enforceThinkingState()
                    }
                }
            }
            progressListener("", true)
            response
        })
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        generateTextResponse(prompt, { _, _ -> }, DEFAULT_PREDICT_LENGTH)
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
        progressListener: (String, Boolean) -> Unit,
        predictLength: Int = DEFAULT_PREDICT_LENGTH,
    ): String {
        val output = StringBuilder()
        engine.sendUserPrompt(prompt, predictLength).collect { token ->
            output.append(token)
            progressListener(token, false)
        }
        return output.toString()
    }

    private suspend fun generateMultimodalResponse(
        prompt: String,
        image: Bitmap,
        progressListener: (String, Boolean) -> Unit,
        predictLength: Int = DEFAULT_PREDICT_LENGTH,
    ): String {
        val tempImage = writeBitmapToTemp(image)
        try {
            val loaded = engine.loadImage(tempImage.absolutePath)
            if (!loaded) {
                throw RuntimeException("Failed to load image for multimodal inference")
            }

            val output = StringBuilder()
            engine.sendImagePrompt(prompt, predictLength).collect { token ->
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
        var model: Model = Model.MEDGEMMA_4B_IT_GPU
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

            val localFromPath = findMmprojFromKnownFolders(File(model.mmprojPath).name)
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
            val f = File(modelPath(context))
            return f.exists() && f.canRead()
        }

        private fun findFromKnownFolders(fileName: String): String? {
            val extRoot = Environment.getExternalStorageDirectory().path
            val candidateDirs = listOf(
                "/storage/emulated/0/Download/medgemma",
                "/storage/emulated/0/Download/MedGemma",
                "$extRoot/Download/medgemma",
                "$extRoot/Download/MedGemma"
            )

            for (dir in candidateDirs) {
                val candidate = File(dir, fileName)
                if (candidate.exists() && candidate.canRead()) {
                    return candidate.absolutePath
                }
            }
            return null
        }

        /**
         * Finds a vision encoder (mmproj) file in the known download folders by pattern,
         * falling back to any *.gguf file whose name contains "mmproj" when the exact
         * [preferredName] is not present or not readable.
         */
        private fun findMmprojFromKnownFolders(preferredName: String): String? {
            // First try the exact preferred name
            findFromKnownFolders(preferredName)?.let { return it }

            // Fall back to any readable mmproj-like GGUF in the known dirs
            val extRoot = Environment.getExternalStorageDirectory().path
            val candidateDirs = listOf(
                "/storage/emulated/0/Download/medgemma",
                "/storage/emulated/0/Download/MedGemma",
                "$extRoot/Download/medgemma",
                "$extRoot/Download/MedGemma"
            )
            for (dir in candidateDirs) {
                val folder = File(dir)
                val mmproj = folder.listFiles { f ->
                    f.isFile && f.name.contains("mmproj", ignoreCase = true) &&
                        f.name.endsWith(".gguf", ignoreCase = true) && f.canRead()
                }?.firstOrNull()
                if (mmproj != null) return mmproj.absolutePath
            }
            return null
        }
    }
}
