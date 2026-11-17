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

    /** A regular video card: 16:9 thumbnail plus title row, full width. */
    private fun ytVideoCard(top: Float, bottom: Float, label: String? = null) = node(
        bounds = b(0f, top, 1f, bottom),
        children = listOf(
            node(bounds = b(0f, top, 1f, top + (bottom - top) * 0.7f)),
            node(desc = label, bounds = b(0.05f, top + (bottom - top) * 0.72f, 0.95f, bottom)),
        ),
    )

    /** Shorts shelf as recent versions draw it: no ids, a title and two tall cards side by side. */
    private fun ytShortsShelfByShape(top: Float) = node(
        bounds = b(0f, top, 1f, top + 0.42f),
        children = listOf(
            node(text = "Shorts", bounds = b(0.05f, top, 0.4f, top + 0.04f)),
            node(bounds = b(0.02f, top + 0.05f, 0.49f, top + 0.41f), children = listOf(node(bounds = b(0.02f, top + 0.05f, 0.49f, top + 0.41f)))),
            node(bounds = b(0.51f, top + 0.05f, 0.98f, top + 0.41f), children = listOf(node(bounds = b(0.51f, top + 0.05f, 0.98f, top + 0.41f)))),
        ),
    )

    private fun ytFeed(vararg items: FakeNode) = node(
        id = yt("results"), cls = RECYCLER, scrollable = true, bounds = b(0f, 0.08f, 1f, 0.93f), rows = 30, cols = 1,
        children = items.toList(),
    )

    /** Home with a sponsored card, a Shorts shelf (shape only) and a regular video. */
    fun youtubeHomeMixed() = FakeTree(
        YT, node(
            cls = FRAME,
            children = listOf(
                ytFeed(
                    ytVideoCard(0.08f, 0.38f, label = "Patrocinado · Loja Exemplo"),
                    ytShortsShelfByShape(0.38f),
                    ytVideoCard(0.8f, 0.93f),
                ),
                ytBottomBar(selected = "Home"),
            ),
        ),
    )

    /** Home with only regular videos and a movie-free feed: nothing to cover but the tab. */
    fun youtubeHomeOnlyVideos() = FakeTree(
        YT, node(
            cls = FRAME,
            children = listOf(
                ytFeed(ytVideoCard(0.08f, 0.38f, label = "Anúncio do novo celular"), ytVideoCard(0.38f, 0.68f), ytVideoCard(0.68f, 0.93f)),
                ytBottomBar(selected = "Home"),
            ),
        ),
    )

    /** Search results: Home stays selected in the bar, but the query box is on top. */
    fun youtubeSearchResults() = FakeTree(
        YT, node(
            cls = FRAME,
            children = listOf(
                node(cls = EDIT_TEXT, bounds = b(0.12f, 0.02f, 0.9f, 0.07f), text = "receita de pão"),
                ytFeed(ytVideoCard(0.08f, 0.38f), ytVideoCard(0.38f, 0.68f)),
                ytBottomBar(selected = "Home"),
            ),
        ),
    )

    fun youtubeSubscriptions() = FakeTree(
        YT, node(cls = FRAME, children = listOf(ytFeed(ytVideoCard(0.08f, 0.38f), ytVideoCard(0.38f, 0.68f)), ytBottomBar(selected = "Subscriptions"))),
    )

    /** Home inside a horizontal tab pager whose single page fills the screen. */
    fun youtubeHomeInsidePager() = FakeTree(
        YT, node(
            cls = FRAME,
            children = listOf(
                node(cls = "androidx.viewpager.widget.ViewPager", scrollable = true, bounds = b(0f, 0f, 1f, 0.93f), children = listOf(
                    node(bounds = b(0f, 0f, 1f, 0.93f), children = listOf(ytFeed(ytShortsShelfByShape(0.1f), ytVideoCard(0.52f, 0.85f)))),
                )),
                ytBottomBar(selected = "Home"),
            ),
        ),
    )

    /** Channel "Shorts" grid: each cell of the list is a tall card. */
    fun youtubeShortsGrid() = FakeTree(
        YT, node(
            cls = FRAME,
            children = listOf(
                node(cls = RECYCLER, scrollable = true, bounds = b(0f, 0.2f, 1f, 0.93f), rows = 10, cols = 3, children = listOf(
                    node(bounds = b(0f, 0.2f, 0.33f, 0.5f)), node(bounds = b(0.33f, 0.2f, 0.66f, 0.5f)), node(bounds = b(0.66f, 0.2f, 1f, 0.5f)),
                )),
            ),
        ),
    )

    fun youtubeAdPlaying(skipId: String? = "skip_ad_button", skipLabel: String? = null, adBadge: Boolean = true) = FakeTree(
        YT, node(
            cls = FRAME,
            children = listOfNotNull(
                node(id = yt("watch_player"), bounds = b(0f, 0.05f, 1f, 0.33f), children = listOfNotNull(
                    if (adBadge) node(id = yt("ad_progress_text"), text = "Anúncio · 0:12", bounds = b(0.02f, 0.29f, 0.3f, 0.32f)) else null,
                    if (skipId != null || skipLabel != null) {
                        node(id = skipId?.let(::yt), text = skipLabel, bounds = b(0.7f, 0.26f, 0.98f, 0.31f))
                    } else {
                        null
                    },
                )),
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

    private fun igPost(top: Float, mediaId: String, sponsored: Boolean = false): List<FakeNode> = listOf(
        node(id = ig("row_feed_profile_header"), bounds = b(0f, top, 1f, top + 0.06f), children = listOfNotNull(
            node(id = ig("row_feed_photo_profile_name"), bounds = b(0.15f, top + 0.01f, 0.6f, top + 0.03f)),
            if (sponsored) node(id = ig("secondary_label"), text = "Patrocinado", bounds = b(0.15f, top + 0.03f, 0.5f, top + 0.05f)) else null,
        )),
        node(id = ig(mediaId), bounds = b(0f, top + 0.06f, 1f, top + 0.5f)),
        node(id = ig("row_feed_view_group_buttons"), bounds = b(0f, top + 0.5f, 1f, top + 0.55f)),
    )

    /** Feed: the end of a photo post, a sponsored post and the start of a Reel post. */
    fun instagramFeedMixed() = FakeTree(
        IG, node(
            cls = FRAME,
            children = listOf(
                node(id = "android:id/list", cls = RECYCLER, scrollable = true, bounds = b(0f, 0.06f, 1f, 0.93f), rows = 40, cols = 1, children =
                    // Tail of a photo post whose header already scrolled away.
                    igPost(-0.3f, "row_feed_photo_imageview").drop(1) +
                        igPost(0.25f, "row_feed_photo_imageview", sponsored = true) +
                        igPost(0.8f, "clips_video_container").take(2),
                ),
                igTabBar(selected = "feed_tab"),
            ),
        ),
    )

    /** Suggested accounts carousel: tall cards side by side that are NOT Reels. */
    fun instagramSuggestedAccounts() = FakeTree(
        IG, node(
            cls = FRAME,
            children = listOf(
                node(id = "android:id/list", cls = RECYCLER, scrollable = true, bounds = b(0f, 0.06f, 1f, 0.93f), rows = 40, cols = 1, children = listOf(
                    node(bounds = b(0f, 0.1f, 1f, 0.5f), children = listOf(
                        node(bounds = b(0.02f, 0.12f, 0.47f, 0.48f)), node(bounds = b(0.53f, 0.12f, 0.98f, 0.48f)),
                    )),
                    node(bounds = b(0f, 0.5f, 1f, 0.9f)),
                )),
                igTabBar(selected = "feed_tab"),
            ),
        ),
    )

    fun instagramStoryAd(sponsored: Boolean = true) = FakeTree(
        IG, node(
            cls = FRAME,
            children = listOf(
                node(id = ig("reel_viewer_root"), bounds = b(0f, 0f, 1f, 1f), children = listOfNotNull(
                    node(id = ig("reel_viewer_media_layout"), bounds = b(0f, 0f, 1f, 0.92f)),
                    if (sponsored) node(id = ig("reel_viewer_subtitle"), text = "Patrocinado", bounds = b(0.15f, 0.04f, 0.4f, 0.06f)) else null,
                )),
            ),
        ),
    )

    /** A Reel shared in a conversation: stays visible. */
    fun instagramDirectWithReel() = FakeTree(
        IG, node(
            cls = FRAME,
            children = listOf(
                node(id = ig("direct_thread_list"), cls = RECYCLER, scrollable = true, bounds = b(0f, 0.08f, 1f, 0.9f), rows = 60, cols = 1, children = listOf(
                    node(bounds = b(0f, 0.1f, 1f, 0.5f), children = listOf(node(id = ig("clips_share_preview"), bounds = b(0.3f, 0.1f, 0.9f, 0.5f)))),
                )),
                node(id = ig("row_thread_composer_edittext"), cls = EDIT_TEXT, bounds = b(0f, 0.9f, 1f, 0.96f)),
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
