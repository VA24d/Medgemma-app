#!/usr/bin/env python3
"""
Download GGUF models for benchmarking.

Downloads all quantized MedGemma 1.5 4B models from HuggingFace to the models/ directory.
Requires HuggingFace CLI authentication for access to MedGemma models.

Requirements:
    pip install huggingface_hub

Usage:
    # Login first
    huggingface-cli login
    
    # Download to default location (models/)
    python benchmarking/download_models.py
    
    # Download to custom location
    python benchmarking/download_models.py --models-dir /path/to/models
"""

import argparse
import os
from pathlib import Path

from huggingface_hub import hf_hub_download


REPO_ID = "unsloth/medgemma-1.5-4b-it-GGUF"
MODEL_FILES = [
    "medgemma-1.5-4b-it-BF16.gguf",    # 7.3 GB
    "medgemma-1.5-4b-it-Q8_0.gguf",    # 3.9 GB
    "medgemma-1.5-4b-it-Q6_K.gguf",    # 3.0 GB
    "medgemma-1.5-4b-it-Q4_K_M.gguf",  # 2.4 GB
]


def main():
    parser = argparse.ArgumentParser(description="Download MedGemma GGUF models for benchmarking")
    parser.add_argument(
        "--models-dir",
        type=str,
        default="models",
        help="Directory to download models to (default: models/)",
    )
    args = parser.parse_args()

    models_dir = Path(args.models_dir)
    models_dir.mkdir(parents=True, exist_ok=True)

    print(f"📦 Downloading {len(MODEL_FILES)} GGUF models from {REPO_ID}")
    print(f"📁 Destination: {models_dir.absolute()}\n")
    print("⚠️  Make sure you're logged in: huggingface-cli login\n")

    total_size_gb = 16.6
    print(f"Total download size: ~{total_size_gb:.1f} GB\n")

    for i, filename in enumerate(MODEL_FILES, 1):
        print(f"[{i}/{len(MODEL_FILES)}] Downloading {filename}...")
        try:
            downloaded_path = hf_hub_download(
                repo_id=REPO_ID,
                filename=filename,
                local_dir=str(models_dir),
                local_dir_use_symlinks=False,
            )
            print(f"✅ Downloaded to: {downloaded_path}\n")
        except Exception as e:
            print(f"❌ Error downloading {filename}: {e}\n")
            print("Make sure you have access to MedGemma models and are logged in:")
            print("  huggingface-cli login\n")
            return 1

    print("✨ All models downloaded successfully!")
    print(f"\n📝 Models are ready for benchmarking. Update models.json paths if needed:")
    print(f'   "path": "{models_dir}/medgemma-1.5-4b-it-Q4_K_M.gguf"')
    return 0


if __name__ == "__main__":
    exit(main())
