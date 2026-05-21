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
import com.rewatt.app.ui.dashboard.DashboardScreen
import com.rewatt.app.ui.dashboard.DashboardViewModel
import com.rewatt.app.ui.dashboard.DashboardViewModelFactory
import com.rewatt.app.ui.theme.ReWattTheme
import com.rewatt.app.usb.UsbSentinelService

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startUsbService()
        }

    private val viewModel: DashboardViewModel by viewModels {
        DashboardViewModelFactory((application as ReWattApplication).appGraph.telemetryRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()

        setContent {
            val uiState = viewModel.uiState.collectAsStateWithLifecycle()
            ReWattTheme {
                DashboardScreen(
                    uiState = uiState.value,
                    onScanUsb = ::startUsbService,
                )
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
}
