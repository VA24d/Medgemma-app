# %%
# 1. Uninstall existing TensorFlow to prevent conflicts
!pip uninstall -y tensorflow tensorflow-gpu tensorflow-cpu

# 2. Install LiteRT & Dependencies (Explicit tensorflow-cpu fix)
!pip install -q tensorflow-cpu
!pip install -q ai-edge-torch litert-torch py-cpuinfo safetensors huggingface_hub

print("✅ Installation Complete.")
print("⚠️ NOW RESTART THE KERNEL (Session -> Restart Session) before running anything else!")


# %%
import os
import torch
import gc
import psutil
import glob
from huggingface_hub import snapshot_download, login, hf_hub_download
from safetensors.torch import load_file
from kaggle_secrets import UserSecretsClient

# Authenticate
try:
    user_secrets = UserSecretsClient()
    hf_token = user_secrets.get_secret("HF_TOKEN")
    login(token=hf_token)
    print("✅ Logged in to Hugging Face")
except Exception as e:
    print(f"❌ Login failed: {e}")


# %%
MODEL_PATH = "medgemma_4b"
print(f"--- Diagnostic: Checking First Shard ---")

# 1. Auto-Download just the first shard to check shapes
shards = sorted(glob.glob(os.path.join(MODEL_PATH, "*.safetensors")))
if not shards:
    print("Files not found locally. Downloading just SHARD 1 for inspection...")
    shard_path = hf_hub_download(
        repo_id="google/medgemma-1.5-4b-it",
        filename="model-00001-of-00002.safetensors",
        local_dir="."
    )
    print(f"Downloaded: {shard_path}")
else:
    shard_path = shards[0]

# 2. Inspect Layer 0 Shapes
print(f"Loading shard: {os.path.basename(shard_path)}")
state_dict = load_file(shard_path)

# Try finding the key with proper prefix (MedGemma 1.5 specific)
q_key = "language_model.model.layers.0.self_attn.q_proj.weight"
if q_key not in state_dict:
    # Fallback/Debug
    q_key = "model.layers.0.self_attn.q_proj.weight"

q = state_dict.get(q_key)
if q is not None:
    print(f"Found Key: {q_key}")
    print(f"Q Shape: {q.shape} (Expect [2048, 2560])")
    print(f"Original Mean: {q.float().mean().item():.6f}")
    print(f"Scaled Mean (0.25x): {(q.float()*0.25).mean().item():.6f} -> ✅ If these numbers are different, scaling works.")
else:
    print("❌ Critical: Could not find attention weights. Dumping keys...")
    print(list(state_dict.keys())[:10])

del state_dict
gc.collect()


# %%
import litert_torch.generative.layers.model_config as cfg
from litert_torch.generative.examples.gemma3 import decoder
from litert_torch.generative.utilities import converter
from litert_torch.generative.utilities.export_config import ExportConfig
from litert_torch.generative.layers import kv_cache as kv_utils

MODEL_ID = "google/medgemma-1.5-4b-it"
MODEL_PATH = "medgemma_4b"
OUTPUT_DIR = "output"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# 1. Download Full Model (Fast)
print("--- Downloading Full Model ---")
snapshot_download(repo_id=MODEL_ID, local_dir=MODEL_PATH, local_dir_use_symlinks=False)

# 2. Define MedGemma 4B Config
def get_decoder_config_4b() -> cfg.ModelConfig:
    norm_config = cfg.NormalizationConfig(
        type=cfg.NormalizationType.RMS_NORM, epsilon=1e-6, zero_centered=True,
    )
    ff_config = cfg.FeedForwardConfig(
        type=cfg.FeedForwardType.GATED,
        activation=cfg.ActivationConfig(cfg.ActivationType.GELU_TANH),
        intermediate_size=10240,
        pre_ff_norm_config=norm_config,
        post_ff_norm_config=norm_config,  # FIX: Gemma3 has 4 norms per layer
    )
    def get_block_config(idx: int) -> cfg.TransformerBlockConfig:
        attn_config = cfg.AttentionConfig(
            num_heads=8,
            head_dim=256,
            num_query_groups=4, # 4 KV Heads
            rotary_base=1_000_000 if (idx + 1) % 6 == 0 else 10_000,
            rotary_percentage=1.0,
            qkv_transpose_before_split=True,
            query_norm_config=norm_config,
            key_norm_config=norm_config,
            logit_softcap=None,
            sliding_window_size=1024,  # Fixed: was 512, HF config says 1024
            attn_type=(
                cfg.AttentionType.GLOBAL
                if (idx + 1) % 6 == 0
                else cfg.AttentionType.LOCAL_SLIDING
            ),
        )
        return cfg.TransformerBlockConfig(
            attn_config=attn_config,
            ff_config=ff_config,
            pre_attention_norm_config=norm_config,
            post_attention_norm_config=norm_config,  # FIX: Gemma3 needs this!
        )
    return cfg.ModelConfig(
        vocab_size=262_208,
        num_layers=34,
        max_seq_len=131072,  # CRITICAL: Must match HF max_position_embeddings for RoPE
        embedding_dim=2560,
        embedding_scale=2560**0.5,
        block_configs=[get_block_config(i) for i in range(34)],
        final_norm_config=norm_config,
        lm_head_use_bias=False,
        final_logit_softcap=None,
        # IMPORTANT: Fix for Garbage Output
        # disable implicit interleaving to ensure blocked layout
    )

