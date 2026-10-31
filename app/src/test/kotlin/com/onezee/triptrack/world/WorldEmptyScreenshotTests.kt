// iOS: AtlasStatesShotTests (22 loading, 10 empty) and PlacesStatesShotTests (13/14 no places, 15 no
// trips, 23 suggestions counting).
package com.onezee.triptrack.world

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.onezee.triptrack.data.RoomTripRepository
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.model.Trip
import kotlinx.coroutines.CompletableDeferred
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

private fun parameters() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))

/** Trips without a single place: the empty tab with suggestions, «Мест пока нет» and «Отметить в поездке». */
@RunWith(ParameterizedRobolectricTestRunner::class)
class PlacesNewcomerScreenshotTests(private val dark: Boolean) : WorldScreenTest() {
    override val seedsPlaces = false
    private val theme = if (dark) "dark" else "light"

    @Test fun placesWithoutPlaces() {
        showPlaces(dark)
        compose.onNodeWithTag("places_no_places_note").assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/world_places_no_places_$theme.png")
    }

    /** Before the suggestions are counted the section is a skeleton, never an empty gap (state 23). */
    @Test fun suggestionsWhileCounted() {
        showPlaces(dark, settles = false)
        compose.onNodeWithTag("places_suggestions_skeleton").assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/world_places_skeleton_$theme.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes() = parameters()
    }
}

/** Not one trip: the Atlas' empty map and the Places tab's one sentence (state 15). */
@RunWith(ParameterizedRobolectricTestRunner::class)
class WorldWithoutTripsScreenshotTests(private val dark: Boolean) : WorldScreenTest() {
    override val seedsPlaces = false
    override val seedsTrips = false
    private val theme = if (dark) "dark" else "light"

    @Test fun atlasEmpty() {
        showAtlas(dark)
        compose.onNodeWithTag("atlas_empty").assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/world_atlas_empty_$theme.png")
    }

    @Test fun placesWithoutTrips() {
        showPlaces(dark)
        compose.onNodeWithTag("places_none_yet").assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/world_places_no_trips_$theme.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes() = parameters()
    }
}

/** The Atlas while it is counted: the skeleton where the three numbers will be (state 22). */
@RunWith(ParameterizedRobolectricTestRunner::class)
class AtlasLoadingScreenshotTests(private val dark: Boolean) : WorldScreenTest() {
    private val theme = if (dark) "dark" else "light"
    private val gate = CompletableDeferred<Unit>()

    override fun worldRepository(repository: RoomTripRepository): TripRepository = object : TripRepository by repository {
        override suspend fun fetchTripsForMap(): List<Trip> {
            gate.await()
            return repository.fetchTripsForMap()
        }
    }

    @Test fun atlasLoading() {
        showAtlas(dark)
        compose.onNodeWithTag("atlas_stats_skeleton").assertExists()
        compose.onRoot().captureRoboImage("src/test/screenshots/world_atlas_loading_$theme.png")
        gate.complete(Unit)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes() = parameters()
    }
}
