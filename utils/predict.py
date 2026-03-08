import tensorflow as tf
import numpy as np
from PIL import Image
import json
import os

base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

model = os.path.join(base, "models", "plant_disease_model.h5")
class_path = os.path.join(base, "models", "class_indices.json")

model = tf.keras.models.load_model(model, compile=False)

with open(class_path) as f:
    class_indices = json.load(f)

class_names = list(class_indices.keys())


def predict_image(img_path):

    img = Image.open(img_path).resize((224,224))

    img = np.array(img) / 255.0
    img = np.expand_dims(img, axis=0)

    prediction = model.predict(img)

    predicted_class = np.argmax(prediction)
    confidence = np.max(prediction)

    disease = class_names[predicted_class].replace("___"," : ")

    return disease, confidence