#!/usr/bin/env python3
"""
convert_litert_generative.py  —  MedGemma 4B → TFLite (StableHLO / LiteRT Generative)
======================================================================================

This script converts the MedGemma 4B text decoder (google/medgemma-1.5-4b-it)
into a TFLite model using the **LiteRT Generative converter**.

HOW IT WORKS
------------
The `litert_torch.generative.converter` pipeline:
  1. Builds a LiteRT Decoder graph (Gemma3 architecture) in PyTorch.
  2. Loads HuggingFace safetensor weights, remapping & fusing QKV projections.
  3. Exports to TFLite with STABLEHLO_COMPOSITE ops — these are high-level ops
     (odml.scaled_dot_product_attention, odml.dynamic_int4_matmul, etc.) that
     the LiteRT native CPU backend can execute efficiently.
  4. Emits two TFLite signatures:
       • "decode"        — auto-regressive single-token decode with KV cache I/O
       • "prefill_<N>"   — batched prefill of N tokens (GPU path only)
     The KV cache tensors are explicit signature inputs/outputs, so the Android
     app manages cache lifetime externally.

OUTPUT MODEL CHARACTERISTICS
----------------------------
  • Ops:            STABLEHLO_COMPOSITE (NOT standard TFLite ops)
  • Delegates:      CPU only — XNNPACK, GPU, and NNAPI cannot execute these ops
  • Quantization:   INT4 block-128 (~2 GB) or INT8 (~3.5 GB)
  • KV Signatures:  ✅  decode + prefill_N
  • Performance:    ~2.8 s/token (INT8) or ~5–10 s/token (INT4) on Pixel 7 Pro CPU

WHEN TO USE
-----------
  • You want the smallest possible model file (INT4 block-128 ≈ 2 GB).
  • You only need CPU inference and your Android app already handles the
    KV-cache signature protocol (LiteRTInferenceEngine).

ENVIRONMENT
-----------
Run this on **Kaggle (GPU T4 x2)** or a machine with ≥ 16 GB RAM.

  pip install tensorflow-cpu ai-edge-torch litert-torch py-cpuinfo safetensors huggingface_hub

USAGE
-----
  # On Kaggle — put your HF_TOKEN in Kaggle Secrets, then:
  python convert_litert_generative.py

  # Locally — set env var:
  export HF_TOKEN=hf_xxxxx
  python convert_litert_generative.py

  The output .tflite file lands in ./output/ with an auto-generated name
  encoding the quant type and KV cache length, e.g.:
      medgemma_4b_mobile_int4_q4b128_ekv128.tflite
"""

import os
import gc
import glob
import torch
import psutil

from huggingface_hub import snapshot_download, login, hf_hub_download
from safetensors.torch import load_file

# ─── LiteRT Generative imports ──────────────────────────────────────────────
import litert_torch.generative.layers.model_config as cfg
from litert_torch.generative.examples.gemma3 import decoder
from litert_torch.generative.utilities import converter
from litert_torch.generative.utilities.export_config import ExportConfig
from litert_torch.generative.layers import kv_cache as kv_utils
from litert_torch.generative.utilities.converter import QuantizationName


# =============================================================================
# CONFIGURATION  —  Tweak these to trade speed vs. context length vs. file size
# =============================================================================

MODEL_ID   = "google/medgemma-1.5-4b-it"   # HuggingFace repo
MODEL_PATH = "medgemma_4b"                  # Local download folder
OUTPUT_DIR = "output"                       # Where the .tflite lands

# KV cache length: how many past tokens attention can see.
#   128  → fastest (~1-3 s/token on Pixel 7 Pro), suits short Q&A chats
#   256  → moderate, suits most medical queries
#   512  → original default, good for long context but slower
#   2048 → large context (INT8 recommended at this size)
KV_CACHE_MAX_LEN = 128

# Prefill window: tokens processed in one batch prefill call (GPU only benefit).
# On CPU the app skips prefill anyway, so match KV_CACHE_MAX_LEN for simplicity.
PREFILL_SEQ_LEN = 128

