package com.google.mediapipe.examples.llminference

enum class Model(
    val path: String,
    val url: String,
    val visionPath: String,
    val visionUrl: String,
    val projectorPath: String,
    val projectorUrl: String,
    val licenseUrl: String,
    val needsAuth: Boolean,
    val thinking: Boolean,
    val temperature: Float,
    val topK: Int,
    val topP: Float,
    // llama.cpp config
    val contextSize: Int,
    val nThreads: Int,
) {
    MEDGEMMA_4B(
        path = "medgemma-1.5-4b-it-Q4_K_M.gguf",
        url = "https://huggingface.co/unsloth/medgemma-1.5-4b-it-GGUF/resolve/main/medgemma-1.5-4b-it-Q4_K_M.gguf",
        visionPath = "siglip_encoder.tflite",
        visionUrl = "https://huggingface.co/megalodon-ml/medgemma_kaggle/resolve/main/models/vision/medsiglip_vision_896.tflite",
        projectorPath = "projector.tflite",
        projectorUrl = "https://huggingface.co/megalodon-ml/medgemma_kaggle/resolve/main/models/projector/multimodal_projector_896.tflite",
        licenseUrl = "https://huggingface.co/unsloth/medgemma-1.5-4b-it-GGUF",
        needsAuth = false,  // unsloth GGUF is public
        thinking = false,
        temperature = 0.7f,
        topK = 40,
        topP = 0.95f,
        contextSize = 2048,
        nThreads = 4,
    ),
}
