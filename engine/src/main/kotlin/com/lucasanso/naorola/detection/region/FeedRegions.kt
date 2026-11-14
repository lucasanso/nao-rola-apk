package com.lucasanso.naorola.detection.region

import com.lucasanso.naorola.detection.DetectionConfig
import com.lucasanso.naorola.detection.tree.RelBounds
import com.lucasanso.naorola.detection.tree.ScreenSummary

/** Geometry shared by the app-specific region finders. */
object FeedRegions {

    /** [bounds] limited to the visible list; null when what is left is too small to matter. */
    fun clip(bounds: RelBounds, container: RelBounds): RelBounds? =
        bounds.intersect(container).takeIf { !it.isEmpty && it.height >= DetectionConfig.MIN_REGION_HEIGHT }

    fun union(bounds: List<RelBounds>): RelBounds = RelBounds(
        bounds.minOf { it.left },
        bounds.minOf { it.top },
        bounds.maxOf { it.right },
        bounds.maxOf { it.bottom },
    )

    /** A button of the bottom navigation bar: low on the screen and not tall. */
    fun isNavButton(b: RelBounds): Boolean =
        !b.isEmpty && b.top >= DetectionConfig.NAV_MIN_TOP && b.height <= DetectionConfig.NAV_MAX_HEIGHT

    /** The tallest node remembered under [key] that sits in the bottom bar (whole button, not just its text). */
    fun navButton(summary: ScreenSummary, key: String): RelBounds? =
        summary.keyedLabels[key].orEmpty().map { it.bounds }.filter(::isNavButton).maxByOrNull { it.height }
}
