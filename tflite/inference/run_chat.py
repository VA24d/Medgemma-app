import numpy as np
import tensorflow as tf
from transformers import AutoTokenizer
import os
import sys

# --- CONFIGURATION ---
# Paths relative to this script's location
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_FILE = os.path.join(SCRIPT_DIR, "..", "models", "medgemma_4b_tpu_q8_ekv128.tflite")
TOKENIZER_DIR = os.path.join(SCRIPT_DIR, "..", "tokenizer")

def main():
    print("="*60)
    print("  🏥 MedGemma 4B TFLite Inference (Offline)  ")
    print("="*60)

    # 1. Load Model
    if not os.path.exists(MODEL_FILE):
        print(f"❌ Error: Model file '{MODEL_FILE}' not found.")
        input("Press Enter to exit...")
        sys.exit(1)
        
    print(f"Loading {MODEL_FILE}...")
    try:
        # Read file into memory (Windows Fix)
        with open(MODEL_FILE, "rb") as f:
            model_content = f.read()
            
        interpreter = tf.lite.Interpreter(model_content=model_content)
        runner = interpreter.get_signature_runner("decode")
        print("✅ Model loaded successfully!")
    except Exception as e:
        print(f"❌ Failed to load model: {e}")
        input("Press Enter to exit...")
        sys.exit(1)

    # 2. Load Tokenizer
    print("Loading tokenizer...")
    try:
        # Load from tokenizer directory
        tokenizer = AutoTokenizer.from_pretrained(TOKENIZER_DIR)
        print("✅ Tokenizer loaded!")
    except Exception as e:
        print(f"❌ Failed to load tokenizer from {TOKENIZER_DIR}: {e}")
        print("Ensure tokenizer files exist in the tokenizer/ folder.")
        sys.exit(1)

    # 3. Inference Loop
    print("\nStarting Chat (Type 'quit' to exit)")
    print("-" * 60)

    while True:
        try:
            prompt = input("\n👨‍⚕️ You: ")
            if prompt.lower() in ['quit', 'exit']:
                break
            
            print("🤖 MedGemma: ", end="", flush=True)
            
            # --- GENERATION LOGIC ---
            # KEY FIX: add_special_tokens=True adds BOS (ID 2)
            input_ids = tokenizer.encode(prompt, add_special_tokens=True)
            
            # Initialize KV Cache
            caches = {}
            for i in range(34):
                caches[f'kv_cache_k_{i}'] = np.zeros((1, 4, 128, 256), dtype=np.float32)
                caches[f'kv_cache_v_{i}'] = np.zeros((1, 4, 256, 128), dtype=np.float32)

            # Prefill Phase
            curr_logits = None
            for pos, token in enumerate(input_ids):
                mask = np.full((1, 1, 1, 128), -1e9, dtype=np.float32)
                mask[:, :, :, :pos+1] = 0.0
                
                inputs = {
                    'tokens': np.array([[token]], dtype=np.int32),
                    'input_pos': np.array([pos], dtype=np.int32),
                    'mask': mask,
                    **caches
                }
                outputs = runner(**inputs)
                for k in caches.keys(): caches[k] = outputs[k]
                curr_logits = outputs['logits'][0, 0]

            # Generation Phase
            next_token = np.argmax(curr_logits)
            curr_pos = len(input_ids)
            
            for _ in range(100):
                if next_token == tokenizer.eos_token_id:
                    break
                    
                print(tokenizer.decode([next_token]), end="", flush=True)
                
                mask = np.full((1, 1, 1, 128), -1e9, dtype=np.float32)
                mask[:, :, :, :curr_pos+1] = 0.0
                
                inputs = {
                    'tokens': np.array([[next_token]], dtype=np.int32),
                    'input_pos': np.array([curr_pos], dtype=np.int32),
                    'mask': mask,
                    **caches
                }
                outputs = runner(**inputs)
                for k in caches.keys(): caches[k] = outputs[k]
                curr_logits = outputs['logits'][0, 0]
                next_token = np.argmax(curr_logits)
                curr_pos += 1
            
            print() 
            
        except KeyboardInterrupt:
            print("\nExiting...")
            break
        except Exception as e:
            print(f"\n❌ Error: {e}")

if __name__ == "__main__":
    main()