# Quantization:
#   DYNAMIC_INT4_BLOCK128  → ~2 GB file, slower dequant on CPU
#   DYNAMIC_INT8           → ~3.5 GB file, 2-3× faster on CPU ARM NEON
# Use INT4 for small file / low-RAM phones.  Use INT8 for speed.
QUANTIZE = QuantizationName.DYNAMIC_INT4_BLOCK128

os.makedirs(OUTPUT_DIR, exist_ok=True)


# =============================================================================
# 1. AUTHENTICATE WITH HUGGING FACE
# =============================================================================
print("--- Authenticating ---")
try:
    # Kaggle path
    from kaggle_secrets import UserSecretsClient
    hf_token = UserSecretsClient().get_secret("HF_TOKEN")
    login(token=hf_token)
    print("✅ Logged in via Kaggle Secrets")
except Exception:
    # Local/Colab path — expects HF_TOKEN env var or prior `huggingface-cli login`
    hf_token = os.environ.get("HF_TOKEN", None)
    if hf_token:
        login(token=hf_token)
        print("✅ Logged in via HF_TOKEN env var")
    else:
        print("⚠️  No HF_TOKEN found; assuming you already ran `huggingface-cli login`")


# =============================================================================
# 2. DOWNLOAD THE FULL MODEL (safetensors + config)
# =============================================================================
print("\n--- Downloading Full Model ---")
snapshot_download(repo_id=MODEL_ID, local_dir=MODEL_PATH, local_dir_use_symlinks=False)


# =============================================================================
# 3. DEFINE MEDGEMMA 4B DECODER CONFIG
# =============================================================================
# This must match the HuggingFace model architecture exactly:
#   34 layers, 8 attn heads, 4 KV heads, 256 head dim, 2560 embed dim,
#   vocab 262208, GELU_TANH gated FFN (intermediate 10240),
#   RMS norm (zero-centered), sliding window 1024 on non-global layers,
#   global attention every 6th layer with RoPE base 1M (vs 10K local).

def get_decoder_config_4b() -> cfg.ModelConfig:
    """Build the LiteRT ModelConfig matching MedGemma-1.5-4B-IT architecture."""

    norm_config = cfg.NormalizationConfig(
        type=cfg.NormalizationType.RMS_NORM,
        epsilon=1e-6,
        zero_centered=True,
    )

    ff_config = cfg.FeedForwardConfig(
        type=cfg.FeedForwardType.GATED,
        activation=cfg.ActivationConfig(cfg.ActivationType.GELU_TANH),
        intermediate_size=10240,
        pre_ff_norm_config=norm_config,
        post_ff_norm_config=norm_config,
    )

    def get_block_config(idx: int) -> cfg.TransformerBlockConfig:
        # Every 6th layer (1-indexed: 6,12,18,24,30) uses GLOBAL attention
        # with RoPE base 1,000,000. All others use LOCAL_SLIDING (window=1024)
        # with RoPE base 10,000.
        is_global = (idx + 1) % 6 == 0

        attn_config = cfg.AttentionConfig(
            num_heads=8,
            head_dim=256,
            num_query_groups=4,           # GQA: 8 Q heads / 4 KV heads
            rotary_base=1_000_000 if is_global else 10_000,
            rotary_percentage=1.0,
            qkv_transpose_before_split=True,
            query_norm_config=norm_config,
            key_norm_config=norm_config,
            logit_softcap=None,
            sliding_window_size=1024,
            attn_type=(
                cfg.AttentionType.GLOBAL if is_global
                else cfg.AttentionType.LOCAL_SLIDING
            ),
        )

        return cfg.TransformerBlockConfig(
            attn_config=attn_config,
            ff_config=ff_config,
            pre_attention_norm_config=norm_config,
            post_attention_norm_config=norm_config,
        )

    return cfg.ModelConfig(
        vocab_size=262_208,
        num_layers=34,
        max_seq_len=131072,
        embedding_dim=2560,
        embedding_scale=2560**0.5,        # √d_model scaling on embeddings
        block_configs=[get_block_config(i) for i in range(34)],
        final_norm_config=norm_config,
        lm_head_use_bias=False,
        final_logit_softcap=None,
    )


