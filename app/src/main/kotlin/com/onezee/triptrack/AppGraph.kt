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
import com.onezee.triptrack.data.GeohashOf
import com.onezee.triptrack.data.LocalDataWipe
import com.onezee.triptrack.data.PreferencesDataFlags
import com.onezee.triptrack.data.RoomPlaceStore
import com.onezee.triptrack.data.PlaceManager
import com.onezee.triptrack.data.PreferencesPendingPlaceHistory
import com.onezee.triptrack.data.RoomRevealedTileStore
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
import com.onezee.triptrack.feature.atlas.AndroidAtlasLocation
import com.onezee.triptrack.feature.atlas.RevealedLayerStore
import com.onezee.triptrack.feature.atlas.WorldFeature
import com.onezee.triptrack.feature.atlas.networkOnline
import com.onezee.triptrack.feature.onboarding.OnboardingStore
import com.onezee.triptrack.feature.settings.AndroidPresentationPreferences
import com.onezee.triptrack.feature.settings.ApiProfilePrivacyService
import com.onezee.triptrack.feature.settings.RecordingSettings
import com.onezee.triptrack.feature.settings.RoomWorldChanges
import com.onezee.triptrack.feature.settings.WorldChange
import com.onezee.triptrack.feature.settings.SettingsManager
import com.onezee.triptrack.feature.settings.SyncSettingsEffects
import com.onezee.triptrack.feature.RecordingDraftQueue
import com.onezee.triptrack.feature.TripsFeature
import com.onezee.triptrack.feature.tripChanges
import com.onezee.triptrack.location.LocationGraph
import com.onezee.triptrack.logic.GeohashEncoder
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * The app's singletons, built once in `TripTrackApplication`: the shell, the data stores, the
 * recording side, the session and the sync engine of the signed-in account.
 */
