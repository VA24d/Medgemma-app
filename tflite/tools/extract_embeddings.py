import torch
from safetensors.torch import load_file
import numpy as np

SHARD_PATH = "medsiglip/medgemma_4b/model-00001-of-00002.safetensors"
OUTPUT_PATH = "medsiglip/vocab_embeddings.npy"

print(f"Loading {SHARD_PATH}...")
try:
    state_dict = load_file(SHARD_PATH)
    
    # Key might be 'model.embed_tokens.weight' or 'language_model.model.embed_tokens.weight'
    key = "model.embed_tokens.weight"
    if key not in state_dict:
        key = "language_model.model.embed_tokens.weight"
        
    if key in state_dict:
        print(f"Found {key}!")
        embeddings = state_dict[key].float().numpy() # Convert to float32 numpy
        print(f"Shape: {embeddings.shape} (Expect [262208, 2560])")
        
        np.save(OUTPUT_PATH, embeddings)
        print(f"✅ Saved to {OUTPUT_PATH}")
    else:
        print("❌ Could not find embed_tokens.weight in this shard.")
        print("Keys found:", list(state_dict.keys())[:5])

except Exception as e:
    print(f"❌ Error: {e}")
