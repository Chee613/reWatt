package com.rewatt.app.data

/**
 * Predicted occupancy for a hall, intended to come from the Prophet model.
 *
 * @param predictedOccupancy expected number of people for the upcoming/next session.
 * @param occupancyLevel coarse label (matches Gemini occupancyHint vocabulary where possible).
 * @param nextSessionStartLabel "HH:mm" of the next scheduled lecture, or null if none.
 * @param source where the forecast came from ("placeholder" until Prophet/Firestore is wired).
 */
data class OccupancyForecast(
    val predictedOccupancy: Int,
    val occupancyLevel: String,
    val nextSessionStartLabel: String?,
    val source: String,
)

interface OccupancyForecastProvider {
    suspend fun forecast(hallLabel: String, nowMillis: Long): OccupancyForecast
}

/**
 * Temporary stand-in until Prophet forecasts are written to / read from Firestore.
 *
 * TODO: replace with a real provider that reads Prophet results from
 *  `rewatt_users/{uid}/rooms/{roomId}/forecast/...`. This is the single swap-in seam — only
 *  [AppGraph] constructs the concrete provider, so the rest of the app is unaffected by the change.
 */
class PlaceholderOccupancyForecastProvider : OccupancyForecastProvider {
    override suspend fun forecast(hallLabel: String, nowMillis: Long): OccupancyForecast =
        OccupancyForecast(
            predictedOccupancy = 40,
            occupancyLevel = "likely_occupied",
            nextSessionStartLabel = "09:00",
            source = "placeholder",
        )
}
