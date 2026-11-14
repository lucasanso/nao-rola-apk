package com.lucasanso.naorola.detection.region

import com.lucasanso.naorola.detection.rules.AdRules
import com.lucasanso.naorola.detection.rules.InstagramRules
import com.lucasanso.naorola.detection.signal.Region
import com.lucasanso.naorola.detection.signal.RegionKind
import com.lucasanso.naorola.detection.tree.FeedItem
import com.lucasanso.naorola.detection.tree.RelBounds
import com.lucasanso.naorola.detection.tree.ScanRequest
import com.lucasanso.naorola.detection.tree.ScreenSummary
import com.lucasanso.naorola.domain.model.Platform
import com.lucasanso.naorola.domain.model.ScreenContext

/**
 * Finds what can be hidden on Instagram screens that are not the Reels viewer: Reels posts and
 * carousels in feeds ("clips_" ids, Instagram's internal name), the Reels tab, sponsored posts and
 * sponsored stories. Messages are left alone (a Reel sent by a friend stays visible).
 *
 * Instagram draws one post as several rows of the feed list (header, media, buttons, caption);
 * an ad is marked only in the header, so ad rows are grouped up to the next post header.
 */
object InstagramRegionFinder {
    val SCAN_REQUEST = ScanRequest(labelKey = AdRules::keyOf)

    fun regions(summary: ScreenSummary, context: ScreenContext): List<Region> {
        when (context) {
            ScreenContext.DIRECT_MESSAGES -> return emptyList()
            ScreenContext.STORIES -> return if (summary.keyedLabels[AdRules.KEY_AD].isNullOrEmpty()) {
                emptyList()
            } else {
                listOf(Region(RelBounds.FULL, RegionKind.AD, Platform.INSTAGRAM))
            }
            else -> Unit
        }
        val regions = ArrayList<Region>()
        summary.mainFeed()?.let { feed ->
            for (post in posts(summary.itemsOf(feed))) {
                if (post.any { item -> item.labels.any(AdRules::isAdLabel) }) {
                    FeedRegions.clip(FeedRegions.union(post.map { it.bounds }), feed.bounds)
                        ?.let { regions += Region(it, RegionKind.AD, Platform.INSTAGRAM, feed.bounds) }
                    continue
                }
                for (item in post.filter(::isReelsItem)) {
                    val kind = if (InstagramRules.REELS_CAROUSEL_ID in item.ids) RegionKind.SHORT_VIDEO_SHELF else RegionKind.SHORT_VIDEO_FEED_ITEM
                    FeedRegions.clip(item.bounds, feed.bounds)?.let { regions += Region(it, kind, Platform.INSTAGRAM, feed.bounds) }
                }
            }
        }
        summary.ids[InstagramRules.REELS_TAB_ID]?.takeIf(FeedRegions::isNavButton)
            ?.let { regions += Region(it, RegionKind.SHORT_VIDEO_TAB, Platform.INSTAGRAM) }
        return regions
    }

    fun isReelsItem(item: FeedItem): Boolean =
        item.ids.any { it.startsWith(InstagramRules.CLIPS_PREFIX) && it !in InstagramRules.CLIPS_FEED_EXCLUDE }

    /** Groups feed rows into posts. Without known header ids every row stands alone. */
    private fun posts(items: List<FeedItem>): List<List<FeedItem>> {
        if (items.none(::isPostHeader)) return items.map { listOf(it) }
        val groups = ArrayList<MutableList<FeedItem>>()
        for (item in items) {
            if (groups.isEmpty() || isPostHeader(item)) groups += mutableListOf(item) else groups.last() += item
        }
        return groups
    }

    private fun isPostHeader(item: FeedItem): Boolean = item.ids.any { it in InstagramRules.POST_HEADER_IDS }
}
