package com.rewatt.app.core

import android.content.Context
import com.rewatt.app.data.AuthRepository
import com.rewatt.app.data.MqttSyncRepository
import com.rewatt.app.data.ReWattDatabase
import com.rewatt.app.data.TelemetryRepository
import com.rewatt.app.data.WeatherRepository
import com.rewatt.app.perception.PerceptionStateHolder
import com.rewatt.app.security.DeterrentVoice
import com.rewatt.app.security.FaceRecognizer
import com.rewatt.app.security.FamilyFaceStore
import com.rewatt.app.security.IntrusionAlarm
import com.rewatt.app.security.IntrusionNotifier
import com.rewatt.app.security.SecurityEventStore
import com.rewatt.app.security.SecuritySettings
import com.rewatt.app.security.SecurityStateHolder
import com.rewatt.app.security.SecurityTestCheck

class AppGraph(context: Context) {
    private val appContext = context.applicationContext

    val authRepository: AuthRepository by lazy {
        AuthRepository(appContext)
    }

    val database: ReWattDatabase by lazy {
        ReWattDatabase.create(appContext)
    }

    val mqttSyncRepository: MqttSyncRepository by lazy {
        MqttSyncRepository(appContext)
    }

    val telemetryRepository: TelemetryRepository by lazy {
        TelemetryRepository(
            telemetryDao = database.telemetryDao(),
        )
    }

    val weatherRepository: WeatherRepository by lazy {
        WeatherRepository(appContext)
    }

    val perceptionStateHolder: PerceptionStateHolder by lazy {
        PerceptionStateHolder()
    }

    // ── CCTV (security camera) ──
    val securitySettings: SecuritySettings by lazy {
        SecuritySettings(appContext)
    }

    val securityStateHolder: SecurityStateHolder by lazy {
        SecurityStateHolder(initialArmed = securitySettings.armed)
    }

    val familyFaceStore: FamilyFaceStore by lazy {
        FamilyFaceStore(appContext)
    }

    val securityEventStore: SecurityEventStore by lazy {
        SecurityEventStore(appContext)
    }

    val faceRecognizer: FaceRecognizer by lazy {
        FaceRecognizer(appContext, familyFaceStore)
    }

    val intrusionNotifier: IntrusionNotifier by lazy {
        IntrusionNotifier(appContext)
    }

    val deterrentVoice: DeterrentVoice by lazy {
        DeterrentVoice(appContext)
    }

    val securityTestCheck: SecurityTestCheck by lazy {
        SecurityTestCheck(appContext, faceRecognizer, securitySettings, securityStateHolder)
    }

    val intrusionAlarm: IntrusionAlarm by lazy {
        IntrusionAlarm(appContext, securityStateHolder, intrusionNotifier, deterrentVoice)
    }
}
