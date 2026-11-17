package com.lucasanso.naorola.domain

import com.lucasanso.naorola.domain.adblock.FilterList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterListTest {
    @Test fun adblockSyntaxIsKept() {
        assertEquals("||ads.example.com^", FilterList.ruleOf("||ads.example.com^"))
        assertEquals("example.com##.banner", FilterList.ruleOf("example.com##.banner"))
        assertEquals("@@||good.example.com^", FilterList.ruleOf("@@||good.example.com^"))
        assertEquals("example.com##div[title='a b']", FilterList.ruleOf("example.com##div[title='a b']"))
    }

    @Test fun hostsAndPlainDomainsAreConverted() {
        assertEquals("||ads.example.com^", FilterList.ruleOf("0.0.0.0 ads.example.com"))
        assertEquals("||ads.example.com^", FilterList.ruleOf("127.0.0.1\tAds.Example.com # comment"))
        assertEquals("||tracker.example.net^", FilterList.ruleOf("tracker.example.net"))
        assertNull(FilterList.ruleOf("127.0.0.1 localhost"))
        assertNull(FilterList.ruleOf("# hosts comment"))
        assertNull(FilterList.ruleOf("isto não é uma regra"))
    }

    @Test fun parseDropsCommentsHeadersAndDuplicates() {
        val parsed = FilterList.parse(
            """
            [Adblock Plus 2.0]
            ! Title: test
            ||a.example^
            0.0.0.0 a.example
            0.0.0.0 b.example
            texto solto qualquer
            """.trimIndent(),
        )
        assertEquals(listOf("||a.example^", "||b.example^"), parsed.rules)
        assertEquals(1, parsed.ignoredLines)
    }

    @Test fun parseStopsAtTheLimit() {
        val parsed = FilterList.parse((1..10).joinToString("\n") { "||ad$it.example^" }, maxRules = 3)
        assertEquals(3, parsed.rules.size)
        assertTrue(parsed.truncated)
    }

    @Test fun renderWritesHeaderAsCommentsAndMergesLists() {
        val text = FilterList.render("Não-Rola\nv1", listOf("||a.example^"), listOf("||a.example^", "||b.example^"))
        assertEquals("! Não-Rola\n! v1\n||a.example^\n||b.example^\n", text)
    }
}
