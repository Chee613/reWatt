package com.rewatt.app.perception

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class CameraCaptureController(
    private val context: Context,
    private val yoloDetectorProvider: () -> YoloDetector?,
    private val onFrameAnalyzed: (Bitmap?, List<ObjectDetection>, String, ExposureReading?, String) -> Unit,
    private val onError: (String) -> Unit,
) : Closeable {
    private val cameraExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var captureLoop: ScheduledFuture<*>? = null

    @Volatile
    private var closed = false

    @Volatile
    private var captureInFlight = false

    fun start(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                if (closed) return@addListener
                runCatching {
                    val provider = providerFuture.get()
                    cameraProvider = provider

                    val preview = Preview.Builder()
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                        .also { it.flashMode = ImageCapture.FLASH_MODE_OFF }
                    imageCapture = capture

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture,
                    )
                    startCaptureLoop()
                }.onFailure { error ->
                    onError("Camera failed to start: ${error.message ?: error::class.java.simpleName}")
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    override fun close() {
        closed = true
        captureLoop?.cancel(true)
        captureLoop = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        imageCapture = null
        cameraExecutor.shutdownNow()
    }

    private fun startCaptureLoop() {
        captureLoop?.cancel(false)
        // Align the first capture to the next 10-minute wall-clock mark (:00/:10/:20...),
        // then keep firing every interval. scheduleAtFixedRate keeps the cadence anchored.
        captureLoop = cameraExecutor.scheduleAtFixedRate(
            { captureJpegFrame() },
            millisUntilNextBoundary(INFERENCE_INTERVAL_MINUTES),
            INFERENCE_INTERVAL_MILLIS,
            TimeUnit.MILLISECONDS,
        )
    }

    /** Debug bypass: capture and analyze one frame immediately, ignoring the interval. */
    fun captureNow() {
        if (closed) return
        cameraExecutor.execute { captureJpegFrame() }
    }

    private fun captureJpegFrame() {
        if (closed || captureInFlight) return
        val capture = imageCapture ?: return
        val photoFile = runCatching {
            File.createTempFile("rewatt-exposure-", ".jpg", context.cacheDir)
        }.getOrElse { error ->
            notifyCaptureFailure("Camera capture failed: ${error.message ?: error::class.java.simpleName}")
            return
        }

        captureInFlight = true
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        runCatching {
            capture.takePicture(
                outputOptions,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        try {
                            analyzeSavedImage(photoFile)
                        } catch (error: Throwable) {
                            notifyCaptureFailure(
                                "Camera analysis failed: ${error.message ?: error::class.java.simpleName}",
                            )
                        } finally {
                            photoFile.delete()
                            captureInFlight = false
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        photoFile.delete()
                        captureInFlight = false
                        notifyCaptureFailure(
                            "Camera image capture failed: ${exception.message ?: exception::class.java.simpleName}",
                        )
                    }
                },
            )
        }.onFailure { error ->
            photoFile.delete()
            captureInFlight = false
            notifyCaptureFailure("Camera image capture failed: ${error.message ?: error::class.java.simpleName}")
        }
    }

    private fun analyzeSavedImage(photoFile: File) {
        if (closed) return
        val exposureReading = runCatching {
            ExposureValueCalculator.fromExif(photoFile.absolutePath)
        }.getOrNull()
        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
            ?.rotateFromExif(photoFile.absolutePath)
        if (bitmap == null) {
            onFrameAnalyzed(
                null,
                emptyList(),
                "Camera frame could not be decoded.",
                exposureReading,
                exposureReading.toExposureMessage(),
            )
            return
        }

        val yoloDetector = yoloDetectorProvider()
        val detections = yoloDetector?.detect(bitmap).orEmpty()
        val personCount = detections.count { it.isPerson }
        val message = if (yoloDetector == null) {
            "Captured frame; YOLO is still loading."
        } else if (detections.isEmpty()) {
            "No people detected above the confidence threshold."
        } else {
            "People detected: $personCount."
        }
        onFrameAnalyzed(
            bitmap,
            detections,
            message,
            exposureReading,
            exposureReading.toExposureMessage(),
        )
    }

    private fun notifyCaptureFailure(message: String) {
        if (!closed) {
            onFrameAnalyzed(null, emptyList(), message, null, "Exposure data unavailable.")
        }
    }

    companion object {
        private const val INFERENCE_INTERVAL_MINUTES = 10
        private const val INFERENCE_INTERVAL_MILLIS = INFERENCE_INTERVAL_MINUTES * 60 * 1_000L
    }
}

private fun ExposureReading?.toExposureMessage(): String =
    this?.let {
        "EV %.1f; lights %s.".format(it.ev, if (it.lightsOn) "on" else "off")
    } ?: "Exposure data unavailable."

private fun Bitmap.rotateFromExif(imagePath: String): Bitmap {
    val orientation = runCatching {
        ExifInterface(imagePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
    return rotate(degrees)
}

private fun Bitmap.rotate(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
