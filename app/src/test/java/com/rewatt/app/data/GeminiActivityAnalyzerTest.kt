package com.rewatt.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiActivityAnalyzerTest {
    @Test
    fun buildPrompt_usesCameraAndEnvironmentOnly() {
        val prompt = GeminiActivityAnalyzer.buildPrompt(
            PerceptionSyncSummary(
                personCount = 2,
                activityLabels = listOf("Clapping", "Speech", "Computer keyboard", "Typing"),
                temperatureC = 26.5f,
                humidityPercent = 58.2f,
            ),
        )

        assertTrue(prompt.contains("people count from YOLO: 2"))
        assertTrue(prompt.contains("temperature C: 26.5"))
        assertTrue(prompt.contains("humidity percent: 58.2"))
        // Audio (YAMNet) output must never reach the prompt.
        assertTrue(!prompt.contains("YAMNet"))
        assertTrue(!prompt.contains("sound"))
        assertTrue(!prompt.contains("Clapping"))
    }

    @Test
    fun buildRequestBody_requestsJsonOutput() {
        val body = JSONObject(
            GeminiActivityAnalyzer.buildRequestBody(
                PerceptionSyncSummary(activityLabels = listOf("Speech")),
            ),
        )
        val generationConfig = body.getJSONObject("generationConfig")

        assertEquals(0.2, generationConfig.getDouble("temperature"), 0.001)
        assertEquals("application/json", generationConfig.getString("responseMimeType"))
    }

    @Test
    fun parseActivityAnalysisJson_clampsAndNormalizesValues() {
        val analysis = GeminiActivityAnalyzer.parseActivityAnalysisJson(
            """
            ```json
            {"activity":"Lecture session","confidence":1.4,"reason":"Speech and keyboard sounds.","occupancyHint":"occupied"}
            ```
            """.trimIndent(),
        )

        assertEquals("Lecture session", analysis.activity)
        assertEquals(1f, analysis.confidence)
        assertEquals("occupied", analysis.occupancyHint)
    }

    @Test
    fun parseGenerateContentResponse_extractsCandidateText() {
        val analysis = GeminiActivityAnalyzer.parseGenerateContentResponse(
            """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{\"activity\":\"Quiet study\",\"confidence\":0.62,\"reason\":\"Keyboard sounds suggest study.\",\"occupancyHint\":\"likely_occupied\"}"
                      }
                    ]
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("Quiet study", analysis.activity)
        assertEquals(0.62f, analysis.confidence)
        assertEquals("likely_occupied", analysis.occupancyHint)
    }
}
