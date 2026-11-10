package com.lucasanso.naorola.domain

import com.lucasanso.naorola.domain.model.DisciplineRules
import com.lucasanso.naorola.domain.model.ExceptionPeriod
import com.lucasanso.naorola.domain.model.ProtectionMode
import com.lucasanso.naorola.domain.model.ProtectionSettings
import com.lucasanso.naorola.domain.model.ScheduleWindow
import com.lucasanso.naorola.domain.model.Surface
import com.lucasanso.naorola.domain.policy.BlockingPolicy
import com.lucasanso.naorola.domain.policy.Discipline
import com.lucasanso.naorola.domain.policy.InactiveReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class BlockingPolicyTest {
    private val zone: ZoneId = ZoneOffset.UTC

    /** Wednesday 2026-11-11 14:00 UTC. */
    private val now = LocalDateTime.of(2026, 11, 11, 14, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val hour = 3_600_000L

    private fun policy(settings: ProtectionSettings, surface: Surface = Surface.SHORTS_PLAYER, at: Long = now) =
        BlockingPolicy.evaluate(settings, surface, at, zone)

    @Test fun defaultsProtectYouTubeAndInstagramButNotOptInSurfaces() {
        val s = ProtectionSettings()
        assertTrue(policy(s, Surface.SHORTS_PLAYER).protects)
        assertTrue(policy(s, Surface.REELS_VIEWER).protects)
        assertEquals(InactiveReason.PLATFORM_OFF, policy(s, Surface.WEB_SHORTS).inactiveReason)
        assertEquals(InactiveReason.PLATFORM_OFF, policy(s, Surface.FEED_REEL_ITEM).inactiveReason)
        assertEquals(InactiveReason.PLATFORM_OFF, policy(s, Surface.SHORT_VIDEO_APP).inactiveReason)
    }

    @Test fun platformSwitchesAreIndependent() {
        val s = ProtectionSettings(youtubeEnabled = false)
        assertFalse(policy(s, Surface.SHORTS_PLAYER).protects)
        assertTrue(policy(s, Surface.REELS_VIEWER).protects)
    }

    @Test fun browserNeedsBothBrowserAndPlatformSwitches() {
        assertTrue(policy(ProtectionSettings(browserEnabled = true), Surface.WEB_SHORTS).protects)
        assertFalse(policy(ProtectionSettings(browserEnabled = true, youtubeEnabled = false), Surface.WEB_SHORTS).protects)
    }

    @Test fun totalModeBlocksEverythingWithoutExceptions() {
        val s = ProtectionSettings(
            mode = ProtectionMode.TOTAL,
            youtubeEnabled = false, instagramEnabled = false, toleranceSeconds = 30,
            snoozedUntil = now + hour, allowReelFromDirect = true,
            activeWindows = listOf(ScheduleWindow(setOf(DayOfWeek.MONDAY), 0, 60)),
            exceptionPeriods = listOf(ExceptionPeriod(now - hour, now + hour)),
        )
        Surface.entries.forEach { surface ->
            val p = policy(s, surface)
            assertTrue("$surface", p.protects)
            assertEquals(0L, p.toleranceMillis)
            assertFalse(p.snoozeAllowed)
            assertFalse(p.allowReelFromDirect)
            assertTrue(p.totalMode)
        }
    }

    @Test fun toleranceAndSnoozeInPersonalizedMode() {
        val p = policy(ProtectionSettings(toleranceSeconds = 15, snoozeMinutes = 5))
        assertEquals(15_000L, p.toleranceMillis)
        assertTrue(p.snoozeAllowed)
        assertEquals(5 * 60_000L, p.snoozeMillis)
    }

    @Test fun snoozeTurnsProtectionOffUntilItEnds() {
        val s = ProtectionSettings(snoozedUntil = now + 1_000)
        assertEquals(InactiveReason.SNOOZED, policy(s).inactiveReason)
        assertTrue(policy(s, at = now + 1_000).protects)
    }

    @Test fun scheduleAndExceptions() {
        val weekdaysWork = ScheduleWindow(DayOfWeek.entries.take(5).toSet(), 9 * 60, 18 * 60)
        assertTrue(policy(ProtectionSettings(activeWindows = listOf(weekdaysWork))).protects)
        val evening = ScheduleWindow(DayOfWeek.entries.toSet(), 20 * 60, 23 * 60)
        assertEquals(InactiveReason.OUTSIDE_SCHEDULE, policy(ProtectionSettings(activeWindows = listOf(evening))).inactiveReason)
        val lunchBreak = ScheduleWindow(setOf(DayOfWeek.WEDNESDAY), 13 * 60, 15 * 60)
        assertEquals(InactiveReason.EXCEPTION, policy(ProtectionSettings(exceptionWindows = listOf(lunchBreak))).inactiveReason)
    }

    @Test fun disciplineAppliesItsRules() {
        val base = ProtectionSettings(
            youtubeEnabled = false, toleranceSeconds = 30, snoozedUntil = now + hour,
            exceptionPeriods = listOf(ExceptionPeriod(now - hour, now + hour)),
        )
        val s = Discipline.activate(base, now - 1, endsAt = null, exitDelayMinutes = 15, rules = DisciplineRules())
        val p = policy(s)
        assertTrue(p.protects) // forced on, exceptions suspended, snooze cleared
        assertEquals(0L, p.toleranceMillis)
        assertFalse(p.snoozeAllowed)
        assertTrue(p.allowReelFromDirect) // kept by default
        assertTrue(p.disciplineActive)
        assertFalse(p.totalMode)
    }

    @Test fun disciplineRulesAreConfigurable() {
        val rules = DisciplineRules(
            zeroTolerance = false, suspendExceptions = false, forceAllPlatforms = false, keepReelFromDirect = false,
        )
        val s = Discipline.activate(
            ProtectionSettings(toleranceSeconds = 10, instagramEnabled = false), now - 1, null, 15, rules,
        )
        assertEquals(10_000L, policy(s).toleranceMillis)
        assertFalse(policy(s, Surface.REELS_VIEWER).protects)
        assertFalse(policy(s).allowReelFromDirect)
    }

    @Test fun disciplineCanForceTotalMode() {
        val s = Discipline.activate(ProtectionSettings(), now - 1, null, 15, DisciplineRules(forceTotalMode = true))
        assertTrue(policy(s, Surface.SHORT_VIDEO_APP).protects)
        assertFalse(policy(s).allowReelFromDirect)
    }
}
