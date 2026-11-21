import xml.etree.ElementTree as ET
from pathlib import Path
from collections import Counter

annotation_dir = Path(
    "data/raw/ip102/VOC2007/Annotations"
)

class_counts = Counter()
invalid_ids = []

for xml_file in annotation_dir.glob("*.xml"):

    try:
        root = ET.parse(xml_file).getroot()

        for obj in root.findall("object"):

            name = obj.findtext("name", "").strip()

            if name.isdigit():
                class_counts[int(name)] += 1

                if not 0 <= int(name) <= 101:
                    invalid_ids.append(
                        (xml_file.name, name)
                    )

            else:
                class_counts[name] += 1

    except ET.ParseError:
        continue

print("Unique class labels:", len(class_counts))
print("Minimum numeric ID:",
      min((x for x in class_counts if isinstance(x, int)),
          default=None))
print("Maximum numeric ID:",
      max((x for x in class_counts if isinstance(x, int)),
          default=None))

print("\nInvalid IDs:", invalid_ids[:20])
print("Total invalid IDs:", len(invalid_ids))

print("\nMost frequent classes:")
for name, count in class_counts.most_common(15):
    print(name, count)