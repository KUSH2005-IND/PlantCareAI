"""
Convert plant_disease_model.h5 → quantized TFLite + labels.txt

Produces two files in the models/ directory:
  - plant_disease_model.tflite   (float16-quantized)
  - labels.txt                   (one label per line, sorted by class index)

Float16 quantization is chosen because:
  - ~2× size reduction over the raw float32 model
  - No calibration dataset required (unlike full int8)
  - Minimal accuracy loss on classification tasks
  - Well-supported by TFLite GPU delegate on Android
"""

import json
import os
import tensorflow as tf

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODELS_DIR = os.path.join(BASE_DIR, "models")

H5_PATH = os.path.join(MODELS_DIR, "plant_disease_model.h5")
CLASS_INDICES_PATH = os.path.join(MODELS_DIR, "class_indices.json")
TFLITE_PATH = os.path.join(MODELS_DIR, "plant_disease_model.tflite")
LABELS_PATH = os.path.join(MODELS_DIR, "labels.txt")


def convert_model():
    """Load Keras model, apply float16 quantization, and export as TFLite."""
    print(f"Loading Keras model from {H5_PATH} ...")
    model = tf.keras.models.load_model(H5_PATH, compile=False)

    # Print model input details for verification
    input_shape = model.input_shape
    print(f"  Input shape : {input_shape}")
    print(f"  Output shape: {model.output_shape}")

    converter = tf.lite.TFLiteConverter.from_keras_model(model)

    # ── Float16 quantization ──
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]

    print("Converting to TFLite with float16 quantization ...")
    tflite_model = converter.convert()

    with open(TFLITE_PATH, "wb") as f:
        f.write(tflite_model)

    size_mb = os.path.getsize(TFLITE_PATH) / (1024 * 1024)
    original_mb = os.path.getsize(H5_PATH) / (1024 * 1024)
    print(f"  Saved: {TFLITE_PATH}")
    print(f"  Original size : {original_mb:.2f} MB")
    print(f"  TFLite size   : {size_mb:.2f} MB")
    print(f"  Reduction     : {(1 - size_mb / original_mb) * 100:.1f}%")

    # ── Verify the exported model ──
    interpreter = tf.lite.Interpreter(model_path=TFLITE_PATH)
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    print("\n  TFLite input  :", input_details[0]["shape"], input_details[0]["dtype"])
    print("  TFLite output :", output_details[0]["shape"], output_details[0]["dtype"])


def generate_labels():
    """Generate labels.txt sorted by class index (one per line)."""
    print(f"\nLoading class indices from {CLASS_INDICES_PATH} ...")
    with open(CLASS_INDICES_PATH) as f:
        class_indices = json.load(f)  # {"class_name": index, ...}

    # Sort by index (value) so line N = class index N
    sorted_labels = sorted(class_indices.items(), key=lambda x: x[1])

    with open(LABELS_PATH, "w", encoding="utf-8") as f:
        for label, idx in sorted_labels:
            f.write(f"{label}\n")

    print(f"  Saved: {LABELS_PATH}")
    print(f"  Total classes: {len(sorted_labels)}")
    print(f"  First 5: {[l for l, _ in sorted_labels[:5]]}")
    print(f"  Last  5: {[l for l, _ in sorted_labels[-5:]]}")


if __name__ == "__main__":
    convert_model()
    generate_labels()
    print("\n[OK] Done! Ready for Android integration.")
