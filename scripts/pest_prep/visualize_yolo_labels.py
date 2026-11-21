
import random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageOps

# --------------------------------------------------
# CONFIGURATION
# --------------------------------------------------

ROOT = Path("data/processed/ip102/yolo")
CLASSES_FILE = Path("data/raw/ip102/classes.txt")

OUTPUT = Path("static/outputs/ip102_yolo_samples.jpg")

SAMPLE_COUNT = 20
RANDOM_SEED = 42

TILE_WIDTH = 400
TILE_HEIGHT = 320

# --------------------------------------------------
# LOAD CLASS NAMES
# --------------------------------------------------

class_names = []

with open(CLASSES_FILE, "r", encoding="utf-8") as file:
    for line in file:
        parts = line.strip().split(maxsplit=1)

        if len(parts) == 2:
            class_names.append(parts[1].strip())

assert len(class_names) == 102

# --------------------------------------------------
# COLLECT IMAGE-LABEL PAIRS
# --------------------------------------------------

samples = []

for split in ["train", "val", "test"]:

    image_dir = ROOT / "images" / split
    label_dir = ROOT / "labels" / split

    for image_path in image_dir.iterdir():

        if image_path.suffix.lower() not in [".jpg", ".jpeg"]:
            continue

        label_path = label_dir / f"{image_path.stem}.txt"

        if label_path.exists() and label_path.stat().st_size > 0:
            samples.append((image_path, label_path, split))

random.seed(RANDOM_SEED)

selected = random.sample(
    samples,
    min(SAMPLE_COUNT, len(samples))
)

# --------------------------------------------------
# CREATE CONTACT SHEET
# --------------------------------------------------

columns = 4
rows = (len(selected) + columns - 1) // columns

sheet = Image.new(
    "RGB",
    (columns * TILE_WIDTH, rows * TILE_HEIGHT),
    "white"
)

font = ImageFont.load_default(size=13)

for index, (image_path, label_path, split) in enumerate(selected):

    image = Image.open(image_path).convert("RGB")

    original_width, original_height = image.size

    draw = ImageDraw.Draw(image)

    with open(label_path, "r") as file:

        for line in file:

            parts = line.strip().split()

            if len(parts) != 5:
                continue

            class_id = int(parts[0])

            x, y, w, h = map(float, parts[1:])

            # Convert normalized YOLO coordinates
            # back into original image coordinates.

            x_center = x * original_width
            y_center = y * original_height

            box_width = w * original_width
            box_height = h * original_height

            xmin = x_center - box_width / 2
            ymin = y_center - box_height / 2
            xmax = x_center + box_width / 2
            ymax = y_center + box_height / 2

            draw.rectangle(
                [xmin, ymin, xmax, ymax],
                outline="lime",
                width=max(2, original_width // 300)
            )

            label = f"{class_id}: {class_names[class_id]}"

            # Draw label background
            text_box = draw.textbbox((xmin, ymin), label, font=font)

            draw.rectangle(
                text_box,
                fill="black"
            )

            draw.text(
                (xmin, ymin),
                label,
                fill="white",
                font=font
            )

    # Resize while maintaining aspect ratio
    image = ImageOps.contain(
        image,
        (TILE_WIDTH - 10, TILE_HEIGHT - 40)
    )

    tile = Image.new(
        "RGB",
        (TILE_WIDTH, TILE_HEIGHT),
        "white"
    )

    x_offset = (TILE_WIDTH - image.width) // 2
    y_offset = 30 + (TILE_HEIGHT - 40 - image.height) // 2

    tile.paste(image, (x_offset, y_offset))

    tile_draw = ImageDraw.Draw(tile)

    tile_draw.text(
        (8, 8),
        f"{split.upper()} | {image_path.stem}",
        fill="black",
        font=font
    )

    sheet_x = (index % columns) * TILE_WIDTH
    sheet_y = (index // columns) * TILE_HEIGHT

    sheet.paste(tile, (sheet_x, sheet_y))

# --------------------------------------------------
# SAVE OUTPUT
# --------------------------------------------------

OUTPUT.parent.mkdir(parents=True, exist_ok=True)

sheet.save(OUTPUT, quality=95)

print("Visualization completed!")
print("Images inspected:", len(selected))
print("Saved to:", OUTPUT.resolve())
