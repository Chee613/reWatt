package com.rewatt.app.perception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class YamnetPostProcessorTest {
    @Test
    fun rank_returnsTopScoresWithLabels() {
        val detections = YamnetPostProcessor.rank(
            scores = floatArrayOf(0.01f, 0.80f, 0.20f),
            labels = listOf("Silence", "Speech", "Keyboard"),
            topK = 2,
        )

        assertEquals(2, detections.size)
        assertEquals("Speech", detections[0].label)
        assertEquals("Keyboard", detections[1].label)
    }

    @Test
    fun rank_filtersLowConfidenceScores() {
        val detections = YamnetPostProcessor.rank(
            scores = floatArrayOf(0.01f, 0.03f),
            labels = listOf("Silence", "Speech"),
        )

        assertTrue(detections.isEmpty())
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
        assertEquals("Typing", labels[2])
        assertEquals("Computer keyboard", labels[3])
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
