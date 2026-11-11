package com.lucasanso.naorola.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.lucasanso.naorola.detection.tree.RelBounds
import com.lucasanso.naorola.detection.tree.UiNode
import com.lucasanso.naorola.detection.tree.UiTree

/**
 * Lazy adapter from AccessibilityNodeInfo to the engine's [UiTree]. Each property is read only
 * when a rule asks for it, because every read may be a call into the observed app.
 *
 * Bounds are made relative to the app's own window (the root node), so "fills the screen"
 * also works in split screen and pop-up windows.
 */
class AccessibilityUiTree(
    override val packageName: String,
    rootInfo: AccessibilityNodeInfo?,
    fallbackWidth: Int,
    fallbackHeight: Int,
) : UiTree {
    private val reference: Rect = Rect().also { r ->
        rootInfo?.getBoundsInScreen(r)
        if (r.width() <= 0 || r.height() <= 0) r.set(0, 0, fallbackWidth, fallbackHeight)
    }

    override val root: UiNode? = rootInfo?.let { AccessibilityUiNode(it, this) }

    internal fun relativeBounds(info: AccessibilityNodeInfo): RelBounds {
        val r = Rect()
        info.getBoundsInScreen(r)
        return RelBounds.fromPixels(
            r.left - reference.left,
            r.top - reference.top,
            r.right - reference.left,
            r.bottom - reference.top,
            reference.width(),
            reference.height(),
        )
    }
}

private class AccessibilityUiNode(
    private val info: AccessibilityNodeInfo,
    private val tree: AccessibilityUiTree,
) : UiNode {
    override val className: String? get() = info.className?.toString()
    override val viewId: String? get() = info.viewIdResourceName
    override val bounds: RelBounds by lazy { tree.relativeBounds(info) }
    override val isScrollable: Boolean get() = info.isScrollable
    override val isSelected: Boolean get() = info.isSelected
    override val isVisibleToUser: Boolean get() = info.isVisibleToUser
    override val text: CharSequence? get() = info.text
    override val contentDescription: CharSequence? get() = info.contentDescription
    override val collectionRowCount: Int? get() = info.collectionInfo?.rowCount?.takeIf { it >= 0 }
    override val collectionColumnCount: Int? get() = info.collectionInfo?.columnCount?.takeIf { it >= 0 }
    override val identityKey: Int get() = info.hashCode()
    override val childCount: Int get() = info.childCount
    override fun child(index: Int): UiNode? =
        runCatching { info.getChild(index) }.getOrNull()?.let { AccessibilityUiNode(it, tree) }
}
