// iOS: MeAggregatesTests.swift. Same June 15 anchor and boundary inputs, UTC calendar.
package com.onezee.triptrack.social

import com.onezee.triptrack.feature.stats.MeAggregates
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.ui.RegionDisplay
import com.onezee.triptrack.designsystem.strings.AppLanguage
import java.time.*
import kotlin.test.*
import org.junit.Test

class MeAggregatesPortTests {
    private val zone=ZoneOffset.UTC
    private val year=2026
    private val now=date(year,6,15,12)
    private fun date(y:Int,m:Int,d:Int,h:Int=10)=LocalDateTime.of(y,m,d,h,0).toInstant(zone)
    private fun trip(start:Instant,km:Double=10.0,hours:Double=1.0,region:String?=null)=Trip(startDate=start,endDate=start.plusSeconds((hours*3600).toLong()),distance=km*1000,region=region)
    private fun compute(vararg trips:Trip)=MeAggregates.compute(trips.toList(),now=now,zone=zone)
    @Test fun emptyIsSafe() {
        val a=compute();assertEquals(0,a.tripCount);assertEquals(0.0,a.totalKm);assertEquals(0,a.yearTripCount);assertEquals(0,a.regionsAllTime);assertEquals(0,a.yearStreak)
        assertEquals(List(12){0.0},a.monthlyKm);assertNull(a.yearAgoTrip);assertNull(a.longestYearTrip);assertNull(a.newRegion);assertNull(a.topRegion);assertTrue(a.recentTrips.isEmpty());assertEquals(year,a.year)
    }
    @Test fun scopesYearAtJan1AndDec31() {
        val a=compute(trip(date(year,1,1,0),100.0),trip(date(year,12,31,23),50.0),trip(date(year-1,12,31,23),999.0),trip(date(year+1,1,1,0),777.0))
        assertEquals(2,a.yearTripCount);assertEquals(150.0,a.yearKm,.001);assertEquals(4,a.tripCount);assertEquals(1926.0,a.totalKm,.001)
        assertEquals(100.0,a.monthlyKm[0],.001);assertEquals(50.0,a.monthlyKm[11],.001);assertEquals(0.0,a.monthlyKm.subList(1,11).sum(),.001)
        assertEquals(100.0,a.longestYearTrip!!.distance/1000,.001);assertEquals(999.0,a.longestTripKm,.001)
    }
    @Test fun longestConsecutiveYearRun() { assertEquals(3,compute(*listOf(3 to 1,3 to 2,3 to 3,5 to 10,5 to 11,6 to 1).map{trip(date(year,it.first,it.second))}.toTypedArray()).yearStreak) }
    @Test fun ignoresMultipleTripsOnSameDay() { assertEquals(2,compute(trip(date(year,4,1,8)),trip(date(year,4,1,20)),trip(date(year,4,2))).yearStreak) }
    @Test fun singleDayIsOne() { assertEquals(1,compute(trip(date(year,2,5))).yearStreak) }
    @Test fun emptyStreakIsZero() { assertEquals(0,MeAggregates.longestStreak(emptySet())) }
    @Test fun newRegionRequiresTwo() { assertNull(compute(trip(date(year,3,1),region="Тверская обл.")).newRegion) }
    @Test fun mostRecentFirstVisitWins() {
        val a=compute(trip(date(year-2,5,1),region="Московская обл."),trip(date(year,1,10),region="Московская обл."),trip(date(year,2,1),region="Тверская обл."),trip(date(year,4,1),region="Ярославская обл."))
        assertEquals("Yaroslavl Oblast",a.newRegion);assertEquals("Ярославская обл.",RegionDisplay.localized(a.newRegion,AppLanguage.ru))
    }
    @Test fun lastYearsRegionIsNotNew() { assertNull(compute(trip(date(year-1,5,1),region="Московская обл."),trip(date(year,2,1),region="Тверская обл."),trip(date(year-1,11,1),region="Тверская обл.")).newRegion) }
    @Test fun yearAgoChoosesBestWithinThreeDays() {
        val target=date(year-1,6,15,12)
        val a=compute(trip(target.plusSeconds(86400),40.0),trip(target.minusSeconds(2*86400),90.0),trip(target.plusSeconds(10*86400),500.0))
        assertEquals(90.0,a.yearAgoTrip!!.distance/1000,.001)
    }
    @Test fun dayMaximaSumAllTrips() {
        val a=compute(trip(date(year,5,1,8),10.0,2.0),trip(date(year,5,1,14),50.0,3.0),trip(date(year,5,2),50.0,4.0))
        assertEquals(60.0,a.maxDayKm,.001);assertEquals(5*3600.0,a.maxDayDuration,1.0)
    }
    @Test fun sameRegionInTwoLanguagesCountsOnce() {
        val a=compute(trip(date(year,3,1),region="Krasnodar Krai"),trip(date(year,3,9),region="Краснодарский край"),trip(date(year,4,2),region="Adjara"))
        assertEquals(2,a.regionsAllTime);assertEquals(2,a.yearRegions);assertEquals(setOf("Krasnodar Krai","Adjara"),a.regionFirstVisit.keys)
        assertEquals(date(year,3,1),a.regionFirstVisit["Krasnodar Krai"]);assertEquals("Krasnodar Krai",a.topRegion)
        assertEquals(setOf("Krasnodar Krai"),a.monthRegions[MeAggregates.monthKey(date(year,3,1),zone)])
    }
    @Test fun distinctRegionsPerMonth() {
        val a=compute(trip(date(year,3,1),region="A"),trip(date(year,3,2),region="A"),trip(date(year,3,3),region="B"),trip(date(year,3,4)),trip(date(year,4,1),region="A"))
        val march=MeAggregates.monthKey(date(year,3,1),zone)
        assertEquals(setOf("A","B"),a.monthRegions[march]);assertEquals(setOf("A"),a.monthRegions[march+1]);assertNull(a.monthRegions[march-1])
    }
}
