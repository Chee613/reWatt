package com.rewatt.app.security

import ai.onnxruntime.OrtEnvironment
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import com.rewatt.app.perception.AssetModelLoader

/**
 * InsightFace buffalo_s pipeline: SCRFD detect → 5-point align → MobileFaceNet embed → best cosine
 * similarity against every registered FAMILY sample. Models load lazily on first use and are
 * shared by the CCTV service and the registration screen.
 */
class FaceRecognizer(
    private val context: Context,
    private val familyStore: FamilyFaceStore,
) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var detector: ScrfdFaceDetector? = null
    private var embedder: MobileFaceNetEmbedder? = null

    sealed interface RegistrationResult {
        class Ok(val embedding: FloatArray, val thumbnail: Bitmap) : RegistrationResult
        data object NoFace : RegistrationResult
        data object MultipleFaces : RegistrationResult
        data object TooSmall : RegistrationResult
    }

    @Synchronized
    fun warmUp(): Boolean = runCatching { ensureLoaded() }
        .onFailure { Log.e(TAG, "Face models failed to load", it) }
        .isSuccess

    /** Face check for a CCTV photo. Faces smaller than [MIN_FACE_PX] are ignored (too small to recognise). */
    @Synchronized
    fun analyze(bitmap: Bitmap, threshold: Float): FaceCheckResult {
        ensureLoaded()
        val started = SystemClock.elapsedRealtime()
        val faces = detector!!.detect(bitmap).filter { it.isUsable() }
        if (faces.isEmpty()) return FaceCheckResult.NoFace

        val family = familyStore.samples.value
        val recognized = faces.map { face ->
            val embedding = embedFace(bitmap, face)
            val best = family.maxOfOrNull { cosineSimilarity(embedding, it.embedding) } ?: 0f
            val confidence = MatchCalibration.confidence(best, threshold)
            RecognizedFace(
                face = face,
                bestSimilarity = best,
                confidence = confidence,
                isFamily = confidence >= MatchCalibration.PASS_CONFIDENCE,
            )
        }
        Log.i(
            TAG,
            "Face check: ${recognized.size} face(s), " +
                "cosine/C(s)=${recognized.map { "%.2f/%.0f%%".format(it.bestSimilarity, it.confidence * 100) }}, " +
                "s0=%.3f k=%.2f, ${SystemClock.elapsedRealtime() - started} ms".format(MatchCalibration.s0For(threshold), MatchCalibration.K),
        )
        return if (recognized.all { it.isFamily }) {
            FaceCheckResult.AllFamily(recognized)
        } else {
            FaceCheckResult.Stranger(recognized)
        }
    }

    /** Registration: the photo must contain exactly one usable face. */
    @Synchronized
    fun extractForRegistration(bitmap: Bitmap): RegistrationResult {
        ensureLoaded()
        val all = detector!!.detect(bitmap)
        if (all.isEmpty()) return RegistrationResult.NoFace
        if (all.size > 1) return RegistrationResult.MultipleFaces
        val face = all.first()
        if (!face.isUsable()) return RegistrationResult.TooSmall
        val aligned = FaceAligner.align(bitmap, face.keypoints)
        return RegistrationResult.Ok(embedder!!.embed(aligned), aligned)
    }

    /** Draws face boxes (green = FAMILY, red = unknown/masked) on a copy scaled to [maxSide]. */
    fun annotate(bitmap: Bitmap, faces: List<RecognizedFace>, maxSide: Int = 1600): Bitmap {
        val scaled = bitmap.scaledToMaxSide(maxSide)
        val out = if (scaled === bitmap) bitmap.copy(Bitmap.Config.ARGB_8888, true) else scaled
        val k = out.width.toFloat() / bitmap.width
        val canvas = Canvas(out)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (out.width / 250f).coerceAtLeast(3f)
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (out.width / 40f).coerceAtLeast(18f)
            isFakeBoldText = true
        }
        for (face in faces) {
            val color = if (face.isFamily) Color.rgb(22, 163, 74) else Color.rgb(220, 38, 38)
            stroke.color = color
            text.color = color
            val r = RectF(face.face.box.left * k, face.face.box.top * k, face.face.box.right * k, face.face.box.bottom * k)
            canvas.drawRect(r, stroke)
            val label = "${if (face.isFamily) FamilyFaceStore.FAMILY_LABEL else "UNKNOWN"} %.2f · %.0f%%"
                .format(face.bestSimilarity, face.confidence * 100)
            canvas.drawText(label, r.left, (r.top - text.textSize / 3f).coerceAtLeast(text.textSize), text)
        }
        return out
    }

    private fun embedFace(bitmap: Bitmap, face: DetectedFace): FloatArray {
        val aligned = FaceAligner.align(bitmap, face.keypoints)
        return embedder!!.embed(aligned).also { aligned.recycle() }
    }

    private fun DetectedFace.isUsable(): Boolean =
        box.width() >= MIN_FACE_PX && box.height() >= MIN_FACE_PX

    private fun ensureLoaded() {
        if (detector != null && embedder != null) return
        val started = SystemClock.elapsedRealtime()
        detector = ScrfdFaceDetector(env, AssetModelLoader.loadBytes(context, DETECTOR_ASSET))
        embedder = MobileFaceNetEmbedder(env, AssetModelLoader.loadBytes(context, EMBEDDER_ASSET))
        Log.i(TAG, "buffalo_s loaded in ${SystemClock.elapsedRealtime() - started} ms")
    }

    companion object {
        private const val TAG = "FaceRecognizer"
        const val DETECTOR_ASSET = "face/det_500m.onnx"
        const val EMBEDDER_ASSET = "face/w600k_mbf.onnx"

        /** Faces narrower than this (in the ~2000 px security photo) are too small to recognise reliably. */
        const val MIN_FACE_PX = 48f
    }
}
