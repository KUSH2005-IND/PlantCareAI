import torch, litert_torch
from torchvision import models

model = models.mobilenet_v3_large(weights=None)
model.classifier[3] = torch.nn.Linear(model.classifier[3].in_features, 38)
model.load_state_dict(torch.load("disease_finetuned.pth", map_location="cpu"))
model.eval()

edge_model = litert_torch.convert(model, (torch.randn(1, 3, 224, 224),))
edge_model.export("disease_mobilenet.tflite")