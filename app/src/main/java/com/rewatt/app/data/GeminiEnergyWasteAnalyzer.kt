package com.rewatt.app.data

import com.rewatt.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Measured waste for a single day, fed to Gemini to estimate the room's energy cost. */
data class EnergyWasteInput(
    val roomName: String,
    val capacity: Int?,
    /** Minutes the room was empty while the lights/AC were still on (10 min per missed capture). */
    val wastedMinutes: Int,
    val tariffRmPerKwh: Double = DEFAULT_TARIFF_RM_PER_KWH,
) {
    companion object {
        // Approx. Malaysian commercial tariff; used so Gemini's RM figure is grounded.
        const val DEFAULT_TARIFF_RM_PER_KWH = 0.509
    }
}

/** Gemini's inferred room composition + the estimated wasted energy and cost. */
data class EnergyWasteAnalysis(
    val roomType: String,
    val acUnits: Int,
    val lightbulbs: Int,
    val wastedKwh: Double,
    val costRm: Double,
    val summary: String,
)

/**
 * Calls Gemini to turn a day's measured "empty room with the lights/AC on" minutes into an energy
 * estimate. Gemini infers the room type, number of AC units and lightbulbs from the room name and
 * capacity (e.g. "DK1, 300 pax"), then computes the wasted kWh and RM cost. Mirrors
 * [GeminiEnergyAdvisor]'s HttpURLConnection + org.json approach and reuses the same GEMINI_API_KEY.
 */
class GeminiEnergyWasteAnalyzer(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val endpointBaseUrl: String = GEMINI_ENDPOINT_BASE_URL,
) {
    fun isConfigured(): Boolean = apiKey.isNotBlank()

    suspend fun analyze(input: EnergyWasteInput): EnergyWasteAnalysis =
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
        const val MODEL_ID = "gemini-3.5-flash"
        private const val GEMINI_ENDPOINT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        fun buildRequestBody(input: EnergyWasteInput): String =
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

        fun buildPrompt(input: EnergyWasteInput): String =
            listOf(
                "You estimate wasted electricity for a university lecture hall in Malaysia (tropical).",
                "Yesterday the room was detected EMPTY but with the lights and air-conditioning still",
                "left ON for a total of ${input.wastedMinutes} minutes (sensed in 10-minute samples).",
                "",
                "Room details:",
                "- room name: ${input.roomName}",
                "- seating capacity (pax): ${input.capacity?.toString() ?: "unknown"}",
                "- electricity tariff: RM ${input.tariffRmPerKwh} per kWh",
                "",
                "From the room name and capacity, infer the room type, how many split/cassette AC",
                "units it would have, and how many ceiling lightbulbs/tubes. Assume a typical AC unit",
                "draws ~1.5 kW and a tube/bulb ~0.036 kW. Compute the energy wasted while the room sat",
                "empty with everything on, using the wasted minutes above, then the cost in RM.",
                "",
                "Return only JSON with keys:",
                "roomType (short string, e.g. 'large lecture hall'),",
                "acUnits (integer),",
                "lightbulbs (integer),",
                "wastedKwh (number, rounded to 2 decimals),",
                "costRm (number, rounded to 2 decimals),",
                "summary (one concise sentence a facilities manager would read).",
            ).joinToString("\n")

        fun parseGenerateContentResponse(responseBody: String): EnergyWasteAnalysis {
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
            return parseWasteJson(text)
        }

        fun parseWasteJson(text: String): EnergyWasteAnalysis {
            val cleaned = text
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val json = JSONObject(cleaned)
            return EnergyWasteAnalysis(
                roomType = json.optString("roomType").ifBlank { "lecture hall" },
                acUnits = json.optInt("acUnits", 0),
                lightbulbs = json.optInt("lightbulbs", 0),
                wastedKwh = json.optDouble("wastedKwh", 0.0),
                costRm = json.optDouble("costRm", 0.0),
                summary = json.optString("summary").ifBlank { "No summary returned." },
            )
        }
    }
}
