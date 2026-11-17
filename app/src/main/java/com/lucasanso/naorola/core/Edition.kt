package com.lucasanso.naorola.core

import com.lucasanso.naorola.BuildConfig
import com.lucasanso.naorola.domain.model.ProtectionSettings

/**
 * Differences between the APK and the Google Play edition (see app/build.gradle.kts). The saved
 * settings are the same in both; only what may run differs.
 */
object Edition {
    val name: String get() = BuildConfig.EDITION

    /** Covers over ads and "Skip ad" inside other apps: not allowed by Google Play policy. */
    val inAppAdBlock: Boolean get() = BuildConfig.IN_APP_AD_BLOCK

    fun effective(settings: ProtectionSettings, inAppAds: Boolean = inAppAdBlock): ProtectionSettings =
        if (inAppAds) settings else settings.copy(adBlockEnabled = false)
}
