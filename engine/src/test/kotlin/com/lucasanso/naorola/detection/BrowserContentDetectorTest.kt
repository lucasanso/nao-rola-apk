package com.lucasanso.naorola.detection

import com.lucasanso.naorola.detection.classifier.Classification
import com.lucasanso.naorola.detection.classifier.ShortVideoClassifier
import com.lucasanso.naorola.detection.detector.BrowserContentDetector
import com.lucasanso.naorola.detection.rules.BrowserRules
import com.lucasanso.naorola.domain.model.Surface
import com.lucasanso.naorola.fixtures.Screens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserContentDetectorTest {
    private val detector = BrowserContentDetector()
    private val classifier = ShortVideoClassifier()

    @Test fun urlInterpretation() {
        val cases = mapOf(
            "https://www.youtube.com/shorts/abc123" to Surface.WEB_SHORTS,
            "m.youtube.com/shorts/abc123?feature=share" to Surface.WEB_SHORTS,
            "youtube.com/shorts" to Surface.WEB_SHORTS,
            "HTTPS://M.YOUTUBE.COM/SHORTS/X" to Surface.WEB_SHORTS,
            "https://www.instagram.com/reel/C0abc/" to Surface.WEB_REELS,
            "instagram.com/reels/C0abc" to Surface.WEB_REELS,
            "https://www.instagram.com/reels" to Surface.WEB_REELS,
        )
        cases.forEach { (url, expected) -> assertEquals(url, expected, BrowserRules.surfaceFor(url)) }
    }

    @Test fun legitimateUrlsAreNotShortVideo() {
        listOf(
            "https://www.youtube.com/watch?v=abc",
            "youtube.com",
            "https://www.youtube.com/@canal/shorts", // channel's Shorts grid, not the player
            "https://www.youtube.com/results?search_query=shorts",
            "https://www.instagram.com/usuario/reels/", // profile grid
            "https://www.instagram.com/p/C0abc/",
            "https://notyoutube.com/shorts/abc",
            "https://youtube.com.evil.example/shorts/abc",
            "como assistir shorts",
            "",
        ).forEach { assertNull(it, BrowserRules.surfaceFor(it)) }
    }

    @Test fun chromeAddressBarWithShortsIsShortVideo() {
        val tree = Screens.browser(BrowserRules.CHROME, "url_bar", "youtube.com/shorts/abc")
        val result = classifier.classify(detector.detect(tree))
        assertTrue(result is Classification.ShortVideo)
        assertEquals(Surface.WEB_SHORTS, (result as Classification.ShortVideo).surface)
    }

    @Test fun unknownAddressBarIdFallsBackToEditableFieldAtTop() {
        val tree = Screens.browser(BrowserRules.SAMSUNG_INTERNET, addressBarId = null, url = "https://m.youtube.com/shorts/abc")
        assertTrue(classifier.classify(detector.detect(tree)) is Classification.ShortVideo)
    }

    @Test fun regularVideoInBrowserIsNotShortVideo() {
        val tree = Screens.browser(BrowserRules.CHROME, "url_bar", "https://m.youtube.com/watch?v=abc")
        assertTrue(classifier.classify(detector.detect(tree)) is Classification.NotShortVideo)
    }
}
