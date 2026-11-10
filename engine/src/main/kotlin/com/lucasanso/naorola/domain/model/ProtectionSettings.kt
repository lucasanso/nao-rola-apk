package com.lucasanso.naorola.domain.model

import java.time.DayOfWeek

/** PERSONALIZED follows each option; TOTAL blocks everything detectable, with no exceptions. */
enum class ProtectionMode { PERSONALIZED, TOTAL }

/**
 * Everything the user configures. Pure data: persistence lives in the app module, rules in
 * [com.lucasanso.naorola.domain.policy.BlockingPolicy].
 */
data class ProtectionSettings(
    val mode: ProtectionMode = ProtectionMode.PERSONALIZED,
    val youtubeEnabled: Boolean = true,
    val instagramEnabled: Boolean = true,
    /** Read the address bar of Chrome/Samsung Internet. Opt-in because it touches browsing. */
    val browserEnabled: Boolean = false,
    /** Cover Reels inside the Instagram feed. Experimental, pending calibration. */
    val feedReelsEnabled: Boolean = false,
    /** Block apps that are only short videos (TikTok, Kwai…). */
    val otherAppsEnabled: Boolean = false,
    val toleranceSeconds: Int = 0,
    val snoozeAllowed: Boolean = true,
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    /** Epoch millis until which blocking is paused ("Desativar temporariamente"); 0 = not paused. */
    val snoozedUntil: Long = 0L,
    /** Let a Reel opened from a DM play; block when swiping to the next one. */
    val allowReelFromDirect: Boolean = true,
    /** When protection runs. Empty = always. */
    val activeWindows: List<ScheduleWindow> = emptyList(),
    /** Recurring windows when protection is off (e.g. Saturday evening). */
    val exceptionWindows: List<ScheduleWindow> = emptyList(),
    /** One-off periods when protection is off. */
    val exceptionPeriods: List<ExceptionPeriod> = emptyList(),
    val discipline: DisciplineState = DisciplineState(),
) {
    companion object {
        const val DEFAULT_SNOOZE_MINUTES = 10
        val TOLERANCE_CHOICES_SECONDS = listOf(0, 5, 10, 15, 30, 60)
        val SNOOZE_CHOICES_MINUTES = listOf(5, 10, 15, 30)
    }
}

/**
 * A weekly window: [startMinute, endMinute) minutes of the day on [days]. When end <= start the
 * window crosses midnight and continues into the next day.
 */
data class ScheduleWindow(val days: Set<DayOfWeek>, val startMinute: Int, val endMinute: Int) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY) { "startMinute fora do dia: $startMinute" }
        require(endMinute in 1..MINUTES_PER_DAY) { "endMinute fora do dia: $endMinute" }
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}

data class ExceptionPeriod(val startMillis: Long, val endMillis: Long) {
    init {
        require(endMillis > startMillis) { "Período de exceção vazio" }
    }
}

/** Rules chosen when Discipline mode is activated. Defaults follow the agreed proposal. */
data class DisciplineRules(
    val zeroTolerance: Boolean = true,
    val suspendExceptions: Boolean = true,
    val forceAllPlatforms: Boolean = true,
    val keepReelFromDirect: Boolean = true,
    val forceTotalMode: Boolean = false,
)

data class DisciplineState(
    val active: Boolean = false,
    val startedAt: Long = 0L,
    /** Automatic end; null = until the user leaves through the app. */
    val endsAt: Long? = null,
    val exitDelayMinutes: Int = DEFAULT_EXIT_DELAY_MINUTES,
    /** When the user asked to leave; protection stays on until the delay passes and they confirm. */
    val exitRequestedAt: Long? = null,
    val rules: DisciplineRules = DisciplineRules(),
) {
    fun isActive(now: Long): Boolean = active && (endsAt == null || now < endsAt)

    fun exitReadyAt(): Long? = exitRequestedAt?.let { it + exitDelayMinutes * MILLIS_PER_MINUTE }

    fun canConfirmExit(now: Long): Boolean = exitReadyAt()?.let { now >= it } == true

    companion object {
        const val DEFAULT_EXIT_DELAY_MINUTES = 15
        val EXIT_DELAY_CHOICES_MINUTES = listOf(5, 15, 30, 60)
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
