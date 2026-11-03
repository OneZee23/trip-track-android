// iOS: ProfileHeroCard (isForeign), ProfileGarageHeader (foreign, onCoverEdge), PublicProfileView (recentTrips, layoutToggle)
// and the 3 November 2026 parity audit of someone else's profile (SO-01 to SO-06).
package com.onezee.triptrack.social

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.social.ui.countryLine
import com.onezee.triptrack.model.ProfilePhotoDTO
import com.onezee.triptrack.model.SocialActiveVehicle
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ForeignProfileLayoutTests : SocialScreenTest() {
    private val ru get() = AppStrings(RuntimeEnvironment.getApplication(), AppLanguage.ru)

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun Dp.near(expected: Float, tolerance: Float = 0.8f) = assertEquals(expected, value, tolerance)

    /** The size the text node was laid out at, in the font-scale-free unit the screen asks for. */
    private fun fontSizeOf(node: SemanticsNodeInteraction): Float {
        val results = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.first().layoutInput.style.fontSize.value
    }

    private fun withCover() {
        feature.remotePhotoBytes = { ByteArray(0) }
        api.profileValue = api.profileValue.copy(photos = listOf(ProfilePhotoDTO(UUID.randomUUID(), originalUrl = "https://fixture.invalid/cover.png")))
    }

    private fun withCar() {
        val car = SocialFixtures.car.copy(name = "Jolion", make = "Haval", model = "Jolion", year = 2022, avatarEmoji = "pixel_car_red")
        api.cars = listOf(car)
        api.profileValue = api.profileValue.copy(activeVehicle = SocialActiveVehicle(car.id, car.name, car.level, car.odometerKm, car.avatarEmoji))
    }

    private fun profile() = showDestination(TripsDestination.PublicProfile(SocialFixtures.account, SocialFixtures.author))

    // ---- SO-01, SO-02: a cover and a car

    @Test fun theCarStandsPulledUpOverTheCoversEdge() {
        withCover(); withCar()
        profile()
        val photo = bounds("profile_cover_photo")
        val car = bounds("garage_header_vehicle")
        // iOS: the garage header is pulled up 82 over the photo.
        car.top.near((photo.bottom - Dp(82f)).value)
        // The stage is 155 tall (250 wide) and the name sits on one line under it: the block is about 181.
        car.height.near(181f, 5f)
        // What follows moves up with it: the identity row is the block less 82, and the 14 of the column, below the photo.
        val identity = bounds("profile_display_name")
        (identity.top - photo.bottom).near(181f - 82f + 14f, 5f)
    }

    @Test fun theNumbersAreNineteenHeavyUnderACoverPhoto() {
        withCover()
        profile()
        assertEquals(19f, fontSizeOf(compose.onNodeWithText("45", useUnmergedTree = true)))
    }

    @Test fun theNumbersOfAPlainCardAreTwentyTwoBold() {
        profile()
        // The fixture pays for a background; the card without photos is the one with 22.
        assertEquals(22f, fontSizeOf(compose.onNodeWithText("45", useUnmergedTree = true)))
    }

    // ---- SO-03, SO-04: the journal

    @Test fun theJournalRowsAreTenAndTwelveApartAsInIos() {
        profile()
        // One scroll, then every measure: the positions move with the list.
        compose.onNodeWithTag("social_trip_card").performScrollTo()
        val list = bounds("profile_layout_list")
        val month = bounds("profile_month_header")
        // «Поездки» is 10 above its content, and the chapter header carries its own 12.
        (month.top - (list.bottom + Dp(2f))).near(22f)
        val summary = compose.onNodeWithText("10 км · 10 поездок · 0 регионов", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val card = bounds("social_trip_card")
        // The header's 4 below the summary and the 12 of the list.
        (card.top - summary.bottom).near(16f)
    }

    @Test fun theSectionsRowStandsSixteenBelowTheHero() {
        profile()
        val hero = bounds("profile_hero")
        // The tag is on the entry's text, centred in a 44-tall target inside the card's 6 of padding.
        val entry = bounds("profile_stats_entry")
        (entry.top - (Dp(44f) - entry.height) / 2 - Dp(6f) - hero.bottom).near(16f)
    }

    @Test fun theLayoutToggleIsTwoSmallFramesTwoApart() {
        profile()
        val grid = compose.onNodeWithTag("profile_layout_grid").performScrollTo().getUnclippedBoundsInRoot()
        val list = bounds("profile_layout_list")
        grid.width.near(26f); grid.height.near(22f)
        list.width.near(26f); list.height.near(22f)
        // Glyph centres 28 apart, the list one 25 from the edge (16 of margin, 2 of padding, half a 26 frame less half the glyph).
        ((list.left + list.width / 2) - (grid.left + grid.width / 2)).near(28f)
        (compose.onRoot().getUnclippedBoundsInRoot().right - list.right).near(18f)
    }

    // ---- SO-05, SO-06: the free card and the country

    @Test fun theFreeHeroCardIsTenBelowTheBar() {
        api.profileValue = api.profileValue.copy(profileBackground = null, hasPlus = false)
        profile()
        // iOS: the card's 4 and the hero section's 6; from the circle of the bar that is 22 (23 measured).
        (bounds("profile_hero_card").top - bounds("profile_more").bottom).near(22f, 1.2f)
    }

    @Test fun onlyOnesOwnPreviewPrintsTheCountry() {
        ownCountryValue = "RU"
        api.profileValue = api.profileValue.copy(profileBackground = null, hasPlus = false)
        profile()
        // Someone else's profile: the country is this device's setting, the profile carries none.
        compose.onNodeWithTag("profile_country").assertDoesNotExist()
    }

    @Test fun theOwnPreviewPrintsTheFlagAndTheName() {
        ownCountryValue = "RU"
        api.profileValue = api.profileValue.copy(id = owner, profileBackground = null, hasPlus = false)
        showDestination(TripsDestination.PublicProfile(owner, SocialFixtures.author))
        compose.onNodeWithTag("profile_country").assertTextEquals("🇷🇺 Россия")
    }

    @Test fun theCountryLineFollowsTheIosRules() {
        assertEquals("🇷🇺 Россия", countryLine("RU", ru))
        assertEquals("🏳️", countryLine("NEUTRAL", ru), "the neutral flag stands alone")
        assertEquals("🌍 ${ru.countryWorld()}", countryLine("WORLD", ru))
        assertNull(countryLine("", ru), "«Не указывать» prints nothing")
        assertNull(countryLine("nonsense", ru))
        assertTrue(countryLine("de", ru)!!.startsWith("🇩🇪 "))
    }
}
