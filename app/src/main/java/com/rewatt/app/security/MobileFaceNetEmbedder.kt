package com.rewatt.app.security

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import java.io.Closeable
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * InsightFace buffalo_s recogniser (MobileFaceNet `w600k_mbf`, ONNX): aligned 112×112 RGB face,
 * `(x - 127.5) / 127.5`, → L2-normalised 512-d embedding. Cosine similarity = dot product.
 */
class MobileFaceNetEmbedder(
    private val env: OrtEnvironment,
    modelBytes: ByteArray,
) : Closeable {
    private val session: OrtSession = env.createSession(
        modelBytes,
        OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) },
    )
    private val inputName: String = session.inputNames.first()

    fun embed(alignedFace: Bitmap): FloatArray {
        val size = FaceAligner.SIZE
        val area = size * size
        val pixels = IntArray(area)
        alignedFace.getPixels(pixels, 0, size, 0, 0, size, size)
        val data = FloatArray(3 * area)
        for (i in 0 until area) {
            val p = pixels[i]
            data[i] = (((p shr 16) and 0xFF) - 127.5f) / 127.5f
            data[area + i] = (((p shr 8) and 0xFF) - 127.5f) / 127.5f
            data[2 * area + i] = ((p and 0xFF) - 127.5f) / 127.5f
        }
        val embedding = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, size.toLong(), size.toLong())).use { input ->
            session.run(mapOf(inputName to input)).use { outputs ->
                val tensor = outputs.get(0) as OnnxTensor
                val buffer = tensor.floatBuffer
                FloatArray(buffer.remaining()).also { buffer.get(it) }
            }
        }
        return embedding.l2Normalized()
    }

    override fun close() {
        session.close()
    }
}

internal fun FloatArray.l2Normalized(): FloatArray {
    var sum = 0f
    for (v in this) sum += v * v
    val norm = sqrt(sum).takeIf { it > 0f } ?: return this
    return FloatArray(size) { this[it] / norm }
}

internal fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    var dot = 0f
    for (i in 0 until minOf(a.size, b.size)) dot += a[i] * b[i]
    return dot
}
