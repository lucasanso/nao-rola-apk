package com.lucasanso.naorola.domain

import com.lucasanso.naorola.domain.stats.ForegroundTimeCalculator
import com.lucasanso.naorola.domain.stats.UsageEventRecord
import com.lucasanso.naorola.domain.stats.UsageEventType.DEVICE_SHUTDOWN
import com.lucasanso.naorola.domain.stats.UsageEventType.PAUSED
import com.lucasanso.naorola.domain.stats.UsageEventType.RESUMED
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundTimeCalculatorTest {
    private val yt = "yt"
    private val ig = "ig"
    private fun e(t: Long, pkg: String, type: com.lucasanso.naorola.domain.stats.UsageEventType, activity: String = "Main") =
        UsageEventRecord(t, pkg, activity, type)

    private fun compute(vararg events: UsageEventRecord, from: Long = 1_000, to: Long = 10_000) =
        ForegroundTimeCalculator.compute(events.toList(), from, to, setOf(yt, ig))

    @Test fun simpleSessions() {
        val r = compute(e(2_000, yt, RESUMED), e(3_000, yt, PAUSED), e(4_000, ig, RESUMED), e(4_500, ig, PAUSED))
        assertEquals(1_000L, r[yt])
        assertEquals(500L, r[ig])
    }

    @Test fun activityChangesInsideTheSameAppAreNotDoubleCounted() {
        val r = compute(
            e(2_000, yt, RESUMED, "A"), e(2_500, yt, RESUMED, "B"), e(2_600, yt, PAUSED, "A"), e(3_000, yt, PAUSED, "B"),
        )
        assertEquals(1_000L, r[yt])
    }

    @Test fun sessionStartedBeforeTheWindowIsClipped() {
        assertEquals(1_000L, compute(e(500, yt, RESUMED), e(2_000, yt, PAUSED))[yt])
    }

    @Test fun pauseWithoutResumeCountsFromWindowStart() {
        assertEquals(1_500L, compute(e(2_500, yt, PAUSED))[yt])
    }

    @Test fun stillOpenCountsUntilNow() {
        assertEquals(1_000L, compute(e(9_000, yt, RESUMED))[yt])
    }

    @Test fun shutdownClosesEverything() {
        assertEquals(3_000L, compute(e(2_000, yt, RESUMED), e(5_000, yt, DEVICE_SHUTDOWN))[yt])
    }

    @Test fun otherAppsAreIgnored() {
        assertEquals(setOf(yt, ig), compute(e(2_000, "other", RESUMED)).keys)
    }
}
