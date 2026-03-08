# PlantCare AI

**Deep Learning-Based Plant Disease Detection System**

PlantCare AI is a computer vision application that identifies plant diseases from leaf images using a convolutional neural network. The project combines **deep learning for image classification** with a **Flask web application** to allow users to upload plant leaf images and receive instant disease predictions.

The system leverages **transfer learning with MobileNetV2**, trained on a large plant pathology dataset, and provides predictions through a clean web interface.

---

# Project Overview

Plant diseases significantly affect agricultural productivity and food security. Early detection helps farmers and gardeners prevent crop loss and apply timely treatment.

PlantCare AI provides:

* Automated **plant disease identification**
* **Image-based diagnosis**
* **Real-time predictions through a web interface**
* A scalable framework for future agricultural AI applications

---

# Dataset

The model was trained using the **New Plant Diseases Dataset (Augmented)** available on Kaggle.

Dataset Source:
https://www.kaggle.com/datasets/vipoooool/new-plant-diseases-dataset

### Dataset Characteristics

| Feature        | Value                                    |
| -------------- | ---------------------------------------- |
| Total Images   | ~87,000                                  |
| Classes        | 38 plant disease categories              |
| Image Type     | RGB plant leaf images                    |
| Resolution     | Varying resolutions                      |
| Plants Covered | Apple, Corn, Grape, Potato, Tomato, etc. |

### Example Classes

* Apple___Apple_scab
* Apple___Black_rot
* Corn___Common_rust
* Tomato___Late_blight
* Tomato___Early_blight
* Potato___Early_blight
* Grape___Black_rot

Each class represents **a specific plant and its disease condition**.

---

# Data Preprocessing

Proper data preprocessing is critical for deep learning models. The following preprocessing steps were applied before training.

## 1. Image Resizing

All images were resized to:

```
224 × 224 × 3
```

This matches the input size expected by **MobileNetV2**.

---

## 2. Pixel Normalization

Pixel values were scaled from:

```
[0,255] → [0,1]
```

Using:

```python
rescale = 1./255
```

Benefits:

* faster convergence
* improved gradient stability
* reduced numerical instability

---

## 3. Data Augmentation

To improve generalization and prevent overfitting, the training images were augmented using:

```python
ImageDataGenerator(
    rescale=1./255,
    rotation_range=25,
    zoom_range=0.2,
    shear_range=0.2,
    horizontal_flip=True
)
```

### Augmentation Techniques

| Technique       | Purpose                             |
| --------------- | ----------------------------------- |
| Rotation        | Handles different leaf orientations |
| Zoom            | Robustness to scale variation       |
| Shear           | Introduces geometric variation      |
| Horizontal Flip | Expands dataset diversity           |

---

## 4. Dataset Split

The dataset already provides predefined splits:

| Split      | Purpose                |
| ---------- | ---------------------- |
| Train      | Model learning         |
| Validation | Performance monitoring |

---

# Model Architecture

The project uses **transfer learning with MobileNetV2**, a lightweight CNN designed for efficient image classification.

MobileNetV2 is particularly suitable because it:

* has **low computational cost**
* performs well on image classification tasks
* is optimized for edge and mobile devices

---

## Transfer Learning Strategy

The pretrained MobileNetV2 model was loaded with **ImageNet weights**.

Steps:

1. Load pretrained backbone
2. Freeze convolutional layers
3. Add custom classification head
4. Train final layers on plant disease dataset

---

## Model Architecture

```
Input Image (224x224x3)
        │
        ▼
MobileNetV2 (Pretrained Backbone)
        │
        ▼
Global Average Pooling
        │
        ▼
Dense Layer (256 units, ReLU)
        │
        ▼
Softmax Output Layer (38 classes)
```

---

# Training Configuration

| Parameter     | Value                    |
| ------------- | ------------------------ |
| Optimizer     | Adam                     |
| Loss Function | Categorical Crossentropy |
| Batch Size    | 32                       |
| Epochs        | 5                        |
| Input Size    | 224×224                  |

---

# Model Evaluation

The trained model achieved the following performance:

| Metric              | Value |
| ------------------- |-------|
| Training Accuracy   | ~94%  |
| Validation Accuracy | ~91%  |
| Loss                | ~0.18 |

Evaluation metrics used:

* Accuracy
* Precision
* Recall
* F1-score
* Confusion matrix

