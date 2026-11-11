package com.lucasanso.naorola.detection.detector

import com.lucasanso.naorola.detection.rules.BrowserRules
import com.lucasanso.naorola.detection.signal.DetectionResult
import com.lucasanso.naorola.detection.signal.Polarity
import com.lucasanso.naorola.detection.signal.Signal
import com.lucasanso.naorola.detection.tree.ScanRequest
import com.lucasanso.naorola.detection.tree.TreeScanner
import com.lucasanso.naorola.detection.tree.UiTree
import com.lucasanso.naorola.domain.model.ScreenContext

/** Reads the address bar of supported browsers and keeps only whether it is a Shorts/Reels URL. */
class BrowserContentDetector(private val scanner: TreeScanner = TreeScanner()) : ContentDetector {
    override val id = "browser"

    override fun supports(packageName: String) = packageName in BrowserRules.PACKAGES

    override fun detect(tree: UiTree): DetectionResult {
        val request = ScanRequest(
            captureTextOfIds = BrowserRules.ADDRESS_BAR_IDS[tree.packageName].orEmpty(),
            captureTopEditTexts = true,
        )
        val summary = scanner.scan(tree, request)
        val surface = summary.capturedTexts.firstNotNullOfOrNull(BrowserRules::surfaceFor)
        return DetectionResult(
            detectorId = id,
            surface = surface,
            signals = if (surface != null) listOf(Signal(SIGNAL_URL, Polarity.POSITIVE, 100)) else emptyList(),
            context = ScreenContext.UNKNOWN,
            truncated = summary.truncated,
        )
    }

    companion object {
        const val SIGNAL_URL = "browser.short_video_url"
    }
}
