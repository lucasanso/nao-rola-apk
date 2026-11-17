package com.lucasanso.naorola.detection

import com.lucasanso.naorola.detection.classifier.Classification
import com.lucasanso.naorola.detection.classifier.ShortVideoClassifier
import com.lucasanso.naorola.detection.detector.DetectorRegistry
import com.lucasanso.naorola.detection.diagnostics.StructureDump
import com.lucasanso.naorola.detection.diagnostics.StructureDump.Expectation
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Real screens captured with the diagnostic mode and labelled in the app ("deveria bloquear" /
 * "não deveria bloquear") live in docs/capturas/. Every labelled capture must be classified as
 * its label says: this is how calibration fixes are verified, here and in CI.
 */
class CapturedScreensTest {
    private val directory = File(System.getProperty("naorola.capturas") ?: "../docs/capturas")

    @Test fun labelledCapturesAreClassifiedAsExpected() {
        val files = directory.listFiles { f -> f.isFile && f.name.endsWith(".txt") }.orEmpty().sortedBy { it.name }
        val registry = DetectorRegistry.default()
        val classifier = ShortVideoClassifier()
        val failures = ArrayList<String>()
        val unlabelled = ArrayList<String>()
        var checked = 0

        for (file in files) {
            val text = file.readText()
            val expected = StructureDump.expectationOf(text)
            if (expected == null) {
                unlabelled += file.name
                continue
            }
            val tree = StructureDump.parse(text)
            val detector = registry.detectorFor(tree.packageName)
            if (detector == null) {
                failures += "${file.name}: nenhum detector para o pacote '${tree.packageName}'"
                continue
            }
            val detection = detector.detect(tree)
            val result = classifier.classify(detection)
            val blocked = result is Classification.ShortVideo
            checked++
            if (blocked != (expected == Expectation.BLOCK)) {
                val signals = detection.signals.joinToString { "${it.id}${if (it.polarity.name == "POSITIVE") "+" else "-"}${it.weight}" }
                failures += "${file.name}: esperado ${expected.label}, o detector ${if (blocked) "bloqueia" else "libera"} " +
                    "(sinais: ${signals.ifEmpty { "nenhum" }})"
            }
        }
        println("Capturas reais: $checked verificadas, ${unlabelled.size} sem rótulo ${if (unlabelled.isEmpty()) "" else unlabelled}")
        assertTrue("Capturas reais classificadas errado:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
