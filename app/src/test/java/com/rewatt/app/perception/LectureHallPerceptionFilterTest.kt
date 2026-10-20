package com.rewatt.app.perception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LectureHallPerceptionFilterTest {
    @Test
    fun filterYoloDetections_keepsOnlyPeople() {
        val detections = listOf(
            detection(label = "person", classId = 0),
            detection(label = "laptop", classId = 63),
            detection(label = "car", classId = 2),
            detection(label = "book", classId = 73),
        )

        val filtered = LectureHallPerceptionFilter.filterYoloDetections(detections)

        assertEquals(listOf("person"), filtered.map { it.label })
        assertEquals(1, filtered.count { it.isPerson })
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
