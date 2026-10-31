package com.onezee.triptrack.feature.account

import android.content.Context
import com.onezee.triptrack.data.DataEnvironment
import com.onezee.triptrack.data.RoomTripRepository
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.files.DirectoryTripPhotoFiles
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.feature.settings.PresentationPreferences
import com.onezee.triptrack.network.ApiClient
import com.onezee.triptrack.network.SecureRead
import com.onezee.triptrack.network.SecureStorageException
import com.onezee.triptrack.network.SecureVault
import com.onezee.triptrack.network.TokenStore
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** iOS: the AuthService test harness (Keychain ops seam, MockURLProtocol); here real Room and HTTP. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
abstract class AccountTestCase {
    protected val context: Context get() = RuntimeEnvironment.getApplication()
    protected val localUser: UUID = UUID.randomUUID()
    protected val accountA: UUID = UUID.fromString("a0000000-0000-4000-8000-00000000000a")
    protected val accountB: UUID = UUID.fromString("b0000000-0000-4000-8000-00000000000b")

    protected lateinit var db: TripTrackDatabase
    protected lateinit var server: MockWebServer
    protected lateinit var vault: MemoryVault
    protected lateinit var tokenStore: TokenStore
    protected lateinit var client: ApiClient
    protected lateinit var prefs: MemoryPreferences
    protected lateinit var accountSettings: AccountSettings
    protected lateinit var latch: ProfileSyncLatch
    protected lateinit var trips: RoomTripRepository
    protected lateinit var credentials: FakeCredentials
    protected lateinit var sync: FakeSync
    protected lateinit var auth: AuthService
    protected lateinit var scope: CoroutineScope
    private lateinit var accountDispatcher: ExecutorCoroutineDispatcher
    protected var wiped = 0
    protected var clientId = "1234-test.apps.googleusercontent.com"
    private lateinit var directory: File

    /** Path → response; unknown paths answer an envelope error so a stray call is visible. */
    protected val routes = mutableMapOf<String, () -> MockResponse>()
    protected val requests = ConcurrentLinkedQueue<RecordedRequest>()

    class MemoryVault : SecureVault {
        @Volatile var values: Map<String, String>? = null
        @Volatile var unavailable = false
        @Volatile var failWrites = false
        @Synchronized override fun read(): SecureRead<Map<String, String>> = when {
            unavailable -> SecureRead.Unavailable("locked")
            values == null -> SecureRead.Missing
            else -> SecureRead.Value(values!!)
        }
        @Synchronized override fun write(values: Map<String, String>) {
            if (unavailable || failWrites) throw SecureStorageException()
            this.values = values
        }
    }

    class MemoryPreferences : PresentationPreferences {
        val values = java.util.concurrent.ConcurrentHashMap<String, String>()
        override fun string(key: String): String? = values[key]
        override fun set(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
    }

    class FakeCredentials : GoogleCredentialSource {
        data class Ask(val flow: GoogleSignInFlow, val hashedNonce: String, val authorizedOnly: Boolean)
        val asks = CopyOnWriteArrayList<Ask>()
        val answers = ArrayDeque<GoogleCredentialResult>()
        @Volatile var cleared = 0
        override suspend fun request(context: Context, flow: GoogleSignInFlow, hashedNonce: String, authorizedOnly: Boolean): GoogleCredentialResult {
            asks += Ask(flow, hashedNonce, authorizedOnly)
            return synchronized(answers) { answers.removeFirstOrNull() } ?: GoogleCredentialResult.Cancelled
        }
        override suspend fun clear(context: Context) { cleared++ }
    }

    /** Records the session's orders to the sync engine, in order. */
    class FakeSync : AccountSyncControl {
        val calls = CopyOnWriteArrayList<String>()
        @Volatile var account: UUID? = null
        override suspend fun switchAccount(accountId: UUID?) { account = accountId; calls += "switch:$accountId" }
        override suspend fun clearAccountWork() { calls += "clear" }
        override suspend fun firstSync() { calls += "firstSync" }
        override fun requestSync() { calls += "requestSync" }
    }

