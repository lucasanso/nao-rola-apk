package com.lucasanso.naorola.detection.tree

/**
 * Bounds as fractions of the screen (0..1 on each axis), so rules work on any resolution.
 */
data class RelBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val isEmpty: Boolean get() = width <= 0f || height <= 0f
    val centerX: Float get() = (left + right) / 2f

    fun intersect(other: RelBounds): RelBounds = RelBounds(
        maxOf(left, other.left),
        maxOf(top, other.top),
        minOf(right, other.right),
        minOf(bottom, other.bottom),
    ).let { if (it.isEmpty) EMPTY else it }

    fun intersects(other: RelBounds): Boolean = !intersect(other).isEmpty

    fun verticalOverlap(other: RelBounds): Float = (minOf(bottom, other.bottom) - maxOf(top, other.top)).coerceAtLeast(0f)

    companion object {
        val EMPTY = RelBounds(0f, 0f, 0f, 0f)
        val FULL = RelBounds(0f, 0f, 1f, 1f)

        fun fromPixels(left: Int, top: Int, right: Int, bottom: Int, screenWidth: Int, screenHeight: Int): RelBounds {
            if (screenWidth <= 0 || screenHeight <= 0) return EMPTY
            fun fx(v: Int) = (v.toFloat() / screenWidth).coerceIn(0f, 1f)
            fun fy(v: Int) = (v.toFloat() / screenHeight).coerceIn(0f, 1f)
            return RelBounds(fx(left), fy(top), fx(right), fy(bottom))
        }
    }
}

/**
 * Read-only view of one element on screen. The Android implementation reads lazily from
 * AccessibilityNodeInfo; tests use in-memory nodes. Text and descriptions are only read when
 * a rule needs them and are never stored.
 */
interface UiNode {
    val className: String?

    /** Fully qualified resource name, e.g. "com.google.android.youtube:id/reel_recycler". */
    val viewId: String?
    val bounds: RelBounds
    val isScrollable: Boolean
    val isSelected: Boolean
    val isVisibleToUser: Boolean
    val text: CharSequence?
    val contentDescription: CharSequence?

    /** Collection info exposed by lists/pagers, or null. A vertical list has 1 column. */
    val collectionRowCount: Int?
    val collectionColumnCount: Int?

    /** Identity of the underlying view while it stays on screen (used to notice page changes). */
    val identityKey: Int
    val childCount: Int
    fun child(index: Int): UiNode?

    /**
     * Presses this element (or its nearest clickable ancestor) on the user's behalf, e.g. the
     * "Skip ad" button. Only real screens can do it; recorded/synthetic trees return false.
     */
    fun click(): Boolean = false
}

/** The screen of one app at one moment. */
interface UiTree {
    val packageName: String
    val root: UiNode?

    /** Height / width of the app window, to judge real shapes from relative bounds. */
    val aspectRatio: Float get() = DEFAULT_ASPECT_RATIO

    companion object {
        /** A 1080 × 2340 phone screen. */
        const val DEFAULT_ASPECT_RATIO = 2340f / 1080f
    }
}
