// iOS: StatsPeriodFormat.monthYearPrepositional, VehicleCatalogCaption, CarCatalog.Variant.specLine, TripRowText, vehicleRecords,
// VehicleAvatar.swatchNeedsBorder, VehicleDetailView.heroCard / PublicVehicleView.header, VehicleEditFormView.avatarCard.
// The numbers are the iPhone's (GA-01…GA-34 of the 3 November audit); the frames themselves are GarageScreenshotTests.
package com.onezee.triptrack.feature.garage

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.model.*
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w411dp-h914dp-420dpi")
class GarageLookTests {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    private fun strings(language: AppLanguage = AppLanguage.ru) = AppStrings(RuntimeEnvironment.getApplication(), language, ZoneId.of("UTC"))
    private fun near(expected: Float, actual: Float, what: String, tolerance: Float = 0.75f) =
        assertTrue(abs(expected - actual) <= tolerance, "$what: expected $expected, was $actual")

    // ---- Words: what the lines of the page say ----

    @Test fun aSoldMonthStandsInThePrepositionalCaseOfRussianAndUkrainian() {
        fun ru(month: Int) = monthYearPrepositional(Instant.parse("2026-%02d-10T12:00:00Z".format(month)), strings())
        assertEquals("марте 2026", ru(3))
        // The months where «drop the ending and add -е» goes wrong.
        assertEquals("мае 2026", ru(5))
        assertEquals("августе 2026", ru(8))
        assertEquals("январе 2026", ru(1))
        assertEquals("березні 2026", monthYearPrepositional(Instant.parse("2026-03-10T12:00:00Z"), strings(AppLanguage.uk)))
        // Every other language reads the genitive form after the preposition.
        assertEquals("March 2026", monthYearPrepositional(Instant.parse("2026-03-10T12:00:00Z"), strings(AppLanguage.en)))
    }

    @Test fun theModelLineIsMakeModelAndBodyThenOnlyTheYearAfterADot() {
        val body = { _: String -> "Седан" }
        val ru = java.util.Locale.forLanguageTag("ru")
        assertEquals("Volkswagen Polo седан · 2018", catalogModelLine("Volkswagen", "Polo", 2018, "car", ru, body))
        // A model that already names its body is not told it twice.
        assertEquals("Lada Granta Седан · 2020", catalogModelLine("Lada", "Granta Седан", 2020, "car", ru, body))
        assertEquals("Polo седан", catalogModelLine("", "Polo", 0, "car", ru, body))
        assertEquals("Volkswagen · 2018", catalogModelLine("Volkswagen", "", 2018, "", ru, body))
        // Nothing filled in: no line, and not a lone dot.
        assertEquals("", catalogModelLine("", "", 0, "", ru, body))
        assertEquals("2018", catalogModelLine("", "", 2018, "", ru, body))
    }

    @Test fun theSpecLineIsEngineThenGearboxThenAcceleration() {
        val polo = GarageFixtures.variant
        assertEquals("1.6 110 л.с. · АКПП 6 · 0–100 за 11.7 с", polo.specLine(strings()))
        // The top speed is not part of it; a part that is missing leaves no hole.
        assertEquals("110 л.с.", polo.copy(displacementCc = null, transmission = null, gears = null, zeroToHundredS = null).specLine(strings()))
        assertEquals("79.6 л.с.", polo.copy(displacementCc = 0.0, powerHp = 79.6, transmission = null, zeroToHundredS = null).specLine(strings()))
        assertEquals("0–100 за 11.7 с", polo.copy(displacementCc = null, powerHp = null, transmission = null).specLine(strings()))
        // A gearbox the catalogue spells in a way we do not know is not printed.
        assertEquals("1.6 110 л.с. · 0–100 за 11.7 с", polo.copy(transmission = "tiptronic").specLine(strings()))
        assertNull(polo.copy(displacementCc = null, powerHp = null, transmission = null, zeroToHundredS = null).specLine(strings()))
    }

