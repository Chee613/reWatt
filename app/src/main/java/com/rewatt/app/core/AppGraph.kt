package com.rewatt.app.core

import android.content.Context
import com.rewatt.app.data.FirebaseSyncRepository
import com.rewatt.app.data.ReWattDatabase
import com.rewatt.app.data.TelemetryRepository

class AppGraph(context: Context) {
    private val appContext = context.applicationContext

    val database: ReWattDatabase by lazy {
        ReWattDatabase.create(appContext)
    }

    val firebaseSyncRepository: FirebaseSyncRepository by lazy {
        FirebaseSyncRepository(appContext)
    }

    val telemetryRepository: TelemetryRepository by lazy {
        TelemetryRepository(
            telemetryDao = database.telemetryDao(),
            firebaseSyncRepository = firebaseSyncRepository,
        )
    }
}
