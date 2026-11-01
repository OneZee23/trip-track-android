// iOS: PIIScrubberTests.swift, APILoggerRedactionTests.swift, APILoggerCostTests.swift
package com.onezee.triptrack.network

import kotlin.test.*
import kotlinx.serialization.json.*
import org.junit.Test

class PIIScrubberTests {
    @Test fun testTokensAndIdentityAreRedacted() {
        val out = PIIScrubber.redact(mapOf(
            "accessToken" to "eyJhbGciOi", "refreshToken" to "r-123",
            "email" to "driver@example.com", "displayName" to "Иван", "statusCode" to 500,
        ))
        for (key in listOf("accessToken", "refreshToken", "email", "displayName")) {
            assertEquals(PIIScrubber.redactedMarker, out[key])
        }
        assertEquals(500, out["statusCode"])
    }

    @Test fun testNestedDictionaryFixturesFromIOS() {
        val out = PIIScrubber.redact(mapOf(
            "response" to mapOf("headers" to mapOf("authorization" to "Bearer x", "accessToken" to "t")),
            "queue" to listOf(mapOf("identityToken" to "id-token"), mapOf("op" to "upload")),
        ))
        val headers = (out["response"] as Map<*, *>)["headers"] as Map<*, *>
        assertEquals(PIIScrubber.redactedMarker, headers["accessToken"])
        val queue = out["queue"] as List<*>
        assertEquals(PIIScrubber.redactedMarker, (queue.first() as Map<*, *>)["identityToken"])
        assertEquals("upload", (queue.last() as Map<*, *>)["op"])
    }

    @Test fun testScrubberListMatchesAPILoggerList() {
        assertTrue(PIISensitiveKeys.all.contains("accessToken"))
        assertTrue(PIISensitiveKeys.all.contains("url"))
        assertTrue(PIISensitiveKeys.all.contains("homeLatitude"))
    }

    @Test fun testPrefixedUserDefaultsKeyIsRedacted() {
        val out = PIIScrubber.redact(mapOf(
            "com.triptrack.settings.homeLatitude" to 45.03,
            "com.triptrack.settings.homeLongitude" to 38.98,
            "com.triptrack.settings.appLanguage" to "ru",
        ))
        assertEquals(PIIScrubber.redactedMarker, out["com.triptrack.settings.homeLatitude"])
        assertEquals(PIIScrubber.redactedMarker, out["com.triptrack.settings.homeLongitude"])
        assertEquals("ru", out["com.triptrack.settings.appLanguage"])
    }

    @Test fun testSDKDeviceAppHashIsRedactedFromAppContext() {
        val sdkContext = mapOf(
            "app" to mapOf("app_identifier" to "app.trip-track.ios", "app_version" to "0.6.6", "app_build" to "58",
                "device_app_hash" to "7b1c9a0f4e2d6853a1b0c9d8e7f60514"),
            "device" to mapOf("model" to "iPhone15,2", "free_memory" to 812_345_678),
        )
        val out = sdkContext.mapValues { PIIScrubber.redact(it.value) }
        assertEquals(PIIScrubber.redactedMarker, out["app"]?.get("device_app_hash"))
        assertEquals("0.6.6", out["app"]?.get("app_version"))
        assertEquals("iPhone15,2", out["device"]?.get("model"))
        assertFalse(out.toString().contains("7b1c9a0f4e2d6853a1b0c9d8e7f60514"))
    }

    @Test fun testDeviceAppHashIsInTheSharedList() {
        assertTrue(PIISensitiveKeys.all.contains("device_app_hash"))
        assertTrue(PIISensitiveKeys.matches("device_app_hash"))
        assertTrue(PIISensitiveKeys.matches("contexts.device_app_hash"))
    }

    @Test fun testURLScrubbingIsIdempotentForCachedBreadcrumbs() {
        val url = "https://api.trip-track.app/users/<id>/trips"
        assertEquals(url, PIIScrubber.redactUrl(url))
    }

    @Test fun testObjectArraysAreWalkedLikeSwiftArrays() {
        val out = PIIScrubber.redact(mapOf("queue" to arrayOf(mapOf("identityToken" to "private"))))
        assertFalse(out.toString().contains("private"))
    }

