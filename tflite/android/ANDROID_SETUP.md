# Android Integration Guide

Reference code for integrating MedGemma TFLite into an Android app.

## Files

- `LiteRTInferenceEngine.kt` - TFLite runtime wrapper
- `HFTokenizer.kt` - HuggingFace tokenizer for Android

## Setup

### 1. Add Dependencies

```kotlin
// build.gradle.kts (app)
dependencies {
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("com.google.code.gson:gson:2.10.1")  // For tokenizer
}
```

### 2. Copy Model & Tokenizer

Place in `app/src/main/assets/`:
```
assets/
├── medgemma_4b_tpu_q8_ekv128.tflite  (~3.9 GB - may need external storage)
├── tokenizer.json
└── config.json
```

**Note:** Large models (>150MB) should be loaded from external storage, not assets.

### 3. Initialize

```kotlin
// In your ViewModel or Service
private lateinit var engine: LiteRTInferenceEngine
private lateinit var tokenizer: HFTokenizer

fun init(context: Context) {
    tokenizer = HFTokenizer(context, "tokenizer.json")
    engine = LiteRTInferenceEngine(
        modelPath = "/path/to/model.tflite",
        kvLen = 128
    )
}
```

### 4. Generate Text

```kotlin
fun generateResponse(prompt: String): String {
    val tokenIds = tokenizer.encode(prompt)
    val outputTokens = engine.generate(tokenIds, maxTokens = 256)
    return tokenizer.decode(outputTokens)
}
```

## Key Concepts

### KV Cache Management
The TFLite model uses a KV cache for efficient autoregressive generation.
- Size depends on model variant (128, 512, or 2048 tokens)
- Must be allocated before inference
- Can reuse between turns for multi-turn chat

### Signature Runner
The model exposes a `decode` signature:
```kotlin
val inputs = mapOf(
    "args" to tokenArray,           // [1, seq_len] INT64
    "attention_mask" to maskArray,  // [1, seq_len] BOOL
    "input_pos" to posArray,        // [1, seq_len] INT64
    "kv" to kvCacheArray           // [34, 2, 1, kv_len, 4, 256] FLOAT32
)
val outputs = runner.run(inputs)
```

### Memory Considerations
- Q8 model requires ~4GB RAM
- Use 16-bit model copy if needed
- Consider streaming responses to avoid OOM

## Full Example

See `Medgemma-app/` in the parent repository for a complete Android implementation including:
- CameraX integration for image capture
- Chat UI with conversation history
- Model loading from external storage
- Background inference with coroutines
