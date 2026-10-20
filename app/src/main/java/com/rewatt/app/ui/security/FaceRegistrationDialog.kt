package com.rewatt.app.ui.security

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rewatt.app.R
import com.rewatt.app.security.FaceRecognizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val SHOTS_PER_PERSON = 5

/** Front-camera registration: 5 guided shots, each must contain exactly one face, saved as FAMILY. */
@Composable
fun FaceRegistrationDialog(
    viewModel: SecurityViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var shot by remember { mutableIntStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    val finished = shot > SHOTS_PER_PERSON

    DisposableEffect(Unit) {
        viewModel.beginRegistration()
        var provider: ProcessCameraProvider? = null
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val job = scope.launch {
            delay(CAMERA_HANDOFF_MS) // let the background service release the back camera
            runCatching {
                provider = ProcessCameraProvider.awaitInstance(context)
                provider?.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageCapture)
            }.onFailure {
                message = R.string.reg_error
                messageIsError = true
            }
        }
        onDispose {
            job.cancel()
            runCatching { provider?.unbind(preview, imageCapture) }
            viewModel.endRegistration()
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF09090B))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.reg_title),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.sec_close), tint = Color.White)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp)),
            ) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            }

            Text(
                text = if (finished) stringResource(R.string.reg_all_done) else stringResource(R.string.reg_step, shot, SHOTS_PER_PERSON),
                color = Color(0xFF9CA3AF),
                fontSize = 13.sp,
            )
            if (!finished) {
                Text(
                    text = stringResource(promptFor(shot)),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            message?.let {
                Text(
                    stringResource(it),
                    color = if (messageIsError) Color(0xFFFCA5A5) else Color(0xFF86EFAC),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }

            if (finished) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            shot = 1
                            message = null
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text(stringResource(R.string.reg_another), color = Color.White) }
                    Button(
                        onClick = onClose,
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF09090B)),
                    ) { Text(stringResource(R.string.reg_done), fontWeight = FontWeight.Bold) }
                }
            } else {
                Button(
                    onClick = {
                        busy = true
                        message = R.string.reg_checking
                        messageIsError = false
                        scope.launch {
                            val bitmap = imageCapture.captureBitmap(context)
                            val result = bitmap?.let { viewModel.registerFace(it) }
                            when (result) {
                                is FaceRecognizer.RegistrationResult.Ok -> {
                                    message = R.string.reg_saved
                                    messageIsError = false
                                    shot++
                                }
                                FaceRecognizer.RegistrationResult.NoFace -> { message = R.string.reg_no_face; messageIsError = true }
                                FaceRecognizer.RegistrationResult.MultipleFaces -> { message = R.string.reg_multi; messageIsError = true }
                                FaceRecognizer.RegistrationResult.TooSmall -> { message = R.string.reg_small; messageIsError = true }
                                null -> { message = R.string.reg_error; messageIsError = true }
                            }
                            bitmap?.recycle()
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF09090B)),
                ) {
                    Text(stringResource(R.string.reg_capture), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun promptFor(shot: Int): Int = when (shot) {
    1 -> R.string.reg_prompt_1
    2 -> R.string.reg_prompt_2
    3 -> R.string.reg_prompt_3
    4 -> R.string.reg_prompt_4
    else -> R.string.reg_prompt_5
}

private suspend fun ImageCapture.captureBitmap(context: android.content.Context): Bitmap? =
    suspendCancellableCoroutine { continuation ->
        takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = runCatching {
                        val raw = image.toBitmap()
                        val degrees = image.imageInfo.rotationDegrees
                        if (degrees == 0) {
                            raw
                        } else {
                            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
                                .also { if (it !== raw) raw.recycle() }
                        }
                    }.getOrNull()
                    image.close()
                    continuation.resume(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    continuation.resume(null)
                }
            },
        )
    }

private const val CAMERA_HANDOFF_MS = 600L
