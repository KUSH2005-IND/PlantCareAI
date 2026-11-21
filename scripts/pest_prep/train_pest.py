from pathlib import Path
from ultralytics import YOLO
import torch

# Project paths
PROJECT_DIR = Path(__file__).resolve().parent

DATA_YAML = (
    PROJECT_DIR
    / "data"
    / "processed"
    / "ip102"
    / "yolo"
    / "data.yaml"
)

RUN_DIR = PROJECT_DIR / "runs" / "pest"

def main():

    print("=" * 60)
    print("PLANTCAREAI - FULL IP102 PEST DETECTION TRAINING")
    print("=" * 60)

    print("PyTorch:", torch.__version__)
    print("CUDA available:", torch.cuda.is_available())
    print("Dataset:", DATA_YAML)

    if not DATA_YAML.exists():
        raise FileNotFoundError(
            f"Dataset YAML not found: {DATA_YAML}"
        )

    # Load pretrained YOLOv8n
    model = YOLO("yolov8n.pt")

    print("\nStarting full dataset training...")

    results = model.train(

        # Dataset
        data=str(DATA_YAML),

        # Training
        epochs=10,
        imgsz=416,
        batch=4,
        device="cpu",

        # CPU configuration
        workers=2,
        cache=False,
        amp=False,

        # Training controls
        patience=5,
        optimizer="auto",
        seed=42,

        # Augmentation / schedule
        close_mosaic=3,

        # Output
        project=str(RUN_DIR),
        name="ip102_yolov8n_baseline",
        exist_ok=True,

        # Save checkpoints
        save=True,
        save_period=5,

        # Validation
        val=True,

        # Avoid unnecessary plots during training
        plots=False,
        verbose=True
    )

    print("\n" + "=" * 60)
    print("TRAINING COMPLETED")
    print("=" * 60)

    print("Results saved in:", RUN_DIR / "ip102_yolov8n_baseline")

if __name__ == "__main__":
    main()
