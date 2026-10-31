package com.onezee.triptrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.onezee.triptrack.data.DataEnvironment
import com.onezee.triptrack.data.LocalDataWipe
import com.onezee.triptrack.data.PreferencesDataFlags
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
import com.onezee.triptrack.feature.account.AccountScopedState
import com.onezee.triptrack.feature.account.AccountScreenSources
import com.onezee.triptrack.feature.account.AccountServerActions
import com.onezee.triptrack.feature.account.AccountSettings
import com.onezee.triptrack.feature.account.AccountViewModel
import com.onezee.triptrack.feature.account.AuthService
import com.onezee.triptrack.feature.account.CredentialManagerGoogleSource
import com.onezee.triptrack.feature.account.GoogleSignInConfig
import com.onezee.triptrack.feature.account.ProfileSyncLatch
import com.onezee.triptrack.feature.account.SessionEvents
import com.onezee.triptrack.feature.account.SyncCounts
import com.onezee.triptrack.feature.account.SyncHost
import com.onezee.triptrack.feature.onboarding.OnboardingStore
import com.onezee.triptrack.feature.settings.AndroidPresentationPreferences
import com.onezee.triptrack.feature.settings.ApiProfilePrivacyService
import com.onezee.triptrack.feature.settings.RecordingSettings
import com.onezee.triptrack.feature.settings.RoomWorldChanges
import com.onezee.triptrack.feature.settings.SettingsManager
import com.onezee.triptrack.feature.settings.SyncSettingsEffects
import com.onezee.triptrack.location.LocationGraph
import com.onezee.triptrack.model.EmptyRequest
import com.onezee.triptrack.model.SocialBlockedListResponse
import com.onezee.triptrack.network.ApiClient
import com.onezee.triptrack.network.ApiEndpoint
import com.onezee.triptrack.network.KeystoreTokenVault
import com.onezee.triptrack.network.TokenStore
import com.onezee.triptrack.shell.LanguageManager
import com.onezee.triptrack.shell.TabStore
import com.onezee.triptrack.sync.LastSyncedAtStore
import com.onezee.triptrack.sync.PrivacyZoneSpec
import com.onezee.triptrack.sync.SyncEngine
import com.onezee.triptrack.sync.SyncScheduler
import com.onezee.triptrack.sync.SyncStoreIdentity
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * The app's singletons, built once in `TripTrackApplication`: the shell, the data stores, the
 * recording side, the session and the sync engine of the signed-in account.
 */
