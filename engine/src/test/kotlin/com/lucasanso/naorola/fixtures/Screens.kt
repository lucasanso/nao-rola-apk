package com.lucasanso.naorola.fixtures

import com.lucasanso.naorola.detection.rules.InstagramRules
import com.lucasanso.naorola.detection.rules.YouTubeRules

/**
 * SYNTHETIC screens built from the candidate ids in the rule sets plus the expected geometry.
 * They test the logic, not the real apps: real dumps from the diagnostic mode will be added
 * next to them once a device is available.
 */
object Screens {
    private const val YT = YouTubeRules.PACKAGE
    private const val IG = InstagramRules.PACKAGE
    private fun yt(id: String) = "$YT:id/$id"
    private fun ig(id: String) = "$IG:id/$id"

    private fun ytBottomBar(selected: String) = node(
        id = yt("pivot_bar"), bounds = b(0f, 0.93f, 1f, 1f),
        children = listOf("Home", "Shorts", "Subscriptions", "You").map { label ->
            node(desc = label, selected = label == selected, bounds = b(0f, 0.93f, 0.25f, 1f))
        },
    )

    private fun shortsPager(pageKey: Int, withIds: Boolean = true) = node(
        id = if (withIds) yt("reel_recycler") else null, cls = RECYCLER, scrollable = true,
        bounds = b(0f, 0f, 1f, 0.93f), rows = 50, cols = 1,
        children = listOf(
            node(
                id = if (withIds) yt("reel_player_page_container") else null,
                bounds = b(0f, 0f, 1f, 0.93f), key = pageKey,
                children = listOf(node(id = if (withIds) yt("reel_player_overlay") else null, bounds = b(0.85f, 0.4f, 1f, 0.9f))),
            ),
        ),
    )

    fun youtubeShortsTab(pageKey: Int = 1) = FakeTree(
        YT, node(cls = FRAME, children = listOf(shortsPager(pageKey), ytBottomBar(selected = "Shorts"))),
    )

    /** Shorts opened from a thumbnail on Home: no tab selected, only the player. */
    fun youtubeShortsFromHome() = FakeTree(
        YT, node(cls = FRAME, children = listOf(shortsPager(pageKey = 7), ytBottomBar(selected = "Home"))),
    )

    /** The ids were renamed by an update: only geometry and the selected tab remain. */
    fun youtubeShortsTabIdsRenamed() = FakeTree(
        YT, node(cls = FRAME, children = listOf(shortsPager(pageKey = 3, withIds = false), ytBottomBar(selected = "Shorts"))),
    )

    /** Shorts with the regular mini player docked at the bottom (small: must not veto). */
    fun youtubeShortsWithMiniPlayer() = FakeTree(
        YT, node(
            cls = FRAME,
            children = listOf(
                shortsPager(pageKey = 4),
                node(id = yt("watch_player"), bounds = b(0f, 0.86f, 0.35f, 0.93f)),
                ytBottomBar(selected = "Shorts"),
            ),
        ),
    )

    /** Regular video (even a vertical one) on the watch page. */
    fun youtubeWatchPage() = FakeTree(
        YT, node(
            cls = FRAME,
            children = listOf(
                node(id = yt("watch_player"), bounds = b(0f, 0.05f, 1f, 0.33f), children = listOf(node(id = yt("youtube_controls_overlay"), bounds = b(0f, 0.05f, 1f, 0.33f)))),
                node(cls = RECYCLER, scrollable = true, bounds = b(0f, 0.33f, 1f, 1f), rows = 20, cols = 1, children = listOf(
                    node(bounds = b(0f, 0.33f, 1f, 0.5f)), node(bounds = b(0f, 0.5f, 1f, 0.7f)),
                )),
            ),
        ),
    )

    /** Regular video in full screen (landscape). */
    fun youtubeWatchFullscreen() = FakeTree(
        YT, node(cls = FRAME, children = listOf(node(id = yt("watch_player"), bounds = b(0f, 0f, 1f, 1f)))),
    )

    /** Home feed with a Shorts shelf: reel-named ids but no full-screen pager. */
    fun youtubeHomeWithShortsShelf() = FakeTree(
        YT, node(
            cls = FRAME,
            children = listOf(
                node(cls = RECYCLER, scrollable = true, bounds = b(0f, 0.08f, 1f, 0.93f), rows = 30, cols = 1, children = listOf(
                    node(bounds = b(0f, 0.08f, 1f, 0.4f)),
                    node(id = yt("reel_shelf_header"), bounds = b(0f, 0.4f, 1f, 0.45f)),
                    node(id = yt("reel_shelf_items"), cls = RECYCLER, scrollable = true, bounds = b(0f, 0.45f, 1f, 0.75f), rows = 1, cols = 10, children = listOf(
                        node(id = yt("reel_item"), bounds = b(0f, 0.45f, 0.5f, 0.75f)),
                        node(id = yt("reel_item"), bounds = b(0.5f, 0.45f, 1f, 0.75f)),
                    )),
                )),
                ytBottomBar(selected = "Home"),
            ),
        ),
    )

