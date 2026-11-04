package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.HiddenSection
import com.onezee.triptrack.model.TrackPoint
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.double
import kotlin.test.Test
import kotlin.test.assertEquals

// iOS: TripHiddenPartsResolverTests.swift.
class TripHiddenPartsResolverTests {
    @Test
    fun matchesEverySharedResolutionAndPhotoVector() {
        val source = File("../../docs/specs/hidden-parts-vectors.json").readText()
        val vectors = Json.parseToJsonElement(source).jsonObject
        val epoch = Instant.parse("2026-01-01T00:00:00Z")
        val points = vectors.getValue("points").jsonArray.map { item ->
            val point = item.jsonObject
            val coordinate = point.getValue("c").jsonArray
            TrackPoint(latitude = coordinate[0].jsonPrimitive.double, longitude = coordinate[1].jsonPrimitive.double, timestamp = epoch.plusSeconds(point.getValue("t").jsonPrimitive.long))
        }
        val photos = vectors.getValue("photos").jsonArray
        for (caseElement in vectors.getValue("cases").jsonArray) {
            val case = caseElement.jsonObject
            val name = case.getValue("name").jsonPrimitive.content
            val input = case.getValue("input").jsonObject
            val sectionValues = input.getValue("sections").jsonArray.map { it.jsonArray.map { value -> value.jsonPrimitive.long } }
            val sections = sectionValues.map { HiddenSection(UUID.randomUUID(), epoch.plusSeconds(it[0]), epoch.plusSeconds(it[1])) }
            val zoneValue = input["zone"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonObject
            val home = zoneValue?.let {
                val coordinate = it.getValue("c").jsonArray
                GeoPoint(coordinate[0].jsonPrimitive.double, coordinate[1].jsonPrimitive.double) to it.getValue("r").jsonPrimitive.double
            }
            val breaks = input.getValue("recordingBreaks").jsonArray.map { epoch.plusSeconds(it.jsonPrimitive.long) }
            val result = TripHiddenPartsResolver.resolve(
                points, breaks, input.getValue("startMetres").jsonPrimitive.content.toInt(),
                input.getValue("endMetres").jsonPrimitive.content.toInt(), sections, home,
            )
            val expect = case.getValue("expect").jsonObject
            val expectedRuns = expect.getValue("visibleRuns").jsonArray.map { it.jsonArray.map { index -> index.jsonPrimitive.content.toInt() }.let { values -> values.first()..values.last() } }
            assertEquals(expectedRuns, result.visibleRuns, name)
            val expectedRanges = expect.getValue("hiddenRanges").jsonArray.map { it.jsonArray.map { time -> time.jsonPrimitive.long } }
            assertEquals(expectedRanges.map { epoch.plusSeconds(it[0]) to epoch.plusSeconds(it[1]) }, result.hiddenRanges.map { it.from to it.to }, name)
            assertEquals(expect.getValue("previewPointCount").jsonPrimitive.content.toInt(), result.previewPoints.size, name)
            assertEquals(expect.getValue("previewBreaks").jsonArray.map { it.jsonPrimitive.content.toInt() }, result.previewBreaks, name)
            val numbers = expect.getValue("publicNumbers").jsonObject
            assertEquals(numbers.getValue("distance").jsonPrimitive.double, result.visibleDistance, 15.0, name)
            assertEquals(numbers["start"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.long?.let(epoch::plusSeconds), result.visibleStart, name)
            assertEquals(numbers["end"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.long?.let(epoch::plusSeconds), result.visibleEnd, name)
            val hiddenPhotos = photos.filter { photoElement ->
                val photo = photoElement.jsonObject
                val captured = photo["capturedAt"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.long?.let(epoch::plusSeconds)
                val coordinate = photo["coordinate"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonArray?.let {
                    GeoPoint(it[0].jsonPrimitive.double, it[1].jsonPrimitive.double)
                }
                TripHiddenPartsResolver.photoIsHidden(
                    captured, coordinate, points, result, home,
                )
            }.map { it.jsonObject.getValue("id").jsonPrimitive.content }
            assertEquals(expect.getValue("hiddenPhotoIds").jsonArray.map { it.jsonPrimitive.content }, hiddenPhotos, name)
        }
    }
}
