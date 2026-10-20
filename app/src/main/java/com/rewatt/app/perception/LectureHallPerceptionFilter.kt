package com.rewatt.app.perception

object LectureHallPerceptionFilter {
    fun filterYoloDetections(detections: List<ObjectDetection>): List<ObjectDetection> =
        detections.filter { detection -> detection.isPerson }
}
