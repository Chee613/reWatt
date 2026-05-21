# Colab Plan: YAMNet (audio) + YOLO26N (vision)

This document is a Colab-ready outline with minimal scripts and commands. It targets:
- Audio: 5s clips, YAMNet embeddings, binary label: human-present vs empty.
- Vision: single class `person`, YOLO26N, high-angle indoor scenes.

## 0) Project layout (Google Drive)

Create a Drive folder:

```
MyDrive/
  rewatt_ml/
    audio/
      raw/
        positive/
        negative/
      clips_5s/
        train/
        val/
        test/
      metadata/
    vision/
      raw_images/
        train/
        val/
        test/
      labels_yolo/
        train/
        val/
        test/
    exports/
      yamnet_tflite/
      yolo_tflite_or_onnx/
```

Notes:
- Raw media should be temporary on phone; upload only the 5s clips or single frames.
- Keep negative audio samples (empty room) at ~30-50% of the dataset.

## 1) Colab setup (first cell)

```bash
# Audio dependencies
pip -q install tensorflow==2.15.0 tensorflow-hub==0.16.1 librosa==0.10.1

# Vision dependencies (YOLO)
pip -q install ultralytics==8.3.0
```

```python
from google.colab import drive
import os

drive.mount('/content/drive')
ROOT = '/content/drive/MyDrive/rewatt_ml'
os.makedirs(ROOT, exist_ok=True)
```

## 2) Audio pipeline (YAMNet)

### 2.1 Load YAMNet and compute embeddings

```python
import tensorflow as tf
import tensorflow_hub as hub
import numpy as np
import librosa

YAMNET_MODEL_HANDLE = 'https://tfhub.dev/google/yamnet/1'
yamnet = hub.load(YAMNET_MODEL_HANDLE)

# Selected YAMNet indices for positive (human-present)
POSITIVE_INDICES = [
    0, 2, 3, 12, 13, 15, 18, 23,  # speech/laughter
    61, 62, 63, 64, 65,           # crowd ambience
    36, 42, 43, 44, 45,           # body sounds
    47, 48, 373, 454, 481,        # movement/footsteps
    348, 351, 352, 353, 354, 355, # doors/entry
    378, 380, 381, 473, 485       # typing/writing
]

NEGATIVE_HINTS = [407, 494, 508]  # optional negatives: HVAC, silence, env noise

SAMPLE_RATE = 16000
CLIP_SECONDS = 5


def load_audio_5s(path):
    audio, _ = librosa.load(path, sr=SAMPLE_RATE, mono=True, duration=CLIP_SECONDS)
    if len(audio) < SAMPLE_RATE * CLIP_SECONDS:
        pad = SAMPLE_RATE * CLIP_SECONDS - len(audio)
        audio = np.pad(audio, (0, pad))
    return audio.astype(np.float32)


def yamnet_embeddings(path):
    audio = load_audio_5s(path)
    scores, embeddings, _ = yamnet(audio)
    # Aggregate over frames
    emb = tf.reduce_mean(embeddings, axis=0)
    score = tf.reduce_mean(scores, axis=0)
    return emb.numpy(), score.numpy()
```

### 2.2 Build dataset from 5s clips

```python
import glob
from sklearn.model_selection import train_test_split

POS_DIR = f'{ROOT}/audio/clips_5s/positive'
NEG_DIR = f'{ROOT}/audio/clips_5s/negative'

pos_files = glob.glob(os.path.join(POS_DIR, '*.wav'))
neg_files = glob.glob(os.path.join(NEG_DIR, '*.wav'))

X = []
y = []

for f in pos_files:
    emb, _ = yamnet_embeddings(f)
    X.append(emb)
    y.append(1)

for f in neg_files:
    emb, _ = yamnet_embeddings(f)
    X.append(emb)
    y.append(0)

X = np.array(X)
y = np.array(y)

X_train, X_tmp, y_train, y_tmp = train_test_split(X, y, test_size=0.2, stratify=y, random_state=42)
X_val, X_test, y_val, y_test = train_test_split(X_tmp, y_tmp, test_size=0.5, stratify=y_tmp, random_state=42)
```

