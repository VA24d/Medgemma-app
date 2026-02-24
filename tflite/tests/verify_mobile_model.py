import numpy as np
import os
import torch
import json
from PIL import Image
from transformers import AutoTokenizer
from ai_edge_litert import interpreter as tfl_interpreter

# --- Configuration ---
VISION_MODEL_PATH = "medsiglip/output_vision/medsiglip_vision_896.tflite"
PROJECTOR_MODEL_PATH = "medsiglip/output_vision/multimodal_projector.tflite"
TEXT_MODEL_PATH = "medsiglip/medgemma_4b_mobile_int8_q8_ekv5120.tflite"  # User will download this
TOKENIZER_PATH = "medsiglip/medgemma_4b/"  # Folder containing tokenizer.json
IMAGE_PATH = "images/image1.jpg"

# --- 1. Load Utilities ---
print(f"[1/5] Loading Tokenizer from {TOKENIZER_PATH}...")
try:
    tokenizer = AutoTokenizer.from_pretrained(TOKENIZER_PATH)
    print("✅ Tokenizer Loaded.")
except Exception as e:
    print(f"❌ Failed to load tokenizer: {e}")
    exit(1)

# --- 2. Load Vision Models ---
print(f"[2/5] Loading Vision Models...")
if not os.path.exists(VISION_MODEL_PATH):
    print(f"❌ Vision model not found: {VISION_MODEL_PATH}")
    exit(1)
if not os.path.exists(PROJECTOR_MODEL_PATH):
    print(f"❌ Projector model not found: {PROJECTOR_MODEL_PATH}")
    exit(1)

try:
    vision_interpreter = tfl_interpreter.Interpreter(model_path=VISION_MODEL_PATH)
    vision_runner = vision_interpreter.get_signature_runner("serving_default")
    
    projector_interpreter = tfl_interpreter.Interpreter(model_path=PROJECTOR_MODEL_PATH)
    projector_runner = projector_interpreter.get_signature_runner("serving_default")
    print("✅ Vision & Projector Loaded.")
except Exception as e:
    print(f"❌ Failed to load vision models: {e}")
    exit(1)

# --- 3. Process Image ---
print(f"[3/5] Processing Image {IMAGE_PATH}...")
if not os.path.exists(IMAGE_PATH):
    # Create dummy image if missing
    print("⚠️ Image not found, creating dummy noise image.")
    dummy_img = np.random.rand(1, 3, 896, 896).astype(np.float32)
    vision_input = dummy_img
else:
    # Load and preprocess
    img = Image.open(IMAGE_PATH).convert("RGB")
    img = img.resize((896, 896))
    img_arr = np.array(img, dtype=np.float32) / 255.0  # Normalize 0-1
    # Standardize (Mean/Std from SigLIP)
    mean = np.array([0.5, 0.5, 0.5], dtype=np.float32)
    std = np.array([0.5, 0.5, 0.5], dtype=np.float32)
    img_arr = (img_arr - mean) / std
    vision_input = np.transpose(img_arr, (2, 0, 1))[None, ...]  # (1, 3, 896, 896)

# Run Vision
# Dynamic Input Name Detection
vision_input_details = vision_runner.get_input_details()
vision_input_name = list(vision_input_details.keys())[0]
print(f"   Vision Input Name: {vision_input_name}")

# Run Vision
vision_out = vision_runner(**{vision_input_name: vision_input})['output_0']  # Shape: (1, 4096, 1152)
print(f"   Vision Output: {vision_out.shape}")

# Run Projector
# Dynamic Projector Input
projector_input_details = projector_runner.get_input_details()
projector_input_name = list(projector_input_details.keys())[0]
print(f"   Projector Input Name: {projector_input_name}")

# Run Projector
projector_out = projector_runner(**{projector_input_name: vision_out})['output_0'] # Shape: (1, 4096, 2560)
print(f"   Projector Output: {projector_out.shape}")


# --- 4. Nearest Neighbor Mapping (Simplified) ---
# In a real app, you'd use the embedding matrix.
# Here, we'll simulate it or implement a slow version if the matrix is available.
print(f"[4/5] Simulating Token Mapping...")
# NOTE: To do this properly, we need the embedding matrix from the Text Model.
# For now, we will create dummy token IDs to verify the Text Model accepts 4096 inputs.
dummy_vision_tokens = np.random.randint(0, 256000, size=(1, 4096), dtype=np.int32)
print(f"   Mapped 4096 vision patches to {dummy_vision_tokens.shape} token IDs.")


