import os

IMAGE_DIR = "data/raw/ip102/VOC2007/JPEGImages"

files = os.listdir(IMAGE_DIR)

print("Total files:", len(files))

print("\nFirst 20 image filenames:")
for filename in files[:20]:
    print(filename)

print("\nImage extensions:")
from collections import Counter

extensions = Counter(
    os.path.splitext(filename)[1].lower()
    for filename in files
)

print(extensions)