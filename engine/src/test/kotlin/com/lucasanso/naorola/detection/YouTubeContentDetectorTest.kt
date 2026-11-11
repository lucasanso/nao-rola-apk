package com.lucasanso.naorola.detection

import com.lucasanso.naorola.detection.classifier.Classification
import com.lucasanso.naorola.detection.classifier.ShortVideoClassifier
import com.lucasanso.naorola.detection.detector.YouTubeContentDetector
import com.lucasanso.naorola.detection.rules.YouTubeRules
import com.lucasanso.naorola.detection.tree.UiTree
import com.lucasanso.naorola.domain.model.ScreenContext
import com.lucasanso.naorola.domain.model.Surface
import com.lucasanso.naorola.fixtures.Screens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeContentDetectorTest {
    private val detector = YouTubeContentDetector()
    private val classifier = ShortVideoClassifier()
    private fun classify(tree: UiTree) = classifier.classify(detector.detect(tree))

    @Test fun shortsTabIsShortVideo() {
        val result = classify(Screens.youtubeShortsTab(pageKey = 5))
        assertTrue(result is Classification.ShortVideo)
        result as Classification.ShortVideo
        assertEquals(Surface.SHORTS_PLAYER, result.surface)
        assertEquals(5, result.pageKey)
    }

    @Test fun shortsOpenedFromHomeIsShortVideo() {
        assertTrue(classify(Screens.youtubeShortsFromHome()) is Classification.ShortVideo)
    }

    @Test fun shortsStillDetectedWhenIdsAreRenamedButTabAndShapeRemain() {
        assertTrue(classify(Screens.youtubeShortsTabIdsRenamed()) is Classification.ShortVideo)
    }

    @Test fun smallMiniPlayerDoesNotCancelShorts() {
        assertTrue(classify(Screens.youtubeShortsWithMiniPlayer()) is Classification.ShortVideo)
    }

    @Test fun conventionalVideoIsNeverShortVideo() {
        val result = classify(Screens.youtubeWatchPage())
        assertTrue(result is Classification.NotShortVideo)
        assertEquals(ScreenContext.CONVENTIONAL_VIDEO, result.context)
        assertEquals(YouTubeRules.SIGNAL_CONVENTIONAL_PLAYER, (result as Classification.NotShortVideo).vetoedBy)
    }

    @Test fun fullscreenConventionalVideoIsNeverShortVideo() {
        assertTrue(classify(Screens.youtubeWatchFullscreen()) is Classification.NotShortVideo)
    }

    @Test fun homeFeedWithShortsShelfIsNotBlocked() {
        assertTrue(classify(Screens.youtubeHomeWithShortsShelf()) is Classification.NotShortVideo)
    }

    @Test fun supportsOnlyYouTube() {
        assertTrue(detector.supports(YouTubeRules.PACKAGE))
        assertTrue(!detector.supports("com.instagram.android"))
    }
}
