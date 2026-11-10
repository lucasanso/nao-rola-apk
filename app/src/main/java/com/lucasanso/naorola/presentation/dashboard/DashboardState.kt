package com.lucasanso.naorola.presentation.dashboard

import com.lucasanso.naorola.data.stats.TodaySummary
import com.lucasanso.naorola.detection.rules.BrowserRules
import com.lucasanso.naorola.detection.rules.InstagramRules
import com.lucasanso.naorola.detection.rules.ShortVideoApps
import com.lucasanso.naorola.detection.rules.YouTubeRules
import com.lucasanso.naorola.domain.model.Platform
import com.lucasanso.naorola.domain.model.ProtectionMode
import com.lucasanso.naorola.domain.model.ProtectionSettings
import com.lucasanso.naorola.domain.model.Surface
import com.lucasanso.naorola.domain.policy.BlockingPolicy
import com.lucasanso.naorola.domain.policy.InactiveReason
import com.lucasanso.naorola.monitoring.HealthSnapshot
import java.time.ZoneId

enum class ProtectionStatus { ACTIVE, PAUSED, SERVICE_OFF, SERVICE_NOT_RUNNING, ADVANCED_PROTECTION }

enum class PlatformKey { YOUTUBE, INSTAGRAM, BROWSER, OTHER_APPS }

enum class UnprotectedReason { PLATFORM_OFF, OUTSIDE_SCHEDULE, EXCEPTION, SNOOZED, SERVICE_OFF }

sealed interface PlatformStatus {
    data object Protected : PlatformStatus
    data class Unprotected(val reason: UnprotectedReason) : PlatformStatus
    data object NotInstalled : PlatformStatus
}

data class PlatformRow(val key: PlatformKey, val status: PlatformStatus)

sealed interface UsageState {
    data object NeedsPermission : UsageState
    data class Available(val youtubeMillis: Long, val instagramMillis: Long) : UsageState
}

data class DashboardState(
    val status: ProtectionStatus = ProtectionStatus.SERVICE_OFF,
    val pausedUntil: Long? = null,
    val totalMode: Boolean = false,
    val disciplineActive: Boolean = false,
    val platforms: List<PlatformRow> = emptyList(),
    val blockedYoutube: Int = 0,
    val blockedInstagram: Int = 0,
    val blockedOther: Int = 0,
    val usage: UsageState = UsageState.NeedsPermission,
    val shortVideoYoutubeMillis: Long = 0,
    val shortVideoInstagramMillis: Long = 0,
) {
    val blockedTotal: Int get() = blockedYoutube + blockedInstagram + blockedOther
    val needsAttention: Boolean get() = status != ProtectionStatus.ACTIVE && status != ProtectionStatus.PAUSED
}

/** Pure mapping from system facts + settings + stats to what the dashboard shows. */
object DashboardStateFactory {
    val PACKAGES_OF_INTEREST: Set<String> =
        setOf(YouTubeRules.PACKAGE, InstagramRules.PACKAGE) + BrowserRules.PACKAGES + ShortVideoApps.KNOWN.keys

    fun build(
        settings: ProtectionSettings,
        health: HealthSnapshot,
        serviceConnected: Boolean,
        today: TodaySummary,
        usageByPackage: Map<String, Long>?,
        now: Long,
        zone: ZoneId,
    ): DashboardState {
        val discipline = settings.discipline.isActive(now)
        val total = settings.mode == ProtectionMode.TOTAL || (discipline && settings.discipline.rules.forceTotalMode)
        val status = when {
            health.advancedProtectionOn == true -> ProtectionStatus.ADVANCED_PROTECTION
            !health.accessibilityEnabled -> ProtectionStatus.SERVICE_OFF
            !serviceConnected -> ProtectionStatus.SERVICE_NOT_RUNNING
            !total && !discipline && now < settings.snoozedUntil -> ProtectionStatus.PAUSED
            else -> ProtectionStatus.ACTIVE
        }
        val running = status == ProtectionStatus.ACTIVE || status == ProtectionStatus.PAUSED

        fun row(key: PlatformKey, packages: Set<String>, surface: Surface): PlatformRow {
            if (packages.none { it in health.installedApps }) return PlatformRow(key, PlatformStatus.NotInstalled)
            if (!running) return PlatformRow(key, PlatformStatus.Unprotected(UnprotectedReason.SERVICE_OFF))
            val policy = BlockingPolicy.evaluate(settings, surface, now, zone)
            val reason = when (policy.inactiveReason) {
                null -> return PlatformRow(key, PlatformStatus.Protected)
                InactiveReason.PLATFORM_OFF -> UnprotectedReason.PLATFORM_OFF
                InactiveReason.OUTSIDE_SCHEDULE -> UnprotectedReason.OUTSIDE_SCHEDULE
                InactiveReason.EXCEPTION -> UnprotectedReason.EXCEPTION
                InactiveReason.SNOOZED -> UnprotectedReason.SNOOZED
            }
            return PlatformRow(key, PlatformStatus.Unprotected(reason))
        }

        val rows = buildList {
            add(row(PlatformKey.YOUTUBE, setOf(YouTubeRules.PACKAGE), Surface.SHORTS_PLAYER))
            add(row(PlatformKey.INSTAGRAM, setOf(InstagramRules.PACKAGE), Surface.REELS_VIEWER))
            if (total || settings.browserEnabled) add(row(PlatformKey.BROWSER, BrowserRules.PACKAGES, Surface.WEB_SHORTS))
            if (total || settings.otherAppsEnabled) {
                add(row(PlatformKey.OTHER_APPS, ShortVideoApps.KNOWN.keys, Surface.SHORT_VIDEO_APP))
            }
        }

        return DashboardState(
            status = status,
            pausedUntil = settings.snoozedUntil.takeIf { status == ProtectionStatus.PAUSED },
            totalMode = total,
            disciplineActive = discipline,
            platforms = rows,
            blockedYoutube = today.blockedByPlatform[Platform.YOUTUBE] ?: 0,
            blockedInstagram = today.blockedByPlatform[Platform.INSTAGRAM] ?: 0,
            blockedOther = today.blockedByPlatform[Platform.OTHER] ?: 0,
            usage = if (health.usageAccessGranted && usageByPackage != null) {
                UsageState.Available(
                    youtubeMillis = usageByPackage[YouTubeRules.PACKAGE] ?: 0L,
                    instagramMillis = usageByPackage[InstagramRules.PACKAGE] ?: 0L,
                )
            } else {
                UsageState.NeedsPermission
            },
            shortVideoYoutubeMillis = today.exposureMillisByPlatform[Platform.YOUTUBE] ?: 0L,
            shortVideoInstagramMillis = today.exposureMillisByPlatform[Platform.INSTAGRAM] ?: 0L,
        )
    }
}