    @Test fun aTripRowTitleFallsFromTheNameToTheRegionToTheGenericWord() {
        val named = GarageFixtures.longest.copy(region = "Краснодарский край")
        val region = com.onezee.triptrack.feature.me.ProfileRegionDisplay.localized("Краснодарский край", "ru")
        assertEquals("Краснодар — Горячий Ключ", GarageTripText.title(named, strings()))
        // The region as the profile shows it (the house abbreviation included), never the raw code.
        assertTrue(!region.isNullOrEmpty() && region != strings().tripTitle())
        assertEquals(region, GarageTripText.title(named.copy(title = null), strings()))
        assertEquals(region, GarageTripText.title(named.copy(title = ""), strings()))
        assertEquals(strings().tripTitle(), GarageTripText.title(named.copy(title = null, region = null), strings()))
    }

    @Test fun aTripRowSaysTheClockAndHowLongAndTheYearOnlyOnAnotherYear() {
        val now = Instant.parse("2026-11-03T12:00:00Z")
        val thisYear = GarageTripText.whenText(GarageFixtures.longest, strings(), now)
        // Two trips of one day are told apart by the clock; the 39 minutes follow it after a dot.
        assertTrue(thisYear.startsWith("2 нояб"), thisYear)
        assertTrue("15:01" in thisYear && thisYear.endsWith(" · 39 мин"), thisYear)
        assertFalse("2026" in thisYear, thisYear)
        val lastYear = GarageTripText.whenText(GarageFixtures.longest.copy(startDate = Instant.parse("2025-11-02T15:01:00Z"), endDate = Instant.parse("2025-11-02T15:40:00Z")), strings(), now)
        assertTrue("2025" in lastYear, lastYear)
        // A trip with no length says only when.
        assertFalse(" · " in GarageTripText.whenText(GarageFixtures.longest.copy(endDate = GarageFixtures.longest.startDate), strings(), now))
    }

    @Test fun theRecordsAreTheLongestThenTheHighestThenTheBestDayThenTheLongestDay() {
        val unit = DistanceUnit.km
        val records = vehicleRecords(GarageFixtures.passport, unit, strings())
        assertEquals(listOf("vehicle_record_longest", "vehicle_record_highest", "vehicle_record_best_day", "vehicle_record_longest_day"), records.map { it.tag })
        assertEquals(strings().recordLongest(), records[0].label)
        assertEquals("К морю", records[0].subtitle)
        assertEquals("214 км", records[0].value)
        // Only the longest trip is a row that opens its trip.
        assertEquals(GarageFixtures.passport.longestTrip, records[0].trip)
        assertTrue(records.drop(1).all { it.trip == null })
        assertEquals("640 м", records[1].value)
        // The best day is named, as iOS names it, by the date of the longest trip.
        assertTrue("28 октября" in records[2].subtitle && "2026" in records[2].subtitle, records[2].subtitle)
        assertEquals("214 км", records[2].value)
    }

    @Test fun aRecordAppearsOnlyWhenItHasSomethingToSay() {
        val unit = DistanceUnit.km
        val passport = GarageFixtures.passport
        assertTrue(vehicleRecords(VehiclePassport(), unit, strings()).isEmpty(), "no trips, no records")
        val flat = vehicleRecords(passport.copy(highestTrip = passport.highestTrip?.copy(elevation = 0.0), maxDayDistanceMetresThisYear = 0.0, maxDayDurationSeconds = 0.0), unit, strings())
        assertEquals(listOf("vehicle_record_longest"), flat.map { it.tag })
        assertEquals(listOf("vehicle_record_longest", "vehicle_record_best_day", "vehicle_record_longest_day"), vehicleRecords(passport.copy(highestTrip = null), unit, strings()).map { it.tag })
    }

    @Test fun tripsSortByDateThenLengthThenClimbEachLargestFirst() {
        val trips = GarageFixtures.recent
        assertEquals(listOf("Краснодар — Горячий Ключ", "Поездка по городу", "К морю"), trips.sortedFor(VehicleTripSort.date).map { it.title })
        assertEquals(listOf("К морю", "Краснодар — Горячий Ключ", "Поездка по городу"), trips.sortedFor(VehicleTripSort.distance).map { it.title })
        assertEquals("К морю", trips.sortedFor(VehicleTripSort.elevation).first().title)
    }

