package com.lucasanso.naorola.detection.detector

import com.lucasanso.naorola.detection.rules.ShortVideoApps
import com.lucasanso.naorola.detection.signal.DetectionResult
import com.lucasanso.naorola.detection.signal.Polarity
import com.lucasanso.naorola.detection.signal.Signal
import com.lucasanso.naorola.detection.tree.UiTree
import com.lucasanso.naorola.domain.model.ScreenContext
import com.lucasanso.naorola.domain.model.Surface

/** Apps that are entirely short videos: the package name alone is the evidence. */
class PackageContentDetector(private val packages: Set<String> = ShortVideoApps.KNOWN.keys) : ContentDetector {
    override val id = "package"

    override fun supports(packageName: String) = packageName in packages

    override fun detect(tree: UiTree) = DetectionResult(
        detectorId = id,
        surface = Surface.SHORT_VIDEO_APP,
        signals = listOf(Signal(SIGNAL_PACKAGE, Polarity.POSITIVE, 100)),
        context = ScreenContext.SHORT_VIDEO,
    )

    companion object {
        const val SIGNAL_PACKAGE = "package.short_video_app"
    }
}
