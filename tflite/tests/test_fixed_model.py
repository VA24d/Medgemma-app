"""
Test the fixed MedGemma model for medical question answering.
"""
import os
import numpy as np
import tensorflow as tf

TFLITE_PATH = "../model_assets/medgemma_4b_tpu_q8_ekv128.tflite"
TOKENIZER_PATH = "../model_assets/tokenizer.model"

# Medical test questions
TEST_QUESTIONS = [
    "What are the symptoms of diabetes?",
    "How is high blood pressure treated?",
]

def load_tokenizer():
    """Load sentencepiece tokenizer."""
    import sentencepiece as spm
    sp = spm.SentencePieceProcessor()
    sp.Load(TOKENIZER_PATH)
    print(f"✅ Tokenizer loaded! Vocab size: {sp.GetPieceSize()}")
    return sp

def load_model():
    """Load TFLite model."""
    print(f"Loading {TFLITE_PATH}...")
    print(f"File size: {os.path.getsize(TFLITE_PATH) / (1024**3):.2f} GB")
    
    # Read into memory (faster for large files on Windows)
    with open(TFLITE_PATH, 'rb') as f:
        model_content = f.read()
    
    interpreter = tf.lite.Interpreter(model_content=model_content)
    del model_content
    print("✅ Interpreter loaded!")
    return interpreter

def generate_response(interpreter, tokenizer, prompt, max_tokens=30):
    """Generate a response."""
    
    signatures = interpreter.get_signature_list()
    sig_name = list(signatures.keys())[0]
    sig_inputs = signatures[sig_name]['inputs']
    runner = interpreter.get_signature_runner(sig_name)
    
    # Tokenize
    input_ids = tokenizer.EncodeAsIds(prompt)
    print(f"Prompt: '{prompt}' -> {len(input_ids)} tokens")
    
    # Initialize inputs
    kv_seq_len = 128  # ekv128 model
    inputs = {}
    for sig_input in sig_inputs:
        if 'token' in sig_input.lower():
            inputs[sig_input] = np.array([[input_ids[0]]], dtype=np.int32)
        elif 'pos' in sig_input.lower():
            inputs[sig_input] = np.array([0], dtype=np.int32)
        elif 'mask' in sig_input.lower():
            inputs[sig_input] = np.zeros((1, 1, 1, kv_seq_len), dtype=np.float32)
        elif 'kv_cache_k' in sig_input:
            inputs[sig_input] = np.zeros((1, 4, kv_seq_len, 256), dtype=np.float32)
        elif 'kv_cache_v' in sig_input:
            inputs[sig_input] = np.zeros((1, 4, 256, kv_seq_len), dtype=np.float32)
    
    # Generate
    generated_ids = []
    EOS_TOKENS = {0, 1, 107}
    
    for step in range(len(input_ids) + max_tokens):
        # Current token
        if step < len(input_ids):
            current_token = input_ids[step]
            is_prefill = True
        else:
            current_token = generated_ids[-1] if generated_ids else input_ids[-1]
            is_prefill = False
        
        # Update inputs
        for sig_input in sig_inputs:
            if 'token' in sig_input.lower():
                inputs[sig_input] = np.array([[current_token]], dtype=np.int32)
            elif 'pos' in sig_input.lower():
                inputs[sig_input] = np.array([step], dtype=np.int32)
        
        # Run
        results = runner(**inputs)
        logits = results.get('logits', list(results.values())[0])
        next_token = int(np.argmax(logits[0, 0, :]))
        
        # Debug first few generation steps
        if not is_prefill and len(generated_ids) < 5:
            decoded = tokenizer.IdToPiece(next_token) if next_token < tokenizer.GetPieceSize() else f"[{next_token}]"
            print(f"  Gen step {len(generated_ids)}: token={next_token} -> '{decoded}'")
        
        if not is_prefill:
            if next_token in EOS_TOKENS:
                print(f"  EOS at step {step}")
                break
            generated_ids.append(next_token)
        
        # Update KV caches
        for key, val in results.items():
            if 'kv_cache' in key and key in inputs:
                inputs[key] = val
    
    # Decode
    response = tokenizer.DecodeIds(generated_ids)
    return response, generated_ids

def main():
    print("="*60)
    print("TESTING FIXED MEDGEMMA MODEL")
    print("="*60 + "\n")
    
    os.chdir(os.path.dirname(os.path.abspath(__file__)) if __file__ else ".")
    
    tokenizer = load_tokenizer()
    interpreter = load_model()
    
    for i, question in enumerate(TEST_QUESTIONS, 1):
        print(f"\n{'='*60}")
        print(f"Q{i}: {question}")
        print("="*60)
        
        try:
            response, token_ids = generate_response(interpreter, tokenizer, question)
            print(f"\nGenerated {len(token_ids)} tokens")
            print(f"Response: {response}")
            
            # Quality check
            if len(response.strip()) < 5:
                print("⚠️ Response too short!")
            elif any(ord(c) > 0x0FFF for c in response):  # Non-Latin chars
                print("⚠️ Contains non-English characters!")
            else:
                print("✅ Response looks valid!")
                
        except Exception as e:
            print(f"❌ Error: {e}")
            import traceback
            traceback.print_exc()
    
    print("\n" + "="*60)
    print("TEST COMPLETE")
    print("="*60)

if __name__ == "__main__":
    main()
