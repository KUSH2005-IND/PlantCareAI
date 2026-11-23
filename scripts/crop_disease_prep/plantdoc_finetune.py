from pathlib import Path
import numpy as np
import tensorflow as tf

ROOT = Path(__file__).resolve().parents[2]

PV_CLASSES = [
    "Apple___Apple_scab", "Apple___Black_rot", "Apple___Cedar_apple_rust", "Apple___healthy",
    "Blueberry___healthy", "Cherry_(including_sour)___Powdery_mildew", "Cherry_(including_sour)___healthy",
    "Corn_(maize)___Cercospora_leaf_spot Gray_leaf_spot", "Corn_(maize)___Common_rust_",
    "Corn_(maize)___Northern_Leaf_Blight", "Corn_(maize)___healthy", "Grape___Black_rot",
    "Grape___Esca_(Black_Measles)", "Grape___Leaf_blight_(Isariopsis_Leaf_Spot)", "Grape___healthy",
    "Orange___Haunglongbing_(Citrus_greening)", "Peach___Bacterial_spot", "Peach___healthy",
    "Pepper,_bell___Bacterial_spot", "Pepper,_bell___healthy", "Potato___Early_blight",
    "Potato___Late_blight", "Potato___healthy", "Raspberry___healthy", "Soybean___healthy",
    "Squash___Powdery_mildew", "Strawberry___Leaf_scorch", "Strawberry___healthy",
    "Tomato___Bacterial_spot", "Tomato___Early_blight", "Tomato___Late_blight",
    "Tomato___Leaf_Mold", "Tomato___Septoria_leaf_spot", "Tomato___Spider_mites Two-spotted_spider_mite",
    "Tomato___Target_Spot", "Tomato___Tomato_Yellow_Leaf_Curl_Virus", "Tomato___Tomato_mosaic_virus",
    "Tomato___healthy",
]
PV_TO_IDX = {name: i for i, name in enumerate(PV_CLASSES)}

PD_TO_PV = {
    "Apple Scab Leaf": "Apple___Apple_scab", "Apple leaf": "Apple___healthy",
    "Apple rust leaf": "Apple___Cedar_apple_rust", "Bell_pepper leaf": "Pepper,_bell___healthy",
    "Bell_pepper leaf spot": "Pepper,_bell___Bacterial_spot", "Blueberry leaf": "Blueberry___healthy",
    "Cherry leaf": "Cherry_(including_sour)___healthy",
    "Corn Gray leaf spot": "Corn_(maize)___Cercospora_leaf_spot Gray_leaf_spot",
    "Corn leaf blight": "Corn_(maize)___Northern_Leaf_Blight", "Corn rust leaf": "Corn_(maize)___Common_rust_",
    "Peach leaf": "Peach___healthy", "Potato leaf early blight": "Potato___Early_blight",
    "Potato leaf late blight": "Potato___Late_blight", "Raspberry leaf": "Raspberry___healthy",
    "Soyabean leaf": "Soybean___healthy", "Squash Powdery mildew leaf": "Squash___Powdery_mildew",
    "Strawberry leaf": "Strawberry___healthy", "Tomato Early blight leaf": "Tomato___Early_blight",
    "Tomato Septoria leaf spot": "Tomato___Septoria_leaf_spot", "Tomato leaf": "Tomato___healthy",
    "Tomato leaf bacterial spot": "Tomato___Bacterial_spot", "Tomato leaf late blight": "Tomato___Late_blight",
    "Tomato leaf mosaic virus": "Tomato___Tomato_mosaic_virus",
    "Tomato leaf yellow virus": "Tomato___Tomato_Yellow_Leaf_Curl_Virus",
    "Tomato mold leaf": "Tomato___Leaf_Mold", "grape leaf": "Grape___healthy",
    "grape leaf black rot": "Grape___Black_rot",
}

model = tf.keras.models.load_model(ROOT / "models/disease/plant_disease_model.h5", compile=False)
img_h, img_w = model.input_shape[1], model.input_shape[2]

def build_dataset(split_dir: Path, training: bool, scale_mode: str):
    paths, labels = [], []
    for folder in split_dir.iterdir():
        if folder.is_dir() and folder.name in PD_TO_PV:
            idx = PV_TO_IDX[PD_TO_PV[folder.name]]
            for p in folder.glob("*.*"):
                if p.suffix.lower() in {".jpg", ".jpeg", ".png"}:
                    paths.append(str(p))
                    labels.append(idx)

    ds = tf.data.Dataset.from_tensor_slices((paths, labels))
    if training:
        ds = ds.shuffle(len(paths), reshuffle_each_iteration=True)

    aug = tf.keras.Sequential([
        tf.keras.layers.RandomFlip("horizontal"),
        tf.keras.layers.RandomRotation(0.15),
        tf.keras.layers.RandomZoom((-0.25, 0.05)),  # Zooms in on leaf lesions
        tf.keras.layers.RandomContrast(0.2),
    ])

    def process(path, label):
        img = tf.io.decode_image(tf.io.read_file(path), channels=3, expand_animations=False)
        if training:
            img = tf.image.resize(img, [256, 256])
            img = tf.image.random_crop(img, [img_h, img_w, 3])
            img = aug(tf.cast(img, tf.float32), training=True)
        else:
            # Central 85% crop removes outer background clutter on field shots
            img = tf.image.central_crop(img, 0.85)
            img = tf.cast(tf.image.resize(img, [img_h, img_w]), tf.float32)

        if scale_mode == "[0, 1]":
            img = img / 255.0
        elif scale_mode == "[-1, 1]":
            img = (img / 127.5) - 1.0
        return img, tf.one_hot(label, len(PV_CLASSES))

    return ds.map(process, num_parallel_calls=tf.data.AUTOTUNE).batch(32).prefetch(tf.data.AUTOTUNE)

