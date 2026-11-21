import tensorflow as tf

MODEL_PATH = "plant_disease_model.tflite"

interpreter = tf.lite.Interpreter(
    model_path=MODEL_PATH,
    num_threads=4
)

interpreter.allocate_tensors()

inputs = interpreter.get_input_details()
outputs = interpreter.get_output_details()

print("\nINPUT DETAILS")
for item in inputs:
    print("Name:", item["name"])
    print("Shape:", item["shape"])
    print("Data type:", item["dtype"])
    print("Quantization:", item["quantization_parameters"])

print("\nOUTPUT DETAILS")
for item in outputs:
    print("Name:", item["name"])
    print("Shape:", item["shape"])
    print("Data type:", item["dtype"])
    print("Quantization:", item["quantization_parameters"])