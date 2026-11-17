package com.lucasanso.naorola.detection.region

import com.lucasanso.naorola.detection.rules.AdRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdRulesTest {
    @Test fun exactAdWords() {
        listOf("patrocinado", "sponsored", "anúncio", "ad", "publicidade").forEach { assertTrue(it, AdRules.isAdLabel(it)) }
    }

    @Test fun sponsoredWithAdvertiser() {
        assertTrue(AdRules.isAdLabel("patrocinado · loja exemplo"))
        assertTrue(AdRules.isAdLabel("sponsored · brand"))
    }

    @Test fun youtubeAdCounterNeedsTheSeparator() {
        assertTrue(AdRules.isAdLabel("anúncio · 0:12"))
        assertTrue(AdRules.isAdLabel("ad · 1 of 2"))
        assertFalse(AdRules.isAdLabel("anúncio do novo celular"))
        assertFalse(AdRules.isAdLabel("adele ao vivo"))
    }

    @Test fun ordinaryLabelsAreNotAds() {
        listOf("início", "shorts", "curtir", "patrocinador oficial", "").forEach { assertFalse(it, AdRules.isAdLabel(it)) }
    }

    @Test fun keys() {
        assertEquals(AdRules.KEY_AD, AdRules.keyOf("patrocinado"))
        assertEquals(AdRules.KEY_SKIP, AdRules.keyOf("pular anúncio"))
        assertEquals(AdRules.KEY_SKIP_WEAK, AdRules.keyOf("pular"))
        assertNull(AdRules.keyOf("pular intro"))
    }
}
