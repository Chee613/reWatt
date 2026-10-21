package com.rewatt.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewatt.app.perception.CaptureStartSource
import com.rewatt.app.perception.PerceptionViewModel
import com.rewatt.app.perception.PerceptionViewModelFactory
import com.rewatt.app.ui.SetupGuideDialog
import com.rewatt.app.ui.security.SecuritySettingsSection
import com.rewatt.app.ui.security.SecurityTab
import com.rewatt.app.ui.security.SecurityViewModel
import com.rewatt.app.ui.security.SecurityViewModelFactory
import com.rewatt.app.ui.sensor.SensorScreen
import com.rewatt.app.ui.sensor.SensorViewModel
import com.rewatt.app.ui.sensor.SensorViewModelFactory
import com.rewatt.app.ui.theme.ReWattTheme
import com.rewatt.app.usb.UsbSentinelService

class MainActivity : AppCompatActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startUsbService()
        }
    private val perceptionPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val cameraGranted = permissions[Manifest.permission.CAMERA] == true ||
                hasPermission(Manifest.permission.CAMERA)
            perceptionViewModel.setPermissionState(cameraGranted)
            if (cameraGranted) {
                perceptionViewModel.startPerception(pendingCaptureSource ?: CaptureStartSource.Manual)
            }
            pendingCaptureSource = null
        }

    private var pendingCaptureSource: CaptureStartSource? = null

    // CCTV: arming needs the camera; the microphone is optional (clips are silent without it).
    private val securityArmPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasPermission(Manifest.permission.CAMERA)) securityViewModel.arm()
        }
    private val securityCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val securityViewModel: SecurityViewModel by viewModels {
        SecurityViewModelFactory(
            appContext = applicationContext,
            graph = (application as ReWattApplication).appGraph,
        )
    }

    private val sensorViewModel: SensorViewModel by viewModels {
        val graph = (application as ReWattApplication).appGraph
        SensorViewModelFactory(
            appContext = applicationContext,
            telemetryRepository = graph.telemetryRepository,
            mqttRepository = graph.mqttSyncRepository,
            perceptionStateHolder = graph.perceptionStateHolder,
        )
    }

    private val perceptionViewModel: PerceptionViewModel by viewModels {
        PerceptionViewModelFactory(
            appContext = applicationContext,
            stateHolder = (application as ReWattApplication).appGraph.perceptionStateHolder,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()
        perceptionViewModel.setPermissionState(
            cameraGranted = hasPermission(Manifest.permission.CAMERA),
        )
        // CCTV stays armed across restarts; Android only allows the camera service to (re)start
        // while the app is in the foreground, so resume it whenever the app is opened.
        if ((application as ReWattApplication).appGraph.securitySettings.armed && hasPermission(Manifest.permission.CAMERA)) {
            securityViewModel.arm()
        }

        setContent {
            ReWattTheme {
                val uiState by sensorViewModel.uiState.collectAsStateWithLifecycle()
                val securityState by securityViewModel.state.collectAsStateWithLifecycle()
                val appGraph = (application as ReWattApplication).appGraph
                val fireAlarm by appGraph.fireMonitor.alarm.state.collectAsStateWithLifecycle()
                // The Quick Setup Guide opens by itself the first time the app is launched.
                val appPrefs = remember { getSharedPreferences(APP_PREFS, MODE_PRIVATE) }
                var showSetupGuide by remember { mutableStateOf(!appPrefs.getBoolean(KEY_SETUP_GUIDE_SEEN, false)) }

                SensorScreen(
                    uiState = uiState,
                    onScanUsb = ::startUsbService,
                    onStartPerception = ::requestPerceptionPermissionsThenStart,
                    onStopPerception = perceptionViewModel::stopPerception,
                    onDebugCapture = perceptionViewModel::captureNow,
                    onSendPhotoMqtt = sensorViewModel::publishCurrentPhoto,
                    onUpdateMqttConfig = sensorViewModel::updateMqttConfig,
                    onToggleDemoMode = sensorViewModel::toggleDemoMode,
                    onOpenSetupGuide = { showSetupGuide = true },
                    securityState = securityState,
                    fireAlarmState = fireAlarm,
                    onToggleFireDemo = appGraph.gasFireDemo::toggle,
                    securityContent = {
                        SecurityTab(
                            viewModel = securityViewModel,
                            onRequestArm = ::requestSecurityPermissionsThenArm,
                        )
                    },
                    settingsSecurityContent = {
                        SecuritySettingsSection(
                            viewModel = securityViewModel,
                            onRequestMicrophone = { securityCameraPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            onRequestCamera = { securityCameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                            onTestFireAlarm = { appGraph.fireMonitor.alarm.trigger(vocIndex = null, noxIndex = null, test = true) },
                        )
                    },
                )

                if (showSetupGuide) {
                    SetupGuideDialog(onDismiss = { name, capacity ->
                        showSetupGuide = false
                        appPrefs.edit().putBoolean(KEY_SETUP_GUIDE_SEEN, true).apply()
                        if (name != null) {
                            sensorViewModel.updateMqttConfig(
                                sensorViewModel.mqttRepository.host,
                                sensorViewModel.mqttRepository.port,
                                sensorViewModel.mqttRepository.username,
                                sensorViewModel.mqttRepository.password,
                                name,
                            )
                        }
                    })
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startUsbService()
        }
    }

    private fun startUsbService() {
        ContextCompat.startForegroundService(
            this,
            UsbSentinelService.createStartIntent(this),
        )
    }

    private fun requestPerceptionPermissionsThenStart(source: CaptureStartSource) {
        pendingCaptureSource = source
        val cameraGranted = hasPermission(Manifest.permission.CAMERA)
        perceptionViewModel.setPermissionState(cameraGranted)

        if (cameraGranted) {
            pendingCaptureSource = null
            perceptionViewModel.startPerception(source)
            return
        }

        perceptionPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
    }

    private fun requestSecurityPermissionsThenArm() {
        val needed = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filterNot(::hasPermission)
        if (needed.isEmpty()) {
            securityViewModel.arm()
        } else {
            securityArmPermissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val APP_PREFS = "rewatt_app_prefs"
        const val KEY_SETUP_GUIDE_SEEN = "setup_guide_seen"
    }
}