    @Test fun testUrlKeepsShapeAndDropsAccountId() {
        assertEquals("https://api.trip-track.app/users/<id>/trips", PIIScrubber.redactUrl(
            "https://api.trip-track.app/users/8B0F1C2E-3D4A-5B6C-7D8E-9F0A1B2C3D4E/trips"))
    }
    @Test fun testUrlDropsQueryEntirely() {
        assertEquals("https://api.trip-track.app/users/<id>/trips", PIIScrubber.redactUrl(
            "https://api.trip-track.app/users/abc123/trips?limit=20&cursor=2026-09-01T10%3A00%3A00Z%7C4F3E"))
    }
    @Test fun testUrlDropsFragmentAndCredentials() {
        assertEquals("https://api.trip-track.app/auth/me", PIIScrubber.redactUrl("https://user:secret@api.trip-track.app/auth/me#token"))
    }
    @Test fun testUrlKeepsPlainRouteWords() {
        for (path in listOf("auth/notification-prefs/update", "sync/pull")) {
            assertEquals("https://api.trip-track.app/$path", PIIScrubber.redactUrl("https://api.trip-track.app/$path"))
        }
    }
    @Test fun testUrlMasksUnknownSegmentsByDefault() {
        assertEquals("https://cdn.example.com/photos/<id>", PIIScrubber.redactUrl("https://cdn.example.com/photos/a1b2c3d4e5f6"))
        assertEquals("https://cdn.example.com/x/<id>", PIIScrubber.redactUrl("https://cdn.example.com/x/averylongsegmentwithoutdigitsatall"))
    }
    @Test fun testUrlPresignedPhotoLinkLosesSignature() {
        val out = PIIScrubber.redactUrl("https://r2.example.com/trip-photos/9F0A1B2C.jpg?X-Amz-Signature=deadbeef&X-Amz-Expires=3600")
        assertFalse(out.contains("deadbeef")); assertFalse(out.contains("X-Amz"))
    }
    @Test fun testUnparsableUrlBecomesMarkerNotPassthrough() {
        for (url in listOf("не url вовсе", "/users/8B0F/trips")) assertEquals(PIIScrubber.redactedMarker, PIIScrubber.redactUrl(url))
    }
    @Test fun testTokensIdentityHomeCoordinatesAndSdkHashAreRedacted() {
        for (key in PIISensitiveKeys.all) {
            val out = PIIScrubber.redact(mapOf(key to "private", "statusCode" to 500))
            assertEquals(PIIScrubber.redactedMarker, out[key]); assertEquals(500, out["statusCode"])
        }
        val out = PIIScrubber.redact(mapOf("homeLatitude" to 45.03, "homeLongitude" to 38.97))
        assertEquals(PIIScrubber.redactedMarker, out["homeLatitude"])
        assertEquals(PIIScrubber.redactedMarker, out["homeLongitude"])
    }
    @Test fun testNestedDictionariesAndArraysAreWalked() {
        val input = NetworkJson.wire.parseToJsonElement("""{"response":{"headers":{"accessToken":"t"}},"queue":[{"identityToken":"proof"},{"op":"upload"}]}""")
        val out = PIIScrubber.redact(input).jsonObject
        assertEquals(PIIScrubber.redactedMarker, out.getValue("response").jsonObject.getValue("headers").jsonObject.getValue("accessToken").jsonPrimitive.content)
        assertEquals("upload", out.getValue("queue").jsonArray[1].jsonObject.getValue("op").jsonPrimitive.content)
        assertFalse(out.toString().contains("proof"))
        val nested = PIIScrubber.redact(mapOf("queue" to listOf(mapOf("identityToken" to "private"))))
        assertFalse(nested.toString().contains("private"))
    }
    @Test fun testPrefixedUserDefaultsKeyAndCaseInsensitiveMatching() {
        for (key in listOf("com.triptrack.settings.homeLatitude", "AccessToken", "user_email", "contexts.device_app_hash")) {
            assertTrue(PIISensitiveKeys.matches(key))
            assertEquals(PIIScrubber.redactedMarker, PIIScrubber.redact(mapOf(key to "private"))[key])
        }
        assertFalse(PIISensitiveKeys.matches("tokenizer"))
        assertFalse(PIISensitiveKeys.matches("com.triptrack.settings.appLanguage"))
    }
    @Test fun requestPreviewMasksEveryCredentialIncludingEscapesAndCutTails() {
        for (key in PIISensitiveKeys.all) {
            for (body in listOf("{\"$key\":\"SECRET-VALUE\"}", "{\"payload\":[{\"$key\":\"SECRET-VALUE\"}]}",
                "{\"$key\":\"SECRET-VALUE\\\"still-secret\"}", "{\"$key\":\"SECRET-VALUE")) {
                assertFalse(ApiLogger.preview(body.toByteArray()).contains("SECRET-VALUE"), key)
                assertFalse(ApiLogger.preview(body.toByteArray()).contains("still-secret"), key)
            }
        }
        val cut = " ".repeat(2000) + "{\"refreshToken\":\"" + "PRIVATE".repeat(1000)
        assertFalse(ApiLogger.preview(cut.toByteArray()).contains("PRIVATE"))
        assertEquals("{\"limit\":20,\"tripId\":\"E52CD860\"}", ApiLogger.preview("{\"limit\":20,\"tripId\":\"E52CD860\"}".toByteArray()))
    }
}