# =============================================================================
# 4. INITIALIZE THE LITERT DECODER MODEL
# =============================================================================
print("\n--- Initializing LiteRT Decoder ---")
config = get_decoder_config_4b()

# CRITICAL: disable fused-interleaved QKV layout.
# MedGemma stores Q/K/V as separate projections; we cat them [Q,K,V] in order.
for block in config.block_configs:
    block.attn_config.qkv_fused_interleaved = False

model = decoder.Decoder(config, mask_cache_size=0)
model.eval()
print(f"  Decoder created — {sum(p.numel() for p in model.parameters()):,} parameters")


# =============================================================================
# 5. LOAD SAFETENSOR WEIGHTS
# =============================================================================
print("\n--- Loading Weights from Safetensors ---")
full_state_dict = {}
safetensor_files = sorted(glob.glob(os.path.join(MODEL_PATH, "*.safetensors")))
for f in safetensor_files:
    if "index.json" in f:
        continue
    print(f"  Loading {os.path.basename(f)}...")
    full_state_dict.update(load_file(f))
print(f"  Total keys loaded: {len(full_state_dict)}")


# =============================================================================
# 6. REMAP WEIGHTS:  HuggingFace → LiteRT Decoder
# =============================================================================
# The HF model uses separate q_proj / k_proj / v_proj weights.
# The LiteRT Decoder expects a single fused qkv_projection weight.
# We also translate all the layer-norm and FFN key names.

print("\n--- Remapping Weight Keys ---")

def safe_copy(target: torch.Tensor, source: torch.Tensor, name: str = ""):
    """Copy source into target, handling dtype/shape/transpose mismatches."""
    if source.dtype != target.dtype:
        source = source.to(target.dtype)
    if source.shape == target.shape:
        target.data.copy_(source)
    elif source.T.shape == target.shape:
        print(f"  ⚠️ Transposing {name}: {source.shape} → {target.shape}")
        target.data.copy_(source.T)
    elif source.numel() == target.numel():
        print(f"  ⚠️ Reshaping {name}: {source.shape} → {target.shape}")
        target.data.copy_(source.view_as(target))
    else:
        raise ValueError(
            f"❌ Shape mismatch for {name}: target {target.shape} vs source {source.shape}"
        )


model_state = model.state_dict()
remapped_count = 0

# ── 6a. Fuse Q/K/V projections per layer ────────────────────────────────────
print("  Processing QKV fusion (34 layers)...")
for i in range(34):
    # Try both possible HF key prefixes
    prefix = f"language_model.model.layers.{i}.self_attn"
    q = full_state_dict.get(f"{prefix}.q_proj.weight")
    k = full_state_dict.get(f"{prefix}.k_proj.weight")
    v = full_state_dict.get(f"{prefix}.v_proj.weight")
    if q is None:
        prefix = f"model.layers.{i}.self_attn"
        q = full_state_dict.get(f"{prefix}.q_proj.weight")
        k = full_state_dict.get(f"{prefix}.k_proj.weight")
        v = full_state_dict.get(f"{prefix}.v_proj.weight")

    if q is None or k is None or v is None:
        print(f"  ❌ Layer {i}: Q/K/V not found — skipping")
        continue

    target_name = f"transformer_blocks.{i}.atten_func.qkv_projection.weight"
    tgt = model_state.get(target_name)
    if tgt is None:
        print(f"  ❌ Target {target_name} not found — skipping layer {i}")
        continue

    # Cast to target dtype
    q = q.to(tgt.dtype)
    k = k.to(tgt.dtype)
    v = v.to(tgt.dtype)

    # Ensure concat dimension matches target input dim
    in_dim_tgt = tgt.shape[1]
    if q.shape[1] != in_dim_tgt:
        q, k, v = q.T, k.T, v.T

    try:
        fused = torch.cat([q, k, v], dim=0)
        if fused.shape == tgt.shape:
            safe_copy(tgt, fused, name=target_name)
        elif fused.T.shape == tgt.shape:
            safe_copy(tgt, fused.T, name=target_name)
        else:
            raise RuntimeError(f"Shape mismatch: fused {fused.shape} vs target {tgt.shape}")
        remapped_count += 3

        # Remove consumed keys
        full_state_dict.pop(f"{prefix}.q_proj.weight", None)
        full_state_dict.pop(f"{prefix}.k_proj.weight", None)
        full_state_dict.pop(f"{prefix}.v_proj.weight", None)
    except Exception as e:
        print(f"  ❌ Error fusing layer {i}: {e}")

