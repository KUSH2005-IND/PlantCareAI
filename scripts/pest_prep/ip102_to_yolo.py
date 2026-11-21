
import random
import re
import shutil
import json
import xml.etree.ElementTree as ET

from pathlib import Path
from collections import Counter


# --------------------------------------------------
# CONFIGURATION
# --------------------------------------------------

PROJECT_ROOT = Path(__file__).resolve().parent

VOC_ROOT = PROJECT_ROOT / "data/raw/ip102/VOC2007"
CLASSES_FILE = PROJECT_ROOT / "data/raw/ip102/classes.txt"

OUTPUT_ROOT = PROJECT_ROOT / "data/processed/ip102/yolo"

TRAIN_RATIO = 0.90
RANDOM_SEED = 42

# --------------------------------------------------
# DIRECTORY SETUP
# --------------------------------------------------

for split in ["train", "val", "test"]:
    (OUTPUT_ROOT / "images" / split).mkdir(
        parents=True, exist_ok=True
    )
    (OUTPUT_ROOT / "labels" / split).mkdir(
        parents=True, exist_ok=True
    )


# --------------------------------------------------
# LOAD CLASS NAMES
# --------------------------------------------------

def normalize_name(name):
    return " ".join(name.strip().lower().split())


classes = {}

with open(CLASSES_FILE, "r", encoding="utf-8") as file:
    for line in file:
        match = re.match(r"^\s*(\d+)\s+(.+?)\s*$", line)

        if match:
            class_id = int(match.group(1)) - 1
            class_name = normalize_name(match.group(2))
            classes[class_name] = class_id

NUM_CLASSES = len(classes)

assert NUM_CLASSES == 102, (
    f"Expected 102 classes, found {NUM_CLASSES}"
)

print("Loaded classes:", NUM_CLASSES)


# --------------------------------------------------
# READ OFFICIAL SPLITS
# --------------------------------------------------

def read_split(filename):
    path = VOC_ROOT / "ImageSets/Main" / filename

    with open(path, "r", encoding="utf-8") as file:
        return [
            line.strip().split()[0]
            for line in file
            if line.strip()
        ]


trainval_ids = read_split("trainval.txt")
test_ids = read_split("test.txt")

assert len(trainval_ids) == len(set(trainval_ids))
assert len(test_ids) == len(set(test_ids))

assert not set(trainval_ids).intersection(test_ids), (
    "Trainval and test splits overlap!"
)

# Deterministic train/validation split
random.seed(RANDOM_SEED)
random.shuffle(trainval_ids)

split_index = int(len(trainval_ids) * TRAIN_RATIO)

train_ids = trainval_ids[:split_index]
val_ids = trainval_ids[split_index:]

print("\nOriginal split counts:")
print("Trainval:", len(trainval_ids))
print("Test:", len(test_ids))

print("\nNew split counts:")
print("Train:", len(train_ids))
print("Validation:", len(val_ids))
print("Test:", len(test_ids))


# --------------------------------------------------
# IMAGE LOOKUP
# --------------------------------------------------

def find_image(image_id):

    for extension in [".jpg", ".jpeg", ".JPG", ".JPEG"]:
        path = VOC_ROOT / "JPEGImages" / (
            image_id + extension
        )

        if path.exists():
            return path

    return None


# --------------------------------------------------
# XML TO YOLO CONVERSION
# --------------------------------------------------

