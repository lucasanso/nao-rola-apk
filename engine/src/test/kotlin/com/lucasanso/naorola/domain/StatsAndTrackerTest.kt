package com.lucasanso.naorola.domain

import com.lucasanso.naorola.domain.model.BlockEvent
import com.lucasanso.naorola.domain.model.BlockOutcome
import com.lucasanso.naorola.domain.model.Platform
import com.lucasanso.naorola.domain.model.ShortVideoExposure
import com.lucasanso.naorola.domain.model.Surface
import com.lucasanso.naorola.domain.session.DailyExposureTracker
import com.lucasanso.naorola.domain.stats.AvoidedExposureEstimator
import com.lucasanso.naorola.domain.stats.StatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class StatsAndTrackerTest {
    private val zone = ZoneOffset.UTC
    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 11, day, hour, 0).toInstant(zone).toEpochMilli()

    @Test fun trackerCountsPerPlatformAndResetsAtMidnight() {
        val tracker = DailyExposureTracker { zone }
        tracker.seed(at(10, 9), mapOf(Platform.YOUTUBE to 60_000L))
        tracker.add(ShortVideoExposure(Surface.SHORTS_PLAYER, at(10, 10), at(10, 10) + 30_000))
        tracker.add(ShortVideoExposure(Surface.WEB_SHORTS, at(10, 11), at(10, 11) + 10_000))
        tracker.add(ShortVideoExposure(Surface.REELS_VIEWER, at(10, 12), at(10, 12) + 5_000))
        assertEquals(100_000L, tracker.usedToday(Platform.YOUTUBE, at(10, 13)))
        assertEquals(5_000L, tracker.usedToday(Platform.INSTAGRAM, at(10, 13)))
        assertEquals(0L, tracker.usedToday(Platform.YOUTUBE, at(11, 8)))
        tracker.add(ShortVideoExposure(Surface.SHORTS_PLAYER, at(11, 9), at(11, 9) + 1_000))
        assertEquals(1_000L, tracker.usedToday(Platform.YOUTUBE, at(11, 10)))
        tracker.add(ShortVideoExposure(Surface.SHORTS_PLAYER, at(10, 23), at(10, 23) + 9_000)) // late record of yesterday
        assertEquals(1_000L, tracker.usedToday(Platform.YOUTUBE, at(11, 10)))
    }

    @Test fun dailySeriesGroupsByLocalDay() {
        val days = StatsCalculator.lastDays(LocalDate.of(2026, 11, 10), 3)
        assertEquals(listOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10)), days)
        val events = listOf(
            BlockEvent(at(9, 10), Surface.SHORTS_PLAYER, BlockOutcome.BLOCKED),
            BlockEvent(at(9, 11), Surface.SHORTS_PLAYER, BlockOutcome.WENT_BACK), // not a block
            BlockEvent(at(10, 10), Surface.REELS_VIEWER, BlockOutcome.BLOCKED),
            BlockEvent(at(10, 12), Surface.SHORT_VIDEO_APP, BlockOutcome.BLOCKED),
        )
        val exposures = listOf(ShortVideoExposure(Surface.REELS_VIEWER, at(10, 9), at(10, 9) + 40_000))
        val series = StatsCalculator.dailySeries(days, events, exposures, appUsage = null, zone = zone)
        assertEquals(0, series[0].blockedTotal)
        assertEquals(mapOf(Platform.YOUTUBE to 1), series[1].blocked)
        assertEquals(2, series[2].blockedTotal)
        assertEquals(40_000L, series[2].shortVideoMillis[Platform.INSTAGRAM])
        assertNull(series[2].appUsageMillis)
    }

    @Test fun avoidedExposureNeedsEnoughOwnData() {
        val few = AvoidedExposureEstimator.estimate(blocked = 20, sessionDurations = List(9) { 30_000L })
        assertTrue(few is AvoidedExposureEstimator.Estimate.Insufficient)
        val durations = List(10) { (it + 1) * 10_000L } // 10 s … 100 s → median 55 s
        val estimate = AvoidedExposureEstimator.estimate(blocked = 4, sessionDurations = durations)
            as AvoidedExposureEstimator.Estimate.Available
        assertEquals(55_000L, estimate.medianSessionMillis)
        assertEquals(220_000L, estimate.millis)
    }
}
