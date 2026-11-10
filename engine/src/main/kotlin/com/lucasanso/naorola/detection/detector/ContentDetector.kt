package com.lucasanso.naorola.detection.detector

import com.lucasanso.naorola.detection.signal.DetectionResult
import com.lucasanso.naorola.detection.tree.UiTree

/**
 * One detector per app (or family of apps). Detectors only gather evidence; the classifier and
 * the blocking policy decide what to do with it. Adding an app = a new detector + registration.
 */
interface ContentDetector {
    val id: String
    fun supports(packageName: String): Boolean
    fun detect(tree: UiTree): DetectionResult
}

class DetectorRegistry(private val detectors: List<ContentDetector>) {
    fun detectorFor(packageName: String): ContentDetector? = detectors.firstOrNull { it.supports(packageName) }

    companion object {
        fun default(): DetectorRegistry = DetectorRegistry(
            listOf(
                YouTubeContentDetector(),
                InstagramContentDetector(),
                BrowserContentDetector(),
                PackageContentDetector(),
            ),
        )
    }
}
