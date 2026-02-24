# Model Download / Conversion Instructions

This folder should contain the converted TFLite models. They are too large for git.

## Required Models

| Model | Size | Purpose |
|-------|------|---------|
| `medgemma_4b_tpu_q8_ekv128.tflite` | ~3.9 GB | Text decoder (Q8 quantized, 128-token context) ✅ Recommended |
| `medsiglip_vision_448.tflite` | ~1.5 GB | Vision encoder (448×448 images) |
| `multimodal_projector_448.tflite` | ~12 MB | Vision-to-text projector |

---

## Option 1: Download Pre-converted Models (Fastest)

We've uploaded pre-converted models to HuggingFace:

### Text Decoder Models
**Repo:** [itikelabhaskar/medgemma-4b-tflite](https://huggingface.co/itikelabhaskar/medgemma-4b-tflite)

```bash
# Install huggingface-cli
pip install huggingface_hub

# Download recommended Q8 model
huggingface-cli download itikelabhaskar/medgemma-4b-tflite medgemma_4b_tpu_q8_ekv128.tflite --local-dir .

# Or download all models
huggingface-cli download itikelabhaskar/medgemma-4b-tflite --local-dir .
```

**Available models:**
| Model | Size | Notes |
|-------|------|-------|
| `medgemma_4b_tpu_q8_ekv128.tflite` | 3.9 GB | ✅ **Recommended** — Best accuracy/size tradeoff |
| `medgemma_4b_tpu_q4_block128_ekv512.tflite` | 2.0 GB | ⚠️ Has A-bias bug, not recommended |
| `medgemma_4b_mobile_int8_q8_ekv2048.tflite` | 3.9 GB | Longer context (2048 tokens) |
| `medgemma_4b_mobile_int8_q8_ekv5120.tflite` | 3.9 GB | Longest context (5120 tokens) |

### Vision Models
**Repo:** [megalodon-ml/medgemma_kaggle](https://huggingface.co/megalodon-ml/medgemma_kaggle)

```bash
# Download from megalodon-ml repo
huggingface-cli download megalodon-ml/medgemma_kaggle models/medsiglip_vision_448.tflite --local-dir .
huggingface-cli download megalodon-ml/medgemma_kaggle models/multimodal_projector_448.tflite --local-dir .
```

---

## Option 2: Convert Models Yourself

Use the scripts in `../conversion/` on Kaggle (free GPU):

### Text Decoder
```bash
# On Kaggle notebook:
python download_model.py         # Downloads MedGemma from HuggingFace
python convert_text_decoder.py   # Converts to TFLite Q8
```

### Vision Encoder
See `../docs/KAGGLE_INSTRUCTIONS.md` for step-by-step instructions, or use `../conversion/notebooks/vision_kaggle.ipynb`.

---

## Verification

After placing models here, verify with:
```bash
cd ../inference
python run_chat.py  # Should load model and start chat
```

---

## Model Architecture

### Text Decoder: MedGemma 4B
- Gemma 3 4B fine-tuned on medical data
- 34 transformer layers
- 2.9B parameters
- Vocab: 262,208 tokens
- KV cache: 128 tokens (Q8) or 512 tokens (Q4) or 2048/5120 (INT8)

### Vision Encoder: MedSigLIP-So400m
- 27 transformer layers
- 400M parameters
- Input: 448×448 or 896×896 RGB images
- Output: 1024 or 4096 × 1152 embeddings

### Multimodal Projector
- Linear layer mapping vision → text space
- 1152 → 2560 dimensions
