
import tensorflow as tf
import os

tflite_path = "output_vision/medsiglip_vision_896.tflite"

if not os.path.exists(tflite_path):
    print(f"File not found: {tflite_path}")
    exit(1)

interpreter = tf.lite.Interpreter(model_path=tflite_path)
signature_defs = interpreter.get_signature_list()
print("Signature Defs:", signature_defs)

# detailed check
my_runner = interpreter.get_signature_runner()
print("Input details:", my_runner.get_input_details())
print("Output details:", my_runner.get_output_details())
