"""Inspect the tflite model tensor shapes, data types, and quantization params."""
import tensorflow as tf
import os
import sys

# Use relative paths - can also pass model path as argument
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = sys.argv[1] if len(sys.argv) > 1 else os.path.join(SCRIPT_DIR, '..', 'models', 'medgemma_4b_tpu_q8_ekv128.tflite')

print(f"Loading model: {MODEL_PATH}")
interp = tf.lite.Interpreter(model_path=MODEL_PATH, num_threads=4)
interp.allocate_tensors()

sigs = interp.get_signature_list()
print(f"\nSignatures: {list(sigs.keys())}")

full_sigs = interp._get_full_signature_list()

for sig_name in ['decode']:
    print(f"\n=== Signature: {sig_name} ===")
    sig = full_sigs[sig_name]
    
    # Key inputs
    for tname in ['tokens', 'input_pos', 'mask', 'kv_cache_k_0', 'kv_cache_v_0']:
        if tname in sig['inputs']:
            idx = sig['inputs'][tname]
            td = interp.get_tensor_details()
            for d in td:
                if d['index'] == idx:
                    print(f"  INPUT  {tname:20s}: dtype={str(d['dtype']):20s} shape={d['shape']}  quant_params={d.get('quantization_parameters', {})}")
                    break
    
    # Key outputs
    for tname in ['logits', 'kv_cache_k_0', 'kv_cache_v_0']:
        if tname in sig['outputs']:
            idx = sig['outputs'][tname]
            td = interp.get_tensor_details()
            for d in td:
                if d['index'] == idx:
                    print(f"  OUTPUT {tname:20s}: dtype={str(d['dtype']):20s} shape={d['shape']}  quant_params={d.get('quantization_parameters', {})}")
                    break

print("\nDone.")
