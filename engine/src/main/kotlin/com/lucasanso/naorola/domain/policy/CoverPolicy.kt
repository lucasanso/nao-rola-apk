package com.lucasanso.naorola.domain.policy

import com.lucasanso.naorola.domain.model.Platform
import com.lucasanso.naorola.domain.model.ProtectionSettings
import com.lucasanso.naorola.domain.model.Surface
import java.time.ZoneId

/** Kinds of things that can be hidden inside an app without leaving it. */
enum class CoverKind { SHORT_VIDEO, AD }

/**
 * Whether a part of a screen should be covered. Short-video covers follow the same switches,
 * schedules and pauses as the block of that platform (a paused block shows no covers either),
 * plus "Hide in feeds" — which Total mode and Discipline's "all platforms" turn on regardless.
 * Ad covers depend only on the ad-block switch.
 */
object CoverPolicy {

    fun shows(settings: ProtectionSettings, kind: CoverKind, platform: Platform, now: Long, zone: ZoneId): Boolean {
        if (kind == CoverKind.AD) return settings.adBlockEnabled && platform != Platform.OTHER
        val surface = when (platform) {
            Platform.YOUTUBE -> Surface.SHORTS_PLAYER
            Platform.INSTAGRAM -> Surface.REELS_VIEWER
            Platform.OTHER -> return false
        }
        val policy = BlockingPolicy.evaluate(settings, surface, now, zone)
        val forced = policy.totalMode || (policy.disciplineActive && settings.discipline.rules.forceAllPlatforms)
        return policy.protects && (settings.hideInFeeds || forced)
    }
}
