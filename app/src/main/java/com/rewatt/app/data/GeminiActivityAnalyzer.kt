package com.rewatt.app.data

import com.rewatt.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GeminiActivityAnalyzer(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val endpointBaseUrl: String = GEMINI_ENDPOINT_BASE_URL,
) {
    fun isConfigured(): Boolean = apiKey.isNotBlank()

    suspend fun analyze(summary: PerceptionSyncSummary): ActivityAnalysis =
        withContext(Dispatchers.IO) {
            require(isConfigured()) { "Gemini API key is missing." }

            val connection = (URL("$endpointBaseUrl/$MODEL_ID:generateContent").openConnection() as HttpURLConnection)
                .apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("x-goog-api-key", apiKey)
                }

            try {
                connection.outputStream.use { output ->
                    output.write(buildRequestBody(summary).toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                val responseBody = if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                }

                if (responseCode !in 200..299) {
                    error("Gemini request failed with HTTP $responseCode: ${responseBody.take(240)}")
                }

                parseGenerateContentResponse(responseBody)
            } finally {
                connection.disconnect()
            }
        }

    companion object {
        const val MODEL_ID = "gemini-3.5-flash"
        private const val GEMINI_ENDPOINT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private val allowedOccupancyHints = setOf(
            "occupied",
            "likely_occupied",
            "unclear",
            "likely_empty",
        )

        fun buildRequestBody(summary: PerceptionSyncSummary): String =
            JSONObject()
                .put(
                    "contents",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", buildPrompt(summary))),
                            ),
                    ),
                )
                .put(
                    "generationConfig",
                    JSONObject()
                        .put("temperature", 0.2)
                        .put("responseMimeType", "application/json"),
                )
                .toString()

        fun buildPrompt(summary: PerceptionSyncSummary): String =
            listOf(
                "You classify lecture hall activity from sensor summaries.",
                "",
                "Inputs:",
                "- people count from YOLO: ${summary.personCount}",
                "- top 3 YAMNet sound labels: ${summary.activityLabels.take(3).joinToString(", ").ifBlank { "none" }}",
                "- temperature C: ${summary.temperatureC?.toString() ?: "unknown"}",
                "- humidity percent: ${summary.humidityPercent?.toString() ?: "unknown"}",
                "",
                "Return only JSON with:",
                "activity, confidence, reason, occupancyHint.",
                "",
                "Allowed occupancyHint values: occupied, likely_occupied, unclear, likely_empty.",
                "Prefer practical room activities such as Lecture session, Group discussion, Quiet study, Cleaning/maintenance, Empty/idle, Unknown.",
            ).joinToString("\n")

        fun parseGenerateContentResponse(responseBody: String): ActivityAnalysis {
            val root = JSONObject(responseBody)
            val parts = root
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
            val text = (0 until (parts?.length() ?: 0))
                .joinToString(separator = "") { index ->
                    parts?.optJSONObject(index)?.optString("text").orEmpty()
                }
                .trim()

            require(text.isNotBlank()) { "Gemini response did not include text." }
            return parseActivityAnalysisJson(text)
        }

        fun parseActivityAnalysisJson(text: String): ActivityAnalysis {
            val cleaned = text
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val json = JSONObject(cleaned)
            val occupancyHint = json.optString("occupancyHint", "unclear")
                .takeIf { it in allowedOccupancyHints }
                ?: "unclear"

            return ActivityAnalysis(
                activity = json.optString("activity").ifBlank { "Unknown" },
                confidence = json.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f),
                reason = json.optString("reason").ifBlank { "Gemini returned no explanation." },
                occupancyHint = occupancyHint,
            )
        }
    }
}
