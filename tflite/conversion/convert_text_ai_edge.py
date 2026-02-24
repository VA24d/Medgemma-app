#!/usr/bin/env python3
"""
convert_ai_edge_torch.py  —  MedGemma 4B → TFLite (Standard Ops / XNNPACK-compatible)
======================================================================================

This script converts the MedGemma 4B text decoder (google/medgemma-1.5-4b-it)
into a TFLite model using **ai_edge_torch.convert()** — producing standard
TFLite flatbuffer ops that are compatible with XNNPACK, GPU delegate, and NNAPI.

HOW IT WORKS
------------
Unlike the LiteRT Generative converter (which emits STABLEHLO_COMPOSITE ops),
this path:
  1. Loads the original HuggingFace Gemma3ForCausalLM model in PyTorch.
  2. Wraps it in a thin forward-pass module that takes explicit KV cache tensors
     as inputs and returns updated caches + logits as outputs.
  3. Uses `ai_edge_torch.convert()` to trace the PyTorch graph via torch.export
     and lower it to standard TFLite ops (FULLY_CONNECTED, ADD, MUL, etc.).
  4. Applies INT8 or INT4 dynamic-range quantization via ai_edge_torch's
     built-in quantizer.

OUTPUT MODEL CHARACTERISTICS
----------------------------
  • Ops:            Standard TFLite (FULLY_CONNECTED, RESHAPE, ADD, …)
  • Delegates:      XNNPACK ✅  GPU ✅  NNAPI ✅  CPU ✅
  • Quantization:   Dynamic INT8 (~3.5 GB) or Dynamic INT4 (~2 GB)
  • KV Signatures:  ❌  No named signatures — single default signature
  • KV Management:  The wrapper flattens KV cache into explicit tensor I/O.
                    Your Android app must manage the KV tensors externally
                    (same as current app, just different tensor names).
  • Expected Perf:  ~1-2 s/token with XNNPACK on Pixel 7 Pro (INT8)

WHEN TO USE
-----------
  • You want hardware acceleration (XNNPACK multithreaded, GPU delegate, NNAPI).
  • You need the model to run on ANY phone, not just Pixel/Tensor devices.
  • You can adapt your Android app to work without named "decode"/"prefill_N"
    signatures (use interpreter.run() with positional I/O).

ENVIRONMENT
-----------
Run this on **Kaggle (GPU T4 x2)** or a machine with ≥ 30 GB RAM.
The HuggingFace model loads in FP32 (~16 GB), then gets traced and quantized.

  pip install tensorflow-cpu ai-edge-torch torch transformers safetensors huggingface_hub

USAGE
-----
  # On Kaggle — put your HF_TOKEN in Kaggle Secrets, then:
  python convert_ai_edge_torch.py

  # Locally — set env var:
  export HF_TOKEN=hf_xxxxx
  python convert_ai_edge_torch.py

  Output lands in ./output/ e.g.:
      medgemma_4b_xnnpack_int8_ekv512.tflite
"""

import os
import gc
import torch
import numpy as np

from huggingface_hub import login
from transformers import AutoModelForCausalLM, AutoConfig

import ai_edge_torch
from ai_edge_torch.quantize.pt2e_quantizer import get_symmetric_quantization_config
from ai_edge_torch.quantize.quant_config import QuantConfig

# =============================================================================
# CONFIGURATION
# =============================================================================

MODEL_ID   = "google/medgemma-1.5-4b-it"   # HuggingFace repo
OUTPUT_DIR = "output"                       # Where the .tflite lands

# KV cache length — how many past tokens attention can see.
#   128  → fastest, suits short Q&A
#   256  → moderate
#   512  → good for longer medical queries
#   2048 → large context (needs more RAM on device)
KV_CACHE_MAX_LEN = 512

# Quantization mode: "int8" or "int4"
#   int8  → ~3.5 GB, fastest on CPU/XNNPACK (ARM NEON accelerated)
#   int4  → ~2 GB, smaller file but slower dequant
QUANT_MODE = "int8"