---

### Metrics
![Prediction Result](static/outputs/metric.png)

# Web Application

The trained model is deployed through a **Flask-based web application** that allows users to upload leaf images and receive predictions.

Framework used:

* Flask (backend)
* HTML + Bootstrap (frontend)

---

#  Frontend

The user interface includes:

### Features

* Image upload
* Image preview
* Prediction result display
* Confidence score visualization
* Responsive layout

The frontend uses **Bootstrap** to provide a modern and clean UI.

### User Workflow

```
Upload leaf image
        ↓
Preview appears
        ↓
Click "Predict"
        ↓
Prediction + confidence score displayed
```

---

# Backend

The backend is implemented using **Flask**, which handles:

* file uploads
* model loading
* prediction processing
* returning results to the user interface

### Web Interface
![Web page Output](static/outputs/Output(PlantCareAI).png)
### Prediction Pipeline

```
User Upload
     │
     ▼
Flask Server
     │
     ▼
Image Preprocessing
(resize + normalization)
     │
     ▼
CNN Model Inference
     │
     ▼
Disease Prediction
     │
     ▼
Display Result Page
```

---

# Project Structure

```
PlantCareAI
│
├── app.py
├── requirements.txt
│
├── models
│   ├── plant_disease_model.h5
│   └── class_indices.json
│
├── notebook
│   └── plantcareai-notebook.ipynb
│
├── static
│   └── uploads
│
├── templates
│   ├── index.html
│   └── result.html
│
└── utils
    └── predict.py
```

---

#  Running the Project

## 1. Clone Repository

```
git clone <repository-url>
cd PlantCareAI
```

---

## 2. Create Virtual Environment

```
python -m venv venv
```

Activate environment:

Windows:

```
venv\Scripts\activate
```

---

## 3. Install Dependencies

```
pip install -r requirements.txt
```

---

## 4. Run Flask Application

```
python app.py
```

Open browser:

```
http://127.0.0.1:5000
```

---

# Example Output

Input: Tomato leaf image

Output:

```
Tomato : Early Blight
Confidence: 94.3%
```

---

# Future Improvements

Potential enhancements include:

* Grad-CAM visualization for explainable AI
* Mobile deployment
* Larger multi-crop datasets
* Real-time camera detection
* Disease treatment recommendations
* Integration with agricultural advisory systems

---

# Conclusion

PlantCare AI demonstrates how **deep learning and computer vision can be applied to agriculture** for automated disease detection.

The project combines:

* data preprocessing
* transfer learning
* model evaluation
* web deployment

to build an end-to-end AI application capable of diagnosing plant diseases from leaf images.

---

# Technologies Used

* Python
* TensorFlow / Keras
* Flask
* NumPy
* Pillow
* Bootstrap
* HTML/CSS

---

Future Implementations for PlantCare AI

PlantCare AI currently provides an automated system for plant disease detection using deep learning and a web interface. While the current system successfully classifies plant diseases from leaf images, there are several opportunities to enhance the project further and make it more impactful for real-world agricultural applications.

The following sections describe potential future implementations that can improve **model intelligence, usability, scalability, and agricultural relevance**.

---

# 1. Explainable AI with Grad-CAM Visualization

## Motivation
Deep learning models are often considered **black-box systems** because it is difficult to understand how they make predictions. For agricultural applications, farmers and researchers need to know **which part of the leaf the model used to detect the disease**.

## Proposed Solution
Integrate **Gradient-weighted Class Activation Mapping (Grad-CAM)** to visualize the regions of the leaf that contributed to the prediction.

Grad-CAM works by:
1. Computing gradients of the predicted class with respect to convolutional feature maps.
2. Generating a heatmap that highlights important regions in the image.
3. Overlaying this heatmap on the original leaf image.

## Expected Output


Original Leaf Image
+
Heatmap Highlighting Infected Region


## Benefits
- Improves **model interpretability**
- Helps users **verify predictions visually**
- Increases trust in AI-based diagnosis

---

# 2. Disease Severity Estimation

## Motivation
The current system only identifies the **type of disease**, but farmers also need to know **how severe the infection is**.

## Proposed Approach
Extend the model to estimate the **percentage of infected leaf area**.

Possible approaches include:

### Pixel-Level Infection Detection
Use segmentation models to identify infected regions.

