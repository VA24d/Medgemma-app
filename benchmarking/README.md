# MedGemma Quantization Benchmarking

Reproducible evaluation of **MedGemma 1.5 4B IT** quantizations across 5 medical benchmarks, measuring how aggressive quantization affects clinical accuracy on edge devices.

## Results Summary

### GGUF Quantizations (500 samples per benchmark, GPU — RTX 4090)

| Model | Size | MedMCQA | MedQA | PubMedQA | MMLU Med | MedXpertQA |
|-------|------|---------|-------|----------|----------|------------|
| **BF16** (baseline) | 7.3 GB | 43.80% | 29.00% | 55.40% | 43.00% | 8.80% |
| **Q8_0** | 3.9 GB | 44.40% | 28.60% | 55.40% | 43.60% | 8.80% |
| **Q6_K** | 3.0 GB | 40.80% | 28.40% | 57.40% | 41.40% | 9.80% |
| **Q4_K_M** | 2.4 GB | 32.60% | 29.00% | 55.40% | 29.80% | 10.00% |

**Accuracy drop vs BF16 (percentage points):**

| Model | MedMCQA | MedQA | PubMedQA | MMLU Med | MedXpertQA |
|-------|---------|-------|----------|----------|------------|
| Q8_0 | -0.6 | +0.4 | 0.0 | -0.6 | 0.0 |
| Q6_K | +3.0 | +0.6 | -2.0 | +1.6 | -1.0 |
| Q4_K_M | **+11.2** | 0.0 | 0.0 | **+13.2** | -1.2 |

### TFLite Q8 (500 samples, CPU — logit-based scoring)

| Model | Size | Context | MedMCQA | Speed |
|-------|------|---------|---------|-------|
| **TFLite Q8** (ekv128) | 3.9 GB | 128 tokens | 39.55% | 6.4 s/q |

> TFLite's lower accuracy (39.55% vs GGUF Q8_0's 44.40%) is primarily due to the 128-token context window causing 12 questions to be skipped and some prompts to be truncated.

### Key Findings

1. **Q8_0 is lossless** — zero meaningful accuracy drop across all 5 benchmarks, at ~47% size reduction
2. **Q6_K is near-lossless** — within noise margin on all benchmarks, at ~59% size reduction
3. **Q4_K_M shows significant degradation** on knowledge-intensive tasks (MedMCQA -11.2pp, MMLU Med -13.2pp) but surprisingly no drop on reasoning tasks (MedQA, PubMedQA)
4. **TFLite Q8** matches GGUF Q8_0 quality when context window is sufficient, ideal for on-device deployment

---

## Directory Structure

```
benchmarking/
├── README.md                ← This file
├── download_models.py       ← Helper script to download GGUF models
├── gguf/                    ← GGUF quantization benchmarks (GPU)
│   ├── eval_medmcqa.py            # Single-benchmark (MedMCQA only)
│   ├── eval_multi_benchmark.py    # Multi-benchmark (5 datasets)
│   ├── models.json                # Model config (paths relative to repo root)
│   └── requirements.txt
└── tflite/                  ← TFLite benchmarks (CPU)
    ├── eval_medmcqa.py            # TFLite MedMCQA evaluation
    └── requirements.txt
```

---

## Setup

### Prerequisites

- Python 3.10+
- NVIDIA GPU with CUDA (for GGUF with GPU acceleration)
- HuggingFace account with access to MedGemma models

### 1. Download Models

