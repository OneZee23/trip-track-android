package com.onezee.triptrack.diagnostics

import com.onezee.triptrack.network.PIIScrubber
import com.onezee.triptrack.network.PIISensitiveKeys
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.protocol.App
import io.sentry.protocol.Device
import io.sentry.protocol.Request
import io.sentry.protocol.SentryTransaction
import io.sentry.protocol.User
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.*
import org.junit.Test

// iOS: PIIScrubberTests.swift; Services/SentryService.swift (SDK payload surfaces).
class SentryPrivacyTests {
    private fun options() = SentryAndroidOptions().also { SentryService.configure(it, "https://public@example.invalid/1") }

    @Test fun networkAndReplayIntegrationsAreNotOnTheRuntimeClasspath() {
        for (name in listOf("io.sentry.okhttp.SentryOkHttpInterceptor", "io.sentry.android.replay.ReplayIntegration")) {
            assertFailsWith<ClassNotFoundException> { Class.forName(name) }
        }
    }

    @Test fun scrubFailureDropsEventsAndBreadcrumbs() {
        val broken = object : Map<String, Any> by emptyMap() {
            override val entries: Set<Map.Entry<String, Any>> get() = error("unreadable payload")
        }
        val options = options()
        val event = SentryEvent().apply { extras = mapOf("nested" to broken) }
        val breadcrumb = Breadcrumb().apply { setData("nested", broken) }
        assertNull(options.beforeSend!!.execute(event, Hint()))
        assertNull(options.beforeBreadcrumb!!.execute(breadcrumb, Hint()))
    }

    @Test fun privacyOptionsDoNotDependOnSdkDefaults() {
        val options = options()
        @Suppress("DEPRECATION")
        assertFalse(options.isSendDefaultPii)
        assertEquals(false, options.dataCollection.userInfo)
        assertEquals(emptySet(), options.dataCollection.httpBodies)
        assertEquals(false, options.dataCollection.filePaths)
        assertEquals(false, options.dataCollection.databaseQueryData)
        assertEquals("OFF", options.dataCollection.cookies?.mode?.name)
        assertEquals("OFF", options.dataCollection.urlQueryParams?.mode?.name)
        assertEquals("OFF", options.dataCollection.httpHeaders.request?.mode?.name)
        assertEquals("OFF", options.dataCollection.httpHeaders.response?.mode?.name)
        assertFalse(options.isEnableNetworkEventBreadcrumbs)
        assertFalse(options.isEnableSystemEventBreadcrumbs)
        assertFalse(options.isEnableUserInteractionBreadcrumbs)
        assertFalse(options.isAttachScreenshot)
        assertFalse(options.isAttachViewHierarchy)
        assertFalse(options.isAttachAnrThreadDump)
        assertFalse(options.isAttachRawTombstone)
        assertFalse(options.logs.isEnabled)
        assertEquals(0.0, options.sessionReplay.sessionSampleRate)
        assertEquals(0.0, options.sessionReplay.onErrorSampleRate)
        assertEquals(0.0, options.profilesSampleRate)
        assertEquals(0.0, options.profileSessionSampleRate)
        assertEquals(0.0, options.anrProfilingSampleRate)
        assertFalse(options.isEnableAppStartProfiling)
        assertEquals(0.1, options.tracesSampleRate)
        assertTrue(options.isEnableNdk)
        assertTrue(options.isEnableScopeSync)
        assertTrue(options.isAnrEnabled)
        assertTrue(options.isEnableUncaughtExceptionHandler)
        assertTrue(options.isEnableAutoSessionTracking)
    }

    @Test fun eventCallbackScrubsEverySharedKeyIncludingTypedSdkContexts() {
        val options = options()
        val event = SentryEvent().apply {
            tags = PIISensitiveKeys.all.associateWith { "private-tag" } + ("statusCode" to "500")
            extras = mapOf("queue" to PIISensitiveKeys.all.map { mapOf(it to "private-extra") })
            contexts.setApp(App().apply {
                appIdentifier = "app.trip-track.ios"
                appVersion = "0.6.6"
                appBuild = "58"
                deviceAppHash = "7b1c9a0f4e2d6853a1b0c9d8e7f60514"
            })
            contexts.setDevice(Device().apply { model = "iPhone15,2"; freeMemory = 812_345_678 })
            contexts["response"] = mapOf("nested" to listOf(mapOf("email" to "driver@example.com")))
            user = User().apply {
                id = "8B0F1C2E-3D4A-5B6C-7D8E-9F0A1B2C3D4E"
                email = "driver@example.com"
                name = "Иван"
            }
        }
        assertSame(event, options.beforeSend!!.execute(event, Hint()))
        assertEquals(mapOf("statusCode" to "500"), event.tags)
        assertEquals(PIIScrubber.redactedMarker, event.contexts.app?.deviceAppHash)
        assertEquals("0.6.6", event.contexts.app?.appVersion)
        assertEquals("iPhone15,2", event.contexts.device?.model)
        assertEquals("8B0F1C2E-3D4A-5B6C-7D8E-9F0A1B2C3D4E", event.user?.id)
        val wire = StringWriter().also { options.serializer.serialize(event, it) }.toString()
        for (secret in listOf("private-tag", "private-extra", "driver@example.com", "Иван", "7b1c9a0f4e2d6853a1b0c9d8e7f60514")) {
            assertFalse(wire.contains(secret))
        }
    }