    @Test fun onlyAPaleSwatchGetsAHairlineAndTheSwatchIsTheBaseOfTheRamp() {
        // Luminance above 0.72 vanishes into a light card: white and yellow; silver at 0.66 does not.
        assertEquals(listOf("white", "yellow"), (VehicleAvatar.colors + "yellow").filter(::swatchNeedsBorder))
        assertEquals(0xFFD4D9E3.toInt(), avatarSwatch("white").toArgb())
        // An unknown colour is the orange of the stock sprite.
        assertEquals(avatarSwatch("orange"), avatarSwatch("no-such-colour"))
    }

    @Test fun theStereoCardHasThreeFacesAndAndroidAutoNeedsNoBluetooth() {
        val stereo = SavedBluetoothDevice("AA:BB", "Polo Radio")
        val auto = SavedBluetoothDevice(com.onezee.triptrack.location.auto.AutoTripController.ANDROID_AUTO, "Android Auto")
        assertEquals(StereoStatus.NotLinked, stereoStatus(true, null, emptyList()))
        // The radio is checked before the car, even for a vehicle with no stereo (iOS isBluetoothOff).
        assertEquals(StereoStatus.BluetoothOff, stereoStatus(false, null, emptyList()))
        assertEquals(StereoStatus.BluetoothOff, stereoStatus(false, stereo, listOf(stereo)))
        assertEquals(StereoStatus.Linked("Polo Radio", connected = false), stereoStatus(true, stereo, emptyList()))
        // Connected when the phone plays through its output now; the id is matched without regard to case.
        assertEquals(StereoStatus.Linked("Polo Radio", connected = true), stereoStatus(true, stereo, listOf(SavedBluetoothDevice("aa:bb", "Polo Radio"))))
        assertEquals(StereoStatus.Linked("Android Auto", connected = false), stereoStatus(false, auto, emptyList()))
    }

    // ---- The hero card: where the parts stand ----

