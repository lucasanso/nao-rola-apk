package com.lucasanso.naorola.domain.model

/** What happened when short-video content was found. No content is ever attached. */
enum class BlockOutcome {
    /** The block screen was shown (one "attempt blocked"). */
    BLOCKED,
    WENT_BACK,
    LEFT_APP,
    SNOOZED,

    /** A Reel opened from a DM was let through (the next one is blocked). */
    ALLOWED_FROM_DIRECT,

    /** Content found while tolerance is running. */
    TOLERANCE_STARTED,
}

data class BlockEvent(val timeMillis: Long, val surface: Surface, val outcome: BlockOutcome)

/** A continuous period with short-video content on screen that was NOT blocked. */
data class ShortVideoExposure(val surface: Surface, val startMillis: Long, val endMillis: Long) {
    val durationMillis: Long get() = (endMillis - startMillis).coerceAtLeast(0L)
}
