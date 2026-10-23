package com.rewatt.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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

class PlaceholderOccupancyForecastProvider : OccupancyForecastProvider {
    override suspend fun forecast(hallLabel: String, nowMillis: Long): OccupancyForecast =
        OccupancyForecast(
            predictedOccupancy = 40,
            occupancyLevel = "likely_occupied",
            nextSessionStartLabel = "09:00",
            source = "placeholder",
        )
}

/**
 * Reads Prophet forecast results from `rewatt_users/{uid}/rooms/{hallLabel}/forecast/latest`.
 * Falls back to a neutral "no forecast yet" value if the document doesn't exist.
 */
class FirebaseOccupancyForecastProvider : OccupancyForecastProvider {
    private val firestore = FirebaseFirestore.getInstance()
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override suspend fun forecast(hallLabel: String, nowMillis: Long): OccupancyForecast =
        suspendCoroutine { cont ->
            if (uid.isEmpty()) { cont.resume(noForecast()); return@suspendCoroutine }
            firestore.collection("rewatt_users").document(uid)
                .collection("rooms").document(hallLabel)
                .collection("forecast").document("latest")
                .get()
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) { cont.resume(noForecast()); return@addOnSuccessListener }
                    cont.resume(
                        OccupancyForecast(
                            predictedOccupancy = (doc.getLong("predictedOccupancy") ?: 0).toInt(),
                            occupancyLevel = doc.getString("occupancyLevel") ?: "unclear",
                            nextSessionStartLabel = doc.getString("nextSessionStartLabel"),
                            source = "prophet",
                        )
                    )
                }
                .addOnFailureListener { cont.resume(noForecast()) }
        }

    private fun noForecast() = OccupancyForecast(
        predictedOccupancy = 0,
        occupancyLevel = "unclear",
        nextSessionStartLabel = null,
        source = "no_forecast_yet",
    )
}
