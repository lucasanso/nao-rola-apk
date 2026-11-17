package com.lucasanso.naorola.detection.detector

import com.lucasanso.naorola.detection.region.InstagramRegionFinder
import com.lucasanso.naorola.detection.rules.InstagramRules
import com.lucasanso.naorola.detection.rules.RuleEvaluator
import com.lucasanso.naorola.detection.signal.DetectionResult
import com.lucasanso.naorola.detection.tree.TreeScanner
import com.lucasanso.naorola.detection.tree.UiTree
import com.lucasanso.naorola.domain.model.Surface

/** Detects the full-screen Reels viewer, plus Reels, ads and the Reels tab on other screens. */
class InstagramContentDetector(private val scanner: TreeScanner = TreeScanner()) : ContentDetector {
    override val id = "instagram"

    override fun supports(packageName: String) = packageName == InstagramRules.PACKAGE

    override fun detect(tree: UiTree): DetectionResult {
        val summary = scanner.scan(tree, InstagramRegionFinder.SCAN_REQUEST)
        val evaluation = RuleEvaluator.evaluate(summary, InstagramRules.rules)
        val context = RuleEvaluator.contextOf(summary, InstagramRules.contextRules)
        return DetectionResult(
            detectorId = id,
            surface = Surface.REELS_VIEWER,
            signals = evaluation.signals,
            context = context,
            pageKey = evaluation.pager?.dominantChildKey,
            regions = InstagramRegionFinder.regions(summary, context),
            truncated = summary.truncated,
        )
    }
}
