package com.lucasanso.naorola.detection.health

import com.lucasanso.naorola.detection.DetectionConfig
import com.lucasanso.naorola.detection.classifier.Classification
import com.lucasanso.naorola.detection.rules.InstagramRules
import com.lucasanso.naorola.detection.rules.YouTubeRules
import com.lucasanso.naorola.detection.signal.DetectionResult
import com.lucasanso.naorola.domain.model.Platform
import java.util.EnumMap
import java.util.EnumSet

sealed interface HealthEvent {
    val platform: Platform

    /** The user stayed on the short-video tab but the player was not recognised. */
    data class Suspected(override val platform: Platform, val sinceMillis: Long) : HealthEvent

    /** The player was recognised again after a suspicion. */
    data class Recovered(override val platform: Platform) : HealthEvent
}

/**
 * Notices when an app update probably broke detection. The navigation signal ("Shorts/Reels tab
 * is selected") is independent from the player signals, so "tab selected for a while, player
 * never recognised" is strong evidence that the player rules are outdated.
 *
 * Screens rejected on purpose (a veto such as Stories or a regular video) never count.
 */
class DetectionHealthMonitor(
    private val minDurationMillis: Long = DetectionConfig.SUSPECT_MIN_MILLIS,
    private val minObservations: Int = DetectionConfig.SUSPECT_MIN_OBSERVATIONS,
) {
    private val streakStart = EnumMap<Platform, Long>(Platform::class.java)
    private val streakCount = EnumMap<Platform, Int>(Platform::class.java)
    private val suspected = EnumSet.noneOf(Platform::class.java)

    /** Restores suspicions saved earlier, so a later recognition can clear them. */
    @Synchronized
    fun seed(platforms: Set<Platform>) {
        suspected.clear()
        suspected.addAll(platforms)
    }

    @Synchronized
    fun observe(time: Long, detection: DetectionResult, classification: Classification): HealthEvent? {
        val surface = detection.surface ?: return null
        if (surface.viaBrowser || surface.platform == Platform.OTHER) return null
        val platform = surface.platform
        return when {
            classification is Classification.ShortVideo -> {
                reset(platform)
                if (suspected.remove(platform)) HealthEvent.Recovered(platform) else null
            }
            classification is Classification.NotShortVideo &&
                classification.vetoedBy == null &&
                detection.signals.any { it.id in NAVIGATION_SIGNALS } -> {
                val start = streakStart.getOrPut(platform) { time }
                val count = (streakCount[platform] ?: 0) + 1
                streakCount[platform] = count
                if (platform !in suspected && count >= minObservations && time - start >= minDurationMillis) {
                    suspected += platform
                    HealthEvent.Suspected(platform, start)
                } else {
                    null
                }
            }
            else -> {
                reset(platform)
                null
            }
        }
    }

    private fun reset(platform: Platform) {
        streakStart.remove(platform)
        streakCount.remove(platform)
    }

    companion object {
        val NAVIGATION_SIGNALS = setOf(YouTubeRules.SIGNAL_TAB, InstagramRules.SIGNAL_TAB)
    }
}
