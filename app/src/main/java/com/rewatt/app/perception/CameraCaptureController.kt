package com.rewatt.app.perception

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraCaptureController(
    private val context: Context,
    private val yoloDetector: YoloDetector?,
    private val onFrameAnalyzed: (Bitmap?, List<ObjectDetection>, String) -> Unit,
    private val onError: (String) -> Unit,
) : Closeable {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var lastInferenceAtMillis = 0L

    fun start(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                runCatching {
                    val provider = providerFuture.get()
                    cameraProvider = provider

                    val preview = Preview.Builder()
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                        .also { imageAnalysis ->
                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                analyzeImage(imageProxy)
                            }
                        }

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }.onFailure { error ->
                    onError("Camera failed to start: ${error.message ?: error::class.java.simpleName}")
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    override fun close() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        cameraExecutor.shutdownNow()
    }

    private fun analyzeImage(imageProxy: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastInferenceAtMillis < INFERENCE_INTERVAL_MILLIS) return
            lastInferenceAtMillis = now

            val bitmap = imageProxy.toBitmapFromRgba()?.rotate(imageProxy.imageInfo.rotationDegrees)
            if (bitmap == null) {
                onFrameAnalyzed(null, emptyList(), "Camera frame could not be converted.")
                return
            }

            val detections = yoloDetector?.detect(bitmap).orEmpty()
            val message = if (yoloDetector == null) {
                "YOLO model is missing."
            } else if (detections.isEmpty()) {
                "No objects above confidence threshold."
            } else {
                "${detections.size} object(s), ${detections.count { it.isPerson }} person(s)."
            }
            onFrameAnalyzed(bitmap, detections, message)
        } catch (error: Throwable) {
            onFrameAnalyzed(null, emptyList(), "YOLO failed: ${error.message ?: error::class.java.simpleName}")
        } finally {
            imageProxy.close()
        }
    }

    companion object {
        private const val INFERENCE_INTERVAL_MILLIS = 5_000L
    }
}

private fun ImageProxy.toBitmapFromRgba(): Bitmap? {
    if (planes.isEmpty()) return null

    val plane = planes[0]
    val width = width
    val height = height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val buffer = plane.buffer
    buffer.rewind()

    if (plane.rowStride == width * 4 && plane.pixelStride == 4) {
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    val rowBuffer = ByteArray(plane.rowStride)
    val bitmapBuffer = java.nio.ByteBuffer.allocate(width * height * 4)
    repeat(height) {
        val rowLength = minOf(plane.rowStride, buffer.remaining())
        buffer.get(rowBuffer, 0, rowLength)
        bitmapBuffer.put(rowBuffer, 0, width * 4)
    }
    bitmapBuffer.rewind()
    bitmap.copyPixelsFromBuffer(bitmapBuffer)
    return bitmap
}

private fun Bitmap.rotate(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

