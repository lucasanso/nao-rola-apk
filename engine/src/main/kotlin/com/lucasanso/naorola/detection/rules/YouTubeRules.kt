package com.lucasanso.naorola.detection.rules

import com.lucasanso.naorola.detection.signal.Polarity.NEGATIVE
import com.lucasanso.naorola.detection.signal.Polarity.POSITIVE
import com.lucasanso.naorola.domain.model.ScreenContext

/**
 * YouTube app rules. Internally YouTube calls Shorts "reel".
 *
 * STATUS: the ids below are CANDIDATES gathered from public sources and must be confirmed with
 * the diagnostic mode on a real device. The structural pager rule does not depend on ids.
 */
object YouTubeRules {
    const val PACKAGE = "com.google.android.youtube"

    val INFO = RuleSetInfo(name = "YouTube", version = "2026.11.0", status = "candidatos, não confirmados em aparelho")

    const val SIGNAL_PLAYER_ID = "yt.shorts_player_id"
    const val SIGNAL_REEL_NAMESPACE = "yt.reel_namespace"
    const val SIGNAL_PAGER = "yt.fullscreen_vertical_pager"
    const val SIGNAL_TAB = "yt.shorts_tab_selected"
    const val SIGNAL_CONVENTIONAL_PLAYER = "yt.conventional_player"

    val SHORTS_PLAYER_IDS = setOf(
        "reel_recycler",
        "reel_player_page_container",
        "reel_watch_player",
        "reel_watch_fragment_root",
    )

    /** Ids of the regular watch page; must be large to count (the mini player is small). */
    val CONVENTIONAL_PLAYER_IDS = setOf(
        "watch_player",
        "youtube_controls_overlay",
        "next_gen_watch_layout",
    )

    /** "Shorts" is a brand name and is not translated in the tab label. */
    val SHORTS_TAB_LABELS = setOf("shorts")

    val rules: List<Rule> = listOf(
        AnyIdRule(SIGNAL_PLAYER_ID, POSITIVE, 45, SHORTS_PLAYER_IDS),
        IdPrefixRule(SIGNAL_REEL_NAMESPACE, POSITIVE, 20, prefix = "reel_", minDistinct = 2, exclude = SHORTS_PLAYER_IDS),
        FullScreenPagerRule(SIGNAL_PAGER, POSITIVE, 35),
        SelectedLabelRule(SIGNAL_TAB, POSITIVE, 25, SHORTS_TAB_LABELS),
        AnyIdRule(SIGNAL_CONVENTIONAL_PLAYER, NEGATIVE, 80, CONVENTIONAL_PLAYER_IDS, minHeight = 0.2f),
    )

    val contextRules: List<ContextRule> = listOf(
        ContextRule(ScreenContext.CONVENTIONAL_VIDEO, anyIds = CONVENTIONAL_PLAYER_IDS),
    )
}
