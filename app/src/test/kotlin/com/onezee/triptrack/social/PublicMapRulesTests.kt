// iOS: PublicMapStartTests.swift, PublicMapRegionsTileTests.swift, TripSourceTests.swift,
// PublicJourneyAdapterTests.swift. Same geometry, timestamps, distances and bounds.
package com.onezee.triptrack.social

import com.onezee.triptrack.logic.encodePolyline

import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.social.*
import com.onezee.triptrack.logic.previewCoordinates
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.*
import java.time.Instant
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Test

class PublicMapRulesTests {
    @Test fun titleRidesFromWire() {
        val dto = NetworkJson.wire.decodeFromString<PublicTripDTO>("""{"id":"D37A2001-0000-4000-8000-000000000001","startDate":"2026-10-20T09:11:00Z","endDate":"2026-10-20T15:00:00Z","distance":418000,"duration":20000,"region":"Киров","title":"Сыктывкар → Киров","previewPolyline":null}""")
        assertEquals("Сыктывкар → Киров", dto.asTrip().title)
    }
    @Test fun oldServerWithoutTitle() {
        val dto = NetworkJson.wire.decodeFromString<PublicTripDTO>("""{"id":"D37A2001-0000-4000-8000-000000000002","startDate":"2026-10-20T09:11:00Z","endDate":null,"distance":1000,"duration":null,"region":null,"previewPolyline":null}""")
        assertNull(dto.asTrip().title)
    }
    @Test fun startBoundsCoverRecentRoutes() {
        val b = PublicMapStart.coveringRoutes(listOf(listOf(GeoPoint(58.60,49.66),GeoPoint(61.67,50.84)), emptyList(), listOf(GeoPoint(54.74,55.97))))!!
        assertEquals(54.74,b.minLat,1e-9); assertEquals(61.67,b.maxLat,1e-9); assertEquals(49.66,b.minLon,1e-9); assertEquals(55.97,b.maxLon,1e-9)
    }
    @Test fun noPreviewsLeaveCameraToRoutes() { assertNull(PublicMapStart.coveringRoutes(emptyList())); assertNull(PublicMapStart.coveringRoutes(listOf(emptyList(),emptyList()))) }
    @Test fun boundsSurviveNavigation() {
        val a=GeoBounds(54.7,61.7,49.6,56.0); val b=GeoBounds(54.7,61.7,49.6,56.0)
        val id=UUID.fromString("D37A2000-0000-4000-8000-000000000081")
        assertEquals(a,b);assertEquals(a.hashCode(),b.hashCode());assertEquals(TripsDestination.PublicMap(id,"A",a),TripsDestination.PublicMap(id,"A",b))
    }
    @Test fun framesEveryPointNotMiddles() {
        val down=listOf(GeoPoint(43.4,1.0),GeoPoint(47.0,2.0),GeoPoint(50.65,3.1))
        val back=listOf(GeoPoint(50.6,3.0),GeoPoint(48.2,2.1),GeoPoint(45.8,1.3))
        assertEquals(GeoBounds(43.4,50.65,1.0,3.1),PublicMapStart.coveringRoutes(listOf(down,emptyList(),back)))
        val middle=PublicMapStart.covering(listOf(down[1],back[1]))!!;assertTrue(middle.maxLat-middle.minLat<2)
    }
    @Test fun tripsPanelStopsAtFourAndHalf() {
        val one=PublicMapStart.tripsPanelHeight(1);val four=PublicMapStart.tripsPanelHeight(4);val five=PublicMapStart.tripsPanelHeight(5)
        assertEquals(3*56f,four-one,.001f);assertEquals(28f,five-four,.001f);assertEquals(five,PublicMapStart.tripsPanelHeight(50));assertEquals(one,PublicMapStart.tripsPanelHeight(0))
    }
    @Test fun zeroRegionsWithRoutesHidden() { assertTrue(PublicMapStart.hidesRegions(0,2)) }
    @Test fun countedRegionsShown() { assertFalse(PublicMapStart.hidesRegions(21,2)) }
    @Test fun emptyMapHidesNothing() { assertFalse(PublicMapStart.hidesRegions(0,0)) }
    @Test fun mapsActualGeometry() { val t=SocialFixtures.publicTrip(1).asTrip();assertEquals(2,t.previewCoordinates.size);assertEquals(55.7558,t.previewCoordinates[0].latitude,.001) }
    @Test fun carriesDistanceRegionAndEnd() { val t=SocialFixtures.publicTrip(1).asTrip();assertEquals(412000.0,t.distance,.5);assertEquals("Нижегородская обл.",t.region);assertEquals(1780017880,t.endDate!!.epochSecond) }
    @Test fun remoteAlwaysPublic() { assertFalse(SocialFixtures.publicTrip(1).asTrip().isPrivate) }
    @Test fun noGeometryStillCountsDistance() { val t=SocialFixtures.publicTrip(1).copy(previewPolyline=null).asTrip();assertTrue(t.previewCoordinates.isEmpty());assertEquals(412000.0,t.distance,.5) }
    @Test fun garbageBase64DoesNotCrash() { val t=SocialFixtures.publicTrip(1).copy(previewPolyline="не base64 ни разу").asTrip();assertNull(t.previewPolyline);assertTrue(t.previewCoordinates.isEmpty()) }
    @Test fun paginationFollowsCursor() = runBlocking {
        val cursors=mutableListOf<String?>();val api=FakeSocialApi().apply { publicPage={ c -> cursors+=c;PublicTripsResponse(listOf(SocialFixtures.publicTrip(cursors.size)),if(c==null)"cursor-2" else null) } }
        assertEquals(2,RemoteTrips.load(api,SocialFixtures.account).trips.size);assertEquals(listOf(null,"cursor-2"),cursors)
    }
    @Test fun repeatedCursorIsTruncated() = runBlocking {
        var count=0;val api=FakeSocialApi().apply { publicPage={count++;PublicTripsResponse(listOf(SocialFixtures.publicTrip(count)),"same")} }
        val result=RemoteTrips.load(api,SocialFixtures.account);assertTrue(count<=2);assertFalse(result.trips.isEmpty());assertTrue(result.failed)
    }
    @Test fun successfulEmptyIsNotFailure() = runBlocking {
        val api=FakeSocialApi().apply { publicPage={PublicTripsResponse(emptyList())} };val result=RemoteTrips.load(api,SocialFixtures.account)
        assertTrue(result.trips.isEmpty());assertFalse(result.failed)
    }
    @Test fun transportFailureIsNotEmptySuccess() = runBlocking {
        val api=FakeSocialApi().apply { publicPage={throw java.io.IOException("offline")} };val result=RemoteTrips.load(api,SocialFixtures.account)
        assertTrue(result.trips.isEmpty());assertTrue(result.failed)
    }
    @Test fun boundaryDeduplicates() = runBlocking {
        val api=FakeSocialApi().apply { publicPage={PublicTripsResponse(listOf(SocialFixtures.publicTrip(1)),if(it==null)"cursor-2" else null)} }
        assertEquals(1,RemoteTrips.load(api,SocialFixtures.account).trips.size)
    }
    @Test fun failedSecondPageKeepsPartial() = runBlocking {
        val api=FakeSocialApi().apply { publicPage={ if(it!=null)throw java.io.IOException("offline");PublicTripsResponse(listOf(SocialFixtures.publicTrip(1)),"cursor-2")} }
        val result=RemoteTrips.load(api,SocialFixtures.account);assertEquals(1,result.trips.size);assertTrue(result.failed)
    }
    @Test fun pageCapIsTwenty() = runBlocking {
        var calls=0;val api=FakeSocialApi().apply { publicPage={calls++;PublicTripsResponse(emptyList(),"$calls")} }
        assertTrue(RemoteTrips.load(api,SocialFixtures.account).failed);assertEquals(20,calls)
    }
    @Test fun publicLegDerivesEndAndSpeed() {
        val start=Instant.parse("2026-09-13T10:00:00Z");val t=PublicJourneyLeg(UUID.randomUUID(),start,distance=480000.0,duration=7200,region="Краснодарский край").asTrip()
        assertEquals(start.plusSeconds(7200),t.endDate);assertEquals(480000.0/7200,t.averageSpeed,.0001)
    }
    @Test fun publicLegDecodesPreview() {
        val coords=listOf(GeoPoint(45.035,38.975),GeoPoint(43.585,39.723))
        val t=PublicJourneyLeg(UUID.randomUUID(),Instant.parse("2026-09-13T00:00:00Z"),distance=100000.0,duration=3600,previewPolyline=java.util.Base64.getEncoder().encodeToString(Trip.encodePolyline(coords))).asTrip()
        assertEquals(2,t.previewCoordinates.size);assertEquals(45.035,t.previewCoordinates.first().latitude,.001)
    }
    @Test fun frenchMapUsesTheSameTwoRoutesAsTheIphoneFlow() {
        val trips = SocialFixtures.mapTrips().map { it.asTrip() }
        assertEquals(2, trips.size)
        assertEquals(1842.0, trips.sumOf { it.distance } / 1000, .0001)
        assertEquals(listOf(2400, 2400), trips.map { it.previewCoordinates.size })
    }

}
