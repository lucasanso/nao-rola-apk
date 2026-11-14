package com.lucasanso.naorola.domain

import com.lucasanso.naorola.domain.model.DisciplineRules
import com.lucasanso.naorola.domain.model.DisciplineState
import com.lucasanso.naorola.domain.model.Platform
import com.lucasanso.naorola.domain.model.ProtectionMode
import com.lucasanso.naorola.domain.model.ProtectionSettings
import com.lucasanso.naorola.domain.policy.CoverKind
import com.lucasanso.naorola.domain.policy.CoverPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class CoverPolicyTest {
    private val now = 1_800_000_000_000L
    private val zone = ZoneOffset.UTC

    private fun shows(s: ProtectionSettings, kind: CoverKind, platform: Platform = Platform.YOUTUBE) =
        CoverPolicy.shows(s, kind, platform, now, zone)

    @Test fun defaultsCoverShortVideoAndAdsOnBothApps() {
        val s = ProtectionSettings()
        assertTrue(shows(s, CoverKind.SHORT_VIDEO, Platform.YOUTUBE))
        assertTrue(shows(s, CoverKind.SHORT_VIDEO, Platform.INSTAGRAM))
        assertTrue(shows(s, CoverKind.AD, Platform.INSTAGRAM))
        assertFalse(shows(s, CoverKind.SHORT_VIDEO, Platform.OTHER))
    }

    @Test fun hideInFeedsSwitch() {
        assertFalse(shows(ProtectionSettings(hideInFeeds = false), CoverKind.SHORT_VIDEO))
    }

    @Test fun followsPlatformSwitchAndPause() {
        assertFalse(shows(ProtectionSettings(youtubeEnabled = false), CoverKind.SHORT_VIDEO))
        assertTrue(shows(ProtectionSettings(youtubeEnabled = false), CoverKind.SHORT_VIDEO, Platform.INSTAGRAM))
        assertFalse(shows(ProtectionSettings(snoozedUntil = now + 60_000), CoverKind.SHORT_VIDEO))
    }

    @Test fun totalModeAndDisciplineForceCovers() {
        assertTrue(shows(ProtectionSettings(mode = ProtectionMode.TOTAL, hideInFeeds = false, youtubeEnabled = false), CoverKind.SHORT_VIDEO))
        val discipline = DisciplineState(active = true, startedAt = now - 1, rules = DisciplineRules(forceAllPlatforms = true))
        assertTrue(shows(ProtectionSettings(hideInFeeds = false, discipline = discipline), CoverKind.SHORT_VIDEO))
    }

    @Test fun adsDependOnlyOnTheirSwitch() {
        assertTrue(shows(ProtectionSettings(snoozedUntil = now + 60_000, youtubeEnabled = false), CoverKind.AD))
        assertFalse(shows(ProtectionSettings(adBlockEnabled = false), CoverKind.AD))
    }
}
