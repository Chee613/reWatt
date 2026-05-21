package com.rewatt.app.perception

import android.graphics.Bitmap

data class DetectionBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun clamped(): DetectionBox =
        DetectionBox(
            left = left.coerceIn(0f, 1f),
            top = top.coerceIn(0f, 1f),
            right = right.coerceIn(0f, 1f),
            bottom = bottom.coerceIn(0f, 1f),
        )
}

data class ObjectDetection(
    val label: String,
    val confidence: Float,
    val classId: Int,
    val box: DetectionBox,
) {
    val isPerson: Boolean = classId == 0 || label.equals("person", ignoreCase = true)
}

data class SoundDetection(
    val label: String,
    val confidence: Float,
)

data class PerceptionUiState(
    val isRunning: Boolean = false,
    val cameraPermissionGranted: Boolean = false,
    val microphonePermissionGranted: Boolean = false,
    val statusMessage: String = "Perception is stopped.",
    val latestFrame: Bitmap? = null,
    val latestFrameAtMillis: Long? = null,
    val yoloDetections: List<ObjectDetection> = emptyList(),
    val yoloMessage: String = "YOLO is waiting for camera frames.",
    val latestAudioAtMillis: Long? = null,
    val audioSampleCount: Int = 0,
    val audioRms: Float = 0f,
    val audioWaveform: List<Float> = emptyList(),
    val soundDetections: List<SoundDetection> = emptyList(),
    val yamnetMessage: String = "YAMNet is waiting for microphone audio.",
) {
    val personCount: Int = yoloDetections.count { it.isPerson }
}

