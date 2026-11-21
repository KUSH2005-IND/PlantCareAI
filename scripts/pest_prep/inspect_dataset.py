
from pathlib import Path

ROOT = Path("data/raw/ip102/VOC2007")

folders = [
    "Annotations",
    "ImageSets/Main",
    "JPEGImages"
]

for folder in folders:
    path = ROOT / folder

    print(f"\n{'=' * 50}")
    print(f"{folder}")

    if not path.exists():
        print("NOT FOUND")
        continue

    files = sorted(path.iterdir())

    print("Total files:", len(files))

    for file in files[:8]:
        print(" ", file.name)

print("\nChecking classes.txt:")

classes_file = Path("data/raw/ip102/classes.txt")

if classes_file.exists():
    print(classes_file.read_text(encoding="utf-8"))
else:
    print("classes.txt not found")
