package com.rewatt.app.data

import com.rewatt.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Thin client for the Google Maps Platform Weather API "currentConditions" endpoint.
 * Mirrors the HttpURLConnection + org.json approach used by [GeminiActivityAnalyzer].
 *
 * Docs: https://developers.google.com/maps/documentation/weather/current-conditions
 */
class GoogleWeatherClient(
    private val apiKey: String = BuildConfig.MAPS_WEATHER_API_KEY,
    private val endpointBaseUrl: String = WEATHER_ENDPOINT_BASE_URL,
) {
    fun isConfigured(): Boolean = apiKey.isNotBlank()

    suspend fun currentConditions(latitude: Double, longitude: Double): WeatherInfo =
        withContext(Dispatchers.IO) {
            require(isConfigured()) { "Google Maps Weather API key is missing." }

            val url = buildString {
                append(endpointBaseUrl)
                append("/currentConditions:lookup")
                append("?key=").append(URLEncoder.encode(apiKey, "UTF-8"))
                append("&location.latitude=").append(latitude)
                append("&location.longitude=").append(longitude)
                append("&unitsSystem=METRIC")
            }

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }

            try {
                val responseCode = connection.responseCode
                val responseBody = if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                }

                if (responseCode !in 200..299) {
                    error("Weather request failed with HTTP $responseCode: ${responseBody.take(240)}")
                }

                parseCurrentConditions(responseBody)
            } finally {
                connection.disconnect()
            }
        }

    companion object {
        private const val WEATHER_ENDPOINT_BASE_URL = "https://weather.googleapis.com/v1"

        fun parseCurrentConditions(responseBody: String): WeatherInfo {
            val root = JSONObject(responseBody)

            val temperatureC = root.optJSONObject("temperature")
                ?.optDouble("degrees", Double.NaN)
                ?.takeIf { !it.isNaN() }

            val humidityPercent = root
                .takeIf { it.has("relativeHumidity") }
                ?.optDouble("relativeHumidity", Double.NaN)
                ?.takeIf { !it.isNaN() }

            val condition = root.optJSONObject("weatherCondition")
            val conditionText = condition?.optJSONObject("description")
                ?.optString("text")
                ?.takeIf { it.isNotBlank() }
            val conditionType = condition?.optString("type")?.takeIf { it.isNotBlank() }

            return WeatherInfo(
                temperatureC = temperatureC,
                humidityPercent = humidityPercent,
                conditionText = conditionText,
                conditionType = conditionType,
            )
        }
    }
}
