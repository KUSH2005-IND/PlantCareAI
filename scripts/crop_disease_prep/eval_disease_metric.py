import csv
import json
import time
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import tensorflow as tf
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "runs/disease/eval_plantdoc"
OUT_DIR.mkdir(parents=True, exist_ok=True)

BASELINE_H5 = ROOT / "models/disease/plant_disease_model.h5"
FINETUNED_KERAS = ROOT / "models/disease/disease_finetuned.keras"
TFLITE_PATH = ROOT / "models/disease/disease_mobilenet.tflite"
TEST_DIR = ROOT / "data/PlantDoc-Dataset/test"

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


def load_test_samples():
    images, labels, paths = [], [], []
    for folder in sorted(TEST_DIR.iterdir()):
        if folder.is_dir() and folder.name in PD_TO_PV:
            target = PV_TO_IDX[PD_TO_PV[folder.name]]
            for p in sorted(folder.glob("*.*")):
                if p.suffix.lower() in {".jpg", ".jpeg", ".png"}:
                    img = Image.open(p).convert("RGB")
                    w, h = img.size
                    cw, ch = int(w * 0.85), int(h * 0.85)
                    left, top = (w - cw) // 2, (h - ch) // 2
                    img = img.crop((left, top, left + cw, top + ch)).resize((224, 224))
                    images.append(np.asarray(img, dtype=np.float32) / 255.0)
                    labels.append(target)
                    paths.append(p)
    return np.stack(images, axis=0), np.array(labels, dtype=np.int32), paths


def compute_metrics(probs: np.ndarray, y_true: np.ndarray):
    crops = [c.split("___")[0] for c in PV_CLASSES]
    top1_pred = np.argmax(probs, axis=1)
    top3_pred = np.argsort(probs, axis=1)[:, -3:]

    top1_acc = float(np.mean(top1_pred == y_true))
    top3_acc = float(np.mean([y_true[i] in top3_pred[i] for i in range(len(y_true))]))

    crop_hits = 0
    for p, t in zip(probs, y_true):
        mask = np.array([1.0 if crops[i] == crops[t] else 0.0 for i in range(len(PV_CLASSES))])
        if int(np.argmax(p * mask)) == int(t):
            crop_hits += 1
    crop_acc = float(crop_hits / len(y_true))

    eval_indices = sorted(set(y_true.tolist()))
    idx_map = {orig_i: new_i for new_i, orig_i in enumerate(eval_indices)}
    n_cls = len(eval_indices)
    cm = np.zeros((n_cls, n_cls), dtype=np.int32)

    for t, p in zip(y_true, top1_pred):
        if p in idx_map:
            cm[idx_map[t], idx_map[p]] += 1

    per_class = []
    for orig_i in eval_indices:
        tp = int(np.sum((y_true == orig_i) & (top1_pred == orig_i)))
        fp = int(np.sum((y_true != orig_i) & (top1_pred == orig_i)))
        fn = int(np.sum((y_true == orig_i) & (top1_pred != orig_i)))
        support = int(np.sum(y_true == orig_i))
        prec = tp / (tp + fp) if (tp + fp) > 0 else 0.0
        rec = tp / (tp + fn) if (tp + fn) > 0 else 0.0
        f1 = (2 * prec * rec / (prec + rec)) if (prec + rec) > 0 else 0.0
        per_class.append({
            "class": PV_CLASSES[orig_i],
            "precision": round(prec, 4),
            "recall": round(rec, 4),
            "f1": round(f1, 4),
            "support": support,
        })

    macro_p = float(np.mean([r["precision"] for r in per_class]))
    macro_r = float(np.mean([r["recall"] for r in per_class]))
    macro_f1 = float(np.mean([r["f1"] for r in per_class]))
    total_sup = sum(r["support"] for r in per_class)
    weighted_f1 = float(sum(r["f1"] * r["support"] for r in per_class) / max(total_sup, 1))

    return {
        "top1_acc": round(top1_acc * 100, 2),
        "top3_acc": round(top3_acc * 100, 2),
        "crop_aware_acc": round(crop_acc * 100, 2),
        "macro_precision": round(macro_p * 100, 2),
        "macro_recall": round(macro_r * 100, 2),
        "macro_f1": round(macro_f1 * 100, 2),
        "weighted_f1": round(weighted_f1 * 100, 2),
        "per_class": per_class,
        "cm": cm,
        "eval_labels": [PV_CLASSES[i].replace("___", " : ") for i in eval_indices],
    }