os.makedirs(OUTPUT_DIR, exist_ok=True)


# =============================================================================
# MedGemma 4B architecture constants (from config.json)
# =============================================================================
NUM_LAYERS   = 34
NUM_KV_HEADS = 4       # GQA: 8 query heads, 4 KV heads
HEAD_DIM     = 256
VOCAB_SIZE   = 262_208
HIDDEN_SIZE  = 2560


# =============================================================================
# 1. AUTHENTICATE WITH HUGGING FACE
# =============================================================================
print("--- Authenticating ---")
try:
    from kaggle_secrets import UserSecretsClient
    hf_token = UserSecretsClient().get_secret("HF_TOKEN")
    login(token=hf_token)
    print("✅ Logged in via Kaggle Secrets")
except Exception:
    hf_token = os.environ.get("HF_TOKEN", None)
    if hf_token:
        login(token=hf_token)
        print("✅ Logged in via HF_TOKEN env var")
    else:
        print("⚠️  No HF_TOKEN found; assuming prior `huggingface-cli login`")


# =============================================================================
# 2. LOAD THE HUGGINGFACE MODEL
# =============================================================================
# We load in float32 for tracing.  Quantization happens during TFLite export.
print("\n--- Loading HuggingFace Model (this takes a few minutes) ---")

hf_config = AutoConfig.from_pretrained(MODEL_ID)
# Force the model to use our KV cache length
hf_config.max_position_embeddings = KV_CACHE_MAX_LEN

hf_model = AutoModelForCausalLM.from_pretrained(
    MODEL_ID,
    config=hf_config,
    torch_dtype=torch.float32,     # FP32 needed for clean tracing
    low_cpu_mem_usage=True,        # Load shard-by-shard to reduce peak RAM
)
hf_model.eval()
print(f"  Model loaded — {sum(p.numel() for p in hf_model.parameters()):,} parameters")


# =============================================================================
# 3. WRAPPER MODULE — Flattens KV cache into explicit tensor I/O
# =============================================================================
# ai_edge_torch.convert() needs a module whose forward() takes flat tensors
# (no nested tuples/dicts).  We wrap the HF model so that:
#
#   Inputs:  tokens (1, seq_len), position_ids (1, seq_len),
#            kv_k_0..kv_k_33, kv_v_0..kv_v_33   (each: 1, num_kv_heads, kv_len, head_dim)
#
#   Outputs: logits (1, seq_len, vocab),
#            new_kv_k_0..new_kv_k_33, new_kv_v_0..new_kv_v_33
#
# This gives the Android app full control over the KV cache lifetime,
# equivalent to what the LiteRT Generative signatures provide.

class MedGemmaWrapper(torch.nn.Module):
    """
    Wraps HuggingFace Gemma3ForCausalLM with flattened KV cache I/O
    for ai_edge_torch export.
    """

    def __init__(self, hf_model, num_layers, num_kv_heads, head_dim, kv_cache_len):
        super().__init__()
        self.model = hf_model
        self.num_layers = num_layers
        self.num_kv_heads = num_kv_heads
        self.head_dim = head_dim
        self.kv_cache_len = kv_cache_len

    def forward(self, tokens, position_ids, *flat_kv_caches):
        """
        Args:
            tokens:       (batch=1, seq_len) int64 token IDs
            position_ids: (batch=1, seq_len) int64 position indices
            *flat_kv_caches: 2*num_layers tensors, alternating K and V:
                kv_k_0, kv_v_0, kv_k_1, kv_v_1, ..., kv_k_33, kv_v_33
                Each K/V shape: (batch=1, num_kv_heads, kv_cache_len, head_dim)

        Returns:
            logits:   (batch=1, seq_len, vocab_size)
            followed by 2*num_layers updated KV cache tensors (same shapes)
        """
        # Reconstruct the past_key_values tuple that HF expects
        # Each layer: (key_states, value_states) both (B, num_kv_heads, seq_len, head_dim)
        past_key_values = []
        for i in range(self.num_layers):
            k = flat_kv_caches[2 * i]       # (1, num_kv_heads, kv_len, head_dim)
            v = flat_kv_caches[2 * i + 1]   # (1, num_kv_heads, kv_len, head_dim)
            past_key_values.append((k, v))

        # Build a simple causal attention mask
        # HF's Gemma3 model handles mask creation internally when we pass
        # use_cache=True and past_key_values
        outputs = self.model(
            input_ids=tokens,
            position_ids=position_ids,
            past_key_values=past_key_values,
            use_cache=True,
        )

        logits = outputs.logits   # (1, seq_len, vocab_size)

        # Flatten the new KV cache back to individual tensors
        new_caches = []
        for i in range(self.num_layers):
            new_k = outputs.past_key_values[i][0]  # (1, num_kv_heads, new_len, head_dim)
            new_v = outputs.past_key_values[i][1]
            new_caches.append(new_k)
            new_caches.append(new_v)

        return (logits, *new_caches)


