package com.rewatt.app.core

import android.content.Context
import com.rewatt.app.data.AuthRepository
import com.rewatt.app.data.FirebaseSyncRepository
import com.rewatt.app.data.GeminiActivityAnalyzer
import com.rewatt.app.data.GeminiEnergyAdvisor
import com.rewatt.app.data.GeminiEnergyWasteAnalyzer
import com.rewatt.app.data.FirebaseOccupancyForecastProvider
import com.rewatt.app.data.OccupancyForecastProvider
import com.rewatt.app.data.ReWattDatabase
import com.rewatt.app.data.TelemetryRepository
import com.rewatt.app.data.WeatherRepository
import com.rewatt.app.perception.PerceptionStateHolder

class AppGraph(context: Context) {
    private val appContext = context.applicationContext

    val authRepository: AuthRepository by lazy {
        AuthRepository(appContext)
    }

    val database: ReWattDatabase by lazy {
        ReWattDatabase.create(appContext)
    }

    val firebaseSyncRepository: FirebaseSyncRepository by lazy {
        FirebaseSyncRepository(appContext)
    }

    val geminiActivityAnalyzer: GeminiActivityAnalyzer by lazy {
        GeminiActivityAnalyzer()
    }

    val telemetryRepository: TelemetryRepository by lazy {
        TelemetryRepository(
            telemetryDao = database.telemetryDao(),
            firebaseSyncRepository = firebaseSyncRepository,
            geminiActivityAnalyzer = geminiActivityAnalyzer,
        )
    }

    val weatherRepository: WeatherRepository by lazy {
        WeatherRepository(appContext)
    }

    val occupancyForecastProvider: OccupancyForecastProvider by lazy {
        FirebaseOccupancyForecastProvider()
    }

    val geminiEnergyAdvisor: GeminiEnergyAdvisor by lazy {
        GeminiEnergyAdvisor()
    }

    val geminiEnergyWasteAnalyzer: GeminiEnergyWasteAnalyzer by lazy {
        GeminiEnergyWasteAnalyzer()
    }

    val perceptionStateHolder: PerceptionStateHolder by lazy {
        PerceptionStateHolder()
    }
}