def run_tflite_eval(x_test: np.ndarray):
    interpreter = tf.lite.Interpreter(model_path=str(TFLITE_PATH))
    interpreter.allocate_tensors()
    in_idx = interpreter.get_input_details()[0]["index"]
    out_idx = interpreter.get_output_details()[0]["index"]

    # Warmup
    for _ in range(5):
        interpreter.set_tensor(in_idx, x_test[0:1])
        interpreter.invoke()

    preds = []
    t0 = time.perf_counter()
    for i in range(len(x_test)):
        interpreter.set_tensor(in_idx, x_test[i : i + 1])
        interpreter.invoke()
        preds.append(interpreter.get_tensor(out_idx)[0])
    elapsed_ms = (time.perf_counter() - t0) * 1000.0
    avg_ms = elapsed_ms / len(x_test)
    return np.stack(preds, axis=0), round(avg_ms, 2), round(1000.0 / avg_ms, 1)


def plot_confusion_matrix(cm: np.ndarray, labels: list, normalized: bool, out_path: Path):
    mat = cm.astype(np.float32)
    if normalized:
        row_sums = mat.sum(axis=1, keepdims=True)
        mat = np.divide(mat, row_sums, out=np.zeros_like(mat), where=row_sums > 0)

    fig, ax = plt.subplots(figsize=(14, 12), facecolor="white")
    im = ax.imshow(mat, cmap="Blues", vmin=0, vmax=(1.0 if normalized else mat.max()))
    plt.colorbar(im, ax=ax, fraction=0.046, pad=0.04)

    short_labels = [l.split(" : ")[0][:8] + " - " + l.split(" : ")[1][:16] for l in labels]
    ax.set_xticks(range(len(labels)))
    ax.set_yticks(range(len(labels)))
    ax.set_xticklabels(short_labels, rotation=55, ha="right", fontsize=8)
    ax.set_yticklabels(short_labels, fontsize=8)
    ax.set_xlabel("Predicted Class", fontsize=11, fontweight="bold")
    ax.set_ylabel("True Class", fontsize=11, fontweight="bold")
    ax.set_title(
        "PlantDoc Normalized Confusion Matrix" if normalized else "PlantDoc Confusion Matrix (Counts)",
        fontsize=13, fontweight="bold", pad=14,
    )

    thresh = mat.max() * 0.55
    for i in range(mat.shape[0]):
        for j in range(mat.shape[1]):
            val = mat[i, j]
            if val > 0:
                txt = f"{val:.2f}" if normalized else f"{int(val)}"
                ax.text(j, i, txt, ha="center", va="center", fontsize=6.5,
                        color="white" if val > thresh else "#1f2937")

    plt.tight_layout()
    fig.savefig(out_path, dpi=220, facecolor="white")
    plt.close(fig)


