package com.rewatt.app.notify

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import java.lang.ref.WeakReference
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rewatt.app.R

/**
 * Full-screen alert activity that appears immediately when an energy-waste alert fires.
 *
 * Designed to demand user action — the user **must** tap Done or Snooze.
 * There is no dismiss-by-swiping or back-button escape; the only exit paths
 * are the two explicit action buttons.
 *
 * Shows over the lock screen and turns the screen on, just like an incoming
 * phone call.
 */
class EnergyAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentInstance = WeakReference(this)
        showOverLockScreen()

        setContent {
            EnergyAlertScreen(
                title = getString(R.string.energy_alert_title),
                message = getString(R.string.energy_alert_text),
                onDone = {
                    EnergyAlertNotifier.onDone(applicationContext)
                    finish()
                },
                onSnooze = {
                    EnergyAlertNotifier.onSnooze(applicationContext)
                    finish()
                },
            )
        }
    }

    override fun onDestroy() {
        if (currentInstance?.get() === this) currentInstance = null
        super.onDestroy()
    }

    /** Prevent the user from dismissing the alert with the back button. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Intentionally do nothing — the user must tap Done or Snooze.
    }

    /**
     * Configure the activity to display over the lock screen and turn the
     * screen on, using the appropriate API level.
     */
    companion object {
        private var currentInstance: WeakReference<EnergyAlertActivity>? = null

        /** Finish the alert activity if it is currently showing (e.g. action taken from drawer). */
        fun finishIfRunning() {
            currentInstance?.get()?.finish()
            currentInstance = null
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        // Keep the screen on while the alert is displayed so it doesn't turn off.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun EnergyAlertScreen(
    title: String,
    message: String,
    onDone: () -> Unit,
    onSnooze: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = Color(0xFFFFB74D),
            modifier = Modifier.size(80.dp),
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = title,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = message,
            color = Color(0xFFB0BEC5),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
        )

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0E7C66),
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Done",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onSnooze,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = "Snooze 5 min",
                fontSize = 18.sp,
                color = Color(0xFFB0BEC5),
            )
        }
    }
}
