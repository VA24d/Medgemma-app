"""Test the Q8 model to verify logit magnitudes."""
import numpy as np
import tensorflow as tf
import json
import os

# Use relative paths - model should be in ../models/
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(SCRIPT_DIR, '..', 'models', 'medgemma_4b_tpu_q8_ekv128.tflite')
TOKENIZER_DIR = os.path.join(SCRIPT_DIR, '..', 'tokenizer')

NUM_LAYERS = 34
NUM_KV_HEADS = 4
KV_MAX = 128
HEAD_DIM = 256
BOS = 2

print(f"Loading {MODEL_PATH} ({3.6:.1f}GB) into memory...")
with open(MODEL_PATH, 'rb') as f:
    model_bytes = f.read()
print(f"Read {len(model_bytes)/1e9:.2f}GB, creating interpreter...")

interp = tf.lite.Interpreter(model_content=model_bytes)
del model_bytes  # free memory
runner = interp.get_signature_runner('decode')
print("Model loaded!")

# Initialize KV caches
caches = {}
for i in range(NUM_LAYERS):
    caches[f'kv_cache_k_{i}'] = np.zeros((1, NUM_KV_HEADS, KV_MAX, HEAD_DIM), dtype=np.float32)
    caches[f'kv_cache_v_{i}'] = np.zeros((1, NUM_KV_HEADS, HEAD_DIM, KV_MAX), dtype=np.float32)

# Test BOS only
print("\nRunning BOS token (token 2) at position 0...")
mask = np.full((1, 1, 1, KV_MAX), -1e9, dtype=np.float32)
mask[:, :, :, :1] = 0.0

inputs = {
    'tokens': np.array([[BOS]], dtype=np.int32),
    'input_pos': np.array([0], dtype=np.int32),
    'mask': mask,
    **caches
}
outputs = runner(**inputs)
logits = outputs['logits'][0, 0]

print(f"BOS logits: min={logits.min():.4f} max={logits.max():.4f}")
print(f"First 10 logits: {logits[:10]}")
print(f"Token 3617 (package): {logits[3617]:.4f}")
print(f"Token 818 (The): {logits[818]:.4f}")

top5_idx = np.argsort(logits)[-5:][::-1]
# Load tokenizer for decoding
with open(os.path.join(TOKENIZER_DIR, 'tokenizer.json'), 'r', encoding='utf-8') as f:
    tok_data = json.load(f)
vocab = {}
if tok_data.get('added_tokens'):
    for t in tok_data['added_tokens']:
        vocab[t['content']] = t['id']
if tok_data.get('model', {}).get('vocab'):
    vocab.update(tok_data['model']['vocab'])
id_to_token = {v: k for k, v in vocab.items()}

print(f"Top 5: {[(int(i), f'{logits[i]:.4f}', id_to_token.get(int(i), f'<{i}>')) for i in top5_idx]}")
print(f"\nArgmax: token {int(np.argmax(logits))} = '{id_to_token.get(int(np.argmax(logits)), '?')}'")
