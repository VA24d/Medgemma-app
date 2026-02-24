#!/usr/bin/env python3
"""
Test Script: 448x448 Vision + 1152 Prefill Text Model
Full pipeline test on laptop before mobile deployment.
"""

import numpy as np
import os
import glob
import torch
import torch.nn as nn
from PIL import Image
from transformers import AutoTokenizer
from ai_edge_litert import interpreter as tfl_interpreter

# ============== CONFIGURATION ==============
# Vision (448x448 PyTorch models)
VISION_PTH_PATH = "output_vision/medsiglip_vision_448.pth"
PROJECTOR_PTH_PATH = "output_vision/multimodal_projector_448.pth"

# Text Model (TFLite)
TEXT_MODEL_PATH = "medgemma_4b_mobile_int8_q8_ekv2048.tflite"

# Other
TOKENIZER_PATH = "medgemma_4b/"
IMAGE_FOLDER = "../images/"

# Config
IMAGE_SIZE = 448
VISION_HIDDEN_SIZE = 1152
TEXT_HIDDEN_SIZE = 2560
NUM_LAYERS = 27
NUM_ATTENTION_HEADS = 16
INTERMEDIATE_SIZE = 4304
NUM_IMAGE_PATCHES = 1024  # (448/14)² = 1024

TARGET_PREFILL_LEN = 1152  # Model's prefill signature
MAX_GEN_LEN = 50

# ============== VISION MODEL DEFINITIONS ==============
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

# ============== MAIN EXECUTION ==============

def preprocess_image(img_path):
    img = Image.open(img_path).convert("RGB")
    img = img.resize((IMAGE_SIZE, IMAGE_SIZE))
    img_arr = np.array(img, dtype=np.float32) / 255.0
    mean = np.array([0.5, 0.5, 0.5], dtype=np.float32)
    std = np.array([0.5, 0.5, 0.5], dtype=np.float32)
    img_arr = (img_arr - mean) / std
    return torch.from_numpy(np.transpose(img_arr, (2, 0, 1))[None, ...])


if __name__ == "__main__":
    print("=" * 50)
    print("448x448 Vision + 1152 Prefill Text Model Test")
    print("=" * 50)
    
    # 1. Load Vision Models (PyTorch)
    print("\n[1/5] Loading Vision Encoder...")
    vision_encoder = SigLIPVisionTransformer()
    vision_encoder.load_state_dict(torch.load(VISION_PTH_PATH, map_location='cpu'))
    vision_encoder.eval()
    print("✅ Vision Encoder Loaded")
    
    print("[2/5] Loading Projector...")
    projector = MultimodalProjector()
    projector.load_state_dict(torch.load(PROJECTOR_PTH_PATH, map_location='cpu'))
    projector.eval()
    print("✅ Projector Loaded")
    
    # 2. Load Text Model (TFLite)
    print("[3/5] Loading Text Model...")
    if not os.path.exists(TEXT_MODEL_PATH):
        print(f"❌ Text model not found: {TEXT_MODEL_PATH}")
        exit(1)
    
    text_interpreter = tfl_interpreter.Interpreter(model_path=TEXT_MODEL_PATH)
    text_interpreter.allocate_tensors()
    
    sigs = text_interpreter.get_signature_list()
    print(f"   Available signatures: {list(sigs.keys())}")
    
    prefill_key = next((k for k in sigs.keys() if 'prefill' in k), None)
    if prefill_key:
        print(f"   Using: {prefill_key}")
        prefill_runner = text_interpreter.get_signature_runner(prefill_key)
    else:
        print("❌ No prefill signature found!")
        exit(1)
    print("✅ Text Model Loaded")
    
    # 3. Load Tokenizer
    print("[4/5] Loading Tokenizer...")
    tokenizer = AutoTokenizer.from_pretrained(TOKENIZER_PATH)
    print("✅ Tokenizer Loaded")
    
    # 4. Find Test Image
    print("[5/5] Finding Test Images...")
    images = glob.glob(IMAGE_FOLDER + "*.jpg") + glob.glob(IMAGE_FOLDER + "*.png")
    if not images:
        print(f"❌ No images in {IMAGE_FOLDER}")
        exit(1)
    print(f"   Found {len(images)} image(s)")
    
    # 5. Run Pipeline
    print("\n" + "=" * 50)
    print("RUNNING FULL PIPELINE")
    print("=" * 50)
    
    img_path = images[0]
    print(f"\nImage: {img_path}")
    
    # Vision
    print("\n[A] Vision Encoder...")
    pixel_values = preprocess_image(img_path)
    with torch.no_grad():
        vision_out = vision_encoder(pixel_values)
    print(f"   Output: {vision_out.shape}")  # [1, 1024, 1152]
    
    # Projector
    print("[B] Projector...")
    with torch.no_grad():
        proj_out = projector(vision_out)
    print(f"   Output: {proj_out.shape}")  # [1, 1024, 2560]
    proj_features = proj_out.numpy()[0]  # [1024, 2560]
    
    # Token Mapping (Simplified - just use placeholder tokens for structure test)
    print("[C] Token Mapping (Placeholder)...")
    # For structural test, use arbitrary token IDs
    # Real implementation would do nearest neighbor search
    visual_token_ids = np.arange(1024, 2048, dtype=np.int32)  # 1024 fake vision tokens
    
    # Text Prompt
    prompt = "Describe this medical image."
    text_ids = tokenizer.encode(prompt, add_special_tokens=True)
    print(f"   Text tokens: {len(text_ids)}")
    
    # Construct Full Input
    full_input = list(visual_token_ids) + text_ids
    current_len = len(full_input)
    print(f"   Total input: {current_len} tokens")
    
    # Pad to TARGET_PREFILL_LEN
    if current_len < TARGET_PREFILL_LEN:
        padding = [0] * (TARGET_PREFILL_LEN - current_len)
        input_tokens = np.array([full_input + padding], dtype=np.int32)
    else:
        input_tokens = np.array([full_input[:TARGET_PREFILL_LEN]], dtype=np.int32)
    
    print(f"   Padded to: {input_tokens.shape}")
    
    # Prefill
    print("\n[D] Running Prefill...")
    pf_inputs = prefill_runner.get_input_details()
    print(f"   Prefill inputs: {list(pf_inputs.keys())}")
    
    input_dict = {}
    for name, det in pf_inputs.items():
        if 'tokens' in name:
            input_dict[name] = input_tokens
        elif 'pos' in name:
            input_dict[name] = np.arange(TARGET_PREFILL_LEN, dtype=np.int32)[None, :]
        elif 'mask' in name:
            shape = det['shape']
            input_dict[name] = np.ones(shape, dtype=np.float32)
        elif 'cache' in name:
            shape = det['shape']
            input_dict[name] = np.zeros(shape, dtype=np.float32)
    
    print(f"   Prepared inputs: {list(input_dict.keys())}")
    
    try:
        pf_output = prefill_runner(**input_dict)
        print("✅ PREFILL SUCCEEDED!")
        print(f"   Output keys: {list(pf_output.keys())}")
        
        if 'logits' in pf_output:
            logits = pf_output['logits']
            print(f"   Logits shape: {logits.shape}")
            next_token = np.argmax(logits[0, -1, :] if len(logits.shape) == 3 else logits[0])
            print(f"   First predicted token: {next_token} = '{tokenizer.decode([next_token])}'")
        
    except Exception as e:
        print(f"❌ PREFILL FAILED: {e}")
    
    print("\n" + "=" * 50)
    print("TEST COMPLETE")
    print("=" * 50)
