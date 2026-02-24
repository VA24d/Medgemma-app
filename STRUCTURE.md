# Med Veda Project Structure

**MedGemma 4B Edge AI Medical Assistant**

```
Medgemma-app/
├── app/                          # Main Android application
│   └── src/main/
│       ├── java/com/example/medgemmademo/
│       │   ├── MainActivity.kt              # Entry point
│       │   ├── ChatViewModel.kt             # Inference orchestration  
│       │   ├── VernacularEngine.kt          # Medical term simplification
│       │   └── LanguageExtension.kt         # 70+ vernacular mappings
│       └── res/                             # UI resources
│
├── aichatlib/                    # Custom llama.cpp JNI wrapper (ARM)
│   └── src/main/
│       ├── cpp/                             # Native C++ code
│       └── java/com/arm/aichat/
│           └── AiChatLib.kt                 # JNI interface
│
├── tflite/                       # ★ Custom TFLite Conversion Pipeline (85+ hrs)
│   ├── CONTRIBUTIONS.md                     # Engineering accomplishments
│   ├── conversion/
│   │   ├── convert_text_decoder.py          # Text decoder (489 lines)
│   │   ├── convert_vision_896.py            # Vision encoder (493 lines)
│   │   ├── convert_vision_448_local.py      # Mobile-optimized vision
│   │   └── notebooks/                       # Kaggle training notebooks
│   ├── android/
│   │   ├── LiteRTInferenceEngine.kt         # Android inference (538 lines)
│   │   └── HFTokenizer.kt                   # Custom tokenizer (222 lines)
│   ├── inference/                           # Python inference scripts
│   │   ├── run_chat.py                      # Interactive chat
│   │   └── run_benchmark.py                 # Accuracy benchmarks
│   ├── tests/                               # Validation test suite
│   ├── tools/                               # Model inspection utilities
│   └── tokenizer/                           # HF tokenizer files
│
├── benchmarking/                 # Accuracy benchmarking suite
│   ├── gguf/                                # GGUF benchmark scripts
│   │   └── benchmark_gguf.py                # MedMCQA evaluation
│   ├── tflite/                              # TFLite benchmarks
│   └── download_models.py                   # Portable model fetcher
│
├── conversion/                   # GGUF conversion utilities
│   └── convert_hf_to_gguf.py
│
└── docs/                         # Project documentation
```

## Custom Engineering Highlights

### TFLite Conversion Pipeline (No Official Support Existed)

| Component | Lines | Achievement |
|-----------|-------|-------------|
| Text Decoder Conversion | 489 | First working MedGemma TFLite ever |
| Vision Encoder (MedSigLIP) | 493 | Hand-rolled 27-layer, 400M param model |
| Android Runtime | 538 | Production-ready inference engine |
| HF Tokenizer | 222 | Pure Kotlin, no dependencies |

**Key Technical Wins:**
- Discovered litert-torch 0.8.0 (via GitHub issue #909)
- Built from scratch when `ai_edge_torch` failed on grouped query attention
- Achieved 44.40% MedMCQA accuracy (Q8_0 lossless baseline)

### HuggingFace Uploads
- `itikelabhaskar/medgemma-4b-tflite` - Converted models
- `megalodon-ml/medgemma_kaggle` - Submission artifacts

## Model Stack

| Layer | Technology | Size |
|-------|------------|------|
| Base Model | MedGemma 1.5 4B (google/medgemma-4b-it) | - |
| Quantization | Q4_K_M GGUF | ~2.8GB |
| Backend | llama.cpp (com.arm.aichat) | Native |
| Vision | MedSigLIP 400M (custom TFLite) | ~1.6GB |

## Branch

Active development: `b2-gguf`
