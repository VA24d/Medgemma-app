import glob
import os
from safetensors.torch import load_file

MODEL_PATH = "medgemma_4b"
safetensor_files = sorted(glob.glob(os.path.join(MODEL_PATH, "*.safetensors")))

print(f"Found {len(safetensor_files)} safetensor files")

for f in safetensor_files:
    if "index.json" in f: continue
    print(f"\nProcessing {os.path.basename(f)}...")
    state_dict = load_file(f)
    keys = list(state_dict.keys())
    print(f"  Total keys: {len(keys)}")
    
    # Print first 10 keys
    print("  First 10 keys:")
    for k in keys[:10]:
        print(f"    {k}")
        
    # Search for 'vision' or 'projector'
    vision_keys = [k for k in keys if "vision" in k]
    projector_keys = [k for k in keys if "projector" in k or "multi_modal" in k]
    
    if vision_keys:
        print(f"  Found {len(vision_keys)} vision keys!")
        print(f"    Example: {vision_keys[0]}")
    else:
        print("  No 'vision' keys found.")
        
    if projector_keys:
        print(f"  Found {len(projector_keys)} projector keys!")
        print(f"    Example: {projector_keys[0]}")
    else:
        print("  No 'projector' keys found.")
