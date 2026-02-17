package com.google.mediapipe.examples.llminference


import com.google.mediapipe.tasks.genai.llminference.LlmInference.Backend

// NB: Make sure the filename is *unique* per model you use!
// Weight caching is currently based on filename alone.
enum class Model(
    val path: String,
    val url: String,
    val visionPath: String,
    val visionUrl: String,
    val projectorPath: String,
    val projectorUrl: String,
    val licenseUrl: String,
    val needsAuth: Boolean,
    val preferredBackend: Backend?,
    val thinking: Boolean,
    val temperature: Float,
    val topK: Int,
    val topP: Float,
) {
    GEMMA3_1B_IT_CPU(
        path = "gemma-3-1b-it-cpu.task", 
        url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task",
        visionPath = "",
        visionUrl = "",
        projectorPath = "",
        projectorUrl = "",
        licenseUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT",
        needsAuth = true,
        preferredBackend = Backend.CPU,
        thinking = false,
        temperature = 1.0f,
        topK = 64,
        topP = 0.95f
    ),
    GEMMA_3_1B_IT_GPU(
        path = "gemma-3-1b-it-gpu.task", // Kept simple for reference
        url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task",
        visionPath = "",
        visionUrl = "",
        projectorPath = "",
        projectorUrl = "",
        licenseUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT",
        needsAuth = true,
        preferredBackend = Backend.GPU,
        thinking = false,
        temperature = 1.0f,
        topK = 64,
        topP = 0.95f
    ),
    MEDGEMMA_4B(
        path = "medgemma_4b.tflite",
        url = "https://huggingface.co/megalodon-ml/medgemma_kaggle/resolve/main/models/text/medgemma_4b_mobile_int8_q8_ekv2048.tflite",
        visionPath = "medsiglip_vision_896.tflite",
        visionUrl = "https://huggingface.co/megalodon-ml/medgemma_kaggle/resolve/main/models/vision/medsiglip_vision_896.tflite",
        projectorPath = "multimodal_projector_896.tflite",
        projectorUrl = "https://huggingface.co/megalodon-ml/medgemma_kaggle/resolve/main/models/projector/multimodal_projector_896.tflite",
        licenseUrl = "https://huggingface.co/megalodon-ml/medgemma_kaggle",
        needsAuth = true,
        preferredBackend = Backend.GPU,
        thinking = false,
        temperature = 0.7f,
        topK = 40,
        topP = 0.95f
    ),
}
