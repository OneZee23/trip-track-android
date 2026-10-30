// iOS: Sync/APIClientEnvelopeTests.swift, ISODateTests.swift
package com.onezee.triptrack.network

import com.onezee.triptrack.model.EmptyRequest
import com.onezee.triptrack.model.EmptyResponse
import java.net.SocketTimeoutException
import java.time.Instant
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test

class ApiClientEnvelopeTests {
    private lateinit var server: MockWebServer
    private lateinit var client: ApiClient
    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        client = ApiClient(signedInStore(), server.url("/").toString(), pause = {}).apply { refreshRecoveryDelays = emptyList() }
    }
    @After fun tearDown() { client.close(); server.close() }

    // iOS: APIClientEnvelopeTests.swift
    @Serializable data class TestPayload(val value: Int)

    @Test fun testOkEnvelopeReturnsPayload() = runBlocking {
        server.enqueue(jsonResponse(fixture("APIClientEnvelopeTests-testOkEnvelopeReturnsPayload-0")))
        assertEquals(TestPayload(42), client.post<Req, TestPayload>("/test", Req(1), requiresAuth = false))
        assertNull(server.takeRequest().headers["x-access-token"])
    }

    @Test fun testErrorEnvelopeMapsToTypedError() = runBlocking {
        server.enqueue(jsonResponse(fixture("APIClientEnvelopeTests-testErrorEnvelopeMapsToTypedError-0")))
        assertEquals(ApiError.TripNotFound, assertFailsWith<ApiError> { client.post<Req, Payload>("/test", Req(1), false) })
    }

    @Test fun testConflictDetectedError() = runBlocking {
        server.enqueue(jsonResponse(fixture("APIClientEnvelopeTests-testConflictDetectedError-0")))
        val error = assertFailsWith<ApiError.ConflictDetected> { client.post<Req, Payload>("/test", Req(1), false) }
        assertEquals(5, error.serverVersion)
        assertEquals(Instant.parse("2026-04-19T10:30:45Z"), error.serverLastModifiedAt)
        server.enqueue(rejected("CONFLICT_DETECTED"))
        assertNull(assertFailsWith<ApiError.ConflictDetected> { client.get<Payload>("/test", false) }.serverVersion)
    }

    @Test fun missingOrNullPayloadOnlySucceedsForEmptyResponse() = runBlocking {
        for (body in listOf("{\"status\":\"ok\"}", "{\"status\":\"ok\",\"payload\":null}")) {
            server.enqueue(jsonResponse(body))
            client.post<EmptyRequest, EmptyResponse>("/test", EmptyRequest(), false)
            server.enqueue(jsonResponse(body))
            assertFailsWith<ApiError.Decoding> { client.get<Payload>("/test", false) }
        }
    }

    @Test fun invalidJsonStatusAndPayloadFailDecoding() = runBlocking {
        for (body in listOf("bad json", "{\"status\":\"future\"}", "{\"status\":\"ok\",\"payload\":{}}")) {
            server.enqueue(jsonResponse(body))
            assertFailsWith<ApiError.Decoding> { client.get<Payload>("/test", false) }
        }
    }

    @Test fun throttleAndRawHttpFailuresAreNotEnvelopeRetries() = runBlocking {
        for ((status, body) in listOf(429 to "{}", 503 to "{\"statusCode\":429}",
            400 to "{\"message\":\"ThrottlerException: Too Many Requests\"}")) {
            server.enqueue(jsonResponse(body, status))
            assertEquals(ApiError.TooManyRequests, assertFailsWith<ApiError> { client.get<Payload>("/test", false) })
        }
        server.enqueue(jsonResponse("<html>down</html>", 502))
        assertEquals(502, assertFailsWith<ApiError.InvalidHttpStatus> { client.get<Payload>("/test", false) }.status)
        assertEquals(4, server.requestCount)
    }

    @Test fun unknownCodesKeepCodeAndMessage() = runBlocking {
        server.enqueue(rejected("USER_NOT_FOUND"))
        val error = assertFailsWith<ApiError.UnknownServer> { client.get<Payload>("/test", false) }
        assertEquals("USER_NOT_FOUND", error.code)
        assertEquals("test", error.serverMessage)
    }

    @Test fun unknownServerRetriesOnceButOncePostDoesNot() = runBlocking {
        server.enqueue(rejected("UNKNOWN_SERVER_ERROR")); server.enqueue(ok())
        assertEquals(Payload(1), client.post<Req, Payload>("/test", Req(1), false))
        server.enqueue(rejected("UNKNOWN_SERVER_ERROR"))
        assertFailsWith<ApiError.UnknownServer> { client.post<Req, Payload>("/test", Req(1), false, singleAttempt = true) }
        assertEquals(3, server.requestCount)
    }

    @Test fun transportRetriesKeepInjectedClientAndOnceHasOneAttempt() = runBlocking {
        var attempts = 0
        val http = OkHttpClient.Builder().addInterceptor {
            if (++attempts == 1) throw SocketTimeoutException()
            it.proceed(it.request())
        }.build()
        client.close()
        client = ApiClient(signedInStore(), server.url("/").toString(), http, pause = {})
        server.enqueue(ok())
        assertEquals(Payload(1), client.post<Req, Payload>("/test", Req(1), false))
        assertEquals(2, attempts)
        attempts = 0
        assertFailsWith<ApiError.Network> { client.post<Req, Payload>("/test", Req(1), false, singleAttempt = true) }
        assertEquals(1, attempts)
        attempts = 0
        assertFailsWith<ApiError.Network> { client.get<Payload>("/test", false) }
        assertEquals(1, attempts)
    }
}
