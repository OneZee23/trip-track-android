// iOS: TripTrackTests/Fixtures and inline JSON fixtures
package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.ModelJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

internal fun fixture(name: String): String = checkNotNull(
    object {}.javaClass.getResourceAsStream("/ios/$name.json"),
) { "Missing iOS fixture: $name" }.bufferedReader().use { it.readText() }

internal fun jsonObject(raw: String): JsonObject = ModelJson.storage.parseToJsonElement(raw).jsonObject
