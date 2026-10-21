package com.rewatt.app.safety

import com.rewatt.app.data.MqttSyncRepository
import com.rewatt.app.security.AlarmOutputs
import com.rewatt.app.security.AlarmReason
import com.rewatt.app.security.IntrusionNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class FireAlarmState(
    val active: Boolean = false,
    val vocIndex: Int? = null,
    val noxIndex: Int? = null,
    val atMillis: Long? = null,
    val test: Boolean = false,
)

/**
 * Fire alert: siren at max alarm volume + vibration + torch strobe ([AlarmOutputs]), red full-screen
 * FIRE ALERT over the lock screen, and an MQTT message on `rewatt/sensors/<room>/fire`. No voice.
 */
class FireAlarm(
    private val outputs: AlarmOutputs,
    private val notifier: IntrusionNotifier,
    private val torch: TorchRouter,
    private val mqtt: MqttSyncRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutableState = MutableStateFlow(FireAlarmState())
    val state: StateFlow<FireAlarmState> = mutableState.asStateFlow()

    fun trigger(vocIndex: Int?, noxIndex: Int?, test: Boolean = false, publish: Boolean = !test) {
        scope.launch {
            if (mutableState.value.active) return@launch
            val now = System.currentTimeMillis()
            mutableState.value = FireAlarmState(true, vocIndex, noxIndex, now, test)
            outputs.begin(AlarmReason.FIRE, torch = { on -> torch.set(on) }, torchRestore = { torch.set(false) })
            outputs.startSiren()
            notifier.showFire(mqtt.roomId, vocIndex, noxIndex, test)
            if (publish) publish(vocIndex, noxIndex, now)
        }
    }

    fun dismiss() {
        scope.launch {
            if (!mutableState.value.active) return@launch
            outputs.end(AlarmReason.FIRE)
            notifier.cancelFire()
            mutableState.value = mutableState.value.copy(active = false)
        }
    }

    private fun publish(vocIndex: Int?, noxIndex: Int?, atMillis: Long) {
        val payload = JSONObject().apply {
            put("nodeId", mqtt.nodeId)
            put("roomId", mqtt.roomId)
            put("timestamp", atMillis)
            put("vocIndex", vocIndex ?: JSONObject.NULL)
            put("noxIndex", noxIndex ?: JSONObject.NULL)
        }
        mqtt.publishJson(mqtt.topicForRoom(mqtt.roomId, "fire"), payload.toString())
    }
}
