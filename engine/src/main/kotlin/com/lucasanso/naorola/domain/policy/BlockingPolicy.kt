package com.lucasanso.naorola.domain.policy

import com.lucasanso.naorola.domain.model.DisciplineState
import com.lucasanso.naorola.domain.model.ProtectionMode
import com.lucasanso.naorola.domain.model.ProtectionSettings
import com.lucasanso.naorola.domain.model.Surface
import java.time.Instant
import java.time.ZoneId

/** Why a surface is not being blocked right now (shown in the app). */
enum class InactiveReason { PLATFORM_OFF, OUTSIDE_SCHEDULE, EXCEPTION, SNOOZED }

/** The rules that apply to one surface at one moment, after modes and Discipline are applied. */
data class EffectivePolicy(
    val protects: Boolean,
    val inactiveReason: InactiveReason?,
    /**
     * How long short-video content may stay on screen in this entry before the block:
     * the smaller of the per-entry tolerance and what is left of the daily limit.
     */
    val allowanceMillis: Long,
    /** Daily limit in effect (null = none) and what is left of it today. */
    val dailyLimitMillis: Long?,
    val dailyRemainingMillis: Long?,
    val snoozeAllowed: Boolean,
    val snoozeMillis: Long,
    val allowReelFromDirect: Boolean,
    val totalMode: Boolean,
    val disciplineActive: Boolean,
)

object BlockingPolicy {

    /**
     * @param usedTodayMillis short-video time already allowed today on this surface's platform
     *   (counts towards the daily limit).
     */
    fun evaluate(
        settings: ProtectionSettings,
        surface: Surface,
        now: Long,
        zone: ZoneId,
        usedTodayMillis: Long = 0L,
    ): EffectivePolicy {
        val discipline = settings.discipline.isActive(now)
        val rules = settings.discipline.rules
        val total = settings.mode == ProtectionMode.TOTAL || (discipline && rules.forceTotalMode)
        val forceAll = total || (discipline && rules.forceAllPlatforms)
        val ignoreExceptions = total || (discipline && rules.suspendExceptions)
        val localTime = Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime()

        val reason = when {
            !forceAll && !surfaceEnabled(settings, surface) -> InactiveReason.PLATFORM_OFF
            !total && !ScheduleEvaluator.withinActiveWindows(settings.activeWindows, localTime) -> InactiveReason.OUTSIDE_SCHEDULE
            !ignoreExceptions && ScheduleEvaluator.inException(settings.exceptionWindows, settings.exceptionPeriods, localTime, now) ->
                InactiveReason.EXCEPTION
            !total && !discipline && now < settings.snoozedUntil -> InactiveReason.SNOOZED
            else -> null
        }

        val noFreeTime = total || (discipline && rules.zeroTolerance)
        val perEntry: Long? = when {
            noFreeTime -> 0L
            settings.toleranceSeconds == ProtectionSettings.UNLIMITED_TOLERANCE -> null
            else -> settings.toleranceSeconds.coerceAtLeast(0) * MILLIS_PER_SECOND
        }
        val dailyLimit: Long? = if (noFreeTime || settings.dailyLimitMinutes <= 0) {
            null
        } else {
            settings.dailyLimitMinutes * DisciplineState.MILLIS_PER_MINUTE
        }
        val dailyRemaining = dailyLimit?.let { (it - usedTodayMillis).coerceAtLeast(0L) }
        val allowance = when {
            // No cap at all would mean "never block": treat it as no free time instead.
            perEntry == null && dailyRemaining == null -> 0L
            perEntry == null -> dailyRemaining!!
            dailyRemaining == null -> perEntry
            else -> minOf(perEntry, dailyRemaining)
        }
        return EffectivePolicy(
            protects = reason == null,
            inactiveReason = reason,
            allowanceMillis = allowance,
            dailyLimitMillis = dailyLimit,
            dailyRemainingMillis = dailyRemaining,
            snoozeAllowed = !total && !discipline && settings.snoozeAllowed,
            snoozeMillis = settings.snoozeMinutes.coerceAtLeast(1) * DisciplineState.MILLIS_PER_MINUTE,
            allowReelFromDirect = !total && settings.allowReelFromDirect && (!discipline || rules.keepReelFromDirect),
            totalMode = total,
            disciplineActive = discipline,
        )
    }

    /** Whether the user's own switches cover this surface (before modes are applied). */
    fun surfaceEnabled(settings: ProtectionSettings, surface: Surface): Boolean = when (surface) {
        Surface.SHORTS_PLAYER -> settings.youtubeEnabled
        Surface.REELS_VIEWER -> settings.instagramEnabled
        Surface.FEED_REEL_ITEM -> settings.instagramEnabled && settings.feedReelsEnabled
        Surface.WEB_SHORTS -> settings.browserEnabled && settings.youtubeEnabled
        Surface.WEB_REELS -> settings.browserEnabled && settings.instagramEnabled
        Surface.SHORT_VIDEO_APP -> settings.otherAppsEnabled
    }

    private const val MILLIS_PER_SECOND = 1_000L
}