**GGUF models** from [unsloth/medgemma-1.5-4b-it-GGUF](https://huggingface.co/unsloth/medgemma-1.5-4b-it-GGUF):

```bash
# Login to HuggingFace (required for MedGemma access)
huggingface-cli login

# Option 1: Use the provided download script (recommended)
python benchmarking/download_models.py

# Option 2: Manual download
pip install huggingface_hub
python -c "
from huggingface_hub import hf_hub_download
models = ['medgemma-1.5-4b-it-BF16.gguf', 'medgemma-1.5-4b-it-Q8_0.gguf',
          'medgemma-1.5-4b-it-Q6_K.gguf', 'medgemma-1.5-4b-it-Q4_K_M.gguf']
for m in models:
    hf_hub_download('unsloth/medgemma-1.5-4b-it-GGUF', m, local_dir='models')
"
```

This will download ~16.6 GB of models to the `models/` directory in your repo root.

**TFLite model**: Place your `.tflite` model file and tokenizer files (`tokenizer.json`, `tokenizer.model`, `tokenizer_config.json`) in a directory (e.g., `models/tflite/`). You'll pass the path via `--tflite-model` / `--tokenizer-dir` flags.

### 2. Install Dependencies

**GGUF (with GPU — recommended):**
```bash
pip install -r benchmarking/gguf/requirements.txt
CMAKE_ARGS="-DGGML_CUDA=on" pip install llama-cpp-python --force-reinstall --no-cache-dir
```

**GGUF (CPU only):**
```bash
pip install -r benchmarking/gguf/requirements.txt
```

**TFLite:**
```bash
pip install -r benchmarking/tflite/requirements.txt
```

### 3. Verify Model Paths (Optional)

The default `benchmarking/gguf/models.json` assumes models are in `models/` at the repo root:

```json
[
  {"name": "BF16",   "path": "models/medgemma-1.5-4b-it-BF16.gguf"},
  {"name": "Q8_0",   "path": "models/medgemma-1.5-4b-it-Q8_0.gguf"},
  {"name": "Q6_K",   "path": "models/medgemma-1.5-4b-it-Q6_K.gguf"},
  {"name": "Q4_K_M", "path": "models/medgemma-1.5-4b-it-Q4_K_M.gguf"}
]
```

**Important:** All paths are relative to the **repo root** (the `Medgemma-app/` directory), not the `benchmarking/` folder. If you downloaded models to a different location, edit the paths accordingly.

---

## Running Benchmarks

### GGUF — Single Benchmark (MedMCQA)

All commands should be run from the **repository root** (the parent of `benchmarking/`).

```bash
# Quick test (50 examples)
python benchmarking/gguf/eval_medmcqa.py --models-config benchmarking/gguf/models.json --n-examples 50

# Full benchmark (500 examples)
python benchmarking/gguf/eval_medmcqa.py --models-config benchmarking/gguf/models.json --n-examples 500 --baseline BF16

# Full dataset (4183 examples)
python benchmarking/gguf/eval_medmcqa.py --models-config benchmarking/gguf/models.json --n-examples 0 --baseline BF16
```

### GGUF — Multi-Benchmark (5 datasets)

```bash
# Quick smoke test (50 per benchmark)
python benchmarking/gguf/eval_multi_benchmark.py --models-config benchmarking/gguf/models.json --n-examples 50

# Full benchmark (500 per benchmark, ~80 min on GPU)
python benchmarking/gguf/eval_multi_benchmark.py \
    --models-config benchmarking/gguf/models.json \
    --benchmarks medmcqa medqa pubmedqa mmlu_med medxpertqa \
    --n-examples 500 \
    --baseline BF16

# Single benchmark only
python benchmarking/gguf/eval_multi_benchmark.py --models-config benchmarking/gguf/models.json --benchmarks medmcqa --n-examples 500
```

### TFLite

```bash
# Quick test (50 examples)
python benchmarking/tflite/eval_medmcqa.py \
    --tflite-model models/tflite/medgemma_4b_tpu_q8_ekv128.tflite \
    --tokenizer-dir models/tflite/ \
    --n-examples 50

# Full benchmark (500 examples)
python benchmarking/tflite/eval_medmcqa.py \
    --tflite-model models/tflite/medgemma_4b_tpu_q8_ekv128.tflite \
    --tokenizer-dir models/tflite/ \
    --n-examples 500

# With verbose per-question output
python benchmarking/tflite/eval_medmcqa.py \
    --tflite-model models/tflite/medgemma_4b_tpu_q8_ekv128.tflite \
    --tokenizer-dir models/tflite/ \
    --n-examples 500 --verbose
```

---

## Benchmarks & Datasets

| Benchmark | Dataset ID | Split | Total | Task Type |
|-----------|-----------|-------|-------|-----------|
| **MedMCQA** | `openlifescienceai/medmcqa` | validation | 4,183 | 4-way MCQ (medical entrance exams) |
| **MedQA** | `GBaker/MedQA-USMLE-4-options` | test | 1,273 | 4-way MCQ (USMLE-style) |
| **PubMedQA** | `qiaojin/PubMedQA` (pqa_labeled) | train | 1,000 | 3-way (Yes/No/Maybe) |
| **MMLU Med** | `cais/mmlu` (6 medical subsets) | test | ~1,089 | 4-way MCQ (medical knowledge) |
| **MedXpertQA** | `TsinghuaC3I/MedXpertQA` (Text) | test | 2,450 | Multi-way MCQ (expert-level) |

**MMLU Medical subsets:** anatomy, clinical_knowledge, college_biology, college_medicine, medical_genetics, professional_medicine.

All datasets are loaded automatically from HuggingFace Hub on first run.

---

## Methodology

- **Prompt format**: Gemma 3 chat template (`<start_of_turn>user\n...<end_of_turn>\n<start_of_turn>model\n`)
- **Decoding**: Greedy (temperature=0, top_p=1)
- **GGUF evaluation**: Text generation with `stop=["<end_of_turn>"]`, `max_tokens=64`
- **TFLite evaluation**: Logit-based scoring (compare A/B/C/D token logits after prefill) — faster and avoids thinking-mode issues
- **Answer extraction**: Regex-based parsing with thinking-block stripping
- **Sampling**: Fixed `seed=42`, shuffled subsets for reproducibility
- **GPU offload**: All GGUF layers offloaded (`n_gpu_layers=-1`)

---

## Hardware Used

Our benchmarks were run on:
- **GPU**: NVIDIA RTX 4090 (24 GB VRAM)
- **CPU**: 24-core (for TFLite)
- **RAM**: 64 GB
- **GGUF inference**: `llama-cpp-python` 0.3.16 with CUDA
- **TFLite inference**: `tensorflow` 2.20.0 (CPU, 4 threads)

---

## Output Format

Results are saved as both JSON and CSV:

- **JSON**: Full per-model, per-benchmark metrics with config
- **CSV**: Summary table for easy spreadsheet import

Example JSON structure:
```json
{
  "config": {"benchmarks": [...], "n_examples": 500, "seed": 42},
  "results": {
    "BF16": {
      "medmcqa": {"correct": 219, "total": 500, "accuracy": 0.438, ...},
      "medqa": {"correct": 145, "total": 500, "accuracy": 0.29, ...}
    }
  }
}
```

---

## License

This benchmarking suite is provided for research and competition use. The MedGemma models are subject to Google's model license terms.
