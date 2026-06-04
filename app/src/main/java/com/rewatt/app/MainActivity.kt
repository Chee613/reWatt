package com.rewatt.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.rewatt.app.BuildConfig
import com.rewatt.app.data.HallSnapshot
import com.rewatt.app.perception.CaptureStartSource
import com.rewatt.app.ui.ModeSelectionDialog
import com.rewatt.app.ui.SetupGuideDialog
import com.rewatt.app.ui.auth.AuthState
import com.rewatt.app.ui.auth.AuthViewModel
import com.rewatt.app.ui.auth.AuthViewModelFactory
import com.rewatt.app.ui.auth.DeleteAccountState
import com.rewatt.app.ui.auth.LoginScreen
import com.rewatt.app.ui.auth.SignUpScreen
import com.rewatt.app.ui.dashboard.DashboardScreen
import com.rewatt.app.ui.dashboard.DashboardViewModel
import com.rewatt.app.ui.dashboard.DashboardViewModelFactory
import com.rewatt.app.ui.theme.ReWattTheme
import com.rewatt.app.perception.PerceptionViewModel
import com.rewatt.app.perception.PerceptionViewModelFactory
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.rewatt.app.data.ClassScheduleWorker
import com.rewatt.app.data.ForecastWorker
import com.rewatt.app.data.EnergyWasteWorker
import com.rewatt.app.data.MalaysiaHolidays
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.rewatt.app.usb.UsbSentinelService
import java.util.concurrent.TimeUnit

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
    private val yoloUploadLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let(perceptionViewModel::analyzeUploadedYoloImage)
        }
    private var pendingCaptureSource: CaptureStartSource? = null

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory((application as ReWattApplication).appGraph.authRepository)
    }
    private val viewModel: DashboardViewModel by viewModels {
        val graph = (application as ReWattApplication).appGraph
        DashboardViewModelFactory(
            appContext = applicationContext,
            repository = graph.telemetryRepository,
            weatherRepository = graph.weatherRepository,
            occupancyForecastProvider = graph.occupancyForecastProvider,
            energyAdvisor = graph.geminiEnergyAdvisor,
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

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "occupancy_forecast",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ForecastWorker>(2, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        )

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "energy_waste_summary",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<EnergyWasteWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        )

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "class_schedule_detector",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ClassScheduleWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        )

        // Refresh holiday list from Google Calendar on startup (uses disk cache if still fresh)
        lifecycleScope.launch { MalaysiaHolidays.refresh(applicationContext) }

        requestNotificationPermissionIfNeeded()
        perceptionViewModel.setPermissionState(
            cameraGranted = hasPermission(Manifest.permission.CAMERA),
        )
        refreshWeatherIfLocationGranted()

        setContent {
            ReWattTheme {
                val authState by authViewModel.authState.collectAsStateWithLifecycle()
                val sensorGuideEvent by authViewModel.sensorGuideEvent.collectAsStateWithLifecycle()
                val showModeSelectionEvent by authViewModel.showModeSelection.collectAsStateWithLifecycle()
                val currentUserRole by authViewModel.currentUserRole.collectAsStateWithLifecycle()
                val spaceName by authViewModel.spaceName.collectAsStateWithLifecycle()
                val deleteAccountState by authViewModel.deleteAccountState.collectAsStateWithLifecycle()
                val hallSnapshot by viewModel.hallSnapshot.collectAsStateWithLifecycle()
                var showSignUp by rememberSaveable { mutableStateOf(false) }
                var devBypass by rememberSaveable { mutableStateOf(false) }
                var showSetupGuide by rememberSaveable { mutableStateOf(false) }
                var showModeDialog by rememberSaveable { mutableStateOf(false) }

                // Load role and space name whenever the user becomes authenticated
                LaunchedEffect(authState) {
                    if (authState is AuthState.Authenticated) {
                        authViewModel.loadUserRole()
                        authViewModel.loadSpaceName()
                    }
                }

                // Show mode selection dialog after signup
                LaunchedEffect(showModeSelectionEvent) {
                    if (showModeSelectionEvent) {
                        showModeDialog = true
                        authViewModel.consumeModeSelection()
                    }
                }

                // Show setup guide when Sensor mode is chosen
                LaunchedEffect(sensorGuideEvent) {
                    if (sensorGuideEvent) {
                        showSetupGuide = true
                        authViewModel.consumeSensorGuide()
                    }
                }

                val showDashboard = devBypass || authState is AuthState.Authenticated

                if (showDashboard) {
                    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
                    val perceptionState = perceptionViewModel.uiState.collectAsStateWithLifecycle()
                    val hallSchedules by viewModel.hallSchedules.collectAsStateWithLifecycle()
                    val hallMeanSchedules by viewModel.hallMeanSchedules.collectAsStateWithLifecycle()
                    val hallMinuteSchedules by viewModel.hallMinuteSchedules.collectAsStateWithLifecycle()
                    val learnedSchedules by viewModel.learnedSchedules.collectAsStateWithLifecycle()
                    val recentCaptures by viewModel.recentCaptures.collectAsStateWithLifecycle()
                    val wasteSummary by viewModel.wasteSummary.collectAsStateWithLifecycle()
                    val wasteRefreshing by viewModel.wasteRefreshing.collectAsStateWithLifecycle()
                    val sensorFleet by viewModel.sensorFleet.collectAsStateWithLifecycle()
                    val authedUser = (authState as? AuthState.Authenticated)?.user
                    DashboardScreen(
                        uiState = uiState.value,
                        perceptionState = perceptionState.value,
                        onScanUsb = ::startUsbService,
                        onStartPerception = ::requestPerceptionPermissionsThenStart,
                        onStopPerception = perceptionViewModel::stopPerception,
                        onUploadYoloImage = ::requestYoloImageUpload,
                        onSelectYoloModel = perceptionViewModel::selectYoloModel,
                        onDebugCapture = perceptionViewModel::captureNow,
                        onRefreshWeather = viewModel::refreshWeather,
                        onRequestEnergyAdvice = viewModel::requestEnergyAdvice,
                        onRefreshWaste = viewModel::refreshWasteSummary,
                        wasteRefreshing = wasteRefreshing,
                        userEmail = authedUser?.email ?: "dev@rewatt.app",
                        userDisplayName = authedUser?.displayName ?: if (devBypass) "Dev Mode" else null,
                        onSignOut = {
                            devBypass = false
                            authViewModel.signOut()
                        },
                        isDeletingAccount = deleteAccountState is DeleteAccountState.Loading,
                        deleteAccountError = (deleteAccountState as? DeleteAccountState.Error)?.message,
                        onDeleteAccount = authViewModel::deleteAccount,
                        onClearDeleteError = authViewModel::resetDeleteAccountState,
                        currentUserRole = currentUserRole,
                        spaceName = spaceName,
                        hallSnapshot = hallSnapshot,
                        hallSchedules = hallSchedules,
                        hallMeanSchedules = hallMeanSchedules,
                        hallMinuteSchedules = hallMinuteSchedules,
                        learnedSchedules = learnedSchedules,
                        recentCaptures = recentCaptures,
                        wasteSummary = wasteSummary,
                        sensorFleet = sensorFleet,
                        onRefreshFleet = viewModel::refreshSensorFleet,
                        onHallSelect = viewModel::selectHall,
                        onModeSelected = { role ->
                            authViewModel.setMode(role)
                        },
                        onRenameSpace = { name ->
                            authViewModel.renameSpace(name)
                        },
                        onSetCapacity = { capacity ->
                            authViewModel.setSpaceCapacity(capacity)
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

                if (showModeDialog) {
                    ModeSelectionDialog(
                        currentRole = currentUserRole,
                        canDismiss = false,
                        onSelect = { role ->
                            showModeDialog = false
                            authViewModel.setMode(role)
                        },
                    )
                }

                if (showSetupGuide) {
                    SetupGuideDialog(onDismiss = { name, capacity ->
                        showSetupGuide = false
                        if (name != null) authViewModel.setSpaceName(name)
                        if (capacity != null) authViewModel.setSpaceCapacity(capacity)
                        // Location may have just been granted inside the guide.
                        refreshWeatherIfLocationGranted()
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

    private fun requestYoloImageUpload() {
        yoloUploadLauncher.launch("image/*")
    }

    private fun refreshWeatherIfLocationGranted() {
        val fineGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseGranted = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fineGranted || coarseGranted) {
            viewModel.refreshWeather()
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
