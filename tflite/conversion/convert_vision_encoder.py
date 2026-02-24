#!/usr/bin/env python3
"""
MedGemma Vision Encoder + Projector Conversion Script
Based on your working text decoder conversion from note2.ipynb

This script extracts MedSigLIP vision encoder and multimodal projector
from google/medgemma-1.5-4b-it safetensors and converts to TFLite.

Author: Your competition team
Date: 2026-02-07
"""

import torch
import torch.nn as nn
import os
import glob
from safetensors.torch import load_file
import numpy as np

# ============== CONFIGURATION ==============
MODEL_PATH = "medgemma_4b"  # Same path as your notebook
OUTPUT_DIR = "output_vision"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# MedSigLIP specs from official docs
IMAGE_SIZE = 896  # Updated to match MedGemma 1.5 weights (4096 patches)
VISION_HIDDEN_SIZE = 1152  # SigLIP-So400m
TEXT_HIDDEN_SIZE = 2560  # Gemma 3 4B
NUM_LAYERS = 27
NUM_ATTENTION_HEADS = 16
INTERMEDIATE_SIZE = 4304
NUM_IMAGE_PATCHES = (IMAGE_SIZE // 14) ** 2  # 4096 for 896px


# ============== CLASS DEFINITIONS ==============

class SigLIPVisionEmbeddings(nn.Module):
    """Image to patch embeddings"""
    def __init__(self):
        super().__init__()
        self.patch_embedding = nn.Conv2d(
            in_channels=3,
            out_channels=VISION_HIDDEN_SIZE,
            kernel_size=14,
            stride=14,
            padding=0,
            bias=True
        )
        self.position_embedding = nn.Embedding(NUM_IMAGE_PATCHES, VISION_HIDDEN_SIZE)
        self.register_buffer(
            "position_ids",
            torch.arange(NUM_IMAGE_PATCHES).expand((1, -1)),
            persistent=False
        )
    
    def forward(self, pixel_values):
        # pixel_values: [batch, 3, 896, 896]
        patch_embeds = self.patch_embedding(pixel_values)  # [batch, 1152, 64, 64]
        batch, hidden, h, w = patch_embeds.shape
        patch_embeds = patch_embeds.flatten(2).transpose(1, 2)  # [batch, 4096, 1152]
        
        embeddings = patch_embeds + self.position_embedding(self.position_ids)
        return embeddings


class SigLIPAttention(nn.Module):
    """Multi-headed attention"""
    def __init__(self):
        super().__init__()
        self.num_heads = NUM_ATTENTION_HEADS
        self.head_dim = VISION_HIDDEN_SIZE // NUM_ATTENTION_HEADS
        
        self.q_proj = nn.Linear(VISION_HIDDEN_SIZE, VISION_HIDDEN_SIZE, bias=True)
        self.k_proj = nn.Linear(VISION_HIDDEN_SIZE, VISION_HIDDEN_SIZE, bias=True)
        self.v_proj = nn.Linear(VISION_HIDDEN_SIZE, VISION_HIDDEN_SIZE, bias=True)
        self.out_proj = nn.Linear(VISION_HIDDEN_SIZE, VISION_HIDDEN_SIZE, bias=True)
    
    def forward(self, x):
        batch, seq_len, embed_dim = x.shape
        
        q = self.q_proj(x).view(batch, seq_len, self.num_heads, self.head_dim).transpose(1, 2)
        k = self.k_proj(x).view(batch, seq_len, self.num_heads, self.head_dim).transpose(1, 2)
        v = self.v_proj(x).view(batch, seq_len, self.num_heads, self.head_dim).transpose(1, 2)
        
        # Scaled dot-product attention
        attn_weights = torch.matmul(q, k.transpose(-2, -1)) / (self.head_dim ** 0.5)
        attn_weights = torch.softmax(attn_weights, dim=-1)
        attn_output = torch.matmul(attn_weights, v)
        
        attn_output = attn_output.transpose(1, 2).contiguous().view(batch, seq_len, embed_dim)
        return self.out_proj(attn_output)


class SigLIPMLP(nn.Module):
    """Feed-forward network"""
    def __init__(self):
        super().__init__()
        self.fc1 = nn.Linear(VISION_HIDDEN_SIZE, INTERMEDIATE_SIZE, bias=True)
        self.fc2 = nn.Linear(INTERMEDIATE_SIZE, VISION_HIDDEN_SIZE, bias=True)
        self.activation = nn.GELU(approximate='tanh')  # GELUTanh from your config
    
    def forward(self, x):
        x = self.fc1(x)
        x = self.activation(x)
        x = self.fc2(x)
        return x


class SigLIPEncoderLayer(nn.Module):
    """Single transformer layer"""
    def __init__(self):
        super().__init__()
        self.self_attn = SigLIPAttention()
        self.layer_norm1 = nn.LayerNorm(VISION_HIDDEN_SIZE, eps=1e-6)
        self.mlp = SigLIPMLP()
        self.layer_norm2 = nn.LayerNorm(VISION_HIDDEN_SIZE, eps=1e-6)
    
    def forward(self, x):
        # Pre-LN architecture
        residual = x
        x = self.layer_norm1(x)
        x = self.self_attn(x)
        x = residual + x
        
        residual = x
        x = self.layer_norm2(x)
        x = self.mlp(x)
        x = residual + x
        return x


class SigLIPVisionModel(nn.Module):
    """Complete SigLIP vision encoder"""
    def __init__(self):
        super().__init__()
        self.embeddings = SigLIPVisionEmbeddings()
        self.encoder_layers = nn.ModuleList([
            SigLIPEncoderLayer() for _ in range(NUM_LAYERS)
        ])
        self.post_layernorm = nn.LayerNorm(VISION_HIDDEN_SIZE, eps=1e-6)
    
    def forward(self, pixel_values):
        x = self.embeddings(pixel_values)
        
        for layer in self.encoder_layers:
            x = layer(x)
        
        x = self.post_layernorm(x)
        return x  # [batch, 4096, 1152]


class MultiModalProjector(nn.Module):
    """Linear projection + Norm from vision to text space"""
    def __init__(self):
        super().__init__()
        # Based on keys: mm_input_projection_weight, mm_soft_emb_norm.weight
        self.norm = nn.LayerNorm(VISION_HIDDEN_SIZE, eps=1e-6) # 1152
        self.linear = nn.Linear(VISION_HIDDEN_SIZE, TEXT_HIDDEN_SIZE, bias=False) # 1152 -> 2560
    
    def forward(self, x):
        x = self.norm(x)
        x = self.linear(x)
        return x


def safe_copy(target, source, name=""):
    """Safe tensor copy with dtype/shape validation"""
    # Dtype conversion
    if source.dtype != target.dtype:
        source = source.to(target.dtype)
    
    # Shape matching
    if source.shape == target.shape:
        target.data.copy_(source)
    elif source.T.shape == target.shape:
        print(f"  ⚠️  Transposing {name}: {source.shape} -> {target.shape}")
        target.data.copy_(source.T)
    elif source.numel() == target.numel():
        print(f"  ⚠️  Reshaping {name}: {source.shape} -> {target.shape}")
        target.data.copy_(source.view_as(target))
    else:
        raise ValueError(f"Shape mismatch for {name}: Target {target.shape} vs Source {source.shape}")


def main():
    print("=" * 60)
    print("MedGemma Vision Encoder Extraction Script")
    print("=" * 60)
    print(f"Model Path: {MODEL_PATH}")
    print(f"Image Size: {IMAGE_SIZE}x{IMAGE_SIZE}")
    print(f"Num Patches: {NUM_IMAGE_PATCHES}")
    print(f"Vision Hidden: {VISION_HIDDEN_SIZE}")
    print(f"Text Hidden: {TEXT_HIDDEN_SIZE}")
    print("=" * 60)

    # ============== STEP 1: LOAD SAFETENSORS ==============
    print("\n[1/5] Loading safetensors...")

    safetensor_files = sorted(glob.glob(os.path.join(MODEL_PATH, "*.safetensors")))
    safetensor_files = [f for f in safetensor_files if "index.json" not in f]

    if not safetensor_files:
        raise FileNotFoundError(f"No safetensors found in {MODEL_PATH}. Download the model first!")

    full_state_dict = {}
    for f in safetensor_files:
        print(f"  Loading {os.path.basename(f)}...")
        full_state_dict.update(load_file(f))

    print(f"  ✅ Loaded {len(full_state_dict)} total tensors")

    # ============== STEP 2: EXTRACT VISION ENCODER WEIGHTS ==============
    print("\n[2/5] Extracting Vision Encoder weights...")

    # Separate vision weights from full state dict
    vision_state_dict = {}
    for hf_key, tensor in full_state_dict.items():
        if hf_key.startswith("vision_tower."):
            # Keep full key for now - we'll map later
            vision_state_dict[hf_key] = tensor

    if not vision_state_dict:
        # Fallback to old name just in case
        for hf_key, tensor in full_state_dict.items():
            if hf_key.startswith("vision_model."):
                vision_state_dict[hf_key] = tensor

    if not vision_state_dict:
        raise ValueError("No vision_tower.* or vision_model.* weights found! Are you using the right model?")

    print(f"  ✅ Extracted {len(vision_state_dict)} vision encoder tensors")

    # Print sample keys for verification
    print("\n  Sample vision keys:")
    sample_keys = list(vision_state_dict.keys())[:5]
    for key in sample_keys:
        shape = vision_state_dict[key].shape
        print(f"    {key}: {shape}")

    # ============== STEP 3: DEFINE PYTORCH SIGLIP MODEL ==============
    print("\n[3/5] Building SigLIP Vision Encoder architecture...")
    
    # Initialize model
    vision_model = SigLIPVisionModel()
    print(f"  ✅ Created SigLIP model with {NUM_LAYERS} layers")

    # ============== STEP 4: MAP WEIGHTS (CRITICAL!) ==============
    print("\n[4/5] Mapping weights to PyTorch model...")

    model_state = vision_model.state_dict()
    mapped_count = 0

    # Mapping rules (HF -> PyTorch)
    for hf_key, tensor in vision_state_dict.items():
        # Remove prefixes
        key = hf_key.replace("vision_tower.vision_model.", "").replace("vision_model.vision_model.", "")
        
        target_key = None
        
        # Embeddings
        if key == "embeddings.patch_embedding.weight":
            target_key = "embeddings.patch_embedding.weight"
        elif key == "embeddings.patch_embedding.bias":
            target_key = "embeddings.patch_embedding.bias"
        elif key == "embeddings.position_embedding.weight":
            target_key = "embeddings.position_embedding.weight"
        
        # Post layernorm
        elif key == "post_layernorm.weight":
            target_key = "post_layernorm.weight"
        elif key == "post_layernorm.bias":
            target_key = "post_layernorm.bias"
        
        # Encoder layers
        elif key.startswith("encoder.layers."):
            # Extract layer number
            parts = key.split(".")
            layer_idx = int(parts[2])
            remaining = ".".join(parts[3:])
            
            # Map component names
            if remaining == "self_attn.q_proj.weight":
                target_key = f"encoder_layers.{layer_idx}.self_attn.q_proj.weight"
            elif remaining == "self_attn.q_proj.bias":
                target_key = f"encoder_layers.{layer_idx}.self_attn.q_proj.bias"
            elif remaining == "self_attn.k_proj.weight":
                target_key = f"encoder_layers.{layer_idx}.self_attn.k_proj.weight"
            elif remaining == "self_attn.k_proj.bias":
                target_key = f"encoder_layers.{layer_idx}.self_attn.k_proj.bias"
            elif remaining == "self_attn.v_proj.weight":
                target_key = f"encoder_layers.{layer_idx}.self_attn.v_proj.weight"
            elif remaining == "self_attn.v_proj.bias":
                target_key = f"encoder_layers.{layer_idx}.self_attn.v_proj.bias"
            elif remaining == "self_attn.out_proj.weight":
                target_key = f"encoder_layers.{layer_idx}.self_attn.out_proj.weight"
            elif remaining == "self_attn.out_proj.bias":
                target_key = f"encoder_layers.{layer_idx}.self_attn.out_proj.bias"
            elif remaining == "layer_norm1.weight":
                target_key = f"encoder_layers.{layer_idx}.layer_norm1.weight"
            elif remaining == "layer_norm1.bias":
                target_key = f"encoder_layers.{layer_idx}.layer_norm1.bias"
            elif remaining == "mlp.fc1.weight":
                target_key = f"encoder_layers.{layer_idx}.mlp.fc1.weight"
            elif remaining == "mlp.fc1.bias":
                target_key = f"encoder_layers.{layer_idx}.mlp.fc1.bias"
            elif remaining == "mlp.fc2.weight":
                target_key = f"encoder_layers.{layer_idx}.mlp.fc2.weight"
            elif remaining == "mlp.fc2.bias":
                target_key = f"encoder_layers.{layer_idx}.mlp.fc2.bias"
            elif remaining == "layer_norm2.weight":
                target_key = f"encoder_layers.{layer_idx}.layer_norm2.weight"
            elif remaining == "layer_norm2.bias":
                target_key = f"encoder_layers.{layer_idx}.layer_norm2.bias"
        
        # Copy if mapped
        if target_key:
            if target_key in model_state:
                safe_copy(model_state[target_key], tensor, name=target_key)
                mapped_count += 1
            else:
                print(f"  ⚠️  Target key not found: {target_key}")
        else:
            print(f"  ⚠️  Unmapped HF key: {key}")

    print(f"  ✅ Mapped {mapped_count} vision encoder parameters")

    # Load mapped weights
    vision_model.load_state_dict(model_state)
    vision_model.float() # Force float32
    vision_model.eval()

    # ============== TEST VISION ENCODER ==============
    print("\n  Testing vision encoder...")
    dummy_image = torch.randn(1, 3, IMAGE_SIZE, IMAGE_SIZE)
    with torch.no_grad():
        output = vision_model(dummy_image)
        print(f"  ✅ Output shape: {output.shape}")  # Should be [1, 1024, 1152]
        print(f"  ✅ Mean: {output.mean().item():.6f}, Std: {output.std().item():.6f}")

    # ============== STEP 5: MULTIMODAL PROJECTOR ==============
    print("\n[5/5] Extracting Multimodal Projector...")

    projector = MultiModalProjector()

    # Extract projector weights
    projector_state_dict = {}
    for hf_key, tensor in full_state_dict.items():
        if "multi_modal_projector" in hf_key:
            key = hf_key.replace("multi_modal_projector.", "")
            
            # 1. Linear Projection
            if "mm_input_projection_weight" in key:
                print(f"    Found projector weight: {key} {tensor.shape}")
                # Check for transpose: [1152, 2560] -> [2560, 1152]
                if tensor.shape == (VISION_HIDDEN_SIZE, TEXT_HIDDEN_SIZE):
                     print(f"    ⚠️ Transposing projector weight")
                     projector_state_dict["linear.weight"] = tensor.T
                else:
                     projector_state_dict["linear.weight"] = tensor
                     
            # 2. Normalization
            elif "mm_soft_emb_norm.weight" in key:
                print(f"    Found norm weight: {key} {tensor.shape}")
                projector_state_dict["norm.weight"] = tensor
            elif "mm_soft_emb_norm.bias" in key:
                print(f"    Found norm bias: {key} {tensor.shape}")
                projector_state_dict["norm.bias"] = tensor

    if not projector_state_dict:
        raise ValueError("No multi_modal_projector.* weights found!")

    print(f"  ✅ Found {len(projector_state_dict)} projector parameters")
    for key, val in projector_state_dict.items():
        print(f"    {key}: {val.shape}")

    # Load projector weights
    # Load projector weights manually to handle missing bias
    try:
        with torch.no_grad():
            # Linear (Linear is now second in forward, but loading doesn't care about order)
            if "linear.weight" in projector_state_dict:
                projector.linear.weight.copy_(projector_state_dict["linear.weight"])
            if "linear.bias" in projector_state_dict:
                projector.linear.bias.copy_(projector_state_dict["linear.bias"])
            
            # Norm
            if "norm.weight" in projector_state_dict:
                projector.norm.weight.copy_(projector_state_dict["norm.weight"])
            
            # Norm Bias (Handle missing)
            if "norm.bias" in projector_state_dict:
                projector.norm.bias.copy_(projector_state_dict["norm.bias"])
            else:
                print("    ⚠️ Note: norm.bias not found in weights. Initializing to zeros.")
                nn.init.zeros_(projector.norm.bias)

        print("  ✅ Projector weights loaded successfully")

    except Exception as e:
        print(f"Error loading projector: {e}")
        print("Keys in dict:", projector_state_dict.keys())
        print("Keys in model:", projector.state_dict().keys())

    projector.float() # Force float32
    projector.eval()

    # Test projector
    print("\n  Testing projector...")
    with torch.no_grad():
        projected = projector(output)
        print(f"  ✅ Projected shape: {projected.shape}")  # Should be [1, 1024, 2560]
        print(f"  ✅ Mean: {projected.mean().item():.6f}, Std: {projected.std().item():.6f}")

    # ============== SAVE PYTORCH MODELS ==============
    print("\n[BONUS] Saving PyTorch checkpoints...")
    torch.save(vision_model.state_dict(), os.path.join(OUTPUT_DIR, f"medsiglip_vision_{IMAGE_SIZE}.pth"))
    torch.save(projector.state_dict(), os.path.join(OUTPUT_DIR, "multimodal_projector.pth"))
    print("  ✅ Saved PyTorch .pth files")

    # ============== CONVERT TO TFLITE ==============
    print("\n[6/6] Converting to TFLite (OPTIONAL - requires litert-torch)...")

    try:
        # Try importing litert_torch first
        try:
            import litert_torch
            converter = litert_torch
            print("  Using litert_torch")
        except ImportError:
            try:
                import ai_edge_torch
                converter = ai_edge_torch
                print("  Using ai_edge_torch")
            except ImportError:
                 raise ImportError("litert-torch not found")

        # Vision encoder wrapper
        class VisionWrapper(nn.Module):
            def __init__(self, model):
                super().__init__()
                self.model = model
            
            def forward(self, pixel_values):
                return self.model(pixel_values)
        
        vision_wrapper = VisionWrapper(vision_model).eval()
        
        print("\n  Converting vision encoder...")
        sample_image = torch.randn(1, 3, IMAGE_SIZE, IMAGE_SIZE)
        
        # Check compatibility
        if hasattr(converter, 'convert'):
             edge_vision = converter.convert(vision_wrapper, (sample_image,))
        else:
             print("  ⚠️ Warning: No .convert() method. Trying fallback...")
             # Extreme fallback
             import ai_edge_torch
             edge_vision = ai_edge_torch.convert(vision_wrapper, (sample_image,))
             
        vision_tflite_path = os.path.join(OUTPUT_DIR, f"medsiglip_vision_{IMAGE_SIZE}.tflite")
        edge_vision.export(vision_tflite_path)
        print(f"  ✅ Saved: {vision_tflite_path}")
        
        print("\n  Converting projector...")
        sample_embeds = torch.randn(1, NUM_IMAGE_PATCHES, VISION_HIDDEN_SIZE)
        edge_projector = converter.convert(projector, (sample_embeds,))
        projector_tflite_path = os.path.join(OUTPUT_DIR, "multimodal_projector.tflite")
        edge_projector.export(projector_tflite_path)
        print(f"  ✅ Saved: {projector_tflite_path}")
        
        print("\n🎉 SUCCESS! You now have:")
        print(f"  1. Vision encoder TFLite: {vision_tflite_path}")
        print(f"  2. Projector TFLite: {projector_tflite_path}")
        print(f"  3. Text decoder TFLite: output/medgemma_4b_tpu_q8_ekv128.tflite (from your notebook)")
        print("\nNext step: Integrate these 3 models in your inference pipeline!")

    except ImportError:
        print("\n  ⚠️  litert-torch not installed. Skipping TFLite conversion.")
        print("  Install with: pip install litert-torch")
        print("\n✅ PyTorch models saved successfully! You can:")
        print("  1. Use PyTorch models directly for testing")
        print("  2. Install litert-torch later for TFLite conversion")
    except Exception as e:
        print(f"\n  ❌ TFLite conversion failed: {e}")
        print("  But PyTorch models are saved and working!")

    print("\n" + "=" * 60)
    print("SCRIPT COMPLETE")
    print("=" * 60)

if __name__ == "__main__":
    main()
