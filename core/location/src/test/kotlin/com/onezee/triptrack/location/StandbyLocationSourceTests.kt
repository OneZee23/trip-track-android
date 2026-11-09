package com.onezee.triptrack.location

import com.onezee.triptrack.location.provider.LocationSource
import com.onezee.triptrack.location.provider.StandbyLocationSource
import com.onezee.triptrack.location.provider.TrackingMode
import com.onezee.triptrack.logic.LocationFix
import com.onezee.triptrack.logic.TrackTestKit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

// No iOS counterpart: MIUI cuts the platform GPS request of a battery-saver app a minute after the
// screen locks (owner's POCO, 7 and 9 Nov 2026), Google Play services keeps its own.
class StandbyLocationSourceTests {
    private var clock = 0L
    private val primary = Fake()
    private val standby = Fake()
    private val delivered = mutableListOf<Double>()
    private val switches = mutableListOf<Boolean>()
    private val source = StandbyLocationSource(primary, standby, elapsedMillis = { clock }, onSwitch = { switches += it })

    private fun fix(second: Double) = TrackTestKit.fix(speed = 10.0, after = second)

    private fun start(mode: TrackingMode = TrackingMode.recording) =
        source.start(mode) { fix, _ -> delivered += (fix.timestamp.toEpochMilli() - TrackTestKit.epoch.toEpochMilli()) / 1000.0 }

    @Test
    fun aWorkingPlatformProviderRecordsAloneAsBefore() {
        start()
        for (second in 1..10) {
            clock = second * 1_000L
            primary.emit(fix(second.toDouble()))
            standby.emit(fix(second + 0.5))
        }
        assertEquals((1..10).map(Int::toDouble), delivered)
        assertTrue(switches.isEmpty())
    }

    @Test
    fun theStandbySpeaksOnlyAfterThePlatformFallsSilent() {
        start()
        clock = 1_000
        primary.emit(fix(1.0))
        // MIUI took the request: the platform provider says nothing more.
        clock = 3_900
        standby.emit(fix(3.9))
        clock = 4_100
        standby.emit(fix(4.1))
        clock = 5_100
        standby.emit(fix(5.1))
        assertEquals(listOf(1.0, 4.1, 5.1), delivered)
        assertEquals(listOf(true), switches)
    }

    @Test
    fun thePlatformTakesOverAgainWhenItComesBack() {
        start()
        clock = 1_000
        primary.emit(fix(1.0))
        clock = 10_000
        standby.emit(fix(10.0))
        // The screen came on: MIUI gave the request back.
        clock = 11_000
        primary.emit(fix(11.0))
        clock = 11_500
        standby.emit(fix(11.5))
        assertEquals(listOf(1.0, 10.0, 11.0), delivered)
        assertEquals(listOf(true, false), switches)
    }

    @Test
    fun aPlatformThatNeverSpokeIsRescuedToo() {
        start()
        clock = 2_000
        standby.emit(fix(2.0))
        clock = 3_000
        standby.emit(fix(3.0))
        assertEquals(listOf(3.0), delivered)
    }

    @Test
    fun aWifiPositionNeverStandsInForGps() {
        start()
        clock = 10_000
        standby.emit(TrackTestKit.fix(speed = 0.0, after = 10.0, accuracy = 35.0))
        standby.emit(TrackTestKit.fix(speed = 0.0, after = 10.2, accuracy = -1.0))
        clock = 11_000
        standby.emit(TrackTestKit.fix(speed = 10.0, after = 11.0, accuracy = 6.0))
        assertEquals(listOf(11.0), delivered)
        assertEquals(listOf(true), switches)
    }

    @Test
    fun idleUsesThePlatformProviderOnly() {
        start(TrackingMode.idle)
        assertEquals(TrackingMode.idle, primary.mode)
        assertNull(standby.mode)
    }

    @Test
    fun aPhoneWithoutPlayServicesRecordsWithThePlatformAlone() {
        val alone = StandbyLocationSource(primary, null, elapsedMillis = { clock })
        alone.start(TrackingMode.recording) { fix, _ -> delivered += fix.speed }
        primary.emit(fix(1.0))
        assertEquals(listOf(10.0), delivered)
    }

    @Test
    fun stopStopsBoth() {
        start()
        source.stop()
        assertTrue(primary.stopped && standby.stopped)
    }

    private class Fake : LocationSource {
        var mode: TrackingMode? = null
        var stopped = false
        private var onFix: ((LocationFix, Double) -> Unit)? = null

        override fun start(mode: TrackingMode, onFix: (LocationFix, Double) -> Unit) {
            this.mode = mode
            this.onFix = onFix
            stopped = false
        }

        override fun stop() {
            stopped = true
        }

        fun emit(fix: LocationFix) {
            onFix?.invoke(fix, 0.0)
        }
    }
}
