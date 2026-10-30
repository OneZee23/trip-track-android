// iOS: Services/RemoteConfig/RemoteConfig.swift, RemoteCatalogs.swift
package com.onezee.triptrack.network

import java.time.Instant
import kotlin.test.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.junit.Test

class RemoteConfigTests {
    private val json = NetworkJson.wire

    @Test fun localizedTextUsesDirectMapAndRequiresEnglish() {
        val text = json.decodeFromString<RemoteConfig.LocalizedText>("{\"en\":\"Roads\",\"ru\":\"Дороги\"}")
        assertEquals("Дороги", text.text("ru")); assertEquals("Roads", text.text("de"))
        assertEquals(json.parseToJsonElement("{\"en\":\"Roads\",\"ru\":\"Дороги\"}"), json.encodeToJsonElement(text))
        assertFailsWith<IllegalArgumentException> { json.decodeFromString<RemoteConfig.LocalizedText>("{\"ru\":\"Дороги\"}") }
    }

    @Test fun malformedAnnouncementsAreSkippedIndividually() {
        val config = json.decodeFromString<RemoteConfig>("""{
            "flags":{"discoveries":false,"future":true},"update":{"required":"0.9"},
            "announcements":[
                {"id":"ok","placement":"feed","title":{"en":"Title"},"body":{"en":"Body"}},
                {"id":"future","placement":"future","title":{"en":"Title"},"body":{"en":"Body"}},
                {"id":"bad-date","placement":"feed","title":{"en":"Title"},"body":{"en":"Body"},"startsAt":"not-a-date"},
                {"id":"bad-title","placement":"me","title":{"ru":"Title"},"body":{"en":"Body"}}
            ],"unknown":true} """)
        assertEquals(listOf("ok"), config.announcements.map { it.id })
        assertEquals(false, config.flags["discoveries"])
        assertEquals(config, json.decodeFromString(json.encodeToString(config)))
        assertEquals(RemoteConfig(), json.decodeFromString<RemoteConfig>("{\"flags\":false,\"update\":null,\"announcements\":42}"))
    }

    @Test fun boundsAreInclusiveAndVersionComparisonIsNumeric() {
        assertEquals(0, AppVersion.compare("0.9", "0.9.0"))
        assertTrue(AppVersion.compare("0.10", "0.9") > 0)
        val now = Instant.parse("2026-10-20T09:00:00Z")
        val config = RemoteConfig(update = RemoteConfig.UpdatePolicy("1.2", "1.0"), announcements = listOf(
            RemoteConfig.Announcement("test", RemoteConfig.Announcement.Placement.feed,
                RemoteConfig.LocalizedText(mapOf("en" to "Title")), RemoteConfig.LocalizedText(mapOf("en" to "Body")),
                startsAt = now, endsAt = now, minVersion = "1.0", maxVersion = "1.0"),
        ))
        assertEquals(RemoteConfig.UpdateNeed.required, config.updateNeed("0.9"))
        assertEquals(RemoteConfig.UpdateNeed.recommended, config.updateNeed("1.0"))
        assertNull(config.updateNeed("1.2"))
        assertEquals(1, config.announcements(RemoteConfig.Announcement.Placement.feed, "1.0", now).size)
        assertTrue(config.announcements(RemoteConfig.Announcement.Placement.feed, "1.0", now.plusSeconds(1)).isEmpty())
    }

    @Test fun unchangedAndArbitraryCatalogJsonKeepTheirShapes() {
        val unchanged = json.decodeFromString<RemoteConfigResponse>("{\"version\":\"v1\",\"unchanged\":true}")
        assertNull(unchanged.config)
        val response = json.decodeFromString<RemoteCatalogResponse>("""{"version":"v2","catalog":{"version":1,"makes":[],"future":[true,null,{"x":"y"}]}}""")
        assertEquals(response, json.decodeFromString(json.encodeToString(response)))
        assertEquals(JsonPrimitive(1), response.catalog!!.jsonObject["version"])
    }
}
