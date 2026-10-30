package com.onezee.triptrack

import android.app.Application
import com.onezee.triptrack.location.LocationGraph
import com.onezee.triptrack.location.LocationGraphOwner

class TripTrackApplication : Application(), LocationGraphOwner {
    lateinit var appGraph: AppGraph
        private set

    override val locationGraph: LocationGraph get() = appGraph.location

    override fun onCreate() {
        super.onCreate()
        appGraph = AppGraph(this)
        appGraph.start()
    }
}
