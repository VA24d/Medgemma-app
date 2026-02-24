
import tensorflow as tf
import os

tflite_path = "../medgemma_4b_tpu_q8_ekv128.tflite"

if not os.path.exists(tflite_path):
    print(f"File not found: {tflite_path}")
    exit(1)

interpreter = tf.lite.Interpreter(model_path=tflite_path)
signature_defs = interpreter.get_signature_list()
print("Signature Keys:", list(signature_defs.keys()))

runner = interpreter.get_signature_runner("prefill_64")
print("Prefill Input Details:", runner.get_input_details())
