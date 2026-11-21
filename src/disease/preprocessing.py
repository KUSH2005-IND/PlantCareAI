import numpy as np
from PIL import Image


def preprocess_image(img_path):
    """
    Load an image and prepare it for the disease classification model.
    Input: image path
    Output: NumPy array of shape (1, 224, 224, 3)
    """

    img = Image.open(img_path).convert("RGB")
    img = img.resize((224, 224))

    img_array = np.array(img, dtype=np.float32) / 255.0
    img_array = np.expand_dims(img_array, axis=0)

    return img_array