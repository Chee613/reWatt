package com.rewatt.app.security

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.rewatt.app.perception.DetectionBox
import com.rewatt.app.perception.YoloDetector

/**
 * YOLO person check for the CCTV door camera.
 *
 * The occupancy model runs at 1920 px so it can count small, distant students; at that scale a person
 * standing close to the camera looks ~3× larger than anything the model was trained on and is missed.
 * So after the normal full-frame pass, the photo is also shrunk to a third of the model input
 * (≈ 640 px, the scale the model was trained at) on a grey canvas, which catches close-range people.
 * Boxes are returned normalised (0..1) to the original photo.
 */
object PersonCheck {
    private const val CLOSE_RANGE_DIVISOR = 3
    private const val LETTERBOX_GREY = 0xFF727272.toInt()

    fun findPersons(yolo: YoloDetector, bitmap: Bitmap): List<DetectionBox> {
        val far = yolo.detect(bitmap, gridSize = 1).filter { it.isPerson }.map { it.box }
        if (far.isNotEmpty()) return far
        return closeRange(yolo, bitmap)
    }

    private fun closeRange(yolo: YoloDetector, bitmap: Bitmap): List<DetectionBox> {
        val size = yolo.inputSize
        val target = size / CLOSE_RANGE_DIVISOR
        val scale = target.toFloat() / maxOf(bitmap.width, bitmap.height)
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)

        val canvasBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        try {
            Canvas(canvasBitmap).apply {
                drawColor(LETTERBOX_GREY)
                drawBitmap(bitmap, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
            }
            return yolo.detect(canvasBitmap, gridSize = 1)
                .filter { it.isPerson }
                .map { d ->
                    // Canvas-normalised → photo-normalised (the photo occupies [0,w]×[0,h] of the canvas).
                    DetectionBox(
                        left = d.box.left * size / w,
                        top = d.box.top * size / h,
                        right = d.box.right * size / w,
                        bottom = d.box.bottom * size / h,
                    ).clamped()
                }
                .filter { it.right > it.left && it.bottom > it.top }
        } finally {
            canvasBitmap.recycle()
        }
    }
}
