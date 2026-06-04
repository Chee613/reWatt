package com.rewatt.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.rewatt.app.R
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.graphics.ImageDecoder
import androidx.compose.foundation.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TOTAL_STEPS = 6

@Composable
fun SetupGuideDialog(onDismiss: (spaceName: String?, capacity: Int?) -> Unit) {
    var step by remember { mutableStateOf(0) }
    var spaceName by remember { mutableStateOf("") }
    var capacityText by remember { mutableStateOf("") }
    var isProcessingSwipe by remember { mutableStateOf(false) }
    val capacity = capacityText.toIntOrNull()

    Dialog(
        onDismissRequest = { onDismiss(null, null) },
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
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.setup_title), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    TextButton(onClick = { onDismiss(null, null) }) { Text(stringResource(R.string.setup_skip)) }
                }

                Spacer(Modifier.height(8.dp))

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
                        stringResource(R.string.setup_step_counter, step + 1, TOTAL_STEPS),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Step content (scrollable with swipe navigation)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                if (!isProcessingSwipe) {
                                    when {
                                        dragAmount > 100 && step > 0 -> {
                                            step--
                                            isProcessingSwipe = true
                                        }
                                        dragAmount < -100 && step < TOTAL_STEPS - 1 -> {
                                            step++
                                            isProcessingSwipe = true
                                        }
                                    }
                                }
                            }
                        },
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (step) {
                        0 -> PermissionsStep()
                        1 -> BatteryStep()
                        2 -> MountStep()
                        3 -> VerifyViewStep()
                        4 -> NameYourSpaceStep(
                            spaceName = spaceName,
                            onSpaceNameChange = { spaceName = it },
                            capacityText = capacityText,
                            onCapacityChange = { capacityText = it },
                        )
                        5 -> DoneStep()
                    }
                }

                LaunchedEffect(step) {
                    isProcessingSwipe = false
                }

                Spacer(Modifier.height(8.dp))

                // Navigation buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (step > 0) {
                        OutlinedButton(
                            onClick = { step-- },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.setup_back)) }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Button(
                        onClick = {
                            if (step < TOTAL_STEPS - 1) step++ else onDismiss(spaceName.ifBlank { null }, capacity)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !(step == 4 && spaceName.isBlank()),
                    ) { Text(if (step == TOTAL_STEPS - 1) stringResource(R.string.setup_done) else stringResource(R.string.setup_next)) }
                }
            }
        }
    }
}

// Sequential request states so the system permission dialogs always appear in a fixed
// order. RequestMultiplePermissions does NOT guarantee dialog order, so we chain
// single-permission requests instead.
private const val REQUEST_IDLE = 0
private const val REQUEST_CAMERA = 1
private const val REQUEST_LOCATION = 2

@Composable
private fun PermissionsStep() {
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    // One launcher per permission so we can chain them in a guaranteed order.
    var requestStep by remember { mutableStateOf(REQUEST_IDLE) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraGranted = granted
        requestStep = REQUEST_LOCATION
    }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        locationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        requestStep = REQUEST_IDLE
    }

    // Drive the chain: each step launches its request, or skips ahead when already granted.
    LaunchedEffect(requestStep) {
        when (requestStep) {
            REQUEST_CAMERA ->
                if (cameraGranted) requestStep = REQUEST_LOCATION
                else cameraLauncher.launch(Manifest.permission.CAMERA)
            REQUEST_LOCATION ->
                if (locationGranted) requestStep = REQUEST_IDLE
                else locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            else -> Unit
        }
    }

    StepHeader(
        title = stringResource(R.string.setup_s1_title),
        body = stringResource(R.string.setup_s1_body),
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PermissionRow(label = stringResource(R.string.setup_perm_camera), granted = cameraGranted)
            PermissionRow(label = stringResource(R.string.setup_perm_location), granted = locationGranted)
        }
    }

    if (!cameraGranted || !locationGranted) {
        Button(
            onClick = { requestStep = REQUEST_CAMERA },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.setup_allow_permissions)) }
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
            text = if (granted) stringResource(R.string.setup_allowed) else stringResource(R.string.setup_not_allowed),
            fontSize = 13.sp,
            color = if (granted) green else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun BatteryStep() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    fun checkIgnoring(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }

    // Re-check on resume so the status updates the moment the user returns from system settings.
    var isIgnoring by remember { mutableStateOf(checkIgnoring()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isIgnoring = checkIgnoring()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val green = Color(0xFF2E7D32)

    StepHeader(
        title = stringResource(R.string.setup_s2_title),
        body = stringResource(R.string.setup_s2_body),
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
                    stringResource(R.string.setup_batt_off)
                else
                    stringResource(R.string.setup_batt_on),
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
        ) { Text(stringResource(R.string.setup_allow_always_on)) }
    }
}

