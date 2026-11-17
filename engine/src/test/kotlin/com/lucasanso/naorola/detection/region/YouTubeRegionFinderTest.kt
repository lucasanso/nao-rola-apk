package com.lucasanso.naorola.detection.region

import com.lucasanso.naorola.detection.classifier.Classification
import com.lucasanso.naorola.detection.classifier.ShortVideoClassifier
import com.lucasanso.naorola.detection.detector.YouTubeContentDetector
import com.lucasanso.naorola.detection.signal.ActionKind
import com.lucasanso.naorola.detection.signal.RegionKind
import com.lucasanso.naorola.detection.tree.RelBounds
import com.lucasanso.naorola.fixtures.FakeNode
import com.lucasanso.naorola.fixtures.Screens
import com.lucasanso.naorola.fixtures.rounded
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeRegionFinderTest {
    private val detector = YouTubeContentDetector()

    private fun kinds(result: com.lucasanso.naorola.detection.signal.DetectionResult) = result.regions.map { it.kind }

    @Test fun homeCoversAdAndShortsShelfButNotRegularVideos() {
        val result = detector.detect(Screens.youtubeHomeMixed())
        assertTrue(ShortVideoClassifier().classify(result) is Classification.NotShortVideo)
        val feedRegions = result.regions.filter { it.kind != RegionKind.SHORT_VIDEO_TAB && it.kind != RegionKind.HOME_FEED }
        assertEquals(listOf(RegionKind.AD, RegionKind.SHORT_VIDEO_SHELF), feedRegions.map { it.kind })
        assertEquals(RelBounds(0f, 0.08f, 1f, 0.38f), feedRegions[0].bounds)
        assertEquals(RelBounds(0f, 0.38f, 1f, 0.8f), feedRegions[1].bounds.rounded())
    }

    @Test fun shortsTabButtonInTheBottomBarIsCovered() {
        val tab = detector.detect(Screens.youtubeHomeOnlyVideos()).regions.single { it.kind != RegionKind.HOME_FEED }
        assertEquals(RegionKind.SHORT_VIDEO_TAB, tab.kind)
        assertEquals(RelBounds(0f, 0.93f, 0.25f, 1f), tab.bounds)
    }

    @Test fun videoTitledAnuncioIsNotAnAd() {
        assertTrue(RegionKind.AD !in kinds(detector.detect(Screens.youtubeHomeOnlyVideos())))
    }

    @Test fun shelfWithReelIdsIsCoveredToo() {
        val regions = detector.detect(Screens.youtubeHomeWithShortsShelf()).regions.filter { it.kind == RegionKind.SHORT_VIDEO_SHELF }
        assertEquals(2, regions.size)
    }

    @Test fun tabPagerIsNotMistakenForTheFeed() {
        val regions = detector.detect(Screens.youtubeHomeInsidePager()).regions.filter { it.kind == RegionKind.SHORT_VIDEO_SHELF }
        // Only the shelf, never the whole page that contains it.
        assertEquals(listOf(RelBounds(0f, 0.1f, 1f, 0.52f)), regions.map { it.bounds.rounded() })
    }

    @Test fun shortsGridCellsAreCovered() {
        assertEquals(3, detector.detect(Screens.youtubeShortsGrid()).regions.count { it.kind == RegionKind.SHORT_VIDEO_SHELF })
    }

    @Test fun shortsPlayerAndWatchPageHaveNoFeedCovers() {
        assertTrue(detector.detect(Screens.youtubeShortsTab()).regions.none { it.kind == RegionKind.SHORT_VIDEO_SHELF })
        assertTrue(detector.detect(Screens.youtubeWatchPage()).regions.isEmpty())
    }

    @Test fun homeFeedIsFoundOnlyWithHomeSelectedAndNoSearch() {
        val home = detector.detect(Screens.youtubeHomeOnlyVideos()).regions.filter { it.kind == RegionKind.HOME_FEED }
        assertEquals(listOf(RelBounds(0f, 0.08f, 1f, 0.93f)), home.map { it.bounds })
        assertTrue(detector.detect(Screens.youtubeSearchResults()).regions.none { it.kind == RegionKind.HOME_FEED })
        assertTrue(detector.detect(Screens.youtubeSubscriptions()).regions.none { it.kind == RegionKind.HOME_FEED })
        assertTrue(detector.detect(Screens.youtubeWatchPage()).regions.none { it.kind == RegionKind.HOME_FEED })
    }

    @Test fun skipButtonFoundById() {
        val action = detector.detect(Screens.youtubeAdPlaying()).actions.single()
        assertEquals(ActionKind.SKIP_AD, action.kind)
        assertEquals("com.google.android.youtube:id/skip_ad_button", (action.target as FakeNode).viewId)
    }

    @Test fun skipButtonFoundByLabelWhenIdChanges() {
        assertEquals(1, detector.detect(Screens.youtubeAdPlaying(skipId = "new_skip", skipLabel = "Pular anúncio")).actions.size)
    }

    @Test fun shortSkipLabelNeedsAnAdOnScreen() {
        assertEquals(1, detector.detect(Screens.youtubeAdPlaying(skipId = null, skipLabel = "Pular")).actions.size)
        assertTrue(detector.detect(Screens.youtubeAdPlaying(skipId = null, skipLabel = "Pular", adBadge = false)).actions.isEmpty())
    }

    @Test fun noSkipButtonNoAction() {
        assertTrue(detector.detect(Screens.youtubeAdPlaying(skipId = null)).actions.isEmpty())
        assertTrue(detector.detect(Screens.youtubeWatchPage()).actions.isEmpty())
    }
}
