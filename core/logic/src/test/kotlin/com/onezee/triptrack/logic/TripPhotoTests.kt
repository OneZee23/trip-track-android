package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import java.util.UUID
import kotlin.test.*

// iOS: TripCheckpointPhotosTests.swift
class TripCheckpointPhotosTests {
    private val start = Instant.ofEpochSecond(1_780_000_000)
    private fun track(seconds: Int) = (0..seconds).map { t -> TrackPoint(latitude = 45 + t * 10.0 / 111_320,
        longitude = 38.9, speed = 10.0, timestamp = start.plusSeconds(t.toLong())) }
    private fun checkpoint(at: Double, photoId: UUID? = null, photoIds: List<UUID> = emptyList()) =
        TripCheckpoint(timestamp = start.addingSeconds(at), latitude = 45 + at * 10 / 111_320,
            longitude = 38.9, distanceFromStart = at * 10, elapsedFromStart = at, photoId = photoId, photoIds = photoIds)
    private fun photo(capturedAt: Double?, lat: Double? = null, lon: Double? = null): TripPhoto {
        val id = UUID.randomUUID()
        return TripPhoto(id, "$id.jpg", timestamp = start, capturedAt = capturedAt?.let { start.addingSeconds(it) },
            exifLatitude = lat, exifLongitude = lon)
    }
    @Test fun testAnAttachedPhotoStaysWithItsCheckpointEvenWhenTimeSaysOtherwise() {
        val far = photo(3000.0)
        val near = checkpoint(3000.0)
        val mine = checkpoint(100.0, photoIds = listOf(far.id))
        val links = TripCheckpointPhotos.link(listOf(mine, near), listOf(far), emptyList())
        assertEquals(listOf(far.id), links[mine.id]?.map { it.id })
        assertNull(links[near.id])
    }
    @Test fun testCoverThenAttachedThenAutomatic() {
        val a = photo(null)
        val b = photo(null)
        val cover = photo(null)
        val cp = checkpoint(100.0, cover.id, listOf(a.id, b.id))
        val auto = photo(120.0, cp.latitude, cp.longitude)
        val links = TripCheckpointPhotos.link(listOf(cp), listOf(auto, b, cover, a), emptyList())
        assertEquals(listOf(cover.id, a.id, b.id, auto.id), links[cp.id]?.map { it.id })
    }
    @Test fun testAGeotaggedPhotoLinksEvenWithoutATrack() {
        val cp = checkpoint(1000.0)
        val p = photo(null, cp.latitude, cp.longitude)
        assertEquals(listOf(p.id), TripCheckpointPhotos.link(listOf(cp), listOf(p), emptyList())[cp.id]?.map { it.id })
    }
    @Test fun testAPhotoTakenMinutesAfterTheFlagBelongsToIt() {
        val cp = checkpoint(1000.0)
        val p = photo(1120.0)
        assertEquals(listOf(p.id), TripCheckpointPhotos.link(listOf(cp), listOf(p), track(3000))[cp.id]?.map { it.id })
    }
    @Test fun testAPhotoOutsideTheWindowStaysFree() {
        val cp = checkpoint(1000.0)
        val p = photo(1000 + TripCheckpointPhotos.timeWindow + 60)
        assertNull(TripCheckpointPhotos.link(listOf(cp), listOf(p), track(3000))[cp.id])
    }
    @Test fun testAnAttachedPhotoIsTheCoverRegardlessOfTime() {
        val cover = photo(2900.0)
        val near = photo(1010.0)
        val cp = checkpoint(1000.0, cover.id)
        val links = TripCheckpointPhotos.link(listOf(cp), listOf(near, cover), track(3000))
        assertEquals(cover.id, links[cp.id]?.first()?.id)
        assertEquals(2, links[cp.id]?.size)
    }
    @Test fun testEachPhotoGoesToExactlyOneCheckpoint() {
        val a = checkpoint(1000.0)
        val b = checkpoint(1400.0)
        val p = photo(1300.0)
        val links = TripCheckpointPhotos.link(listOf(a, b), listOf(p), track(3000))
        assertNull(links[a.id])
        assertEquals(listOf(p.id), links[b.id]?.map { it.id })
    }
    @Test fun testAGeotaggedPhotoWithoutTimeLinksByPlace() {
        val cp = checkpoint(1000.0)
        val p = photo(null, cp.latitude + 50.0 / 111_320, cp.longitude)
        assertEquals(listOf(p.id), TripCheckpointPhotos.link(listOf(cp), listOf(p), track(3000))[cp.id]?.map { it.id })
    }
    @Test fun testAPhotoWithNoMetadataStaysFree() {
        assertTrue(TripCheckpointPhotos.link(listOf(checkpoint(1000.0)), listOf(photo(null)), track(3000)).isEmpty())
    }
    @Test fun automaticTimeOrderAndMissingManualIds() {
        val early = photo(1000.0)
        val late = photo(1100.0)
        val cp = checkpoint(1000.0, UUID.randomUUID(), listOf(UUID.randomUUID()))
        assertEquals(listOf(early.id, late.id), TripCheckpointPhotos.link(listOf(cp), listOf(late, early), track(3000))[cp.id]?.map { it.id })
    }
    @Test fun placelessCheckpointParticipatesOnlyByTime() {
        val cp = checkpoint(1000.0).copy(latitude = 0.0, longitude = 0.0)
        val p = photo(null, 0.0, 0.0)
        assertTrue(TripCheckpointPhotos.link(listOf(cp), listOf(p), emptyList()).isEmpty())
        assertEquals(cp.id, TripCheckpointPhotos.nearestCheckpoint(p.copy(capturedAt = cp.timestamp), listOf(cp), track(3000))?.id)
    }
    // iOS: HomePrivacyTrimTests.swift (coordinate presence is pure model logic).
    @Test fun testAPlacelessCheckpointIsRecognisedAsSuch() {
        val placed = TripCheckpoint(timestamp = start, latitude = 45.035, longitude = 38.975,
            distanceFromStart = 0.0, elapsedFromStart = 0.0)
        assertTrue(placed.hasCoordinate)
        assertFalse(placed.copy(latitude = 0.0, longitude = 0.0).hasCoordinate)
    }
    @Test fun captureWindowIncludesItsEdgesAndRejectsOutsideTheTripWindow() {
        val cp = checkpoint(0.0)
        assertEquals(cp.id, TripCheckpointPhotos.nearestCheckpoint(photo(-900.0), listOf(cp), track(100))?.id)
        assertNull(TripCheckpointPhotos.nearestCheckpoint(photo(-900.001), listOf(cp), track(100)))
    }
}