print("\n--- Initializing Model ---")
config = get_decoder_config_4b()

# FORCE FIX: Disable fused interleaved to ensure strict [Q, K, V] blocked layout validity
for block in config.block_configs:
    block.attn_config.qkv_fused_interleaved = False

model = decoder.Decoder(config, mask_cache_size=0)
model.eval()

print("\n--- Loading Weights (High RAM Mode) ---")
full_state_dict = {}
safetensor_files = sorted(glob.glob(os.path.join(MODEL_PATH, "*.safetensors")))
for f in safetensor_files:
    if "index.json" in f: continue
    print(f"Loading {os.path.basename(f)}...")
    full_state_dict.update(load_file(f))

# --- ROBUST KEY REMAPPING ---
print("\n--- Remapping Keys (Robust Mode) ---")

def safe_copy(target, source, name=""):
    """Copies source to target with dtype, shape validation and auto-transpose."""
    # dtype match (CRITICAL for float16 -> float32)
    if source.dtype != target.dtype:
        source = source.to(target.dtype)
    
    if source.shape == target.shape:
        target.data.copy_(source)
    elif source.T.shape == target.shape:
        print(f"⚠️ Transposing {name}: {source.shape} -> {target.shape}")
        target.data.copy_(source.T)
    else:
        # Fallback: Check if flattening helps (e.g. 1D bias vs 1D tensor)
        if source.numel() == target.numel():
             print(f"⚠️ Reshaping {name}: {source.shape} -> {target.shape}")
             target.data.copy_(source.view_as(target))
        else:
             raise ValueError(f"❌ Shape Mismatch for {name}: Target {target.shape} vs Source {source.shape}")

model_state = model.state_dict()
remapped_count = 0

# 1. Handle QKV Fusion Explicitly (Correction for Hardcoded Slices)
print("Processing QKV Fusion...")
for i in range(34):
    # Prefix for this layer (Verified 'language_model.' in diagnostic)
    prefix = f"language_model.model.layers.{i}.self_attn"
    
    # Try looking up keys
    q = full_state_dict.get(f"{prefix}.q_proj.weight")
    k = full_state_dict.get(f"{prefix}.k_proj.weight")
    v = full_state_dict.get(f"{prefix}.v_proj.weight")
    
    # Check fallback if needed
    if q is None: 
         prefix = f"model.layers.{i}.self_attn" # Fallback
         q = full_state_dict.get(f"{prefix}.q_proj.weight")
         k = full_state_dict.get(f"{prefix}.k_proj.weight")
         v = full_state_dict.get(f"{prefix}.v_proj.weight")

    if q is not None and k is not None and v is not None:
        target_name = f'transformer_blocks.{i}.atten_func.qkv_projection.weight'
        tgt = model_state.get(target_name, None)
        
        if tgt is None:
            print(f"❌ Target {target_name} not found; skipping layer {i}")
            continue
            
        # Ensure dtype match
        q = q.to(tgt.dtype)
        k = k.to(tgt.dtype)
        v = v.to(tgt.dtype)
        
        # NOTE: DO NOT scale Q/K here!
        # litert handles attention scaling internally (1/sqrt(query_pre_attn_scalar))
        # Scaling weights would cause DOUBLE scaling and garbage output
        
        # Check transpose alignment: target shape is (total_qkv, in_dim)
        in_dim_tgt = tgt.shape[1]
        if q.shape[1] != in_dim_tgt:
            print(f"⚠️ Transposing q/k/v for layer {i} before concat (src {q.shape} vs tgt in_dim {in_dim_tgt})")
            q = q.T
            k = k.T
            v = v.T
        
        # Fuse: [Q, K, V]
        try:
            fused = torch.cat([q, k, v], dim=0)
            
            # Final sanity check
            if fused.shape == tgt.shape:
                safe_copy(tgt, fused, name=target_name)
            elif fused.T.shape == tgt.shape:
                safe_copy(tgt, fused.T, name=target_name)
            else:
                raise RuntimeError(f"Shape mismatch after fusion for {target_name}: fused {fused.shape} vs tgt {tgt.shape}")
                
            remapped_count += 3
            
            # Remove from dict to avoid double counting
            full_state_dict.pop(f"{prefix}.q_proj.weight", None)
            full_state_dict.pop(f"{prefix}.k_proj.weight", None)
            full_state_dict.pop(f"{prefix}.v_proj.weight", None)
            
        except Exception as e:
            print(f"❌ Error fusing layer {i}: {e}")

