// iOS: Networking/APIClient.swift
package com.onezee.triptrack.network

import com.onezee.triptrack.model.*
import com.onezee.triptrack.model.serialization.IsoInstantSerializer
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class ApiClient(
    val tokenStore: TokenStore,
    baseUrl: String = ApiEndpoint.defaultBaseUrl,
    httpClient: OkHttpClient = OkHttpClient.Builder().connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build(),
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val logger: ApiLogger = ApiLogger(),
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val onSessionExpired: () -> Unit = {},
    private val onSessionRecovered: () -> Unit = {},
    private val onHardSignOut: (HardSignOutReason) -> Unit = {},
    private val onIdentityChanged: (previous: UUID?, current: UUID) -> Unit = { _, _ -> },
) : Closeable {
    private val base = baseUrl.toHttpUrl()
    init { require(tokenStore.apiHost == null || tokenStore.apiHost.equals(base.host, ignoreCase = true)) }
    // A failed shared refresh must not cancel the app or its other requests.
    private val refreshScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    // Hidden retries would duplicate Once writes and token rotations. Redirects
    // cannot forward our custom credential header to another host.
    private val http = httpClient.newBuilder().retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false).build()
    private val lock = Any()
    private var generation = 0L
    private var identityRevision = 0L
    private var refreshEpoch = 0L
    private var refreshTask: Deferred<Unit>? = null
    private var recoveryTask: Job? = null
    var refreshRecoveryDelays: List<Long> = listOf(5_000, 30_000, 120_000)

    suspend inline fun <reified Req, reified Res> post(
        path: String, body: Req, requiresAuth: Boolean = true, singleAttempt: Boolean = false,
    ): Res = post(path, body, serializer<Req>(), serializer<Res>(), requiresAuth, singleAttempt)

    suspend fun <Req, Res> post(
        path: String, body: Req, requestSerializer: KSerializer<Req>, responseSerializer: KSerializer<Res>,
        requiresAuth: Boolean = true, singleAttempt: Boolean = false,
    ): Res {
        val started = currentGeneration()
        val raw = withContext(Dispatchers.Default) { NetworkJson.wire.encodeToString(requestSerializer, body).toByteArray() }
        return postEncoded(path, raw, responseSerializer, requiresAuth, singleAttempt, false, started)
    }

    suspend inline fun <reified Res> get(path: String, requiresAuth: Boolean = true): Res =
        get(path, serializer<Res>(), requiresAuth)

    suspend fun <Res> get(path: String, serializer: KSerializer<Res>, requiresAuth: Boolean = true): Res =
        perform(path, null, null, serializer, requiresAuth, 1, null, false, true, currentGeneration())

    suspend inline fun <reified Res> uploadMultipart(path: String, fields: List<Pair<String, String>>, file: MultipartFile): Res =
        uploadMultipart(path, fields, file, serializer<Res>())

    suspend fun <Res> uploadMultipart(
        path: String, fields: List<Pair<String, String>>, file: MultipartFile, serializer: KSerializer<Res>,
    ): Res {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            fields.forEach { (name, value) -> addFormDataPart(name, value) }
            addFormDataPart(file.name, file.filename, file.data.toRequestBody(file.mimeType.toMediaType()))
        }.build()
        return perform(path, body, null, serializer, true, 3, null, false, true, currentGeneration())
    }

    suspend fun loginGoogle(request: GoogleLoginRequest): LoginResponse {
        val started = currentGeneration()
        val response: LoginResponse = post(ApiEndpoint.googleLogin, request, requiresAuth = false)
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                ensureGeneration(started)
                val previous = (tokenStore.accountIdRead as? SecureRead.Value)?.value?.let(UUID::fromString)
                tokenStore.acceptLogin(response)
                crossBoundary()
                if (previous != response.account.id) onIdentityChanged(previous, response.account.id)
            }
        }
        return response
    }

    suspend fun signOut() {
        val identity = synchronized(lock) { identityRevision }
        try {
            if (tokenStore.accessTokenRead is SecureRead.Value) {
                try { post<EmptyRequest, EmptyResponse>(ApiEndpoint.logout, EmptyRequest()) }
                catch (error: CancellationException) { throw error }
                catch (_: ApiError) { /* The explicit local sign-out still proceeds. */ }
            }
        } finally {
            withContext(NonCancellable) { hardSignOut(HardSignOutReason.userSignOut, identity) }
        }
    }

    suspend fun deleteAccount() {
        val identity = synchronized(lock) { identityRevision }
        post<EmptyRequest, EmptyResponse>(ApiEndpoint.deleteAccount, EmptyRequest())
        hardSignOut(HardSignOutReason.accountDeletion, identity)
    }

    private suspend fun hardSignOut(reason: HardSignOutReason, expectedIdentity: Long) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (identityRevision != expectedIdentity) return@synchronized
            crossBoundary()
            tokenStore.clear(reason)
            onHardSignOut(reason)
        }
    }

    // The app calls this on launch, foreground and ACTION_USER_UNLOCKED.
    suspend fun reviveSoftExpiredSession() {
        withContext(Dispatchers.IO) { tokenStore.hydrate() }
        if (tokenStore.refreshTokenRead !is SecureRead.Value) return
        try { refreshIfNeeded() }
        catch (error: CancellationException) { throw error }
        catch (_: ApiError) { /* A transient failure already armed recovery. */ }
    }

    fun sessionBoundaryCrossed() = synchronized(lock) { crossBoundary() }

    private fun crossBoundary(cancelRefresh: Boolean = true, identityChanged: Boolean = true) {
        generation++
        if (identityChanged) identityRevision++
        if (cancelRefresh) refreshTask?.cancel()
        refreshTask = null
        recoveryTask?.cancel()
        recoveryTask = null
    }

    private fun currentGeneration(): Long = synchronized(lock) { generation }
    private fun ensureGeneration(started: Long) {
        if (currentGeneration() != started) throw ApiError.Transport("Stale session response")
    }

    private suspend fun <Res> postEncoded(
        path: String, raw: ByteArray, serializer: KSerializer<Res>, requiresAuth: Boolean,
        singleAttempt: Boolean, isRetry: Boolean, started: Long,
    ): Res {
        val wire = withContext(Dispatchers.Default) {
            if (raw.size >= 4_096) ByteArrayOutputStream().apply { GZIPOutputStream(this).use { it.write(raw) } }.toByteArray() else raw
        }
        return perform(path, wire.toRequestBody("application/json".toMediaType()), raw, serializer, requiresAuth,
            if (singleAttempt) 1 else if (wire.size >= 10_000) 2 else 3,
            if (raw.size >= 4_096) 90 else 30, isRetry, !singleAttempt, started,
            gzipped = raw.size >= 4_096)
    }

    private suspend fun <Res> perform(
        path: String, body: RequestBody?, raw: ByteArray?, serializer: KSerializer<Res>, requiresAuth: Boolean,
        attempts: Int, timeoutSeconds: Long?, isRetry: Boolean, retryServerError: Boolean, started: Long,
        gzipped: Boolean = false,
    ): Res {
        ensureGeneration(started)
        val request = Request.Builder().url(url(path)).apply {
            if (body == null) get() else post(body)
            if (gzipped) header("Content-Encoding", "gzip")
            if (requiresAuth) (tokenStore.accessTokenRead as? SecureRead.Value)?.value?.let { header("x-access-token", it) }
        }.build()
        logger.request(request.method, request.url.toString(), raw)
        val response = send(request, attempts, timeoutSeconds, started)
        logger.response(response.status)
        ensureGeneration(started)
        if (response.status != 200) {
            if (throttled(response)) throw ApiError.TooManyRequests
            throw ApiError.InvalidHttpStatus(response.status)
        }
        val envelope = withContext(Dispatchers.Default) {
            try { NetworkJson.wire.decodeFromString(ApiEnvelope.serializer(serializer), response.bytes.toString(Charsets.UTF_8)) }
            catch (error: IllegalArgumentException) { throw ApiError.Decoding(error) }
        }
        ensureGeneration(started)
        if (envelope.status == ApiStatus.ok) {
            return envelope.payload ?: if (serializer.descriptor.serialName == EmptyResponse.serializer().descriptor.serialName) {
                NetworkJson.wire.decodeFromString(serializer, "{}")
            } else throw ApiError.Decoding()
        }
        val code = envelope.code ?: "UNKNOWN"
        if (code == "USER_NOT_AUTH" && requiresAuth) {
            if (!isRetry) {
                refreshIfNeeded()
                return perform(path, body, raw, serializer, requiresAuth, attempts, timeoutSeconds, true, retryServerError, started, gzipped)
            }
            // A long-running resend can outlive even the rotated access token.
            try { refreshIfNeeded() }
            catch (error: CancellationException) { throw error }
            catch (_: ApiError) { /* The refresh decides session health. */ }
        }
        if (code == "UNKNOWN_SERVER_ERROR" && !isRetry && retryServerError) {
            pause(1_000)
            return perform(path, body, raw, serializer, requiresAuth, attempts, timeoutSeconds, true, retryServerError, started, gzipped)
        }
        if (code == "USER_BANNED") {
            synchronized(lock) {
                ensureGeneration(started)
                crossBoundary(cancelRefresh = false)
                try { tokenStore.clear(HardSignOutReason.banned) }
                catch (_: SecureStorageException) { throw ApiError.Transport("Secure storage unavailable") }
                onHardSignOut(HardSignOutReason.banned)
            }
        }
        val modified = envelope.serverLastModifiedAt?.takeIf { raw != null }?.let { rawDate ->
            runCatching { NetworkJson.wire.decodeFromJsonElement(IsoInstantSerializer, JsonPrimitive(rawDate)) }.getOrNull()
        }
        throw ApiError.from(code, envelope.message ?: "", envelope.serverVersion, modified)
    }

    private fun url(path: String): HttpUrl {
        require(path.startsWith('/') && !path.startsWith("//"))
        return base.newBuilder().encodedPath(base.encodedPath.trimEnd('/') + path.substringBefore('?'))
            .encodedQuery(path.substringAfter('?', "").takeIf(String::isNotEmpty)).build()
    }

    private fun throttled(response: WireResponse): Boolean {
        if (response.status == 429) return true
        val body = runCatching { NetworkJson.wire.parseToJsonElement(response.bytes.toString(Charsets.UTF_8)).jsonObject }.getOrNull()
        return (body?.get("statusCode") as? JsonPrimitive)?.intOrNull == 429 ||
            (body?.get("message") as? JsonPrimitive)?.contentOrNull?.contains("ThrottlerException") == true
    }

    private suspend fun send(request: Request, attempts: Int, timeoutSeconds: Long?, started: Long): WireResponse {
        var last: ApiError.Network? = null
        repeat(attempts) { attempt ->
            if (attempt > 0) pause(attempt * 400L + 400)
            ensureGeneration(started)
            val builder = http.newBuilder()
            if (timeoutSeconds != null) builder.callTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS).writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            else if (attempt > 0) builder.readTimeout(90, TimeUnit.SECONDS).writeTimeout(90, TimeUnit.SECONDS)
            val freshPool = if (attempt > 0) ConnectionPool() else null
            if (freshPool != null) builder.connectionPool(freshPool)
            try { return execute(builder.build(), request) }
            catch (error: IOException) {
                val network = ApiError.network(error)
                if (network.failure == NetworkFailure.other) throw network
                last = network
            } finally { freshPool?.evictAll() }
        }
        throw checkNotNull(last)
    }

    // iOS: URLSession.data(for:) in APIClient.swift
    private data class WireResponse(val status: Int, val bytes: ByteArray)

    private suspend fun execute(client: OkHttpClient, request: Request): WireResponse = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val value = response.use { WireResponse(it.code, it.body.bytes()) }
                    if (!continuation.isCancelled) continuation.resume(value)
                } catch (error: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(error)
                }
            }
        })
    }

    suspend fun refreshIfNeeded() {
        val task = synchronized(lock) {
            val started = generation
            refreshTask ?: refreshScope.async(start = CoroutineStart.LAZY) { refresh(started) }.also { task ->
                refreshTask = task
                task.invokeOnCompletion { synchronized(lock) { if (refreshTask === task) refreshTask = null } }
                task.start()
            }
        }
        task.await()
    }

    private suspend fun refresh(started: Long) {
        try {
            val refresh = when (val read = tokenStore.refreshTokenRead) {
                is SecureRead.Value -> read.value
                SecureRead.Missing -> throw ApiError.InvalidRefreshToken
                is SecureRead.Unavailable -> throw ApiError.Transport("Secure storage unavailable")
            }
            suspend fun postRefresh(): RefreshResponse = postEncoded(ApiEndpoint.refresh,
                NetworkJson.wire.encodeToString(RefreshRequest(refresh)).toByteArray(), RefreshResponse.serializer(),
                requiresAuth = false, singleAttempt = true, isRetry = true, started = started)
            val response = try { postRefresh() }
            catch (error: ApiError.Network) {
                if (error.failure != NetworkFailure.connectionLost) throw error
                pause(1_000)
                postRefresh()
            }
            synchronized(lock) {
                ensureGeneration(started)
                tokenStore.set(response.accessToken, response.refreshToken)
                tokenStore.sessionRecovered()
                refreshEpoch++
                onSessionRecovered()
            }
        } catch (error: SecureStorageException) {
            synchronized(lock) { if (generation == started) scheduleRecovery() }
            throw ApiError.Transport("Secure storage unavailable")
        } catch (error: ApiError) {
            synchronized(lock) {
                if (generation == started) {
                    val transient = when (error) {
                        is ApiError.Network, is ApiError.Decoding, is ApiError.Transport, is ApiError.UnknownServer,
                        ApiError.TooManyRequests -> true
                        is ApiError.InvalidHttpStatus -> error.status >= 500
                        else -> false
                    }
                    if (transient) scheduleRecovery() else {
                        try { tokenStore.sessionExpired() }
                        catch (_: SecureStorageException) {
                            scheduleRecovery()
                            throw ApiError.Transport("Secure storage unavailable")
                        }
                        crossBoundary(cancelRefresh = false, identityChanged = false)
                        onSessionExpired()
                    }
                }
            }
            throw error
        }
    }

    private fun scheduleRecovery() {
        if (recoveryTask != null || refreshRecoveryDelays.isEmpty()) return
        val armedGeneration = generation
        val armedEpoch = refreshEpoch
        val delays = refreshRecoveryDelays.toList()
        val job = refreshScope.launch(start = CoroutineStart.LAZY) {
            for (wait in delays) {
                pause(wait)
                synchronized(lock) { if (generation != armedGeneration || refreshEpoch != armedEpoch) return@launch }
                when (tokenStore.refreshTokenRead) {
                    SecureRead.Missing -> return@launch
                    is SecureRead.Unavailable -> continue
                    is SecureRead.Value -> Unit
                }
                try { refreshIfNeeded(); return@launch }
                catch (error: CancellationException) { throw error }
                catch (_: ApiError) { /* Sequential delays stop after this bounded ladder. */ }
            }
        }
        recoveryTask = job
        job.invokeOnCompletion { synchronized(lock) { if (recoveryTask === job) recoveryTask = null } }
        job.start()
    }

    suspend fun getBytes(url: String): ByteArray {
        val parsed = url.toHttpUrl()
        if (!isAllowedPhotoUrl(parsed)) throw ApiError.Transport("Disallowed photo host")
        val response = try { execute(http, Request.Builder().url(parsed).get().build()) }
        catch (error: IOException) { throw ApiError.network(error) }
        if (response.status != 200) throw ApiError.InvalidHttpStatus(response.status)
        return response.bytes
    }

    // iOS: ProductAnalytics.swift (HTTP 204, public, no envelope or retries)
    suspend inline fun <reified Req> postUsage(path: String, body: Req) = postUsage(path, body, serializer<Req>())

    suspend fun <Req> postUsage(path: String, body: Req, serializer: KSerializer<Req>) {
        require(path == ApiEndpoint.usageSnapshot || path == ApiEndpoint.usageErase)
        val raw = withContext(Dispatchers.Default) { NetworkJson.wire.encodeToString(serializer, body).toByteArray() }
        val response = try { execute(http, Request.Builder().url(url(path))
            .post(raw.toRequestBody("application/json".toMediaType())).build()) }
        catch (error: IOException) { throw ApiError.network(error) }
        if (response.status != 204) throw ApiError.InvalidHttpStatus(response.status)
    }

    override fun close() { sessionBoundaryCrossed(); refreshScope.cancel() }

    companion object {
        fun isAllowedPhotoUrl(url: HttpUrl): Boolean = url.isHttps && (url.host.endsWith(".r2.cloudflarestorage.com") ||
            url.host.endsWith(".r2.dev") || url.host == "trip-track.app" || url.host.endsWith(".trip-track.app"))
    }
}

// iOS: MultipartFormDataBuilder.swift
data class MultipartFile(val name: String, val filename: String, val mimeType: String, val data: ByteArray)
