// iOS: Services/RemoteConfig/{RemoteConfig,RemoteConfigStore,RemoteCatalogs}.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(IsoInstantSerializer::class)

package com.onezee.triptrack.network

import com.onezee.triptrack.model.serialization.IsoInstantSerializer
import com.onezee.triptrack.model.serialization.IosObjectSerializer
import java.time.Instant
import kotlinx.serialization.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

// iOS: RemoteConfigStore.swift
@Serializable
data class RemoteConfigResponse(val version: String, val unchanged: Boolean? = null, val config: RemoteConfig? = null)

// iOS: RemoteCatalogs.swift (AnyJSON is kotlinx.serialization's JsonElement)
@Serializable
data class RemoteCatalogResponse(val version: String, val unchanged: Boolean? = null, val catalog: JsonElement? = null)

// iOS: RemoteConfig.swift
@KeepGeneratedSerializer
@Serializable(with = RemoteConfigSerializer::class)
data class RemoteConfig(
    val flags: Map<String, Boolean> = emptyMap(),
    val update: UpdatePolicy = UpdatePolicy(),
    val announcements: List<Announcement> = emptyList(),
) {
    // iOS: RemoteConfig.swift
    @Serializable
    data class UpdatePolicy(val recommended: String? = null, val required: String? = null, val message: LocalizedText? = null)

    // iOS: RemoteConfig.swift
    @Serializable
    data class Announcement(
        val id: String,
        val placement: Placement,
        val title: LocalizedText,
        val body: LocalizedText,
        val action: Action? = null,
        val startsAt: Instant? = null,
        val endsAt: Instant? = null,
        val minVersion: String? = null,
        val maxVersion: String? = null,
    ) {
        // iOS: RemoteConfig.swift
        @Serializable
        enum class Placement { feed, me }

        // iOS: RemoteConfig.swift
        @Serializable
        data class Action(val label: LocalizedText, val url: String) {
            init { java.net.URI(url) }
        }
    }

    // iOS: RemoteConfig.swift
    @Serializable(with = LocalizedTextSerializer::class)
    data class LocalizedText(val values: Map<String, String>) {
        init { require(!values["en"].isNullOrEmpty()) }
        fun text(language: String): String = values[language] ?: values.getValue("en")
    }

    // iOS: RemoteConfig.swift
    enum class UpdateNeed { recommended, required }

    fun updateNeed(appVersion: String): UpdateNeed? = when {
        update.required?.let { AppVersion.compare(appVersion, it) < 0 } == true -> UpdateNeed.required
        update.recommended?.let { AppVersion.compare(appVersion, it) < 0 } == true -> UpdateNeed.recommended
        else -> null
    }

    fun announcements(placement: Announcement.Placement, appVersion: String, now: Instant): List<Announcement> =
        announcements.filter { a ->
            a.placement == placement && (a.startsAt == null || now >= a.startsAt) &&
                (a.endsAt == null || now <= a.endsAt) &&
                (a.minVersion == null || AppVersion.compare(appVersion, a.minVersion) >= 0) &&
                (a.maxVersion == null || AppVersion.compare(appVersion, a.maxVersion) <= 0)
        }
}

// iOS: RemoteConfig.swift
object AppVersion {
    fun compare(a: String, b: String): Int {
        val left = a.split('.').filter(String::isNotEmpty).map { it.toIntOrNull() ?: 0 }
        val right = b.split('.').filter(String::isNotEmpty).map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(left.size, right.size)) {
            val comparison = (left.getOrNull(i) ?: 0).compareTo(right.getOrNull(i) ?: 0)
            if (comparison != 0) return comparison
        }
        return 0
    }
}

// iOS: RemoteConfig.swift (lossy announcement decoding)
object RemoteConfigSerializer : IosObjectSerializer<RemoteConfig>({ RemoteConfig.generatedSerializer() }) {
    override fun read(value: JsonObject, json: Json): JsonObject = buildJsonObject {
        val flags = value["flags"]?.let { runCatching { json.decodeFromJsonElement<Map<String, Boolean>>(it) }.getOrNull() }
        put("flags", json.encodeToJsonElement(flags ?: emptyMap()))
        val update = value["update"]?.let { runCatching { json.decodeFromJsonElement<RemoteConfig.UpdatePolicy>(it) }.getOrNull() }
        put("update", json.encodeToJsonElement(update ?: RemoteConfig.UpdatePolicy()))
        put("announcements", JsonArray((value["announcements"] as? JsonArray).orEmpty().filter { item ->
            runCatching { json.decodeFromJsonElement<RemoteConfig.Announcement>(item) }.isSuccess
        }))
    }
}

// iOS: RemoteConfig.swift (LocalizedText single-value container)
object LocalizedTextSerializer : KSerializer<RemoteConfig.LocalizedText> {
    private val map = MapSerializer(String.serializer(), String.serializer())
    override val descriptor: SerialDescriptor = map.descriptor
    override fun serialize(encoder: Encoder, value: RemoteConfig.LocalizedText) = encoder.encodeSerializableValue(map, value.values)
    override fun deserialize(decoder: Decoder) = RemoteConfig.LocalizedText(decoder.decodeSerializableValue(map))
}
