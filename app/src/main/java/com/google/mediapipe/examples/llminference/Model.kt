package com.google.mediapipe.examples.llminference

// NB: Make sure the filename is *unique* per model you use!
// Weight caching is currently based on filename alone.
enum class Model(
    val path: String,
    val url: String,
    val mmprojPath: String,
    val mmprojUrl: String,
    val licenseUrl: String,
    val needsAuth: Boolean,
    val thinking: Boolean,
    val temperature: Float,
    val topK: Int,
    val topP: Float,
) {
    GEMMA3_1B_IT_CPU(
        path = "medgemma-1.5-4b-it-Q4_K_M.gguf",
        url = "https://huggingface.co/sunset/medgemma-1.5-4b-it-GGUF/resolve/main/medgemma-1.5-4b-it-Q4_K_M.gguf",
        mmprojPath = "medgemma-1.5-4b-it.mmproj-f16.gguf",
        mmprojUrl = "https://huggingface.co/sunset/medgemma-1.5-4b-it-GGUF/resolve/main/medgemma-1.5-4b-it.mmproj-f16.gguf",
        licenseUrl = "https://huggingface.co/sunset/medgemma-1.5-4b-it-GGUF",
        needsAuth = false,
        thinking = false,
        temperature = 0.7f,
        topK = 40,
        topP = 0.95f
    ),
    GEMMA_3_1B_IT_GPU(
        path = "medgemma-1.5-4b-it-Q4_K_M.gguf",
        url = "https://huggingface.co/sunset/medgemma-1.5-4b-it-GGUF/resolve/main/medgemma-1.5-4b-it-Q4_K_M.gguf",
        mmprojPath = "medgemma-1.5-4b-it.mmproj-f16.gguf",
        mmprojUrl = "https://huggingface.co/sunset/medgemma-1.5-4b-it-GGUF/resolve/main/medgemma-1.5-4b-it.mmproj-f16.gguf",
        licenseUrl = "https://huggingface.co/sunset/medgemma-1.5-4b-it-GGUF",
        needsAuth = false,
        thinking = false,
        temperature = 0.7f,
        topK = 40,
        topP = 0.95f
    ),
    GEMMA_3_1B_IT_NPU(
        path = "medgemma-1.5-4b-it-Q4_K_M.gguf",
        url = "https://huggingface.co/sunset/medgemma-1.5-4b-it-GGUF/resolve/main/medgemma-1.5-4b-it-Q4_K_M.gguf",
        mmprojPath = "medgemma-1.5-4b-it.mmproj-f16.gguf",
        mmprojUrl = "https://huggingface.co/sunset/medgemma-1.5-4b-it-GGUF/resolve/main/medgemma-1.5-4b-it.mmproj-f16.gguf",
        licenseUrl = "https://huggingface.co/sunset/medgemma-1.5-4b-it-GGUF",
        needsAuth = false,
        thinking = false,
        temperature = 0.7f,
        topK = 40,
        topP = 0.95f
    ),
}
