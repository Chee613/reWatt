package com.rewatt.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.rewatt.app.BuildConfig
import com.rewatt.app.perception.CaptureStartSource
import com.rewatt.app.ui.auth.AuthState
import com.rewatt.app.ui.auth.AuthViewModel
import com.rewatt.app.ui.auth.AuthViewModelFactory
import com.rewatt.app.ui.auth.LoginScreen
import com.rewatt.app.ui.auth.SignUpScreen
import com.rewatt.app.ui.dashboard.DashboardScreen
import com.rewatt.app.ui.dashboard.DashboardViewModel
import com.rewatt.app.ui.dashboard.DashboardViewModelFactory
import com.rewatt.app.ui.theme.ReWattTheme
import com.rewatt.app.perception.PerceptionViewModel
import com.rewatt.app.perception.PerceptionViewModelFactory
import com.rewatt.app.usb.UsbSentinelService

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startUsbService()
        }
    private val perceptionPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val cameraGranted = permissions[Manifest.permission.CAMERA] == true ||
                hasPermission(Manifest.permission.CAMERA)
            val microphoneGranted = permissions[Manifest.permission.RECORD_AUDIO] == true ||
                hasPermission(Manifest.permission.RECORD_AUDIO)
            perceptionViewModel.setPermissionState(cameraGranted, microphoneGranted)
            pendingPreviewView?.let { previewView ->
                if (cameraGranted && microphoneGranted) {
                    perceptionViewModel.startPerception(
                        lifecycleOwner = this,
                        previewView = previewView,
                        source = pendingCaptureSource ?: CaptureStartSource.Manual,
                    )
                }
            }
            pendingPreviewView = null
            pendingCaptureSource = null
        }
    private var pendingPreviewView: PreviewView? = null
    private var pendingCaptureSource: CaptureStartSource? = null

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory((application as ReWattApplication).appGraph.authRepository)
    }
    private val viewModel: DashboardViewModel by viewModels {
        DashboardViewModelFactory((application as ReWattApplication).appGraph.telemetryRepository)
    }
    private val perceptionViewModel: PerceptionViewModel by viewModels {
        PerceptionViewModelFactory(
            appContext = applicationContext,
            telemetryRepository = (application as ReWattApplication).appGraph.telemetryRepository,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()
        perceptionViewModel.setPermissionState(
            cameraGranted = hasPermission(Manifest.permission.CAMERA),
            microphoneGranted = hasPermission(Manifest.permission.RECORD_AUDIO),
        )

        setContent {
            ReWattTheme {
                val authState by authViewModel.authState.collectAsStateWithLifecycle()
                var showSignUp by rememberSaveable { mutableStateOf(false) }
                var devBypass by rememberSaveable { mutableStateOf(false) }

                val showDashboard = devBypass || authState is AuthState.Authenticated

                if (showDashboard) {
                    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
                    val perceptionState = perceptionViewModel.uiState.collectAsStateWithLifecycle()
                    val authedUser = (authState as? AuthState.Authenticated)?.user
                    DashboardScreen(
                        uiState = uiState.value,
                        perceptionState = perceptionState.value,
                        onScanUsb = ::startUsbService,
                        onStartPerception = ::requestPerceptionPermissionsThenStart,
                        onStopPerception = perceptionViewModel::stopPerception,
                        userEmail = authedUser?.email ?: "dev@rewatt.app",
                        userDisplayName = authedUser?.displayName ?: if (devBypass) "Dev Mode" else null,
                        onSignOut = {
                            devBypass = false
                            authViewModel.signOut()
                        },
                    )
                } else {
                    when (authState) {
                        is AuthState.Loading -> {
                            // blank while Firebase resolves the auth state (< 1 s)
                        }
                        else -> {
                            if (showSignUp) {
                                SignUpScreen(
                                    viewModel = authViewModel,
                                    onNavigateToLogin = { showSignUp = false },
                                )
                            } else {
                                LoginScreen(
                                    viewModel = authViewModel,
                                    onNavigateToSignUp = { showSignUp = true },
                                    onDevBypass = if (BuildConfig.DEBUG) {
                                        { devBypass = true }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
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

    private fun requestPerceptionPermissionsThenStart(
        previewView: PreviewView,
        source: CaptureStartSource,
    ) {
        pendingPreviewView = previewView
        pendingCaptureSource = source
        val cameraGranted = hasPermission(Manifest.permission.CAMERA)
        val microphoneGranted = hasPermission(Manifest.permission.RECORD_AUDIO)
        perceptionViewModel.setPermissionState(cameraGranted, microphoneGranted)

        if (cameraGranted && microphoneGranted) {
            pendingPreviewView = null
            pendingCaptureSource = null
            perceptionViewModel.startPerception(
                lifecycleOwner = this,
                previewView = previewView,
                source = source,
            )
            return
        }

        perceptionPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
            ),
        )
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
