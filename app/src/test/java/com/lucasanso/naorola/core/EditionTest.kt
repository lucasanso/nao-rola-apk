package com.lucasanso.naorola.core

import com.lucasanso.naorola.domain.model.ProtectionSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EditionTest {
    @Test fun playEditionNeverBlocksAdsInsideOtherApps() {
        val saved = ProtectionSettings(adBlockEnabled = true, hideInFeeds = true, adultBlockEnabled = true)
        val play = Edition.effective(saved, inAppAds = false)
        assertFalse(play.adBlockEnabled)
        assertEquals(saved.copy(adBlockEnabled = false), play) // everything else unchanged
        assertEquals(saved, Edition.effective(saved, inAppAds = true))
    }
}
