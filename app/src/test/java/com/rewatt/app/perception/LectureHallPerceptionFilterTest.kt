package com.rewatt.app.perception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LectureHallPerceptionFilterTest {
    @Test
    fun filterYoloDetections_keepsPeopleAndLectureHallContext() {
        val detections = listOf(
            detection(label = "person", classId = 0),
            detection(label = "laptop", classId = 63),
            detection(label = "car", classId = 2),
            detection(label = "book", classId = 73),
        )

        val filtered = LectureHallPerceptionFilter.filterYoloDetections(detections)

        assertEquals(listOf("person", "laptop", "book"), filtered.map { it.label })
        assertEquals(1, filtered.count { it.isPerson })
        assertEquals(2, filtered.count { it.isLectureHallContext })
    }

    @Test
    fun classifyAudioLabel_marksStrongPresenceSounds() {
        val speech = SoundDetection(label = "Speech", confidence = 0.9f)
        val typing = SoundDetection(label = "Typing", confidence = 0.8f)
        val footsteps = SoundDetection(label = "Walk, footsteps", confidence = 0.7f)

        assertTrue(speech.indicatesHumanPresence)
        assertTrue(typing.indicatesHumanPresence)
        assertTrue(footsteps.indicatesHumanPresence)
    }

    @Test
    fun classifyAudioLabel_marksWeakSupportSounds() {
        val clicking = SoundDetection(label = "Clicking", confidence = 0.9f)

        assertTrue(clicking.supportsHumanPresence)
        assertFalse(clicking.indicatesHumanPresence)
    }

    private fun detection(
        label: String,
        classId: Int,
    ): ObjectDetection =
        ObjectDetection(
            label = label,
            confidence = 0.8f,
            classId = classId,
            box = DetectionBox(0f, 0f, 1f, 1f),
        )
}
