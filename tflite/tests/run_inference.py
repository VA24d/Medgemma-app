import numpy as np
import tensorflow as tf
from ai_edge_litert import interpreter as tfl_interpreter
from transformers import AutoTokenizer
import os

MODEL_PATH = "../model_assets/medgemma_4b_tpu_q8_ekv128.tflite"
TOKENIZER_PATH = "../model_assets" # Local dir

def main():
    print("--- Loading Tokenizer ---")
    tokenizer = AutoTokenizer.from_pretrained(TOKENIZER_PATH)
    
    print(f"--- Loading Model: {MODEL_PATH} ---")
    interpreter = tfl_interpreter.Interpreter(model_path=MODEL_PATH)
    runner = interpreter.get_signature_runner("decode")
    
    print("--- Initializing KV Cache (Zeros) ---")
    # Identify cache shapes from signature
    # We know from inspection: K: [1,4,128,256], V: [1,4,256,128]
    # Num layers: 34
    
    caches = {}
    for i in range(34):
        # K shape: [1, 4, 128, 256]
        caches[f'kv_cache_k_{i}'] = np.zeros((1, 4, 128, 256), dtype=np.float32)
        # V shape: [1, 4, 256, 128]
        caches[f'kv_cache_v_{i}'] = np.zeros((1, 4, 256, 128), dtype=np.float32)

    prompt = "Symptoms of flu include"
    print(f"\nPrompt: {prompt}")
    tokens = tokenizer.encode(prompt, add_special_tokens=True)
    
    print("\n--- Generating ---")
    # Generation Loop
    generated_ids = []
    
    curr_token = tokens[0]
    
    # We will simulate a simplified loop:
    # 1. Prefill manually (feed prompt tokens one by one)
    # 2. Decode (feed new tokens)
    
    max_len = 64 # Stay within 128 limit
    
    for pos in range(max_len):
        # 1. Prepare Inputs
        input_token = np.array([[curr_token]], dtype=np.int32)
        input_pos = np.array([pos], dtype=np.int32)
        
        # Mask: [1, 1, 1, 128]
        # Valid up to pos (inclusive)
        # We assume 0.0 = Attend, -1e9 = Mask
        mask = np.full((1, 1, 1, 128), -1e9, dtype=np.float32)
        mask[:, :, :, :pos+1] = 0.0

        # 2. Run Inference
        inputs = {
            'tokens': input_token,
            'input_pos': input_pos,
            'mask': mask,
            **caches
        }
        
        results = runner(**inputs)
        
        # 3. Update Cache & Get Logits
        logits = results['logits'] # [1, 1, Vocab]
        
        # Update caches for next step
        for k in caches.keys():
            caches[k] = results[k]
            
        # 4. Next Token logic
        if pos < len(tokens) - 1:
            # Still in prompt
            curr_token = tokens[pos + 1]
            print(tokenizer.decode([curr_token]), end="", flush=True)
        else:
            # Generating new
            next_token = np.argmax(logits[0, 0])
            curr_token = next_token
            print(tokenizer.decode([curr_token]), end="", flush=True)
            generated_ids.append(curr_token)
            
            if curr_token == tokenizer.eos_token_id:
                break
                
    print("\n\n--- Done ---")

if __name__ == "__main__":
    main()
