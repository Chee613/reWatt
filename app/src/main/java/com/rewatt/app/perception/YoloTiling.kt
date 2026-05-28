package com.rewatt.app.perception

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Geometry helpers for recall-oriented YOLO inference:
 *  - [letterbox]: aspect-preserving pad to a square (no stretch distortion).
 *  - [regionsFor]: full frame + an overlapping 4x4 grid (SAHI-style tiling).
 *  - [remapToFullImage]: map a tile detection back to full-image normalized coords.
 *  - [nms]: cross-tile de-duplication so overlap regions + the full frame don't double-count.
 */

const val TILE_OVERLAP = 0.15f
const val NMS_IOU = 0.5f
const val PAD_GRAY = 114

/** A square model input plus the transform used to build it, so detections can be inverted. */
data class Letterboxed(
    val bitmap: Bitmap,
    val scale: Float,
    val padX: Float,
    val padY: Float,
)

/** A crop rectangle (pixels) within the source image. */
data class Region(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
) {
    val isFullFrame: Boolean get() = left == 0 && top == 0
}

/**
 * Scale [src] preserving aspect ratio and center-pad it onto a [size]x[size] gray canvas.
 * The caller owns the returned bitmap and must recycle it.
 */
fun letterbox(src: Bitmap, size: Int = YoloPostProcessor.INPUT_SIZE, padGray: Int = PAD_GRAY): Letterboxed {
    val scale = size.toFloat() / max(src.width, src.height)
    val newW = (src.width * scale).roundToInt().coerceAtLeast(1)
    val newH = (src.height * scale).roundToInt().coerceAtLeast(1)
    val padX = (size - newW) / 2f
    val padY = (size - newH) / 2f

    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(Color.rgb(padGray, padGray, padGray))
    val scaled = Bitmap.createScaledBitmap(src, newW, newH, true)
    canvas.drawBitmap(scaled, padX, padY, null)
    if (scaled !== src) scaled.recycle()

    return Letterboxed(bitmap = out, scale = scale, padX = padX, padY = padY)
}

/** Tiling grid (cells per side): full frame + 2x2 overlapping cells. */
const val DEFAULT_GRID = 2

/**
 * Full frame plus an overlapping [gridSize]x[gridSize] grid of cells (cells expanded by [TILE_OVERLAP]
 * so people on cell borders aren't cut). Tiling is skipped for small images (long side < 2x inputSize).
 */
fun regionsFor(
    width: Int,
    height: Int,
    inputSize: Int = YoloPostProcessor.INPUT_SIZE,
    gridSize: Int = DEFAULT_GRID,
): List<Region> {
    val full = Region(0, 0, width, height)
    if (gridSize <= 1 || max(width, height) < 2 * inputSize) return listOf(full)

    val cellW = width / gridSize
    val cellH = height / gridSize
    val ovX = (cellW * TILE_OVERLAP).roundToInt()
    val ovY = (cellH * TILE_OVERLAP).roundToInt()

    fun region(l: Int, t: Int, r: Int, b: Int): Region {
        val cl = l.coerceIn(0, width)
        val ct = t.coerceIn(0, height)
        val cr = r.coerceIn(0, width)
        val cb = b.coerceIn(0, height)
        return Region(cl, ct, (cr - cl).coerceAtLeast(1), (cb - ct).coerceAtLeast(1))
    }

    val regions = mutableListOf(full)
    for (row in 0 until gridSize) {
        for (col in 0 until gridSize) {
            val left = col * cellW - ovX
            val top = row * cellH - ovY
            val right = if (col == gridSize - 1) width else (col + 1) * cellW + ovX
            val bottom = if (row == gridSize - 1) height else (row + 1) * cellH + ovY
            regions.add(region(left, top, right, bottom))
        }
    }
    return regions
}

/** Invert the letterbox + region crop to map a tile-space box into full-image normalized coords. */
fun remapToFullImage(
    box: DetectionBox,
    lb: Letterboxed,
    region: Region,
    fullWidth: Int,
    fullHeight: Int,
    inputSize: Int = YoloPostProcessor.INPUT_SIZE,
): DetectionBox {
    val size = inputSize.toFloat()

    fun mapX(nx: Float): Float {
        val regionPx = (nx * size - lb.padX) / lb.scale
        return (region.left + regionPx) / fullWidth
    }
    fun mapY(ny: Float): Float {
        val regionPx = (ny * size - lb.padY) / lb.scale
        return (region.top + regionPx) / fullHeight
    }

    return DetectionBox(
        left = mapX(box.left),
        top = mapY(box.top),
        right = mapX(box.right),
        bottom = mapY(box.bottom),
    ).clamped()
}

fun DetectionBox.iou(other: DetectionBox): Float {
    val interLeft = max(left, other.left)
    val interTop = max(top, other.top)
    val interRight = min(right, other.right)
    val interBottom = min(bottom, other.bottom)
    val interW = (interRight - interLeft).coerceAtLeast(0f)
    val interH = (interBottom - interTop).coerceAtLeast(0f)
    val interArea = interW * interH
    val areaA = (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)
    val areaB = (other.right - other.left).coerceAtLeast(0f) * (other.bottom - other.top).coerceAtLeast(0f)
    val union = areaA + areaB - interArea
    return if (union <= 0f) 0f else interArea / union
}

/** Greedy, class-aware non-maximum suppression; keeps the highest-confidence box per cluster. */
fun nms(detections: List<ObjectDetection>, iouThreshold: Float = NMS_IOU): List<ObjectDetection> {
    val kept = mutableListOf<ObjectDetection>()
    for (candidate in detections.sortedByDescending { it.confidence }) {
        val duplicate = kept.any { it.classId == candidate.classId && it.box.iou(candidate.box) > iouThreshold }
        if (!duplicate) kept.add(candidate)
    }
    return kept
}
