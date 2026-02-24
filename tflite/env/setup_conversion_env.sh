#!/bin/bash
# Auto-setup script for TFLite conversion environment
# Run with: bash setup_conversion_env.sh

set -e  # Exit on error

echo "================================"
echo "TFLite Conversion Environment Setup"
echo "================================"
echo ""

# Check Python version
echo "[1/5] Checking Python version..."
PYTHON_VERSION=$(python3 --version 2>&1 | awk '{print $2}')
echo "   Found: Python $PYTHON_VERSION"

if [[ ! "$PYTHON_VERSION" =~ ^3\.(8|9|10|11) ]]; then
    echo "   ⚠️  Warning: Python 3.8-3.11 recommended"
fi

# Check if using system Python
PYTHON_PATH=$(which python3)
echo "   Using: $PYTHON_PATH"

if [[ "$PYTHON_PATH" == *"/venv/"* ]] || [[ "$PYTHON_PATH" == *"/.venv/"* ]]; then
    echo "   ❌ ERROR: You're in a virtual environment!"
    echo "   Please deactivate venv and run with system Python3"
    exit 1
fi

# Uninstall conflicting packages
echo ""
echo "[2/5] Removing conflicting packages..."
python3 -m pip uninstall -y ai-edge-torch 2>/dev/null || true
echo "   Done"

# Install core dependencies
echo ""
echo "[3/5] Installing litert-torch..."
python3 -m pip install --user litert-torch==0.8.0
echo "   ✅ litert-torch installed"

# Install protobuf (critical version)
echo ""
echo "[4/5] Installing protobuf 3.20.3..."
python3 -m pip install --user protobuf==3.20.3
echo "   ✅ protobuf installed"

# Install PyTorch (if not already installed)
echo ""
echo "[5/5] Checking PyTorch..."
if python3 -c "import torch" 2>/dev/null; then
    TORCH_VERSION=$(python3 -c "import torch; print(torch.__version__)")
    echo "   PyTorch $TORCH_VERSION already installed"
else
    echo "   Installing PyTorch..."
    python3 -m pip install --user torch torchvision
    echo "   ✅ PyTorch installed"
fi

# Verify installation
echo ""
echo "================================"
echo "Verifying Installation..."
echo "================================"

python3 -c "
import sys
try:
    import litert_torch
    print('✅ litert_torch:', litert_torch.__version__)
except ImportError as e:
    print('❌ litert_torch not found:', e)
    sys.exit(1)

try:
    import torch
    print('✅ torch:', torch.__version__)
except ImportError as e:
    print('❌ torch not found:', e)
    sys.exit(1)

try:
    import google.protobuf
    print('✅ protobuf:', google.protobuf.__version__)
except ImportError as e:
    print('❌ protobuf not found:', e)
    sys.exit(1)
"

echo ""
echo "================================"
echo "✅ Setup Complete!"
echo "================================"
echo ""
echo "You can now run the conversion:"
echo "  python3 convert_448_tflite_local.py"
echo ""
