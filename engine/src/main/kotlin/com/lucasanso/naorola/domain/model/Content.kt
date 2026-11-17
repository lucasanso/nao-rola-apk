package com.lucasanso.naorola.domain.model

/** Platform that owns the short-video content (used for per-platform settings and stats). */
enum class Platform { YOUTUBE, INSTAGRAM, OTHER, ADULT }

/** Where short-video content was found. */
enum class Surface(
    val platform: Platform,
    val viaBrowser: Boolean = false,
) {
    /** YouTube Shorts full-screen vertical player. */
    SHORTS_PLAYER(Platform.YOUTUBE),

    /** Instagram Reels full-screen vertical viewer. */
    REELS_VIEWER(Platform.INSTAGRAM),

    /** youtube.com/shorts/… opened in a browser. */
    WEB_SHORTS(Platform.YOUTUBE, viaBrowser = true),

    /** instagram.com/reel(s)/… opened in a browser. */
    WEB_REELS(Platform.INSTAGRAM, viaBrowser = true),

    /** An app that is entirely short videos (TikTok, Kwai…). */
    SHORT_VIDEO_APP(Platform.OTHER),

    /** A pornographic site opened in a browser (adult filter). */
    ADULT_SITE(Platform.ADULT, viaBrowser = true),

    /** A search for explicit terms in a browser (adult filter). */
    ADULT_SEARCH(Platform.ADULT, viaBrowser = true),
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
