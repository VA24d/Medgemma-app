# MedGemma TFLite — On-Device Medical AI

Complete pipeline for converting and running **MedGemma 4B** on mobile devices and laptops using TFLite.

> **Built for:** [Kaggle Google MedGemma Challenge](https://www.kaggle.com/competitions/medgemma)  
> **Note:** This required custom engineering — see [CONTRIBUTIONS.md](CONTRIBUTIONS.md) for what we built.

---

## Quick Start

### Option 1: Download Pre-converted Models (Fastest)

```bash
# Install huggingface-cli
pip install huggingface_hub

# Download text decoder (Q8, recommended)
huggingface-cli download itikelabhaskar/medgemma-4b-tflite medgemma_4b_tpu_q8_ekv128.tflite --local-dir models/

# Place in models/ folder
```

**HuggingFace Repos:**
- 📦 **Pre-converted TFLite models:** [itikelabhaskar/medgemma-4b-tflite](https://huggingface.co/itikelabhaskar/medgemma-4b-tflite)
- 🔧 **Conversion scripts + Vision models:** [megalodon-ml/medgemma_kaggle](https://huggingface.co/megalodon-ml/medgemma_kaggle)

### Option 2: Convert Models Yourself (Kaggle)

Convert on Kaggle (free GPU + HuggingFace access):
- **Text decoder:** See `conversion/convert_text_decoder.py` or notebook `conversion/notebooks/text_decoder_kaggle.ipynb`
- **Vision encoder:** See `docs/KAGGLE_INSTRUCTIONS.md` or notebook `conversion/notebooks/vision_kaggle.ipynb`

### Run Inference

```bash
# Install dependencies
pip install -r env/requirements_text_decoder.txt

# Interactive chat with text model
cd inference
python run_chat.py

# Benchmark on MedMCQA (60% accuracy expected)
python run_benchmark.py
```

---

## Folder Structure

```
tflite/
├── CONTRIBUTIONS.md              # What we built vs what existed
├── conversion/                   # HuggingFace → TFLite conversion
│   ├── convert_text_decoder.py   # LiteRT Generative path (recommended)
│   ├── convert_text_ai_edge.py   # ai-edge-torch path (XNNPACK/GPU)
│   ├── convert_vision_896.py     # 896×896 vision encoder
│   ├── convert_vision_448_local.py # 448×448 for local/WSL
│   ├── download_model.py         # HuggingFace downloader
│   ├── KAGGLE_VISION_GUIDE.md    # Step-by-step Kaggle guide
│   └── notebooks/                # Original Kaggle notebooks
├── inference/                    # Inference scripts
│   ├── run_chat.py               # Interactive text chat
│   ├── run_benchmark.py          # MedMCQA accuracy benchmark
│   └── run_vision_test.py        # Vision encoder test
├── env/                          # Environment setup
│   ├── requirements_text_decoder.txt    # For text model inference
│   ├── requirements_tflite_conversion.txt # For vision conversion
│   └── setup_conversion_env.sh   # Auto-setup script (WSL/Linux)
├── tokenizer/                    # Gemma 3 tokenizer files
├── models/                       # Place .tflite files here
├── tests/                        # Validation & test scripts
├── tools/                        # Diagnostic utilities
├── android/                      # Reference Android code
│   ├── LiteRTInferenceEngine.kt  # TFLite runtime wrapper
│   ├── HFTokenizer.kt            # Tokenizer for Android
│   └── ANDROID_SETUP.md          # Android integration guide
└── docs/                         # Documentation
    ├── ARCHITECTURE.md           # Technical architecture
    ├── dothistowork.md           # What works & what doesn't (critical!)
    ├── KAGGLE_INSTRUCTIONS.md    # Kaggle setup guide
    └── FULL-TECHNICAL-PLAN.md    # Complete implementation plan
```

## Model Variants

| Model | Quantization | KV Cache | Size | Accuracy | Status |
|-------|-------------|----------|------|----------|--------|
| Q8 ekv128 | INT8 | 128 tokens | 3.9 GB | ~60% | ✅ Recommended |
| Q4 ekv512 | INT4 | 512 tokens | 2.0 GB | ~20% | ❌ Broken (A-bias) |
| INT8 ekv2048 | INT8 | 2048 tokens | 4.5 GB | TBD | Larger context |

**Use Q8 ekv128 for mobile deployment.**

## Technical Details

### TFLite Signature
```python
interpreter = tf.lite.Interpreter(model_path="model.tflite")
runner = interpreter.get_signature_runner("decode")

output = runner(
    args=token_ids,           # [1, seq_len]
    attention_mask=mask,      # [1, seq_len]
    input_pos=positions,      # [1, seq_len]
    kv=kv_cache              # [34, 2, 1, total_kvlen, 4, 256]
)
```

### KV Cache Shape
- 34 layers
- 2 = key + value
- 4 KV heads
- 256 head dimension
- Total KV length varies by model (128, 512, 2048)

### MCQ Scoring
Score answers by comparing logits at token positions:
```python
A_TOKEN = 236776
B_TOKEN = 236799
C_TOKEN = 236780
D_TOKEN = 236796

# Get logits for each option, pick highest
```

## Android Integration

See `android/LiteRTInferenceEngine.kt` for:
- Loading TFLite model
- Running inference with signature runner
- KV cache management
- Streaming token output

See `android/HFTokenizer.kt` for tokenizer integration using `tokenizer.json`.

---

## Requirements

### For Inference (Laptop/Desktop)
- Python 3.10+
- TensorFlow 2.16+ (`pip install tensorflow`)
- ~8GB RAM for Q8 model

### For Conversion
**Text Decoder (Kaggle recommended):**
- Kaggle TPU v5e-8 or GPU T4 x2
- 16+ GB RAM
- See `env/requirements_text_decoder.txt`

**Vision Encoder (WSL/Linux only):**
- System Python 3.10 — NOT a virtual environment! (ABI issues)
- `litert-torch==0.8.0` (NOT `ai-edge-torch`)
- See `env/requirements_tflite_conversion.txt` and `docs/dothistowork.md`

### For Android
- TensorFlow Lite runtime (`org.tensorflow:tensorflow-lite`)
- See `android/ANDROID_SETUP.md` for full setup guide

---

## HuggingFace Repositories

| Repository | Contents |
|------------|----------|
| [itikelabhaskar/medgemma-4b-tflite](https://huggingface.co/itikelabhaskar/medgemma-4b-tflite) | Pre-converted TFLite models (Q8, Q4, INT8 variants) + tokenizer |
| [megalodon-ml/medgemma_kaggle](https://huggingface.co/megalodon-ml/medgemma_kaggle) | Conversion scripts + Vision encoder TFLite models (448px, 896px) |
| [google/medgemma-1.5-4b-it](https://huggingface.co/google/medgemma-1.5-4b-it) | Original MedGemma weights (requires access approval) |

---

## Documentation

| Document | Purpose |
|----------|---------|
| [CONTRIBUTIONS.md](CONTRIBUTIONS.md) | What we built vs what existed |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 3-model pipeline architecture |
| [docs/dothistowork.md](docs/dothistowork.md) | **Critical:** What works and what doesn't |
| [docs/KAGGLE_INSTRUCTIONS.md](docs/KAGGLE_INSTRUCTIONS.md) | Step-by-step Kaggle setup |
| [docs/FULL-TECHNICAL-PLAN.md](docs/FULL-TECHNICAL-PLAN.md) | Detailed implementation plan |
| [docs/QUICKREF_CONVERSION.md](docs/QUICKREF_CONVERSION.md) | Quick reference commands |

---

## The 3-Model Pipeline

```
Image (448×448 or 896×896)     Text Prompt
         │                          │
         ▼                          │
┌─────────────────┐                 │
│  MedSigLIP      │                 │
│  Vision Encoder │                 │
│  (27 layers,    │                 │
│   400M params)  │                 │
└────────┬────────┘                 │
         │ [1, 1024/4096, 1152]     │
         ▼                          │
┌─────────────────┐                 │
│  Multimodal     │                 │
│  Projector      │                 │
│  (1152 → 2560)  │                 │
└────────┬────────┘                 │
         │ [1, 1024/4096, 2560]     │
         └──────────┬───────────────┘
                    ▼
         ┌─────────────────┐
         │   MedGemma 4B   │
         │   Text Decoder  │
         │   (34 layers,   │
         │    2.9B params) │
         └────────┬────────┘
                  ▼
           Generated Text
```

---

## References

- [MedGemma Technical Report (arXiv:2507.05201)](https://arxiv.org/abs/2507.05201)
- [litert-torch Issue #909](https://github.com/google-ai-edge/litert-torch/issues/909) — The GitHub issue documenting lack of 4B support
- [MedSigLIP Documentation](https://developers.google.com/health-ai-developer-foundations/medsiglip)
- [Kaggle MedGemma Competition](https://www.kaggle.com/competitions/medgemma)

