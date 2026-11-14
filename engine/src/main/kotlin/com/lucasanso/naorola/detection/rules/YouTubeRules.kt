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
    private const val PLAYER_MIN_HEIGHT = 0.6f

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

    /** Shorts-related views are named "reel_…" internally. */
    const val SHORTS_ID_PREFIX = "reel_"

    /** "Skip ad" button of the player, older and newer layouts (candidates). */
    val SKIP_AD_IDS = setOf("skip_ad_button", "modern_skip_ad_text", "skip_ad_button_text", "skip_ad_button_container")

    /** Elements shown only while an ad plays (candidates). */
    val AD_PLAYER_IDS = setOf("ad_progress_text", "ad_badge", "ad_text", "ad_countdown", "player_learn_more_button", "visit_advertiser")

    val rules: List<Rule> = listOf(
        AnyIdRule(SIGNAL_PLAYER_ID, POSITIVE, 45, SHORTS_PLAYER_IDS, minHeight = PLAYER_MIN_HEIGHT),
        IdPrefixRule(SIGNAL_REEL_NAMESPACE, POSITIVE, 20, prefix = "reel_", minDistinct = 2, exclude = SHORTS_PLAYER_IDS),
        FullScreenPagerRule(SIGNAL_PAGER, POSITIVE, 35),
        SelectedLabelRule(SIGNAL_TAB, POSITIVE, 25, SHORTS_TAB_LABELS),
        AnyIdRule(SIGNAL_CONVENTIONAL_PLAYER, NEGATIVE, 80, CONVENTIONAL_PLAYER_IDS, minHeight = 0.2f),
    )

    val contextRules: List<ContextRule> = listOf(
        ContextRule(ScreenContext.CONVENTIONAL_VIDEO, anyIds = CONVENTIONAL_PLAYER_IDS),
    )
}
