import os

BASE = "data/raw/ip102/VOC2007"

IMAGE_DIR = os.path.join(BASE, "JPEGImages")
ANNOTATION_DIR = os.path.join(BASE, "Annotations")
SPLIT_DIR = os.path.join(BASE, "ImageSets", "Main")


def check_split(filename):
    with open(os.path.join(SPLIT_DIR, filename)) as f:
        ids = [line.strip() for line in f if line.strip()]

    missing_images = []
    missing_annotations = []

    for image_id in ids:
        if not os.path.exists(
            os.path.join(IMAGE_DIR, image_id + ".jpg")
        ):
            missing_images.append(image_id)

        if not os.path.exists(
            os.path.join(ANNOTATION_DIR, image_id + ".xml")
        ):
            missing_annotations.append(image_id)

    print(f"\n{filename}")
    print("Total IDs:", len(ids))
    print("Missing images:", len(missing_images))
    print("Missing annotations:", len(missing_annotations))


check_split("trainval.txt")
check_split("test.txt")