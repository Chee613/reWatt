package com.rewatt.app.perception

object YamnetPostProcessor {
    const val DEFAULT_TOP_K = 3

    fun rank(
        scores: FloatArray,
        labels: List<String>,
        topK: Int = DEFAULT_TOP_K,
        confidenceThreshold: Float = 0.05f,
    ): List<SoundDetection> =
        LectureHallPerceptionFilter.filterHumanActivitySounds(
            scores.asSequence()
                .mapIndexed { index, score ->
                    SoundDetection(
                        label = labels.getOrNull(index) ?: "class $index",
                        confidence = score,
                    )
                }
                .filter { it.confidence >= confidenceThreshold }
                .sortedByDescending { it.confidence }
                .toList(),
        ).take(topK)
}
