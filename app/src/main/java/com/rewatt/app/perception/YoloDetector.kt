package com.rewatt.app.perception

import android.content.Context
import android.graphics.Bitmap
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream
import org.json.JSONObject
import org.tensorflow.lite.Interpreter

class YoloDetector private constructor(
    private val interpreter: Interpreter,
    private val labels: List<String>,
) : Closeable {
    fun detect(bitmap: Bitmap): List<ObjectDetection> {
        val inputBuffer = bitmap.toYoloInputBuffer()
        val output = Array(1) { Array(300) { FloatArray(6) } }
        interpreter.run(inputBuffer, output)
        return LectureHallPerceptionFilter.filterYoloDetections(
            YoloPostProcessor.process(output[0], labels),
        )
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        const val MODEL_ASSET = "custom_human_detector_fp16.tflite"

        fun create(context: Context): YoloDetector {
            val modelBytes = AssetModelLoader.loadBytes(context, MODEL_ASSET)
            val labels = readLabelsFromMetadata(modelBytes).ifEmpty { fallbackCocoLabels }
            val modelBuffer = ByteBuffer
                .allocateDirect(modelBytes.size)
                .order(ByteOrder.nativeOrder())
                .apply {
                    put(modelBytes)
                    rewind()
                }

            return YoloDetector(
                interpreter = Interpreter(modelBuffer, Interpreter.Options().setNumThreads(2)),
                labels = labels,
            )
        }

        private fun readLabelsFromMetadata(modelBytes: ByteArray): List<String> =
            runCatching {
                ZipInputStream(ByteArrayInputStream(modelBytes)).use { zip ->
                    generateSequence { zip.nextEntry }
                        .firstOrNull { it.name == "metadata.json" }
                        ?.let { zip.readBytes().toString(Charsets.UTF_8) }
                        ?.let(::labelsFromMetadataJson)
                        .orEmpty()
                }
            }.getOrDefault(emptyList())

        private fun labelsFromMetadataJson(json: String): List<String> {
            val names = JSONObject(json).getJSONObject("names")
            return names.keys().asSequence()
                .mapNotNull { key -> key.toIntOrNull()?.let { it to names.getString(key) } }
                .sortedBy { it.first }
                .map { it.second }
                .toList()
        }
    }
}

private fun Bitmap.toYoloInputBuffer(): ByteBuffer {
    val resized = Bitmap.createScaledBitmap(
        this,
        YoloPostProcessor.INPUT_SIZE,
        YoloPostProcessor.INPUT_SIZE,
        true,
    )
    val pixels = IntArray(YoloPostProcessor.INPUT_SIZE * YoloPostProcessor.INPUT_SIZE)
    resized.getPixels(
        pixels,
        0,
        YoloPostProcessor.INPUT_SIZE,
        0,
        0,
        YoloPostProcessor.INPUT_SIZE,
        YoloPostProcessor.INPUT_SIZE,
    )

    return ByteBuffer
        .allocateDirect(4 * pixels.size * 3)
        .order(ByteOrder.nativeOrder())
        .apply {
            pixels.forEach { pixel ->
                putFloat(((pixel shr 16) and 0xFF) / 255f)
                putFloat(((pixel shr 8) and 0xFF) / 255f)
                putFloat((pixel and 0xFF) / 255f)
            }
            rewind()
        }
}

private val fallbackCocoLabels = listOf(
    "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck",
    "boat", "traffic light", "fire hydrant", "stop sign", "parking meter", "bench",
    "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra",
    "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
    "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove",
    "skateboard", "surfboard", "tennis racket", "bottle", "wine glass", "cup",
    "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
    "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
    "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
    "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
    "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
    "hair drier", "toothbrush",
)
