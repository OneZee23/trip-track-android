// iOS: APIClientEnvelopeTests.swift, VehiclePhotoUploadWireTests.swift,
// MultipartFormDataBuilderTests.swift, APIEndpoint.swift, AuthService.swift
package com.onezee.triptrack.network

import com.onezee.triptrack.model.*
import java.io.ByteArrayInputStream
import java.net.SocketTimeoutException
import java.time.Instant
import java.util.Base64
import java.util.Random
import java.util.UUID
import java.util.zip.GZIPInputStream
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test

class ApiClientWireTests {
    private lateinit var server: MockWebServer
    private lateinit var client: ApiClient
    private lateinit var store: TokenStore
    @Before fun setUp() {
        server = MockWebServer().apply { start() }; store = signedInStore()
        client = ApiClient(store, server.url("/").toString(), pause = {}).apply { refreshRecoveryDelays = emptyList() }
    }
    @After fun tearDown() { client.close(); server.close() }

    // iOS: APIClient.swift (serializeBody)
    @Serializable data class TextRequest(val text: String)

    @Test fun rawJsonThresholdSelectsGzipAndTimeout() = runBlocking {
        val ceilings = mutableListOf<Int>()
        val http = OkHttpClient.Builder().addInterceptor { ceilings.add(it.readTimeoutMillis()); it.proceed(it.request()) }.build()
        client.close(); client = ApiClient(store, server.url("/").toString(), http, pause = {})
        for (size in listOf(4095, 4096)) {
            val body = TextRequest("a".repeat(size - 11))
            server.enqueue(ok())
            client.post<TextRequest, Payload>("/test", body)
            val request = server.takeRequest()
            val bytes = request.body!!.toByteArray()
            val decoded = if (size >= 4096) {
                assertEquals("gzip", request.headers["Content-Encoding"])
                GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            } else { assertNull(request.headers["Content-Encoding"]); bytes }
            assertEquals(size, decoded.size)
            assertEquals(body, NetworkJson.wire.decodeFromString<TextRequest>(decoded.toString(Charsets.UTF_8)))
            assertEquals("expired", request.headers["x-access-token"])
        }
        assertEquals(listOf(30_000, 90_000), ceilings)
    }

    @Test fun retryBudgetUsesCompressedWireSize() = runBlocking {
        var attempts = 0
        val waits = mutableListOf<Long>()
        val http = OkHttpClient.Builder().addInterceptor { attempts++; throw SocketTimeoutException() }.build()
        client.close(); client = ApiClient(store, server.url("/").toString(), http, pause = { waits.add(it) })
        val noise = ByteArray(20_000).also { Random(42).nextBytes(it) }
        for ((text, expected) in listOf("a".repeat(20_000) to 3, Base64.getEncoder().encodeToString(noise) to 2)) {
            attempts = 0; waits.clear()
            assertFailsWith<ApiError.Network> { client.post<TextRequest, Payload>("/test", TextRequest(text), false) }
            assertEquals(expected, attempts)
            assertEquals(if (expected == 3) listOf(800L, 1200L) else listOf(800L), waits)
        }
    }

    @Test fun googleLoginSendsRawNonceAndSameLoginResponse() = runBlocking {
        val nonce = GoogleNonce.fromRaw("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", nonce.sha256)
        val random = GoogleNonce.create()
        assertEquals(32, random.raw.length); assertEquals(64, random.sha256.length)
        assertNotEquals(random.raw, GoogleNonce.create().raw)
        server.enqueue(ok("{\"accessToken\":\"google\",\"refreshToken\":\"googleRefresh\",\"account\":{\"id\":\"$accountId\",\"avatarEmoji\":\"🚗\"},\"isNewAccount\":false}"))
        val response = client.loginGoogle(GoogleLoginRequest("test-google-proof", "local-install", nonce.raw, "Test device", "1.0"))
        assertEquals(accountId, response.account.id)
        assertEquals(SecureRead.Value("googleRefresh"), store.refreshTokenRead)
        val request = server.takeRequest()
        assertEquals(ApiEndpoint.googleLogin, request.url.encodedPath); assertEquals("POST", request.method)
        assertNull(request.headers["x-access-token"])
        assertEquals(NetworkJson.wire.parseToJsonElement("""{"identityToken":"test-google-proof","localUserId":"local-install","nonce":"abc","deviceName":"Test device","platform":"android","appVersion":"1.0"}"""),
            NetworkJson.wire.parseToJsonElement(request.body!!.utf8()))
    }

