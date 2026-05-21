package com.rewatt.app.perception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YoloPostProcessorTest {
    private val labels = listOf("person", "keyboard")

    @Test
    fun process_filtersLowConfidenceRows() {
        val detections = YoloPostProcessor.process(
            rows = arrayOf(
                floatArrayOf(10f, 20f, 100f, 200f, 0.20f, 0f),
                floatArrayOf(20f, 30f, 160f, 240f, 0.90f, 0f),
            ),
            labels = labels,
        )

        assertEquals(1, detections.size)
        assertEquals("person", detections.single().label)
        assertTrue(detections.single().isPerson)
    }

    @Test
    fun process_normalizesPixelBoxes() {
        val detections = YoloPostProcessor.process(
            rows = arrayOf(floatArrayOf(64f, 128f, 320f, 512f, 0.80f, 1f)),
            labels = labels,
        )

        val box = detections.single().box
        assertEquals(0.1f, box.left)
        assertEquals(0.2f, box.top)
        assertEquals(0.5f, box.right)
        assertEquals(0.8f, box.bottom)
    }

    @Test
    fun process_returnsEmptyWhenNoRowsPassThreshold() {
        val detections = YoloPostProcessor.process(
            rows = arrayOf(floatArrayOf(0f, 0f, 1f, 1f, 0.01f, 0f)),
            labels = labels,
        )

        assertTrue(detections.isEmpty())
    }
}

