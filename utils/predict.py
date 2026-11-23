from typing import Dict, List, Optional, Tuple

from src.disease.classifier import predict_disease
from src.pest.detector import predict_pest


def predict_image(
    img_path: str,
    mode: str = "disease",
    crop: Optional[str] = "all",
) -> Tuple[str, float, str, List[Dict]]:
    """
    Unified prediction router for PlantCareAI.
    Returns: (prediction_label, confidence, display_image_path, detections)
    """
    if mode == "pest":
        return predict_pest(img_path, crop=crop)

    disease, confidence = predict_disease(img_path, crop=crop)
    return disease, confidence, img_path.replace("\\", "/"), []