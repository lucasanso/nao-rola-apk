package com.lucasanso.naorola.detection

import com.lucasanso.naorola.detection.classifier.Classification
import com.lucasanso.naorola.detection.classifier.ShortVideoClassifier
import com.lucasanso.naorola.detection.detector.BrowserContentDetector
import com.lucasanso.naorola.detection.rules.AdultRules
import com.lucasanso.naorola.detection.rules.AdultSiteMatcher
import com.lucasanso.naorola.detection.rules.BrowserRules
import com.lucasanso.naorola.domain.model.Surface
import com.lucasanso.naorola.fixtures.Screens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultFilterTest {
    private val matcher = AdultSiteMatcher()

    @Test fun bundledListIsLoaded() {
        assertTrue(AdultRules.BUNDLED_DOMAINS.size > 80)
        assertTrue("pornhub.com" in AdultRules.BUNDLED_DOMAINS)
    }

    @Test fun adultSitesByListSubdomainTldAndWord() {
        listOf(
            "https://www.xvideos.com/video123/titulo",
            "pt.pornhub.com",
            "m.xhamster.com/videos/x",
            "chaturbate.com",
            "qualquercoisa.xxx",
            "site.porn/pagina",
            "novo-site-porno.com.br",
            "meuhentai.net",
            "HTTPS://WWW.ONLYFANS.COM/perfil",
        ).forEach { assertEquals(it, Surface.ADULT_SITE, matcher.surfaceFor(it)) }
    }

    @Test fun ordinarySitesAreNotBlocked() {
        listOf(
            "https://www.google.com/search?q=educa%C3%A7%C3%A3o+sexual",
            "essex.ac.uk",
            "sextafeira.com.br",
            "https://pt.wikipedia.org/wiki/Sexo",
            "https://www.youtube.com/watch?v=abc",
            "",
        ).forEach { assertNull(it, matcher.surfaceFor(it)) }
    }

    @Test fun hostWordsCatchMirrorsOfListedSites() {
        assertEquals(Surface.ADULT_SITE, matcher.surfaceFor("pornhub.com.mirror.example"))
        assertEquals(Surface.ADULT_SITE, matcher.surfaceFor("xvideos-espelho.net"))
    }

    @Test fun explicitSearchesInSearchEngines() {
        listOf(
            "https://www.google.com/search?q=v%C3%ADdeos+porn%C3%B4",
            "google.com.br/search?q=xvideos&client=ms-android",
            "https://www.bing.com/search?q=mulheres+peladas",
            "duckduckgo.com/?q=onlyfans+vazado",
            "br.search.yahoo.com/search?p=hentai",
        ).forEach { assertEquals(it, Surface.ADULT_SEARCH, matcher.surfaceFor(it)) }
    }

    @Test fun healthAndEverydaySearchesAreAllowed() {
        listOf(
            "https://www.google.com/search?q=sexo+seguro",
            "https://www.google.com/search?q=pelada+de+futebol+domingo",
            "https://www.google.com/search?q=sexta-feira+feriado",
            "https://www.google.com/search?q=porto+alegre",
            "https://example.com/?q=porno", // not a search engine and not an adult host
        ).forEach { assertNull(it, matcher.surfaceFor(it)) }
    }

    @Test fun searchTermsShownInTheAddressBar() {
        assertEquals(Surface.ADULT_SEARCH, matcher.surfaceFor("vídeos pornô"))
        assertEquals(Surface.ADULT_SEARCH, matcher.surfaceFor("xnxx"))
        assertNull(matcher.surfaceFor("receita de bolo"))
    }

    @Test fun importedDomainsCount() {
        val custom = AdultSiteMatcher(bundled = emptySet())
        assertNull(custom.surfaceFor("siteadulto-exemplo.com.br"))
        custom.extraDomains = AdultSiteMatcher.domainsOf(listOf("||siteadulto-exemplo.com.br^", "example.com##.ad"))
        assertEquals(Surface.ADULT_SITE, custom.surfaceFor("https://www.siteadulto-exemplo.com.br/x"))
    }

    @Test fun browserDetectorReportsAdultSiteInAnyBrowser() {
        val classifier = ShortVideoClassifier()
        listOf(BrowserRules.CHROME to "url_bar", "org.mozilla.firefox" to "mozac_browser_toolbar_url_view", "com.opera.browser" to null)
            .forEach { (pkg, id) ->
                val result = classifier.classify(BrowserContentDetector().detect(Screens.browser(pkg, id, "pt.pornhub.com/view")))
                assertEquals(pkg, Surface.ADULT_SITE, (result as Classification.ShortVideo).surface)
            }
        val shorts = classifier.classify(BrowserContentDetector().detect(Screens.browser(BrowserRules.CHROME, "url_bar", "youtube.com/shorts/x")))
        assertEquals(Surface.WEB_SHORTS, (shorts as Classification.ShortVideo).surface)
    }
}
