package com.rewatt.app.data

/**
 * Outdoor weather reading fetched from the Google Maps Platform Weather API.
 * Temperatures are Celsius and humidity is a percentage (0-100). All nullable so the UI can
 * degrade gracefully when a field is missing from the API response.
 */
data class WeatherInfo(
    val temperatureC: Double? = null,
    val humidityPercent: Double? = null,
    val conditionText: String? = null,
    val conditionType: String? = null,
    val fetchedAtMillis: Long = System.currentTimeMillis(),
)

enum class WeatherStatus {
    /** Nothing fetched yet. */
    Idle,

    /** Location permission has not been granted, so we cannot resolve coordinates. */
    NeedsPermission,

    /** MAPS_WEATHER_API_KEY is blank (not set in local.properties). */
    ConfigMissing,

    /** A fetch is in-flight. */
    Loading,

    /** A fetch succeeded; [WeatherState.info] is populated. */
    Loaded,

    /** A fetch failed; [WeatherState.message] explains why. */
    Failed,
}

data class WeatherState(
    val status: WeatherStatus = WeatherStatus.Idle,
    val message: String = "Outdoor weather has not been fetched yet.",
    val info: WeatherInfo? = null,
)
