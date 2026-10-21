package com.rewatt.app.ui.security

import android.content.Context
import android.graphics.Bitmap
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rewatt.app.R
import com.rewatt.app.core.AppGraph
import com.rewatt.app.perception.CameraCaptureService
import com.rewatt.app.security.FaceRecognizer
import com.rewatt.app.security.FaceSample
import com.rewatt.app.security.SecurityEvent
import com.rewatt.app.security.SecuritySettings
import com.rewatt.app.security.SecurityUiState
import com.rewatt.app.usb.UsbConnectionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SecurityViewModel(
    private val appContext: Context,
    private val graph: AppGraph,
) : ViewModel() {
    val state: StateFlow<SecurityUiState> = graph.securityStateHolder.state
    val familyFaces: StateFlow<List<FaceSample>> = graph.familyFaceStore.samples
    val events: StateFlow<List<SecurityEvent>> = graph.securityEventStore.events

    private val mutableThreshold = MutableStateFlow(graph.securitySettings.matchThreshold)
    val threshold: StateFlow<Float> = mutableThreshold.asStateFlow()

    val radarConnected: StateFlow<Boolean> = graph.telemetryRepository.telemetryState
        .map { it.connectionStatus == UsbConnectionStatus.Connected }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val roomId: String get() = graph.mqttSyncRepository.roomId

    private var demoAlertJob: Job? = null

    init {
        graph.securityEventStore.refresh()
        // Warm up text-to-speech so the hidden alert demo's first "You are being recorded" is heard.
        graph.deterrentVoice.prepare()
    }

    /** Caller must have requested camera (+ microphone) permission first. */
    fun arm() {
        graph.securitySettings.armed = true
        graph.securityStateHolder.update { it.copy(armed = true) }
        ContextCompat.startForegroundService(appContext, CameraCaptureService.armIntent(appContext))
    }

    fun disarm() {
        graph.securitySettings.armed = false
        graph.securityStateHolder.update { it.copy(armed = false) }
        runCatching { appContext.startService(CameraCaptureService.disarmIntent(appContext)) }
    }

    fun setThreshold(value: Float) {
        graph.securitySettings.matchThreshold = value
        mutableThreshold.value = graph.securitySettings.matchThreshold
    }

    fun deleteFace(id: String) = graph.familyFaceStore.delete(id)

    fun clearFaces() = graph.familyFaceStore.clear()

    fun deleteEvent(id: String) = graph.securityEventStore.delete(id)

    fun clipFiles(id: String) = graph.securityEventStore.clipFiles(id)

    fun photoFiles(id: String) = graph.securityEventStore.photoFiles(id)

    fun canUseFullScreenIntent(): Boolean = graph.intrusionNotifier.canUseFullScreenIntent()

    fun isIgnoringBatteryOptimizations(): Boolean =
        appContext.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(appContext.packageName)

    val phoneSetupConfirmed: StateFlow<Boolean> = graph.securitySettings.phoneSetupConfirmedFlow

    fun confirmPhoneSetup() {
        graph.securitySettings.phoneSetupConfirmed = true
    }

    /** Plays the full alert (strobe is skipped: the camera isn't held outside an event). */
    fun testAlarm() {
        graph.deterrentVoice.prepare()
        graph.intrusionAlarm.trigger(
            eventId = TEST_EVENT_ID,
            roomId = roomId,
            photoName = null,
            score = null,
            torch = {},
            torchRestore = {},
        )
    }

    /**
     * Hidden demo (long-press the ARMED / DISARMED card): "You are being recorded" right away, then
     * after [DEMO_ALERT_DELAY_MS] the full intruder alert — siren + red full-screen — exactly like
     * Test intruder alarm (no MQTT, no event saved). Dismiss it from the alert screen as usual.
     */
    fun demoIntruderAlert() {
        if (demoAlertJob?.isActive == true || graph.intrusionAlarm.alarmActive) return
        graph.deterrentVoice.prepare()
        graph.intrusionAlarm.announceRecording()
        demoAlertJob = viewModelScope.launch {
            delay(DEMO_ALERT_DELAY_MS)
            graph.intrusionAlarm.trigger(
                eventId = TEST_EVENT_ID,
                roomId = roomId,
                photoName = null,
                score = null,
                torch = {},
                torchRestore = {},
                speakDeterrent = false,
            )
        }
    }

    /** 5 s countdown (time to step in front of the back camera), then one full CCTV check. */
    fun runTestCheck() {
        val current = graph.securityStateHolder.state.value.testCheck
        if (current.running || current.countdown > 0) return
        viewModelScope.launch {
            for (seconds in TEST_COUNTDOWN_S downTo 1) {
                graph.securityStateHolder.update { it.copy(testCheck = it.testCheck.copy(countdown = seconds, errorRes = null)) }
                delay(1_000)
            }
            graph.securityStateHolder.update { it.copy(testCheck = it.testCheck.copy(countdown = 0, running = true)) }
            val started = runCatching {
                ContextCompat.startForegroundService(appContext, CameraCaptureService.testCheckIntent(appContext))
            }.isSuccess
            // Watchdog: never leave the button stuck on "Checking…" if the camera service failed.
            if (started) delay(TEST_TIMEOUT_MS)
            if (graph.securityStateHolder.state.value.testCheck.running) {
                graph.securityStateHolder.update {
                    it.copy(testCheck = it.testCheck.copy(running = false, errorRes = R.string.sec_test_err_camera))
                }
            }
        }
    }

    /** Registration uses the front camera; ask the camera service (if running) to let go first. */
    fun beginRegistration() {
        if (cameraServiceRunning()) {
            runCatching { appContext.startService(CameraCaptureService.releaseCameraIntent(appContext)) }
        }
    }

    fun endRegistration() {
        if (cameraServiceRunning()) {
            runCatching { appContext.startService(CameraCaptureService.resumeCameraIntent(appContext)) }
        }
    }

    suspend fun registerFace(bitmap: Bitmap): FaceRecognizer.RegistrationResult? = withContext(Dispatchers.Default) {
        runCatching { graph.faceRecognizer.extractForRegistration(bitmap) }
            .getOrNull()
            ?.also { result ->
                if (result is FaceRecognizer.RegistrationResult.Ok) {
                    graph.familyFaceStore.add(result.embedding, result.thumbnail)
                }
            }
    }

    private fun cameraServiceRunning(): Boolean =
        graph.perceptionStateHolder.state.value.isRunning || graph.securitySettings.armed

    fun storageUsedBytes(): Long = events.value.sumOf { it.sizeBytes }

    companion object {
        const val TEST_EVENT_ID = "TEST"
        private const val DEMO_ALERT_DELAY_MS = 4_000L
        private const val TEST_COUNTDOWN_S = 5
        private const val TEST_TIMEOUT_MS = 45_000L
        val THRESHOLD_RANGE = SecuritySettings.MIN_THRESHOLD..SecuritySettings.MAX_THRESHOLD
    }
}

class SecurityViewModelFactory(
    private val appContext: Context,
    private val graph: AppGraph,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SecurityViewModel(appContext, graph) as T
}
