package com.lucasanso.naorola.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.lucasanso.naorola.data.db.NaoRolaDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Users updating from 0.1.0 (database v1) must keep their history. */
@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), NaoRolaDatabase::class.java)

    @Test fun v1HistorySurvivesTheUpdateToV2() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO block_events (timeMillis, surface, platform, outcome) VALUES (1000, 'SHORTS_PLAYER', 'YOUTUBE', 'BLOCKED')")
            execSQL("INSERT INTO exposures (surface, platform, startMillis, endMillis) VALUES ('REELS_VIEWER', 'INSTAGRAM', 0, 5000)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 2, true)

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), NaoRolaDatabase::class.java, dbName)
            .allowMainThreadQueries().build()
        runBlocking {
            assertEquals(1, db.statsDao().eventsSince(0).size)
            assertEquals(5000L, db.statsDao().exposuresSince(0).single().endMillis)
            assertEquals(0, db.statsDao().dailyUsageSince(0).size) // new table exists and is empty
        }
        db.close()
    }
}