    @Before fun setUpAccount() {
        directory = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        db = TripTrackDatabase.inMemory(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return routes[request.url.encodedPath]?.invoke() ?: envelopeError("NO_ROUTE")
            }
        }
        server.start()
        vault = MemoryVault()
        tokenStore = TokenStore(vault)
        prefs = MemoryPreferences()
        accountSettings = AccountSettings(prefs)
        latch = ProfileSyncLatch(prefs)
        trips = RoomTripRepository(db, DataEnvironment({ accountSettings.cloudSyncEnabled }, { localUser }), DirectoryTripPhotoFiles(File(directory, "TripPhotos")))
        credentials = FakeCredentials()
        sync = FakeSync()
        accountDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        scope = CoroutineScope(SupervisorJob() + accountDispatcher)
        buildAuth()
    }

    protected fun buildAuth() {
        val events = SessionEvents()
        client = ApiClient(
            tokenStore, server.url("/").toString(), pause = {},
            onSessionExpired = events::sessionExpired, onSessionRecovered = events::sessionRecovered,
            onHardSignOut = events::hardSignOut, onIdentityChanged = events::identityChanged,
        ).apply { refreshRecoveryDelays = emptyList() }
        auth = AuthService(
            client, tokenStore, credentials, GoogleSignInConfig(clientId, "1.0-test", "Pixel Test", isDebugBuild = true),
            localUserId = { localUser }, language = { AppLanguage.en },
            accountState = AccountScopedState(db, trips, accountSettings, latch), accountSettings = accountSettings, latch = latch,
            sync = sync, serverActions = AccountServerActions(client, db, trips, { null }),
            wipeLocalData = { wiped++ }, appContext = context, scope = scope, io = Dispatchers.IO,
        ).also { events.listener = it }
    }

    @After fun tearDownAccount() {
        // Auth's cancellation can finish a database/network operation; its fixtures still exist then.
        runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        accountDispatcher.close()
        client.close(); server.close(); db.close(); directory.deleteRecursively()
    }

    // MARK: Fixtures

    protected fun ok(payload: String? = null): MockResponse =
        MockResponse.Builder().body(if (payload == null) "{\"status\":\"ok\"}" else "{\"status\":\"ok\",\"payload\":$payload}").build()

    protected fun envelopeError(code: String): MockResponse =
        MockResponse.Builder().body("{\"status\":\"error\",\"code\":\"$code\",\"message\":\"test\"}").build()

    protected fun loginPayload(account: UUID, name: String? = null, isNew: Boolean = false, email: String? = "alex@gmail.com",
                               access: String = "access-$account", refresh: String = "refresh-$account") =
        "{\"accessToken\":\"$access\",\"refreshToken\":\"$refresh\",\"account\":{\"id\":\"$account\"," +
            (if (name != null) "\"displayName\":\"$name\"," else "\"displayName\":null,") +
            (if (email != null) "\"email\":\"$email\"," else "") +
            "\"avatarEmoji\":\"🏎️\",\"showOnPublicMap\":true},\"isNewAccount\":$isNew}"

    protected fun routeLogin(account: UUID, name: String? = null, isNew: Boolean = false) {
        routes["/auth/google/login"] = { ok(loginPayload(account, name, isNew)) }
    }

    protected fun routeQuietAccount(account: UUID) {
        routes["/auth/profile-update"] = { ok() }
        routes["/auth/me"] = { ok("{\"id\":\"$account\",\"isPublic\":true}") }
        routes["/auth/logout"] = { ok() }
    }

    /** A session as a previous launch left it, signed in to [account]. */
    protected fun seedSession(account: UUID, name: String? = "Тестовый Водитель") {
        vault.values = buildMap {
            put(TokenStore.accessTokenKey, "access-$account")
            put(TokenStore.refreshTokenKey, "refresh-$account")
            put(TokenStore.accountIdKey, account.toString())
            put(TokenStore.signedInKey, "true")
            if (name != null) put(TokenStore.userNameKey, name)
        }
        tokenStore.hydrate()
    }

    protected fun token(name: String? = "Alex Example", email: String? = "alex@gmail.com") =
        GoogleCredentialResult.Token("header.payload.signature", name, email)

    protected fun requestsTo(path: String) = requests.filter { it.url.encodedPath == path }

    protected fun awaitState(predicate: (AuthState) -> Boolean): AuthState = runBlocking {
        withTimeout(5_000) { auth.state.first(predicate) }
    }

    protected fun awaitCalls(predicate: (List<String>) -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!predicate(sync.calls.toList())) kotlinx.coroutines.delay(10) }
    }

    protected fun awaitRequests(path: String, count: Int = 1) = runBlocking {
        withTimeout(5_000) { while (requestsTo(path).size < count) kotlinx.coroutines.delay(10) }
    }
}
