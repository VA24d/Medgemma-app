# What We Built vs What Existed

This document explains what parts of this TFLite conversion pipeline were **custom built** vs what existed from official Google tools.

---

## The Core Problem

**No official tool supported MedGemma 4B → TFLite conversion.**

📌 **GitHub Issue:** [google-ai-edge/litert-torch #909 — Support conversion for Gemma 3-4B model](https://github.com/google-ai-edge/litert-torch/issues/909)

The official `litert-torch` library only supported smaller Gemma models (1B, 270M). MedGemma 4B is based on Gemma 3-4B architecture, which had no conversion support.

---

## What We Built Ourselves

### 1. Complete Text Decoder Conversion Pipeline
**Location:** `conversion/convert_text_decoder.py`, `conversion/convert_text_ai_edge.py`

| Component | What We Did |
|-----------|-------------|
| HuggingFace Weight Remapping | Discovered non-standard key prefix: `language_model.model.layers.*` instead of `model.layers.*`. Wrote 444-parameter remapping logic. |
| QKV Fusion | HuggingFace stores separate `q_proj`, `k_proj`, `v_proj`. LiteRT expects fused `qkv_projection`. Manually concatenated for all 34 layers. |
| GQA Configuration | Reverse-engineered: 8 Q heads, 4 KV heads. Set `num_query_groups=4` explicitly. |
| 4-Norm Configuration | Discovered 4 RMS-Norms per layer (pre-attn, post-attn, pre-ff, post-ff), overriding default config. |
| KV Cache Layout | Figured out transposed V layout: K = `[1, 4, kv_len, 256]`, V = `[1, 4, 256, kv_len]`. Set `KV_LAYOUT_TRANSPOSED`. |
| RoPE Base Discovery | Alternating bases: 10,000 (local sliding) / 1,000,000 (global every 6th layer). |

**Time spent:** ~40+ hours of debugging, weight inspection, and trial-and-error.

### 2. Hand-Rolled Vision Encoder
**Location:** `conversion/convert_vision_896.py`, `conversion/convert_vision_448_local.py`

The MedSigLIP vision encoder did not have ANY conversion support. We built it from scratch:

| Component | What We Did |
|-----------|-------------|
| PyTorch Architecture | Implemented SigLIP-So400m (27 layers, 400M params) from scratch based on paper |
| Weight Extraction | Extracted weights from `vision_tower.vision_model.*` safetensors keys |
| Projector Fix | Discovered projector weight is transposed in HuggingFace — applied `.T` before loading |
| Embedding Bridge | Built nearest-neighbor cosine lookup to convert float embeddings → integer token IDs |

**Time spent:** ~30+ hours.

### 3. Environment Discovery
**Location:** `docs/dothistowork.md`, `env/setup_conversion_env.sh`

Discovered through trial-and-error:
- ❌ `ai-edge-torch` is deprecated and broken
- ✅ Must use `litert-torch==0.8.0`
- ❌ Virtual environments cause ABI incompatibility errors
- ✅ Must use system Python3 (`/usr/bin/python3`)
- ❌ `protobuf >= 4.0` breaks TensorFlow compatibility
- ✅ Must use `protobuf==3.20.3`

**Time spent:** ~15+ hours of debugging segfaults and symbol errors.

---

## What Existed (Used As-Is)

| Component | Source |
|-----------|--------|
| TFLite Runtime | Google TensorFlow (`tensorflow-cpu`) |
| LiteRT Converter | Google's `litert-torch` package |
| Base Model Weights | HuggingFace: `google/medgemma-1.5-4b-it` |
| Tokenizer | Gemma 3 tokenizer from HuggingFace |
| Android TFLite Interpreter | Google TensorFlow Lite (`org.tensorflow:tensorflow-lite`) |

---

## Our HuggingFace Uploads

We uploaded our converted models for others to use:

### Pre-converted TFLite Models
**Repo:** [itikelabhaskar/medgemma-4b-tflite](https://huggingface.co/itikelabhaskar/medgemma-4b-tflite)

| Model | Size | Notes |
|-------|------|-------|
| `medgemma_4b_tpu_q8_ekv128.tflite` | 3.92 GB | ✅ **Recommended** — Q8 quantized, 128-token KV cache |
| `medgemma_4b_tpu_q4_block128_ekv512.tflite` | 2.01 GB | ⚠️ Has A-bias bug, not recommended |
| `medgemma_4b_mobile_int8_q8_ekv2048.tflite` | 3.92 GB | Longer context (2048 tokens) |
| `medgemma_4b_mobile_int8_q8_ekv5120.tflite` | 3.92 GB | Longest context (5120 tokens) |

### Conversion Scripts + Vision Models
**Repo:** [megalodon-ml/medgemma_kaggle](https://huggingface.co/megalodon-ml/medgemma_kaggle)

Contains:
- `conversion/` — All conversion scripts
- `models/` — Vision encoder TFLite models (448px, 896px)
- `tokenizers/` — Tokenizer files

---

## The Hard Parts (Summary)

1. **No documentation** on HuggingFace MedGemma weight structure
2. **Non-standard key prefixes** required reverse-engineering via inspection scripts
3. **QKV fusion** not documented — had to trace through litert-torch source code
4. **Environment hell** — 15+ hours finding the right combination of packages
5. **Vision encoder** had zero conversion support — reimplemented architecture from scratch

---

## Team Contributions

This work was done by the Kaggle competition team as part of the [Google MedGemma Challenge](https://www.kaggle.com/competitions/medgemma).

**Total estimated effort:** 85+ hours of engineering work

---

## References

- [MedGemma Technical Report (arXiv:2507.05201)](https://arxiv.org/abs/2507.05201)
- [litert-torch Issue #909](https://github.com/google-ai-edge/litert-torch/issues/909)
- [MedSigLIP Documentation](https://developers.google.com/health-ai-developer-foundations/medsiglip)
- [ai-edge-torch GitHub](https://github.com/google-ai-edge/ai-edge-torch)
