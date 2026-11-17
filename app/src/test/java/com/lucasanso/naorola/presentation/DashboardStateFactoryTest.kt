package com.lucasanso.naorola.presentation

import com.lucasanso.naorola.data.stats.TodaySummary
import com.lucasanso.naorola.detection.rules.InstagramRules
import com.lucasanso.naorola.detection.rules.YouTubeRules
import com.lucasanso.naorola.domain.model.Platform
import com.lucasanso.naorola.domain.model.ProtectionMode
import com.lucasanso.naorola.domain.model.ProtectionSettings
import com.lucasanso.naorola.monitoring.HealthSnapshot
import com.lucasanso.naorola.presentation.dashboard.DashboardStateFactory
import com.lucasanso.naorola.presentation.dashboard.PlatformKey
import com.lucasanso.naorola.presentation.dashboard.PlatformStatus
import com.lucasanso.naorola.presentation.dashboard.ProtectionStatus
import com.lucasanso.naorola.presentation.dashboard.UnprotectedReason
import com.lucasanso.naorola.presentation.dashboard.UsageState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class DashboardStateFactoryTest {
    private val now = 1_800_000_000_000L
    private val installed = setOf(YouTubeRules.PACKAGE, InstagramRules.PACKAGE)
    private val healthy = HealthSnapshot(
        accessibilityEnabled = true, usageAccessGranted = false, ignoringBatteryOptimizations = true,
        advancedProtectionOn = false, installedApps = installed,
    )

    private fun build(
        settings: ProtectionSettings = ProtectionSettings(),
        health: HealthSnapshot = healthy,
        connected: Boolean = true,
        today: TodaySummary = TodaySummary(),
        usage: Map<String, Long>? = null,
    ) = DashboardStateFactory.build(settings, health, connected, today, usage, now, ZoneOffset.UTC)

    private fun statusOf(state: com.lucasanso.naorola.presentation.dashboard.DashboardState, key: PlatformKey) =
        state.platforms.single { it.key == key }.status

    @Test fun activeWhenServiceEnabledAndRunning() {
        val state = build()
        assertEquals(ProtectionStatus.ACTIVE, state.status)
        assertEquals(PlatformStatus.Protected, statusOf(state, PlatformKey.YOUTUBE))
        assertEquals(PlatformStatus.Protected, statusOf(state, PlatformKey.INSTAGRAM))
    }

    @Test fun serviceDisabledLeavesEverythingUnprotected() {
        val state = build(health = healthy.copy(accessibilityEnabled = false), connected = false)
        assertEquals(ProtectionStatus.SERVICE_OFF, state.status)
        assertTrue(state.needsAttention)
        assertEquals(PlatformStatus.Unprotected(UnprotectedReason.SERVICE_OFF), statusOf(state, PlatformKey.YOUTUBE))
    }

    @Test fun enabledButNotRunningIsReported() {
        assertEquals(ProtectionStatus.SERVICE_NOT_RUNNING, build(connected = false).status)
    }

    @Test fun advancedProtectionTakesPriority() {
        assertEquals(ProtectionStatus.ADVANCED_PROTECTION, build(health = healthy.copy(advancedProtectionOn = true)).status)
    }

    @Test fun notInstalledAppsAreShownAsSuch() {
        val state = build(health = healthy.copy(installedApps = setOf(YouTubeRules.PACKAGE)))
        assertEquals(PlatformStatus.NotInstalled, statusOf(state, PlatformKey.INSTAGRAM))
    }

    @Test fun disabledPlatformAndPauseAreExplained() {
        val off = build(ProtectionSettings(youtubeEnabled = false))
        assertEquals(PlatformStatus.Unprotected(UnprotectedReason.PLATFORM_OFF), statusOf(off, PlatformKey.YOUTUBE))
        val paused = build(ProtectionSettings(snoozedUntil = now + 60_000))
        assertEquals(ProtectionStatus.PAUSED, paused.status)
        assertEquals(now + 60_000, paused.pausedUntil)
        assertEquals(PlatformStatus.Unprotected(UnprotectedReason.SNOOZED), statusOf(paused, PlatformKey.INSTAGRAM))
    }

    @Test fun totalModeShowsAllPlatforms() {
        val state = build(ProtectionSettings(mode = ProtectionMode.TOTAL, youtubeEnabled = false, snoozedUntil = now + 60_000))
        assertTrue(state.totalMode)
        assertEquals(ProtectionStatus.ACTIVE, state.status) // pause does not apply in total mode
        assertEquals(listOf(PlatformKey.YOUTUBE, PlatformKey.INSTAGRAM, PlatformKey.BROWSER, PlatformKey.OTHER_APPS), state.platforms.map { it.key })
        assertEquals(PlatformStatus.Protected, statusOf(state, PlatformKey.YOUTUBE))
    }

    @Test fun detectionSuspicionsAreShown() {
        val state = DashboardStateFactory.build(
            ProtectionSettings(), healthy, true, TodaySummary(), null, now, ZoneOffset.UTC, setOf(Platform.INSTAGRAM),
        )
        assertEquals(setOf(Platform.INSTAGRAM), state.suspectedDetection)
        assertTrue(build().suspectedDetection.isEmpty())
    }

    @Test fun countsAndUsageComeOnlyFromMeasuredData() {
        val today = TodaySummary(
            blockedByPlatform = mapOf(Platform.YOUTUBE to 3, Platform.INSTAGRAM to 2),
            exposureMillisByPlatform = mapOf(Platform.YOUTUBE to 90_000L),
        )
        val withoutPermission = build(today = today, usage = mapOf(YouTubeRules.PACKAGE to 1L))
        assertEquals(5, withoutPermission.blockedTotal)
        assertEquals(UsageState.NeedsPermission, withoutPermission.usage)
        assertEquals(90_000L, withoutPermission.shortVideoYoutubeMillis)

        val withPermission = build(health = healthy.copy(usageAccessGranted = true), usage = mapOf(YouTubeRules.PACKAGE to 600_000L))
        assertEquals(UsageState.Available(600_000L, 0L), withPermission.usage)
    }
}