    @Test fun plusStatusAndPromoRedemptionUseTheirFixturesAndExactWireContract() = runBlocking {
        server.enqueue(ok(fixture("PromoCodeTests-statusIncludesPromoUntil")))
        val status = PlusApi(client).status()
        assertEquals(Instant.parse("2026-12-06T10:00:00Z"), status.promoUntil)
        val statusRequest = server.takeRequest()
        assertEquals(ApiEndpoint.plusStatus, statusRequest.url.encodedPath)
        assertEquals("expired", statusRequest.headers["x-access-token"])
        assertEquals("{}", statusRequest.body!!.utf8())

        server.enqueue(ok(fixture("PromoCodeTests-redeemSuccess")))
        val response = PlusApi(client).redeem("  AbC-1234  ")
        assertEquals(30, response.redemption.durationDays)
        val redeemRequest = server.takeRequest()
        assertEquals(ApiEndpoint.promoRedeem, redeemRequest.url.encodedPath)
        assertEquals("expired", redeemRequest.headers["x-access-token"])
        assertEquals("{\"code\":\"AbC-1234\"}", redeemRequest.body!!.utf8())
    }

    @Test fun failedLoginKeepsPreviousIdentityAndTokens() = runBlocking {
        server.enqueue(rejected("INVALID_GOOGLE_TOKEN"))
        assertFailsWith<ApiError.UnknownServer> { client.loginGoogle(GoogleLoginRequest("test", "local", "raw", appVersion = "1")) }
        assertEquals(SecureRead.Value("valid"), store.refreshTokenRead)
        assertEquals(accountId, store.state.value.accountId); assertEquals("Test driver", store.state.value.displayName)
    }

    @Test fun cursorsAreEncodedExactlyOnceWithPathSeparateFromQuery() = runBlocking {
        val cursor = "2026-09-13T12:00:00.000+03:00|11111111-1111-4111-8111-111111111111"
        server.enqueue(ok())
        client.get<Payload>(ApiEndpoint.userTrips(accountId, cursor, 20))
        val request = server.takeRequest()
        assertEquals("/users/$accountId/trips", request.url.encodedPath)
        assertEquals(cursor, request.url.queryParameter("cursor"))
        assertFalse(request.url.toString().contains("%25"))
        assertEquals("2026-09-13T12:00:00.000Z|$accountId", ApiEndpoint.feedCursor(Instant.parse("2026-09-13T12:00:00Z"), accountId))
        assertEquals("/app/config", ApiEndpoint.appConfig(""))
    }

    @Test fun multipartUsesLowercaseUuidsBooleanAndIsoDateText() = runBlocking {
        val id = UUID.fromString("ABCDEFAB-CDEF-4ABC-8DEF-ABCDEFABCDEF")
        val body = VehiclePhotoUploadRequest(id, id, "thumbnail", false, Instant.parse("2026-10-20T09:00:00Z"))
        server.enqueue(jsonResponse("{\"status\":\"ok\"}"))
        client.uploadMultipart<EmptyResponse>(ApiEndpoint.vehiclePhotoUpload, body.fields(), MultipartFile("file", "photo.jpg", "image/jpeg", byteArrayOf(1, 2)))
        val request = server.takeRequest()
        val text = request.body!!.utf8()
        assertEquals("expired", request.headers["x-access-token"])
        assertTrue(request.headers["Content-Type"]!!.startsWith("multipart/form-data; boundary="))
        assertTrue(text.contains("name=\"vehicleId\"")); assertTrue(text.contains(id.toString())); assertFalse(text.contains(id.toString().uppercase()))
        assertTrue(text.contains("false")); assertTrue(text.contains("2026-10-20T09:00:00.000Z"))
        assertTrue(text.contains("filename=\"photo.jpg\"")); assertTrue(text.contains("Content-Type: image/jpeg"))
    }

    @Test fun onceCommentKeepsOneAttemptAfterAuthRefresh() = runBlocking {
        var comments = 0
        val http = OkHttpClient.Builder().addInterceptor {
            if (it.request().url.encodedPath == ApiEndpoint.socialComment && ++comments == 2) throw SocketTimeoutException()
            it.proceed(it.request())
        }.build()
        client.close(); client = ApiClient(store, server.url("/").toString(), http, pause = {})
        server.enqueue(rejected()); server.enqueue(refreshed())
        assertFailsWith<ApiError.Network> { SocialCommentsApi(client).create(SocialCommentCreateRequest(accountId, "Test comment")) }
        assertEquals(2, comments)
    }

    @Test fun usageIsPublicUncompressedAndAcceptsOnly204() = runBlocking {
        server.enqueue(jsonResponse("", 204))
        client.postUsage(ApiEndpoint.usageErase, UsageEraseRequest("test-consent"))
        val request = server.takeRequest()
        assertNull(request.headers["x-access-token"]); assertNull(request.headers["Content-Encoding"])
        server.enqueue(jsonResponse("{\"status\":\"ok\"}"))
        assertEquals(200, assertFailsWith<ApiError.InvalidHttpStatus> { client.postUsage(ApiEndpoint.usageErase, UsageEraseRequest("test-consent")) }.status)
        assertEquals(2, server.requestCount)
    }
}
