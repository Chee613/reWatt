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
    // The model's expected square input size (e.g. 640 or 1280), read from its input tensor so
    // models of different resolutions can be swapped via the dropdown without code changes.
    private val inputSize: Int =
        interpreter.getInputTensor(0).shape().let { shape ->
            shape.getOrNull(1)?.takeIf { it > 0 } ?: YoloPostProcessor.INPUT_SIZE
        }

    fun detect(bitmap: Bitmap, gridSize: Int = DEFAULT_GRID): List<ObjectDetection> {
        val fullWidth = bitmap.width
        val fullHeight = bitmap.height

        // Full frame + grid cells (SAHI-style), each letterboxed to the model's square input.
        val merged = regionsFor(fullWidth, fullHeight, inputSize, gridSize).flatMap { region ->
            val crop = if (region.isFullFrame && region.width == fullWidth && region.height == fullHeight) {
                bitmap
            } else {
                Bitmap.createBitmap(bitmap, region.left, region.top, region.width, region.height)
            }
            val letterboxed = letterbox(crop, inputSize)
            try {
                runOnSquare(letterboxed.bitmap).map { detection ->
                    detection.copy(
                        box = remapToFullImage(detection.box, letterboxed, region, fullWidth, fullHeight, inputSize),
                    )
                }
            } finally {
                letterboxed.bitmap.recycle()
                if (crop !== bitmap) crop.recycle()
            }
        }

        // De-duplicate across the full frame + overlapping cells before the label filter.
        return LectureHallPerceptionFilter.filterYoloDetections(nms(merged))
    }

    /** Run the interpreter on an already-square (inputSize) bitmap; boxes are normalized to it. */
    private fun runOnSquare(square: Bitmap): List<ObjectDetection> {
        val output = Array(1) { Array(300) { FloatArray(6) } }
        interpreter.run(square.toYoloInputBuffer(inputSize), output)
        return YoloPostProcessor.process(output[0], labels, inputSize = inputSize)
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        const val MODEL_ASSET = "yolo26n_float16.tflite"

        fun create(context: Context, assetName: String = MODEL_ASSET): YoloDetector {
            val modelBytes = AssetModelLoader.loadBytes(context, assetName)
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

// Encodes an already-square [size] bitmap (produced by letterbox) as an RGB float32 [0,1] buffer.
private fun Bitmap.toYoloInputBuffer(size: Int): ByteBuffer {
    val square = if (width == size && height == size) this else Bitmap.createScaledBitmap(this, size, size, true)
    val pixels = IntArray(size * size)
    square.getPixels(pixels, 0, size, 0, 0, size, size)
    if (square !== this) square.recycle()

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
