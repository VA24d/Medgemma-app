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
    }

    private val interpreter: Interpreter
    private var gpuDelegate: GpuDelegate? = null

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

    init {
        val engineStart = System.currentTimeMillis()
        Log.i(TAG, "Loading model: $modelPath")
        val modelFile = File(modelPath)

        // Try GPU first, fall back to CPU
        interpreter = try {
            val gpuOpts = Interpreter.Options().apply {
                numThreads = 4
                val options = GpuDelegate.Options().apply {
                    setQuantizedModelsAllowed(true)
                }
                val delegate = GpuDelegate(options)
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
            val cpuOpts = Interpreter.Options().apply { 
                numThreads = Runtime.getRuntime().availableProcessors()
                setUseXNNPACK(false)
            }
            val interp = Interpreter(modelFile, cpuOpts)
            Log.i(TAG, "Model loaded with CPU (XNNPACK disabled, \${cpuOpts.numThreads} threads)")
            interp
        }

        // Log signature info for ALL signatures
        val signatureKeys = interpreter.signatureKeys
        Log.i(TAG, "Signature keys: ${signatureKeys?.toList()}")
        signatureKeys?.forEach { sig ->
            val inputs = interpreter.getSignatureInputs(sig)
            val outputs = interpreter.getSignatureOutputs(sig)
            Log.i(TAG, "Signature '$sig' inputs: ${inputs?.toList()}")
            Log.i(TAG, "Signature '$sig' outputs: ${outputs?.toList()}")
        }
        // Calculate buffer sizes
        kvKSize = model.numKvHeads * model.kvCacheMaxLen * model.headDim * 4
        kvVSize = model.numKvHeads * model.headDim * model.kvCacheMaxLen * 4
        maskBuf = ByteBuffer.allocateDirect(model.kvCacheMaxLen * 4).order(ByteOrder.nativeOrder())
        logitsBuf = ByteBuffer.allocateDirect(model.vocabSize * 4).order(ByteOrder.nativeOrder())

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
        val signatureKey = interpreter.signatureKeys?.firstOrNull() ?: "decode"
        val inBuf = activeBuf
        val outBuf = 1 - activeBuf

        tokenBuf.rewind(); tokenBuf.putInt(tokenId); tokenBuf.rewind()
        posBuf.rewind(); posBuf.putInt(pos); posBuf.rewind()

        maskBuf.rewind()
        for (i in 0 until model.kvCacheMaxLen) {
            maskBuf.putFloat(if (i <= pos) 0f else Float.NEGATIVE_INFINITY)
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

    fun generateResponse(
        prompt: String,
        maxTokens: Int = 64,
        onPartialResult: ((String) -> Unit)? = null
    ): String {
        Log.i(TAG, "=== INFERENCE START ===")
        Log.i(TAG, "Loaded Model: ${model.path}")
        Log.i(TAG, "Prompt: \n$prompt\n")
        Log.i(TAG, "Prompt length: ${prompt.length} chars")
        resetKVCache()

        val encodeStart = System.currentTimeMillis()
        val inputTokens = tokenizer.encode(prompt)
        Log.i(TAG, "Tokenized ${inputTokens.size} input tokens in ${System.currentTimeMillis() - encodeStart}ms")
        Log.i(TAG, "Input tokens: $inputTokens")

        if (inputTokens.size >= model.kvCacheMaxLen) {
            Log.w(TAG, "Prompt too long (${inputTokens.size} tokens, max ${model.kvCacheMaxLen})")
            return "Error: Prompt too long. Please use a shorter prompt."
        }
        // Prefill: feed all input tokens one by one
        var lastLogits: FloatArray? = null
        val prefillStart = System.currentTimeMillis()
        for (i in inputTokens.indices) {
            val stepStart = System.nanoTime()
            lastLogits = decodeStep(inputTokens[i], i)
            currentPos = i
            val stepTime = (System.nanoTime() - stepStart) / 1_000_000
            if (i % 5 == 0 || i == inputTokens.lastIndex) {
                 Log.i(TAG, "Prefill token $i/${inputTokens.size} took ${stepTime}ms")
            }
        }
        val prefillDuration = System.currentTimeMillis() - prefillStart
        Log.i(TAG, "Prefill complete: ${inputTokens.size} tokens in ${prefillDuration}ms (${prefillDuration/inputTokens.size}ms/token)")
        if (lastLogits == null) return ""

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

            val stepStart = System.nanoTime()
            val logits = decodeStep(nextToken, currentPos)
            val stepTime = (System.nanoTime() - stepStart) / 1_000_000
            Log.i(TAG, "Decode token $step took ${stepTime}ms")
            nextToken = sampleToken(logits)
        }

        Log.i(TAG, "Generated ${generatedTokens.size} tokens")
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
