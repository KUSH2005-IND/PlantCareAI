import json
import os
from typing import Dict, List, Optional, Tuple

import cv2
import numpy as np
import tensorflow as tf

from src.pest.crop_pest_map import get_allowed_class_ids, get_crop_for_class_id


BASE_DIR = os.path.dirname(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
)

PEST_MODEL_DIR = os.path.join(
    BASE_DIR, "models", "pest", "ip102_yolov26n_baseline"
)
MODEL_PATH = os.path.join(
    PEST_MODEL_DIR, "yolo26n_ip102_640_raw_w8a32.tflite"
)
LABELS_PATH = os.path.join(PEST_MODEL_DIR, "ip102_labels.json")
OUTPUT_DIR = os.path.join(BASE_DIR, "static", "outputs")
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Load LiteRT interpreter and label metadata once at import
interpreter = tf.lite.Interpreter(model_path=MODEL_PATH)
interpreter.allocate_tensors()
input_details = interpreter.get_input_details()[0]
output_details = interpreter.get_output_details()[0]

with open(LABELS_PATH, "r", encoding="utf-8") as f:
    label_meta = json.load(f)

CLASS_NAMES: Dict[int, str] = {
    int(k): v for k, v in label_meta["names"].items()
}
INPUT_SIZE: int = int(label_meta.get("input_size", 640))


def _letterbox(image_bgr: np.ndarray) -> Tuple[np.ndarray, float, Tuple[int, int]]:
    h, w = image_bgr.shape[:2]
    scale = min(INPUT_SIZE / w, INPUT_SIZE / h)
    new_w, new_h = int(round(w * scale)), int(round(h * scale))

    resized = cv2.resize(image_bgr, (new_w, new_h), interpolation=cv2.INTER_LINEAR)
    pad_w = (INPUT_SIZE - new_w) // 2
    pad_h = (INPUT_SIZE - new_h) // 2

    canvas = np.full((INPUT_SIZE, INPUT_SIZE, 3), 114, dtype=np.uint8)
    canvas[pad_h : pad_h + new_h, pad_w : pad_w + new_w] = resized

    rgb = cv2.cvtColor(canvas, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
    # Ultralytics LiteRT export expects planar NCHW: (1, 3, 640, 640)
    nchw = np.transpose(rgb, (2, 0, 1))[np.newaxis, ...]
    return np.ascontiguousarray(nchw), scale, (pad_w, pad_h)


def _draw_detections(
    image_bgr: np.ndarray, detections: List[Dict], src_path: str
) -> str:
    annotated = image_bgr.copy()
    for det in detections:
        x1, y1, x2, y2 = det["bbox"]
        tag = f"{det['label']} ({det['confidence'] * 100:.1f}%)"

        cv2.rectangle(annotated, (x1, y1), (x2, y2), (34, 197, 94), 2)
        (tw, th), _ = cv2.getTextSize(tag, cv2.FONT_HERSHEY_SIMPLEX, 0.55, 2)
        top = max(y1 - th - 8, 0)
        cv2.rectangle(annotated, (x1, top), (x1 + tw + 8, top + th + 8), (34, 197, 94), -1)
        cv2.putText(
            annotated,
            tag,
            (x1 + 4, top + th + 4),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.55,
            (0, 0, 0),
            2,
            cv2.LINE_AA,
        )

    stem, ext = os.path.splitext(os.path.basename(src_path))
    out_filename = f"pest_{stem}{ext or '.jpg'}"
    out_abs_path = os.path.join(OUTPUT_DIR, out_filename)
    cv2.imwrite(out_abs_path, annotated)
    return os.path.join("static", "outputs", out_filename).replace("\\", "/")


def predict_pest(
    img_path: str,
    crop: Optional[str] = "all",
    conf_threshold: float = 0.25,
    iou_threshold: float = 0.45,
) -> Tuple[str, float, str, List[Dict]]:
    image_bgr = cv2.imread(img_path)
    if image_bgr is None:
        raise ValueError(f"Could not read image: {img_path}")

    orig_h, orig_w = image_bgr.shape[:2]
    input_tensor, scale, (pad_w, pad_h) = _letterbox(image_bgr)

    interpreter.set_tensor(input_details["index"], input_tensor)
    interpreter.invoke()

    # Raw output shape: (1, 106, 8400) -> transpose to (8400, 106)
    raw_out = interpreter.get_tensor(output_details["index"])[0]
    preds = raw_out.T

    boxes_cxcywh = preds[:, :4].copy()
    class_scores = preds[:, 4:].copy()

    allowed_ids = get_allowed_class_ids(crop)
    if allowed_ids is not None:
        mask = np.zeros(class_scores.shape[1], dtype=bool)
        mask[allowed_ids] = True
        class_scores[:, ~mask] = 0.0

    class_ids = np.argmax(class_scores, axis=1)
    confidences = class_scores[np.arange(len(class_scores)), class_ids]

    keep = confidences >= conf_threshold
    if not np.any(keep):
        rel_path = img_path.replace("\\", "/")
        return "No pest detected", 0.0, rel_path, []

    boxes_cxcywh = boxes_cxcywh[keep]
    class_ids = class_ids[keep]
    confidences = confidences[keep]

    if boxes_cxcywh.max() <= 1.5:
        boxes_cxcywh *= INPUT_SIZE

    x1 = boxes_cxcywh[:, 0] - boxes_cxcywh[:, 2] / 2.0
    y1 = boxes_cxcywh[:, 1] - boxes_cxcywh[:, 3] / 2.0
    w = boxes_cxcywh[:, 2]
    h = boxes_cxcywh[:, 3]
    nms_boxes = np.stack([x1, y1, w, h], axis=1).tolist()

    indices = cv2.dnn.NMSBoxes(
        nms_boxes, confidences.tolist(), conf_threshold, iou_threshold
    )
    if len(indices) == 0:
        rel_path = img_path.replace("\\", "/")
        return "No pest detected", 0.0, rel_path, []

    detections: List[Dict] = []
    for idx in np.array(indices).flatten():
        bx = (x1[idx] - pad_w) / scale
        by = (y1[idx] - pad_h) / scale
        bw = w[idx] / scale
        bh = h[idx] / scale

        xmin = int(np.clip(round(bx), 0, orig_w))
        ymin = int(np.clip(round(by), 0, orig_h))
        xmax = int(np.clip(round(bx + bw), 0, orig_w))
        ymax = int(np.clip(round(by + bh), 0, orig_h))
        cid = int(class_ids[idx])

        detections.append(
            {
                "class_id": cid,
                "label": CLASS_NAMES.get(cid, f"class_{cid}"),
                "host_crop": get_crop_for_class_id(cid),
                "confidence": round(float(confidences[idx]), 4),
                "bbox": [xmin, ymin, xmax, ymax],
            }
        )

    detections.sort(key=lambda d: d["confidence"], reverse=True)
    top = detections[0]
    annotated_rel_path = _draw_detections(image_bgr, detections, img_path)

    label_display = f"{top['host_crop']} : {top['label'].title()}"
    if len(detections) > 1:
        label_display += f" (+{len(detections) - 1} more)"

    return label_display, top["confidence"], annotated_rel_path, detections