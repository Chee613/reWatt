package com.rewatt.app.perception

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import java.io.Closeable
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Captures and analyzes a single JPEG frame on demand. Unlike a live-preview camera, the camera is
 * bound to the lifecycle only for the duration of one capture and unbound immediately after, so the
 * sensor powers down between the 10-minute marks. Capture timing is driven externally by
 * [CameraCaptureService]'s exact alarm — this class no longer schedules itself.
 */
class CameraCaptureController(
    private val context: Context,
    private val yoloDetectorProvider: () -> YoloDetector?,
    // Publishes the decoded, upright frame immediately so the UI shows it without waiting for the
    // slow tiled YOLO pass. Detection results arrive later via [onFrameAnalyzed].
    private val onFramePreview: (Bitmap, ExposureReading?, String) -> Unit,
    private val onFrameAnalyzed: (Bitmap?, List<ObjectDetection>, String, ExposureReading?, String) -> Unit,
    private val onError: (String) -> Unit,
) : Closeable {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var lifecycleOwner: LifecycleOwner? = null

    @Volatile
    private var closed = false

    @Volatile
    private var captureInFlight = false

    // The phone's current physical rotation, read from the accelerometer so it is correct even when
    // the phone is fixed in a mount with auto-rotate locked. Feeds ImageCapture.targetRotation so a
    // phone mounted 90° anti-clockwise still produces an upright frame for display and detection.
    @Volatile
    private var deviceRotation: Int = Surface.ROTATION_0

    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            deviceRotation = when (orientation) {
                in 45 until 135 -> Surface.ROTATION_270
                in 135 until 225 -> Surface.ROTATION_180
                in 225 until 315 -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }
        }
    }

    /** Provide the [LifecycleOwner] (the service) that CameraX binds the capture use case to. */
    fun attach(owner: LifecycleOwner) {
        lifecycleOwner = owner
        ContextCompat.getMainExecutor(context).execute {
            runCatching { if (orientationListener.canDetectOrientation()) orientationListener.enable() }
        }
    }

    /**
     * Bind the camera, take one picture, analyze it, then unbind so the sensor powers down.
     * Safe to call from any thread; CameraX provider/binding work is marshaled to the main thread.
     */
    fun capture() {
        if (closed || captureInFlight) return
        val owner = lifecycleOwner ?: return
        captureInFlight = true

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                if (closed) {
                    captureInFlight = false
                    return@addListener
                }
                runCatching {
                    val provider = providerFuture.get()
                    val capture = buildImageCapture()
                    provider.unbindAll()
                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, capture)
                    takePicture(provider, capture)
                }.onFailure { error ->
                    captureInFlight = false
                    onError("Camera failed to start: ${error.message ?: error::class.java.simpleName}")
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /** Alias kept for the debug "Capture now" control. */
    fun captureNow() = capture()

    private fun buildImageCapture(): ImageCapture =
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            // Force 4:3 frames at the source so the capture box and detections share that ratio.
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build(),
            )
            .build()
            .also {
                it.flashMode = ImageCapture.FLASH_MODE_OFF
                // There is no display in a headless service, so derive the target rotation from the
                // phone's physical orientation. CameraX writes the matching EXIF tag, and
                // rotateFromExif then produces an upright frame regardless of how the phone is mounted.
                it.targetRotation = deviceRotation
            }

    private fun takePicture(provider: ProcessCameraProvider, capture: ImageCapture) {
        val photoFile = runCatching {
            File.createTempFile("rewatt-exposure-", ".jpg", context.cacheDir)
        }.getOrElse { error ->
            unbind(provider)
            captureInFlight = false
            notifyCaptureFailure("Camera capture failed: ${error.message ?: error::class.java.simpleName}")
            return
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        runCatching {
            capture.takePicture(
                outputOptions,
                analysisExecutor,
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
                            unbind(provider)
                            captureInFlight = false
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        photoFile.delete()
                        unbind(provider)
                        captureInFlight = false
                        notifyCaptureFailure(
                            "Camera image capture failed: ${exception.message ?: exception::class.java.simpleName}",
                        )
                    }
                },
            )
        }.onFailure { error ->
            photoFile.delete()
            unbind(provider)
            captureInFlight = false
            notifyCaptureFailure("Camera image capture failed: ${error.message ?: error::class.java.simpleName}")
        }
    }

    /** Unbind on the main thread so the camera sensor powers down until the next capture. */
    private fun unbind(provider: ProcessCameraProvider) {
        ContextCompat.getMainExecutor(context).execute {
            runCatching { provider.unbindAll() }
        }
    }

    override fun close() {
        closed = true
        lifecycleOwner = null
        runCatching { orientationListener.disable() }
        runCatching {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({ runCatching { future.get().unbindAll() } }, ContextCompat.getMainExecutor(context))
        }
        analysisExecutor.shutdownNow()
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

        // Show the frame right away; detection (tiled inference) can take a few seconds.
        onFramePreview(bitmap, exposureReading, exposureReading.toExposureMessage())

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
