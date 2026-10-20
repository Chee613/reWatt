package com.rewatt.app.security

import kotlin.math.exp
import kotlin.math.ln

/**
 * Logistic calibration of the Buffalo_S matcher score (cosine similarity of L2-normalised
 * MobileFaceNet embeddings — not the SCRFD detection score):
 *
 *   C(s) = 1 / (1 + exp(-k · (s − s0)))
 *
 * A face is FAMILY when C(s) ≥ [PASS_CONFIDENCE]. The Security tab's raw-cosine slider sets the
 * cosine value at which C reaches [PASS_CONFIDENCE], so s0 = slider − ln(P / (1 − P)) / k.
 * With the default slider 0.40 this gives s0 = 0.30, k = 17.35.
 */
object MatchCalibration {
    const val K = 17.35f
    const val PASS_CONFIDENCE = 0.85f

    private val passLogit: Float = ln(PASS_CONFIDENCE / (1f - PASS_CONFIDENCE))

    /** Midpoint s0 (50% confidence) for a raw-cosine pass threshold. */
    fun s0For(rawThreshold: Float): Float = rawThreshold - passLogit / K

    fun confidence(similarity: Float, rawThreshold: Float): Float =
        1f / (1f + exp(-K * (similarity - s0For(rawThreshold))))

    fun isFamily(similarity: Float, rawThreshold: Float): Boolean =
        confidence(similarity, rawThreshold) >= PASS_CONFIDENCE
}
