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

    val SCAN_REQUEST = ScanRequest(labelKey = ::keyOf, nodeIds = YouTubeRules.SKIP_AD_IDS)

    private fun keyOf(label: String): String? =
        AdRules.keyOf(label) ?: if (label in YouTubeRules.SHORTS_TAB_LABELS) KEY_SHORTS else null

    fun regions(summary: ScreenSummary): List<Region> {
        val regions = ArrayList<Region>()
        summary.mainFeed()?.let { feed ->
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