def plot_domain_shift_bar(base_m: dict, ft_m: dict, tflite_m: dict, out_path: Path):
    categories = ["Top-1 (38-Class)", "Top-3 (38-Class)", "Crop-Aware Top-1", "Macro F1"]
    base_vals = [base_m["top1_acc"], base_m["top3_acc"], base_m["crop_aware_acc"], base_m["macro_f1"]]
    ft_vals = [ft_m["top1_acc"], ft_m["top3_acc"], ft_m["crop_aware_acc"], ft_m["macro_f1"]]
    tfl_vals = [tflite_m["top1_acc"], tflite_m["top3_acc"], tflite_m["crop_aware_acc"], tflite_m["macro_f1"]]

    x = np.arange(len(categories))
    w = 0.25
    fig, ax = plt.subplots(figsize=(10, 5.5), facecolor="white")
    b1 = ax.bar(x - w, base_vals, w, label="Baseline (.h5 Zero-Shot)", color="#94a3b8")
    b2 = ax.bar(x, ft_vals, w, label="Fine-Tuned (.keras)", color="#2563eb")
    b3 = ax.bar(x + w, tfl_vals, w, label="LiteRT Edge (.tflite)", color="#10b981")

    for bars in (b1, b2, b3):
        for bar in bars:
            h = bar.get_height()
            ax.annotate(f"{h:.1f}%", xy=(bar.get_x() + bar.get_width() / 2, h),
                        xytext=(0, 3), textcoords="offset points", ha="center", fontsize=8.5, fontweight="bold")

    ax.set_ylabel("Score (%)", fontsize=11, fontweight="bold")
    ax.set_title("Lab-to-Field Domain Adaptation Benchmark (PlantDoc Test Set)", fontsize=13, fontweight="bold")
    ax.set_xticks(x)
    ax.set_xticklabels(categories, fontsize=10, fontweight="bold")
    ax.set_ylim(0, 100)
    ax.grid(axis="y", linestyle="--", alpha=0.4)
    ax.spines[["top", "right"]].set_visible(False)
    ax.legend(frameon=False, fontsize=9.5)

    plt.tight_layout()
    fig.savefig(out_path, dpi=220, facecolor="white")
    plt.close(fig)


def plot_per_class_f1(per_class: list, out_path: Path):
    sorted_rows = sorted(per_class, key=lambda r: r["f1"])
    names = [r["class"].replace("___", " : ") for r in sorted_rows]
    scores = [r["f1"] * 100 for r in sorted_rows]

    fig, ax = plt.subplots(figsize=(11, 8.5), facecolor="white")
    colors = ["#ef4444" if s < 40 else ("#f59e0b" if s < 65 else "#10b981") for s in scores]
    bars = ax.barh(names, scores, color=colors, height=0.72)
    for bar, s in zip(bars, scores):
        ax.text(s + 1.0, bar.get_y() + bar.get_height() / 2, f"{s:.1f}%", va="center", fontsize=8)

    ax.set_xlim(0, 108)
    ax.set_xlabel("F1-Score (%)", fontsize=11, fontweight="bold")
    ax.set_title("Per-Class F1-Score on Real-World Field Images (PlantDoc)", fontsize=12.5, fontweight="bold")
    ax.grid(axis="x", linestyle="--", alpha=0.4)
    ax.spines[["top", "right"]].set_visible(False)

    plt.tight_layout()
    fig.savefig(out_path, dpi=220, facecolor="white")
    plt.close(fig)


def plot_val_grid(x_test: np.ndarray, y_true: np.ndarray, probs: np.ndarray, out_path: Path):
    rng = np.random.default_rng(42)
    idxs = rng.choice(len(x_test), size=min(16, len(x_test)), replace=False)

    fig, axes = plt.subplots(4, 4, figsize=(14, 14), facecolor="white")
    for ax, idx in zip(axes.flat, idxs):
        ax.imshow(x_test[idx])
        pred_i = int(np.argmax(probs[idx]))
        conf = probs[idx, pred_i] * 100
        gt_name = PV_CLASSES[y_true[idx]].replace("___", "\n")
        pr_name = PV_CLASSES[pred_i].replace("___", "\n")
        correct = pred_i == y_true[idx]
        color = "#16a34a" if correct else "#dc2626"

        ax.set_title(f"GT: {gt_name}\nPred: {pr_name} ({conf:.1f}%)", fontsize=7.5, color=color, fontweight="bold")
        for spine in ax.spines.values():
            spine.set_edgecolor(color)
            spine.set_linewidth(2.5)
        ax.set_xticks([])
        ax.set_yticks([])

    plt.suptitle("PlantDoc Validation Batch Predictions (Green = Match, Red = Mismatch)", fontsize=13, fontweight="bold")
    plt.tight_layout()
    fig.savefig(out_path, dpi=200, facecolor="white")
    plt.close(fig)


