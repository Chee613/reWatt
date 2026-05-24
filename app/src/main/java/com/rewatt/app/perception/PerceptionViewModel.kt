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
    private var perceptionSessionId: Long = 0L

    fun setPermissionState(cameraGranted: Boolean, microphoneGranted: Boolean) {
        mutableState.update {
            it.copy(
                cameraPermissionGranted = cameraGranted,
                microphonePermissionGranted = microphoneGranted,
            )
        }
    }

    fun startPerception(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        source: CaptureStartSource = CaptureStartSource.Manual,
    ) {
        stopPerception()
        val sessionId = ++perceptionSessionId
        mutableState.update {
            it.copy(
                isRunning = true,
                captureStartSource = source,
                statusMessage = when (source) {
                    CaptureStartSource.Manual -> "Manual capture starting."
                    CaptureStartSource.Auto -> "Auto capture triggered by LD2412 presence."
                },
                yoloMessage = "Loading YOLO in the background.",
                yamnetMessage = "Loading YAMNet in the background.",
            )
        }

        startControllers(lifecycleOwner, previewView)
        loadYoloDetector(sessionId)
        loadYamnetDetector(sessionId)
    }

    private fun loadYoloDetector(sessionId: Long) {
        viewModelScope.launch(Dispatchers.Default) {
            val loadedYolo = runCatching { YoloDetector.create(appContext) }
                .onFailure { error ->
                    updateYoloMessage("YOLO model failed to load: ${error.message ?: error::class.java.simpleName}")
                }
                .getOrNull()

            if (perceptionSessionId == sessionId && mutableState.value.isRunning) {
                yoloDetector = loadedYolo
                if (loadedYolo != null) {
                    updateYoloMessage("YOLO loaded; analyzing one frame every 10 minutes.")
                }
            } else {
                loadedYolo?.close()
            }
        }
    }

    private fun loadYamnetDetector(sessionId: Long) {
        viewModelScope.launch(Dispatchers.Default) {
            if (!AssetModelLoader.assetExists(appContext, YamnetDetector.MODEL_ASSET)) {
                updateYamnetMessage("Captured audio will be shown, but yamnet.tflite is missing.")
                return@launch
            }

            val loadedYamnet = runCatching { YamnetDetector.create(appContext) }
                .onFailure { error ->
                    updateYamnetMessage("YAMNet model failed to load: ${error.message ?: error::class.java.simpleName}")
                }
                .getOrNull()

            if (perceptionSessionId == sessionId && mutableState.value.isRunning) {
                yamnetDetector = loadedYamnet
                if (loadedYamnet != null) {
                    updateYamnetMessage("YAMNet loaded; recording a 15-second window every 10 minutes.")
                }
            } else {
                loadedYamnet?.close()
            }
        }
    }

    fun stopPerception() {
        perceptionSessionId++
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
                captureStartSource = null,
                statusMessage = "Perception is stopped.",
            )
        }
    }

    override fun onCleared() {
        stopPerception()
        super.onCleared()
    }

    private fun startControllers(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        cameraController = CameraCaptureController(
            context = appContext,
            yoloDetectorProvider = { yoloDetector },
            onFrameAnalyzed = { bitmap, detections, yoloMessage, exposureReading, exposureMessage ->
                mutableState.update {
                    it.copy(
                        latestFrame = bitmap ?: it.latestFrame,
                        latestFrameAtMillis = if (bitmap != null) System.currentTimeMillis() else it.latestFrameAtMillis,
                        yoloDetections = detections,
                        yoloMessage = yoloMessage,
                        exposureEv = exposureReading?.ev,
                        exposureIso = exposureReading?.iso,
                        exposureTimeSeconds = exposureReading?.exposureTimeSeconds,
                        exposureAperture = exposureReading?.aperture ?: ExposureValueCalculator.FIXED_APERTURE,
                        lightsOn = exposureReading?.lightsOn,
                        exposureMessage = exposureMessage,
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
            yamnetDetectorProvider = { yamnetDetector },
            onStatus = { message ->
                mutableState.update {
                    it.copy(
                        yamnetMessage = message,
                        statusMessage = "Perception is running.",
                    )
                }
            },
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
                activityLabels = state.soundDetections
                    .take(YamnetPostProcessor.DEFAULT_TOP_K)
                    .map { it.label },
                audioPresenceDetected = state.audioPresenceDetected,
                audioPresenceScore = state.audioPresenceScore,
                lightsOn = state.lightsOn,
                exposureEv = state.exposureEv,
                exposureIso = state.exposureIso,
                exposureTimeSeconds = state.exposureTimeSeconds,
                exposureAperture = state.exposureAperture,
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
