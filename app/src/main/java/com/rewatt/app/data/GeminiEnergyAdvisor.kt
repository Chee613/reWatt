package com.rewatt.app.data

import com.rewatt.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Inputs fed to Gemini to derive an AC energy-optimization recommendation. */
data class EnergyAdviceInput(
    val deltaTC: Double?,
    val indoorTempC: Double?,
    val outdoorTempC: Double?,
    val indoorHumidity: Double?,
    val outdoorHumidity: Double?,
    val occupancy: OccupancyForecast,
    val conditionText: String?,
)

data class EnergyAdvice(
    val acAction: String,
    val recommendedAcStartTime: String?,
    val targetSetpointC: Double?,
    val reason: String,
    val confidence: Float,
)

enum class EnergyAdviceStatus {
    ConfigMissing,
    Idle,
    Pending,
    Complete,
    Failed,
}

data class EnergyAdviceState(
    val status: EnergyAdviceStatus = EnergyAdviceStatus.Idle,
    val message: String = "Tap Suggest action to get an AC energy recommendation.",
    val advice: EnergyAdvice? = null,
    val occupancy: OccupancyForecast? = null,
    val lastAdvisedAtMillis: Long? = null,
)

/**
 * Calls Gemini to recommend AC actions for energy optimization, given temperature deltaT, humidity,
 * and predicted occupancy. Mirrors [GeminiActivityAnalyzer]'s HttpURLConnection + org.json approach
 * and reuses the same GEMINI_API_KEY.
 */
class GeminiEnergyAdvisor(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val endpointBaseUrl: String = GEMINI_ENDPOINT_BASE_URL,
) {
    fun isConfigured(): Boolean = apiKey.isNotBlank()

    suspend fun advise(input: EnergyAdviceInput): EnergyAdvice =
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
                    output.write(buildRequestBody(input).toByteArray(Charsets.UTF_8))
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
        const val MODEL_ID = "gemini-2.5-flash"
        private const val GEMINI_ENDPOINT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        fun buildRequestBody(input: EnergyAdviceInput): String =
            JSONObject()
                .put(
                    "contents",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", buildPrompt(input))),
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

        fun buildPrompt(input: EnergyAdviceInput): String =
            listOf(
                "You are an HVAC energy-optimization assistant for a tropical-climate lecture hall.",
                "The air-conditioner only COOLS (never heats). Goal: minimize energy use while keeping",
                "occupants comfortable when a class is present.",
                "",
                "Inputs:",
                "- outdoor temperature C: ${input.outdoorTempC?.toString() ?: "unknown"}",
                "- indoor temperature C (room sensor): ${input.indoorTempC?.toString() ?: "unknown"}",
                "- deltaT C (outdoor minus indoor): ${input.deltaTC?.toString() ?: "unknown"}",
                "- indoor relative humidity percent: ${input.indoorHumidity?.toString() ?: "unknown"}",
                "- outdoor relative humidity percent: ${input.outdoorHumidity?.toString() ?: "unknown"}",
                "- outdoor condition: ${input.conditionText ?: "unknown"}",
                "- predicted occupancy (people): ${input.occupancy.predictedOccupancy}",
                "- occupancy level: ${input.occupancy.occupancyLevel}",
                "- next scheduled lecture start (HH:mm): ${input.occupancy.nextSessionStartLabel ?: "unknown"}",
                "- occupancy data source: ${input.occupancy.source}",
                "",
                "Guidelines (balance comfort vs energy; do not over-cool):",
                "- High deltaT (outdoor much hotter than indoor): heat leaks in faster, so start AC EARLIER",
                "  before the lecture to pre-cool (e.g. ~40 min before).",
                "- Low or negative deltaT: little cooling load, so start AC LATER, closer to the lecture",
                "  start (e.g. ~10 min before).",
                "- High indoor humidity: it feels hotter, so lower the setpoint slightly / favor dehumidify.",
                "- Low indoor humidity: raise the setpoint slightly to save energy (power-saving).",
                "- If the room is empty or likely empty, do NOT pre-cool; keep AC off.",
                "",
                "Return only JSON with keys:",
                "acAction (short imperative, e.g. 'Pre-cool now', 'Start AC at 08:20', 'Raise setpoint', 'Keep AC off'),",
                "recommendedAcStartTime (HH:mm or null),",
                "targetSetpointC (number, e.g. 24, or null),",
                "reason (one concise sentence),",
                "confidence (0.0 to 1.0).",
            ).joinToString("\n")

        fun parseGenerateContentResponse(responseBody: String): EnergyAdvice {
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
            return parseEnergyAdviceJson(text)
        }

        fun parseEnergyAdviceJson(text: String): EnergyAdvice {
            val cleaned = text
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val json = JSONObject(cleaned)

            val startTime = json.optString("recommendedAcStartTime")
                .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            val setpoint = json.optDouble("targetSetpointC", Double.NaN).takeIf { !it.isNaN() }

            return EnergyAdvice(
                acAction = json.optString("acAction").ifBlank { "No action suggested" },
                recommendedAcStartTime = startTime,
                targetSetpointC = setpoint,
                reason = json.optString("reason").ifBlank { "Gemini returned no explanation." },
                confidence = json.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f),
            )
        }
    }
}
