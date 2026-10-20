package com.rewatt.app.security

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive as coroutineActive
import kotlinx.coroutines.launch

/**
 * The intruder alert: red full-screen notification, torch strobe and vibration immediately, then
 * the deterrent voice "You are being recorded", then a looping siren on the ALARM stream at max
 * volume (the user's alarm volume is restored on [dismiss]).
 */
class IntrusionAlarm(
    context: Context,
    private val stateHolder: SecurityStateHolder,
    private val notifier: IntrusionNotifier,
    private val voice: DeterrentVoice,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private var siren: AudioTrack? = null
    private var strobeJob: Job? = null
    private var sirenJob: Job? = null
    private var savedAlarmVolume: Int? = null
    private var torchControl: ((Boolean) -> Unit)? = null
    private var restoreTorch: (() -> Unit)? = null

    val alarmActive: Boolean get() = stateHolder.state.value.alarmActive

    fun trigger(
        eventId: String,
        roomId: String,
        photoName: String?,
        score: Float?,
        torch: (Boolean) -> Unit,
        torchRestore: () -> Unit,
    ) {
        scope.launch {
            if (alarmActive) return@launch
            stateHolder.update { it.copy(alarmActive = true, alarmEventId = eventId) }
            torchControl = torch
            restoreTorch = torchRestore

            raiseAlarmVolume()
            notifier.showIntrusion(eventId, roomId, photoName, score)
            startVibration()
            startStrobe()

            // Deterrent first so it is heard clearly, then the siren (fallback if TTS never reports done).
            sirenJob = launch {
                delay(VOICE_TIMEOUT_MS)
                startSiren()
            }
            voice.speak {
                scope.launch {
                    sirenJob?.cancel()
                    startSiren()
                }
            }
        }
    }

    /**
     * Deterrent only — "You are being recorded" at max alarm volume, no siren, no red screen.
     * Used when a person is seen but their face isn't (back turned, fully covered, too far).
     */
    fun announceRecording() {
        scope.launch {
            if (alarmActive) return@launch // the full alarm already speaks the deterrent
            raiseAlarmVolume()
            voice.speak {
                scope.launch { if (!alarmActive) restoreAlarmVolume() }
            }
        }
    }

    fun dismiss() {
        scope.launch {
            if (!alarmActive) return@launch
            sirenJob?.cancel()
            sirenJob = null
            stopSiren()
            strobeJob?.cancel()
            strobeJob = null
            restoreTorch?.invoke()
            torchControl = null
            restoreTorch = null
            vibrator()?.cancel()
            restoreAlarmVolume()
            notifier.cancelIntrusion()
            stateHolder.update { it.copy(alarmActive = false, alarmEventId = null) }
        }
    }

    private fun startStrobe() {
        strobeJob?.cancel()
        strobeJob = scope.launch {
            var on = true
            while (coroutineActive) {
                torchControl?.invoke(on)
                on = !on
                delay(STROBE_INTERVAL_MS)
            }
        }
    }

    private fun startSiren() {
        if (siren != null || !alarmActive) return
        runCatching {
            val samples = sirenSamples()
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
                .also { track ->
                    track.write(samples, 0, samples.size)
                    track.setLoopPoints(0, samples.size, -1)
                    track.play()
                    siren = track
                }
        }.onFailure { Log.e(TAG, "Siren failed to start", it) }
    }

    private fun stopSiren() {
        siren?.let { track ->
            runCatching { track.stop() }
            track.release()
        }
        siren = null
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        val pattern = VibrationEffect.createWaveform(longArrayOf(0, 700, 300), 0)
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        runCatching { vibrator()?.vibrate(pattern, attributes) }
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Vibrator::class.java)
        }

    private fun raiseAlarmVolume() {
        runCatching {
            if (savedAlarmVolume == null) savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        }.onFailure { Log.w(TAG, "Could not raise alarm volume", it) }
    }

    private fun restoreAlarmVolume() {
        val saved = savedAlarmVolume ?: return
        runCatching { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, saved, 0) }
        savedAlarmVolume = null
    }

    /** One 1.2 s cycle of a 600 ↔ 1400 Hz sweep, slightly clipped for loudness; looped forever. */
    private fun sirenSamples(): ShortArray {
        val count = (SAMPLE_RATE * SWEEP_SECONDS).toInt()
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / count
            val tri = if (t < 0.5) t * 2 else (1 - t) * 2
            val freq = 600.0 + 800.0 * tri
            phase += 2 * PI * freq / SAMPLE_RATE
            val v = (sin(phase) * 1.4).coerceIn(-1.0, 1.0)
            out[i] = (v * Short.MAX_VALUE * 0.95).toInt().toShort()
        }
        return out
    }

    companion object {
        private const val TAG = "IntrusionAlarm"
        private const val SAMPLE_RATE = 22_050
        private const val SWEEP_SECONDS = 1.2
        private const val STROBE_INTERVAL_MS = 250L
        private const val VOICE_TIMEOUT_MS = 5_000L
    }
}
