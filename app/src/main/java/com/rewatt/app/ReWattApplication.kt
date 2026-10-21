package com.rewatt.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.firebase.FirebaseApp
import com.rewatt.app.core.AppGraph
import com.rewatt.app.core.LocaleManager

class ReWattApplication : Application() {
    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        appGraph = AppGraph(this)
        // Fire detection is always on whenever SGP41 gas data arrives (no-op for modules without it).
        appGraph.fireMonitor.start()

        // Default the app to English until the user explicitly picks a language in Settings,
        // regardless of the device's system locale (we don't auto-switch to Malay).
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(LocaleManager.ENGLISH))
        }
    }
}

