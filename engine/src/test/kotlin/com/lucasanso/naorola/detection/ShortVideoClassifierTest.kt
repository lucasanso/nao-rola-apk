package com.lucasanso.naorola.detection

import com.lucasanso.naorola.detection.classifier.Classification
import com.lucasanso.naorola.detection.classifier.ShortVideoClassifier
import com.lucasanso.naorola.detection.detector.PackageContentDetector
import com.lucasanso.naorola.detection.signal.DetectionResult
import com.lucasanso.naorola.detection.signal.Polarity.NEGATIVE
import com.lucasanso.naorola.detection.signal.Polarity.POSITIVE
import com.lucasanso.naorola.detection.signal.Signal
import com.lucasanso.naorola.domain.model.ScreenContext
import com.lucasanso.naorola.domain.model.Surface
import com.lucasanso.naorola.fixtures.FakeTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortVideoClassifierTest {
    private val classifier = ShortVideoClassifier(threshold = 60, vetoWeight = 60)

    private fun result(vararg signals: Signal, surface: Surface? = Surface.SHORTS_PLAYER) =
        DetectionResult("test", surface, signals.toList(), ScreenContext.UNKNOWN)

    @Test fun belowThresholdIsNotShortVideo() {
        assertTrue(classifier.classify(result(Signal("a", POSITIVE, 35), Signal("b", POSITIVE, 20))) is Classification.NotShortVideo)
    }

    @Test fun atThresholdIsShortVideo() {
        assertTrue(classifier.classify(result(Signal("a", POSITIVE, 35), Signal("b", POSITIVE, 25))) is Classification.ShortVideo)
    }

    @Test fun scoreIsCappedAt100() {
        val c = classifier.classify(result(Signal("a", POSITIVE, 90), Signal("b", POSITIVE, 90)))
        assertEquals(100, (c as Classification.ShortVideo).confidence)
    }

    @Test fun strongNegativeVetoesEvenFullScore() {
        val c = classifier.classify(result(Signal("a", POSITIVE, 100), Signal("veto", NEGATIVE, 60)))
        assertEquals("veto", (c as Classification.NotShortVideo).vetoedBy)
    }

    @Test fun weakNegativeDoesNotVeto() {
        assertTrue(classifier.classify(result(Signal("a", POSITIVE, 80), Signal("weak", NEGATIVE, 20))) is Classification.ShortVideo)
    }

    @Test fun noSurfaceMeansNotShortVideo() {
        assertTrue(classifier.classify(result(Signal("a", POSITIVE, 100), surface = null)) is Classification.NotShortVideo)
    }

    @Test fun shortVideoAppsAreBlockedByPackage() {
        val detector = PackageContentDetector()
        assertTrue(detector.supports("com.zhiliaoapp.musically"))
        val c = classifier.classify(detector.detect(FakeTree("com.zhiliaoapp.musically", null)))
        assertEquals(Surface.SHORT_VIDEO_APP, (c as Classification.ShortVideo).surface)
    }
}
