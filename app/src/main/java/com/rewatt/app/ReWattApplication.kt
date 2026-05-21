package com.rewatt.app

import android.app.Application
import com.rewatt.app.core.AppGraph

class ReWattApplication : Application() {
    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        appGraph = AppGraph(this)
    }
}

