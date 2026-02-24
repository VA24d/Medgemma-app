import os
from huggingface_hub import snapshot_download

MODEL_ID = "google/medgemma-1.5-4b-it"
MODEL_PATH = "medgemma_4b"

print(f"Downloading {MODEL_ID} to {MODEL_PATH}...")
snapshot_download(repo_id=MODEL_ID, local_dir=MODEL_PATH, local_dir_use_symlinks=False)
print("Download complete.")
