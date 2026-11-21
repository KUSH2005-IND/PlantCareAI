from typing import Dict, List, Optional

# Exact IP102 102-class taxonomy ranges verified against ip102_labels.json
CROP_TO_CLASS_IDS: Dict[str, List[int]] = {
    "rice": list(range(0, 14)),       # 0-13   (14 classes)
    "corn": list(range(14, 27)),      # 14-26  (13 classes)
    "wheat": list(range(27, 36)),     # 27-35  (9 classes)
    "beet": list(range(36, 44)),      # 36-43  (8 classes)
    "alfalfa": list(range(44, 57)),   # 44-56  (13 classes)
    "vitis": list(range(57, 69)),     # 57-68  (12 classes - Grape)
    "citrus": list(range(69, 87)),    # 69-86  (18 classes)
    "mango": list(range(87, 102)),    # 87-101 (15 classes)
}

SUPPORTED_CROPS: List[str] = ["all"] + list(CROP_TO_CLASS_IDS.keys())


def get_allowed_class_ids(crop: Optional[str]) -> Optional[List[int]]:
    if not crop or crop.strip().lower() in ("all", "any", "none"):
        return None
    return CROP_TO_CLASS_IDS.get(crop.strip().lower())


def get_crop_for_class_id(class_id: int) -> str:
    for crop_name, ids in CROP_TO_CLASS_IDS.items():
        if class_id in ids:
            return crop_name.capitalize()
    return "Unknown"