print("\n--- Creating Wrapper Module ---")
wrapper = MedGemmaWrapper(
    hf_model=hf_model,
    num_layers=NUM_LAYERS,
    num_kv_heads=NUM_KV_HEADS,
    head_dim=HEAD_DIM,
    kv_cache_len=KV_CACHE_MAX_LEN,
)
wrapper.eval()
print("  ✅ Wrapper created")


# =============================================================================
# 4. BUILD SAMPLE INPUTS FOR TRACING
# =============================================================================
# ai_edge_torch.convert() traces the model with concrete example inputs.
# We build dummy inputs matching the shapes the wrapper expects.

print("\n--- Building Sample Inputs ---")

seq_len = 1  # single-token decode step (most common inference path)

sample_tokens      = torch.zeros((1, seq_len), dtype=torch.long)
sample_position_ids = torch.zeros((1, seq_len), dtype=torch.long)

# KV caches: one K and one V per layer
sample_kv_caches = []
for _ in range(NUM_LAYERS):
    # Key cache
    sample_kv_caches.append(
        torch.zeros((1, NUM_KV_HEADS, KV_CACHE_MAX_LEN, HEAD_DIM), dtype=torch.float32)
    )
    # Value cache
    sample_kv_caches.append(
        torch.zeros((1, NUM_KV_HEADS, KV_CACHE_MAX_LEN, HEAD_DIM), dtype=torch.float32)
    )

sample_args = (sample_tokens, sample_position_ids, *sample_kv_caches)
print(f"  Tracing with seq_len={seq_len}, kv_cache_len={KV_CACHE_MAX_LEN}")
print(f"  Total input tensors: {len(sample_args)} (tokens + pos + {NUM_LAYERS*2} KV caches)")


# =============================================================================
# 5. CONFIGURE QUANTIZATION
# =============================================================================
print(f"\n--- Configuring Quantization: {QUANT_MODE} ---")

if QUANT_MODE == "int4":
    # Dynamic INT4 quantization — smaller model, slightly slower inference
    quant_config = QuantConfig(
        generative_recipe=get_symmetric_quantization_config(
            is_dynamic=True,
            weight_bits=4,
        )
    )
    quant_tag = "int4"
elif QUANT_MODE == "int8":
    # Dynamic INT8 quantization — best speed on ARM NEON / XNNPACK
    quant_config = QuantConfig(
        generative_recipe=get_symmetric_quantization_config(
            is_dynamic=True,
            weight_bits=8,
        )
    )
    quant_tag = "int8"
else:
    raise ValueError(f"Unknown QUANT_MODE: {QUANT_MODE}. Use 'int4' or 'int8'.")


# =============================================================================
# 6. CONVERT TO TFLITE
# =============================================================================
output_name = f"medgemma_4b_xnnpack_{quant_tag}_ekv{KV_CACHE_MAX_LEN}"
output_path = os.path.join(OUTPUT_DIR, f"{output_name}.tflite")

