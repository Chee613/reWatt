package com.rewatt.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
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
import kotlinx.coroutines.withTimeoutOrNull

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
        // 1. Fast path: cached last-known location. Bounded by a timeout so a stuck Play Services
        //    call can never hang the whole weather fetch (the previous code had no timeout here).
        val last = withTimeoutOrNull(LAST_LOCATION_TIMEOUT_MS) {
            runCatching { fusedClient.lastLocation.await() }.getOrNull()
        }
        if (last != null) return last

        // 2. Play Services returned no cached fix — fall back to the platform LocationManager's
        //    last-known location, which often still holds a recent GPS fix (confirmed via
        //    `dumpsys location`) even when FusedLocationProviderClient.lastLocation is null.
        platformLastKnownLocation()?.let { return it }

        // 3. No cached fix anywhere: request an active one. PRIORITY_HIGH_ACCURACY uses GPS, which is the
        //    only provider enabled on some devices; bounded by a timeout so we fail fast instead
        //    of hanging forever when no fix is obtainable (e.g. indoors).
        val cancellation = CancellationTokenSource()
        val current = withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MS) {
            runCatching {
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token).await()
            }.getOrNull()
        }
        if (current == null) cancellation.cancel()
        return current
    }

    /** Most recent last-known location across the platform providers (gps/network/passive). */
    @SuppressLint("MissingPermission") // permission is verified by hasLocationPermission() before calling.
    private fun platformLastKnownLocation(): Location? {
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
            .mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    companion object {
        private const val LAST_LOCATION_TIMEOUT_MS = 4_000L
        private const val CURRENT_LOCATION_TIMEOUT_MS = 20_000L
    }
}

/** Suspends until a Play Services [Task] completes, bridging it into coroutines. */
private suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> if (cont.isActive) cont.resume(result) }
    addOnFailureListener { error -> if (cont.isActive) cont.resumeWithException(error) }
    addOnCanceledListener { if (cont.isActive) cont.resume(null) }
}
