package com.lucasanso.naorola.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lucasanso.naorola.core.Clock
import com.lucasanso.naorola.data.db.NaoRolaDatabase
import com.lucasanso.naorola.data.stats.StatsRepository
import com.lucasanso.naorola.domain.model.BlockEvent
import com.lucasanso.naorola.domain.model.BlockOutcome
import com.lucasanso.naorola.domain.model.Platform
import com.lucasanso.naorola.domain.model.ShortVideoExposure
import com.lucasanso.naorola.domain.model.Surface
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class StatsRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NaoRolaDatabase::class.java)
        .allowMainThreadQueries().build()
    private val noon = LocalDateTime.of(2026, 11, 10, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val repo = StatsRepository(db.statsDao(), Clock { noon }, zone = { ZoneOffset.UTC })

    @After fun close() = db.close()

    @Test fun countsOnlyTodaysBlocksPerPlatform() = runTest {
        val yesterday = noon - 24 * 3_600_000L
        repo.record(BlockEvent(yesterday, Surface.SHORTS_PLAYER, BlockOutcome.BLOCKED))
        repo.record(BlockEvent(noon - 1_000, Surface.SHORTS_PLAYER, BlockOutcome.BLOCKED))
        repo.record(BlockEvent(noon - 900, Surface.SHORTS_PLAYER, BlockOutcome.WENT_BACK))
        repo.record(BlockEvent(noon - 800, Surface.WEB_SHORTS, BlockOutcome.BLOCKED))
        repo.record(BlockEvent(noon - 700, Surface.REELS_VIEWER, BlockOutcome.BLOCKED))
        repo.recordExposure(ShortVideoExposure(Surface.REELS_VIEWER, noon - 60_000, noon - 30_000))

        val today = repo.today().first()
        assertEquals(2, today.blockedByPlatform[Platform.YOUTUBE])
        assertEquals(1, today.blockedByPlatform[Platform.INSTAGRAM])
        assertEquals(3, today.blockedTotal)
        assertEquals(30_000L, today.exposureMillisByPlatform[Platform.INSTAGRAM])
    }

    @Test fun purgeRemovesOldHistory() = runTest {
        repo.record(BlockEvent(noon - 40L * 24 * 3_600_000L, Surface.SHORTS_PLAYER, BlockOutcome.BLOCKED))
        repo.purgeOlderThan(30)
        assertEquals(0, db.statsDao().countByPlatformSince(BlockOutcome.BLOCKED.name, 0).first().size)
    }
}
