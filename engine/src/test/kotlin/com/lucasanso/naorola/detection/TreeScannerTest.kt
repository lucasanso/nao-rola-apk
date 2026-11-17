package com.lucasanso.naorola.detection

import com.lucasanso.naorola.detection.tree.ScanRequest
import com.lucasanso.naorola.detection.tree.TreeScanner
import com.lucasanso.naorola.detection.tree.UiTree
import com.lucasanso.naorola.fixtures.FakeTree
import com.lucasanso.naorola.fixtures.RECYCLER
import com.lucasanso.naorola.fixtures.b
import com.lucasanso.naorola.fixtures.Screens
import com.lucasanso.naorola.fixtures.node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeScannerTest {
    @Test fun respectsNodeBudget() {
        val wide = node(children = List(50) { node(children = List(50) { node() }) })
        val summary = TreeScanner(maxNodes = 100).scan(FakeTree("x", wide))
        assertEquals(100, summary.visitedNodes)
        assertTrue(summary.truncated)
    }

    @Test fun skipsInvisibleSubtrees() {
        val hidden = node(id = "x:id/hidden", visible = false, children = listOf(node(id = "x:id/inside")))
        val summary = TreeScanner().scan(FakeTree("x", node(children = listOf(hidden, node(id = "x:id/shown")))))
        assertEquals(setOf("shown"), summary.ids.keys)
        assertFalse(summary.truncated)
    }

    @Test fun ownIdsAreShortenedAndForeignIdsKeptWhole() {
        val root = node(children = listOf(node(id = "x:id/mine"), node(id = "android:id/list")))
        assertEquals(setOf("mine", "android:id/list"), TreeScanner().scan(FakeTree("x", root)).ids.keys)
    }

    @Test fun feedItemsCollectIdsLabelsAndPortraitCards() {
        val feed = node(
            cls = RECYCLER, scrollable = true, bounds = b(0f, 0.1f, 1f, 0.9f), rows = 10, cols = 1,
            children = listOf(
                node(id = "x:id/post", bounds = b(0f, 0.1f, 1f, 0.4f), children = listOf(node(desc = "Patrocinado"))),
                node(bounds = b(0f, 0.4f, 1f, 0.8f), children = listOf(
                    node(bounds = b(0f, 0.42f, 0.48f, 0.78f), children = listOf(node(bounds = b(0f, 0.42f, 0.48f, 0.78f)))),
                    node(bounds = b(0.52f, 0.42f, 1f, 0.78f)),
                )),
            ),
        )
        val summary = TreeScanner().scan(FakeTree("x", feed), ScanRequest(labelKey = { if (it == "patrocinado") "ad" else null }))
        val main = summary.mainFeed()!!
        val items = summary.itemsOf(main)
        assertEquals(2, items.size)
        assertEquals(setOf("post"), items[0].ids)
        assertEquals(setOf("patrocinado"), items[0].labels)
        assertEquals(1, summary.keyedLabels["ad"]?.size)
        // The card and its same-sized thumbnail count once.
        assertEquals(2, items[1].portraitCards.size)
        assertTrue(items[1].hasPortraitPair())
        assertFalse(items[0].hasPortraitPair())
    }

    @Test fun portraitShapeUsesTheRealScreenProportion() {
        val card = node(cls = RECYCLER, scrollable = true, bounds = b(0f, 0f, 1f, 1f), children = listOf(node(bounds = b(0f, 0f, 0.4f, 0.3f))))
        val phone = TreeScanner().scan(FakeTree("x", card))
        assertTrue(phone.items.single().isPortrait)
        // The same fractions on a landscape tablet are a wide rectangle.
        val tablet = TreeScanner().scan(object : UiTree {
            override val packageName = "x"
            override val root = card
            override val aspectRatio = 0.6f
        })
        assertFalse(tablet.items.single().isPortrait)
    }

    @Test fun rememberedNodesById() {
        val root = node(children = listOf(node(id = "x:id/skip_ad_button", bounds = b(0.7f, 0.3f, 0.9f, 0.35f))))
        val summary = TreeScanner().scan(FakeTree("x", root), ScanRequest(nodeIds = setOf("skip_ad_button")))
        assertEquals(b(0.7f, 0.3f, 0.9f, 0.35f), summary.keyedIds["skip_ad_button"]?.bounds)
    }

    @Test fun summaryNeverPrintsCapturedText() {
        val summary = TreeScanner().scan(
            Screens.browser("com.android.chrome", "url_bar", "https://secret.example/private"),
        )
        assertFalse(summary.toString().contains("secret"))
    }
}
