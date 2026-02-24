package com.google.mediapipe.examples.llminference

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * LiteRT-based inference engine that loads raw .tflite models
 * converted via litert_torch.generative.converter and runs
 * autoregressive text generation.
 *
 * Uses memory-mapped temp files for KV cache to avoid OOM from
 * ByteBuffer.allocateDirect() (which goes through JVM tracked allocation).
 */
class LiteRTInferenceEngine(
    modelPath: String,
    private val tokenizer: HFTokenizer,
    private val model: Model,
    private val context: Context
) {
    companion object {
        private const val TAG = "LiteRTEngine"
        // Thinking marker token IDs (from tokenizer.json)
        const val TOKEN_THINKING_START = 100   // <unused94>
        const val TOKEN_THINKING_END = 101     // <unused95>
        const val TOKEN_THOUGHT_WORD = 3305    // " thought" (note leading space)
        const val TOKEN_NEWLINE = 108          // "\n"
    }

    private val interpreter: Interpreter
    private var gpuDelegate: GpuDelegate? = null
    private var useGpu = false  // true only when GPU delegate fully loaded

    // KV cache: 2 sets (ping-pong), memory-mapped via temp files
    private val kvK = Array(2) { arrayOfNulls<MappedByteBuffer>(model.numLayers) }
    private val kvV = Array(2) { arrayOfNulls<MappedByteBuffer>(model.numLayers) }
    private val kvFiles = mutableListOf<RandomAccessFile>() // keep refs to avoid GC
    private var activeBuf = 0

    // Pre-allocated small buffers
    private val tokenBuf: ByteBuffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
    private val posBuf: ByteBuffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
    private val maskBuf: ByteBuffer
    private val logitsBuf: ByteBuffer
    private var currentPos = 0

    // Buffer sizes
    private val kvKSize: Int
    private val kvVSize: Int

    // Prefill signature — key and window size auto-detected from model at load time
    private var prefillSigKey: String? = null   // e.g. "prefill_128", "prefill_512"
    private var prefillSeqLen: Int = 0          // parsed from signature name
    private lateinit var prefillTokensBuf: ByteBuffer
    private lateinit var prefillPosBuf: ByteBuffer
    private lateinit var prefillMaskBuf: ByteBuffer

    init {
        val engineStart = System.currentTimeMillis()
        Log.i(TAG, "Loading model: $modelPath")
        val modelFile = File(modelPath)

        // Interpreter loading strategy (3-level fallback):
        //   1. GPU delegate — best performance if model uses standard TFLite ops (e.g. mobile_int8)
        //   2. CPU with XNNPACK — correct dequantization for INT8 models (matches Python TFLite behavior)
        //   3. CPU without XNNPACK — required for STABLEHLO_COMPOSITE op models (e.g. tpu_q4/q8)
        val numCpuThreads = Runtime.getRuntime().availableProcessors().coerceAtLeast(4)
        interpreter = try {
            // Try GPU first — succeeds if model uses standard TFLite ops (e.g. mobile_int8 variant)
            val gpuOpts = Interpreter.Options().apply {
                numThreads = numCpuThreads
                val options = GpuDelegate.Options().apply {
                    setQuantizedModelsAllowed(true)
                }
                val delegate = GpuDelegate(options)
                addDelegate(delegate)
                gpuDelegate = delegate
            }
            val interp = Interpreter(modelFile, gpuOpts)
            Log.i(TAG, "✅ Model loaded with GPU delegate")
            useGpu = true
            interp
        } catch (gpuError: Exception) {
            Log.w(TAG, "GPU unavailable: ${gpuError.message?.take(120)}")
            gpuDelegate?.close()
            gpuDelegate = null
            // Try CPU with XNNPACK ON first — this is the correct backend for INT8/mobile models
            // (Python TFLite uses XNNPACK by default and produces correct results).
            // Only fall back to XNNPACK-off for STABLEHLO_COMPOSITE op models (tpu_q4/q8).
            try {
                val xnnOpts = Interpreter.Options().apply {
                    numThreads = numCpuThreads
                    setUseXNNPACK(true)
                }
                val interp = Interpreter(modelFile, xnnOpts)
                Log.i(TAG, "✅ Model loaded on CPU (XNNPACK on, $numCpuThreads threads)")
                interp
            } catch (xnnError: Exception) {
                Log.w(TAG, "XNNPACK failed: ${xnnError.message?.take(120)}")
                // Final fallback: CPU without XNNPACK — required for STABLEHLO_COMPOSITE op models
                val cpuOpts = Interpreter.Options().apply {
                    numThreads = numCpuThreads
                    setUseXNNPACK(false)
                }
                val interp = Interpreter(modelFile, cpuOpts)
                Log.i(TAG, "✅ Model loaded on CPU (XNNPACK off, $numCpuThreads threads) — LiteRT native backend")
                interp
            }
        }

        // Log signature info and auto-detect prefill signature
        val signatureKeys = interpreter.signatureKeys
        Log.i(TAG, "Signature keys: ${signatureKeys?.toList()}")

        // Log all input/output tensor shapes and data types for the decode signature
        try {
            for (i in 0 until interpreter.inputTensorCount) {
                val t = interpreter.getInputTensor(i)
                Log.i(TAG, "Input tensor $i: name=${t.name()}, type=${t.dataType()}, shape=${t.shape().toList()}, bytes=${t.numBytes()}")
            }
            for (i in 0 until interpreter.outputTensorCount) {
                val t = interpreter.getOutputTensor(i)
                Log.i(TAG, "Output tensor $i: name=${t.name()}, type=${t.dataType()}, shape=${t.shape().toList()}, bytes=${t.numBytes()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not log tensor details: ${e.message}")
        }

        signatureKeys?.forEach { sig ->
            val inputs = interpreter.getSignatureInputs(sig)
            val outputs = interpreter.getSignatureOutputs(sig)
            Log.i(TAG, "Signature '$sig' inputs: ${inputs?.toList()}")
            Log.i(TAG, "Signature '$sig' outputs: ${outputs?.toList()}")
            // Detect prefill_N signature (e.g. prefill_128, prefill_512, prefill_1152)
            if (sig.startsWith("prefill_")) {
                val n = sig.removePrefix("prefill_").toIntOrNull()
                if (n != null && n > 0) {
                    prefillSigKey = sig
                    prefillSeqLen = n
                    Log.i(TAG, "Detected prefill signature: '$sig' (window=$n)")
                }
            }
        }
        // Calculate buffer sizes
        kvKSize = model.numKvHeads * model.kvCacheMaxLen * model.headDim * 4
        kvVSize = model.numKvHeads * model.headDim * model.kvCacheMaxLen * 4
        maskBuf = ByteBuffer.allocateDirect(model.kvCacheMaxLen * 4).order(ByteOrder.nativeOrder())
        logitsBuf = ByteBuffer.allocateDirect(model.vocabSize * 4).order(ByteOrder.nativeOrder())
        // Prefill buffers — allocated only if a prefill signature was found
        if (prefillSigKey != null) {
            prefillTokensBuf = ByteBuffer.allocateDirect(prefillSeqLen * 4).order(ByteOrder.nativeOrder())
            prefillPosBuf    = ByteBuffer.allocateDirect(prefillSeqLen * 4).order(ByteOrder.nativeOrder())
            prefillMaskBuf   = ByteBuffer.allocateDirect(prefillSeqLen * model.kvCacheMaxLen * 4).order(ByteOrder.nativeOrder())
        }

        // Allocate KV cache via memory-mapped temp files
        // This bypasses the JVM DirectByteBuffer tracking limit
        val allocStart = System.currentTimeMillis()
        Log.i(TAG, "Allocating KV cache via mmap: 2 sets x ${model.numLayers} layers x ${kvKSize / 1024}KB")
        val cacheDir = context.cacheDir

        for (set in 0..1) {
            for (layer in 0 until model.numLayers) {
                // K cache
                val kFile = File(cacheDir, "kvcache_k_${set}_$layer.tmp")
                val kRaf = RandomAccessFile(kFile, "rw")
                kRaf.setLength(kvKSize.toLong())
                kvK[set][layer] = kRaf.channel.map(FileChannel.MapMode.READ_WRITE, 0, kvKSize.toLong())
                kvK[set][layer]!!.order(ByteOrder.nativeOrder())
                kvFiles.add(kRaf)
                kFile.deleteOnExit()

                // V cache
                val vFile = File(cacheDir, "kvcache_v_${set}_$layer.tmp")
                val vRaf = RandomAccessFile(vFile, "rw")
                vRaf.setLength(kvVSize.toLong())
                kvV[set][layer] = vRaf.channel.map(FileChannel.MapMode.READ_WRITE, 0, kvVSize.toLong())
                kvV[set][layer]!!.order(ByteOrder.nativeOrder())
                kvFiles.add(vRaf)
                vFile.deleteOnExit()
            }
            Log.i(TAG, "KV cache set $set allocated via mmap in ${System.currentTimeMillis() - allocStart}ms")
        }

        resetKVCache()
        Log.i(TAG, "Model initialized successfully: ${model.path}")
        Log.i(TAG, "Engine ready (Total creation time: ${System.currentTimeMillis() - engineStart}ms)")

        // Quick self-test: feed BOS token at position 0, check logits range
        try {
            val testLogits = decodeStep(HFTokenizer.BOS_TOKEN_ID, 0)
            val testMin = testLogits.min()
            val testMax = testLogits.max()
            val testArgmax = testLogits.indices.maxByOrNull { testLogits[it] } ?: -1
            Log.i(TAG, "[SELF-TEST] BOS logits: min=$testMin max=$testMax argmax=$testArgmax")
            Log.i(TAG, "[SELF-TEST] First 10 logits: ${testLogits.take(10)}")
            Log.i(TAG, "[SELF-TEST] Logits around token 3617 (package): ${testLogits[3617]}")
            Log.i(TAG, "[SELF-TEST] Logits around token 818 (The): ${testLogits[818]}")
            // Reset KV cache after self-test so inference starts clean
            resetKVCache()
        } catch (e: Exception) {
            Log.w(TAG, "[SELF-TEST] Failed: ${e.message}")
        }
    }

    private fun resetKVCache() {
        for (set in 0..1) {
            for (layer in 0 until model.numLayers) {
                val k = kvK[set][layer]!!
                k.rewind()
                // Zero fill using bulk array
                val zeros = ByteArray(minOf(8192, kvKSize))
                var remaining = kvKSize
                while (remaining > 0) {
                    val chunk = minOf(zeros.size, remaining)
                    k.put(zeros, 0, chunk)
                    remaining -= chunk
                }
                k.rewind()

                val v = kvV[set][layer]!!
                v.rewind()
                remaining = kvVSize
                while (remaining > 0) {
                    val chunk = minOf(zeros.size, remaining)
                    v.put(zeros, 0, chunk)
                    remaining -= chunk
                }
                v.rewind()
            }
        }
        activeBuf = 0
        currentPos = 0
    }

    /**
     * Run a single decode step using ping-pong mmap buffers.
     */
    private fun decodeStep(tokenId: Int, pos: Int): FloatArray {
        val signatureKey = "decode"  // Must be explicit — firstOrNull() could return prefill_N
        val inBuf = activeBuf
        val outBuf = 1 - activeBuf

        tokenBuf.rewind(); tokenBuf.putInt(tokenId); tokenBuf.rewind()
        posBuf.rewind(); posBuf.putInt(pos); posBuf.rewind()

        maskBuf.rewind()
        for (i in 0 until model.kvCacheMaxLen) {
            // Use -1e9 instead of -Infinity to avoid NaN in StableHLO quantized softmax
            maskBuf.putFloat(if (i <= pos) 0f else -1e9f)
        }
        maskBuf.rewind()

        val inputs = mutableMapOf<String, Any>()
        inputs["tokens"] = tokenBuf
        inputs["input_pos"] = posBuf
        inputs["mask"] = maskBuf

        for (i in 0 until model.numLayers) {
            kvK[inBuf][i]!!.rewind()
            kvV[inBuf][i]!!.rewind()
            inputs["kv_cache_k_$i"] = kvK[inBuf][i]!!
            inputs["kv_cache_v_$i"] = kvV[inBuf][i]!!
        }

        logitsBuf.rewind()
        val outputs = mutableMapOf<String, Any>()
        outputs["logits"] = logitsBuf

        for (i in 0 until model.numLayers) {
            kvK[outBuf][i]!!.rewind()
            kvV[outBuf][i]!!.rewind()
            outputs["kv_cache_k_$i"] = kvK[outBuf][i]!!
            outputs["kv_cache_v_$i"] = kvV[outBuf][i]!!
        }

        interpreter.runSignature(inputs, outputs, signatureKey)
        activeBuf = outBuf

        logitsBuf.rewind()
        val logits = FloatArray(model.vocabSize)
        logitsBuf.asFloatBuffer().get(logits)
        return logits
    }

    /**
     * Run a full prefill using the prefill_N batch signature (window auto-detected at load time).
     * Fills the KV cache with all input tokens in one forward pass (no logits output).
     * Returns true on success; caller must follow with decodeStep() to obtain valid logits.
     * Only beneficial on GPU. On CPU we skip it (prefillSigKey is ignored) to avoid 512× overhead.
     */
    private fun prefillBatch(tokens: IntArray): Boolean {
        if (!useGpu) return false          // on CPU the batch pass is slower than the decode loop
        val sigKey = prefillSigKey ?: return false
        if (tokens.size > prefillSeqLen) return false
        // prefill_512 processes 512 tokens in ONE pass — efficient on GPU (parallel rows) but
        // ~(512/N)× SLOWER than decode loop on CPU (serial computation, full matrix regardless of padding).
        // Only use it on GPU.
        if (!useGpu) return false

        val numTokens = tokens.size
        val inBuf = activeBuf
        val outBuf = 1 - activeBuf

        // Padded token sequence [prefillSeqLen]
        prefillTokensBuf.rewind()
        for (i in 0 until prefillSeqLen) {
            prefillTokensBuf.putInt(if (i < numTokens) tokens[i] else 0)
        }
        prefillTokensBuf.rewind()

        // Positions 0..numTokens-1, then 0 for padding
        prefillPosBuf.rewind()
        for (i in 0 until prefillSeqLen) {
            prefillPosBuf.putInt(if (i < numTokens) i else 0)
        }
        prefillPosBuf.rewind()

        // Causal attention mask [prefillSeqLen × kvCacheMaxLen]
        // Row i sees positions 0..i (if i < numTokens), padding rows see nothing
        prefillMaskBuf.rewind()
        for (q in 0 until prefillSeqLen) {
            for (k in 0 until model.kvCacheMaxLen) {
                val visible = (q < numTokens) && (k <= q)
                prefillMaskBuf.putFloat(if (visible) 0f else -1e9f)
            }
        }
        prefillMaskBuf.rewind()

        val inputs = mutableMapOf<String, Any>()
        inputs["tokens"] = prefillTokensBuf
        inputs["input_pos"] = prefillPosBuf
        inputs["mask"] = prefillMaskBuf
        for (i in 0 until model.numLayers) {
            kvK[inBuf][i]!!.rewind(); kvV[inBuf][i]!!.rewind()
            inputs["kv_cache_k_$i"] = kvK[inBuf][i]!!
            inputs["kv_cache_v_$i"] = kvV[inBuf][i]!!
        }

        // NOTE: prefill_512 outputs ONLY kv caches, no logits tensor.
        // We just fill the KV cache and return true; caller must do one decodeStep for logits.
        val outputs = mutableMapOf<String, Any>()
        for (i in 0 until model.numLayers) {
            kvK[outBuf][i]!!.rewind(); kvV[outBuf][i]!!.rewind()
            outputs["kv_cache_k_$i"] = kvK[outBuf][i]!!
            outputs["kv_cache_v_$i"] = kvV[outBuf][i]!!
        }

        return try {
            interpreter.runSignature(inputs, outputs, sigKey)
            activeBuf = outBuf
            currentPos = numTokens - 1
            Log.i(TAG, "$sigKey KV cache filled for $numTokens tokens")
            true
        } catch (e: Exception) {
            Log.w(TAG, "prefill_512 failed (${e.message}), will use decode loop")
            false
        }
    }

    private fun sampleToken(logits: FloatArray): Int {
        val temp = model.temperature.coerceAtLeast(0.01f)
        val scaled = FloatArray(logits.size) { logits[it] / temp }

        val indexed = scaled.mapIndexed { idx, v -> idx to v }
            .sortedByDescending { it.second }
            .take(model.topK)

        val maxLogit = indexed.first().second
        val expValues = indexed.map { (idx, v) -> idx to Math.exp((v - maxLogit).toDouble()).toFloat() }
        val sum = expValues.sumOf { it.second.toDouble() }.toFloat()
        val probs = expValues.map { (idx, v) -> idx to v / sum }

        var cumSum = 0f
        val filtered = mutableListOf<Pair<Int, Float>>()
        for ((idx, p) in probs) {
            cumSum += p
            filtered.add(idx to p)
            if (cumSum >= model.topP) break
        }

        val filteredSum = filtered.sumOf { it.second.toDouble() }.toFloat()
        val normalized = filtered.map { (idx, p) -> idx to p / filteredSum }

        val r = Math.random().toFloat()
        var acc = 0f
        for ((idx, p) in normalized) {
            acc += p
            if (acc >= r) return idx
        }
        return normalized.last().first
    }

    // Thinking mode: when skipThinking=true, we prefill <unused94>thought\n<unused95>
    // to make the model skip its internal chain-of-thought and go straight to the answer.
    var skipThinking: Boolean = true

    /**
     * Prefill "skip thinking" tokens into the KV cache so the model sees thinking
     * as already completed and jumps straight to the response.
     * Equivalent to GGUF's processUserPrompt() that prefills "<unused94>thought\n<unused95>".
     * Returns the logits from the final token (<unused95>), or null if KV cache is full.
     */
    private fun prefillSkipThinking(): FloatArray? {
        // These tokens tell the model "thinking happened, here's the empty result"
        val skipTokens = intArrayOf(TOKEN_THINKING_START, TOKEN_THOUGHT_WORD, TOKEN_NEWLINE, TOKEN_THINKING_END)
        var logits: FloatArray? = null
        for (token in skipTokens) {
            currentPos++
            if (currentPos >= model.kvCacheMaxLen - 1) return null
            logits = decodeStep(token, currentPos)
        }
        Log.i(TAG, "Prefilled skip-thinking tokens at positions ${currentPos - skipTokens.size + 1}..$currentPos")
        return logits
    }

    fun generateResponse(
        prompt: String,
        maxTokens: Int = 512,
        onPartialResult: ((String) -> Unit)? = null
    ): String {
        Log.i(TAG, "=== INFERENCE START ===")
        Log.i(TAG, "Loaded Model: ${model.path}")
        Log.i(TAG, "Prompt (${prompt.length} chars): ${prompt.take(120)}...")
        Log.i(TAG, "skipThinking=$skipThinking")
        resetKVCache()

        val encodeStart = System.currentTimeMillis()
        val inputTokens = tokenizer.encode(prompt)
        Log.i(TAG, "Tokenized ${inputTokens.size} input tokens in ${System.currentTimeMillis() - encodeStart}ms")

        if (inputTokens.size >= model.kvCacheMaxLen) {
            Log.w(TAG, "Prompt too long (${inputTokens.size} tokens, max ${model.kvCacheMaxLen})")
            return "Error: Prompt too long. Please use a shorter prompt."
        }

        // --- PREFILL PHASE ---
        var lastLogits: FloatArray? = null
        val prefillStart = System.currentTimeMillis()

        val prefillOk = prefillBatch(inputTokens.toIntArray())
        if (prefillOk) {
            lastLogits = decodeStep(inputTokens.last(), inputTokens.size - 1)
            currentPos = inputTokens.size - 1
            Log.i(TAG, "Batch prefill + 1 decode: ${inputTokens.size} tokens in ${System.currentTimeMillis() - prefillStart}ms")
        } else {
            for (i in inputTokens.indices) {
                val stepStart = System.nanoTime()
                lastLogits = decodeStep(inputTokens[i], i)
                currentPos = i
                val stepTime = (System.nanoTime() - stepStart) / 1_000_000
                if (i % 10 == 0 || i == inputTokens.lastIndex) {
                    Log.i(TAG, "Prefill token $i/${inputTokens.size} took ${stepTime}ms")
                }
            }
            Log.i(TAG, "Decode-loop prefill: ${inputTokens.size} tokens in ${System.currentTimeMillis() - prefillStart}ms")
        }

        if (lastLogits == null) return ""

        // --- SKIP THINKING PHASE ---
        // When skipThinking=true, prefill the thinking-skip tokens so the model
        // jumps directly to the response (saves ~100-500 thinking tokens).
        // Like GGUF's processUserPrompt(), we feed: <unused94> thought \n <unused95>
        if (skipThinking) {
            val skipLogits = prefillSkipThinking()
            if (skipLogits == null) {
                Log.w(TAG, "Skip-thinking failed (KV cache full)")
                return "Error: KV cache too small for prompt + thinking skip."
            }
            lastLogits = skipLogits
            Log.i(TAG, "Skip-thinking done, now at pos=$currentPos")
        }

        val generatedTokens = mutableListOf<Int>()
        val sb = StringBuilder()

        var nextToken = sampleToken(lastLogits)
        val maxGenLen = minOf(maxTokens, model.kvCacheMaxLen - currentPos - 1)
        Log.i(TAG, "Max generation length: $maxGenLen tokens (kvMax=${model.kvCacheMaxLen}, pos=$currentPos)")

        for (step in 0 until maxGenLen) {
            if (nextToken == HFTokenizer.EOS_TOKEN_ID) {
                Log.i(TAG, "[GEN] Step $step: EOS token, stopping")
                break
            }
            generatedTokens.add(nextToken)

            val decoded = tokenizer.decodeToken(nextToken)
            sb.append(decoded)
            if (step < 5 || step % 20 == 0) {
                Log.i(TAG, "[GEN] Step $step: tokenId=$nextToken decoded=\"$decoded\"")
            }
            onPartialResult?.invoke(decoded)

            currentPos++
            if (currentPos >= model.kvCacheMaxLen - 1) {
                Log.i(TAG, "KV cache full at step $step, stopping generation")
                break
            }

            val stepStart = System.nanoTime()
            val logits = decodeStep(nextToken, currentPos)
            val stepTime = (System.nanoTime() - stepStart) / 1_000_000

            if (step < 3 || step % 50 == 0) {
                val argmax = logits.indices.maxByOrNull { logits[it] } ?: -1
                Log.i(TAG, "[LOGITS-${step+1}] min=${logits.min()} max=${logits.max()} argmax=$argmax time=${stepTime}ms")
            }
            nextToken = sampleToken(logits)
        }

        Log.i(TAG, "Generated ${generatedTokens.size} tokens in ${sb.length} chars")
        Log.i(TAG, "Output preview: \"${sb.toString().take(200)}\"")
        return sb.toString()
    }

    fun close() {
        try {
            interpreter.close()
            gpuDelegate?.close()
            // Clean up temp files
            kvFiles.forEach { it.close() }
            val cacheDir = context.cacheDir
            cacheDir.listFiles()?.filter { it.name.startsWith("kvcache_") }?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing engine: ${e.message}")
        }
    }
}
