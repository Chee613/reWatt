package com.rewatt.app.security

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewatt.app.R
import com.rewatt.app.core.AppGraph
import com.rewatt.app.perception.CameraCaptureService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AlarmRed = Color(0xFFDC2626)

/**
 * In-app alarm effect (fire or intruder) while the app is open — no full-screen page, so the live
 * preview stays visible: a pulsing red glow around the screen edges plus a compact alert card at
 * the top (pulsing warning triangle, heading, one detail line, DISMISS). Touches pass through to
 * the app; it follows the app's current orientation. Replaces the separate [IntrusionAlertActivity]
 * launch, which lagged the siren.
 */
@Composable
fun InAppAlarmOverlay(graph: AppGraph) {
    val context = LocalContext.current
    val security by graph.securityStateHolder.state.collectAsStateWithLifecycle()
    val fire by graph.fireMonitor.alarm.state.collectAsStateWithLifecycle()

    when {
        fire.active -> {
            val time = remember(fire.atMillis) { realDateTimeOf(fire.atMillis) }
            AlarmEffect(
                heading = stringResource(R.string.fire_alert_heading),
                detail = if (fire.test) {
                    stringResource(R.string.fire_alert_test, time)
                } else {
                    stringResource(
                        R.string.fire_alert_detail,
                        time,
                        fire.vocIndex?.toString() ?: "--",
                        fire.noxIndex?.toString() ?: "--",
                    )
                },
                onDismiss = { graph.fireMonitor.dismiss() },
            )
        }
        security.alarmActive -> {
            val time = remember(security.alarmEventId) { realDateTimeOf(null) }
            val score = security.alarmScore
            AlarmEffect(
                heading = stringResource(R.string.cctv_alert_heading),
                detail = if (score != null) {
                    stringResource(R.string.cctv_alert_detail_score, time, "%.2f".format(score))
                } else {
                    stringResource(R.string.cctv_alert_detail, time)
                },
                onDismiss = {
                    if (graph.securitySettings.armed) {
                        // The service also releases the camera / records the dismissal on the event.
                        context.startService(CameraCaptureService.dismissAlarmIntent(context))
                    } else {
                        graph.intrusionAlarm.dismiss() // e.g. Test alarm while disarmed
                    }
                },
            )
        }
    }
}

/** Alarm banner time: the real date and time it rang, e.g. "23 Oct 2026, 9:14:05 PM". */
private fun realDateTimeOf(millis: Long?): String =
    SimpleDateFormat("d MMM yyyy, h:mm:ss a", Locale.getDefault()).format(Date(millis ?: System.currentTimeMillis()))

@Composable
private fun AlarmEffect(heading: String, detail: String, onDismiss: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "alarm")
    val glow by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val triangleScale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "triangle",
    )

    Box(Modifier.fillMaxSize()) {
        // Pulsing red glow along the four screen edges (draw-only: touches pass through).
        Canvas(Modifier.fillMaxSize()) {
            val edge = 36.dp.toPx()
            val red = AlarmRed.copy(alpha = glow)
            drawRect(Brush.verticalGradient(listOf(red, Color.Transparent), endY = edge), size = Size(size.width, edge))
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, red), startY = size.height - edge, endY = size.height),
                topLeft = Offset(0f, size.height - edge),
                size = Size(size.width, edge),
            )
            drawRect(Brush.horizontalGradient(listOf(red, Color.Transparent), endX = edge), size = Size(edge, size.height))
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, red), startX = size.width - edge, endX = size.width),
                topLeft = Offset(size.width - edge, 0f),
                size = Size(edge, size.height),
            )
        }

        // Thin alert pill at the top (one line, ~44 dp tall) so the live preview stays visible:
        // pulsing warning triangle + heading · detail + DISMISS.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                .widthIn(max = 300.dp)
                .heightIn(min = 44.dp)
                .shadow(8.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(AlarmRed)
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = Color(0xFFFDE047),
                modifier = Modifier
                    .size(22.dp)
                    .scale(triangleScale),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text(heading, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text(
                    detail,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AlarmRed),
            ) {
                Text(stringResource(R.string.cctv_dismiss).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}
