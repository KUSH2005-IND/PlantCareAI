import json
import zipfile
from pathlib import Path
from ultralytics import YOLO
from ai_edge_litert.interpreter import Interpreter

WEIGHTS_PATH = "yolo26n.pt"
CALIB_DIR = Path("calib_images")  # Optional: put 30-50 sample pest .jpgs here for INT8
IMGSZ = 640

model = YOLO(WEIGHTS_PATH)
names = {int(k): v for k, v in sorted(model.names.items())}

if CALIB_DIR.exists() and any(CALIB_DIR.glob("*.jpg")):
    auto_yaml = Path("auto_calib.yaml")
    names_block = "\n".join([f"  {k}: {v}" for k, v in names.items()])
    auto_yaml.write_text(
        f"path: {CALIB_DIR.resolve().as_posix()}\ntrain: .\nval: .\nnames:\n{names_block}\n",
        encoding="utf-8",
    )
    print("Exporting INT8 LiteRT model...")
    exported = model.export(format="litert", quantize="int8", imgsz=IMGSZ, data=str(auto_yaml))
else:
    print("No calib_images/ folder found -> Exporting FP16 LiteRT model (~5 MB)...")
    exported = model.export(format="litert", half=True, imgsz=IMGSZ)

tflite_path = Path(exported)
print(f"Exported: {tflite_path} ({tflite_path.stat().st_size / 1e6:.2f} MB)")

# Inspect actual exported LiteRT tensor signatures
interp = Interpreter(model_path=str(tflite_path))
interp.allocate_tensors()
in_det = interp.get_input_details()[0]
out_det = interp.get_output_details()[0]

if zipfile.is_zipfile(tflite_path):
    with zipfile.ZipFile(tflite_path, "r") as zf:
        if "metadata.json" in zf.namelist():
            meta = json.loads(zf.read("metadata.json").decode("utf-8"))
            if "names" in meta:
                names = {int(k): v for k, v in sorted(meta["names"].items(), key=lambda x: int(x[0]))}

manifest = {
    "model_file": tflite_path.name,
    "input_shape": in_det["shape"].tolist(),
    "input_dtype": in_det["dtype"].__name__,
    "input_quantization": {"scale": float(in_det["quantization"][0]), "zero_point": int(in_det["quantization"][1])},
    "output_shape": out_det["shape"].tolist(),
    "output_dtype": out_det["dtype"].__name__,
    "output_quantization": {"scale": float(out_det["quantization"][0]), "zero_point": int(out_det["quantization"][1])},
    "num_classes": len(names),
    "classes": {str(k): v for k, v in names.items()},
}

Path("pest_model_signature.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")
print("Output shape:", manifest["output_shape"], "| dtype:", manifest["output_dtype"])
print("Saved pest_model_signature.json")