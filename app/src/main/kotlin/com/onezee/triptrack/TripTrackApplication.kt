package com.onezee.triptrack

import android.app.Application

class TripTrackApplication : Application() {
    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        appGraph = AppGraph()
    }
}
