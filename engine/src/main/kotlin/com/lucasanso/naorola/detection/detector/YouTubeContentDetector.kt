package com.lucasanso.naorola.detection.detector

import com.lucasanso.naorola.detection.rules.RuleEvaluator
import com.lucasanso.naorola.detection.rules.YouTubeRules
import com.lucasanso.naorola.detection.signal.DetectionResult
import com.lucasanso.naorola.detection.tree.TreeScanner
import com.lucasanso.naorola.detection.tree.UiTree
import com.lucasanso.naorola.domain.model.Surface

class YouTubeContentDetector(private val scanner: TreeScanner = TreeScanner()) : ContentDetector {
    override val id = "youtube"

    override fun supports(packageName: String) = packageName == YouTubeRules.PACKAGE

    override fun detect(tree: UiTree): DetectionResult {
        val summary = scanner.scan(tree)
        val evaluation = RuleEvaluator.evaluate(summary, YouTubeRules.rules)
        return DetectionResult(
            detectorId = id,
            surface = Surface.SHORTS_PLAYER,
            signals = evaluation.signals,
            context = RuleEvaluator.contextOf(summary, YouTubeRules.contextRules),
            pageKey = evaluation.pager?.dominantChildKey,
            truncated = summary.truncated,
        )
    }
}