# ── 6b. Remap all remaining keys ────────────────────────────────────────────
print("  Processing remaining keys...")
for hf_key, tensor in list(full_state_dict.items()):
    # Skip vision / multimodal keys — we only convert the text decoder
    if "vision" in hf_key or "multi_modal" in hf_key:
        continue

    # Strip common HF prefixes
    key = hf_key.replace("language_model.model.", "").replace("model.", "")
    target_key = None

    # ── Embedding / head / final norm ────
    if key == "embed_tokens.weight":
        target_key = "tok_embedding.weight"
    elif key == "norm.weight":
        target_key = "final_norm.weight"
    elif key == "lm_head.weight":
        target_key = "lm_head.weight"
    # ── Per-layer norms ────
    elif ".input_layernorm.weight" in key:
        l = key.split(".")[1]
        target_key = f"transformer_blocks.{l}.pre_atten_norm.weight"
    elif ".post_attention_layernorm.weight" in key:
        l = key.split(".")[1]
        target_key = f"transformer_blocks.{l}.post_atten_norm.weight"
    elif ".pre_feedforward_layernorm.weight" in key:
        l = key.split(".")[1]
        target_key = f"transformer_blocks.{l}.ff.pre_ff_norm.weight"
    elif ".post_feedforward_layernorm.weight" in key:
        l = key.split(".")[1]
        target_key = f"transformer_blocks.{l}.ff.post_ff_norm.weight"
    # ── FFN weights ────
    elif ".mlp.gate_proj.weight" in key:
        l = key.split(".")[1]
        target_key = f"transformer_blocks.{l}.ff.w1.weight"
    elif ".mlp.down_proj.weight" in key:
        l = key.split(".")[1]
        target_key = f"transformer_blocks.{l}.ff.w2.weight"
    elif ".mlp.up_proj.weight" in key:
        l = key.split(".")[1]
        target_key = f"transformer_blocks.{l}.ff.w3.weight"
    # ── Attention norms & output projection ────
    elif ".self_attn.q_norm.weight" in key:
        l = key.split(".")[1]
        target_key = f"transformer_blocks.{l}.atten_func.query_norm.weight"
    elif ".self_attn.k_norm.weight" in key:
        l = key.split(".")[1]
        target_key = f"transformer_blocks.{l}.atten_func.key_norm.weight"
    elif ".self_attn.o_proj.weight" in key:
        l = key.split(".")[1]
        target_key = f"transformer_blocks.{l}.atten_func.output_projection.weight"

    if target_key:
        if target_key in model_state:
            safe_copy(model_state[target_key], tensor, name=target_key)
            remapped_count += 1
        else:
            print(f"  ❓ Unmapped target: {target_key} (source: {hf_key})")

print(f"  Total keys remapped: {remapped_count}")

# Free the raw HF state dict — we no longer need it
del full_state_dict
gc.collect()


# =============================================================================
# 7. CONVERT TO TFLITE
# =============================================================================
# Build a descriptive output filename so you know exactly what was built.
quant_tag = {
    QuantizationName.DYNAMIC_INT4_BLOCK128: "int4_q4b128",
    QuantizationName.DYNAMIC_INT8:          "int8",
}.get(QUANTIZE, "quant")