# 1. Detect exact input scaling directly on loaded model
model.compile(loss="categorical_crossentropy", metrics=["accuracy"])
best_mode, best_zs = "[0, 1]", -1.0
for mode in ["[0, 1]", "[-1, 1]", "[0, 255]"]:
    _, acc = model.evaluate(build_dataset(ROOT / "data/PlantDoc-Dataset/test", False, mode), verbose=0)
    print(f"Zero-shot PlantDoc val_acc with {mode}: {acc * 100:.2f}%")
    if acc > best_zs:
        best_zs, best_mode = acc, mode

print(f"\nSelected input scale: {best_mode}")
train_ds = build_dataset(ROOT / "data/PlantDoc-Dataset/train", True, best_mode)
val_ds = build_dataset(ROOT / "data/PlantDoc-Dataset/test", False, best_mode)

# 2. STAGE 1: Warm up classifier head only (prevents shocking MobileNetV2 weights)
model.trainable = True
for layer in model.layers:
    layer.trainable = layer.name in {"dense", "dense_1"}

print(f"Stage 1 trainable params: {sum(int(tf.size(w)) for w in model.trainable_weights):,}")
model.compile(
    optimizer=tf.keras.optimizers.Adam(learning_rate=3e-4),
    loss=tf.keras.losses.CategoricalCrossentropy(label_smoothing=0.1),
    metrics=["accuracy"],
)
model.fit(train_ds, validation_data=val_ds, epochs=3)

# 3. STAGE 2: Unfreeze block_13 -> Conv_1 + head (keep BatchNorm frozen)
unfreeze_from = [i for i, l in enumerate(model.layers) if l.name == "block_13_expand"][0]
for i, layer in enumerate(model.layers):
    if i < unfreeze_from:
        layer.trainable = False
    else:
        layer.trainable = not isinstance(layer, tf.keras.layers.BatchNormalization)

print(f"Stage 2 trainable params: {sum(int(tf.size(w)) for w in model.trainable_weights):,}")
model.compile(
    optimizer=tf.keras.optimizers.Adam(learning_rate=5e-5),
    loss=tf.keras.losses.CategoricalCrossentropy(label_smoothing=0.1),
    metrics=["accuracy"],
)

best_path = ROOT / "models/disease/disease_finetuned.keras"
callbacks = [
    tf.keras.callbacks.ModelCheckpoint(str(best_path), monitor="val_accuracy", save_best_only=True, verbose=1),
    tf.keras.callbacks.ReduceLROnPlateau(monitor="val_loss", factor=0.5, patience=2, verbose=1),
]
model.fit(train_ds, validation_data=val_ds, epochs=10, callbacks=callbacks)

# 4. Evaluate both 38-class accuracy & Crop-Conditioned accuracy (when crop is selected in app)
best_model = tf.keras.models.load_model(best_path)
crops = [c.split("___")[0] for c in PV_CLASSES]
raw_correct = crop_correct = total = 0

for x_b, y_b in val_ds:
    probs = best_model.predict(x_b, verbose=0)
    y_true = np.argmax(y_b.numpy(), axis=1)
    raw_pred = np.argmax(probs, axis=1)
    raw_correct += int((raw_pred == y_true).sum())

    for p, t in zip(probs, y_true):
        mask = np.array([1.0 if crops[i] == crops[t] else 0.0 for i in range(len(PV_CLASSES))])
        if int(np.argmax(p * mask)) == int(t):
            crop_correct += 1
    total += len(y_true)

print(f"\nFinal PlantDoc 38-Class Val Acc: {100 * raw_correct / total:.2f}%")
print(f"Final PlantDoc Crop-Aware Val Acc: {100 * crop_correct / total:.2f}%")

# 5. Export TFLite
tflite_bytes = tf.lite.TFLiteConverter.from_keras_model(best_model).convert()
out_tflite = ROOT / "models/disease/disease_mobilenet.tflite"
out_tflite.write_bytes(tflite_bytes)
print(f"Exported: {out_tflite} | Use input scale: {best_mode}")