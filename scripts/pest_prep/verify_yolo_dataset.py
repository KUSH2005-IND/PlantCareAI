
from pathlib import Path
from collections import Counter

ROOT = Path("data/processed/ip102/yolo")

splits = ["train", "val", "test"]

all_image_ids = {}
all_label_ids = {}

total_images = 0
total_boxes = 0
errors = []
class_counts = Counter()

# --------------------------------------------------
# VERIFY EACH SPLIT
# --------------------------------------------------

for split in splits:

    image_dir = ROOT / "images" / split
    label_dir = ROOT / "labels" / split

    images = {
        p.stem for p in image_dir.iterdir()
        if p.suffix.lower() in [".jpg", ".jpeg"]
    }

    labels = {
        p.stem for p in label_dir.glob("*.txt")
    }

    all_image_ids[split] = images
    all_label_ids[split] = labels

    missing_labels = images - labels
    missing_images = labels - images

    print(f"\n{'=' * 45}")
    print(split.upper())

    print("Images:", len(images))
    print("Labels:", len(labels))
    print("Images without labels:", len(missing_labels))
    print("Labels without images:", len(missing_images))

    if missing_labels:
        errors.append(
            f"{split}: images without labels"
        )

    if missing_images:
        errors.append(
            f"{split}: labels without images"
        )

    total_images += len(images)

    # ----------------------------------------------
    # VERIFY YOLO LABEL FORMAT
    # ----------------------------------------------

    split_boxes = 0

    for image_id in images:

        label_path = label_dir / f"{image_id}.txt"

        if not label_path.exists():
            continue

        with open(label_path, "r") as file:

            for line_number, line in enumerate(file, 1):

                parts = line.strip().split()

                if not parts:
                    continue

                if len(parts) != 5:
                    errors.append(
                        f"{label_path}: invalid column count"
                    )
                    continue

                try:
                    class_id = int(parts[0])

                    x, y, w, h = map(
                        float, parts[1:]
                    )

                except ValueError:
                    errors.append(
                        f"{label_path}: non-numeric value"
                    )
                    continue

                if not 0 <= class_id <= 101:
                    errors.append(
                        f"{label_path}: invalid class ID"
                    )
                    continue

                if not all(
                    0 <= value <= 1
                    for value in [x, y, w, h]
                ):
                    errors.append(
                        f"{label_path}: coordinate outside range"
                    )
                    continue

                if w <= 0 or h <= 0:
                    errors.append(
                        f"{label_path}: zero/negative box size"
                    )
                    continue

                split_boxes += 1
                class_counts[class_id] += 1

    total_boxes += split_boxes

    print("Valid bounding boxes:", split_boxes)

# --------------------------------------------------
# CHECK SPLIT OVERLAP
# --------------------------------------------------

print("\n" + "=" * 45)
print("SPLIT OVERLAP CHECK")

for i in range(len(splits)):
    for j in range(i + 1, len(splits)):

        a = splits[i]
        b = splits[j]

        overlap = (
            all_image_ids[a] &
            all_image_ids[b]
        )

        print(f"{a} vs {b}: {len(overlap)} overlapping images")

        if overlap:
            errors.append(
                f"Overlap between {a} and {b}"
            )

# --------------------------------------------------
# CLASS DISTRIBUTION
# --------------------------------------------------

print("\n" + "=" * 45)
print("CLASS DISTRIBUTION")

print("Classes with objects:", len(class_counts))
print("Classes without objects:",
      102 - len(class_counts))

print("\nTop 10 classes:")

for class_id, count in class_counts.most_common(10):
    print(f"Class {class_id}: {count} objects")

# --------------------------------------------------
# FINAL REPORT
# --------------------------------------------------

print("\n" + "=" * 45)
print("FINAL DATASET VALIDATION")

print("Total images:", total_images)
print("Total bounding boxes:", total_boxes)
print("Total classes represented:", len(class_counts))
print("Total validation errors:", len(errors))

if errors:
    print("\nERRORS:")
    for error in errors[:30]:
        print("-", error)
else:
    print("\nALL CHECKS PASSED!")
