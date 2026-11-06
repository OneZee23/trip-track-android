// iOS: ManualTripRouter.swift, ManualTripSuggestions.swift
package com.onezee.triptrack.network

import com.onezee.triptrack.model.GeoPoint
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class ManualTripsApiTests {
    @Test fun capabilitiesSearchAndOneRequestRouteMatchServerContract() = runBlocking {
        val server = MockWebServer().apply { start() }
        val token = signedInStore()
        val client = ApiClient(token, server.url("/").toString(), pause = {}).apply { refreshRecoveryDelays = emptyList() }
        try {
            server.enqueue(MockResponse.Builder().body("""{"status":"ok","payload":{"manualTrips":false,"search":true,"routing":true}}""").build())
            val api = ManualTripsApi(client)
            val capabilities = api.capabilities()
            assertFalse(capabilities.manualTrips)
            assertTrue(capabilities.search && capabilities.routing)
            assertEquals(ApiEndpoint.webRoutingCapabilities, server.takeRequest().url.encodedPath)

            server.enqueue(MockResponse.Builder().body("""{"status":"ok","payload":{"places":[{"name":"Rostov","latitude":47.2,"longitude":39.7}]}}""").build())
            assertEquals("Rostov", api.search("Rostov", "ru").places.single().name)
            val search = server.takeRequest()
            assertEquals(ApiEndpoint.webRoutingSearch, search.url.encodedPath)
            assertEquals("{\"query\":\"Rostov\",\"language\":\"ru\"}", search.body!!.utf8())

            server.enqueue(MockResponse.Builder().body("""{"status":"ok","payload":{"coordinates":[[47.2,39.7],[43.6,39.7]],"distance":320000,"durationSeconds":12000}}""").build())
            val route = api.route(listOf(GeoPoint(47.2, 39.7), GeoPoint(46.0, 39.0), GeoPoint(45.0, 38.0), GeoPoint(44.0, 39.0), GeoPoint(43.6, 39.7)))
            assertEquals(2, route.coordinates.size)
            assertEquals(320000.0, route.distance)
            val request = server.takeRequest()
            assertEquals(ApiEndpoint.webRoutingRoute, request.url.encodedPath)
            assertEquals("{\"waypoints\":[[47.2,39.7],[46.0,39.0],[45.0,38.0],[44.0,39.0],[43.6,39.7]]}", request.body!!.utf8())
            Unit
        } finally { client.close(); server.close() }
    }

    @Test fun everyRoutingBusinessErrorRemainsAStableCode() = runBlocking {
        val server = MockWebServer().apply { start() }
        val client = ApiClient(signedInStore(), server.url("/").toString(), pause = {}).apply { refreshRecoveryDelays = emptyList() }
        try {
            val codes = listOf("ROUTE_NOT_FOUND", "TOO_MANY_STOPS", "PRO_REQUIRED", "ROUTING_NOT_CONFIGURED", "ROUTING_UNAVAILABLE", "MANUAL_TRIPS_DISABLED")
            for (code in codes) {
                server.enqueue(MockResponse.Builder().body("""{"status":"error","code":"$code","message":"provider secret"}""").build())
                val error = assertFailsWith<ApiError.UnknownServer> {
                    ManualTripsApi(client).route(listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 1.0)))
                }
                assertEquals(code, error.code)
                assertFalse(error.message.orEmpty().contains("provider secret"))
                server.takeRequest(1, TimeUnit.SECONDS)
            }
            server.enqueue(MockResponse.Builder().body("""{"status":"error","code":"TOO_MANY_REQUESTS"}""").build())
            assertFailsWith<ApiError.TooManyRequests> {
                ManualTripsApi(client).route(listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 1.0)))
            }
            server.takeRequest(1, TimeUnit.SECONDS)
            Unit
        } finally { client.close(); server.close() }
    }

    @Test fun searchValidatesLengthAndSupportedLanguageBeforeRequest() = runBlocking {
        val server = MockWebServer().apply { start() }
        val client = ApiClient(signedInStore(), server.url("/").toString(), pause = {}).apply { refreshRecoveryDelays = emptyList() }
        try {
            val api = ManualTripsApi(client)
            assertFailsWith<IllegalArgumentException> { api.search("ab", "ru") }
            assertFailsWith<IllegalArgumentException> { api.search("Rostov", "fil") }
            assertEquals(0, server.requestCount)
        } finally { client.close(); server.close() }
    }
}
