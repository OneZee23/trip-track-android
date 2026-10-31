package com.onezee.triptrack

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

// iOS: StoreHealthTests.swift (AppBootstrap service-start seam).
class TestTripTrackApplication : TripTrackApplication() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val graph = lazy { AppGraph(this, scope) }
    override val appGraph: AppGraph get() = graph.value

    // Component and repository tests must not start recording recovery, auth or world migrations.
    // Screen tests request the graph lazily; their settings and UI owners still run normally.
    override fun startServices() = Unit

    override fun onTerminate() {
        scope.cancel()
        if (graph.isInitialized()) {
            appGraph.settingsManager.viewModelScope.cancel()
            appGraph.location.close()
        }
        super.onTerminate()
    }
}
