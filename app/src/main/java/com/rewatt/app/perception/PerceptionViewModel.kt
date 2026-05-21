package com.rewatt.app.perception

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rewatt.app.data.PerceptionSyncSummary
import com.rewatt.app.data.TelemetryRepository
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PerceptionViewModel(
    private val appContext: Context,
    private val telemetryRepository: TelemetryRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PerceptionUiState())
    val uiState: StateFlow<PerceptionUiState> = mutableState.asStateFlow()

    private var yoloDetector: YoloDetector? = null
    private var yamnetDetector: YamnetDetector? = null
    private var cameraController: CameraCaptureController? = null
    private var audioController: AudioCaptureController? = null

    fun setPermissionState(cameraGranted: Boolean, microphoneGranted: Boolean) {
        mutableState.update {
            it.copy(
                cameraPermissionGranted = cameraGranted,
                microphonePermissionGranted = microphoneGranted,
            )
        }
    }

    fun startPerception(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        stopPerception()
        mutableState.update {
            it.copy(
                isRunning = true,
                statusMessage = "Starting camera and microphone capture.",
                yoloMessage = "Loading YOLO.",
                yamnetMessage = "Loading YAMNet.",
            )
        }

        viewModelScope.launch(Dispatchers.Default) {
            val yolo = runCatching { YoloDetector.create(appContext) }
                .onFailure { error ->
                    updateYoloMessage("YOLO model failed to load: ${error.message ?: error::class.java.simpleName}")
                }
                .getOrNull()
            val yamnet = if (AssetModelLoader.assetExists(appContext, YamnetDetector.MODEL_ASSET)) {
                runCatching { YamnetDetector.create(appContext) }
                    .onFailure { error ->
                        updateYamnetMessage("YAMNet model failed to load: ${error.message ?: error::class.java.simpleName}")
                    }
                    .getOrNull()
            } else {
                updateYamnetMessage("Captured audio will be shown, but yamnet.tflite is missing.")
                null
            }

            yoloDetector = yolo
            yamnetDetector = yamnet

            viewModelScope.launch(Dispatchers.Main) {
                startControllers(lifecycleOwner, previewView)
            }
        }
    }

    fun stopPerception() {
        cameraController?.close()
        cameraController = null
        audioController?.close()
        audioController = null
        yoloDetector?.close()
        yoloDetector = null
        yamnetDetector?.close()
        yamnetDetector = null
        mutableState.update {
            it.copy(
                isRunning = false,
                statusMessage = "Perception is stopped.",
            )
        }
    }

    override fun onCleared() {
        stopPerception()
        super.onCleared()
    }

    private fun startControllers(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val yolo = yoloDetector
        val yamnet = yamnetDetector

        cameraController = CameraCaptureController(
            context = appContext,
            yoloDetector = yolo,
            onFrameAnalyzed = { bitmap, detections, message ->
                mutableState.update {
                    it.copy(
                        latestFrame = bitmap ?: it.latestFrame,
                        latestFrameAtMillis = if (bitmap != null) System.currentTimeMillis() else it.latestFrameAtMillis,
                        yoloDetections = detections,
                        yoloMessage = message,
                        statusMessage = "Perception is running.",
                    )
                        .also(::publishPerceptionSummary)
                }
            },
            onError = { message ->
                mutableState.update { it.copy(yoloMessage = message) }
            },
        ).also { it.start(lifecycleOwner, previewView) }

        audioController = AudioCaptureController(
            context = appContext,
            scope = viewModelScope,
            yamnetDetector = yamnet,
            onAudioCaptured = { result ->
                mutableState.update {
                    it.copy(
                        latestAudioAtMillis = result.capturedAtMillis,
                        audioSampleCount = result.sampleCount,
                        audioRms = result.rms,
                        audioWaveform = result.waveform,
                        soundDetections = result.detections,
                        yamnetMessage = result.message,
                        statusMessage = "Perception is running.",
                    )
                        .also(::publishPerceptionSummary)
                }
            },
            onError = { message ->
                mutableState.update { it.copy(yamnetMessage = message) }
            },
        ).also { it.start() }
    }

    private fun updateYoloMessage(message: String) {
        mutableState.update { it.copy(yoloMessage = message) }
    }

    private fun updateYamnetMessage(message: String) {
        mutableState.update { it.copy(yamnetMessage = message) }
    }

    private fun publishPerceptionSummary(state: PerceptionUiState) {
        telemetryRepository.updatePerceptionSummary(
            PerceptionSyncSummary(
                personCount = state.personCount,
                topAudioLabels = state.soundDetections
                    .take(3)
                    .map { it.label },
                capturedAtMillis = listOfNotNull(
                    state.latestFrameAtMillis,
                    state.latestAudioAtMillis,
                ).maxOrNull(),
            ),
        )
    }
}

class PerceptionViewModelFactory(
    private val appContext: Context,
    private val telemetryRepository: TelemetryRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PerceptionViewModel::class.java)) {
            return PerceptionViewModel(
                appContext = appContext.applicationContext,
                telemetryRepository = telemetryRepository,
            ) as T
        }
        error("Unknown ViewModel class: ${modelClass.name}")
    }
}
