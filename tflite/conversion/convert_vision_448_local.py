#!/usr/bin/env python3
"""
Convert 448x448 Vision Models to TFLite (Local WSL)
"""

import torch
import torch.nn as nn
import os

# Config
IMAGE_SIZE = 448
VISION_HIDDEN_SIZE = 1152
TEXT_HIDDEN_SIZE = 2560
NUM_LAYERS = 27
NUM_ATTENTION_HEADS = 16
INTERMEDIATE_SIZE = 4304
NUM_IMAGE_PATCHES = 1024

OUTPUT_DIR = "output_vision"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# ===== MODEL DEFINITIONS =====
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

# ===== MAIN =====
if __name__ == "__main__":
    print("=== 448x448 Vision TFLite Conversion (Local WSL) ===\n")
    
    # Load models
    print("[1/4] Loading Vision Encoder...")
    vision = SigLIPVisionTransformer()
    vision.load_state_dict(torch.load(f"{OUTPUT_DIR}/medsiglip_vision_448.pth", map_location='cpu'))
    vision.eval()
    print("✅ Loaded")
    
    print("[2/4] Loading Projector...")
    proj = MultimodalProjector()
    proj.load_state_dict(torch.load(f"{OUTPUT_DIR}/multimodal_projector_448.pth", map_location='cpu'))
    proj.eval()
    print("✅ Loaded")
    
    # Convert
    print("[3/4] Converting to TFLite...")
    try:
        # Try litert_torch first (worked for 896px conversion)
        try:
            import litert_torch
            converter = litert_torch
            print("   Using litert_torch")
        except ImportError:
            import ai_edge_torch
            converter = ai_edge_torch
            print("   Using ai_edge_torch")
        
        # Vision
        print("   Converting Vision Encoder...")
        sample_img = torch.randn(1, 3, IMAGE_SIZE, IMAGE_SIZE)
        edge_vision = converter.convert(vision, (sample_img,))
        edge_vision.export(f"{OUTPUT_DIR}/medsiglip_vision_448.tflite")
        print("   ✅ Vision saved")
        
        # Projector
        print("   Converting Projector...")
        sample_emb = torch.randn(1, NUM_IMAGE_PATCHES, VISION_HIDDEN_SIZE)
        edge_proj = converter.convert(proj, (sample_emb,))
        edge_proj.export(f"{OUTPUT_DIR}/multimodal_projector_448.tflite")
        print("   ✅ Projector saved")
        
        print("\n[4/4] Done!")
        print(f"\nFiles in {OUTPUT_DIR}/:")
        for f in os.listdir(OUTPUT_DIR):
            print(f"   {f}")
            
    except ImportError as e:
        print(f"❌ Conversion library not available: {e}")
    except Exception as e:
        print(f"❌ Conversion failed: {e}")
