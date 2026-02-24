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
        url = "https://huggingface.co/unsloth/medgemma-1.5-4b-it-GGUF/resolve/main/medgemma-1.5-4b-it-Q4_K_M.gguf",
        mmprojPath = "mmproj-F16.gguf",
        mmprojUrl = "https://huggingface.co/unsloth/medgemma-1.5-4b-it-GGUF/resolve/main/mmproj-F16.gguf",
        licenseUrl = "https://huggingface.co/unsloth/medgemma-1.5-4b-it-GGUF",
        needsAuth = true,
        thinking = false,
        temperature = 0.7f,
        topK = 40,
        topP = 0.95f
    ),
    GEMMA_3_1B_IT_GPU(
        path = "medgemma-1.5-4b-it-Q4_K_M.gguf",
        url = "https://huggingface.co/unsloth/medgemma-1.5-4b-it-GGUF/resolve/main/medgemma-1.5-4b-it-Q4_K_M.gguf",
        mmprojPath = "mmproj-F16.gguf",
        mmprojUrl = "https://huggingface.co/unsloth/medgemma-1.5-4b-it-GGUF/resolve/main/mmproj-F16.gguf",
        licenseUrl = "https://huggingface.co/unsloth/medgemma-1.5-4b-it-GGUF",
        needsAuth = true,
        thinking = false,
        temperature = 0.7f,
        topK = 40,
        topP = 0.95f
    ),
    GEMMA_3_1B_IT_NPU(
        path = "medgemma-1.5-4b-it-Q4_K_M.gguf",
        url = "https://huggingface.co/unsloth/medgemma-1.5-4b-it-GGUF/resolve/main/medgemma-1.5-4b-it-Q4_K_M.gguf",
        mmprojPath = "mmproj-F16.gguf",
        mmprojUrl = "https://huggingface.co/unsloth/medgemma-1.5-4b-it-GGUF/resolve/main/mmproj-F16.gguf",
        licenseUrl = "https://huggingface.co/unsloth/medgemma-1.5-4b-it-GGUF",
        needsAuth = true,
        thinking = false,
        temperature = 0.7f,
        topK = 40,
        topP = 0.95f
    ),
}

/**
 * Available GGUF quantizations from unsloth/medgemma-1.5-4b-it-GGUF
 */
data class HfGgufFile(
    val fileName: String,
    val displayName: String,
    val sizeLabel: String,
    val url: String
)

object HfModelRepository {
    const val REPO_URL = "https://huggingface.co/unsloth/medgemma-1.5-4b-it-GGUF"
    const val REPO_ID = "unsloth/medgemma-1.5-4b-it-GGUF"
    private const val BASE = "https://huggingface.co/unsloth/medgemma-1.5-4b-it-GGUF/resolve/main"

    val availableModels = listOf(
        HfGgufFile("medgemma-1.5-4b-it-Q2_K.gguf", "Q2_K", "~1.7 GB", "$BASE/medgemma-1.5-4b-it-Q2_K.gguf"),
        HfGgufFile("medgemma-1.5-4b-it-Q3_K_M.gguf", "Q3_K_M", "~2.0 GB", "$BASE/medgemma-1.5-4b-it-Q3_K_M.gguf"),
        HfGgufFile("medgemma-1.5-4b-it-Q4_K_M.gguf", "Q4_K_M (recommended)", "~2.5 GB", "$BASE/medgemma-1.5-4b-it-Q4_K_M.gguf"),
        HfGgufFile("medgemma-1.5-4b-it-Q5_K_M.gguf", "Q5_K_M", "~2.9 GB", "$BASE/medgemma-1.5-4b-it-Q5_K_M.gguf"),
        HfGgufFile("medgemma-1.5-4b-it-Q6_K.gguf", "Q6_K", "~3.3 GB", "$BASE/medgemma-1.5-4b-it-Q6_K.gguf"),
        HfGgufFile("medgemma-1.5-4b-it-Q8_0.gguf", "Q8_0", "~4.2 GB", "$BASE/medgemma-1.5-4b-it-Q8_0.gguf"),
        HfGgufFile("medgemma-1.5-4b-it-F16.gguf", "F16 (full)", "~8.1 GB", "$BASE/medgemma-1.5-4b-it-F16.gguf"),
    )

    val visionEncoder = HfGgufFile(
        "mmproj-F16.gguf",
        "Vision Encoder (mmproj-F16)",
        "~812 MB",
        "$BASE/mmproj-F16.gguf"
    )
}
