// iOS: GarageProfileShotTests. The frames are compared with the iPhone's by number in GarageLookTests.
package com.onezee.triptrack.feature.garage

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.model.*
import com.onezee.triptrack.ui.LocalAppVersion
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w411dp-h914dp-420dpi")
class GarageScreenshotTests(private val dark: Boolean) {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    private val suffix get() = if (dark) "dark" else "light"

    private fun content(body: @Composable () -> Unit) {
        compose.setContent {
            TripTrackTheme(isDark = dark, language = AppLanguage.ru, reduceMotion = true) {
                CompositionLocalProvider(LocalInspectionMode provides true, LocalAppVersion provides "0.9.2-dev",
                    com.onezee.triptrack.ui.LocalClock provides java.time.Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC)) {
                    Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) { body() }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun shot(name: String, scrollTo: String? = null, scroller: String = "vehicle_passport_scroll") {
        if (scrollTo != null) { compose.onNodeWithTag(scroller).performScrollToNode(hasTestTag(scrollTo)); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("src/test/screenshots/garage_${name}_$suffix.png")
    }

    private fun passport(state: VehiclePassportState = GarageFixtures.passportState, stereo: StereoStatus = StereoStatus.NotLinked, autoRecordOn: Boolean = false) = content {
        VehiclePassportScreen(state, DistanceUnit.km, {}, {}, {}, {}, stereo = stereo, autoRecordOn = autoRecordOn)
    }

    @Test fun passportTop() { passport(); shot("passport_top") }
    @Test fun passportPlaces() { passport(); shot("passport_places", scrollTo = "vehicle_where_was") }
    @Test fun passportTrips() { passport(); shot("passport_trips", scrollTo = "vehicle_trips") }
    @Test fun passportRecords() { passport(); shot("passport_records", scrollTo = "vehicle_energy") }

    @Test fun passportSoldWithPlate() {
        val sold = GarageFixtures.car.copy(soldAt = Instant.parse("2026-03-01T12:00:00Z"), plate = "К777ХВ 123")
        passport(GarageFixtures.passportState.copy(vehicle = sold)); shot("passport_sold")
    }
    @Test fun passportStereoLinked() {
        passport(stereo = StereoStatus.Linked("Polo Radio", connected = true), autoRecordOn = true); shot("passport_stereo", scrollTo = "vehicle_recording")
    }
    @Test fun passportBluetoothOff() {
        passport(stereo = StereoStatus.BluetoothOff, autoRecordOn = true); shot("passport_bluetooth_off", scrollTo = "vehicle_recording")
    }
    @Test fun passportHybrid() {
        val hybrid = GarageFixtures.car.copy(powertrain = Powertrain.pluginHybrid, electricConsumption = 17.5, electricityPrice = 6.0, electricRangeKm = 50.0)
        passport(GarageFixtures.passportState.copy(vehicle = hybrid)); shot("passport_hybrid", scrollTo = "vehicle_energy")
    }

    private fun form(vehicle: Vehicle) = content {
        VehicleEditContent(VehicleEditState(vehicle = vehicle, odometerText = "96400", initialOdometerText = "96400", energy = VehicleEnergyDraft.from(vehicle, DistanceUnit.km)),
            DistanceUnit.km, isNew = false, {}, {}, {}, {}, {}, { _, _ -> }, {}, { _, _, _, _, _ -> }, { _, _, _, _ -> }, {}, {}, onSold = {})
    }
    @Test fun formTop() { form(GarageFixtures.car); shot("form_top") }
    @Test fun formEngine() { form(GarageFixtures.car.copy(powertrain = Powertrain.pluginHybrid)); shot("form_engine", scrollTo = "vehicle_powertrain_row", scroller = "vehicle_edit_scroll") }
    @Test fun newVehicleFormUsesAddFlowFields() {
        content {
            VehicleEditContent(VehicleEditState(vehicle = GarageFixtures.car, energy = VehicleEnergyDraft.from(GarageFixtures.car, DistanceUnit.km)),
                DistanceUnit.km, isNew = true, {}, {}, {}, {}, {}, { _, _ -> }, {}, { _, _, _, _, _ -> }, { _, _, _, _ -> }, {}, {}, onSold = null)
        }
        listOf("vehicle_name", "vehicle_type_tile_car", "vehicle_catalog", "vehicle_year", "vehicle_plate", "vehicle_avatar_card")
            .forEach { compose.onNodeWithTag(it).assertExists() }
        listOf("vehicle_about", "vehicle_odometer", "vehicle_dashboard")
            .forEach { compose.onNodeWithTag(it).assertDoesNotExist() }
    }
    @Test fun powertrainOptionsShowTheirHints() {
        val strings = com.onezee.triptrack.designsystem.strings.AppStrings(RuntimeEnvironment.getApplication(), AppLanguage.ru)
        content {
            com.onezee.triptrack.designsystem.components.SettingsOptionPicker(
                "Двигатель", Powertrain.entries, Powertrain.fuel, "Подсказка",
                label = { it.name }, onSelect = {}, onDismissRequest = {},
                subtitle = { when (it) {
                    Powertrain.fuel -> strings.powertrainFuelHint()
                    Powertrain.electric -> strings.powertrainElectricHint()
                    Powertrain.pluginHybrid -> strings.powertrainHybridHint()
                } },
            )
        }
        compose.onNodeWithText(strings.powertrainFuelHint(), substring = true).assertExists()
        compose.onNodeWithText(strings.powertrainElectricHint(), substring = true).assertExists()
        compose.onNodeWithText(strings.powertrainHybridHint(), substring = true).assertExists()
    }
    @Test fun formOfAMotorcycleHasNoTypeRow() { form(GarageFixtures.car.copy(type = VehicleType.moto, avatarStyle = "motorcycle", avatarEmoji = "pixel_motorcycle_red")); shot("form_moto") }

    @Test fun levelPage() { content { VehicleLevelScreen(6, 1655.0, DistanceUnit.km, {}) }; shot("level") }
    @Test fun tripsPage() { content { VehicleTripsScreen("Полторашка", GarageFixtures.recent, true, DistanceUnit.km, {}, {}) }; shot("trips") }
    @Test fun photosPage() { content { VehiclePhotosPage("Полторашка", GarageFixtures.vehiclePhotos, true, false, false, {}, {}, {}) }; shot("photos") }
    @Test fun photosPageEmpty() { content { VehiclePhotosPage("Полторашка", emptyList(), true, false, false, {}, {}, {}) }; shot("photos_empty") }
    @Test fun photoGrid() {
        content { GaragePhotoGridCover("Фото из поездок", GarageFixtures.photos.map { GaragePhoto.Local(it.id.toString(), it.filename) } + GarageFixtures.photos.map { GaragePhoto.Local(it.id.toString() + "b", it.filename) }, {}, {}) }
        compose.waitForIdle(); captureScreenRoboImage("src/test/screenshots/garage_photo_grid_$suffix.png")
    }
    @Test fun drive2Sheet() {
        content { Drive2Sheet(GarageFixtures.car.drive2URL.orEmpty(), syncs = false, onSave = { true }, onDismiss = {}) }
        compose.waitForIdle(); captureScreenRoboImage("src/test/screenshots/garage_drive2_$suffix.png")
    }
    @Test fun drive2SheetEmpty() {
        content { Drive2Sheet("", syncs = true, onSave = { true }, onDismiss = {}) }
        compose.waitForIdle(); captureScreenRoboImage("src/test/screenshots/garage_drive2_empty_$suffix.png")
    }

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
