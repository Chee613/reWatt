package com.rewatt.app.security

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

/**
 * Warps a face to the standard ArcFace 112×112 crop using a least-squares similarity transform
 * (rotation + uniform scale + translation) from the 5 SCRFD keypoints to the ArcFace template.
 */
object FaceAligner {
    const val SIZE = 112

    // insightface `arcface_dst` for a 112×112 crop: left eye, right eye, nose, left mouth, right mouth.
    private val TEMPLATE = floatArrayOf(
        38.2946f, 51.6963f,
        73.5318f, 51.5014f,
        56.0252f, 71.7366f,
        41.5493f, 92.3655f,
        70.7299f, 92.2041f,
    )

    fun align(source: Bitmap, keypoints: FloatArray): Bitmap {
        val matrix = similarityTransform(keypoints)
        val aligned = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        Canvas(aligned).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return aligned
    }

    /** Closed-form least squares for x' = a·x − b·y + tx, y' = b·x + a·y + ty. */
    private fun similarityTransform(src: FloatArray): Matrix {
        var msx = 0f; var msy = 0f; var mdx = 0f; var mdy = 0f
        for (i in 0 until 5) {
            msx += src[i * 2]; msy += src[i * 2 + 1]
            mdx += TEMPLATE[i * 2]; mdy += TEMPLATE[i * 2 + 1]
        }
        msx /= 5f; msy /= 5f; mdx /= 5f; mdy /= 5f

        var num1 = 0f; var num2 = 0f; var den = 0f
        for (i in 0 until 5) {
            val sx = src[i * 2] - msx
            val sy = src[i * 2 + 1] - msy
            val dx = TEMPLATE[i * 2] - mdx
            val dy = TEMPLATE[i * 2 + 1] - mdy
            num1 += sx * dx + sy * dy
            num2 += sx * dy - sy * dx
            den += sx * sx + sy * sy
        }
        val a = if (den == 0f) 1f else num1 / den
        val b = if (den == 0f) 0f else num2 / den
        val tx = mdx - (a * msx - b * msy)
        val ty = mdy - (b * msx + a * msy)
        return Matrix().apply { setValues(floatArrayOf(a, -b, tx, b, a, ty, 0f, 0f, 1f)) }
    }
}
