package com.lucasanso.naorola.detection.detector

import com.lucasanso.naorola.detection.rules.AdultSiteMatcher
import com.lucasanso.naorola.detection.rules.BrowserRules
import com.lucasanso.naorola.detection.signal.DetectionResult
import com.lucasanso.naorola.detection.signal.Polarity
import com.lucasanso.naorola.detection.signal.Signal
import com.lucasanso.naorola.detection.tree.ScanRequest
import com.lucasanso.naorola.detection.tree.TreeScanner
import com.lucasanso.naorola.detection.tree.UiTree
import com.lucasanso.naorola.domain.model.ScreenContext
import com.lucasanso.naorola.domain.model.Surface

/**
 * Reads the address bar of supported browsers and keeps only the conclusion: Shorts/Reels URL,
 * adult site, explicit search, or nothing. Whether each one is blocked is decided by the policy.
 */
class BrowserContentDetector(
    private val scanner: TreeScanner = TreeScanner(),
    private val adult: AdultSiteMatcher = AdultSiteMatcher(),
) : ContentDetector {
    override val id = "browser"

    override fun supports(packageName: String) = packageName in BrowserRules.PACKAGES

    override fun detect(tree: UiTree): DetectionResult {
        val request = ScanRequest(
            captureTextOfIds = BrowserRules.ADDRESS_BAR_IDS[tree.packageName].orEmpty(),
            captureTopEditTexts = true,
        )
        val summary = scanner.scan(tree, request)
        val texts = summary.capturedTexts
        val surface = texts.firstNotNullOfOrNull(adult::surfaceFor) ?: texts.firstNotNullOfOrNull(BrowserRules::surfaceFor)
        val signal = when (surface) {
            null -> null
            Surface.ADULT_SITE -> SIGNAL_ADULT_SITE
            Surface.ADULT_SEARCH -> SIGNAL_ADULT_SEARCH
            else -> SIGNAL_URL
        }
        return DetectionResult(
            detectorId = id,
            surface = surface,
            signals = listOfNotNull(signal?.let { Signal(it, Polarity.POSITIVE, 100) }),
            context = ScreenContext.UNKNOWN,
            truncated = summary.truncated,
        )
    }

    companion object {
        const val SIGNAL_URL = "browser.short_video_url"
        const val SIGNAL_ADULT_SITE = "browser.adult_site"
        const val SIGNAL_ADULT_SEARCH = "browser.adult_search"
    }
}
