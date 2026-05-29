package com.rewatt.app.perception

object LectureHallPerceptionFilter {
    private val strongAudioLabels = setOf(
        "speech",
        "child speech, kid speaking",
        "conversation",
        "narration, monologue",
        "whispering",
        "laughter",
        "chuckle, chortle",
        "singing",
        "humming",
        "cough",
        "throat clearing",
        "sneeze",
        "walk, footsteps",
        "hands",
        "clapping",
        "cheering",
        "applause",
        "chatter",
        "crowd",
        "hubbub, speech noise, speech babble",
        "typing",
        "computer keyboard",
        "writing",
    )

    private val weakAudioLabels = setOf(
        "keys jangling",
        "knock",
        "tap",
        "clicking",
    )

    fun filterYoloDetections(detections: List<ObjectDetection>): List<ObjectDetection> =
        detections.filter { detection -> detection.isPerson }

    fun classifyAudioLabel(label: String): AudioPresenceLevel =
        when (label.normalized()) {
            in strongAudioLabels -> AudioPresenceLevel.Strong
            in weakAudioLabels -> AudioPresenceLevel.Weak
            else -> AudioPresenceLevel.None
        }

    fun filterHumanActivitySounds(detections: List<SoundDetection>): List<SoundDetection> =
        detections.mapNotNull { detection ->
            val level = classifyAudioLabel(detection.label)
            if (level == AudioPresenceLevel.None) null else detection.copy(presenceLevel = level)
        }

    private fun String.normalized(): String =
        trim().lowercase()
}
