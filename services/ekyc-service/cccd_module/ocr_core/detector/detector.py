import numpy as np
import cv2
import torch

# Fix for PyTorch 2.6 weights_only=True security restriction
_original_load = torch.load
def safe_load(*args, **kwargs):
    kwargs['weights_only'] = False
    return _original_load(*args, **kwargs)
torch.load = safe_load

from ultralytics import YOLO


class Detector(object):
    def __init__(self, path_to_model, nms_threshold=0.2, score_threshold=0.3):
        self.path_to_model = path_to_model
        self.nms_threshold = nms_threshold
        self.score_threshold = score_threshold
        self.model = YOLO(self.path_to_model)

    def predict(self, img):
        # Predict with YOLO (use agnostic_nms to prevent overlapping boxes of different classes)
        results = self.model.predict(img, conf=self.score_threshold, iou=self.nms_threshold, agnostic_nms=True, verbose=False)
        
        # Get category index mapping
        category_index = {i: {'name': name} for i, name in self.model.names.items()}
        
        detection_boxes = []
        detection_classes = []
        
        if len(results) > 0:
            boxes = results[0].boxes
            for box in boxes:
                # YOLO box is [xmin, ymin, xmax, ymax]
                x1, y1, x2, y2 = box.xyxy[0].cpu().numpy()
                x1, y1, x2, y2 = int(x1), int(y1), int(x2), int(y2)
                cls_id = int(box.cls[0].cpu().item())
                # Format to [ymin, xmin, ymax, xmax] for compatibility with merged_model.py
                detection_boxes.append([y1, x1, y2, x2])
                detection_classes.append(cls_id)
                
        if len(detection_boxes) == 0:
            return np.empty((0, 4)), np.empty((0,), dtype=int), category_index
            
        return np.array(detection_boxes), np.array(detection_classes), category_index

    def draw(self, image):
        detection_boxes, detection_classes, category_index = self.predict(image)
        height, width, _ = image.shape

        for i in range(len(detection_classes)):
            label = str(category_index[detection_classes[i]]['name'])
            real_ymin = int(max(1, detection_boxes[i][0]))
            real_xmin = int(max(1, detection_boxes[i][1]))
            real_ymax = int(min(height, detection_boxes[i][2]))
            real_xmax = int(min(width, detection_boxes[i][3]))

            cv2.rectangle(image, (real_xmin, real_ymin), (real_xmax, real_ymax), (0, 255, 0), 2)
            cv2.putText(image, label, (real_xmin, real_ymin), cv2.FONT_HERSHEY_SIMPLEX, color=(0, 0, 255),
                        fontScale=0.5)

        return image

