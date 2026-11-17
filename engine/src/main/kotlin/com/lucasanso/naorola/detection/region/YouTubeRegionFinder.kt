package com.lucasanso.naorola.detection.region

import com.lucasanso.naorola.detection.rules.AdRules
import com.lucasanso.naorola.detection.rules.YouTubeRules
import com.lucasanso.naorola.detection.signal.ActionKind
import com.lucasanso.naorola.detection.signal.Region
import com.lucasanso.naorola.detection.signal.RegionKind
import com.lucasanso.naorola.detection.signal.UiAction
import com.lucasanso.naorola.detection.tree.FeedItem
import com.lucasanso.naorola.detection.tree.ScanRequest
import com.lucasanso.naorola.detection.tree.ScreenSummary
import com.lucasanso.naorola.domain.model.Platform

/**
 * Finds what can be hidden on YouTube screens that are not the Shorts player: Shorts shelves and
 * grids in feeds (by shape: tall cards side by side), the Shorts tab, sponsored cards, and the
 * "Skip ad" button of the player.
 */
object YouTubeRegionFinder {
    const val KEY_SHORTS = "shorts"
    const val KEY_HOME = "home"

    val SCAN_REQUEST = ScanRequest(labelKey = ::keyOf, nodeIds = YouTubeRules.SKIP_AD_IDS, captureTopEditTexts = true)

    private fun keyOf(label: String): String? = AdRules.keyOf(label) ?: when (label) {
        in YouTubeRules.SHORTS_TAB_LABELS -> KEY_SHORTS
        in YouTubeRules.HOME_TAB_LABELS -> KEY_HOME
        else -> null
    }

    fun regions(summary: ScreenSummary): List<Region> {
        val regions = ArrayList<Region>()
        val feed = summary.mainFeed()
        if (feed != null && isHomeFeed(summary)) regions += Region(feed.bounds, RegionKind.HOME_FEED, Platform.YOUTUBE)
        if (feed != null) run {
            for (item in summary.itemsOf(feed)) {
                val kind = when {
                    item.labels.any(AdRules::isAdLabel) -> RegionKind.AD
                    isShortsItem(item) -> RegionKind.SHORT_VIDEO_SHELF
                    else -> null
                } ?: continue
                FeedRegions.clip(item.bounds, feed.bounds)?.let { regions += Region(it, kind, Platform.YOUTUBE, feed.bounds) }
            }
        }
        FeedRegions.navButton(summary, KEY_SHORTS)?.let { regions += Region(it, RegionKind.SHORT_VIDEO_TAB, Platform.YOUTUBE) }
        return regions
    }

    /**
     * Home tab selected in the bottom bar and no search on screen (YouTube keeps Home selected while
     * showing search results, which must stay visible).
     */
    fun isHomeFeed(summary: ScreenSummary): Boolean {
        val homeSelected = summary.keyedLabels[KEY_HOME].orEmpty().any { FeedRegions.isNavButton(it.bounds) && it.node.isSelected }
        val searching = summary.capturedTexts.isNotEmpty() || summary.ids.keys.any { it in YouTubeRules.SEARCH_IDS }
        return homeSelected && !searching
    }

    /**
     * A shelf or grid cell of Shorts. YouTube only shows Shorts as tall thumbnails, so the shape
     * is the main evidence; the "Shorts" title or a "reel_" id (YouTube's internal name) also count.
     */
    fun isShortsItem(item: FeedItem): Boolean =
        item.hasPortraitPair() ||
            item.isPortrait ||
            (item.portraitCards.isNotEmpty() && item.labels.any { it in YouTubeRules.SHORTS_TAB_LABELS }) ||
            item.ids.any { it.startsWith(YouTubeRules.SHORTS_ID_PREFIX) }

    /** The skip button, when it can be pressed. Weak labels ("Pular") need an ad evidently playing. */
    fun actions(summary: ScreenSummary): List<UiAction> {
        val byId = YouTubeRules.SKIP_AD_IDS.firstNotNullOfOrNull { summary.keyedIds[it] }
        val target = byId
            ?: summary.keyedLabels[AdRules.KEY_SKIP]?.firstOrNull()
            ?: summary.keyedLabels[AdRules.KEY_SKIP_WEAK]?.firstOrNull()?.takeIf { adPlaying(summary) }
        return listOfNotNull(target?.let { UiAction(ActionKind.SKIP_AD, it.node) })
    }

    private fun adPlaying(summary: ScreenSummary): Boolean =
        !summary.keyedLabels[AdRules.KEY_AD].isNullOrEmpty() ||
            summary.ids.keys.any { id -> id in YouTubeRules.AD_PLAYER_IDS || id.startsWith("ad_") || "_ad_" in id || id.endsWith("_ad") }
}
