"""Lightweight CSV-to-YOLO label helper.

CSV columns (required):
- image_path
- x_min, y_min, x_max, y_max
- img_width, img_height

Optional:
- class_id (defaults to 0)

Usage:
python yolo_label_helper.py --csv labels.csv --out labels_yolo
"""

import argparse
import csv
import os
from collections import defaultdict


def clamp01(value: float) -> float:
    return max(0.0, min(1.0, value))


def to_yolo(x_min, y_min, x_max, y_max, img_w, img_h):
    x_center = ((x_min + x_max) / 2.0) / img_w
    y_center = ((y_min + y_max) / 2.0) / img_h
    width = (x_max - x_min) / img_w
    height = (y_max - y_min) / img_h
    return (
        clamp01(x_center),
        clamp01(y_center),
        clamp01(width),
        clamp01(height),
    )


def read_rows(csv_path):
    with open(csv_path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            yield row


def main():
    parser = argparse.ArgumentParser(description="Convert bounding boxes CSV to YOLO label files")
    parser.add_argument("--csv", required=True, help="Path to CSV file")
    parser.add_argument("--out", required=True, help="Output labels folder")
    args = parser.parse_args()

    os.makedirs(args.out, exist_ok=True)

    boxes_by_image = defaultdict(list)

    for row in read_rows(args.csv):
        image_path = row["image_path"]
        class_id = int(row.get("class_id", 0))

        x_min = float(row["x_min"])
        y_min = float(row["y_min"])
        x_max = float(row["x_max"])
        y_max = float(row["y_max"])
        img_w = float(row["img_width"])
        img_h = float(row["img_height"])

        x_center, y_center, width, height = to_yolo(x_min, y_min, x_max, y_max, img_w, img_h)
        boxes_by_image[image_path].append((class_id, x_center, y_center, width, height))

    for image_path, boxes in boxes_by_image.items():
        base = os.path.splitext(os.path.basename(image_path))[0]
        label_path = os.path.join(args.out, f"{base}.txt")
        with open(label_path, "w", encoding="utf-8") as f:
            for class_id, x_center, y_center, width, height in boxes:
                f.write(f"{class_id} {x_center:.6f} {y_center:.6f} {width:.6f} {height:.6f}\n")

    print(f"Wrote {len(boxes_by_image)} label files to {args.out}")


if __name__ == "__main__":
    main()
