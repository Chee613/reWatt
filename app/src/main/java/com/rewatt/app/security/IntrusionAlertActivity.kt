package com.rewatt.app.security

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewatt.app.R
import com.rewatt.app.ReWattApplication
import com.rewatt.app.perception.CameraCaptureService
import java.text.DateFormat
import java.util.Date

/** Red full-screen intruder alert shown over the lock screen with one big DISMISS button. */
class IntrusionAlertActivity : ComponentActivity() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val graph = (application as ReWattApplication).appGraph
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()
        val roomId = intent.getStringExtra(EXTRA_ROOM_ID).orEmpty()
        val photoName = intent.getStringExtra(EXTRA_PHOTO)
        val score = intent.getFloatExtra(EXTRA_SCORE, Float.NaN).takeUnless { it.isNaN() }
        val photoPath = photoName?.let { graph.securityEventStore.photoFile(eventId, it).absolutePath }

        setContent {
            val state by graph.securityStateHolder.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.alarmActive) {
                if (!state.alarmActive) finish()
            }
            IntrusionAlertScreen(
                roomId = roomId,
                photoPath = photoPath,
                score = score,
                onDismiss = {
                    if (graph.securitySettings.armed) {
                        // The service also releases the camera / records the dismissal on the event.
                        startService(CameraCaptureService.dismissAlarmIntent(this))
                    } else {
                        graph.intrusionAlarm.dismiss() // e.g. Test alarm while disarmed
                    }
                    finish()
                },
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Same trick as the old PIR project: a short bright wake lock guarantees the panel lights up.
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "rewatt:intrusion")
            .also { runCatching { it.acquire(WAKE_MS) } }
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_EVENT_ID = "event_id"
        private const val EXTRA_ROOM_ID = "room_id"
        private const val EXTRA_PHOTO = "photo"
        private const val EXTRA_SCORE = "score"
        private const val WAKE_MS = 8_000L

        fun intent(context: Context, eventId: String, roomId: String, photoName: String?, score: Float?): Intent =
            Intent(context, IntrusionAlertActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(EXTRA_EVENT_ID, eventId)
                .putExtra(EXTRA_ROOM_ID, roomId)
                .putExtra(EXTRA_PHOTO, photoName)
                .putExtra(EXTRA_SCORE, score ?: Float.NaN)
    }
}

@Composable
private fun IntrusionAlertScreen(
    roomId: String,
    photoPath: String?,
    score: Float?,
    onDismiss: () -> Unit,
) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    val photo = remember(photoPath) {
        photoPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    val time = remember { DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFB91C1C))
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.cctv_alert_heading),
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(pulse),
        )
        Text(
            text = stringResource(R.string.cctv_alert_text, roomId),
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        if (photo != null) {
            Image(
                bitmap = photo.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Text(
            text = if (score != null) {
                stringResource(R.string.cctv_alert_detail_score, time, "%.2f".format(score))
            } else {
                stringResource(R.string.cctv_alert_detail, time)
            },
            color = Color.White,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB91C1C)),
        ) {
            Text(stringResource(R.string.cctv_dismiss).uppercase(), fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
    }
}
