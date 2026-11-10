package com.lucasanso.naorola.detection.rules

import com.lucasanso.naorola.detection.signal.Polarity.NEGATIVE
import com.lucasanso.naorola.detection.signal.Polarity.POSITIVE
import com.lucasanso.naorola.domain.model.ScreenContext

/**
 * Instagram rules. Internally Instagram calls Reels "clips" and Stories "reel" — the opposite of
 * YouTube, which is why rule sets are never shared between apps.
 *
 * STATUS: "clips_tab", "search_tab", "profile_tab" and "clips_header_title" appear in recent
 * open-source code; every other id is a CANDIDATE to be confirmed with the diagnostic mode.
 */
object InstagramRules {
    const val PACKAGE = "com.instagram.android"

    val INFO = RuleSetInfo(name = "Instagram", version = "2026.11.0", status = "parcialmente confirmados (abas)")

    const val SIGNAL_VIEWER_ID = "ig.reels_viewer_id"
    const val SIGNAL_CLIPS_NAMESPACE = "ig.clips_namespace"
    const val SIGNAL_PAGER = "ig.fullscreen_vertical_pager"
    const val SIGNAL_TAB = "ig.reels_tab_selected"
    const val SIGNAL_STORIES = "ig.stories_viewer"

    val REELS_VIEWER_IDS = setOf(
        "clips_viewer_view_pager",
        "clips_viewer_container",
        "clips_video_container",
        "root_clips_layout",
    )

    /** Confirmed ids that also appear outside the viewer (tab bar, feed carousel header). */
    val CLIPS_IDS_OUTSIDE_VIEWER = setOf("clips_tab", "clips_header_title")

    const val REELS_TAB_ID = "clips_tab"
    const val FEED_TAB_ID = "feed_tab"

    /** Stories viewer ids start with this prefix. Never block Stories. */
    const val STORIES_PREFIX = "reel_viewer"

    val DIRECT_PREFIXES = setOf("direct_thread", "row_thread_composer")

    val rules: List<Rule> = listOf(
        AnyIdRule(SIGNAL_VIEWER_ID, POSITIVE, 45, REELS_VIEWER_IDS),
        IdPrefixRule(
            SIGNAL_CLIPS_NAMESPACE, POSITIVE, 20,
            prefix = "clips_", minDistinct = 2, exclude = REELS_VIEWER_IDS + CLIPS_IDS_OUTSIDE_VIEWER,
        ),
        FullScreenPagerRule(SIGNAL_PAGER, POSITIVE, 35),
        SelectedIdRule(SIGNAL_TAB, POSITIVE, 25, setOf(REELS_TAB_ID)),
        IdPrefixRule(SIGNAL_STORIES, NEGATIVE, 100, prefix = STORIES_PREFIX),
    )

    val contextRules: List<ContextRule> = listOf(
        ContextRule(ScreenContext.STORIES, anyIdPrefixes = setOf(STORIES_PREFIX)),
        ContextRule(ScreenContext.DIRECT_MESSAGES, anyIdPrefixes = DIRECT_PREFIXES),
        ContextRule(ScreenContext.FEED, anySelectedIds = setOf(FEED_TAB_ID)),
    )
}
