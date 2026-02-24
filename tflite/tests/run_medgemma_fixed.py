import numpy as np
import tensorflow as tf
from transformers import AutoTokenizer
import os
import sys

# --- CONFIGURATION ---
MODEL_PATH = "../model_assets/medgemma_4b_tpu_q8_ekv128.tflite"
TOKENIZER_ID = "google/medgemma-1.5-4b-it"

def main():
    print(f"--- Loading Model: {MODEL_PATH} ---")
    
    # Read file into memory (Bypass Windows large file path issues)
    if not os.path.exists(MODEL_PATH):
        raise FileNotFoundError(f"Model file not found: {MODEL_PATH}")
        
    with open(MODEL_PATH, "rb") as f:
        model_content = f.read()
        
    interpreter = tf.lite.Interpreter(model_content=model_content)
    # Get signature
    runner = interpreter.get_signature_runner("decode")
    print("✅ Interpreter loaded!")

    print(f"--- Loading Tokenizer: {TOKENIZER_ID} ---")
    try:
        # Try loading from Hub first
        tokenizer = AutoTokenizer.from_pretrained(TOKENIZER_ID)
    except Exception as e:
        print(f"⚠️ Failed to load from Hub, trying local 'tokenizer.model' fallback...")
        # Fallback to local sentencepiece if hub fails
        from transformers import LlamaTokenizer
        if os.path.exists("../model_assets/tokenizer.model"):
            tokenizer = LlamaTokenizer(vocab_file="../model_assets/tokenizer.model")
        else:
            print("❌ tokenizer.model not found locally either.")
            sys.exit(1)
            
    print("✅ Tokenizer loaded!")

    # --- INFERENCE FUNCTION ---
    def generate(prompt, max_new_tokens=100):
        print(f"\nPrompt: '{prompt}'")
        
        # KEY FIX: add_special_tokens=True adds BOS (ID 2)
        # Without this -> Repetitive Garbage!
        input_ids = tokenizer.encode(prompt, add_special_tokens=True)
        print(f"Input Tokens: {input_ids}")
        
        # Initialize KV Cache (Shape: [1, 4, 128, 256] for K, [1, 4, 256, 128] for V)
        caches = {}
        for i in range(34):
            caches[f'kv_cache_k_{i}'] = np.zeros((1, 4, 128, 256), dtype=np.float32)
            caches[f'kv_cache_v_{i}'] = np.zeros((1, 4, 256, 128), dtype=np.float32)

        # PREFILL PHASE
        # We model prefill by feeding one token at a time to build cache state
        # (This is slow but correct for diagnostics without a dedicated prefill signature)
        
        curr_logits = None
        
        print("Prefilling...", end="", flush=True)
        for pos, token in enumerate(input_ids):
            # Mask: [1, 1, 1, 128]
            # Valid positions = 0.0, rest = -1e9
            mask = np.full((1, 1, 1, 128), -1e9, dtype=np.float32)
            mask[:, :, :, :pos+1] = 0.0
            
            inputs = {
                'tokens': np.array([[token]], dtype=np.int32),
                'input_pos': np.array([pos], dtype=np.int32),
                'mask': mask,
                **caches
            }
            outputs = runner(**inputs)
            
            # Update Cache for next step
            for k in caches.keys():
                caches[k] = outputs[k]
            
            curr_logits = outputs['logits'][0, 0] # [VocabSize]
            
        # GENERATION PHASE
        generated_ids = []
        next_token = np.argmax(curr_logits) # Greedy decoding
        curr_pos = len(input_ids)
        
        print("\nGenerating:", end=" ", flush=True)
        
        for _ in range(max_new_tokens):
            if next_token == tokenizer.eos_token_id:
                break
                
            generated_ids.append(next_token)
            # Print token (decode individually)
            token_str = tokenizer.decode([next_token])
            print(token_str, end="", flush=True)
            
            # Prepare inputs for next step
            mask = np.full((1, 1, 1, 128), -1e9, dtype=np.float32)
            mask[:, :, :, :curr_pos+1] = 0.0
            
            inputs = {
                'tokens': np.array([[next_token]], dtype=np.int32),
                'input_pos': np.array([curr_pos], dtype=np.int32),
                'mask': mask,
                **caches
            }
            outputs = runner(**inputs)
            
            # Update Cache
            for k in caches.keys():
                caches[k] = outputs[k]
            
            curr_logits = outputs['logits'][0, 0]
            next_token = np.argmax(curr_logits)
            curr_pos += 1
            
        print("\n\n--- Done ---")

    # --- EXECUTE TESTS ---
    generate("What are the symptoms of diabetes?")
    generate("How is high blood pressure treated?")

if __name__ == "__main__":
    main()