output_name = f"medgemma_4b_mobile_{quant_tag}_ekv{KV_CACHE_MAX_LEN}"

print(f"\n--- Converting to TFLite ---")
print(f"  Output:         {output_name}.tflite")
print(f"  kv_cache_max:   {KV_CACHE_MAX_LEN}")
print(f"  prefill_seq:    {PREFILL_SEQ_LEN}")
print(f"  quantization:   {QUANTIZE}")

export_config = ExportConfig()
export_config.kvcache_layout = kv_utils.KV_LAYOUT_TRANSPOSED
export_config.mask_as_input = True

converter.convert_to_tflite(
    model,
    output_path=OUTPUT_DIR,
    output_name_prefix=output_name,
    prefill_seq_len=PREFILL_SEQ_LEN,
    kv_cache_max_len=KV_CACHE_MAX_LEN,
    quantize=QUANTIZE,
    export_config=export_config,
)

print(f"\n✅ SUCCESS → {OUTPUT_DIR}/{output_name}*.tflite")
print()
print("📱 UPDATE YOUR ANDROID APP:")
print(f'   Model.kt → path = "{output_name}.tflite"')
print(f"   Model.kt → kvCacheMaxLen = {KV_CACHE_MAX_LEN}")
print(f"   LiteRTInferenceEngine.kt → prefill auto-detected from signature name")


# =============================================================================
# 8. QUICK SANITY CHECK (optional — runs a single decode step)
# =============================================================================
print("\n--- Quick Sanity Check ---")
try:
    import numpy as np
    from ai_edge_litert import interpreter as tfl_interpreter

    files = glob.glob(os.path.join(OUTPUT_DIR, "*.tflite"))
    if files:
        tflite_path = files[0]
        print(f"  Testing: {tflite_path}")

        interp = tfl_interpreter.Interpreter(model_path=tflite_path)
        runner = interp.get_signature_runner("decode")

        # Build dummy KV caches (all zeros)
        caches = {}
        for i in range(34):
            caches[f"kv_cache_k_{i}"] = np.zeros(
                (1, 4, KV_CACHE_MAX_LEN, 256), dtype=np.float32
            )
            caches[f"kv_cache_v_{i}"] = np.zeros(
                (1, 4, 256, KV_CACHE_MAX_LEN), dtype=np.float32
            )

        inputs = {
            "tokens":    np.array([[1000]], dtype=np.int32),
            "input_pos": np.array([0], dtype=np.int32),
            "mask":      np.zeros((1, 1, 1, KV_CACHE_MAX_LEN), dtype=np.float32),
            **caches,
        }

        results = runner(**inputs)
        logits = results["logits"]

        if np.isnan(logits).any():
            print("  ❌ FAILED: logits contain NaN")
        else:
            print(f"  Logits mean: {np.mean(logits):.4f}")
            print("  ✅ Model produces valid numbers!")
    else:
        print("  ⚠️ No .tflite file found — skipping sanity check")
except ImportError:
    print("  ⚠️ ai_edge_litert not available — skipping sanity check")


# =============================================================================
# 9. UPLOAD TO HUGGING FACE (optional)
# =============================================================================
# Uncomment the block below to auto-upload the model to your HF repo.
#
# from huggingface_hub import HfApi
#
# files = glob.glob(os.path.join(OUTPUT_DIR, "*.tflite"))
# if not files:
#     raise ValueError("No file to upload!")
#
# tflite_path = files[0]
# filename = os.path.basename(tflite_path)
#
# api = HfApi()
# username = api.whoami()["name"]
# repo_id = f"{username}/medgemma-4b-tflite"
#
# print(f"Uploading {filename} to {repo_id}...")
# api.create_repo(repo_id=repo_id, private=True, exist_ok=True)
# api.upload_file(
#     path_or_fileobj=tflite_path,
#     path_in_repo=filename,
#     repo_id=repo_id,
#     repo_type="model",
# )
# print(f"✅ UPLOAD DONE! https://huggingface.co/{repo_id}")
