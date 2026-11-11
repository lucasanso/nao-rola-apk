package com.lucasanso.naorola.accessibility

import com.lucasanso.naorola.detection.rules.BrowserRules
import com.lucasanso.naorola.detection.rules.InstagramRules
import com.lucasanso.naorola.detection.rules.ShortVideoApps
import com.lucasanso.naorola.detection.rules.YouTubeRules
import com.lucasanso.naorola.domain.model.ProtectionMode
import com.lucasanso.naorola.domain.model.ProtectionSettings

/**
 * Which apps the accessibility service receives events from. Apps whose protection is off are
 * not observed at all (less data seen, less battery).
 */
object MonitoredPackages {
    val ALL_SUPPORTED: Set<String> =
        setOf(YouTubeRules.PACKAGE, InstagramRules.PACKAGE) + BrowserRules.PACKAGES + ShortVideoApps.KNOWN.keys

    fun compute(settings: ProtectionSettings, now: Long, diagnosticArmed: Boolean, ownPackage: String): Set<String> {
        val discipline = settings.discipline.isActive(now)
        val rules = settings.discipline.rules
        val total = settings.mode == ProtectionMode.TOTAL || (discipline && rules.forceTotalMode)
        val forceAll = total || (discipline && rules.forceAllPlatforms)

        val result = LinkedHashSet<String>()
        if (forceAll || settings.youtubeEnabled) result += YouTubeRules.PACKAGE
        if (forceAll || settings.instagramEnabled) result += InstagramRules.PACKAGE
        if (forceAll || settings.browserEnabled) result += BrowserRules.PACKAGES
        if (forceAll || settings.otherAppsEnabled) result += ShortVideoApps.KNOWN.keys
        if (diagnosticArmed) result += setOf(YouTubeRules.PACKAGE, InstagramRules.PACKAGE) + BrowserRules.PACKAGES
        // An empty list would mean "every app" to Android: fall back to our own package instead.
        if (result.isEmpty()) result += ownPackage
        return result
    }
}
