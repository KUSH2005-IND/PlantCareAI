import os
import xml.etree.ElementTree as ET

BASE_DIR = "data/raw/ip102/VOC2007"

ANNOTATIONS_DIR = os.path.join(BASE_DIR, "Annotations")
IMAGES_DIR = os.path.join(BASE_DIR, "JPEGImages")

xml_files = [
    f for f in os.listdir(ANNOTATIONS_DIR)
    if f.endswith(".xml")
]

missing_images = []
invalid_xml = []
dimension_errors = []

for xml_file in xml_files:
    xml_path = os.path.join(ANNOTATIONS_DIR, xml_file)

    try:
        root = ET.parse(xml_path).getroot()

        filename = root.findtext("filename")

        if not filename:
            filename = os.path.splitext(xml_file)[0]

        # Handle XML filenames with or without extensions
        filename = os.path.basename(filename)

        if not os.path.splitext(filename)[1]:
            filename += ".jpg"

        image_path = os.path.join(IMAGES_DIR, filename)

        if not os.path.exists(image_path):
            missing_images.append(filename)
            continue

        size = root.find("size")
        width = int(size.findtext("width"))
        height = int(size.findtext("height"))

        if width <= 0 or height <= 0:
            dimension_errors.append(xml_file)

    except Exception:
        invalid_xml.append(xml_file)

print("Total XML files:", len(xml_files))
print("Total images:", len(os.listdir(IMAGES_DIR)))
print("Missing images:", len(missing_images))
print("Invalid XML:", len(invalid_xml))
print("Invalid dimensions:", len(dimension_errors))

print("\nSample missing images:", missing_images[:10])
print("Invalid XML files:", invalid_xml)