package com.rewatt.app.perception

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import com.rewatt.app.R
import com.rewatt.app.ReWattApplication
import com.rewatt.app.data.PerceptionSyncSummary
import com.rewatt.app.data.TelemetryRepository
import com.rewatt.app.notify.EnergyAlertNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the perception pipeline so captures keep firing while the screen is
 * off. The camera is driven by an exact alarm ([AlarmManager.setExactAndAllowWhileIdle]) anchored to
 * the next 10-minute wall-clock mark, with a partial wake lock held across the capture — this is
 * what fixes the missed 3:10 AM capture. Results are written to [PerceptionStateHolder] (for the
 * dashboard) and [TelemetryRepository] (for sync/forecast).
 */
class CameraCaptureService : LifecycleService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var stateHolder: PerceptionStateHolder
    private lateinit var telemetryRepository: TelemetryRepository
    private lateinit var alarmManager: AlarmManager
    private lateinit var powerManager: PowerManager

    private var cameraController: CameraCaptureController? = null
    private var yoloDetector: YoloDetector? = null
    private var started = false

    override fun onCreate() {
        super.onCreate()
        val appGraph = (application as ReWattApplication).appGraph
        stateHolder = appGraph.perceptionStateHolder
        telemetryRepository = appGraph.telemetryRepository
        alarmManager = getSystemService(AlarmManager::class.java)
        powerManager = getSystemService(PowerManager::class.java)

        startAsForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_CAPTURE -> runCaptureCycle()
            ACTION_CAPTURE_NOW -> cameraController?.captureNow()
            else -> startPerception()
        }
        return START_STICKY
    }

    private fun startPerception() {
        if (started) return
        started = true
        stateHolder.update {
            it.copy(
                isRunning = true,
                statusMessage = "Perception is running in the background.",
                yoloMessage = "Loading YOLO in the background.",
            )
        }
        setupControllers()
        loadDetectors()
        // Do NOT capture immediately — the first capture fires at the next 10-minute boundary.
        // Immediate capture is only done via the explicit "Capture Now" (ACTION_CAPTURE_NOW).
        scheduleNextCaptureAlarm()
    }

    private fun setupControllers() {
        cameraController = CameraCaptureController(
            context = this,
            yoloDetectorProvider = { yoloDetector },
            onFramePreview = { bitmap, exposureReading, exposureMessage ->
                // Fast path: show the frame immediately, clear stale boxes, defer detection/alerts.
                stateHolder.update {
                    it.copy(
                        latestFrame = bitmap,
                        latestFrameAtMillis = System.currentTimeMillis(),
                        yoloDetections = emptyList(),
                        yoloMessage = "Frame captured; running person detection…",
                        exposureEv = exposureReading?.ev,
                        exposureIso = exposureReading?.iso,
                        exposureTimeSeconds = exposureReading?.exposureTimeSeconds,
                        exposureAperture = exposureReading?.aperture ?: ExposureValueCalculator.FIXED_APERTURE,
                        lightsOn = exposureReading?.lightsOn,
                        exposureMessage = exposureMessage,
                        statusMessage = "Perception is running.",
                    )
                }
            },
            onFrameAnalyzed = { bitmap, detections, yoloMessage, exposureReading, exposureMessage ->
                val newState = stateHolder.updateAndGet {
                    it.copy(
                        latestFrame = bitmap ?: it.latestFrame,
                        latestFrameAtMillis = if (bitmap != null) System.currentTimeMillis() else it.latestFrameAtMillis,
                        yoloDetections = detections,
                        yoloMessage = yoloMessage,
                        exposureEv = exposureReading?.ev,
                        exposureIso = exposureReading?.iso,
                        exposureTimeSeconds = exposureReading?.exposureTimeSeconds,
                        exposureAperture = exposureReading?.aperture ?: ExposureValueCalculator.FIXED_APERTURE,
                        lightsOn = exposureReading?.lightsOn,
                        exposureMessage = exposureMessage,
                        statusMessage = "Perception is running.",
                    )
                }
                publishPerceptionSummary(newState)
                // Real-time waste prompt: empty room with the lights still on.
                EnergyAlertNotifier.onPerception(
                    context = applicationContext,
                    personCount = newState.personCount,
                    lightsOn = newState.lightsOn,
                )
            },
            onError = { message -> stateHolder.update { it.copy(yoloMessage = message) } },
        ).also { it.attach(this) }
    }

    private fun loadDetectors() {
        serviceScope.launch {
            runCatching { YoloDetector.create(applicationContext) }
                .onSuccess {
                    yoloDetector = it
                    stateHolder.update { s -> s.copy(yoloMessage = "YOLO loaded; analyzing one frame every 10 minutes.") }
                }
                .onFailure { error ->
                    stateHolder.update { s -> s.copy(yoloMessage = "YOLO model failed to load: ${error.message ?: error::class.java.simpleName}") }
                }
        }
    }

    private fun runCaptureCycle() {
        // Hold the CPU just long enough to complete the capture, even in Doze. The timeout is a
        // safety net; capture is asynchronous and typically finishes in a few seconds.
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
        runCatching { wakeLock.acquire(WAKELOCK_TIMEOUT_MS) }
        try {
            cameraController?.capture()
        } finally {
            scheduleNextCaptureAlarm()
            if (wakeLock.isHeld) runCatching { wakeLock.release() }
        }
    }

    /** Schedule the next exact, Doze-exempt wakeup at the next 10-minute wall-clock mark. */
    private fun scheduleNextCaptureAlarm() {
        val triggerAt = System.currentTimeMillis() + millisUntilNextBoundary(INFERENCE_INTERVAL_MINUTES)
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                capturePendingIntent(),
            )
        }.onFailure {
            // Falls back to a windowed alarm if exact alarms are not permitted.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, capturePendingIntent())
        }
    }

    private fun capturePendingIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            CAPTURE_REQUEST_CODE,
            Intent(this, CameraCaptureService::class.java).setAction(ACTION_CAPTURE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun publishPerceptionSummary(state: PerceptionUiState) {
        telemetryRepository.updatePerceptionSummary(
            PerceptionSyncSummary(
                personCount = state.personCount,
                lightsOn = state.lightsOn,
                exposureEv = state.exposureEv,
                exposureIso = state.exposureIso,
                exposureTimeSeconds = state.exposureTimeSeconds,
                exposureAperture = state.exposureAperture,
                capturedAtMillis = state.latestFrameAtMillis,
            ),
        )
    }

    private fun startAsForegroundService() {
        createNotificationChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            foregroundServiceType(),
        )
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rewatt)
            .setContentTitle(getString(R.string.perception_notification_title))
            .setContentText(getString(R.string.perception_notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.perception_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        started = false
        runCatching { alarmManager.cancel(capturePendingIntent()) }
        cameraController?.close()
        cameraController = null
        yoloDetector?.close()
        yoloDetector = null
        serviceScope.cancel()
        stateHolder.update {
            it.copy(isRunning = false, statusMessage = "Perception is stopped.")
        }
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "rewatt_perception"
        private const val NOTIFICATION_ID = 73
        private const val CAPTURE_REQUEST_CODE = 71
        private const val WAKELOCK_TAG = "rewatt:capture"
        private const val WAKELOCK_TIMEOUT_MS = 90_000L
        private const val INFERENCE_INTERVAL_MINUTES = 10

        const val ACTION_CAPTURE = "com.rewatt.app.action.CAPTURE"
        const val ACTION_CAPTURE_NOW = "com.rewatt.app.action.CAPTURE_NOW"

        fun startIntent(context: Context): Intent =
            Intent(context, CameraCaptureService::class.java)

        fun captureNowIntent(context: Context): Intent =
            Intent(context, CameraCaptureService::class.java).setAction(ACTION_CAPTURE_NOW)
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else {
            0
        }
}