@Composable
private fun MountStep() {
    StepHeader(
        title = stringResource(R.string.setup_s3_title),
        body = stringResource(R.string.setup_s3_body),
    )

    MountPhoto()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BulletTip(stringResource(R.string.setup_s3_tip1))
            BulletTip(stringResource(R.string.setup_s3_tip2))
            BulletTip(stringResource(R.string.setup_s3_tip3))
            BulletTip(stringResource(R.string.setup_s3_tip4))
        }
    }
}

@Composable
private fun VerifyViewStep() {
    StepHeader(title = stringResource(R.string.setup_s4_title), body = null)

    CameraPreviewBox()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BulletTip(stringResource(R.string.setup_s4_tip1))
            BulletTip(stringResource(R.string.setup_s4_tip2))
            BulletTip(stringResource(R.string.setup_s4_tip3))
        }
    }
}

@Composable
private fun NameYourSpaceStep(
    spaceName: String,
    onSpaceNameChange: (String) -> Unit,
    capacityText: String,
    onCapacityChange: (String) -> Unit,
) {
    StepHeader(
        title = stringResource(R.string.setup_s5_title),
        body = stringResource(R.string.setup_s5_body),
    )

    OutlinedTextField(
        value = spaceName,
        onValueChange = { if (it.length <= 40) onSpaceNameChange(it) },
        label = { Text(stringResource(R.string.setup_space_name)) },
        placeholder = { Text(stringResource(R.string.setup_space_name_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = capacityText,
        onValueChange = { input -> onCapacityChange(input.filter { it.isDigit() }.take(5)) },
        label = { Text(stringResource(R.string.setup_capacity)) },
        placeholder = { Text(stringResource(R.string.setup_capacity_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DoneStep() {
    StepHeader(
        title = stringResource(R.string.setup_s6_title),
        body = stringResource(R.string.setup_s6_body),
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
                stringResource(R.string.setup_complete),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                stringResource(R.string.setup_complete_desc),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun StepHeader(title: String, body: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        if (!body.isNullOrBlank()) {
            Text(
                body,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun ImagePlaceholder(caption: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 180.dp)
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

@Composable
private fun MountPhoto() {
    val context = LocalContext.current
    val imageBitmap = produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null) {
        try {
            val assetManager = context.assets
            val file = java.io.File(context.cacheDir, "mount_photo.heic")
            assetManager.open("mount_photo.heic").use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val source = ImageDecoder.createSource(file)
            val bitmap = ImageDecoder.decodeBitmap(source)
            value = bitmap.asImageBitmap()
        } catch (e: Exception) {
            // Fallback to placeholder if image fails to load
            value = null
        }
    }
    
    if (imageBitmap.value != null) {
        Image(
            bitmap = imageBitmap.value!!,
            contentDescription = "Mount photo",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        ImagePlaceholder("Loading mount photo...")
    }
}

@Composable
private fun CameraPreviewBox() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }
    
    // Check camera permission and request if needed
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasPermission) {
            hasCameraPermission = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (hasCameraPermission) {
                if (cameraError != null) {
                    ImagePlaceholder("Camera Error: ${cameraError}")
                } else {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                
                                // Bind camera with delay to ensure lifecycle is ready
                                post {
                                    try {
                                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                        cameraProviderFuture.addListener({
                                            try {
                                                val cameraProvider = cameraProviderFuture.get()
                                                
                                                // Check if camera is available
                                                val availableCameras = cameraProvider.availableCameraInfos
                                                if (availableCameras.isEmpty()) {
                                                    cameraError = "No cameras available"
                                                    return@addListener
                                                }
                                                
                                                val preview = Preview.Builder().build().also {
                                                    it.surfaceProvider = surfaceProvider
                                                }
                                                
                                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                                
                                                cameraProvider.unbindAll()
                                                cameraProvider.bindToLifecycle(
                                                    lifecycleOwner,
                                                    cameraSelector,
                                                    preview
                                                )
                                            } catch (e: Exception) {
                                                cameraError = e.message ?: "Camera binding failed"
                                            }
                                        }, ContextCompat.getMainExecutor(ctx))
                                    } catch (e: Exception) {
                                        cameraError = e.message ?: "Failed to get camera provider"
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 240.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                    )
                }
            } else {
                ImagePlaceholder("Requesting camera access...")
            }
        }
    }
}