class AppGraph(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // The iOS UserDefaults.standard: the app's default preferences file.
    private val preferences = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
    private val presentationPreferences = AndroidPresentationPreferences(preferences)

    val themeManager = ThemeManager(preferences)
    val languageManager = LanguageManager()
    val tabStore = TabStore(preferences)
    val onboarding = OnboardingStore(preferences)

    /** The settings row's id, the identity every row is stamped with; known once [start] loaded it. */
    @Volatile
    private var userId: UUID? = null
    private val userIdLoaded = CompletableDeferred<UUID>()

    /** Debug builds may point at a local or staging backend (docs/sign-in.md). */
    val apiBaseUrl: String = BuildConfig.API_BASE_URL
    private val apiScope: String = apiBaseUrl.toHttpUrl().host

    val accountSettings: AccountSettings = AccountSettings(presentationPreferences) { if (settingsManagerReady) settingsManager.reload() }

    /**
     * Every repository write goes through the signed-in account's sync engine, where the gates
     * decide what may leave the phone. One engine per account: see [SyncHost].
     */
    val syncHost: SyncHost = SyncHost(
        createEngine = ::createSyncEngine,
        cancelScheduledWork = {
            WorkManager.getInstance(context).run {
                cancelUniqueWork(SyncScheduler.immediateName)
                cancelUniqueWork(SyncScheduler.periodicName)
            }
        },
        cloudSyncEnabled = { accountSettings.cloudSyncEnabled },
        scope = scope,
    )
    val sync: SyncRequestSink get() = syncHost

    val environment: DataEnvironment = DataEnvironment(
        cloudSyncEnabled = { accountSettings.cloudSyncEnabled },
        localUserId = { checkNotNull(userId) { "the settings row is loaded before anything is recorded" } },
        homeZoneActive = { privacyZone() != null },
    )
    val database: TripTrackDatabase = TripTrackDatabase.open(context)
    val settings = RoomSettingsStore(database)
    // noBackupFilesDir, like iOS keeps TripPhotos out of the backup; it is also where the sync
    // engine's transport reads the files it uploads.
    private val tripPhotoFiles = DirectoryTripPhotoFiles(File(context.noBackupFilesDir, "TripPhotos"))
    private val vehiclePhotoFiles = DirectoryVehiclePhotoFiles(File(context.noBackupFilesDir, "VehiclePhotos"))
    val trips = RoomTripRepository(database, environment, tripPhotoFiles, syncHost)
    val vehicles = RoomVehicleStore(database, trips, RoomVehiclePhotoStore(database, vehiclePhotoFiles, syncHost), environment, syncHost)

    val strings: AppStrings get() = AppStrings(context)

    val location = LocationGraph(
        context, database, trips, settings, vehicles, RoomPlaceStore(database), RoomTerritoryStore(database), syncHost, environment,
        strings = { strings },
        autoTitle = ::autoTitle,
        isOffline = ::isOffline,
    )

    // MARK: - Session

    val tokenStore = TokenStore(KeystoreTokenVault(context, apiBaseUrl))
    private val sessionEvents = SessionEvents()
    val apiClient = ApiClient(
        tokenStore, apiBaseUrl,
        onSessionExpired = sessionEvents::sessionExpired,
        onSessionRecovered = sessionEvents::sessionRecovered,
        onHardSignOut = sessionEvents::hardSignOut,
        onIdentityChanged = sessionEvents::identityChanged,
    )

    @Volatile private var settingsManagerReady = false
    val settingsManager: SettingsManager = SettingsManager(
        settings, presentationPreferences, syncHost,
        SyncSettingsEffects({ syncHost.engine.value }, settings, recordingSettings(), ApiProfilePrivacyService(apiClient)),
        Dispatchers.Main.immediate,
        events = RoomWorldChanges(database).events,
    ).also { settingsManagerReady = true }

    private val profileLatch = ProfileSyncLatch(presentationPreferences)
    val accountState = AccountScopedState(database, trips, accountSettings, profileLatch)
    private val localDataWipe = LocalDataWipe(database, tripPhotoFiles, vehiclePhotoFiles, PreferencesDataFlags(context),
        afterWipe = listOf({ location.rawFixLog.wipe() }))

    val auth: AuthService = AuthService(
        client = apiClient,
        tokenStore = tokenStore,
        credentials = CredentialManagerGoogleSource(BuildConfig.GOOGLE_WEB_CLIENT_ID),
        config = GoogleSignInConfig(BuildConfig.GOOGLE_WEB_CLIENT_ID, BuildConfig.VERSION_NAME, Build.MODEL, BuildConfig.DEBUG),
        localUserId = { userIdLoaded.await() },
        language = { strings.language },
        accountState = accountState,
        accountSettings = accountSettings,
        latch = profileLatch,
        sync = syncHost,
        serverActions = AccountServerActions(apiClient, database, trips, ::privacyZone),
        wipeLocalData = { localDataWipe.run() },
        appContext = context,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    ).also { sessionEvents.listener = it }

    /**
     * The settings row first (it is the identity), then the recording side's launch recovery, then
     * the session, whose engine needs the identity for its pending recovery.
     */
    fun start() {
        scope.launch {
            val id = settings.loadOrCreate().id
            userId = id
            userIdLoaded.complete(id)
            location.start()
            auth.start()
        }
        // A launch before the first unlock reads the session as «unknown»; read it again then.
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = auth.onForeground()
        }, IntentFilter(Intent.ACTION_USER_UNLOCKED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** The app came to the front: the 300-second timer, a pass, and a silent revival of a soft expiry. */
    fun onForeground() {
        syncHost.onForeground()
        auth.onForeground()
    }

    fun onBackground() = syncHost.onBackground()

    /** «Account & Sync»'s state, one per opening of the screen. */
    fun accountViewModel(): AccountViewModel =
        AccountViewModel(auth, settingsManager, syncHost, presentationPreferences, accountSources, Dispatchers.Main.immediate)

    private val accountSources = object : AccountScreenSources {
        override suspend fun avatarEmoji(): String? = settings.loadOrCreate().avatarEmoji
        override suspend fun counts(): SyncCounts = SyncCounts.read(database)
        override suspend fun lastSyncedAt(): Instant? {
            val account = tokenStore.state.value.accountId ?: return null
            return LastSyncedAtStore(context.getSharedPreferences("triptrack.sync", Context.MODE_PRIVATE), apiScope)
                .get(account, SyncStoreIdentity.get(database))
        }
        override suspend fun blockedCount(): Int? = try {
            apiClient.post<EmptyRequest, SocialBlockedListResponse>(ApiEndpoint.socialBlocked, EmptyRequest()).users.size
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun createSyncEngine(): SyncEngine = SyncEngine.create(
        context = context,
        db = database,
        client = apiClient,
        apiScope = apiScope,
        localUserId = { checkNotNull(userId) },
        cloudSyncEnabled = { accountSettings.cloudSyncEnabled },
        zone = ::privacyZone,
        isRecording = { location.recorder.state.value.isRecording },
        syncAccountProfile = { auth.backfillNameIfNeeded() },
        purgeVehiclePreferences = { vehicleId ->
            location.autoRecord.removeDevices(vehicleId)
            settingsManager.removeStereos(vehicleId)
        },
        reloadSettings = { settingsManager.reload() },
    )

    /** iOS: HomeSettings.activeZone. Trims public tracks only while the person has it on. */
    private fun privacyZone(): PrivacyZoneSpec? =
        settingsManager.state.value.home.activeZone?.let { PrivacyZoneSpec(it.centre, it.radius) }

    /**
     * The recording owner applies the auto-record settings to its own store and detectors.
     */
    private fun recordingSettings() = RecordingSettings { mode, timeout, stereos ->
        location.autoRecord.mode = mode
        location.autoRecord.autoStopTimeout = timeout
        location.autoRecord.savedDevices = stereos
        location.rearmAutoRecord()
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
