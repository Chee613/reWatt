package com.rewatt.app.core

import android.content.Context
import android.net.Uri
import com.rewatt.app.data.AuthRepository
import com.rewatt.app.data.MqttSyncRepository
import com.rewatt.app.data.ReWattDatabase
import com.rewatt.app.data.TelemetryRepository
import com.rewatt.app.data.WeatherRepository
import com.rewatt.app.energy.EnergyDemo
import com.rewatt.app.energy.EnergyStateHolder
import com.rewatt.app.perception.LivePreviewHub
import com.rewatt.app.perception.PerceptionStateHolder
import com.rewatt.app.safety.FireAlarm
import com.rewatt.app.safety.FireMonitor
import com.rewatt.app.safety.GasFireDemo
import com.rewatt.app.safety.TorchRouter
import com.rewatt.app.security.AlarmOutputs
import com.rewatt.app.security.DeterrentVoice
import com.rewatt.app.security.FaceRecognizer
import com.rewatt.app.security.FamilyFaceStore
import com.rewatt.app.security.IntrusionAlarm
import com.rewatt.app.security.IntrusionNotifier
import com.rewatt.app.security.SecurityEventStore
import com.rewatt.app.security.SecuritySettings
import com.rewatt.app.security.SecurityStateHolder
import com.rewatt.app.security.SecurityTestCheck
import kotlinx.coroutines.flow.MutableStateFlow

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

    // ── Energy (motion → photo → YOLO occupancy, live camera view) ──
    val livePreviewHub: LivePreviewHub by lazy {
        LivePreviewHub()
    }

    val energyStateHolder: EnergyStateHolder by lazy {
        EnergyStateHolder()
    }

    /** SECURITY tab: gallery video looping in the camera box (null = live view); long-press to set. */
    val securityPreviewVideo = MutableStateFlow<Uri?>(null)

    /** ENERGY demo mode (off at start; long-press the ENERGY tab icon to switch). */
    val energyDemo: EnergyDemo by lazy {
        EnergyDemo(energyStateHolder)
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

    /** Siren / volume / vibration / strobe shared by the intruder and fire alarms. */
    val alarmOutputs: AlarmOutputs by lazy {
        AlarmOutputs(appContext)
    }

    val intrusionAlarm: IntrusionAlarm by lazy {
        IntrusionAlarm(securityStateHolder, intrusionNotifier, deterrentVoice, alarmOutputs)
    }

    // ── Fire (optional SGP41 gas sensor) ──
    val torchRouter: TorchRouter by lazy {
        TorchRouter(appContext)
    }

    val fireMonitor: FireMonitor by lazy {
        FireMonitor(
            telemetryRepository = telemetryRepository,
            alarm = FireAlarm(alarmOutputs, intrusionNotifier, torchRouter, mqttSyncRepository),
        )
    }

    /** Long-press the FIRE tab icon: simulated fire through the real pipeline (no MQTT). */
    val gasFireDemo: GasFireDemo by lazy {
        GasFireDemo(telemetryRepository, fireMonitor)
    }
}
