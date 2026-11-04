package com.onezee.triptrack.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.onezee.triptrack.data.db.TripTrackDatabase
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// iOS: CoreDataV25MigrationTests.swift.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TripHiddenPartsMigrationTests {
    @Test
    fun addsOptionalHiddenPartsJsonWithoutChangingExistingTrips() {
        val context = RuntimeEnvironment.getApplication() as Context
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE TripEntity (id TEXT NOT NULL PRIMARY KEY, startDate INTEGER NOT NULL)")
                        db.execSQL("INSERT INTO TripEntity (id, startDate) VALUES ('${UUID.randomUUID()}', 42)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            val db = helper.writableDatabase
            TripTrackDatabase.MIGRATION_1_2.migrate(db)
            val columns = db.query("PRAGMA table_info(TripEntity)").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
            assertTrue("hiddenPartsJson" in columns)
            assertTrue("previewBreaksJSON" in columns)
            assertEquals(1, db.query("SELECT COUNT(*) FROM TripEntity WHERE startDate = 42 AND hiddenPartsJson IS NULL").use { it.moveToFirst(); it.getInt(0) })
        } finally {
            helper.close()
        }
    }
}
