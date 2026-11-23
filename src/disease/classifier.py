from pathlib import Path
from typing import Optional, Tuple
import numpy as np
import tensorflow as tf
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
MODEL_PATH = ROOT / "models/disease/disease_finetuned.keras"
_MODEL = tf.keras.models.load_model(MODEL_PATH, compile=False)

PV_CLASSES = [
    "Apple___Apple_scab", "Apple___Black_rot", "Apple___Cedar_apple_rust", "Apple___healthy",
    "Blueberry___healthy", "Cherry_(including_sour)___Powdery_mildew", "Cherry_(including_sour)___healthy",
    "Corn_(maize)___Cercospora_leaf_spot Gray_leaf_spot", "Corn_(maize)___Common_rust_",
    "Corn_(maize)___Northern_Leaf_Blight", "Corn_(maize)___healthy", "Grape___Black_rot",
    "Grape___Esca_(Black_Measles)", "Grape___Leaf_blight_(Isariopsis_Leaf_Spot)", "Grape___healthy",
    "Orange___Haunglongbing_(Citrus_greening)", "Peach___Bacterial_spot", "Peach___healthy",
    "Pepper,_bell___Bacterial_spot", "Pepper,_bell___healthy", "Potato___Early_blight",
    "Potato___Late_blight", "Potato___healthy", "Raspberry___healthy", "Soybean___healthy",
    "Squash___Powdery_mildew", "Strawberry___Leaf_scorch", "Strawberry___healthy",
    "Tomato___Bacterial_spot", "Tomato___Early_blight", "Tomato___Late_blight",
    "Tomato___Leaf_Mold", "Tomato___Septoria_leaf_spot", "Tomato___Spider_mites Two-spotted_spider_mite",
    "Tomato___Target_Spot", "Tomato___Tomato_Yellow_Leaf_Curl_Virus", "Tomato___Tomato_mosaic_virus",
    "Tomato___healthy",
]

CROP_ALIASES = {
    "maize": "corn",
    "bell pepper": "pepper",
    "bell_pepper": "pepper",
    "capsicum": "pepper",
}


def predict_disease(img_path: str, crop: Optional[str] = "all") -> Tuple[str, float]:
    img = Image.open(img_path).convert("RGB")
    w, h = img.size

    # Central 85% crop removes outer field/soil clutter (matches validation)
    cw, ch = int(w * 0.85), int(h * 0.85)
    left, top = (w - cw) // 2, (h - ch) // 2
    img = img.crop((left, top, left + cw, top + ch)).resize((224, 224))

    arr = np.expand_dims(np.asarray(img, dtype=np.float32) / 255.0, axis=0)
    probs = _MODEL.predict(arr, verbose=0)[0]

    # Condition probabilities on the selected crop when provided
    crop_key = CROP_ALIASES.get((crop or "all").strip().lower(), (crop or "all").strip().lower())
    if crop_key and crop_key != "all":
        mask = np.array(
            [1.0 if crop_key in cls.split("___")[0].lower() else 0.0 for cls in PV_CLASSES],
            dtype=np.float32,
        )
        if mask.sum() > 0:
            masked_probs = probs * mask
            if masked_probs.sum() > 0:
                probs = masked_probs / masked_probs.sum()

    best_idx = int(np.argmax(probs))
    label = PV_CLASSES[best_idx].replace("___", " : ")
    return label, float(probs[best_idx])