// iOS: PIIScrubberTests.swift, APILoggerRedactionTests.swift, APILoggerCostTests.swift
package com.onezee.triptrack.network

import kotlin.test.*
import kotlinx.serialization.json.*
import org.junit.Test

class PIIScrubberTests {
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
