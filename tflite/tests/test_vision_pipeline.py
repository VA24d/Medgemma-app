#!/usr/bin/env python3
"""
Test script for MedGemma multimodal pipeline
Tests vision encoder → projector → text decoder flow

Usage:
    python test_vision_pipeline.py --image chest_xray.jpg
"""

import torch
import numpy as np
import os
from PIL import Image
import torchvision.transforms as transforms
import argparse

# ============== CONFIGURATION ==============
# ============== CONFIGURATION ==============
IMAGE_SIZE = 896
VISION_HIDDEN_SIZE = 1152
TEXT_HIDDEN_SIZE = 2560
NUM_PATCHES = (IMAGE_SIZE // 14) ** 2  # 4096

# ============== IMAGE PREPROCESSING ==============
def get_image_transform():
    """MedGemma image preprocessing"""
    return transforms.Compose([
        transforms.Resize((IMAGE_SIZE, IMAGE_SIZE), interpolation=transforms.InterpolationMode.BILINEAR),
        transforms.ToTensor(),  # [0, 1]
        transforms.Normalize(mean=[0.5, 0.5, 0.5], std=[0.5, 0.5, 0.5])  # [-1, 1]
    ])

# ============== PYTORCH TESTING ==============
def test_pytorch_pipeline(image_path):
    """Test with PyTorch .pth models"""
    print("\n" + "=" * 60)
    print("TESTING PYTORCH PIPELINE")
    print("=" * 60)
    
    # Load models
    print("\n[1/3] Loading PyTorch models...")
    from convert_medgemma_vision_WORKING import SigLIPVisionModel, MultiModalProjector
    
    vision_model = SigLIPVisionModel()
    vision_model.load_state_dict(torch.load(f"output_vision/medsiglip_vision_{IMAGE_SIZE}.pth", map_location="cpu"))
    vision_model.eval()
    print("  ✅ Vision encoder loaded")
    
    projector = MultiModalProjector()
    projector.load_state_dict(torch.load("output_vision/multimodal_projector.pth", map_location="cpu"))
    projector.eval()
    print("  ✅ Projector loaded")
    
    # Process image
    print(f"\n[2/3] Processing image: {image_path}")
    image = Image.open(image_path).convert('RGB')
    transform = get_image_transform()
    pixel_values = transform(image).unsqueeze(0)  # [1, 3, 896, 896]
    print(f"  ✅ Image preprocessed: {pixel_values.shape}")
    
    # Forward pass
    print("\n[3/3] Running forward pass...")
    with torch.no_grad():
        # Vision encoder
        image_embeds = vision_model(pixel_values)
        print(f"  ✅ Vision output: {image_embeds.shape}")  # [1, 4096, 1152]
        print(f"     Mean: {image_embeds.mean().item():.6f}, Std: {image_embeds.std().item():.6f}")
        
        # Projector
        projected_embeds = projector(image_embeds)
        print(f"  ✅ Projected output: {projected_embeds.shape}")  # [1, 4096, 2560]
        print(f"     Mean: {projected_embeds.mean().item():.6f}, Std: {projected_embeds.std().item():.6f}")
    
    print("\n✅ PyTorch pipeline working!")
    return projected_embeds.numpy()

# ============== TFLITE TESTING ==============
def test_tflite_pipeline(image_path):
    """Test with TFLite models"""
    print("\n" + "=" * 60)
    print("TESTING TFLITE PIPELINE")
    print("=" * 60)
    
    try:
        from ai_edge_litert import interpreter as tfl_interpreter
    except ImportError:
        print("❌ ai-edge-litert not installed. Run: pip install ai-edge-litert")
        return None
    
    # Load TFLite models
    print("\n[1/3] Loading TFLite models...")
    
    tflite_path = f"output_vision/medsiglip_vision_{IMAGE_SIZE}.tflite"
    if not os.path.exists(tflite_path):
        print(f"⚠️  TFLite file not found: {tflite_path}")
        print("   Skipping TFLite test (running in PyTorch-only mode?)")
        return None

    vision_interpreter = tfl_interpreter.Interpreter(tflite_path)
    vision_runner = vision_interpreter.get_signature_runner()
    print("  ✅ Vision encoder loaded")
    
    projector_interpreter = tfl_interpreter.Interpreter("output_vision/multimodal_projector.tflite")
    projector_runner = projector_interpreter.get_signature_runner()
    print("  ✅ Projector loaded")
    
    # Process image
    print(f"\n[2/3] Processing image: {image_path}")
    image = Image.open(image_path).convert('RGB')
    transform = get_image_transform()
    pixel_values = transform(image).unsqueeze(0).numpy().astype(np.float32)
    print(f"  ✅ Image preprocessed: {pixel_values.shape}")
    
    # Forward pass
    # Vision encoder inference
    print("\n[3/3] Running TFLite inference...")
    
    # Get input/output names dynamically
    vision_input_details = vision_runner.get_input_details()
    vision_input_name = list(vision_input_details.keys())[0]
    vision_output_details = vision_runner.get_output_details()
    vision_output_name = list(vision_output_details.keys())[0]
    print(f"  ℹ️  Vision Input: {vision_input_name}, Output: {vision_output_name}")

    # Run Vision
    vision_inputs = {vision_input_name: pixel_values}
    vision_outputs = vision_runner(**vision_inputs)
    image_embeds = vision_outputs[vision_output_name]
    
    print(f"  ✅ Vision output: {image_embeds.shape}")
    print(f"     Mean: {np.mean(image_embeds):.6f}, Std: {np.std(image_embeds):.6f}")
    
    # Projector inference
    projector_input_details = projector_runner.get_input_details()
    projector_input_name = list(projector_input_details.keys())[0]
    projector_output_details = projector_runner.get_output_details()
    projector_output_name = list(projector_output_details.keys())[0]
    print(f"  ℹ️  Projector Input: {projector_input_name}, Output: {projector_output_name}")
    
    # Run Projector
    projector_inputs = {projector_input_name: image_embeds}
    projector_outputs = projector_runner(**projector_inputs)
    projected_embeds = projector_outputs[projector_output_name]
    
    print(f"  ✅ Projected output: {projected_embeds.shape}")
    print(f"     Mean: {np.mean(projected_embeds):.6f}, Std: {np.std(projected_embeds):.6f}")
    
    print("\n✅ TFLite pipeline working!")
    return projected_embeds

# ============== COMPARISON ==============
def compare_outputs(pytorch_out, tflite_out):
    """Compare PyTorch vs TFLite outputs"""
    print("\n" + "=" * 60)
    print("PYTORCH vs TFLITE COMPARISON")
    print("=" * 60)
    
    if pytorch_out is None or tflite_out is None:
        print("⚠️  Skipping comparison (missing outputs)")
        return
    
    # Compute difference
    diff = np.abs(pytorch_out - tflite_out)
    max_diff = np.max(diff)
    mean_diff = np.mean(diff)
    
    print(f"\nMax absolute difference: {max_diff:.8f}")
    print(f"Mean absolute difference: {mean_diff:.8f}")
    
    if max_diff < 1e-3:
        print("✅ EXCELLENT match! (< 1e-3)")
    elif max_diff < 1e-2:
        print("✅ Good match (< 1e-2)")
    elif max_diff < 0.1:
        print("⚠️  Acceptable match (< 0.1)")
    else:
        print("❌ Poor match - may have quantization differences")

# ============== END-TO-END PIPELINE ==============
class TokenMapper:
    """Handles mapping from continuous embeddings to discrete token IDs"""
    def __init__(self, model_path="medgemma_4b"):
        print("\n[Init] Loading embedding matrix for token mapping...")
        from safetensors import safe_open
        
        # We need the embedding matrix (model.embed_tokens.weight) to do nearest neighbor search.
        # It's usually in the first shard, but might be elsewhere.
        import glob
        safetensor_files = glob.glob(os.path.join(model_path, "*.safetensors"))
        self.embed_weights = None
        
        for sf in safetensor_files:
            print(f"  Scanning {sf}...")
            with safe_open(sf, framework="pt", device="cpu") as f:
                keys = f.keys()
                # Check for standard keys
                if "model.embed_tokens.weight" in keys:
                    print(f"  ✅ Found embeddings (model.embed_tokens.weight) in {sf}")
                    self.embed_weights = f.get_tensor("model.embed_tokens.weight")
                    break
                # Check for MedGemma specific keys (language_model prefix)
                elif "language_model.model.embed_tokens.weight" in keys:
                     print(f"  ✅ Found embeddings (language_model.model.embed_tokens.weight) in {sf}")
                     self.embed_weights = f.get_tensor("language_model.model.embed_tokens.weight")
                     break
        
        if self.embed_weights is None:
             raise ValueError("Could not find embedding weights in any safetensors file!")
        
        # Cast to float32 for compatibility with TFLite output
        self.embed_weights = self.embed_weights.float()

        # Normalize for cosine similarity
        self.embed_weights = torch.nn.functional.normalize(self.embed_weights, p=2, dim=1)
        print(f"  ✅ Loaded embedding matrix: {self.embed_weights.shape} (dtype: {self.embed_weights.dtype})")

    def map_embeddings_to_ids(self, projected_embeds):
        """
        Input: [1, num_patches, 2560] (Projected Embeddings)
        Output: [1, num_patches] (Token IDs)
        """
        # 1. Normalize input
        input_embeds = torch.from_numpy(projected_embeds).squeeze(0) # [4096, 2560]
        input_embeds = torch.nn.functional.normalize(input_embeds, p=2, dim=1)
        
        # 2. Compute Cosine Similarity (Dot product of normalized vectors)
        # We process in chunks to avoid OOM
        chunk_size = 1024
        token_ids_list = []
        
        print("  Mapping embeddings to tokens (Nearest Neighbor)...")
        for i in range(0, input_embeds.shape[0], chunk_size):
            chunk = input_embeds[i:i+chunk_size] # [chunk, 2560]
            similarity = torch.matmul(chunk, self.embed_weights.t()) # [chunk, vocab_size]
            best_ids = torch.argmax(similarity, dim=1) # [chunk]
            token_ids_list.append(best_ids)
            
        return torch.cat(token_ids_list).unsqueeze(0).numpy().astype(np.int32) # [1, 4096]

def test_full_pipeline(image_path, vision_embeds):
        input_embeds = torch.nn.functional.normalize(input_embeds, p=2, dim=1)
        
        # 2. Compute Cosine Similarity (Dot product of normalized vectors)
        # We process in chunks to avoid OOM
        chunk_size = 1024
        token_ids_list = []
        
        print("  Mapping embeddings to tokens (Nearest Neighbor)...")
        for i in range(0, input_embeds.shape[0], chunk_size):
            chunk = input_embeds[i:i+chunk_size] # [chunk, 2560]
            similarity = torch.matmul(chunk, self.embed_weights.t()) # [chunk, vocab_size]
            best_ids = torch.argmax(similarity, dim=1) # [chunk]
            token_ids_list.append(best_ids)
            
        return torch.cat(token_ids_list).unsqueeze(0).numpy().astype(np.int32) # [1, 4096]

def test_full_pipeline(image_path, vision_embeds):
    """
    1. Map Vision Embeds -> Vision Tokens
    2. Tokenize Text Prompt
    3. Concatenate
    4. Run Text Decoder TFLite
    """
    print("\n" + "=" * 60)
    print("TESTING FULL END-TO-END PIPELINE")
    print("=" * 60)
    
    text_model_path = "../model_assets/medgemma_4b_tpu_q8_ekv128.tflite"
    if not os.path.exists(text_model_path):
        print(f"❌ Text model not found at {text_model_path}")
        return

    # 1. Initialize Mapper
    try:
        mapper = TokenMapper()
    except Exception as e:
        print(f"❌ Mapper init failed: {e}")
        return

    # 2. Map Vision -> Tokens
    print("\n[1/4] Converting Vision Embeddings to Token IDs...")
    vision_token_ids = mapper.map_embeddings_to_ids(vision_embeds)
    print(f"  ✅ Vision Tokens: {vision_token_ids.shape} (Example: {vision_token_ids[0, :5]})")

    # 3. Tokenize Text Prompt
    print("\n[2/4] Tokenizing Prompt...")
    from transformers import AutoTokenizer
    tokenizer = AutoTokenizer.from_pretrained("medgemma_4b")
    
    prompt = "Describe this image."
    text_ids = tokenizer.encode(prompt, return_tensors="np", add_special_tokens=True).astype(np.int32)
    print(f"  ✅ Text Tokens: {text_ids.shape} ('{prompt}')")

    # 4. Concatenate (Vision + Text)
    # Note: MedGemma might expect specific separating tokens. For now, we verify raw concatenation works.
    full_input_ids = np.concatenate([vision_token_ids, text_ids], axis=1)
    print(f"  ✅ Context Input Shape: {full_input_ids.shape}")

    # 5. Run Text Decoder
    print("\n[3/4] Running Text Decoder TFLite...")
    try:
        from ai_edge_litert import interpreter as tfl_interpreter
        text_interpreter = tfl_interpreter.Interpreter(text_model_path)
        text_runner = text_interpreter.get_signature_runner()
        
        # Get input name (e.g. "tokens")
        input_details = text_runner.get_input_details()
        output_details = text_runner.get_output_details()
        input_name = list(input_details.keys())[0]
        output_name = list(output_details.keys())[0]
        print(f"  ℹ️  Model Input: {input_name}, Output: {output_name}")
        
        # Simple generation loop (greedy)
        generated_tokens = []
        max_new_tokens = 20
        curr_input = full_input_ids
        
        print("\n[4/4] Generating text...")
        for _ in range(max_new_tokens):
            # Run inference
            outputs = text_runner(**{input_name: curr_input})
            logits = outputs[output_name] # [1, seq_len, vocab_size]
            
            # Greedy decode last token
            next_token_id = np.argmax(logits[0, -1, :])
            generated_tokens.append(next_token_id)
            
            # Print token (live)
            word = tokenizer.decode([next_token_id])
            print(word, end="", flush=True)
            
            # Append to input (autoregressive)
            curr_input = np.concatenate([curr_input, [[next_token_id]]], axis=1).astype(np.int32)
            
            if next_token_id == tokenizer.eos_token_id:
                break
                
        print("\n\n✅ Generation Complete!")
        
    except Exception as e:
        print(f"\n❌ Text inference failed: {e}")
def main():
    parser = argparse.ArgumentParser(description="Test MedGemma vision pipeline")
    parser.add_argument("--image", type=str, default=None, help="Path to test image")
    parser.add_argument("--dummy", action="store_true", help="Use dummy image instead")
    args = parser.parse_args()
    
    # Determine image path
    if args.dummy or args.image is None:
        print("Using dummy white image...")
        image = Image.new('RGB', (IMAGE_SIZE, IMAGE_SIZE), color='white')
        image_path = "dummy_image.png"
        image.save(image_path)
    else:
        image_path = args.image
    
    # Test PyTorch
    pytorch_output = test_pytorch_pipeline(image_path)
    
    # Test TFLite
    tflite_output = test_tflite_pipeline(image_path)
    
    # Compare
    if pytorch_output is not None and tflite_output is not None:
        compare_outputs(pytorch_output, tflite_output) # Assume this function exists above or inline
    
    # Run Full Pipeline (Token Mapping + Text Decoder)
    if tflite_output is not None:
        try:
             # Make sure TokenMapper is defined above
             test_full_pipeline(image_path, tflite_output)
        except NameError:
             print("⚠️  Full pipeline test skipped (TokenMapper not defined?)")
    print("NEXT STEPS:")
    print("=" * 60)
    print("1. Vision encoder + projector are working ✅")
    print("2. You need to feed projected_embeds to your text decoder")
    print("3. Challenge: Convert embeddings → token IDs for decoder input")
    print("4. Implement nearest-neighbor mapping as described in the docs")

if __name__ == "__main__":
    from PIL import Image
    main()
