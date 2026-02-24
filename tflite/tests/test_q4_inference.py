"""Quick inference test for the Q4 tflite model to verify model produces coherent output."""
import numpy as np
import tensorflow as tf
import json, os

# Use relative paths
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(SCRIPT_DIR, '..', 'models', 'medgemma_4b_tpu_q4_block128_ekv512.tflite')
TOKENIZER_DIR = os.path.join(SCRIPT_DIR, '..', 'tokenizer')
TOKENIZER_PATH = os.path.join(TOKENIZER_DIR, 'tokenizer.json')

# Load tokenizer vocabulary
print("Loading tokenizer...")
with open(TOKENIZER_PATH, 'r', encoding='utf-8') as f:
    tok_data = json.load(f)

vocab = {}
if tok_data.get('added_tokens'):
    for t in tok_data['added_tokens']:
        vocab[t['content']] = t['id']
if tok_data.get('model', {}).get('vocab'):
    vocab.update(tok_data['model']['vocab'])

id_to_token = {v: k for k, v in vocab.items()}
print(f"Vocab size: {len(vocab)}")

# Simple prompt
prompt = "What is diabetes"
BOS = 2; EOS = 1

# Use HuggingFace tokenizer if available, else simple encode
try:
    from transformers import AutoTokenizer
    tokenizer = AutoTokenizer.from_pretrained(TOKENIZER_DIR)
    input_ids = tokenizer.encode(prompt, add_special_tokens=True)
    print(f"HF Tokenizer input_ids: {input_ids}")
except:
    # Manual greedy encode
    text = '\u2581' + prompt.replace(' ', '\u2581')
    input_ids = [BOS]
    i = 0
    while i < len(text):
        best = ''
        best_id = -1
        for l in range(min(32, len(text)-i), 0, -1):
            candidate = text[i:i+l]
            if candidate in vocab:
                best = candidate
                best_id = vocab[candidate]
                break
        if best_id >= 0:
            input_ids.append(best_id)
            i += len(best)
        else:
            input_ids.append(vocab.get(text[i], 3))
            i += 1
    print(f"Manual input_ids: {input_ids}")

print(f"Decoded tokens: {[id_to_token.get(t, f'<{t}>') for t in input_ids]}")

# Load model
print(f"\nLoading model: {MODEL_PATH}")
with open(MODEL_PATH, 'rb') as f:
    model_bytes = f.read()
interp = tf.lite.Interpreter(model_content=model_bytes)
runner = interp.get_signature_runner('decode')
print("Model loaded!")

# Initialize KV cache (matching Q4 model: 34 layers, 4 heads, ekv512, head_dim 256)
NUM_LAYERS = 34
NUM_KV_HEADS = 4
KV_MAX = 512
HEAD_DIM = 256

caches = {}
for i in range(NUM_LAYERS):
    caches[f'kv_cache_k_{i}'] = np.zeros((1, NUM_KV_HEADS, KV_MAX, HEAD_DIM), dtype=np.float32)
    caches[f'kv_cache_v_{i}'] = np.zeros((1, NUM_KV_HEADS, HEAD_DIM, KV_MAX), dtype=np.float32)

# Prefill
print(f"\nPrefilling {len(input_ids)} tokens...")
curr_logits = None
for pos, token in enumerate(input_ids):
    mask = np.full((1, 1, 1, KV_MAX), -1e9, dtype=np.float32)
    mask[:, :, :, :pos+1] = 0.0
    
    inputs = {
        'tokens': np.array([[token]], dtype=np.int32),
        'input_pos': np.array([pos], dtype=np.int32),
        'mask': mask,
        **caches
    }
    outputs = runner(**inputs)
    for k in caches.keys():
        caches[k] = outputs[k]
    curr_logits = outputs['logits'][0, 0]
    
    if pos == 0 or pos == len(input_ids) - 1:
        top5_idx = np.argsort(curr_logits)[-5:][::-1]
        print(f"  pos={pos} token={token} ({id_to_token.get(token, '?')}) logits: min={curr_logits.min():.2f} max={curr_logits.max():.2f}")
        print(f"  top5: {[(int(i), f'{curr_logits[i]:.2f}', id_to_token.get(int(i), f'<{i}>')) for i in top5_idx]}")

# Generate
print(f"\nGenerating...")
next_token = int(np.argmax(curr_logits))
curr_pos = len(input_ids)
generated = []

for step in range(20):
    if next_token == EOS:
        print("[EOS]")
        break
    
    token_str = id_to_token.get(next_token, f'<{next_token}>').replace('\u2581', ' ')
    generated.append(token_str)
    print(f"  step={step} token={next_token} -> '{token_str}'")
    
    mask = np.full((1, 1, 1, KV_MAX), -1e9, dtype=np.float32)
    mask[:, :, :, :curr_pos+1] = 0.0
    
    inputs = {
        'tokens': np.array([[next_token]], dtype=np.int32),
        'input_pos': np.array([curr_pos], dtype=np.int32),
        'mask': mask,
        **caches
    }
    outputs = runner(**inputs)
    for k in caches.keys():
        caches[k] = outputs[k]
    curr_logits = outputs['logits'][0, 0]
    next_token = int(np.argmax(curr_logits))
    curr_pos += 1

print(f"\nFull output: {''.join(generated)}")
