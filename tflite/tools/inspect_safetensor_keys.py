
import glob
import os
from safetensors import safe_open

model_path = "medgemma_4b"
safetensor_files = glob.glob(os.path.join(model_path, "*.safetensors"))

for sf in safetensor_files:
    print(f"Scanning {sf}...")
    try:
        with safe_open(sf, framework="pt", device="cpu") as f:
            keys = f.keys()
            print(f"  Found {len(keys)} keys.")
            # Print keys containing 'embed' or 'token'
            for k in keys:
                if "embed" in k or "token" in k or "weight" in k:
                    print(f"    - {k}")
    except Exception as e:
        print(f"Error reading {sf}: {e}")
