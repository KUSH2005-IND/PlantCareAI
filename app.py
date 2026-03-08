from flask import Flask, render_template, request
import os
from utils.predict import predict_image

app = Flask(__name__)

UPLOAD_FOLDER = "static/uploads"
app.config["UPLOAD_FOLDER"] = UPLOAD_FOLDER


@app.route("/", methods=["GET", "POST"])
def index():

    if request.method == "POST":

        file = request.files["image"]

        filepath = os.path.join(app.config["UPLOAD_FOLDER"], file.filename)

        file.save(filepath)

        prediction = predict_image(filepath)

        return render_template("result.html",
                               prediction=prediction,
                               image=filepath)

    return render_template("index.html")


if __name__ == "__main__":
    app.run(debug=True)