# --- 5. Verify Text Model ---
print(f"[5/5] Verifying Mobile Text Model...")
if not os.path.exists(TEXT_MODEL_PATH):
    print(f"⚠️ Text model not found at {TEXT_MODEL_PATH}")
    print("   Please download 'medgemma_4b_mobile_int8_q8_ekv5120.tflite' from Kaggle and place it in 'medsiglip/'.")
    exit(0)

try:
    text_interpreter = tfl_interpreter.Interpreter(model_path=TEXT_MODEL_PATH)
    text_runner = text_interpreter.get_signature_runner("decode")
    
    # Text Input (User Prompt)
    prompt = "Describe this medical image."
    text_tokens = tokenizer.encode(prompt, return_tensors="np").astype(np.int32)
    
    # Concatenate [Vision, Text]
    full_input_tokens = np.concatenate([dummy_vision_tokens, text_tokens], axis=1)
    
    # Truncate or Pad to EXACTLY 4224
    TARGET_LEN = 4224
    current_len = full_input_tokens.shape[1]
    
    if current_len > TARGET_LEN:
         print(f"⚠️ Input length {current_len} > {TARGET_LEN}. Truncating.")
         full_input_tokens = full_input_tokens[:, :TARGET_LEN]
    elif current_len < TARGET_LEN:
         print(f"ℹ️ Input length {current_len} < {TARGET_LEN}. Padding.")
         padding = np.zeros((1, TARGET_LEN - current_len), dtype=np.int32)
         full_input_tokens = np.concatenate([full_input_tokens, padding], axis=1)
    
    print(f"   Final Input Shape: {full_input_tokens.shape}")

    # Prepare KV Cache (Empty for Prefill)
    # The signature expects all inputs, including caches, even if not used in prefill (depends on implementation)
    # But usually 'prefill' signature differs from 'decode'.
    # Inspecting signature keys first to be safe.
    sigs = text_interpreter.get_signature_list()
    print(f"   Signatures found: {list(sigs.keys())}")
    
    prefill_key = next((k for k in sigs.keys() if 'prefill' in k), None)
    if prefill_key:
        print(f"   Using Prefill Signature: {prefill_key}")
        runner = text_interpreter.get_signature_runner(prefill_key) 
        # Check input details
        inputs = runner.get_input_details()
        # Create input dictionary
        input_dict = {}
        for name, detail in inputs.items():
            if 'tokens' in name:
                input_dict[name] = full_input_tokens
            elif 'pos' in name:
                input_dict[name] = np.arange(full_input_tokens.shape[1], dtype=np.int32)[None, :]
            elif 'mask' in name:
                 # Create mask (1, 1, 1, 4102)
                 input_dict[name] = np.zeros((1, 1, 1, full_input_tokens.shape[1]), dtype=np.float32)
            elif 'cache' in name:
                 # Handle KV Cache (e.g. kv_cache_k_0)
                 # We need the shape from details
                 shape = detail['shape'] # e.g. [1, 4, 128, 256] -> [1, 4, 5120, 256]
                 # We must provide the full cache size (5120) even if we only use 4224? 
                 # Usually prefill doesn't need cache input content, but needs valid shape.
                 input_dict[name] = np.zeros(shape, dtype=np.float32)
            
        # Run!
        print("   Running Inference...")
        output = runner(**input_dict)
        print("✅ Success! Text Model accepted the input.")
        print(f"   Output Logits: {output['logits'].shape}")
        
    else:
        # If only 'decode' exists, we might have to loop or it might support dynamic.
        # But our goal was to verify the *context length*.
        # Assuming the new model has a 'prefill' or 'serving_default' that handles the sequence.
        print("⚠️ 'prefill' signature not found. Checking 'decode' or 'serving_default'...")
        # (This part depends heavily on how the converter exported it. 
        # The script requested `prefill_seq_len=4224`, so it should be capable.)
        pass

except Exception as e:
    print(f"❌ Text verification failed: {e}")
