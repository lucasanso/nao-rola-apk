package com.lucasanso.naorola.accessibility

import android.view.accessibility.AccessibilityEvent

/** Event categories that matter for scheduling an evaluation. */
enum class EventKind {
    WINDOW_STATE,
    CONTENT_CHANGED,
    SCROLLED,
    INTERACTION,
    OTHER,
    ;

    companion object {
        fun of(eventType: Int): EventKind = when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> WINDOW_STATE
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> CONTENT_CHANGED
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> SCROLLED
            AccessibilityEvent.TYPE_VIEW_CLICKED, AccessibilityEvent.TYPE_VIEW_SELECTED -> INTERACTION
            else -> OTHER
        }
    }
}

/**
 * Turns bursts of events into few evaluations: one evaluation pending at a time (it reads the
 * latest screen anyway), a short debounce, and a longer minimum interval for content changes,
 * which fire continuously while a video plays.
 */
class EvaluationThrottle(
    private val debounceMillis: Long = DEBOUNCE_MILLIS,
    private val contentMinIntervalMillis: Long = CONTENT_MIN_INTERVAL_MILLIS,
) {
    private var pending = false
    private var lastEvaluationAt = Long.MIN_VALUE / 2

    /** Delay before evaluating, or null when an evaluation is already scheduled. */
    @Synchronized
    fun onEvent(kind: EventKind, now: Long): Long? {
        if (pending) return null
        pending = true
        return when (kind) {
            EventKind.CONTENT_CHANGED, EventKind.OTHER ->
                maxOf(debounceMillis, lastEvaluationAt + contentMinIntervalMillis - now)
            else -> debounceMillis
        }
    }

    @Synchronized
    fun onEvaluated(now: Long) {
        pending = false
        lastEvaluationAt = now
    }

    companion object {
        const val DEBOUNCE_MILLIS = 120L
        const val CONTENT_MIN_INTERVAL_MILLIS = 400L
    }
}
