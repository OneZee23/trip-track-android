// iOS: LocalDataWipe.swift (the batch deletes)
package com.onezee.triptrack.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * «Delete account — irreversibly, everywhere». Every table that holds the person's data is named,
 * including the relationless ones a cascade from `TripEntity` never reaches. `UserSettingsEntity`
 * is deliberately absent: theme, language and units are the app's preferences, and resetting them
 * would drop the person into a first-launch app that looks broken rather than emptied.
 */
@Dao
interface WipeDao {
    @Transaction
    suspend fun wipeUserData() {
        deleteTripPhotos()
        deleteTrackPoints()
        deleteCheckpoints()
        deleteTrips()
        deleteVehiclePhotos()
        deleteVehicles()
        deleteJourneys()
        deletePlacePasses()
        deletePlaces()
        deleteVisitedGeohashes()
        deleteRoads()
        deleteRevealedCells()
        deleteDiscoveries()
        deleteGeocodeCache()
    }

    @Query("DELETE FROM TripPhotoEntity") suspend fun deleteTripPhotos()
    @Query("DELETE FROM TrackPointEntity") suspend fun deleteTrackPoints()
    @Query("DELETE FROM TripCheckpointEntity") suspend fun deleteCheckpoints()
    @Query("DELETE FROM TripEntity") suspend fun deleteTrips()
    @Query("DELETE FROM VehiclePhotoEntity") suspend fun deleteVehiclePhotos()
    @Query("DELETE FROM VehicleEntity") suspend fun deleteVehicles()
    @Query("DELETE FROM JourneyEntity") suspend fun deleteJourneys()
    @Query("DELETE FROM PlacePassEntity") suspend fun deletePlacePasses()
    @Query("DELETE FROM PlaceEntity") suspend fun deletePlaces()
    @Query("DELETE FROM VisitedGeohashEntity") suspend fun deleteVisitedGeohashes()
    @Query("DELETE FROM RoadEntity") suspend fun deleteRoads()
    @Query("DELETE FROM RevealedCellEntity") suspend fun deleteRevealedCells()
    @Query("DELETE FROM DiscoveryEntity") suspend fun deleteDiscoveries()
    @Query("DELETE FROM GeocodeCacheEntity") suspend fun deleteGeocodeCache()
}
