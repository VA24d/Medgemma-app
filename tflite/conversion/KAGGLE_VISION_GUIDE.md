# 🦅 MedGemma 448x448 Vision Conversion - Kaggle Guide (LATEST)

We are using the **latest LiteRT (formerly AI Edge)** library because older versions are broken on Kaggle.

## Prerequisite: Local Files
Ensure you have these files on your computer (they should be in `medsiglip/output_vision/`):
1. `medsiglip_vision_448.pth`
2. `multimodal_projector_448.pth`

---

## Step 1: Create a Kaggle Notebook

1. Go to [Kaggle Kernels](https://www.kaggle.com/code/new).
2. **Settings** (Right Sidebar):
   - **Accelerator**: GPU T4 x2 (Recommended)
   - **Internet**: On

## Step 2: Upload Models

1. Click **File -> Upload Data** (or the "Add Input" button).
2. Upload `medsiglip_vision_448.pth` and `multimodal_projector_448.pth`.
3. Name the dataset `medgemma-vision-448`.

---

## Step 3: Install Converter (Cell 1)

**Copy-paste this into the first cell and RUN IT.**
It will clean up the environment and install the correct libraries.

```python
# 1. Clean up environment
!pip uninstall -y ai-edge-torch litert-torch tensorflow-cpu tensorflow

# 2. Install latest LiteRT
!pip install litert-torch
!pip install tensorflow-cpu

# 3. Verify Import
import sys
try:
    import litert.torch
    print("✅ litert.torch imported successfully!")
except ImportError:
    try:
        import litert_torch
        print("✅ litert_torch imported successfully!")
    except ImportError:
        print("⚠️ Modules not found yet. Please RESTART SESSION (Run -> Restart Session) and run this cell again.")
```

> **IF YOU SEE THE ⚠️ WARNING:**  
> Go to **Run -> Restart Session**, then run this cell again.

---

## Step 4: Run Conversion (Cell 2)

Copy this **ENTIRE SCRIPT** into the second cell. It automatically handles the library import names.

```python
# =============================================================================
# 448x448 Vision Model TFLite Conversion (LiteRT Version)
# =============================================================================
import torch
import torch.nn as nn
import os
import sys

# ===== ROBUST IMPORT LOGIC =====
# Try all known import variations for the converter
convert_func = None
try:
    import litert.torch as litert
    convert_func = litert.convert
    print("✅ Using: import litert.torch")
except ImportError:
    try:
        import litert_torch as litert
        convert_func = litert.convert
        print("✅ Using: import litert_torch")
    except ImportError:
        try:
            import ai_edge_torch as litert
            convert_func = litert.convert
            print("✅ Using: import ai_edge_torch")
        except ImportError:
             print("❌ CRITICAL ERROR: Could not import litert-torch library.")
             print("Please run 'pip install litert-torch' and Restart Session.")
             sys.exit(1)

# Config
IMAGE_SIZE = 448
VISION_HIDDEN_SIZE = 1152
TEXT_HIDDEN_SIZE = 2560
NUM_LAYERS = 27
NUM_ATTENTION_HEADS = 16
INTERMEDIATE_SIZE = 4304
NUM_IMAGE_PATCHES = 1024 # (448/14)^2

# Update these paths to match your uploaded dataset!
VISION_PTH_PATH = "/kaggle/input/medgemma-vision-448/medsiglip_vision_448.pth"
PROJECTOR_PTH_PATH = "/kaggle/input/medgemma-vision-448/multimodal_projector_448.pth"

# Locate files automatically if names match (fallback)
if not os.path.exists(VISION_PTH_PATH):
    print(f"⚠️ specific path not found, searching /kaggle/input...")
    for root, _, files in os.walk("/kaggle/input"):
        for f in files:
            if f == "medsiglip_vision_448.pth": VISION_PTH_PATH = os.path.join(root, f)
            if f == "multimodal_projector_448.pth": PROJECTOR_PTH_PATH = os.path.join(root, f)

print(f"Vision Path: {VISION_PTH_PATH}")
print(f"Projector Path: {PROJECTOR_PTH_PATH}")

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

# ===== LOAD WEIGHTS =====
print("Loading Vision Encoder...")
vision_encoder = SigLIPVisionTransformer()
vision_encoder.load_state_dict(torch.load(VISION_PTH_PATH, map_location='cpu'))
vision_encoder.eval()

print("Loading Projector...")
projector = MultimodalProjector()
projector.load_state_dict(torch.load(PROJECTOR_PTH_PATH, map_location='cpu'))
projector.eval()

# ===== CONVERT TO TFLITE =====
print("Converting Vision Encoder to TFLite...")
sample_input = (torch.randn(1, 3, IMAGE_SIZE, IMAGE_SIZE),)
vision_edge = convert_func(vision_encoder, sample_input)
vision_edge.export(os.path.join(OUTPUT_DIR, "medsiglip_vision_448.tflite"))
print("✅ Vision Encoder saved")

print("Converting Projector to TFLite...")
sample_input = (torch.randn(1, NUM_IMAGE_PATCHES, VISION_HIDDEN_SIZE),)
proj_edge = convert_func(projector, sample_input)
proj_edge.export(os.path.join(OUTPUT_DIR, "multimodal_projector_448.tflite"))
print("✅ Projector saved")

print("\n=== VISION CONVERSION COMPLETE ===")
```

---

## Step 5: Download Results (Cell 3)

```python
!zip -r vision_models_448.zip output_vision/
from IPython.display import FileLink
FileLink(r'vision_models_448.zip')
```
