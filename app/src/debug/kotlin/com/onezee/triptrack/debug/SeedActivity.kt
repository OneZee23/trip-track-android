package com.onezee.triptrack.debug

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.onezee.triptrack.MainActivity
import com.onezee.triptrack.TripTrackApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Debug builds only, the Android side of the iOS `-seed-map-demo` launch arguments:
 * `adb shell am start -n com.onezee.triptrack.dev/com.onezee.triptrack.debug.SeedActivity`
 * seeds the trips, a journey, checkpoints with a segment, photos and drafts ([TripsDemoSeed]), the
 * places and the opened world ([PlacesDemoSeed]), then opens the app. Idempotent, like the iOS seeds: a second launch adds nothing.
 */
class SeedActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as TripTrackApplication).appGraph
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                graph.awaitSettings()
                TripsDemoSeed(graph.database, graph.trips, graph.tripsFeature.journeys).run()
                // The Atlas and Places on top of it (iOS -seed-places-rich -seed-places-many).
                PlacesDemoSeed(graph.database, graph.world.places, graph.world.history, graph.revealedLayer).run()
            }
            graph.tripsFeature.drafts.refresh()
            startActivity(Intent(this@SeedActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
    }
}
