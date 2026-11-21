import os
import xml.etree.ElementTree as ET
from collections import Counter
import numpy as np

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
IP102_DIR = os.path.join(BASE_DIR, "data", "raw", "ip102")
CLASSES_FILE = os.path.join(IP102_DIR, "classes.txt")
ANNOTATIONS_DIR = os.path.join(IP102_DIR, "VOC2007", "Annotations")

# 1. Load class definitions (1-indexed in classes.txt, 0-indexed in XML annotations)
classes_map = {}
with open(CLASSES_FILE, "r", encoding="utf-8") as f:
    for line in f:
        parts = line.strip().split(maxsplit=1)
        if len(parts) == 2:
            classes_map[int(parts[0]) - 1] = parts[1].strip()

# 2. Count bounding boxes from VOC XML annotations
counts = Counter()
total_xmls = 0

for filename in os.listdir(ANNOTATIONS_DIR):
    if not filename.endswith(".xml"):
        continue
    total_xmls += 1
    xml_path = os.path.join(ANNOTATIONS_DIR, filename)
    try:
        root = ET.parse(xml_path).getroot()
        for obj in root.findall("object"):
            name = obj.findtext("name")
            if name is not None and name.strip() != "":
                counts[int(name.strip())] += 1
    except Exception as e:
        pass

all_counts = [counts[i] for i in range(102)]
non_zero = [c for c in all_counts if c > 0]
total_boxes = sum(all_counts)

print("=" * 65)
print(" IP102 BOUNDING BOX DISTRIBUTION & IMBALANCE REPORT")
print("=" * 65)
print(f"Total XML Annotation Files:       {total_xmls:,}")
print(f"Total Bounding Boxes:             {total_boxes:,}")
print(f"Total Target Classes Defined:     102")
print(f"Classes with Active Bounding Boxes: {len(non_zero)} / 102")
print(f"Classes with 0 Bounding Boxes:    {102 - len(non_zero)}")
print("-" * 65)
print(f"Mean Boxes/Class (all 102):       {np.mean(all_counts):.1f}")
print(f"Mean Boxes/Class (active 97):     {np.mean(non_zero):.1f}")
print(f"Median Boxes/Class (all 102):     {np.median(all_counts):.1f}")
print(f"Median Boxes/Class (active 97):   {np.median(non_zero):.1f}")
print(f"Standard Deviation:               {np.std(all_counts):.1f}")
print(f"Min Box Count (active):           {min(non_zero)}")
print(f"Max Box Count:                    {max(all_counts)}")
print(f"Imbalance Ratio (Max / Min active): {max(all_counts) / min(non_zero):.1f}x")
print("=" * 65)

print("\n[!] CLASSES WITH ZERO BOUNDING BOXES (5 classes):")
for i in range(102):
    if counts[i] == 0:
        cname = classes_map.get(i, "Unknown")
        print(f"  - Class ID {i:2d} (1-indexed {i+1:3d}): {cname}")

sorted_indices = sorted(range(102), key=lambda i: counts[i], reverse=True)

print("\n[+] TOP 10 MOST FREQUENT CLASSES:")
print(f"  {'ID':>3}  {'1-Idx':>5}  {'Class Name':<32}  {'Boxes':>7}  {'Share':>7}")
print("  " + "-" * 56)
for idx in sorted_indices[:10]:
    cnt = counts[idx]
    pct = (cnt / total_boxes) * 100
    cname = classes_map.get(idx, "Unknown")
    print(f"  {idx:3d}  {idx+1:5d}  {cname:<32}  {cnt:7d}  {pct:6.2f}%")

active_sorted = [idx for idx in sorted_indices if counts[idx] > 0]

print("\n[-] BOTTOM 10 LEAST FREQUENT ACTIVE CLASSES:")
print(f"  {'ID':>3}  {'1-Idx':>5}  {'Class Name':<32}  {'Boxes':>7}  {'Share':>7}")
print("  " + "-" * 56)
for idx in active_sorted[-10:]:
    cnt = counts[idx]
    pct = (cnt / total_boxes) * 100
    cname = classes_map.get(idx, "Unknown")
    print(f"  {idx:3d}  {idx+1:5d}  {cname:<32}  {cnt:7d}  {pct:6.2f}%")

print("\n[*] DISTRIBUTION BRACKETS:")
b0 = sum(1 for c in all_counts if c == 0)
b1_20 = sum(1 for c in all_counts if 1 <= c <= 20)
b21_50 = sum(1 for c in all_counts if 21 <= c <= 50)
b51_100 = sum(1 for c in all_counts if 51 <= c <= 100)
b101_300 = sum(1 for c in all_counts if 101 <= c <= 300)
b301_500 = sum(1 for c in all_counts if 301 <= c <= 500)
b501_1000 = sum(1 for c in all_counts if 501 <= c <= 1000)
b1000plus = sum(1 for c in all_counts if c > 1000)

print(f"  - 0 boxes (empty):         {b0:2d} classes")
print(f"  - 1 to 20 boxes (critical): {b1_20:2d} classes")
print(f"  - 21 to 50 boxes (scarce):  {b21_50:2d} classes")
print(f"  - 51 to 100 boxes (low):    {b51_100:2d} classes")
print(f"  - 101 to 300 boxes (medium):{b101_300:2d} classes")
print(f"  - 301 to 500 boxes (high):  {b301_500:2d} classes")
print(f"  - 501 to 1000 boxes:        {b501_1000:2d} classes")
print(f"  - > 1,000 boxes (dominant): {b1000plus:2d} classes")