    @Test fun requestUrlAndAllCredentialHeadersAreScrubbed() {
        val event = SentryEvent().apply {
            request = Request().apply {
                url = "https://user:secret@api.trip-track.app/users/abc123/trips?cursor=2026#token"
                queryString = "cursor=2026"
                fragment = "token"
                cookies = "session=private"
                data = "private-body"
                headers = mapOf("Authorization" to "private", "Cookie" to "private", "x-access-token" to "private",
                    "X-Refresh-Token" to "private", "X-Secret" to "private", "X-API-KEY" to "private", "Accept" to "application/json")
            }
        }
        val options = options()
        options.beforeSend!!.execute(event, Hint())
        assertEquals("https://api.trip-track.app/users/<id>/trips", event.request?.url)
        assertEquals(mapOf("Accept" to "application/json"), event.request?.headers)
        assertNull(event.request?.queryString)
        assertNull(event.request?.fragment)
        assertNull(event.request?.cookies)
        assertNull(event.request?.data)
    }

    @Test fun breadcrumbCallbackAndCachedEventBreadcrumbsUseTheSameScrubber() {
        for (httpField in listOf("category", "type")) {
            val breadcrumb = Breadcrumb().apply {
                if (httpField == "category") category = "http" else type = "http"
                setData("url", "https://api.trip-track.app/users/abc123/trips?cursor=2026#secret")
                setData("http.query", "cursor=2026")
                setData("http.fragment", "secret")
                setData("queue", listOf(mapOf("identityToken" to "private"), mapOf("op" to "upload")))
            }
            val options = options()
            assertSame(breadcrumb, options.beforeBreadcrumb!!.execute(breadcrumb, Hint()))
            assertEquals("https://api.trip-track.app/users/<id>/trips", breadcrumb.getData("url"))
            assertNull(breadcrumb.getData("http.query"))
            assertNull(breadcrumb.getData("http.fragment"))
            assertFalse(breadcrumb.data.toString().contains("private"))
            val event = SentryEvent().apply { breadcrumbs = listOf(breadcrumb) }
            options.beforeSend!!.execute(event, Hint())
            // The second pass must retain the safe endpoint shape from a previously scrubbed crumb.
            assertEquals("https://api.trip-track.app/users/<id>/trips", breadcrumb.getData("url"))
        }
    }

    @Test fun nonHttpBreadcrumbPresignedUrlIsFullyRedacted() {
        val breadcrumb = Breadcrumb().apply {
            category = "photo"
            setData("url", "https://r2.example.com/trip-photos/9F0A1B2C.jpg?X-Amz-Signature=deadbeef")
            setData("homeLatitude", 45.03)
        }
        options().beforeBreadcrumb!!.execute(breadcrumb, Hint())
        assertEquals(PIIScrubber.redactedMarker, breadcrumb.getData("url"))
        assertEquals(PIIScrubber.redactedMarker, breadcrumb.getData("homeLatitude"))
    }

    @Test fun nativeOutboxEventAlsoPassesTheEventScrubber() {
        val options = options()
        val event = assertNotNull(options.serializer.deserialize(StringReader("""{
            "platform":"native","contexts":{"app":{"device_app_hash":"private-native"}},
            "extra":{"queue":[{"accessToken":"private-token"}]},
            "exception":{"values":[{"type":"SIGSEGV","value":"Segfault","mechanism":{"type":"signal","handled":false}}]}
        }"""), SentryEvent::class.java))
        options.beforeSend!!.execute(event, Hint())
        val wire = StringWriter().also { options.serializer.serialize(event, it) }.toString()
        assertFalse(wire.contains("private-native"))
        assertFalse(wire.contains("private-token"))
        assertTrue(wire.contains("SIGSEGV"))
    }

    @Test fun transactionsKeepSafeSpansAndDropNetworkAndFileDescriptions() {
        val options = options()
        val transaction = assertNotNull(options.serializer.deserialize(StringReader("""{
            "type":"transaction","transaction":"MainActivity","start_timestamp":1,"timestamp":2,
            "contexts":{"trace":{"trace_id":"8b0f1c2e3d4a5b6c7d8e9f0a1b2c3d4e","span_id":"8b0f1c2e3d4a5b6c","op":"ui.load"}},
            "spans":[
                {"start_timestamp":1,"timestamp":2,"trace_id":"8b0f1c2e3d4a5b6c7d8e9f0a1b2c3d4e","span_id":"8b0f1c2e3d4a5b6c","op":"http.client","description":"GET https://example.com/users/abc123"},
                {"start_timestamp":1,"timestamp":2,"trace_id":"8b0f1c2e3d4a5b6c7d8e9f0a1b2c3d4e","span_id":"8b0f1c2e3d4a5b6d","op":"file.read","description":"/photos/abc123.jpg"},
                {"start_timestamp":1,"timestamp":2,"trace_id":"8b0f1c2e3d4a5b6c7d8e9f0a1b2c3d4e","span_id":"8b0f1c2e3d4a5b6e","op":"ui.load","data":{"accessToken":"private","http.query":"cursor=2026","http.fragment":"secret"}}
            ]}
        """), SentryTransaction::class.java))
        options.beforeSendTransaction!!.execute(transaction, Hint())
        assertEquals(listOf("ui.load"), transaction.spans.map { it.op })
        assertEquals(PIIScrubber.redactedMarker, transaction.spans.single().data?.get("accessToken"))
        assertNull(transaction.spans.single().data?.get("http.query"))
        assertNull(transaction.spans.single().data?.get("http.fragment"))
    }
}
