package com.rewatt.app.perception

import kotlin.math.max
import kotlin.math.roundToInt

object YoloPostProcessor {
    // Low floor: tiled inference + cross-tile NMS recover the recall, then the label filter prunes.
    const val DEFAULT_CONFIDENCE_THRESHOLD = 0.10f
    const val INPUT_SIZE = 640

    fun process(
        rows: Array<FloatArray>,
        labels: List<String>,
        confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
        inputSize: Int = INPUT_SIZE,
    ): List<ObjectDetection> =
        rows.asSequence()
            .mapNotNull { row -> row.toDetection(labels, confidenceThreshold, inputSize) }
            .sortedByDescending { it.confidence }
            .toList()

    private fun FloatArray.toDetection(
        labels: List<String>,
        confidenceThreshold: Float,
        inputSize: Int,
    ): ObjectDetection? {
        if (size < 6) return null

        val confidence = this[4]
        if (confidence < confidenceThreshold) return null

        val classId = this[5].roundToInt()
        val label = labels.getOrNull(classId) ?: "class $classId"
        val maxCoordinate = max(max(this[0], this[1]), max(this[2], this[3]))
        val scale = if (maxCoordinate > 2f) inputSize.toFloat() else 1f

        var left = this[0] / scale
        var top = this[1] / scale
        var right = this[2] / scale
        var bottom = this[3] / scale

        if (right < left || bottom < top) {
            val centerX = this[0] / scale
            val centerY = this[1] / scale
            val width = this[2] / scale
            val height = this[3] / scale
            left = centerX - width / 2f
            top = centerY - height / 2f
            right = centerX + width / 2f
            bottom = centerY + height / 2f
        }

        val box = DetectionBox(left, top, right, bottom).clamped()
        if (box.right <= box.left || box.bottom <= box.top) return null

        return ObjectDetection(
            label = label,
            confidence = confidence,
            classId = classId,
            box = box,
        )
    }
}

