package com.google.mediapipe.examples.llminference

import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * LiteRT-based inference engine that loads raw .tflite models
 * converted via litert_torch.generative.converter and runs
 * autoregressive text generation.
 *
 * The model uses a "decode" signature with inputs:
 * - tokens: [1, 1] int32
 * - input_pos: [1] int32
 * - mask: [1, 1, 1, kvCacheMaxLen] float32
 * - kv_cache_k_0..N: [1, numKvHeads, kvCacheMaxLen, headDim] float32
 * - kv_cache_v_0..N: [1, numKvHeads, headDim, kvCacheMaxLen] float32 (transposed)
 *
 * Outputs:
 * - logits: [1, 1, vocabSize] float32
 * - updated kv_cache_k/v for each layer
 */
class LiteRTInferenceEngine(
    modelPath: String,
    private val tokenizer: HFTokenizer,
    private val model: Model
) {
    companion object {
        private const val TAG = "LiteRTEngine"
    }

    private val interpreter: Interpreter
    private var gpuDelegate: GpuDelegate? = null

    // KV cache buffers (allocated once, reused)
    private val kvCacheK: Array<ByteBuffer>
    private val kvCacheV: Array<ByteBuffer>
    private var currentPos = 0

    init {
        Log.i(TAG, "Loading model: $modelPath")
        val modelFile = File(modelPath)

        // Try GPU delegate first, fall back to CPU
        interpreter = try {
            val gpuOpts = Interpreter.Options().apply {
                numThreads = 4
                val delegate = GpuDelegate()
                addDelegate(delegate)
                gpuDelegate = delegate
            }
            val interp = Interpreter(modelFile, gpuOpts)
            Log.i(TAG, "Model loaded with GPU delegate")
            interp
        } catch (gpuError: Exception) {
            Log.w(TAG, "GPU failed (${gpuError.message}), falling back to CPU")
            gpuDelegate?.close()
            gpuDelegate = null
            val cpuOpts = Interpreter.Options().apply { numThreads = 4 }
            val interp = Interpreter(modelFile, cpuOpts)
            Log.i(TAG, "Model loaded with CPU")
            interp
        }


        // Log signature info
        val signatureKeys = interpreter.signatureKeys
        Log.i(TAG, "Signature keys: ${signatureKeys?.toList()}")
        if (signatureKeys != null && signatureKeys.isNotEmpty()) {
            val sig = signatureKeys[0]
            val inputs = interpreter.getSignatureInputs(sig)
            val outputs = interpreter.getSignatureOutputs(sig)
            Log.i(TAG, "Signature '$sig' inputs: ${inputs?.toList()}")
            Log.i(TAG, "Signature '$sig' outputs: ${outputs?.toList()}")
        }

        // Allocate KV cache buffers
        val kvK = model.numKvHeads * model.kvCacheMaxLen * model.headDim * 4 // float32
        val kvV = model.numKvHeads * model.headDim * model.kvCacheMaxLen * 4 // float32 (transposed)

        kvCacheK = Array(model.numLayers) {
            ByteBuffer.allocateDirect(kvK).order(ByteOrder.nativeOrder())
        }
        kvCacheV = Array(model.numLayers) {
            ByteBuffer.allocateDirect(kvV).order(ByteOrder.nativeOrder())
        }

        resetKVCache()
        Log.i(TAG, "KV cache allocated: ${model.numLayers} layers, K=${kvK/1024}KB, V=${kvV/1024}KB each")
    }

    private fun resetKVCache() {
        kvCacheK.forEach { buf ->
            buf.rewind()
            while (buf.hasRemaining()) buf.putFloat(0f)
            buf.rewind()
        }
        kvCacheV.forEach { buf ->
            buf.rewind()
            while (buf.hasRemaining()) buf.putFloat(0f)
            buf.rewind()
        }
        currentPos = 0
    }

    /**
     * Create a causal attention mask for the given position.
     * Shape: [1, 1, 1, kvCacheMaxLen]
     * -inf for future positions, 0 for valid positions
     */
    private fun createMask(pos: Int): ByteBuffer {
        val maskSize = 1 * 1 * 1 * model.kvCacheMaxLen * 4
        val mask = ByteBuffer.allocateDirect(maskSize).order(ByteOrder.nativeOrder())
        for (i in 0 until model.kvCacheMaxLen) {
            mask.putFloat(if (i <= pos) 0f else Float.NEGATIVE_INFINITY)
        }
        mask.rewind()
        return mask
    }

    /**
     * Run a single decode step: feed one token, get logits for next token.
     */
    private fun decodeStep(tokenId: Int, pos: Int): FloatArray {
        val signatureKey = interpreter.signatureKeys?.firstOrNull() ?: "decode"

        // Prepare inputs
        val tokenBuf = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        tokenBuf.putInt(tokenId)
        tokenBuf.rewind()

        val posBuf = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        posBuf.putInt(pos)
        posBuf.rewind()

        val mask = createMask(pos)

        // Build input map
        val inputs = mutableMapOf<String, Any>()
        inputs["tokens"] = tokenBuf
        inputs["input_pos"] = posBuf
        inputs["mask"] = mask

        for (i in 0 until model.numLayers) {
            kvCacheK[i].rewind()
            kvCacheV[i].rewind()
            inputs["kv_cache_k_$i"] = kvCacheK[i]
            inputs["kv_cache_v_$i"] = kvCacheV[i]
        }

        // Build output map  
        val logitsSize = 1 * 1 * model.vocabSize * 4
        val logitsBuf = ByteBuffer.allocateDirect(logitsSize).order(ByteOrder.nativeOrder())

        val outputs = mutableMapOf<String, Any>()
        outputs["logits"] = logitsBuf

        // Allocate output KV cache buffers (interpreter updates them)
        val outKvK = Array(model.numLayers) {
            ByteBuffer.allocateDirect(kvCacheK[0].capacity()).order(ByteOrder.nativeOrder())
        }
        val outKvV = Array(model.numLayers) {
            ByteBuffer.allocateDirect(kvCacheV[0].capacity()).order(ByteOrder.nativeOrder())
        }
        for (i in 0 until model.numLayers) {
            outputs["kv_cache_k_$i"] = outKvK[i]
            outputs["kv_cache_v_$i"] = outKvV[i]
        }

        // Run inference
        interpreter.runSignature(inputs, outputs, signatureKey)

        // Copy updated KV caches back
        for (i in 0 until model.numLayers) {
            outKvK[i].rewind()
            kvCacheK[i].rewind()
            kvCacheK[i].put(outKvK[i])
            kvCacheK[i].rewind()

            outKvV[i].rewind()
            kvCacheV[i].rewind()
            kvCacheV[i].put(outKvV[i])
            kvCacheV[i].rewind()
        }

        // Extract logits
        logitsBuf.rewind()
        val logits = FloatArray(model.vocabSize)
        logitsBuf.asFloatBuffer().get(logits)

        return logits
    }

    /**
     * Sample a token from logits using temperature + top-k + top-p.
     */
    private fun sampleToken(logits: FloatArray): Int {
        val temp = model.temperature.coerceAtLeast(0.01f)

        // Apply temperature
        val scaled = FloatArray(logits.size) { logits[it] / temp }

        // Top-K: keep only top K values
        val indexed = scaled.mapIndexed { idx, v -> idx to v }
            .sortedByDescending { it.second }
            .take(model.topK)

        // Top-P (nucleus sampling)
        val maxLogit = indexed.first().second
        val expValues = indexed.map { (idx, v) -> idx to Math.exp((v - maxLogit).toDouble()).toFloat() }
        val sum = expValues.sumOf { it.second.toDouble() }.toFloat()
        val probs = expValues.map { (idx, v) -> idx to v / sum }

        // Cumulative sum for top-p
        var cumSum = 0f
        val filtered = mutableListOf<Pair<Int, Float>>()
        for ((idx, p) in probs) {
            cumSum += p
            filtered.add(idx to p)
            if (cumSum >= model.topP) break
        }

        // Renormalize
        val filteredSum = filtered.sumOf { it.second.toDouble() }.toFloat()
        val normalized = filtered.map { (idx, p) -> idx to p / filteredSum }

        // Sample
        val r = Math.random().toFloat()
        var acc = 0f
        for ((idx, p) in normalized) {
            acc += p
            if (acc >= r) return idx
        }

        return normalized.last().first
    }

    /**
     * Generate text from a prompt, streaming partial results via callback.
     */
    fun generateResponse(
        prompt: String,
        maxTokens: Int = 64,
        onPartialResult: ((String) -> Unit)? = null
    ): String {
        resetKVCache()

        val inputTokens = tokenizer.encode(prompt)
        Log.i(TAG, "Input tokens (${inputTokens.size}): ${inputTokens.take(10)}...")

        // Check if we have room
        if (inputTokens.size >= model.kvCacheMaxLen) {
            Log.w(TAG, "Prompt too long (${inputTokens.size} tokens, max ${model.kvCacheMaxLen})")
            return "Error: Prompt too long. Please use a shorter prompt."
        }

        // Prefill: feed all input tokens one by one
        var lastLogits: FloatArray? = null
        for (i in inputTokens.indices) {
            lastLogits = decodeStep(inputTokens[i], i)
            currentPos = i
        }

        if (lastLogits == null) return ""

        // Decode: generate tokens autoregressively
        val generatedTokens = mutableListOf<Int>()
        val sb = StringBuilder()
        var nextToken = sampleToken(lastLogits)

        val maxGenLen = minOf(maxTokens, model.kvCacheMaxLen - inputTokens.size - 1)

        for (step in 0 until maxGenLen) {
            if (nextToken == HFTokenizer.EOS_TOKEN_ID) break

            generatedTokens.add(nextToken)

            val decoded = tokenizer.decodeToken(nextToken)
            sb.append(decoded)
            onPartialResult?.invoke(decoded)

            currentPos++
            if (currentPos >= model.kvCacheMaxLen - 1) break

            val logits = decodeStep(nextToken, currentPos)
            nextToken = sampleToken(logits)
        }

        Log.i(TAG, "Generated ${generatedTokens.size} tokens")
        return sb.toString()
    }

    fun close() {
        try {
            interpreter.close()
            gpuDelegate?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing engine: ${e.message}")
        }
    }
}
