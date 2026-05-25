package com.rewatt.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Resolves the device location (GPS via FusedLocationProviderClient) and fetches outdoor weather
 * from [GoogleWeatherClient], exposing the result as a [WeatherState] StateFlow for the dashboard.
 */
class WeatherRepository(
    private val appContext: Context,
    private val weatherClient: GoogleWeatherClient = GoogleWeatherClient(),
) {
    private val mutableState = MutableStateFlow(WeatherState())
    val weatherState: StateFlow<WeatherState> = mutableState.asStateFlow()

    private val fusedClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(appContext)
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun refresh() {
        if (!weatherClient.isConfigured()) {
            mutableState.update {
                it.copy(
                    status = WeatherStatus.ConfigMissing,
                    message = "Weather API key missing. Add MAPS_WEATHER_API_KEY to local.properties and rebuild.",
                )
            }
            return
        }
        if (!hasLocationPermission()) {
            mutableState.update {
                it.copy(
                    status = WeatherStatus.NeedsPermission,
                    message = "Location permission is required to fetch outdoor weather.",
                )
            }
            return
        }

        mutableState.update { it.copy(status = WeatherStatus.Loading, message = "Fetching outdoor weather.") }

        runCatching {
            val location = resolveLocation() ?: error("Could not resolve the device location.")
            weatherClient.currentConditions(location.latitude, location.longitude)
        }.onSuccess { info ->
            mutableState.update {
                it.copy(
                    status = WeatherStatus.Loaded,
                    message = info.conditionText ?: "Outdoor weather updated.",
                    info = info,
                )
            }
        }.onFailure { error ->
            mutableState.update {
                it.copy(
                    status = WeatherStatus.Failed,
                    message = "Weather fetch failed: ${error.message ?: error::class.java.simpleName}",
                )
            }
        }
    }

    @SuppressLint("MissingPermission") // permission is verified by hasLocationPermission() before calling.
    private suspend fun resolveLocation(): Location? {
        fusedClient.lastLocation.await()?.let { return it }
        val cancellation = CancellationTokenSource()
        return fusedClient
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
            .await()
    }
}

/** Suspends until a Play Services [Task] completes, bridging it into coroutines. */
private suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> if (cont.isActive) cont.resume(result) }
    addOnFailureListener { error -> if (cont.isActive) cont.resumeWithException(error) }
    addOnCanceledListener { if (cont.isActive) cont.resume(null) }
}