### 2.3 Train a small classifier head

```python
from tensorflow import keras

model = keras.Sequential([
    keras.layers.Input(shape=(X.shape[1],)),
    keras.layers.Dense(128, activation='relu'),
    keras.layers.Dropout(0.2),
    keras.layers.Dense(1, activation='sigmoid')
])

model.compile(
    optimizer='adam',
    loss='binary_crossentropy',
    metrics=['accuracy', keras.metrics.AUC(name='auc')]
)

history = model.fit(
    X_train, y_train,
    validation_data=(X_val, y_val),
    epochs=15,
    batch_size=32
)

model.evaluate(X_test, y_test)
```

### 2.4 Export classifier to TFLite (INT8)

```python
def representative_data_gen():
    for i in range(min(200, len(X_train))):
        yield [X_train[i:i+1].astype(np.float32)]

converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.representative_dataset = representative_data_gen
converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
converter.inference_input_type = tf.int8
converter.inference_output_type = tf.int8

tflite_model = converter.convert()

out_path = f'{ROOT}/exports/yamnet_tflite/yamnet_head_int8.tflite'
os.makedirs(os.path.dirname(out_path), exist_ok=True)
with open(out_path, 'wb') as f:
    f.write(tflite_model)
```

## 3) Vision pipeline (YOLO26N)

### 3.1 Dataset format (single class)

YOLO expects:
- Images: `vision/raw_images/{train,val,test}/*.jpg`
- Labels: `vision/labels_yolo/{train,val,test}/*.txt`

Each label file line:

```
class_id x_center y_center width height
```

Use `class_id = 0` for `person` only.

### 3.2 YOLO training config (Ultralytics)

Create `data.yaml` in Drive:

```yaml
path: /content/drive/MyDrive/rewatt_ml/vision
train: raw_images/train
val: raw_images/val
test: raw_images/test

names:
  0: person
```

### 3.3 Train YOLO26N

```python
from ultralytics import YOLO

model = YOLO('yolo26n.pt')  # If unavailable, replace with the closest nano model

model.train(
    data=f'{ROOT}/vision/data.yaml',
    imgsz=640,
    epochs=100,
    batch=16,
    device=0,
    workers=2
)

metrics = model.val()
print(metrics)
```

### 3.4 Export to TFLite / ONNX

```python
model.export(format='tflite', int8=True)
# or
model.export(format='onnx', opset=12)
```

Move exported files to:

```
{ROOT}/exports/yolo_tflite_or_onnx/
```

## 4) Inference sanity checks

### 4.1 Audio

```python
# Example: run a single clip through the head
sample = X_test[0:1].astype(np.float32)
pred = model.predict(sample)[0][0]
print('human_present_score:', float(pred))
```

### 4.2 Vision

```python
results = model.predict(f'{ROOT}/vision/raw_images/test/sample.jpg')
print(results[0].boxes)
```

## 5) Thresholding guidance

- Start with a conservative audio threshold (e.g., 0.7) for human-present.
- Tune based on false positives in empty-room validation clips.
- For vision, count only detections above confidence 0.4-0.6 and apply NMS IoU 0.5.

## 6) Data collection pipeline (workflow level)

Phone-side capture loop (no raw retention):
- Every 5s: record 5s audio, run quick checksum, upload clip to Drive, then delete.
- Every 1 min: capture one frame, upload to Drive, then delete.
- Persist only metadata (timestamp, location tag, device id, ambient label).

Labeling:
- Audio: auto-assign label using YAMNet indices above; manual review on uncertain cases.
- Vision: pre-label with a generic person detector, then correct manually for high-angle occlusions.

## 7) Deliverables checklist

- [ ] YAMNet classifier head (TFLite INT8)
- [ ] YOLO26N (TFLite or ONNX)
- [ ] Validation metrics screenshots/notes
- [ ] Threshold recommendation sheet
