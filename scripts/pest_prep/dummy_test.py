from pathlib import Path
import zipfile

zip_path = Path(
    r"C:\Users\sriva\PycharmProjects\PlantCareAI\ip102_yolo_fixed.zip"
)

print("Exists:", zip_path.exists())

if zip_path.exists():
    print("Size (MB):", round(zip_path.stat().st_size / (1024**2), 2))
    print("Valid ZIP:", zipfile.is_zipfile(zip_path))

    if zipfile.is_zipfile(zip_path):
        with zipfile.ZipFile(zip_path, "r") as z:
            print("Integrity:", z.testzip())
            print("Total files:", len(z.namelist()))
