package com.lucasanso.naorola.detection.signal

import com.lucasanso.naorola.detection.tree.RelBounds
import com.lucasanso.naorola.detection.tree.UiNode
import com.lucasanso.naorola.domain.model.Platform
import com.lucasanso.naorola.domain.model.ScreenContext
import com.lucasanso.naorola.domain.model.Surface
import com.lucasanso.naorola.domain.policy.CoverKind

enum class Polarity { POSITIVE, NEGATIVE }

/** One piece of evidence, e.g. "Shorts player id present" (+45) or "Stories viewer" (−100). */
data class Signal(val id: String, val polarity: Polarity, val weight: Int)

/** What a part of the screen is, when it can be hidden without leaving the app. */
enum class RegionKind(val coverKind: CoverKind) {
    /** Shorts shelf or Reels carousel inside a feed. */
    SHORT_VIDEO_SHELF(CoverKind.SHORT_VIDEO),

    /** The "Shorts"/"Reels" button of the bottom bar. */
    SHORT_VIDEO_TAB(CoverKind.SHORT_VIDEO),

    /** A single Reel/Short post inside a feed. */
    SHORT_VIDEO_FEED_ITEM(CoverKind.SHORT_VIDEO),

    /** A sponsored post, story or card. */
    AD(CoverKind.AD),

    /** YouTube's Home feed (recommendations). */
    HOME_FEED(CoverKind.HOME_FEED),
}

/**
 * A part of the screen to cover, in fractions of the app window. [container] is the list it
 * belongs to: the cover moves with that list's scrolling and never leaves it. Null = fixed.
 */
data class Region(
    val bounds: RelBounds,
    val kind: RegionKind,
    val platform: Platform,
    val container: RelBounds? = null,
)

enum class ActionKind { SKIP_AD }

/** Something the app may press for the user (e.g. "Skip ad"). Lives only for one evaluation. */
class UiAction(val kind: ActionKind, val target: UiNode) {
    override fun toString() = "UiAction($kind)"
}

/** What a detector found on one screen. The classifier turns this into a decision. */
data class DetectionResult(
    val detectorId: String,
    /** Surface this screen would be if the evidence is strong enough; null = nothing to consider. */
    val surface: Surface?,
    val signals: List<Signal>,
    val context: ScreenContext,
    /** Identity of the current page in a short-video pager (changes when the user swipes). */
    val pageKey: Int? = null,
    /** Parts of a screen that is NOT a short-video player which may be covered. */
    val regions: List<Region> = emptyList(),
    val actions: List<UiAction> = emptyList(),
    val truncated: Boolean = false,
)
