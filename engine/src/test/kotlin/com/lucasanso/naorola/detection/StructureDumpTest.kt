package com.lucasanso.naorola.detection

import com.lucasanso.naorola.detection.classifier.Classification
import com.lucasanso.naorola.detection.classifier.ShortVideoClassifier
import com.lucasanso.naorola.detection.detector.InstagramContentDetector
import com.lucasanso.naorola.detection.detector.YouTubeContentDetector
import com.lucasanso.naorola.detection.diagnostics.StructureDump
import com.lucasanso.naorola.fixtures.FakeTree
import com.lucasanso.naorola.fixtures.Screens
import com.lucasanso.naorola.fixtures.node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructureDumpTest {
    private val classifier = ShortVideoClassifier()

    @Test fun dumpNeverContainsTextOrDescriptions() {
        val tree = FakeTree(
            "x",
            node(children = listOf(
                node(id = "x:id/msg", text = "mensagem privada da Ana", desc = "foto de @ana"),
                node(id = "x:id/tab", desc = "Shorts", selected = true),
            )),
        )
        val dump = StructureDump.write(tree)
        assertFalse(dump.contains("Ana"))
        assertFalse(dump.contains("privada"))
        assertTrue(dump.contains("\tshorts"))
        assertTrue(dump.contains("x:id/msg\t"))
    }

    @Test fun roundTripKeepsDetectionResult() {
        listOf(
            Screens.youtubeShortsTab() to YouTubeContentDetector(),
            Screens.youtubeWatchPage() to YouTubeContentDetector(),
            Screens.instagramReelFromFeed() to InstagramContentDetector(),
            Screens.instagramStories() to InstagramContentDetector(),
        ).forEach { (tree, detector) ->
            val original = classifier.classify(detector.detect(tree))
            val parsed = StructureDump.parse(StructureDump.write(tree))
            assertEquals(tree.packageName, parsed.packageName)
            val reparsed = classifier.classify(detector.detect(parsed))
            assertEquals(original is Classification.ShortVideo, reparsed is Classification.ShortVideo)
            assertEquals(original.context, reparsed.context)
        }
    }

    @Test fun parsesFixtureFileFromResources() {
        val text = javaClass.getResource("/fixtures/youtube_shorts_sintetico.txt")!!.readText()
        val tree = StructureDump.parse(text)
        assertTrue(classifier.classify(YouTubeContentDetector().detect(tree)) is Classification.ShortVideo)
    }
}
