package com.rewatt.app.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persisted CCTV settings. Survives app restarts so a START_STICKY service restart can re-arm. */
class SecuritySettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("rewatt_security_prefs", Context.MODE_PRIVATE)

    var armed: Boolean
        get() = prefs.getBoolean(KEY_ARMED, false)
        set(value) { prefs.edit().putBoolean(KEY_ARMED, value).apply() }

    /** Whether the 10-minute occupancy capture was running, so a sticky restart restores only what was on. */
    var perceptionActive: Boolean
        get() = prefs.getBoolean(KEY_PERCEPTION_ACTIVE, false)
        set(value) { prefs.edit().putBoolean(KEY_PERCEPTION_ACTIVE, value).apply() }

    /** Cosine similarity at or above which a face counts as FAMILY. */
    var matchThreshold: Float
        get() = prefs.getFloat(KEY_THRESHOLD, DEFAULT_MATCH_THRESHOLD)
        set(value) { prefs.edit().putFloat(KEY_THRESHOLD, value.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)).apply() }

    /** The user confirmed the phone-maker settings (lock-screen pop-ups, autostart). Not detectable by the app. */
    var phoneSetupConfirmed: Boolean
        get() = prefs.getBoolean(KEY_PHONE_SETUP, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PHONE_SETUP, value).apply()
            mutablePhoneSetup.value = value
        }

    // Shared so confirming in the Quick Setup Guide also hides the card on the Security tab.
    private val mutablePhoneSetup = MutableStateFlow(prefs.getBoolean(KEY_PHONE_SETUP, false))
    val phoneSetupConfirmedFlow: StateFlow<Boolean> = mutablePhoneSetup.asStateFlow()

    companion object {
        private const val KEY_PHONE_SETUP = "phone_setup_confirmed"
        private const val KEY_ARMED = "armed"
        private const val KEY_PERCEPTION_ACTIVE = "perception_active"
        private const val KEY_THRESHOLD = "match_threshold"

        const val DEFAULT_MATCH_THRESHOLD = 0.40f
        const val MIN_THRESHOLD = 0.30f
        const val MAX_THRESHOLD = 0.60f
    }
}
