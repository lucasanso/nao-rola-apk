package com.lucasanso.naorola.detection

import com.lucasanso.naorola.detection.classifier.Classification
import com.lucasanso.naorola.detection.classifier.ShortVideoClassifier
import com.lucasanso.naorola.detection.detector.InstagramContentDetector
import com.lucasanso.naorola.detection.rules.InstagramRules
import com.lucasanso.naorola.detection.tree.UiTree
import com.lucasanso.naorola.domain.model.ScreenContext
import com.lucasanso.naorola.domain.model.Surface
import com.lucasanso.naorola.fixtures.Screens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramContentDetectorTest {
    private val detector = InstagramContentDetector()
    private val classifier = ShortVideoClassifier()
    private fun classify(tree: UiTree) = classifier.classify(detector.detect(tree))

    @Test fun reelsTabIsShortVideo() {
        val result = classify(Screens.instagramReelsTab(pageKey = 31))
        assertTrue(result is Classification.ShortVideo)
        result as Classification.ShortVideo
        assertEquals(Surface.REELS_VIEWER, result.surface)
        assertEquals(31, result.pageKey)
    }

    @Test fun reelOpenedFromFeedIsShortVideo() {
        assertTrue(classify(Screens.instagramReelFromFeed()) is Classification.ShortVideo)
    }

    @Test fun reelOpenedFromDirectIsShortVideo() {
        assertTrue(classify(Screens.instagramReelFromDirect()) is Classification.ShortVideo)
    }

    @Test fun reelsTabStillDetectedWhenViewerIdsAreRenamed() {
        assertTrue(classify(Screens.instagramReelsTabIdsRenamed()) is Classification.ShortVideo)
    }

    @Test fun storiesAreNeverShortVideo() {
        val detection = detector.detect(Screens.instagramStories())
        val result = classifier.classify(detection)
        assertTrue(result is Classification.NotShortVideo)
        assertEquals(ScreenContext.STORIES, result.context)
        assertEquals(InstagramRules.SIGNAL_STORIES, (result as Classification.NotShortVideo).vetoedBy)
    }

    @Test fun mainFeedIsNotBlocked() {
        val result = classify(Screens.instagramFeed())
        assertTrue(result is Classification.NotShortVideo)
        assertEquals(ScreenContext.FEED, result.context)
    }

    @Test fun directThreadIsNotBlockedAndRecognised() {
        val result = classify(Screens.instagramDirectThread())
        assertTrue(result is Classification.NotShortVideo)
        assertEquals(ScreenContext.DIRECT_MESSAGES, result.context)
    }

    @Test fun pageKeyChangesWhenSwipingToNextReel() {
        val first = detector.detect(Screens.instagramReelsTab(pageKey = 1)).pageKey
        val next = detector.detect(Screens.instagramReelsTab(pageKey = 2)).pageKey
        assertTrue(first != null && next != null && first != next)
    }
}
