package com.rewatt.app.perception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class YamnetPostProcessorTest {
    @Test
    fun rank_returnsTopScoresWithLabels() {
        val detections = YamnetPostProcessor.rank(
            scores = floatArrayOf(0.01f, 0.80f, 0.20f, 0.60f),
            labels = listOf("Silence", "Speech", "Car", "Computer keyboard"),
            topK = 2,
        )

        assertEquals(2, detections.size)
        assertEquals("Speech", detections[0].label)
        assertTrue(detections[0].indicatesHumanPresence)
        assertEquals("Computer keyboard", detections[1].label)
        assertTrue(detections[1].indicatesHumanPresence)
    }

    @Test
    fun rank_filtersLowConfidenceScores() {
        val detections = YamnetPostProcessor.rank(
            scores = floatArrayOf(0.01f, 0.03f, 0.90f),
            labels = listOf("Silence", "Speech", "Car"),
        )

        assertTrue(detections.isEmpty())
    }

    @Test
    fun rank_keepsWeakSupportSoundsWithoutMarkingPresence() {
        val detections = YamnetPostProcessor.rank(
            scores = floatArrayOf(0.80f, 0.75f, 0.70f),
            labels = listOf("Clicking", "Tap", "Wind"),
        )

        assertEquals(2, detections.size)
        assertEquals("Clicking", detections[0].label)
        assertTrue(detections[0].supportsHumanPresence)
        assertTrue(!detections[0].indicatesHumanPresence)
        assertEquals("Tap", detections[1].label)
    }

    @Test
    fun classMap_parseLabels_handlesQuotedDisplayNames() {
        val labels = YamnetClassMap.parseLabels(
            """
            index,mid,display_name
            0,/m/09x0r,Speech
            1,/m/0ytgt,"Child speech, kid speaking"
            378,/m/0316dw,Typing
            380,/m/01m2v,Computer keyboard
            """.trimIndent(),
        )

        assertEquals("Speech", labels[0])
        assertEquals("Child speech, kid speaking", labels[1])
        assertEquals("Typing", labels[378])
        assertEquals("Computer keyboard", labels[380])
    }

    @Test
    fun classMap_assetMapsImportantActivityClasses() {
        val labels = YamnetClassMap.parseLabels(
            File("src/main/assets/yamnet_class_map.csv").readText(),
        )

        assertEquals("Speech", labels[0])
        assertEquals("Typing", labels[378])
        assertEquals("Computer keyboard", labels[380])
    }
}
