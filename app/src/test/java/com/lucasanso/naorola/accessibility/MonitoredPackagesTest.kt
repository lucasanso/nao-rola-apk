package com.lucasanso.naorola.accessibility

import com.lucasanso.naorola.detection.rules.BrowserRules
import com.lucasanso.naorola.detection.rules.InstagramRules
import com.lucasanso.naorola.detection.rules.YouTubeRules
import com.lucasanso.naorola.domain.model.DisciplineRules
import com.lucasanso.naorola.domain.model.ProtectionMode
import com.lucasanso.naorola.domain.model.ProtectionSettings
import com.lucasanso.naorola.domain.policy.Discipline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoredPackagesTest {
    private val own = "com.lucasanso.naorola"

    @Test fun defaultsObserveOnlyYouTubeAndInstagram() {
        assertEquals(
            setOf(YouTubeRules.PACKAGE, InstagramRules.PACKAGE),
            MonitoredPackages.compute(ProtectionSettings(), 0, diagnosticArmed = false, ownPackage = own),
        )
    }

    @Test fun browsersAreObservedOnlyWhenEnabled() {
        val packages = MonitoredPackages.compute(ProtectionSettings(browserEnabled = true), 0, false, own)
        assertTrue(packages.containsAll(BrowserRules.PACKAGES))
    }

    @Test fun nothingEnabledNeverMeansEveryApp() {
        val s = ProtectionSettings(youtubeEnabled = false, instagramEnabled = false)
        assertEquals(setOf(own), MonitoredPackages.compute(s, 0, false, own))
    }

    @Test fun totalModeAndDisciplineObserveEverythingSupported() {
        val total = ProtectionSettings(mode = ProtectionMode.TOTAL, youtubeEnabled = false)
        assertEquals(MonitoredPackages.ALL_SUPPORTED, MonitoredPackages.compute(total, 0, false, own))
        val discipline = Discipline.activate(ProtectionSettings(youtubeEnabled = false), 0, null, 5, DisciplineRules())
        assertEquals(MonitoredPackages.ALL_SUPPORTED, MonitoredPackages.compute(discipline, 1, false, own))
    }

    @Test fun diagnosticModeObservesSupportedAppsEvenIfOff() {
        val s = ProtectionSettings(youtubeEnabled = false, instagramEnabled = false)
        val packages = MonitoredPackages.compute(s, 0, diagnosticArmed = true, ownPackage = own)
        assertTrue(packages.containsAll(setOf(YouTubeRules.PACKAGE, InstagramRules.PACKAGE)))
    }
}
