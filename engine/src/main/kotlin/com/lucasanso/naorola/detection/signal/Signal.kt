package com.lucasanso.naorola.detection.signal

import com.lucasanso.naorola.detection.tree.RelBounds
import com.lucasanso.naorola.domain.model.ScreenContext
import com.lucasanso.naorola.domain.model.Surface

enum class Polarity { POSITIVE, NEGATIVE }

/** One piece of evidence, e.g. "Shorts player id present" (+45) or "Stories viewer" (−100). */
data class Signal(val id: String, val polarity: Polarity, val weight: Int)

/** What a detector found on one screen. The classifier turns this into a decision. */
data class DetectionResult(
    val detectorId: String,
    /** Surface this screen would be if the evidence is strong enough; null = nothing to consider. */
    val surface: Surface?,
    val signals: List<Signal>,
    val context: ScreenContext,
    /** Identity of the current page in a short-video pager (changes when the user swipes). */
    val pageKey: Int? = null,
    val regions: List<RelBounds> = emptyList(),
    val truncated: Boolean = false,
)
