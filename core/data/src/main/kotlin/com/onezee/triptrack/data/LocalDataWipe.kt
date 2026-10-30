// iOS: LocalDataWipe.swift
package com.onezee.triptrack.data

import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.files.TripPhotoFiles
import com.onezee.triptrack.data.files.VehiclePhotoFiles

/**
 * Erases everything this device holds ABOUT the person, for one row: «Delete account —
 * irreversibly, everywhere». A promise on a destructive button has to be literally true, and the
 * drives, their points and photos, the cars and the territory all live on this phone too.
 *
 * Every relationless table is named (car photos, journeys, places and passes, the opened world,
 * finds, the geocoder cache): a cascade from trips never reaches them. Photo files go with the rows.
 * The settings row is deliberately kept: theme, language and units are the app's preferences.
 *
 * The opened world's rebuild latch is reopened with its data, because it says «the build ran», not
 * «a build once started»: kept, it would leave trips returning by sync without fog forever.
 * State held elsewhere (the raw fix log, profile photos, home, journey suggestions, the secrets
 * catalog, the milestone history) is cleared by [afterWipe], supplied by the modules that own it.
 */
class LocalDataWipe(
    private val db: TripTrackDatabase,
    private val tripPhotos: TripPhotoFiles,
    private val vehiclePhotos: VehiclePhotoFiles,
    private val flags: DataFlags,
    private val afterWipe: List<suspend () -> Unit> = emptyList(),
) {
    suspend fun run() {
        db.wipeDao().wipeUserData()
        tripPhotos.wipe()
        vehiclePhotos.wipe()
        flags.set(RevealedTileStore.REBUILD_FLAG_KEY, false)
        for (step in afterWipe) step()
    }
}
