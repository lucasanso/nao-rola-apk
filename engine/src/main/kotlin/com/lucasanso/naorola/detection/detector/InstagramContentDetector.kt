package com.lucasanso.naorola.detection.detector

import com.lucasanso.naorola.detection.rules.InstagramRules
import com.lucasanso.naorola.detection.rules.RuleEvaluator
import com.lucasanso.naorola.detection.signal.DetectionResult
import com.lucasanso.naorola.detection.tree.TreeScanner
import com.lucasanso.naorola.detection.tree.UiTree
import com.lucasanso.naorola.domain.model.Surface

/**
 * Detects the full-screen Reels viewer. Reels inside the main feed (regions) are left for the
 * calibration phase: their structure is unknown until real dumps exist.
 */
class InstagramContentDetector(private val scanner: TreeScanner = TreeScanner()) : ContentDetector {
    override val id = "instagram"

    override fun supports(packageName: String) = packageName == InstagramRules.PACKAGE

    override fun detect(tree: UiTree): DetectionResult {
        val summary = scanner.scan(tree)
        val evaluation = RuleEvaluator.evaluate(summary, InstagramRules.rules)
        return DetectionResult(
            detectorId = id,
            surface = Surface.REELS_VIEWER,
            signals = evaluation.signals,
            context = RuleEvaluator.contextOf(summary, InstagramRules.contextRules),
            pageKey = evaluation.pager?.dominantChildKey,
            truncated = summary.truncated,
        )
    }
}