def convert_annotation(image_id):

    xml_path = VOC_ROOT / "Annotations" / (
        image_id + ".xml"
    )

    if not xml_path.exists():
        return None, "missing_xml"

    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()

    except ET.ParseError:
        return None, "malformed_xml"

    size = root.find("size")

    if size is None:
        return None, "missing_size"

    width = int(size.findtext("width", "0"))
    height = int(size.findtext("height", "0"))

    if width <= 0 or height <= 0:
        return None, "invalid_image_size"

    objects = root.findall("object")

    labels = []

    for obj in objects:

        raw_name = obj.findtext("name", "").strip()

        if raw_name.isdigit():
            class_id = int(raw_name)

            if not 0 <= class_id < NUM_CLASSES:
                raise ValueError(
                    f"Invalid class ID '{raw_name}' "
                    f"in {xml_path.name}"
                )
        else:
            name = normalize_name(raw_name)

            if name not in classes:
                raise ValueError(
                    f"Unknown class '{name}' in {xml_path.name}"
                )

            class_id = classes[name]

        box = obj.find("bndbox")

        if box is None:
            continue

        try:
            xmin = float(box.findtext("xmin"))
            ymin = float(box.findtext("ymin"))
            xmax = float(box.findtext("xmax"))
            ymax = float(box.findtext("ymax"))

        except (TypeError, ValueError):
            continue

        # Reject invalid or out-of-image coordinates
        if not (
            0 <= xmin < xmax <= width
            and 0 <= ymin < ymax <= height
        ):
            continue

        # Pascal VOC to YOLO normalization
        x_center = ((xmin + xmax) / 2) / width
        y_center = ((ymin + ymax) / 2) / height

        box_width = (xmax - xmin) / width
        box_height = (ymax - ymin) / height

        labels.append(
            f"{class_id} "
            f"{x_center:.6f} "
            f"{y_center:.6f} "
            f"{box_width:.6f} "
            f"{box_height:.6f}"
        )

    # Do not treat an image with entirely invalid
    # annotations as a genuine background image.
    if objects and not labels:
        return None, "no_valid_objects"

    return labels, "success"


# --------------------------------------------------
# PROCESS EACH SPLIT
# --------------------------------------------------

stats = {
    split: Counter()
    for split in ["train", "val", "test"]
}

invalid_files = []

def process_split(image_ids, split):

    for index, image_id in enumerate(image_ids, start=1):

        image_path = find_image(image_id)

        if image_path is None:
            stats[split]["missing_image"] += 1
            continue

        labels, status = convert_annotation(image_id)

        if status != "success":
            stats[split][status] += 1
            invalid_files.append(
                {"image": image_id, "reason": status,
                 "split": split}
            )
            continue

        image_destination = (
            OUTPUT_ROOT / "images" / split /
            f"{image_id}{image_path.suffix.lower()}"
        )

        label_destination = (
            OUTPUT_ROOT / "labels" / split /
            f"{image_id}.txt"
        )

        shutil.copy2(image_path, image_destination)

        label_destination.write_text(
            "\n".join(labels),
            encoding="utf-8"
        )

        stats[split]["images_copied"] += 1
        stats[split]["valid_objects"] += len(labels)

        if index % 1000 == 0:
            print(
                f"{split}: processed "
                f"{index}/{len(image_ids)}"
            )


process_split(train_ids, "train")
process_split(val_ids, "val")
process_split(test_ids, "test")


# --------------------------------------------------
# GENERATE YOLO DATASET YAML
# --------------------------------------------------

sorted_classes = [
    name
    for name, class_id in sorted(
        classes.items(), key=lambda item: item[1]
    )
]

yaml_content = [
    "path: .",
    "train: images/train",
    "val: images/val",
    "test: images/test",
    "",
    "names:"
]

for class_id, name in enumerate(sorted_classes):
    safe_name = json.dumps(name, ensure_ascii=False)
    yaml_content.append(f"  {class_id}: {safe_name}")

(OUTPUT_ROOT / "data.yaml").write_text(
    "\n".join(yaml_content),
    encoding="utf-8"
)


# --------------------------------------------------
# SAVE CONVERSION REPORT
# --------------------------------------------------

report = {
    "classes": NUM_CLASSES,
    "seed": RANDOM_SEED,
    "train_ratio": TRAIN_RATIO,
    "statistics": {
        split: dict(values)
        for split, values in stats.items()
    },
    "skipped_files": invalid_files
}

(OUTPUT_ROOT / "conversion_report.json").write_text(
    json.dumps(report, indent=4),
    encoding="utf-8"
)


# --------------------------------------------------
# FINAL SUMMARY
# --------------------------------------------------

print("\n" + "=" * 50)
print("DATASET CONVERSION COMPLETED")
print("=" * 50)

for split, values in stats.items():

    print(f"\n{split.upper()}")

    for key, value in values.items():
        print(f"{key}: {value}")

print("\nSkipped files:", len(invalid_files))

print("\nYOLO dataset location:")
print(OUTPUT_ROOT)

print("\nDataset YAML:")
print(OUTPUT_ROOT / "data.yaml")