// iOS: TripPhotoPlacementTests.swift
class TripPhotoPlacementTests {
    private val start = Instant.ofEpochSecond(1_780_000_000)
    private fun track(seconds: Int) = (0..seconds).map { t -> TrackPoint(latitude = 45 + t * 10.0 / 111_320,
        longitude = 38.9, speed = 10.0, timestamp = start.plusSeconds(t.toLong())) }
    private fun photo(capturedAt: Instant?, lat: Double? = null, lon: Double? = null) = TripPhoto(UUID.randomUUID(), "p.jpg",
        timestamp = start, capturedAt = capturedAt, exifLatitude = lat, exifLongitude = lon)
    @Test fun testThePhotoOwnCoordinateWins() {
        val placed = TripPhotoPlacement.place(listOf(photo(start.plusSeconds(50), 44.0, 37.0)), track(100))
        assertEquals(1, placed.size)
        assertEquals(TripPhotoPlacement.Placed.Source.photo, placed.first().source)
        assertNear(44.0, placed.first().latitude, 0.0001)
    }
    @Test fun testWithoutACoordinateThePlaceComesFromTheTrack() {
        val placed = TripPhotoPlacement.place(listOf(photo(start.plusSeconds(50))), track(100))
        assertEquals(TripPhotoPlacement.Placed.Source.track, placed.first().source)
        assertNear(45 + 500.0 / 111_320, placed.first().latitude, 0.0002)
    }
    @Test fun testAPhotoWithoutACaptureTimeStaysOffTheMap() {
        assertTrue(TripPhotoPlacement.place(listOf(photo(null)), track(100)).isEmpty())
    }
    @Test fun testAPhotoTakenOutsideTheTripStaysOffTheMap() {
        assertTrue(TripPhotoPlacement.place(listOf(photo(start.minusSeconds(3600)), photo(start.plusSeconds(86400))), track(100)).isEmpty())
    }
    @Test fun testATripWithoutATrackPlacesNothing() {
        assertTrue(TripPhotoPlacement.place(listOf(photo(start)), emptyList()).isEmpty())
    }
    // iOS: PhotoMetadataTests.swift (placement assertions; image parsing/storage are outside core:logic).
    @Test fun photoMetadataPlacementVectors() {
        val geotagged = photo(start, 44.7239, 37.7686)
        val placed = TripPhotoPlacement.place(listOf(geotagged), emptyList())
        assertEquals(TripPhotoPlacement.Placed.Source.photo, placed.first().source)
        assertNear(44.7239, placed.first().latitude, 0.0001)
        val points = (0..100).map { TrackPoint(latitude = 45 + it * 0.001, longitude = 38.9, speed = 10.0,
            timestamp = start.plusSeconds(it.toLong() - 50)) }
        val fromTrack = TripPhotoPlacement.place(listOf(photo(start)), points)
        assertEquals(TripPhotoPlacement.Placed.Source.track, fromTrack.first().source)
        assertNear(45.05, fromTrack.first().latitude, 0.0011)
    }
}