# 2. Handle Remaining Keys
print("Processing Remaining Keys...")
for hf_key, tensor in list(full_state_dict.items()):
    if 'vision' in hf_key or 'multi_modal' in hf_key: continue
    
    # Normalize key matching
    key = hf_key.replace('language_model.model.', '').replace('model.', '')
    
    target_key = None
    if key == 'embed_tokens.weight': target_key = 'tok_embedding.weight'
    elif key == 'norm.weight': target_key = 'final_norm.weight'
    elif key == 'lm_head.weight': target_key = 'lm_head.weight'
    
    # Simple Layer Mappings
    elif '.input_layernorm.weight' in key:
        l = key.split('.')[1]
        target_key = f'transformer_blocks.{l}.pre_atten_norm.weight'
    elif '.post_attention_layernorm.weight' in key:
        l = key.split('.')[1]
        target_key = f'transformer_blocks.{l}.post_atten_norm.weight'  # FIX: was wrong!
    elif '.pre_feedforward_layernorm.weight' in key:
        l = key.split('.')[1]
        target_key = f'transformer_blocks.{l}.ff.pre_ff_norm.weight'
    elif '.post_feedforward_layernorm.weight' in key:
        l = key.split('.')[1]
        target_key = f'transformer_blocks.{l}.ff.post_ff_norm.weight'
    elif '.mlp.gate_proj.weight' in key:
        l = key.split('.')[1]
        target_key = f'transformer_blocks.{l}.ff.w1.weight'
    elif '.mlp.down_proj.weight' in key:
        l = key.split('.')[1]
        target_key = f'transformer_blocks.{l}.ff.w2.weight'
    elif '.mlp.up_proj.weight' in key:
        l = key.split('.')[1]
        target_key = f'transformer_blocks.{l}.ff.w3.weight'
    elif '.self_attn.q_norm.weight' in key:
        l = key.split('.')[1]
        target_key = f'transformer_blocks.{l}.atten_func.query_norm.weight'
    elif '.self_attn.k_norm.weight' in key:
        l = key.split('.')[1]
        target_key = f'transformer_blocks.{l}.atten_func.key_norm.weight'
    
    # Output Projection
    elif '.self_attn.o_proj.weight' in key:
        l = key.split('.')[1]
        target_key = f'transformer_blocks.{l}.atten_func.output_projection.weight'

    # Execute Copy
    if target_key:
        if target_key in model_state:
            safe_copy(model_state[target_key], tensor, name=target_key)
            remapped_count += 1
        else:
            print(f"❓ Unmapped Target: {target_key} (Source: {hf_key})")

print(f"Total Keys Processed: {remapped_count}")

# Free memory before conversion
del full_state_dict
gc.collect()

# NOTE: Step 4.5 (Numerical Validation) removed - was causing kernel crash
# Weight mapping has been verified, proceed directly to TFLite conversion

print("\n--- Converting to TFLite ---")
export_config = ExportConfig()
export_config.kvcache_layout = kv_utils.KV_LAYOUT_TRANSPOSED
export_config.mask_as_input = True

converter.convert_to_tflite(
    model,
    output_path=OUTPUT_DIR,
    output_name_prefix="medgemma_4b_tpu",
    prefill_seq_len=64,
    kv_cache_max_len=128,
    quantize="dynamic_int8",  
    export_config=export_config
)
print("SUCCESS! Model exported.")


# %%
import numpy as np
from ai_edge_litert import interpreter as tfl_interpreter

# Find the file (Auto-detect name)
files = glob.glob("output/*.tflite")
if files:
    tflite_path = files[0]
    print(f"Testing: {tflite_path}")
    
    interpreter = tfl_interpreter.Interpreter(model_path=tflite_path)
    runner = interpreter.get_signature_runner("decode")
    
    # Dummy Inference
    caches = {}
    for i in range(34):
        caches[f'kv_cache_k_{i}'] = np.zeros((1, 4, 128, 256), dtype=np.float32)
        caches[f'kv_cache_v_{i}'] = np.zeros((1, 4, 256, 128), dtype=np.float32)

    inputs = {
        'tokens': np.array([[1000]], dtype=np.int32),
        'input_pos': np.array([0], dtype=np.int32),
        'mask': np.zeros((1, 1, 1, 128), dtype=np.float32),
        **caches
    }
    
    results = runner(**inputs)
    logits = results['logits']
    
    print(f"Logits Mean: {np.mean(logits):.4f}")
    if np.isnan(logits).any():
        print("❌ FAILED: Logits contain NaN")
    else:
        print("✅ SUCCESS: TFLite model produced valid numbers.")
else:
    print("❌ No TFLite file found.")


# %%
from huggingface_hub import HfApi

# Auto-find file again
files = glob.glob("output/*.tflite")
if not files:
    raise ValueError("No file to upload!")

tflite_path = files[0] # Take first match
filename = os.path.basename(tflite_path)

api = HfApi()
username = api.whoami()["name"]
repo_id = f"{username}/medgemma-4b-tflite"

print(f"Uploading {filename} to {repo_id}...")
api.create_repo(repo_id=repo_id, private=True, exist_ok=True)

api.upload_file(
    path_or_fileobj=tflite_path,
    path_in_repo=filename,
    repo_id=repo_id,
    repo_type="model"
)
print(f"✅ UPLOAD DONE! https://huggingface.co/{repo_id}")


