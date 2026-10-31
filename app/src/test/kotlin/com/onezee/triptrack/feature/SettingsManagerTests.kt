// iOS: SettingsUnitWireTests.swift, HomeSettingsTests.swift, NotificationPreferencesView.swift
package com.onezee.triptrack.feature

import com.onezee.triptrack.data.RoomSettingsStore
import com.onezee.triptrack.data.db.entity.UserSettingsEntity
import com.onezee.triptrack.data.sync.*
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.*
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsManagerTests : WorldTest() {
    private val zones = mutableListOf<Pair<HomeZone?, HomeZone?>>()
    private val requests = mutableListOf<SyncRequest>()
    private val effects = object : SettingsEffects {
        override suspend fun cloudSyncChanged(enabled: Boolean) = Unit
        override suspend fun homeZoneChanged(before: HomeZone?, after: HomeZone?) { zones += before to after }
        override suspend fun autoRecordChanged(mode: AutoRecordMode, timeout: Int, stereos: List<SavedBluetoothDevice>) = Unit
        override suspend fun publicMapChanged(enabled: Boolean) = Unit
    }
    private val sink = object : SyncRequestSink {
        override fun enqueue(request: SyncRequest) { requests += request }
        override fun cancel(entityId: UUID, entityType: SyncEntityType) = Unit
    }
    private fun manager(country: String = "RU") = own(SettingsManager(RoomSettingsStore(db) { t0 }, preferences, sink, effects, dispatcher,
        deviceLocale = Locale.forLanguageTag("en-" + country)))
    @Test fun choosingMilesAndGallonsLandInEntityAndDefaults() = runTest(dispatcher) {
        val vm = manager(); runCurrent(); vm.setDistanceUnit(DistanceUnit.miles); vm.setVolumeUnit(VolumeUnit.gallons); runCurrent()
        assertEquals("miles", db.settingsDao().settings()?.distanceUnit)
        assertEquals("gallons", db.settingsDao().settings()?.volumeUnit)
        assertEquals(DistanceUnit.miles, vm.state.value.distanceUnit)
        assertEquals("miles", preferences.string("distanceUnit"))
        assertEquals(t0, db.settingsDao().settings()?.lastModifiedAt)
    }
    @Test fun chosenKilometresAreWrittenToo() = runTest(dispatcher) {
        val vm = manager(); runCurrent(); vm.setDistanceUnit(DistanceUnit.miles); runCurrent(); vm.setDistanceUnit(DistanceUnit.km); runCurrent()
        assertEquals("km", db.settingsDao().settings()?.distanceUnit)
        assertEquals("km", preferences.string("distanceUnit"))
    }
    @Test fun anyOtherSaveCarriesUnitChoiceAndSyncCoalescesForFiveSeconds() = runTest(dispatcher) {
        val vm = manager(); runCurrent()
        preferences.set("distanceUnit", "miles"); preferences.set("volumeUnit", "gallons")
        vm.selectVehicle(null); runCurrent(); vm.setTheme("light"); runCurrent()
        assertEquals("miles", db.settingsDao().settings()?.distanceUnit)
        assertEquals("gallons", db.settingsDao().settings()?.volumeUnit)
        advanceTimeBy(4999); runCurrent(); assertTrue(requests.isEmpty())
        advanceTimeBy(1); runCurrent(); assertEquals(1, requests.size)
        assertEquals(SyncAction.upload, requests.single().action)
    }
    @Test fun remoteMilesReachDefaults() = runTest(dispatcher) {
        val vm = manager(); runCurrent()
        val row = db.settingsDao().settings()!!
        db.settingsDao().upsert(row.copy(distanceUnit = "miles", volumeUnit = "gallons"))
        vm.reload(); runCurrent()
        assertEquals(DistanceUnit.miles, vm.state.value.distanceUnit)
        assertEquals(VolumeUnit.gallons, vm.state.value.volumeUnit)
    }
    @Test fun unknownRemoteValueLeavesChoiceAlone() = runTest(dispatcher) {
        preferences.set("distanceUnit", "miles")
        val vm = manager(); runCurrent()
        db.settingsDao().upsert(db.settingsDao().settings()!!.copy(distanceUnit = "kilometres"))
        vm.reload(); runCurrent(); assertEquals(DistanceUnit.miles, vm.state.value.distanceUnit)
    }
    @Test fun launchPrefersLocalChoiceOverLegacyKmColumn() = runTest(dispatcher) {
        db.settingsDao().upsert(UserSettingsEntity(userId, distanceUnit = "km", volumeUnit = "liters"))
        preferences.set("distanceUnit", "miles"); preferences.set("volumeUnit", "gallons")
        val vm = manager(); runCurrent()
        assertEquals(DistanceUnit.miles, vm.state.value.distanceUnit)
        assertEquals("miles", db.settingsDao().settings()?.distanceUnit)
        assertEquals("gallons", db.settingsDao().settings()?.volumeUnit)
    }
    @Test fun noLocalChoiceTakesColumnAndRemoteMilesBeatMetricGuess() = runTest(dispatcher) {
        db.settingsDao().upsert(UserSettingsEntity(userId, distanceUnit = "miles", volumeUnit = "gallons"))
        val vm = manager(); runCurrent()
        assertEquals(DistanceUnit.miles, vm.state.value.distanceUnit)
        assertEquals(VolumeUnit.gallons, vm.state.value.volumeUnit)
    }
    @Test fun firstMileRegionGuessDoesNotStampRow() = runTest(dispatcher) {
        val stamp = t0.minusSeconds(3600)
        db.settingsDao().upsert(UserSettingsEntity(userId, lastModifiedAt = stamp))
        val vm = manager("US"); runCurrent()
        assertEquals(DistanceUnit.miles, vm.state.value.distanceUnit)
        assertEquals("miles", db.settingsDao().settings()?.distanceUnit)
        assertEquals(stamp, db.settingsDao().settings()?.lastModifiedAt)
    }
    @Test fun chosenKmSurvivesMileRegionGuess() = runTest(dispatcher) {
        preferences.set("distanceUnit", "km")
        val vm = manager("US"); runCurrent()
        assertEquals(DistanceUnit.km, vm.state.value.distanceUnit)
    }
    @Test fun cloudAndHomePrivacyAreOptIn() = runTest(dispatcher) {
        val vm = manager(); runCurrent()
        assertFalse(vm.state.value.cloudSyncEnabled)
        assertFalse(vm.state.value.home.trimsPublicTracks)
        assertEquals(500, vm.state.value.home.radius.rawValue)
        assertNull(vm.state.value.home.activeZone)
    }
    @Test fun hidingHomeDoesNotDisableZoneOrResend() = runTest(dispatcher) {
        val vm = manager(); runCurrent()
        val home = HomeState(GeoPoint(45.035, 38.975), trimsPublicTracks = true)
        vm.setHome(home); runCurrent(); assertEquals(1, zones.size)
        vm.setHome(home.copy(showsOnMap = false)); runCurrent()
        assertNull(vm.state.value.home.mapPin); assertNotNull(vm.state.value.home.activeZone)
        assertEquals(1, zones.size)
        vm.setHome(home.copy(radius = HomeSettings.Radius.district)); runCurrent(); assertEquals(2, zones.size)
        vm.setHome(home.copy(trimsPublicTracks = false)); runCurrent(); assertEquals(3, zones.size)
        assertNull(vm.state.value.home.activeZone)
    }
    @Test fun everyZoneCoordinateAndRadiusChangeResendsBothWays() = runTest(dispatcher) {
        val vm = manager(); runCurrent()
        val point = GeoPoint(45.0, 38.975)
        vm.setHome(HomeState(point)); runCurrent(); assertTrue(zones.isEmpty())
        vm.setHome(HomeState(point, trimsPublicTracks = true)); runCurrent()
        vm.setHome(HomeState(point.copy(latitude = 45.01), trimsPublicTracks = true)); runCurrent()
        vm.setHome(HomeState(point.copy(longitude = 39.0), trimsPublicTracks = true)); runCurrent()
        vm.setHome(HomeState()); runCurrent()
        assertEquals(4, zones.size)
    }
    @Test fun stereosDeduplicateByUuidAndKeepVehicleAssociation() = runTest(dispatcher) {
        val vm = manager(); runCurrent()
        val id = UUID.randomUUID()
        val stereo = SavedBluetoothDevice("chosen", "Car", id)
        vm.addStereo(stereo); vm.addStereo(stereo); runCurrent()
        assertEquals(1, vm.state.value.stereos.size)
        assertEquals(id, vm.vehicleForStereo("Car", listOf(Vehicle(id = id))))
        assertNull(vm.vehicleForStereo("Car", listOf(Vehicle(id = id, soldAt = t0))))
        vm.removeStereos(id); runCurrent(); assertTrue(vm.state.value.stereos.isEmpty())
    }
    @Test fun pickerOnlyChangesPresentationUntilSetterIsCalled() = runTest(dispatcher) {
        val vm = manager(); runCurrent(); val before = db.settingsDao().settings()
        vm.openSheet(SettingsSheet.settings); vm.openSheet(SettingsSheet.units); vm.dismissSheet()
        assertEquals(SettingsSheet.settings, vm.state.value.sheet)
        assertEquals(before, db.settingsDao().settings())
        vm.setLanguage("fil"); runCurrent(); assertEquals("fil", db.settingsDao().settings()?.language)
    }
    @Test fun notificationsMissingFieldsDefaultTrueAndSaveCoalesces() = runTest(dispatcher) {
        val saved = mutableListOf<NotificationPrefsUpdateRequest>()
        val vm = own(NotificationPreferencesViewModel(object : NotificationPreferencesService {
            override suspend fun load() = NotificationPrefsResponse(false, true, notifyWeeklyRecap = false)
            override suspend fun save(request: NotificationPrefsUpdateRequest): NotificationPrefsResponse { saved += request; return NotificationPrefsResponse(request.notifyReactions!!, request.notifyFollows!!, request.notifyComments, request.notifyWeeklyRecap!!, request.notifyCompanions) }
        }, preferences, dispatcher))
        runCurrent(); assertTrue(vm.state.value.loaded); assertTrue(vm.state.value.comments); assertTrue(vm.state.value.companions)
        assertTrue(saved.isEmpty())
        vm.setPreferences(reactions = true); advanceTimeBy(200); vm.setPreferences(comments = false)
        advanceTimeBy(399); runCurrent(); assertTrue(saved.isEmpty())
        advanceTimeBy(1); runCurrent(); assertEquals(1, saved.size); assertEquals(false, saved.single().notifyComments)
    }
    @Test fun notificationMasterRestoresCategoriesAndKeepsAdminSeparate() = runTest(dispatcher) {
        val vm = own(NotificationPreferencesViewModel(object : NotificationPreferencesService {
            override suspend fun load() = NotificationPrefsResponse(true, false, false, false, false, false, true)
            override suspend fun save(request: NotificationPrefsUpdateRequest) = NotificationPrefsResponse(
                request.notifyReactions!!, request.notifyFollows!!, request.notifyComments, request.notifyWeeklyRecap!!,
                request.notifyCompanions, false, true)
        }, preferences, dispatcher))
        runCurrent(); assertTrue(vm.state.value.master); assertFalse(vm.state.value.isAdmin)
        vm.setMaster(false); assertFalse(vm.state.value.master); assertTrue(vm.state.value.newAccounts)
        vm.setMaster(true); assertTrue(vm.state.value.reactions); assertFalse(vm.state.value.follows)
        assertFalse(vm.state.value.comments); assertFalse(vm.state.value.weeklyRecap); assertFalse(vm.state.value.companions)
        advanceUntilIdle(); assertTrue(vm.state.value.master)
    }


}
