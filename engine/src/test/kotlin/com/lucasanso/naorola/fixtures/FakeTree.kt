package com.lucasanso.naorola.fixtures

import com.lucasanso.naorola.detection.tree.RelBounds
import com.lucasanso.naorola.detection.tree.UiNode
import com.lucasanso.naorola.detection.tree.UiTree

const val VIEW_GROUP = "android.view.ViewGroup"
const val FRAME = "android.widget.FrameLayout"
const val RECYCLER = "androidx.recyclerview.widget.RecyclerView"
const val EDIT_TEXT = "android.widget.EditText"

class FakeNode(
    override val className: String?,
    override val viewId: String?,
    override val bounds: RelBounds,
    override val isScrollable: Boolean,
    override val isSelected: Boolean,
    override val isVisibleToUser: Boolean,
    override val text: CharSequence?,
    override val contentDescription: CharSequence?,
    override val collectionRowCount: Int?,
    override val collectionColumnCount: Int?,
    override val identityKey: Int,
    val children: List<FakeNode>,
) : UiNode {
    var childReads = 0
        private set
    override val childCount: Int get() = children.size
    override fun child(index: Int): UiNode? {
        childReads++
        return children.getOrNull(index)
    }
}

class FakeTree(override val packageName: String, override val root: UiNode?) : UiTree

private var nextKey = 1000

fun b(left: Float, top: Float, right: Float, bottom: Float) = RelBounds(left, top, right, bottom)

fun node(
    id: String? = null,
    cls: String = VIEW_GROUP,
    bounds: RelBounds = RelBounds.FULL,
    scrollable: Boolean = false,
    selected: Boolean = false,
    visible: Boolean = true,
    text: String? = null,
    desc: String? = null,
    rows: Int? = null,
    cols: Int? = null,
    key: Int = nextKey++,
    children: List<FakeNode> = emptyList(),
) = FakeNode(cls, id, bounds, scrollable, selected, visible, text, desc, rows, cols, key, children)