print(f"\n--- Converting to TFLite (this may take 15-30 minutes) ---")
print(f"  Output:       {output_path}")
print(f"  Quantization: dynamic {QUANT_MODE}")
print(f"  kv_cache_len: {KV_CACHE_MAX_LEN}")

edge_model = ai_edge_torch.convert(
    wrapper,
    sample_args,
    quant_config=quant_config,
)
edge_model.export(output_path)

print(f"\n✅ SUCCESS → {output_path}")
file_size_gb = os.path.getsize(output_path) / (1024**3)
print(f"  File size: {file_size_gb:.2f} GB")

# Free memory
del hf_model, wrapper, edge_model
gc.collect()


# =============================================================================
# 7. QUICK SANITY CHECK
# =============================================================================
print("\n--- Quick Sanity Check ---")
try:
    from ai_edge_litert import interpreter as tfl_interpreter

    interp = tfl_interpreter.Interpreter(model_path=output_path)
    interp.allocate_tensors()

    input_details  = interp.get_input_details()
    output_details = interp.get_output_details()

    print(f"  Inputs:  {len(input_details)}")
    print(f"  Outputs: {len(output_details)}")

    for i, d in enumerate(input_details[:5]):
        print(f"    Input[{i}]: {d['name']}  shape={d['shape']}  dtype={d['dtype']}")
    if len(input_details) > 5:
        print(f"    ... and {len(input_details) - 5} more")

    for i, d in enumerate(output_details[:3]):
        print(f"    Output[{i}]: {d['name']}  shape={d['shape']}  dtype={d['dtype']}")
    if len(output_details) > 3:
        print(f"    ... and {len(output_details) - 3} more")

    print("  ✅ Model loads and allocates successfully!")

except ImportError:
    print("  ⚠️ ai_edge_litert not available — skipping sanity check")
except Exception as e:
    print(f"  ❌ Sanity check failed: {e}")


# =============================================================================
# 8. ANDROID APP INTEGRATION NOTES
# =============================================================================
print()
print("=" * 70)
print("📱 ANDROID APP INTEGRATION NOTES")
print("=" * 70)
print("""
This model uses STANDARD TFLite ops, so you can enable XNNPACK and GPU:

  1. Enable XNNPACK in your interpreter options:
       options.setUseXNNPACK(true)
       options.setNumThreads(Runtime.getRuntime().availableProcessors())

  2. Or try GPU delegate:
       val gpuDelegate = GpuDelegate()
       options.addDelegate(gpuDelegate)

  3. The model has a SINGLE default signature (no named "decode"/"prefill").
     Use interpreter.run() or interpreter.runForMultipleInputsOutputs().

  4. Input tensors (in order):
       [0] tokens:       int64 (1, 1)   — current token ID
       [1] position_ids: int64 (1, 1)   — current position
       [2..69] kv caches:               — 34 K + 34 V tensors
              each (1, 4, {kv_len}, 256)  float32

  5. Output tensors (in order):
       [0] logits:       float32 (1, 1, 262208)
       [1..68] updated kv caches         — same shapes as input caches

  6. KV cache management:
       - Initialize all KV caches as zeros
       - After each decode step, feed the output caches back as inputs
       - The cache length grows each step; use position_ids to track

  7. Push model to device:
       adb push {output_name}.tflite /data/local/tmp/medgemma/
""".strip())
print()


# =============================================================================
# OPTIONAL: Upload to Hugging Face
# =============================================================================
# Uncomment to auto-upload:
#
# from huggingface_hub import HfApi
# api = HfApi()
# username = api.whoami()["name"]
# repo_id = f"{username}/medgemma-4b-tflite-xnnpack"
# api.create_repo(repo_id=repo_id, private=True, exist_ok=True)
# api.upload_file(
#     path_or_fileobj=output_path,
#     path_in_repo=os.path.basename(output_path),
#     repo_id=repo_id,
#     repo_type="model",
# )
# print(f"✅ Uploaded to https://huggingface.co/{repo_id}")
