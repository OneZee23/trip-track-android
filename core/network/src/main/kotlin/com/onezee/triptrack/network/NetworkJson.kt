// iOS: APIClient.swift, ISODate.swift
package com.onezee.triptrack.network

import com.onezee.triptrack.model.serialization.ModelJson
import kotlinx.serialization.json.Json

object NetworkJson {
    val wire: Json = ModelJson.wire
}
