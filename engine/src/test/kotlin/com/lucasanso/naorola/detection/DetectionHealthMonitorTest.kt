package com.lucasanso.naorola.detection

import com.lucasanso.naorola.detection.classifier.Classification
import com.lucasanso.naorola.detection.classifier.ShortVideoClassifier
import com.lucasanso.naorola.detection.detector.YouTubeContentDetector
import com.lucasanso.naorola.detection.health.DetectionHealthMonitor
import com.lucasanso.naorola.detection.health.HealthEvent
import com.lucasanso.naorola.detection.rules.InstagramRules
import com.lucasanso.naorola.detection.rules.YouTubeRules
import com.lucasanso.naorola.detection.signal.DetectionResult
import com.lucasanso.naorola.detection.signal.Polarity
import com.lucasanso.naorola.detection.signal.Signal
import com.lucasanso.naorola.domain.model.Platform
import com.lucasanso.naorola.domain.model.ScreenContext
import com.lucasanso.naorola.domain.model.Surface
import com.lucasanso.naorola.fixtures.FRAME
import com.lucasanso.naorola.fixtures.FakeTree
import com.lucasanso.naorola.fixtures.Screens
import com.lucasanso.naorola.fixtures.b
import com.lucasanso.naorola.fixtures.node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectionHealthMonitorTest {
    private val monitor = DetectionHealthMonitor(minDurationMillis = 4_000, minObservations = 3)

    private val tabOnly = DetectionResult(
        "youtube", Surface.SHORTS_PLAYER, listOf(Signal(YouTubeRules.SIGNAL_TAB, Polarity.POSITIVE, 25)), ScreenContext.UNKNOWN,
    )
    private val notShort = Classification.NotShortVideo(ScreenContext.UNKNOWN, score = 25)
    private val shortVideo = Classification.ShortVideo(Surface.SHORTS_PLAYER, 90)

    @Test fun suspectsAfterStayingOnTheTabWithoutRecognisingThePlayer() {
        assertNull(monitor.observe(0, tabOnly, notShort))
        assertNull(monitor.observe(2_000, tabOnly, notShort))
        assertNull(monitor.observe(3_999, tabOnly, notShort)) // 3 observations but not long enough
        assertEquals(HealthEvent.Suspected(Platform.YOUTUBE, 0), monitor.observe(4_000, tabOnly, notShort))
        assertNull(monitor.observe(9_000, tabOnly, notShort)) // reported once
    }

    @Test fun recognisingThePlayerRecoversOnce() {
        monitor.seed(setOf(Platform.YOUTUBE))
        assertEquals(HealthEvent.Recovered(Platform.YOUTUBE), monitor.observe(0, tabOnly, shortVideo))
        assertNull(monitor.observe(1_000, tabOnly, shortVideo))
    }

    @Test fun leavingTheTabResetsTheStreak() {
        monitor.observe(0, tabOnly, notShort)
        monitor.observe(2_000, tabOnly, notShort)
        val home = DetectionResult("youtube", Surface.SHORTS_PLAYER, emptyList(), ScreenContext.UNKNOWN)
        monitor.observe(3_000, home, Classification.NotShortVideo(ScreenContext.UNKNOWN))
        assertNull(monitor.observe(5_000, tabOnly, notShort))
        assertNull(monitor.observe(6_000, tabOnly, notShort))
    }

    @Test fun deliberateRejectionsNeverCount() {
        val stories = DetectionResult(
            "instagram", Surface.REELS_VIEWER,
            listOf(Signal(InstagramRules.SIGNAL_TAB, Polarity.POSITIVE, 25), Signal(InstagramRules.SIGNAL_STORIES, Polarity.NEGATIVE, 100)),
            ScreenContext.STORIES,
        )
        val vetoed = Classification.NotShortVideo(ScreenContext.STORIES, 25, vetoedBy = InstagramRules.SIGNAL_STORIES)
        repeat(5) { assertNull(monitor.observe(it * 2_000L, stories, vetoed)) }
    }

    @Test fun realDetectorOnABrokenShortsTabIsSuspected() {
        // Shorts tab selected but the player area has neither known ids nor a full-screen pager.
        val broken = FakeTree(
            YouTubeRules.PACKAGE,
            node(cls = FRAME, children = listOf(
                node(id = "${YouTubeRules.PACKAGE}:id/new_player_v2", bounds = b(0f, 0f, 1f, 0.93f)),
                node(id = "${YouTubeRules.PACKAGE}:id/pivot_bar", bounds = b(0f, 0.93f, 1f, 1f), children = listOf(
                    node(desc = "Shorts", selected = true, bounds = b(0.25f, 0.93f, 0.5f, 1f)),
                )),
            )),
        )
        val detector = YouTubeContentDetector()
        val classifier = ShortVideoClassifier()
        val events = (0..3).mapNotNull { i ->
            val detection = detector.detect(broken)
            monitor.observe(i * 2_000L, detection, classifier.classify(detection))
        }
        assertEquals(listOf<HealthEvent>(HealthEvent.Suspected(Platform.YOUTUBE, 0)), events)
        // The current (synthetic) Shorts screen is recognised, so it never raises a suspicion.
        val healthy = DetectionHealthMonitor()
        val ok = (0..5).mapNotNull { i ->
            val detection = detector.detect(Screens.youtubeShortsTab())
            healthy.observe(i * 2_000L, detection, classifier.classify(detection))
        }
        assertEquals(emptyList<HealthEvent>(), ok)
    }

    @Test fun browserAndOtherAppsAreIgnored() {
        val web = DetectionResult("browser", Surface.WEB_SHORTS, listOf(Signal(YouTubeRules.SIGNAL_TAB, Polarity.POSITIVE, 25)), ScreenContext.UNKNOWN)
        repeat(5) { assertNull(monitor.observe(it * 2_000L, web, notShort)) }
    }
}
