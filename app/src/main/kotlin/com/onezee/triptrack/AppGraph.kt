package com.onezee.triptrack

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.onezee.triptrack.data.DataEnvironment
import com.onezee.triptrack.data.RoomPlaceStore
import com.onezee.triptrack.data.RoomSettingsStore
import com.onezee.triptrack.data.RoomTerritoryStore
import com.onezee.triptrack.data.RoomTripRepository
import com.onezee.triptrack.data.RoomVehiclePhotoStore
import com.onezee.triptrack.data.RoomVehicleStore
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.files.DirectoryTripPhotoFiles
import com.onezee.triptrack.data.files.DirectoryVehiclePhotoFiles
import com.onezee.triptrack.data.sync.SyncRequestSink
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.strings.LocalizedDateFormatter
import com.onezee.triptrack.designsystem.theme.ThemeManager
import com.onezee.triptrack.location.LocationGraph
import com.onezee.triptrack.shell.LanguageManager
import com.onezee.triptrack.shell.TabStore
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The app's singletons, built once in `TripTrackApplication`. The shell, the data stores and the recording
 * side are wired; `core:sync` replaces [sync] with its queue when the screens arrive.
 */
class AppGraph(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // The iOS UserDefaults.standard: the app's default preferences file.
    private val preferences = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

    val themeManager = ThemeManager(preferences)
    val languageManager = LanguageManager()
    val tabStore = TabStore(preferences)


    /** The settings row's id, the identity every row is stamped with; known once [start] loaded it. */
    @Volatile
    private var userId: UUID? = null

    val sync: SyncRequestSink = SyncRequestSink.None
    val environment = DataEnvironment(
        localUserId = { checkNotNull(userId) { "the settings row is loaded before anything is recorded" } },
    )
    val database: TripTrackDatabase = TripTrackDatabase.open(context)
    val settings = RoomSettingsStore(database)
    val trips = RoomTripRepository(database, environment, DirectoryTripPhotoFiles(File(context.filesDir, "TripPhotos")), sync)
    val vehicles = RoomVehicleStore(
        database, trips, RoomVehiclePhotoStore(database, DirectoryVehiclePhotoFiles(File(context.filesDir, "VehiclePhotos")), sync),
        environment, sync,
    )

    val strings: AppStrings get() = AppStrings(context)

    val location = LocationGraph(
        context, database, trips, settings, vehicles, RoomPlaceStore(database), RoomTerritoryStore(database), sync, environment,
        strings = { strings },
        autoTitle = ::autoTitle,
        isOffline = ::isOffline,
    )

    /** The settings row first (it is the identity), then the recording side's launch recovery. */
    fun start() {
        scope.launch {
            userId = settings.loadOrCreate().id
            location.start()
        }
    }

    /** iOS: TripAutoTitle.generate, «5 Aug, 17:41» in the app's language, without the abbreviation dots. */
    private fun autoTitle(date: Instant): String {
        val language = strings.language
        return LocalizedDateFormatter.patterns("d MMM, HH:mm").getValue(language).format(date).replace(".", "")
    }

    private fun isOffline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return true
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