if __name__ == "__main__":
    print("Loading PlantDoc test set...")
    x_test, y_test, _ = load_test_samples()
    print(f"Loaded {len(x_test)} field test images across {len(set(y_test.tolist()))} classes.")

    # 1. Baseline zero-shot evaluation
    base_model = tf.keras.models.load_model(BASELINE_H5, compile=False)
    base_probs = base_model.predict(x_test, batch_size=32, verbose=0)
    base_metrics = compute_metrics(base_probs, y_test)

    # 2. Fine-tuned Keras evaluation
    ft_model = tf.keras.models.load_model(FINETUNED_KERAS, compile=False)
    t0 = time.perf_counter()
    ft_probs = ft_model.predict(x_test, batch_size=32, verbose=0)
    keras_ms = round(((time.perf_counter() - t0) * 1000.0) / len(x_test), 2)
    ft_metrics = compute_metrics(ft_probs, y_test)

    # 3. TFLite edge evaluation & parity
    tfl_probs, tfl_ms, tfl_fps = run_tflite_eval(x_test)
    tfl_metrics = compute_metrics(tfl_probs, y_test)
    max_prob_diff = float(np.max(np.abs(ft_probs - tfl_probs)))

    # 4. Save CSV per-class report
    csv_path = OUT_DIR / "classification_report.csv"
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=["class", "precision", "recall", "f1", "support"])
        writer.writeheader()
        writer.writerows(ft_metrics["per_class"])

    # 5. Save JSON summary
    summary = {
        "dataset": "PlantDoc-Dataset (test split)",
        "num_test_images": int(len(x_test)),
        "num_evaluated_classes": int(len(ft_metrics["per_class"])),
        "model_artifacts": {
            "baseline_h5_mb": round(BASELINE_H5.stat().st_size / (1024 * 1024), 2),
            "finetuned_keras_mb": round(FINETUNED_KERAS.stat().st_size / (1024 * 1024), 2),
            "edge_tflite_mb": round(TFLITE_PATH.stat().st_size / (1024 * 1024), 2),
            "total_params": int(ft_model.count_params()),
        },
        "latency_cpu": {
            "keras_batch_ms_per_img": keras_ms,
            "tflite_single_ms_per_img": tfl_ms,
            "tflite_fps": tfl_fps,
        },
        "baseline_zero_shot": {k: v for k, v in base_metrics.items() if k not in {"per_class", "cm", "eval_labels"}},
        "finetuned_keras": {k: v for k, v in ft_metrics.items() if k not in {"per_class", "cm", "eval_labels"}},
        "edge_tflite": {
            **{k: v for k, v in tfl_metrics.items() if k not in {"per_class", "cm", "eval_labels"}},
            "max_prob_diff_vs_keras": round(max_prob_diff, 6),
        },
    }
    (OUT_DIR / "metrics_summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")

    # 6. Generate YOLO-style plots
    plot_confusion_matrix(ft_metrics["cm"], ft_metrics["eval_labels"], False, OUT_DIR / "confusion_matrix.png")
    plot_confusion_matrix(ft_metrics["cm"], ft_metrics["eval_labels"], True, OUT_DIR / "confusion_matrix_normalized.png")
    plot_domain_shift_bar(base_metrics, ft_metrics, tfl_metrics, OUT_DIR / "domain_shift_comparison.png")
    plot_per_class_f1(ft_metrics["per_class"], OUT_DIR / "per_class_f1.png")
    plot_val_grid(x_test, y_test, ft_probs, OUT_DIR / "val_predictions_grid.png")

    print("\n" + "=" * 62)
    print("PLANTCAREAI DISEASE MODEL — BENCHMARK SUMMARY")
    print("=" * 62)
    print(f"Baseline (.h5)  -> Top-1: {base_metrics['top1_acc']}% | Top-3: {base_metrics['top3_acc']}% | Crop-Aware: {base_metrics['crop_aware_acc']}%")
    print(f"Fine-Tuned      -> Top-1: {ft_metrics['top1_acc']}% | Top-3: {ft_metrics['top3_acc']}% | Crop-Aware: {ft_metrics['crop_aware_acc']}% | Macro-F1: {ft_metrics['macro_f1']}%")
    print(f"Edge (.tflite)  -> Top-1: {tfl_metrics['top1_acc']}% | Size: {summary['model_artifacts']['edge_tflite_mb']} MB | Latency: {tfl_ms} ms ({tfl_fps} FPS) | Max Diff: {max_prob_diff:.2e}")
    print(f"\nAll reports & plots saved to: {OUT_DIR}")