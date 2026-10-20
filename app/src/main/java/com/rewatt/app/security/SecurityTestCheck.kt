package com.rewatt.app.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.Log
import com.rewatt.app.R
import com.rewatt.app.perception.CameraCaptureController
import com.rewatt.app.perception.DetectionBox
import com.rewatt.app.perception.YoloDetector
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Manual "Test CCTV check": one back-camera photo through the same Lane 2 pipeline as a real event.
 * Faces are checked first (fast); YOLO only runs when no face is visible, to tell "person, no face"
 * from "no person". No alarm, no recording, nothing added to Event history; the annotated photo
 * overwrites cache/security_test.jpg.
 */
class SecurityTestCheck(
    context: Context,
    private val recognizer: FaceRecognizer,
    private val settings: SecuritySettings,
    private val stateHolder: SecurityStateHolder,
) {
    private val outputFile = File(context.applicationContext.cacheDir, "security_test.jpg")

    suspend fun run(controller: CameraCaptureController, yoloProvider: () -> YoloDetector?) {
        if (stateHolder.state.value.activeEventId != null) {
            finish(errorRes = R.string.sec_test_err_busy)
            return
        }
        stateHolder.update { it.copy(testCheck = it.testCheck.copy(countdown = 0, running = true, errorRes = null)) }

        val frame = controller.captureForSecurity()
        if (frame == null) {
            finish(errorRes = R.string.sec_test_err_camera)
            return
        }

        withContext(Dispatchers.Default) {
            try {
                val check = runCatching { recognizer.analyze(frame.bitmap, settings.matchThreshold) }
                    .onFailure { Log.e(TAG, "Face check failed", it) }
                    .getOrNull()
                if (check == null) {
                    finish(errorRes = R.string.sec_test_err_face)
                    return@withContext
                }

                var persons: List<DetectionBox> = emptyList()
                val (result, faces) = when (check) {
                    is FaceCheckResult.AllFamily -> EventResult.FAMILY to check.faces
                    is FaceCheckResult.Stranger -> EventResult.STRANGER to check.faces
                    FaceCheckResult.NoFace -> {
                        val yolo = waitForYolo(yoloProvider)
                        if (yolo == null) {
                            finish(errorRes = R.string.sec_test_err_models)
                            return@withContext
                        }
                        val started = SystemClock.elapsedRealtime()
                        persons = PersonCheck.findPersons(yolo, frame.bitmap)
                        Log.i(TAG, "YOLO found ${persons.size} person(s) in ${SystemClock.elapsedRealtime() - started} ms")
                        (if (persons.isEmpty()) EventResult.NO_PERSON else EventResult.NO_FACE) to emptyList()
                    }
                }
                val decisive = when (result) {
                    EventResult.FAMILY -> faces.maxByOrNull { it.bestSimilarity }
                    EventResult.STRANGER -> faces.filterNot { it.isFamily }.minByOrNull { it.bestSimilarity }
                    else -> null
                }
                val score = decisive?.bestSimilarity
                val confidence = decisive?.confidence

                val annotated = recognizer.annotate(frame.bitmap, faces)
                drawPersons(annotated, persons)
                outputFile.outputStream().use { annotated.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                annotated.recycle()

                stateHolder.update {
                    it.copy(
                        testCheck = TestCheckState(
                            running = false,
                            atMillis = System.currentTimeMillis(),
                            result = result,
                            score = score,
                            confidence = confidence,
                            // Each visible face is a person; YOLO only counts when no face was seen.
                            personCount = if (faces.isNotEmpty()) faces.size else persons.size,
                            faceCount = faces.size,
                            photoPath = outputFile.absolutePath,
                        ),
                    )
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Test check failed", error)
                finish(errorRes = R.string.sec_test_err_face)
            } finally {
                frame.bitmap.recycle()
            }
        }
    }

    private suspend fun waitForYolo(provider: () -> YoloDetector?): YoloDetector? {
        var waited = 0L
        while (waited < YOLO_WAIT_MS) {
            provider()?.let { return it }
            delay(250)
            waited += 250
        }
        return provider()
    }

    /** Person boxes from YOLO (normalised 0..1) in yellow. */
    private fun drawPersons(bitmap: Bitmap, boxes: List<DetectionBox>) {
        if (boxes.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (bitmap.width / 300f).coerceAtLeast(2f)
            color = Color.rgb(250, 204, 21)
        }
        val canvas = Canvas(bitmap)
        for (box in boxes) {
            canvas.drawRect(box.left * bitmap.width, box.top * bitmap.height, box.right * bitmap.width, box.bottom * bitmap.height, paint)
        }
    }

    private fun finish(errorRes: Int) {
        stateHolder.update { it.copy(testCheck = it.testCheck.copy(countdown = 0, running = false, errorRes = errorRes)) }
    }

    companion object {
        private const val TAG = "SecurityTestCheck"
        private const val YOLO_WAIT_MS = 20_000L
    }
}
