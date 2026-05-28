package com.rewatt.app.perception

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.Closeable
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class AudioCaptureController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val yamnetDetectorProvider: () -> YamnetDetector?,
    private val onStatus: (String) -> Unit,
    private val onAudioCaptured: (AudioCaptureResult) -> Unit,
    private val onError: (String) -> Unit,
) : Closeable {
    private var captureJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private val triggerChannel = Channel<Unit>(Channel.CONFLATED)

    @SuppressLint("MissingPermission")
    fun start() {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onError("Microphone permission is not granted.")
            return
        }

        captureJob = scope.launch(Dispatchers.IO) {
            val sampleCount = RECORDING_SAMPLE_COUNT
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBufferSize <= 0) {
                onError("Microphone buffer could not be created.")
                return@launch
            }

            val record = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBufferSize, sampleCount * BYTES_PER_SAMPLE * 2))
                .build()
            audioRecord = record

            runCatching {
                record.startRecording()
                val shortBuffer = ShortArray(sampleCount)
                while (isActive) {
                    // Hold until the next 10-minute wall-clock mark, or until the debug
                    // "Capture now" trigger fires — whichever happens first.
                    onStatus("Waiting for the next 10-minute mark to record audio.")
                    withTimeoutOrNull(millisUntilNextBoundary(RECORDING_INTERVAL_MINUTES)) {
                        triggerChannel.receive()
                    }
                    if (!isActive) break
                    onStatus("Recording audio for 15 seconds.")
                    // Discard audio buffered during the wait so the window is fresh at the mark.
                    record.drainStale(shortBuffer)
                    val readCount = record.readFully(shortBuffer, sampleCount)
                    if (readCount <= 0) {
                        onError("Microphone read failed.")
                        delay(1_000L)
                        continue
                    }

                    val samples = FloatArray(readCount) { index ->
                        shortBuffer[index] / Short.MAX_VALUE.toFloat()
                    }
                    val yamnetDetector = yamnetDetectorProvider()
                    val detections = runCatching {
                        yamnetDetector?.detect(samples).orEmpty()
                    }.getOrElse { error ->
                        onError("YAMNet failed: ${error.message ?: error::class.java.simpleName}")
                        emptyList()
                    }

                    onAudioCaptured(
                        AudioCaptureResult(
                            capturedAtMillis = System.currentTimeMillis(),
                            sampleCount = readCount,
                            rms = samples.rms(),
                            waveform = samples.toWaveformBuckets(),
                            detections = detections,
                            message = when {
                                yamnetDetector == null -> "Captured audio; YAMNet is still loading or unavailable."
                                detections.isEmpty() -> "Captured audio; no human activity sound above threshold."
                                else -> "Captured audio; ${detections.size} human activity sound(s)."
                            },
                        ),
                    )
                }
            }.onFailure { error ->
                onError("Audio capture failed: ${error.message ?: error::class.java.simpleName}")
            }
        }
    }

    /** Debug bypass: record one window immediately instead of waiting for the next mark. */
    fun captureNow() {
        triggerChannel.trySend(Unit)
    }

    override fun close() {
        captureJob?.cancel()
        captureJob = null
        audioRecord?.let { record ->
            runCatching { record.stop() }
            record.release()
        }
        audioRecord = null
    }

    /** Drain any samples buffered during the idle wait so the next read starts fresh. */
    private fun AudioRecord.drainStale(scratch: ShortArray) {
        while (true) {
            val read = read(scratch, 0, scratch.size, AudioRecord.READ_NON_BLOCKING)
            if (read <= 0) break
        }
    }

    private fun AudioRecord.readFully(target: ShortArray, sampleCount: Int): Int {
        var totalRead = 0
        while (totalRead < sampleCount && captureJob?.isActive == true) {
            val read = read(target, totalRead, sampleCount - totalRead, AudioRecord.READ_BLOCKING)
            if (read <= 0) return read
            totalRead += read
        }
        return totalRead
    }

    companion object {
        private const val SAMPLE_RATE_HZ = 16_000
        private const val RECORDING_INTERVAL_MINUTES = 10
        private const val RECORDING_SAMPLE_COUNT = SAMPLE_RATE_HZ * 15
        private const val BYTES_PER_SAMPLE = 2
    }
}

data class AudioCaptureResult(
    val capturedAtMillis: Long,
    val sampleCount: Int,
    val rms: Float,
    val waveform: List<Float>,
    val detections: List<SoundDetection>,
    val message: String,
)

private fun FloatArray.rms(): Float {
    if (isEmpty()) return 0f
    val meanSquare = fold(0.0) { total, value -> total + value * value } / size
    return sqrt(meanSquare).toFloat().coerceIn(0f, 1f)
}

private fun FloatArray.toWaveformBuckets(bucketCount: Int = 32): List<Float> {
    if (isEmpty()) return emptyList()
    val bucketSize = (size / bucketCount).coerceAtLeast(1)
    return (0 until bucketCount).map { bucketIndex ->
        val start = bucketIndex * bucketSize
        if (start >= size) return@map 0f
        val end = minOf(start + bucketSize, size)
        var max = 0f
        for (index in start until end) {
            max = maxOf(max, kotlin.math.abs(this[index]))
        }
        max.coerceIn(0f, 1f)
    }
}
