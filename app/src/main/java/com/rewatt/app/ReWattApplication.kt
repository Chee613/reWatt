package com.rewatt.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.rewatt.app.core.AppGraph

class ReWattApplication : Application() {
    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        appGraph = AppGraph(this)
    }
}