// iOS: TripPhotoPreparationTests.swift (value work; Swift Task scheduling/cancellation is outside this JVM port).
class TripPhotoPreparationTests {
    private val start = Instant.ofEpochSecond(1_780_000_000)
    private fun points() = (0..100).map { i -> TrackPoint(latitude = 45 + i * 10.0 / 111_320, longitude = 38.9,
        speed = 10.0, timestamp = start.plusSeconds(i.toLong())) }
    private fun photo(at: Double?, coordinate: GeoPoint? = null) = TripPhoto(UUID.randomUUID(), "test.jpg", timestamp = start,
        capturedAt = at?.let { start.addingSeconds(it) }, exifLatitude = coordinate?.latitude, exifLongitude = coordinate?.longitude)
    private fun assertSame(a: TripRouteLocator.Fix?, b: TripRouteLocator.Fix?) {
        assertEquals(a?.index, b?.index)
        assertEquals(a?.coordinate, b?.coordinate)
        assertEquals(a?.timestamp, b?.timestamp)
        assertEquals(a?.distanceFromStart, b?.distanceFromStart)
        assertEquals(a?.elapsedFromStart, b?.elapsedFromStart)
    }
    @Test fun testPreparedReadingsPreserveExifPriorityAndTimestampFallback() {
        val track = points()
        val ownCoordinate = photo(90.0, track[20].coordinate)
        val farExif = photo(60.0, GeoPoint(50.0, 40.0))
        val timestampOnly = photo(40.0)
        val noMetadata = photo(null)
        val outsideTrip = photo(-10.0)
        val photos = listOf(ownCoordinate, farExif, timestampOnly, noMetadata, outsideTrip)
        val origin = start.minusSeconds(30)
        val prepared = TripPhotoPlacement.prepare(photos, emptyList(), track, origin)
        assertEquals(listOf(ownCoordinate.id, farExif.id, timestampOnly.id), prepared.placed.map { it.id })
        assertEquals(20, prepared.fixes[ownCoordinate.id]?.index)
        assertEquals(60, prepared.fixes[farExif.id]?.index)
        assertEquals(50.0, prepared.placed.first { it.id == farExif.id }.latitude)
        assertEquals(70.0, prepared.fixes[timestampOnly.id]?.elapsedFromStart)
        assertNull(prepared.fixes[noMetadata.id])
        assertNull(prepared.fixes[outsideTrip.id])
        for (photo in photos) {
            val oldFix = photo.exifCoordinate?.let { TripRouteLocator.passes(it, track, 300.0, origin).firstOrNull() }
                ?: photo.capturedAt?.let { TripRouteLocator.fix(it, track, origin) }
            assertSame(prepared.fixes[photo.id], oldFix)
        }
    }
    @Test fun testNoLoosePhotosDoesNotCreateReadingsAndKeepsCheckpointLinks() {
        val track = points()
        val attached = photo(50.0)
        val cp = TripCheckpoint(timestamp = start.plusSeconds(50), latitude = 45.0, longitude = 38.9,
            distanceFromStart = 500.0, elapsedFromStart = 50.0, photoId = attached.id)
        val empty = TripPhotoPlacement.prepare(emptyList(), listOf(cp), track, start)
        assertTrue(empty.placed.isEmpty())
        assertTrue(empty.fixes.isEmpty())
        val linked = TripPhotoPlacement.prepare(listOf(attached), listOf(cp), track, start)
        assertEquals(listOf(attached.id), linked.links[cp.id]?.map { it.id })
        assertTrue(linked.placed.isEmpty())
        assertTrue(linked.fixes.isEmpty())
    }
    @Test fun testReusableIndexKeepsBothPassesAndDistanceGateRules() {
        val outward = points()
        val back = outward.reversed().mapIndexed { i, p -> TrackPoint(latitude = p.latitude, longitude = p.longitude,
            horizontalAccuracy = if (i % 7 == 0) 200.0 else 5.0, timestamp = start.plusSeconds(1300 + i.toLong()),
            isInterpolated = i % 5 == 0) }
        val track = outward + back
        val index = TripRouteIndex(track, start)
        val original = TripRouteLocator.passes(outward[50].coordinate, track, 120.0, start)
        val prepared = index.passes(outward[50].coordinate)
        assertEquals(2, prepared.size)
        assertEquals(original.size, prepared.size)
        original.zip(prepared).forEach { (a, b) -> assertSame(a, b) }
        assertSame(index.fix(track.last().timestamp), TripRouteLocator.fix(track.last().timestamp, track, start))
    }
    @Test fun testReplacementWithSamePhotoCountGetsFreshReadings() {
        val track = points()
        val old = photo(20.0)
        val new = photo(80.0)
        val first = TripPhotoPlacement.prepare(listOf(old), emptyList(), track, start)
        val second = TripPhotoPlacement.prepare(listOf(new), emptyList(), track, start)
        assertEquals(20, first.fixes[old.id]?.index)
        assertNull(second.fixes[old.id])
        assertEquals(80, second.fixes[new.id]?.index)
    }
    @Test fun indexIsAnImmutableSnapshot() {
        val points = points().toMutableList()
        val expected = TripRouteLocator.fix(points.last().timestamp, points, start)
        val index = TripRouteIndex(points, start)
        points.last().recordingSegmentIndex = 12
        points.clear()
        assertSame(expected, index.fix(start.plusSeconds(100)))
    }
}

