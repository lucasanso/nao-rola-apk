package com.lucasanso.naorola.detection.classifier

import com.lucasanso.naorola.detection.DetectionConfig
import com.lucasanso.naorola.detection.signal.DetectionResult
import com.lucasanso.naorola.detection.signal.Polarity
import com.lucasanso.naorola.detection.tree.RelBounds
import com.lucasanso.naorola.domain.model.ScreenContext
import com.lucasanso.naorola.domain.model.Surface

sealed interface Classification {
    val context: ScreenContext

    data class NotShortVideo(
        override val context: ScreenContext,
        val score: Int = 0,
        val vetoedBy: String? = null,
    ) : Classification

    data class ShortVideo(
        val surface: Surface,
        val confidence: Int,
        val pageKey: Int? = null,
        val regions: List<RelBounds> = emptyList(),
    ) : Classification {
        override val context: ScreenContext get() = ScreenContext.SHORT_VIDEO
    }
}

/**
 * App-agnostic decision: sum of positive weights (capped), cancelled by any strong negative
 * signal, and only positive above the threshold. When in doubt, it says "not short video".
 */
class ShortVideoClassifier(
    private val threshold: Int = DetectionConfig.CLASSIFIER_THRESHOLD,
    private val vetoWeight: Int = DetectionConfig.CLASSIFIER_VETO,
) {
    fun classify(result: DetectionResult): Classification {
        val veto = result.signals.firstOrNull { it.polarity == Polarity.NEGATIVE && it.weight >= vetoWeight }
        val score = result.signals
            .filter { it.polarity == Polarity.POSITIVE }
            .sumOf { it.weight }
            .coerceAtMost(DetectionConfig.MAX_SCORE)
        val surface = result.surface
        return when {
            veto != null -> Classification.NotShortVideo(result.context, score, vetoedBy = veto.id)
            surface == null || score < threshold -> Classification.NotShortVideo(result.context, score)
            else -> Classification.ShortVideo(surface, score, result.pageKey, result.regions)
        }
    }
}