### CNN Regression Head
Predict severity percentage directly.

Example output:


Disease: Tomato Early Blight
Severity: 35% infected area
Recommended Action: Moderate treatment required


## Potential Models
- U-Net
- Mask R-CNN
- Attention-based CNNs

## Benefits
- More informative predictions
- Enables **precision agriculture**

---

# 3. Multi-Crop and Multi-Dataset Training

## Motivation
The current dataset focuses on specific plant species. Real agricultural environments involve **many crops and varying disease conditions**.

## Proposed Enhancement
Combine multiple datasets to improve generalization.

Possible datasets include:

- PlantVillage
- PlantDoc
- AI Challenger Crop Dataset

## Techniques
- Domain adaptation
- Dataset balancing
- Cross-dataset validation

## Expected Outcome
A model capable of detecting diseases across **multiple crops and environments**.

---

# 4. Real-Time Mobile Application

## Motivation
Farmers primarily use **smartphones** for field work.

## Proposed Implementation

Convert the trained model to **TensorFlow Lite** and deploy it in a mobile application.

Steps:

1. Convert model to TensorFlow Lite format
2. Build mobile app using Flutter or React Native
3. Integrate real-time camera detection

Workflow:


Camera Capture
↓
Leaf Image
↓
Mobile CNN Model
↓
Disease Prediction


## Benefits
- Portable AI tool
- Works directly in agricultural fields
- Offline inference capability

---

# 5. Automated Treatment Recommendation System

## Motivation
Detecting a disease is useful, but farmers also need **actionable guidance**.

## Proposed Feature
Create a disease knowledge base mapping diseases to treatments.

Example output:


Disease: Potato Late Blight

Recommended Actions:
• Remove infected leaves
• Apply copper-based fungicide
• Improve ventilation in crop area


## Implementation Options
- Rule-based expert system
- Agricultural database integration
- Machine learning recommendation models

---

# 6. Leaf Segmentation for Improved Accuracy

## Motivation
Background noise such as soil, sky, or other plants can reduce classification accuracy.

## Proposed Solution
Add a preprocessing stage that isolates the leaf.

Possible techniques:

- Semantic segmentation
- Background removal models
- Color-based segmentation

Pipeline:


Input Image
↓
Leaf Segmentation
↓
Clean Leaf Image
↓
Disease Classification


## Expected Benefits
- Improved model accuracy
- Better feature extraction

---

# 7. Active Learning for Continuous Model Improvement

## Motivation
Agricultural environments constantly change and new disease variations may appear.

## Proposed System
Implement an **active learning pipeline** where the model improves from user inputs.

Workflow:

1. User uploads new images
2. Low-confidence predictions are flagged
3. Experts review and label those samples
4. Model retrains periodically

## Benefits
- Continuous learning
- Adapts to new disease patterns

---

# 8. Multi-Modal Disease Diagnosis

## Motivation
Disease detection may depend on additional factors besides leaf appearance.

Possible factors include:

- soil quality
- temperature
- humidity
- crop growth stage

## Proposed Enhancement
Integrate multiple data sources.

Example architecture:


Leaf Image
Weather Data
Soil Data
↓
Multi-modal Neural Network
↓
Disease Prediction


## Potential Approaches
- CNN + tabular data fusion
- Transformer-based multi-modal models

---

# 9. Cloud-Based AI Agriculture Platform

## Motivation
To support large-scale agricultural deployment, the system should be scalable.

## Proposed Architecture


Web/Mobile Client
↓
Cloud API
↓
Model Inference Server
↓
Prediction Result


Technologies:

- Docker containers
- Kubernetes orchestration
- AWS SageMaker or Google Cloud AI

## Benefits
- Supports thousands of users
- Easy model updates
- Centralized infrastructure

---

# 10. Early Disease Detection with Temporal Monitoring

## Motivation
Plant diseases often develop gradually. Detecting early symptoms helps reduce crop damage.

## Proposed Approach
Use **time-series image analysis**.

Farmers capture plant images periodically.

Example:


Day 1
Day 3
Day 7
Day 10


A temporal model analyzes disease progression.

Possible techniques:

- Recurrent Neural Networks
- Vision Transformers
- Temporal CNN models

## Impact
- Earlier detection
- Reduced pesticide usage
- Higher crop yield

---

# License

This project is for educational and research purposes.
