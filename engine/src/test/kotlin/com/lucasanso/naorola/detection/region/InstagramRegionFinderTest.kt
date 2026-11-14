package com.lucasanso.naorola.detection.region

import com.lucasanso.naorola.detection.classifier.Classification
import com.lucasanso.naorola.detection.classifier.ShortVideoClassifier
import com.lucasanso.naorola.detection.detector.InstagramContentDetector
import com.lucasanso.naorola.detection.signal.RegionKind
import com.lucasanso.naorola.detection.tree.RelBounds
import com.lucasanso.naorola.fixtures.Screens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramRegionFinderTest {
    private val detector = InstagramContentDetector()

    @Test fun feedCoversSponsoredPostWholeAndReelMedia() {
        val result = detector.detect(Screens.instagramFeedMixed())
        assertTrue(ShortVideoClassifier().classify(result) is Classification.NotShortVideo)
        val byKind = result.regions.groupBy { it.kind }
        // Sponsored post: header, media and buttons, up to the next post header.
        assertEquals(listOf(RelBounds(0f, 0.25f, 1f, 0.8f)), byKind[RegionKind.AD]?.map { it.bounds })
        // Reel post: the media row, clipped to the visible list.
        assertEquals(listOf(RelBounds(0f, 0.86f, 1f, 0.93f)), byKind[RegionKind.SHORT_VIDEO_FEED_ITEM]?.map { it.bounds })
        assertEquals(RelBounds(0f, 0.93f, 0.25f, 1f), byKind[RegionKind.SHORT_VIDEO_TAB]?.single()?.bounds)
    }

    @Test fun reelsCarouselIsCovered() {
        val shelf = detector.detect(Screens.instagramFeed()).regions.filter { it.kind == RegionKind.SHORT_VIDEO_SHELF }
        assertEquals(listOf(RelBounds(0f, 0.7f, 1f, 0.74f)), shelf.map { it.bounds })
    }

    @Test fun tallCardsThatAreNotReelsStayVisible() {
        val regions = detector.detect(Screens.instagramSuggestedAccounts()).regions
        assertEquals(listOf(RegionKind.SHORT_VIDEO_TAB), regions.map { it.kind })
    }

    @Test fun sponsoredStoryIsCoveredWhole() {
        val region = detector.detect(Screens.instagramStoryAd()).regions.single()
        assertEquals(RegionKind.AD, region.kind)
        assertEquals(RelBounds.FULL, region.bounds)
        assertTrue(detector.detect(Screens.instagramStoryAd(sponsored = false)).regions.isEmpty())
        assertTrue(detector.detect(Screens.instagramStories()).regions.isEmpty())
    }

    @Test fun reelSentInAConversationStaysVisible() {
        assertTrue(detector.detect(Screens.instagramDirectWithReel()).regions.isEmpty())
    }

    @Test fun reelsViewerHasNoFeedCovers() {
        assertTrue(detector.detect(Screens.instagramReelsTab()).regions.none { it.kind == RegionKind.SHORT_VIDEO_FEED_ITEM })
    }
}