    private fun hero(foreign: Boolean) {
        compose.setContent {
            TripTrackTheme(isDark = false, language = AppLanguage.ru, reduceMotion = true) {
                Box(Modifier.width(375.dp).testTag("hero_host")) {
                    VehicleHeroCard("Полторашка", "Volkswagen", "Polo", 2018, "car", GarageFixtures.variant, emptyList(), null, null, 6,
                        VehicleHeroStats(56, 4, 37), "март 2025", foreign) { Box(Modifier.size(260.dp, 150.dp).testTag("hero_stage")) }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun theOwnHeroCardIs18AcrossAndSetsTheStageAndTheNameApart() {
        hero(foreign = false)
        val card = compose.onNodeWithTag("hero_host").getBoundsInRoot()
        val stage = compose.onNodeWithTag("hero_stage").getBoundsInRoot()
        val name = compose.onNodeWithTag("vehicle_hero_name").getBoundsInRoot()
        val numbers = compose.onNodeWithTag("vehicle_passport_numbers").getBoundsInRoot()
        val since = compose.onNodeWithText(strings().inGarageSince("март 2025")).getBoundsInRoot()
        near(26f, stage.top.value - card.top.value, "22 above the card's content and 4 more above the stage")
        near(16f, name.top.value - stage.bottom.value, "the name 16 under the stage")
        near(18f, numbers.left.value - card.left.value, "18 across")
        near(16f, card.bottom.value - since.bottom.value, "16 below the last line")
        near(card.width.value / 2, (stage.left.value + stage.right.value) / 2 - card.left.value, "the stage is centred")
    }

    @Test fun someoneElsesHeroCardIs14AcrossAnd18AboveAndBelow() {
        hero(foreign = true)
        val card = compose.onNodeWithTag("hero_host").getBoundsInRoot()
        val stage = compose.onNodeWithTag("hero_stage").getBoundsInRoot()
        val name = compose.onNodeWithTag("vehicle_hero_name").getBoundsInRoot()
        val numbers = compose.onNodeWithTag("vehicle_passport_numbers").getBoundsInRoot()
        val since = compose.onNodeWithText(strings().inGarageSince("март 2025")).getBoundsInRoot()
        near(18f, stage.top.value - card.top.value, "18 above the stage")
        near(12f, name.top.value - stage.bottom.value, "the name 12 under the stage")
        near(14f, numbers.left.value - card.left.value, "14 across")
        near(18f, card.bottom.value - since.bottom.value, "18 below the last line")
    }

    @Test fun theThreeNumbersShareTheWidthEqually() {
        hero(foreign = false)
        val tags = listOf("vehicle_trips_count", "vehicle_regions_count", "vehicle_days_count")
        val centres = tags.map { compose.onNodeWithTag(it).getBoundsInRoot().let { b -> (b.left.value + b.right.value) / 2 } }
        val row = compose.onNodeWithTag("vehicle_passport_numbers").getBoundsInRoot()
        val third = row.width.value / 3
        centres.forEachIndexed { index, centre -> near(row.left.value + third * (index + 0.5f), centre, "number ${index + 1} stands in the middle of its third") }
    }

    // ---- The avatar card of the vehicle form ----

    private fun avatarCard(vehicle: Vehicle, onAvatar: (String, String) -> Unit = { _, _ -> }) {
        compose.setContent { TripTrackTheme(isDark = false, language = AppLanguage.ru, reduceMotion = true) { Box(Modifier.width(375.dp)) { VehicleAvatarCard(vehicle, onAvatar) } } }
        compose.waitForIdle()
    }

    private fun styleTiles() = compose.onAllNodes(SemanticsMatcher("a silhouette tile") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("vehicle_avatar_style_") == true })

    @Test fun aCarOffersSevenSilhouettesAndTheChosenOneIsMarked() {
        avatarCard(GarageFixtures.car)
        styleTiles().assertCountEquals(7)
        compose.onNodeWithTag("vehicle_avatar_style_car").assertIsSelected()
        compose.onNodeWithTag("vehicle_avatar_style_pickup").assertIsNotSelected()
        compose.onNodeWithTag("vehicle_avatar_color_blue").assertIsSelected()
        compose.onNodeWithTag("vehicle_avatar_color_red").assertIsNotSelected()
    }

    @Test fun aVehicleWithOneSilhouetteHasNoTypeRow() {
        // A row holding one tile reads as a choice taken away.
        avatarCard(GarageFixtures.car.copy(type = VehicleType.moto, avatarStyle = "motorcycle", avatarEmoji = "pixel_motorcycle_blue"))
        styleTiles().assertCountEquals(0)
        compose.onNodeWithTag("vehicle_avatar_color_blue").assertIsSelected()
    }

    @Test fun pickingOneAxisKeepsTheOther() {
        val picks = mutableListOf<Pair<String, String>>()
        avatarCard(GarageFixtures.car) { color, style -> picks += color to style }
        compose.onNodeWithTag("vehicle_avatar_color_red").performClick()
        compose.onNodeWithTag("vehicle_avatar_style_pickup").performClick()
        assertEquals(listOf("red" to "car", "blue" to "pickup"), picks)
    }

    @Test fun theTilesAre68HighAndTheSwatchesAre44WideAndTenApart() {
        avatarCard(GarageFixtures.car)
        near(68f, compose.onNodeWithTag("vehicle_avatar_style_car").getBoundsInRoot().height.value, "a silhouette tile")
        val white = compose.onNodeWithTag("vehicle_avatar_color_white").getBoundsInRoot()
        val black = compose.onNodeWithTag("vehicle_avatar_color_black").getBoundsInRoot()
        near(44f, white.width.value, "a swatch's target")
        near(44f, white.height.value, "a swatch's target")
        near(54f, black.left.value - white.left.value, "44 of target and 10 between")
        val hero = compose.onNodeWithTag("vehicle_avatar_hero").getBoundsInRoot()
        near(208f, hero.width.value, "the sprite plate")
        near(130f, hero.height.value, "the sprite plate")
    }
}
