import os
import xml.etree.ElementTree as ET

BASE = "data/raw/ip102/VOC2007"
ANNOTATIONS = os.path.join(BASE, "Annotations")

invalid_boxes = []
total_boxes = 0

for filename in os.listdir(ANNOTATIONS):
    if not filename.endswith(".xml"):
        continue

    try:
        root = ET.parse(
            os.path.join(ANNOTATIONS, filename)
        ).getroot()

        size = root.find("size")
        width = int(size.findtext("width"))
        height = int(size.findtext("height"))

        for obj in root.findall("object"):
            box = obj.find("bndbox")

            xmin = float(box.findtext("xmin"))
            ymin = float(box.findtext("ymin"))
            xmax = float(box.findtext("xmax"))
            ymax = float(box.findtext("ymax"))

            total_boxes += 1

            if (
                xmin < 0 or ymin < 0
                or xmax > width or ymax > height
                or xmin >= xmax or ymin >= ymax
            ):
                invalid_boxes.append(
                    (filename, xmin, ymin, xmax, ymax)
                )

    except Exception:
        continue

print("Total bounding boxes:", total_boxes)
print("Invalid bounding boxes:", len(invalid_boxes))

print("\nExamples:")
for box in invalid_boxes[:10]:
    print(box)