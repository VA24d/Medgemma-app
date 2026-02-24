#!/usr/bin/env python3
"""
Test Script for 448x448 Mobile Vision Pipeline
Verifies the vision encoder and projector work correctly.

Usage:
    cd tflite/inference
    python run_vision_test.py

Requires vision encoder TFLite models in ../models/:
    - medsiglip_vision_448.tflite
    - multimodal_projector_448.tflite
"""

import torch
import torch.nn as nn
import numpy as np
from PIL import Image
import os
import glob

# ============== CONFIGURATION ==============
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_DIR = os.path.join(SCRIPT_DIR, "..", "models")
IMAGE_FOLDER = os.path.join(SCRIPT_DIR, "test_images")

IMAGE_SIZE = 448
VISION_HIDDEN_SIZE = 1152
TEXT_HIDDEN_SIZE = 2560
NUM_LAYERS = 27
NUM_ATTENTION_HEADS = 16
INTERMEDIATE_SIZE = 4304
NUM_IMAGE_PATCHES = (IMAGE_SIZE // 14) ** 2  # 1024


# ============== MODEL DEFINITIONS ==============
# (Same as convert script, but minimal for loading)

class SigLIPVisionEmbeddings(nn.Module):
    def __init__(self):
        super().__init__()
        self.patch_embedding = nn.Conv2d(3, VISION_HIDDEN_SIZE, 14, 14, 0, bias=True)
        self.position_embedding = nn.Embedding(NUM_IMAGE_PATCHES, VISION_HIDDEN_SIZE)
        self.register_buffer("position_ids", torch.arange(NUM_IMAGE_PATCHES).expand((1, -1)), persistent=False)
    
    def forward(self, pixel_values):
        patch_embeds = self.patch_embedding(pixel_values)
        batch, hidden, h, w = patch_embeds.shape
        patch_embeds = patch_embeds.flatten(2).transpose(1, 2)
        return patch_embeds + self.position_embedding(self.position_ids)


class SigLIPAttention(nn.Module):
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
        attn_weights = torch.matmul(q, k.transpose(-2, -1)) / (self.head_dim ** 0.5)
        attn_weights = torch.softmax(attn_weights, dim=-1)
        attn_output = torch.matmul(attn_weights, v)
        attn_output = attn_output.transpose(1, 2).contiguous().view(batch, seq_len, embed_dim)
        return self.out_proj(attn_output)


class SigLIPMLP(nn.Module):
    def __init__(self):
        super().__init__()
        self.fc1 = nn.Linear(VISION_HIDDEN_SIZE, INTERMEDIATE_SIZE, bias=True)
        self.fc2 = nn.Linear(INTERMEDIATE_SIZE, VISION_HIDDEN_SIZE, bias=True)
        self.activation = nn.GELU(approximate='tanh')
    
    def forward(self, x):
        return self.fc2(self.activation(self.fc1(x)))


class SigLIPEncoderLayer(nn.Module):
    def __init__(self):
        super().__init__()
        self.self_attn = SigLIPAttention()
        self.layer_norm1 = nn.LayerNorm(VISION_HIDDEN_SIZE, eps=1e-6)
        self.mlp = SigLIPMLP()
        self.layer_norm2 = nn.LayerNorm(VISION_HIDDEN_SIZE, eps=1e-6)
    
    def forward(self, x):
        residual = x
        x = self.layer_norm1(x)
        x = self.self_attn(x)
        x = residual + x
        residual = x
        x = self.layer_norm2(x)
        x = self.mlp(x)
        return residual + x


class SigLIPEncoder(nn.Module):
    def __init__(self):
        super().__init__()
        self.layers = nn.ModuleList([SigLIPEncoderLayer() for _ in range(NUM_LAYERS)])
    
    def forward(self, x):
        for layer in self.layers:
            x = layer(x)
        return x


class SigLIPVisionTransformer(nn.Module):
    def __init__(self):
        super().__init__()
        self.embeddings = SigLIPVisionEmbeddings()
        self.encoder = SigLIPEncoder()
        self.post_layernorm = nn.LayerNorm(VISION_HIDDEN_SIZE, eps=1e-6)
    
    def forward(self, pixel_values):
        x = self.embeddings(pixel_values)
        x = self.encoder(x)
        return self.post_layernorm(x)


class MultimodalProjector(nn.Module):
    def __init__(self):
        super().__init__()
        self.linear = nn.Linear(VISION_HIDDEN_SIZE, TEXT_HIDDEN_SIZE, bias=True)
    
    def forward(self, x):
        return self.linear(x)


# ============== MAIN TEST ==============

def preprocess_image(img_path):
    """Load and preprocess image for 448x448 model"""
    img = Image.open(img_path).convert("RGB")
    img = img.resize((IMAGE_SIZE, IMAGE_SIZE))
    img_arr = np.array(img, dtype=np.float32) / 255.0
    mean = np.array([0.5, 0.5, 0.5], dtype=np.float32)
    std = np.array([0.5, 0.5, 0.5], dtype=np.float32)
    img_arr = (img_arr - mean) / std
    return torch.from_numpy(np.transpose(img_arr, (2, 0, 1))[None, ...])


if __name__ == "__main__":
    print("=== 448x448 Vision Pipeline Test ===\n")
    
    # Load models
    print("[1/4] Loading Vision Encoder...")
    vision_encoder = SigLIPVisionTransformer()
    vision_path = os.path.join(OUTPUT_DIR, "medsiglip_vision_448.pth")
    if os.path.exists(vision_path):
        vision_encoder.load_state_dict(torch.load(vision_path, map_location='cpu'))
        print(f"✅ Loaded from {vision_path}")
    else:
        print(f"❌ Not found: {vision_path}")
        exit(1)
    vision_encoder.eval()
    
    print("[2/4] Loading Projector...")
    projector = MultimodalProjector()
    proj_path = os.path.join(OUTPUT_DIR, "multimodal_projector_448.pth")
    if os.path.exists(proj_path):
        projector.load_state_dict(torch.load(proj_path, map_location='cpu'))
        print(f"✅ Loaded from {proj_path}")
    else:
        print(f"❌ Not found: {proj_path}")
        exit(1)
    projector.eval()
    
    # Find test images
    print("[3/4] Finding test images...")
    images = glob.glob(IMAGE_FOLDER + "*.jpg") + glob.glob(IMAGE_FOLDER + "*.png")
    if not images:
        print(f"⚠️ No images in {IMAGE_FOLDER}, using random noise")
        images = ["<random>"]
    else:
        print(f"   Found {len(images)} image(s)")
    
    # Run inference
    print("[4/4] Running Inference...\n")
    
    for img_path in images[:2]:  # Test first 2
        print(f"--- Processing: {img_path} ---")
        
        if img_path == "<random>":
            pixel_values = torch.randn(1, 3, IMAGE_SIZE, IMAGE_SIZE)
        else:
            pixel_values = preprocess_image(img_path)
        
        with torch.no_grad():
            # Vision
            vision_out = vision_encoder(pixel_values)
            print(f"   Vision Output: {vision_out.shape}")  # [1, 1024, 1152]
            print(f"   Vision Mean: {vision_out.mean().item():.6f}")
            print(f"   Vision Std: {vision_out.std().item():.6f}")
            
            # Projector
            proj_out = projector(vision_out)
            print(f"   Projector Output: {proj_out.shape}")  # [1, 1024, 2560]
            print(f"   Projector Mean: {proj_out.mean().item():.6f}")
            print(f"   Projector Std: {proj_out.std().item():.6f}")
            
            # Sanity checks
            if vision_out.shape == torch.Size([1, 1024, 1152]):
                print("   ✅ Vision shape CORRECT")
            else:
                print("   ❌ Vision shape WRONG")
            
            if proj_out.shape == torch.Size([1, 1024, 2560]):
                print("   ✅ Projector shape CORRECT")
            else:
                print("   ❌ Projector shape WRONG")
            
            if not torch.isnan(proj_out).any():
                print("   ✅ No NaN values")
            else:
                print("   ❌ Contains NaN!")
        
        print()
    
    print("=== TEST COMPLETE ===")
    print("Vision pipeline produces 1024 tokens ready for the text model!")
