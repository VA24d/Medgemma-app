package com.google.mediapipe.examples.llminference

import android.util.Log
import de.kherud.llama.LlamaModel
import de.kherud.llama.ModelParameters
import de.kherud.llama.InferenceParameters
import de.kherud.llama.LlamaOutput

/**
 * GGUF inference engine using java-llama.cpp (de.kherud:llama).
 *
 * llama.cpp handles tokenization, KV cache, sampling, and context management
 * internally — no manual buffer management needed.
 */
class LlamaCppEngine(
    modelPath: String,
    private val model: Model
) {
    companion object {
        private const val TAG = "LlamaCppEngine"
    }

    private val llamaModel: LlamaModel

    init {
        Log.i(TAG, "Loading GGUF model: $modelPath")
        val params = ModelParameters()
            .setModel(modelPath)
            .setThreads(model.nThreads)
            .setCtxSize(model.contextSize)
            .setGpuLayers(0) // CPU only for now

        llamaModel = LlamaModel(params)
        Log.i(TAG, "Model loaded successfully")
    }

    /**
     * Generate text from a prompt, streaming partial results via callback.
     * llama.cpp handles all tokenization, KV cache, and sampling internally.
     */
    fun generateResponse(
        prompt: String,
        maxTokens: Int = 512,
        onPartialResult: ((String) -> Unit)? = null
    ): String {
        val inferParams = InferenceParameters(prompt)
            .setTemperature(model.temperature)
            .setTopK(model.topK)
            .setTopP(model.topP)
            .setNPredict(maxTokens)
            .setStopStrings("<end_of_turn>", "<eos>")

        val sb = StringBuilder()
        var tokenCount = 0

        for (output: LlamaOutput in llamaModel.generate(inferParams)) {
            val token = output.text
            sb.append(token)
            onPartialResult?.invoke(token)
            tokenCount++
        }

        Log.i(TAG, "Generated $tokenCount tokens")
        return sb.toString()
    }

    fun close() {
        try {
            llamaModel.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing engine: ${e.message}")
        }
    }
}
