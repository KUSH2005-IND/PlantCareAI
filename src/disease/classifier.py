import os
import json
import tensorflow as tf
import numpy as np

from src.disease.preprocessing import preprocess_image


BASE_DIR = os.path.dirname(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
)

MODEL_PATH = os.path.join(
    BASE_DIR, "models", "disease", "plant_disease_model.h5"
)

CLASS_PATH = os.path.join(
    BASE_DIR, "models", "disease", "class_indices.json"
)


# Load model once when the module is imported
model = tf.keras.models.load_model(MODEL_PATH, compile=False)

with open(CLASS_PATH, "r") as file:
    class_indices = json.load(file)

# Ensure labels follow their actual numeric class indices
class_names = [
    name for name, index in
    sorted(class_indices.items(), key=lambda item: item[1])
]


def predict_disease(img_path):
    img_array = preprocess_image(img_path)

    prediction = model.predict(img_array, verbose=0)

    predicted_class = int(np.argmax(prediction[0]))
    confidence = float(np.max(prediction[0]))

    disease = class_names[predicted_class].replace("___", " : ")

    return disease, confidence