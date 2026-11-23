import json
from pathlib import Path
from ultralytics import YOLO

weights = Path(r"C:\Users\sriva\PycharmProjects\PlantCareAI\models\pest\ip102_yolov26n_baseline\weights\yolo26n.pt")
model = YOLO(str(weights))

print("Task:", model.task)
print("Num classes:", len(model.names))
print("Sample classes:", list(model.names.items())[:5])

out = {
    "checkpoint": weights.name,
    "num_classes": len(model.names),
    "classes": {str(k): v for k, v in sorted(model.names.items())},
}
weights.with_name("pest_classes.json").write_text(json.dumps(out, indent=2, ensure_ascii=False), encoding="utf-8")
print("Saved:", weights.with_name("pest_classes.json"))