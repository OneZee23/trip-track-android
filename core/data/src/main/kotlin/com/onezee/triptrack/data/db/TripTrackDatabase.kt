// iOS: PersistenceController.swift, TripTrack.xcdatamodeld (current version v24)
package com.onezee.triptrack.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverters
import com.onezee.triptrack.data.db.dao.DiscoveryDao
import com.onezee.triptrack.data.db.dao.JourneyDao
import com.onezee.triptrack.data.db.dao.MapDao
import com.onezee.triptrack.data.db.dao.PlaceDao
import com.onezee.triptrack.data.db.dao.RecordingDao
import com.onezee.triptrack.data.db.dao.SettingsDao
import com.onezee.triptrack.data.db.dao.TripDao
import com.onezee.triptrack.data.db.dao.VehicleDao
import com.onezee.triptrack.data.db.dao.WipeDao
import com.onezee.triptrack.data.db.entity.DiscoveryEntity
import com.onezee.triptrack.data.db.entity.GeocodeCacheEntity
import com.onezee.triptrack.data.db.entity.JourneyEntity
import com.onezee.triptrack.data.db.entity.PlaceEntity
import com.onezee.triptrack.data.db.entity.PlacePassEntity
import com.onezee.triptrack.data.db.entity.RevealedCellEntity
import com.onezee.triptrack.data.db.entity.RoadEntity
import com.onezee.triptrack.data.db.entity.TrackPointEntity
import com.onezee.triptrack.data.db.entity.TripCheckpointEntity
import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.data.db.entity.TripPhotoEntity
import com.onezee.triptrack.data.db.entity.UserSettingsEntity
import com.onezee.triptrack.data.db.entity.VehicleEntity
import com.onezee.triptrack.data.db.entity.VehiclePhotoEntity
import com.onezee.triptrack.data.db.entity.VisitedGeohashEntity

/**
 * The fifteen entities of Core Data model v24 as Room tables.
 *
 * Version 1 of the Android schema IS v24: Android starts where iOS is today, so the iOS history of
 * lightweight migrations has no counterpart here. Every later change is a new version with a
 * migration and a test against the exported schema in `core/data/schemas`.
 *
 * One database per process, like `PersistenceController.managedObjectModel`: two open instances on
 * one file would each keep their own invalidation tracker and miss the other's writes.
 */
@Database(
    entities = [
        TripEntity::class,
        TrackPointEntity::class,
        TripPhotoEntity::class,
        TripCheckpointEntity::class,
        VehicleEntity::class,
        VehiclePhotoEntity::class,
        UserSettingsEntity::class,
        VisitedGeohashEntity::class,
        GeocodeCacheEntity::class,
        RoadEntity::class,
        JourneyEntity::class,
        PlaceEntity::class,
        PlacePassEntity::class,
        RevealedCellEntity::class,
        DiscoveryEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TripTrackDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun settingsDao(): SettingsDao
    abstract fun journeyDao(): JourneyDao
    abstract fun placeDao(): PlaceDao
    abstract fun discoveryDao(): DiscoveryDao
    abstract fun mapDao(): MapDao
    abstract fun wipeDao(): WipeDao
    abstract fun recordingDao(): RecordingDao

    companion object {
        const val FILE_NAME = "TripTrack.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE TripEntity ADD COLUMN hiddenPartsJson TEXT")
                db.execSQL("ALTER TABLE TripEntity ADD COLUMN previewBreaksJSON TEXT")
            }
        }

        /**
         * The app's store. There is deliberately no destructive fallback: a store that fails to
         * migrate is a crash to fix, not a library to throw away (`PersistenceController.loadStore`
         * learned that from 107 trips that disappeared from one phone).
         */
        fun open(context: Context): TripTrackDatabase =
            Room.databaseBuilder(context.applicationContext, TripTrackDatabase::class.java, FILE_NAME)
                .addMigrations(MIGRATION_1_2)
                .build()

        /** A throwaway store, for tests and previews (`PersistenceController(inMemory:)`). */
        fun inMemory(context: Context): TripTrackDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, TripTrackDatabase::class.java).build()
    }
}
