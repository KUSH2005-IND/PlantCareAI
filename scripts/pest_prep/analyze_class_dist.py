
from pathlib import Path
from collections import Counter, defaultdict
import csv

ROOT = Path("data/processed/ip102/yolo")
CLASSES_FILE = Path("data/raw/ip102/classes.txt")
OUTPUT_DIR = Path("data/processed/ip102")

# Read class names
class_names = []

with open(CLASSES_FILE, "r", encoding="utf-8") as file:
    for line in file:
        parts = line.strip().split(maxsplit=1)

        if len(parts) == 2:
            class_names.append(parts[1].strip())

assert len(class_names) == 102, "Expected 102 classes"

# Store statistics
stats = {}

for split in ["train", "val", "test"]:

    box_counts = Counter()
    image_counts = Counter()

    label_dir = ROOT / "labels" / split

    for label_file in label_dir.glob("*.txt"):

        classes_in_image = set()

        with open(label_file, "r", encoding="utf-8") as file:
            for line in file:

                parts = line.strip().split()

                if len(parts) != 5:
                    continue

                class_id = int(parts[0])

                box_counts[class_id] += 1
                classes_in_image.add(class_id)

        for class_id in classes_in_image:
            image_counts[class_id] += 1

    stats[split] = {
        "boxes": box_counts,
        "images": image_counts
    }

# Print results
print("=" * 75)
print("IP102 CLASS DISTRIBUTION")
print("=" * 75)

for split in ["train", "val", "test"]:

    print(f"\n{split.upper()}")

    print(f"Total bounding boxes: {sum(stats[split]['boxes'].values())}")
    print(f"Represented classes: {len(stats[split]['boxes'])}")

# Combined distribution
combined_boxes = Counter()
combined_images = Counter()

for split in ["train", "val", "test"]:
    combined_boxes.update(stats[split]["boxes"])
    combined_images.update(stats[split]["images"])

print("\n" + "=" * 75)
print("COMBINED CLASS DISTRIBUTION")
print("=" * 75)

print(f"Total classes: 102")
print(f"Represented classes: {len(combined_boxes)}")
print(f"Missing classes: {102 - len(combined_boxes)}")

print("\nCLASS-WISE COUNTS")
print("-" * 75)

for class_id in range(102):

    name = class_names[class_id]
    boxes = combined_boxes[class_id]
    images = combined_images[class_id]

    print(
        f"Class {class_id:3d} | "
        f"{name:35.35s} | "
        f"Boxes: {boxes:5d} | "
        f"Images: {images:5d}"
    )

# Save CSV report
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
csv_path = OUTPUT_DIR / "class_distribution.csv"

with open(csv_path, "w", newline="", encoding="utf-8") as file:

    writer = csv.writer(file)

    writer.writerow([
        "class_id",
        "class_name",
        "train_boxes",
        "val_boxes",
        "test_boxes",
        "total_boxes",
        "total_images"
    ])

    for class_id in range(102):

        writer.writerow([
            class_id,
            class_names[class_id],
            stats["train"]["boxes"][class_id],
            stats["val"]["boxes"][class_id],
            stats["test"]["boxes"][class_id],
            combined_boxes[class_id],
            combined_images[class_id]
        ])

print("\nCSV report saved to:", csv_path.resolve())
print("\nAnalysis completed!")
