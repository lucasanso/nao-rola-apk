package com.lucasanso.naorola.monitoring

import com.lucasanso.naorola.core.AppLogger
import com.lucasanso.naorola.core.Clock
import com.lucasanso.naorola.detection.classifier.ShortVideoClassifier
import com.lucasanso.naorola.detection.detector.YouTubeContentDetector
import com.lucasanso.naorola.detection.diagnostics.StructureDump
import com.lucasanso.naorola.detection.diagnostics.StructureDump.Expectation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticRecorderTest {
    @get:Rule val folder = TemporaryFolder()
    private var now = 1_800_000_000_000L

    private val screen = StructureDump.parse(
        listOf(
            "# naorola-structure-dump v1",
            "# package=com.google.android.youtube",
            "0\tandroid.widget.FrameLayout\t-\t0.000,0.000,1.000,1.000\tVT\t-\t-",
        ).joinToString("\n"),
    )

    private fun recorder() = DiagnosticRecorder(
        directory = folder.root,
        clock = Clock { now },
        logger = AppLogger.None,
        context = DiagnosticContext("0.3.0", "17", 37, "Teste", versionOf = { "1.0" }),
    )

    private fun capture(r: DiagnosticRecorder) {
        r.arm()
        val detection = YouTubeContentDetector().detect(screen)
        r.captureIfArmed(screen, detection, ShortVideoClassifier().classify(detection))
    }

    @Test fun capturesOncePerArmingAndLabelsWithoutReordering() {
        val r = recorder()
        capture(r)
        now += 2_000
        capture(r)
        val detection = YouTubeContentDetector().detect(screen)
        r.captureIfArmed(screen, detection, ShortVideoClassifier().classify(detection)) // not armed: ignored
        assertEquals(2, r.files.value.size)
        assertFalse(r.armed.value)

        val (newest, oldest) = r.files.value
        r.setExpectation(oldest.file, Expectation.BLOCK)
        assertEquals(listOf(newest.file, oldest.file), r.files.value.map { it.file })
        assertEquals(Expectation.BLOCK, r.files.value[1].expectation)
        assertNull(r.files.value[0].expectation)

        val text = oldest.file.readText()
        assertTrue(text.contains("# esperado=bloquear"))
        assertTrue(text.contains("# versao_app_observado=1.0"))
        r.setExpectation(oldest.file, null)
        assertNull(r.files.value[1].expectation)
    }

    @Test fun ignoresFilesOutsideItsFolder() {
        val r = recorder()
        val outside = folder.newFolder("outro").resolve("x.txt").apply { writeText("# naorola-structure-dump v1\n") }
        r.setExpectation(outside, Expectation.ALLOW)
        assertFalse(outside.readText().contains("esperado"))
    }
}
