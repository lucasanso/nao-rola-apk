package com.lucasanso.naorola.detection

import com.lucasanso.naorola.detection.tree.TreeScanner
import com.lucasanso.naorola.fixtures.FakeTree
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

    @Test fun summaryNeverPrintsCapturedText() {
        val summary = TreeScanner().scan(
            Screens.browser("com.android.chrome", "url_bar", "https://secret.example/private"),
        )
        assertFalse(summary.toString().contains("secret"))
    }
}
