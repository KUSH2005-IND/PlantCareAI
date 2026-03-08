

from flask import Flask, render_template, request
import os
from utils.predict import predict_image

app = Flask(__name__)

upload = "static/uploads"
app.config["UPLOAD_FOLDER"] = upload

# ensure upload folder exists
os.makedirs(upload, exist_ok=True)


@app.route("/", methods=["GET", "POST"])
def index():

    if request.method == "POST":

        file = request.files["image"]

        if file.filename == "":
            return render_template("index.html", error="No file selected")

        filepath = os.path.join(app.config["UPLOAD_FOLDER"], file.filename)

        file.save(filepath)

        prediction, confidence = predict_image(filepath)

        return render_template(
            "result.html",
            prediction=prediction,
            confidence=round(confidence * 100, 2),
            image=filepath
        )

    return render_template("index.html")


if __name__ == "__main__":
    app.run(debug=True)