    private fun igTabBar(selected: String) = node(
        id = ig("tab_bar"), bounds = b(0f, 0.93f, 1f, 1f),
        children = listOf("feed_tab", "search_tab", "clips_tab", "profile_tab").map { tab ->
            node(id = ig(tab), selected = tab == selected, bounds = b(0f, 0.93f, 0.25f, 1f))
        },
    )

    private fun reelsViewer(pageKey: Int, withIds: Boolean = true) = node(
        id = if (withIds) ig("clips_viewer_view_pager") else null, cls = RECYCLER, scrollable = true,
        bounds = b(0f, 0f, 1f, 0.93f), rows = 100, cols = 1,
        children = listOf(
            node(
                id = if (withIds) ig("clips_video_container") else null, bounds = b(0f, 0f, 1f, 0.93f), key = pageKey,
                children = listOf(node(id = if (withIds) ig("clips_ufi_component") else null, bounds = b(0.85f, 0.5f, 1f, 0.9f))),
            ),
        ),
    )

    fun instagramReelsTab(pageKey: Int = 11) = FakeTree(
        IG, node(cls = FRAME, children = listOf(reelsViewer(pageKey), igTabBar(selected = "clips_tab"))),
    )

    /** A Reel opened from the feed, Explore or a profile: viewer without the Reels tab selected. */
    fun instagramReelFromFeed(pageKey: Int = 12) = FakeTree(
        IG, node(cls = FRAME, children = listOf(reelsViewer(pageKey), igTabBar(selected = "feed_tab"))),
    )

    /** Reel opened from a DM conversation: full-screen viewer, no tab bar. */
    fun instagramReelFromDirect(pageKey: Int = 21) = FakeTree(IG, node(cls = FRAME, children = listOf(reelsViewer(pageKey))))

    fun instagramReelsTabIdsRenamed() = FakeTree(
        IG, node(cls = FRAME, children = listOf(reelsViewer(pageKey = 13, withIds = false), igTabBar(selected = "clips_tab"))),
    )

    /** Stories: full-screen viewer, horizontal, with "reel_viewer" ids. Must never be blocked. */
    fun instagramStories() = FakeTree(
        IG, node(
            cls = FRAME,
            children = listOf(
                node(id = ig("reel_viewer_root"), bounds = b(0f, 0f, 1f, 1f), children = listOf(
                    node(id = ig("reel_viewer_media_layout"), bounds = b(0f, 0f, 1f, 0.92f)),
                    node(id = ig("reel_viewer_progress_bar"), bounds = b(0f, 0.01f, 1f, 0.02f)),
                )),
                node(cls = RECYCLER, scrollable = true, bounds = b(0f, 0f, 1f, 1f), rows = 1, cols = 8, children = listOf(
                    node(bounds = b(0f, 0f, 1f, 1f)),
                )),
            ),
        ),
    )

    /** Main feed: vertical list with several posts visible (no item fills the screen). */
    fun instagramFeed() = FakeTree(
        IG, node(
            cls = FRAME,
            children = listOf(
                node(cls = RECYCLER, scrollable = true, bounds = b(0f, 0.06f, 1f, 0.93f), rows = 40, cols = 1, children = listOf(
                    node(id = ig("row_feed_photo_profile_name"), bounds = b(0f, 0.06f, 1f, 0.1f)),
                    node(id = ig("row_feed_photo_imageview"), bounds = b(0f, 0.1f, 1f, 0.68f)),
                    node(id = ig("clips_header_title"), bounds = b(0f, 0.7f, 1f, 0.74f)),
                )),
                igTabBar(selected = "feed_tab"),
            ),
        ),
    )

    fun instagramDirectThread() = FakeTree(
        IG, node(
            cls = FRAME,
            children = listOf(
                node(id = ig("direct_thread_list"), cls = RECYCLER, scrollable = true, bounds = b(0f, 0.08f, 1f, 0.9f), rows = 60, cols = 1, children = listOf(
                    node(bounds = b(0f, 0.1f, 1f, 0.2f)), node(bounds = b(0f, 0.2f, 1f, 0.3f)),
                )),
                node(id = ig("row_thread_composer_edittext"), cls = EDIT_TEXT, bounds = b(0f, 0.9f, 1f, 0.96f)),
            ),
        ),
    )

    fun browser(packageName: String, addressBarId: String?, url: String) = FakeTree(
        packageName, node(
            cls = FRAME,
            children = listOf(
                node(id = addressBarId?.let { "$packageName:id/$it" }, cls = EDIT_TEXT, bounds = b(0.1f, 0.03f, 0.9f, 0.08f), text = url),
                node(id = "$packageName:id/content", bounds = b(0f, 0.1f, 1f, 1f)),
            ),
        ),
    )
}
