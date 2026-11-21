import os
from flask import Flask, render_template, request
from werkzeug.utils import secure_filename

from src.pest.crop_pest_map import SUPPORTED_CROPS
from utils.predict import predict_image

app = Flask(__name__)

UPLOAD_FOLDER = "static/uploads"
app.config["UPLOAD_FOLDER"] = UPLOAD_FOLDER
os.makedirs(UPLOAD_FOLDER, exist_ok=True)


@app.route("/", methods=["GET", "POST"])
def index():
    if request.method == "POST":
        file = request.files.get("image")
        if not file or file.filename == "":
            return render_template(
                "index.html", error="No file selected", crops=SUPPORTED_CROPS
            )

        mode = request.form.get("mode", "disease").strip().lower()
        crop = request.form.get("crop", "all").strip().lower()

        filename = secure_filename(file.filename)
        filepath = os.path.join(app.config["UPLOAD_FOLDER"], filename)
        file.save(filepath)

        prediction, confidence, display_image, detections = predict_image(
            filepath, mode=mode, crop=crop
        )

        return render_template(
            "result.html",
            prediction=prediction,
            confidence=round(confidence * 100, 2),
            image=display_image,
            mode=mode,
            crop=crop,
            detections=detections,
        )

    return render_template("index.html", crops=SUPPORTED_CROPS)


if __name__ == "__main__":
    app.run(debug=True)