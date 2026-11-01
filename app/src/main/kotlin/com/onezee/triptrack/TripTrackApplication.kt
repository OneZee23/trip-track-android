package com.onezee.triptrack

import android.app.Application
import androidx.work.Configuration
import com.onezee.triptrack.feature.account.AccountSyncWorkerFactory
import com.onezee.triptrack.location.LocationGraph
import com.onezee.triptrack.location.LocationGraphOwner

/**
 * Also WorkManager's configuration: core:sync removes the default initializer, and the sync worker
 * has to find the CURRENT account's coordinator through the app graph (custom initialization).
 */
// iOS: TripTrackApp.swift, StoreHealth.swift (AppBootstrap).
open class TripTrackApplication : Application(), LocationGraphOwner, Configuration.Provider {
    // Robolectric's test Application can supply screen dependencies without booting the services.
    open val appGraph: AppGraph by lazy { AppGraph(this) }

    override val locationGraph: LocationGraph get() = appGraph.location

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(AccountSyncWorkerFactory { appGraph.syncHost.awaitCoordinator() })
            .build()

    override fun onCreate() {
        super.onCreate()
        startServices()
    }

    protected open fun startServices() {
        com.onezee.triptrack.diagnostics.SentryService.start(this)
        appGraph.start()
    }
}
