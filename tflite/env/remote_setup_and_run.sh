#!/bin/bash
set -e

echo "=== Remote Setup & Conversion on $(hostname) ==="

# 1. Setup Venv
if [ ! -d "venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv venv
fi

source venv/bin/activate

# 2. Install Dependencies
echo "Installing dependencies..."
pip install --upgrade pip
# Uninstalls to be safe
pip uninstall -y ai-edge-torch litert-torch tensorflow-cpu tensorflow numpy

# Install robust stack
pip install ai-edge-torch==0.2.0
pip install numpy==1.26.4
pip install tensorflow-cpu

# 3. Operations
echo "Running conversion..."
# Assuming script is in current dir
python3 convert_vision.py

echo "=== DONE ==="