// iOS: TripMomentsTests.swift
class TripMomentsTests {
    private val start = Instant.ofEpochSecond(1_700_000_000)
    private fun checkpoint(at: Double) = TripCheckpoint(timestamp = start.addingSeconds(at), latitude = 45.0, longitude = 38.9,
        distanceFromStart = at * 10, elapsedFromStart = at)
    private fun placed(at: Double): TripMoments.PlacedPhoto {
        val photo = TripPhoto(UUID.randomUUID(), "x.jpg", timestamp = start)
        return TripMoments.PlacedPhoto(photo, TripRouteLocator.Fix(0, GeoPoint(45.0, 38.9), start.addingSeconds(at), at * 10, at))
    }
    @Test fun testCheckpointsAreNumberedByTimeNotByInsertion() {
        val late = checkpoint(3600.0)
        val early = checkpoint(600.0)
        val moments = TripMoments.build(listOf(late, early), emptyMap(), emptyList())
        val first = assertIs<TripMoment.Checkpoint>(moments[0])
        assertEquals(early.id, first.value1.id)
        assertEquals(1, first.number)
    }
    @Test fun testLoosePhotosAreStackedByTheLinkingWindow() {
        val loose = listOf(placed(100.0), placed(500.0), placed(100.0 + 16 * 60))
        val moments = TripMoments.build(emptyList(), emptyMap(), loose)
        assertEquals(2, moments.size)
        assertEquals(2, assertIs<TripMoment.Photos>(moments[0]).photos.size)
    }
    @Test fun testMomentsInterleaveByTime() {
        val moments = TripMoments.build(listOf(checkpoint(2000.0)), emptyMap(), listOf(placed(500.0), placed(4000.0)))
        assertEquals(listOf(500.0, 2000.0, 4000.0), moments.map { it.elapsedFromStart })
    }
    @Test fun testLinkedPhotosRideWithTheirCheckpoint() {
        val cp = checkpoint(1000.0)
        val photo = TripPhoto(UUID.randomUUID(), "y.jpg", timestamp = start)
        val moments = TripMoments.build(listOf(cp), mapOf(cp.id to listOf(photo)), emptyList())
        assertEquals(1, moments.size)
        assertEquals(listOf(photo.id), assertIs<TripMoment.Checkpoint>(moments[0]).photos.map { it.id })
    }
    @Test fun stackWindowStartsAtFirstPhotoAndIncludesBoundary() {
        val moments = TripMoments.build(emptyList(), emptyMap(), listOf(placed(0.0), placed(900.0), placed(901.0)))
        assertEquals(listOf(2, 1), moments.map { assertIs<TripMoment.Photos>(it).photos.size })
        assertEquals(listOf(0.0, 901.0), moments.map { it.elapsedFromStart })
    }
}
