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

enum class AudioPresenceLevel {
    None,
    Weak,
    Strong,
}

enum class CaptureStartSource {
    Manual,
    Auto,
}

enum class YoloModel(val displayName: String, val assetName: String) {
    Yolo26nFp16("YOLO26n FP16 (1280)", "yolo26n_float16.tflite"),
    BestFp16("Best (custom FP16)", "best_float16.tflite"),
    Yolo26m("YOLO26m (medium)", "yolo26m.tflite"),
    Yolo26n1920("YOLO26n (1920)", "yolo26n-1920.tflite"),
}

data class SoundDetection(
    val label: String,
    val confidence: Float,
    val presenceLevel: AudioPresenceLevel =
        LectureHallPerceptionFilter.classifyAudioLabel(label),
) {
    val indicatesHumanPresence: Boolean = presenceLevel == AudioPresenceLevel.Strong
    val supportsHumanPresence: Boolean = presenceLevel != AudioPresenceLevel.None
}

data class ExposureReading(
    val ev: Double,
    val iso: Int,
    val exposureTimeSeconds: Double,
    val aperture: Double,
    val lightsOn: Boolean,
)

data class PerceptionUiState(
    val isRunning: Boolean = false,
    val captureStartSource: CaptureStartSource? = null,
    val cameraPermissionGranted: Boolean = false,
    val microphonePermissionGranted: Boolean = false,
    val statusMessage: String = "Perception is stopped.",
    val latestFrame: Bitmap? = null,
    val latestFrameAtMillis: Long? = null,
    val yoloDetections: List<ObjectDetection> = emptyList(),
    val yoloMessage: String = "YOLO is waiting for camera frames.",
    val exposureEv: Double? = null,
    val exposureIso: Int? = null,
    val exposureTimeSeconds: Double? = null,
    val exposureAperture: Double = ExposureValueCalculator.FIXED_APERTURE,
    val lightsOn: Boolean? = null,
    val exposureMessage: String = "Exposure is waiting for camera capture.",
    val latestAudioAtMillis: Long? = null,
    val audioSampleCount: Int = 0,
    val audioRms: Float = 0f,
    val audioWaveform: List<Float> = emptyList(),
    val soundDetections: List<SoundDetection> = emptyList(),
    val yamnetMessage: String = "YAMNet is waiting for microphone audio.",
    val selectedYoloModel: YoloModel = YoloModel.Yolo26nFp16,
) {
    val peopleDetections: List<ObjectDetection> = yoloDetections.filter { it.isPerson }
    val personCount: Int = peopleDetections.size
    val audioPresenceDetected: Boolean = soundDetections.any { it.indicatesHumanPresence }
    val audioPresenceScore: Float =
        soundDetections.filter { it.indicatesHumanPresence }.maxOfOrNull { it.confidence } ?: 0f
}
