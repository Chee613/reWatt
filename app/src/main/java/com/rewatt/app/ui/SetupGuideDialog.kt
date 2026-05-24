package com.rewatt.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat

private const val TOTAL_STEPS = 5

@Composable
fun SetupGuideDialog(onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Sensor Setup Guide", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    TextButton(onClick = onDismiss) { Text("Skip") }
                }

                Spacer(Modifier.height(16.dp))

                // Progress dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(TOTAL_STEPS) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == step) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index <= step) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                ),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${step + 1} / $TOTAL_STEPS",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Step content (scrollable)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (step) {
                        0 -> PermissionsStep()
                        1 -> BatteryStep()
                        2 -> MountStep()
                        3 -> VerifyViewStep()
                        4 -> DoneStep()
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Navigation buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (step > 0) {
                        OutlinedButton(
                            onClick = { step-- },
                            modifier = Modifier.weight(1f),
                        ) { Text("Back") }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Button(
                        onClick = { if (step < TOTAL_STEPS - 1) step++ else onDismiss() },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (step == TOTAL_STEPS - 1) "Done" else "Next") }
                }
            }
        }
    }
}

@Composable
private fun PermissionsStep() {
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        cameraGranted = result[Manifest.permission.CAMERA] == true
        micGranted = result[Manifest.permission.RECORD_AUDIO] == true
    }

    StepHeader(
        title = "1. Allow Camera & Microphone",
        body = "reWatt uses the camera and microphone to detect who is in the room. Make sure both are turned on.",
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PermissionRow(label = "Camera", granted = cameraGranted)
            PermissionRow(label = "Microphone", granted = micGranted)
        }
    }

    if (!cameraGranted || !micGranted) {
        Button(
            onClick = {
                val toRequest = buildList {
                    if (!cameraGranted) add(Manifest.permission.CAMERA)
                    if (!micGranted) add(Manifest.permission.RECORD_AUDIO)
                }
                permissionLauncher.launch(toRequest.toTypedArray())
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Allow Permissions") }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    val green = Color(0xFF2E7D32)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (granted) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint = if (granted) green else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            text = if (granted) "Allowed" else "Not allowed",
            fontSize = 13.sp,
            color = if (granted) green else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun BatteryStep() {
    val context = LocalContext.current
    val isIgnoring = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        (context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)
    } else {
        true
    }
    val green = Color(0xFF2E7D32)

    StepHeader(
        title = "2. Turn Off Battery Saver for reWatt",
        body = "Your phone may pause the app to save battery. Turn off Battery Optimization so reWatt keeps running at all times — even when the screen is off.",
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isIgnoring)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.errorContainer,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (isIgnoring) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (isIgnoring) green else MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = if (isIgnoring)
                    "Battery optimization is off — good!"
                else
                    "Battery optimization is ON — please turn it off",
                fontSize = 14.sp,
                color = if (isIgnoring) green else MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }

    if (!isIgnoring) {
        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Allow Always-On Access") }
    }
}

@Composable
private fun MountStep() {
    StepHeader(
        title = "3. Mount the Sensor Phone",
        body = "Use the wall mount in the kit to attach this phone to the classroom wall or whiteboard. Point the camera toward the centre of the room.",
    )

    ImagePlaceholder(caption = "Mount photo — coming soon")

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BulletTip("Attach the mount at shoulder height or above.")
            BulletTip("Make sure the camera faces the seating area.")
            BulletTip("Keep the lens clear — no tape or cover over it.")
            BulletTip("Plug in the charger so the phone stays powered.")
        }
    }
}

@Composable
private fun VerifyViewStep() {
    StepHeader(
        title = "4. Check the Sensor View",
        body = "Ask the person with the Admin phone to open the Capture tab and choose your hall. They will confirm the camera can see the whole room clearly.",
    )

    ImagePlaceholder(caption = "Example view — coming soon")

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BulletTip("The full room should be visible — no blind spots.")
            BulletTip("Avoid pointing at bright light sources.")
            BulletTip("If the angle is wrong, re-adjust the mount and check again.")
        }
    }
}

@Composable
private fun DoneStep() {
    StepHeader(
        title = "5. You're All Set!",
        body = "Everything is ready. reWatt will now watch the room and help manage energy automatically. You can come back to this guide anytime from the Settings tab.",
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
            Text(
                "Setup complete",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "Tap Done to start using reWatt.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun StepHeader(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(
            body,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun ImagePlaceholder(caption: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = caption,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BulletTip(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("•", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