// iOS: TripTrackApp.swift, StoreHealth.swift (AppBootstrap).
class AppGraph(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

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
        defaultHiddenEdgeMetres = { preferences.getString("privacy.edgeMetres", null)?.toIntOrNull() },
    )
    val database: TripTrackDatabase = TripTrackDatabase.open(context)
    val settings = RoomSettingsStore(database)
    // noBackupFilesDir, like iOS keeps TripPhotos out of the backup; it is also where the sync
    // engine's transport reads the files it uploads.
    val tripPhotoFiles = DirectoryTripPhotoFiles(File(context.noBackupFilesDir, "TripPhotos"))
    private val vehiclePhotoFiles = DirectoryVehiclePhotoFiles(File(context.noBackupFilesDir, "VehiclePhotos"))
    private val meVehiclePhotos = RoomVehiclePhotoStore(database, vehiclePhotoFiles, syncHost)
    val trips = RoomTripRepository(database, environment, tripPhotoFiles, syncHost)
    private val placeStore = RoomPlaceStore(database)
    val placeManager = PlaceManager(trips, placeStore, PreferencesPendingPlaceHistory(preferences), scope)
    val vehicles = RoomVehicleStore(database, trips, meVehiclePhotos, environment, syncHost)

    val strings: AppStrings get() = AppStrings(context)

    /** The opened world in storage: a finished trip is burnt into it, the Atlas reads it. */
    val revealedLayer = RevealedLayerStore(RoomRevealedTileStore(database), PreferencesDataFlags(context), preferences, Dispatchers.Default)

    val location = LocationGraph(
        context, database, trips, settings, vehicles, placeManager, RoomTerritoryStore(database), syncHost, environment,
        strings = { strings },
        autoTitle = ::autoTitle,
        isOffline = ::isOffline,
        reveal = revealedLayer,
    )

    val recording by lazy { com.onezee.triptrack.recording.RecordingPresenter(location, trips, location.scope) }

    // MARK: - Session

    val tokenStore = TokenStore(KeystoreTokenVault(context, apiBaseUrl))
    private val sessionEvents = SessionEvents()
    val meDiagnostics = com.onezee.triptrack.feature.settings.MeDiagnostics(File(context.noBackupFilesDir, "Diagnostics/network.log"))
    val apiClient = ApiClient(
        tokenStore, apiBaseUrl,
        onSessionExpired = sessionEvents::sessionExpired,
        onSessionRecovered = sessionEvents::sessionRecovered,
        onHardSignOut = sessionEvents::hardSignOut,
        onIdentityChanged = sessionEvents::identityChanged,
        logger = com.onezee.triptrack.network.ApiLogger(meDiagnostics::append),
    )

    @Volatile private var settingsManagerReady = false
    val settingsManager: SettingsManager = SettingsManager(
        settings, presentationPreferences, syncHost,
        SyncSettingsEffects({ syncHost.engine.value }, settings, recordingSettings(), ApiProfilePrivacyService(apiClient)),
        Dispatchers.Main.immediate,
        autoRecord = location.autoRecord,
        events = RoomWorldChanges(database).events,
    ).also { settingsManagerReady = true }

    private val profileLatch = ProfileSyncLatch(presentationPreferences)
    val accountState = AccountScopedState(database, trips, accountSettings, profileLatch)
    private val localDataWipe = LocalDataWipe(database, tripPhotoFiles, vehiclePhotoFiles, PreferencesDataFlags(context),
        afterWipe = listOf({ location.rawFixLog.wipe() }, { revealedLayer.forgetRegions() }, { placeManager.reload() }))

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
        scope = CoroutineScope(scope.coroutineContext + Dispatchers.Main.immediate),
    ).also { sessionEvents.listener = it }

    /** The trip screens: feed, trip, journey and drafts, with their view models. */
    val tripsFeature: TripsFeature by lazy {
        TripsFeature(trips, placeStore, vehicles, tripPhotoFiles, RecordingDraftQueue(location), location.recorder.state,
            tripChanges(database), preferences, syncHost, ::isOffline, placeManager = placeManager,
            placeChanges = RoomWorldChanges(database).events.filter { it == WorldChange.places }.map { },
            nameCheckpoint = location.checkpointNamer::name,
            failedSyncQueue = syncHost.queueState.map { it.failed.isNotEmpty() },
            homeZone = ::privacyZone,
            filmingMode = settingsManager.state.map { it.filmingActive }.stateIn(scope, SharingStarted.Eagerly, settingsManager.state.value.filmingActive),
            setDefaultHiddenEdge = settingsManager::setPrivacyEdgeMetres,
            deferPrivacyEdgeQuestion = settingsManager::deferPrivacyEdgeQuestion,
        ).also { feature ->
            feature.openPlace = { world.openPlace(it) }
            feature.pins = object : com.onezee.triptrack.feature.TripPins {
                // A getter: the Я features are built when the menu first asks, not with the trip screens.
                override val pinnedIds get() = me.identity.identity.map { it.pinnedTripIds }
                override suspend fun toggle(id: java.util.UUID): Boolean {
                    val pins = me.identity.identity.value.pinnedTripIds
                    if (id in pins) { me.identity.setPinnedTrips(pins - id); return true }
                    // iOS: the showcase holds three; the fourth asks to free a place.
                    if (pins.size >= 3) return false
                    me.identity.setPinnedTrips(pins + id)
                    return true
                }
            }
            feature.openVehicle = { id ->
                tabStore.select(com.onezee.triptrack.designsystem.navigation.AppTab.profile)
                me.navigator.push(com.onezee.triptrack.feature.me.MeRoute.Passport(id))
            }
            // The social half of the trip screens reaches the server through the signed-in session.
            feature.accountId = { auth.state.value.signedInAccountId }
            feature.socialApi = com.onezee.triptrack.feature.feed.ApiTripSocial(apiClient) { auth.state.value.isSignedIn }
            feature.cloudSyncEnabled = { accountSettings.cloudSyncEnabled }
            feature.remotePhotoBytes = { url -> apiClient.getBytes(url) }
        }
    }

    /** The website's pages, read only to resolve a shared link's code (`/s/`, `/j/`) to its trip or journey. */
    private val websiteClient by lazy { okhttp3.OkHttpClient.Builder().followRedirects(false).build() }

    /** The social screens: profiles, lists, maps, stats, comments, sharing, links and the inbox. */
    val social by lazy {
        com.onezee.triptrack.feature.social.SocialFeature(
            api = com.onezee.triptrack.feature.social.ApiSocial(apiClient) { auth.state.value.isSignedIn },
            trips = tripsFeature,
            auth = auth,
            accountIdSource = { auth.state.value.signedInAccountId },
            preferences = preferences,
            sharePage = { path -> websitePage(path) },
            ownAvatar = { me.identity.identity.value.avatarEmoji },
            ownCountry = { me.identity.identity.value.country },
            ownAuthor = { auth.state.value.signedInAccountId?.let { id ->
                val identity = me.identity.identity.value
                com.onezee.triptrack.model.SocialAuthor(id, identity.name, identity.avatarEmoji, identity.level)
            } },
            commentArchive = com.onezee.triptrack.feature.feed.SyncCommentArchive(
                com.onezee.triptrack.sync.DiscussionArchive(apiClient, java.io.File(context.noBackupFilesDir, "TripDiscussions"))),
            syncOwnProfile = { me.identity.syncJournal() },
            notificationPreferences = { me.notificationPreferences() },
            loadRemoteConfig = { com.onezee.triptrack.network.RemoteConfigApi(apiClient).config(BuildConfig.VERSION_NAME).config },
        ).also { it.selectTab = tabStore::select }
    }

    private suspend fun websitePage(path: String): String = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val request = okhttp3.Request.Builder().url("https://trip-track.app$path").get().build()
        websiteClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "share page ${response.code}" }
            response.body.string()
        }
    }

    /** The Atlas and Places tabs, with their view models and stacks. */
    val world: WorldFeature by lazy {
        WorldFeature(
            repository = trips, places = placeStore, territory = RoomTerritoryStore(database),
            tiles = RoomRevealedTileStore(database), revealed = revealedLayer, presentation = presentationPreferences,
            location = AndroidAtlasLocation(context),
            home = settingsManager.state.map { it.home }.stateIn(scope, SharingStarted.Eagerly, settingsManager.state.value.home),
            events = RoomWorldChanges(database).events, online = networkOnline(context, scope), trips = tripsFeature,
            saveHome = { home -> settingsManager.setHome(home) },
            placeManager = placeManager,
            filmingMode = tripsFeature.filmingMode,
            defaultHiddenEdgeMetres = { settingsManager.state.value.privacyEdgeMetres },
        ).also { it.selectTab = tabStore::select }
    }

    /** Writes stamp the settings row's id: anything writing at launch waits for it. */
    suspend fun awaitSettings() { userIdLoaded.await() }

    val me by lazy { com.onezee.triptrack.feature.me.MeFeatures(this, presentationPreferences, meVehiclePhotos,
        com.onezee.triptrack.location.world.PreferencesBadgeLedgerStore(context), context.noBackupFilesDir) }
    init { accountState.register { me.identity.purge(); me.profilePhotos.wipe(); meDiagnostics.clear(); com.onezee.triptrack.feature.me.ProfileMapCache.clear(); com.onezee.triptrack.feature.me.MePhotoCache.clear(); social.clear() } }

    /**
     * The settings row first (it is the identity), then the recording side's launch recovery. The
     * session is read at once; its engine waits for the identity, which its pending recovery needs.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        // iOS: AuthService.swift -> SentryService.setAccount; only the account UUID enters the scope.
        scope.launch {
            auth.state.map { it.accountId?.toString() }.distinctUntilChanged()
                .collect(com.onezee.triptrack.diagnostics.SentryService::setAccount)
        }
        auth.start { userIdLoaded.await() }
        scope.launch {
            val id = settings.loadOrCreate().id
            userId = id
            userIdLoaded.complete(id)
            location.start()
            // iOS MapViewModel's launch migrations: the territory of the whole library once (the
            // Atlas counts cities by it), then the one-off build of the opened world.
            RoomTerritoryStore(database).backfillIfNeeded(PreferencesDataFlags(context), GeohashOf { lat, lon -> GeohashEncoder.encode(lat, lon, 6) })
            revealedLayer.rebuildIfNeeded()
            placeManager.reconcile()
        }
        // A pull brings trips past the finish and the build: the fog reads what it brought.
        scope.launch {
            syncHost.engine.flatMapLatest { it?.coordinator?.completed ?: emptyFlow() }.collect { pulled ->
                launch { placeManager.reconcile() }
                revealedLayer.reconcile(RevealedLayerStore.ReconcileRequest.Ids(pulled.appliedTripIds.toSet()))
            }
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
        // iOS NotificationsInboxStore: the unread count is honest when the app comes back.
        scope.launch { social.inbox.refreshUnreadOnly() }
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
        defaultHiddenEdgeMetres = { preferences.getString("privacy.edgeMetres", null)?.toIntOrNull() },
        isRecording = { location.recorder.state.value.isRecording },
        syncAccountProfile = { auth.backfillNameIfNeeded(); me.identity.syncJournal(); me.profilePhotos.sync() },
        purgeVehiclePreferences = { vehicleId ->
            location.autoRecord.removeDevices(vehicleId)
        },
        reloadSettings = { settingsManager.reload() },
    )

    /** iOS: HomeSettings.activeZone. Trims public tracks only while the person has it on. */
    private fun privacyZone(): PrivacyZoneSpec? =
        settingsManager.state.value.home.activeZone?.let { PrivacyZoneSpec(it.centre, it.radius) }

    /**
     * The settings are already persisted in the shared owner; only re-arm the detectors.
     */
    private fun recordingSettings() = RecordingSettings { _, _, _ ->
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
