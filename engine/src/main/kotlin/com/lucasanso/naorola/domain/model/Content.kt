package com.lucasanso.naorola.domain.model

/** Platform that owns the short-video content (used for per-platform settings and stats). */
enum class Platform { YOUTUBE, INSTAGRAM, OTHER }

/** Where short-video content was found. */
enum class Surface(
    val platform: Platform,
    val viaBrowser: Boolean = false,
    val regionBased: Boolean = false,
) {
    /** YouTube Shorts full-screen vertical player. */
    SHORTS_PLAYER(Platform.YOUTUBE),

    /** Instagram Reels full-screen vertical viewer. */
    REELS_VIEWER(Platform.INSTAGRAM),

    /** A Reel inside Instagram's main feed (covered by region, experimental). */
    FEED_REEL_ITEM(Platform.INSTAGRAM, regionBased = true),

    /** youtube.com/shorts/… opened in a browser. */
    WEB_SHORTS(Platform.YOUTUBE, viaBrowser = true),

    /** instagram.com/reel(s)/… opened in a browser. */
    WEB_REELS(Platform.INSTAGRAM, viaBrowser = true),

    /** An app that is entirely short videos (TikTok, Kwai…). */
    SHORT_VIDEO_APP(Platform.OTHER),
}

/** Best guess of what the current screen is, used by policies (e.g. "Reel opened from a DM"). */
enum class ScreenContext {
    UNKNOWN,
    SHORT_VIDEO,
    CONVENTIONAL_VIDEO,
    STORIES,
    DIRECT_MESSAGES,
    FEED,
}
