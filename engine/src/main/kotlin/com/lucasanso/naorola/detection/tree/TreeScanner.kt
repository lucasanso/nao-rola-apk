package com.lucasanso.naorola.detection.tree

import com.lucasanso.naorola.detection.DetectionConfig

/** A scrollable container found on screen, with the geometry rules need. */
data class ScrollableInfo(
    val viewId: String?,
    val className: String?,
    val bounds: RelBounds,
    val rows: Int?,
    val columns: Int?,
    val childBounds: List<RelBounds>,
    /** Identity of the visible child that is tallest (the current page of a pager). */
    val dominantChildKey: Int?,
    val dominantChildHeight: Float,
) {
    /** True when the container clearly scrolls sideways (e.g. the Stories viewer). */
    val isHorizontal: Boolean
        get() {
            val r = rows
            val c = columns
            if (r != null && c != null) return r == 1 && c > 1
            if (childBounds.size < 2) return false
            val dx = childBounds.maxOf { it.left } - childBounds.minOf { it.left }
            val dy = childBounds.maxOf { it.top } - childBounds.minOf { it.top }
            return dx > dy
        }
}

/** What to capture beyond structure. Only browsers ask for text, and only of address bars. */
data class ScanRequest(
    val captureTextOfIds: Set<String> = emptySet(),
    val captureTopEditTexts: Boolean = false,
)

/**
 * Structural summary of a screen. View ids belonging to the scanned app are stored by their
 * short name ("reel_recycler"); ids of other packages keep the full name.
 *
 * Labels and captured texts are transient: never persisted, never logged (see [toString]).
 */
class ScreenSummary(
    val packageName: String,
    /** Short id → tallest bounds seen for that id. */
    val ids: Map<String, RelBounds>,
    val selectedIds: Set<String>,
    /** Lower-cased labels (description or text) of selected nodes, e.g. the active tab. */
    val selectedLabels: Set<String>,
    val scrollables: List<ScrollableInfo>,
    val capturedTexts: List<String>,
    val visitedNodes: Int,
    val truncated: Boolean,
) {
    override fun toString(): String =
        "ScreenSummary(package=$packageName, ids=${ids.size}, scrollables=${scrollables.size}, " +
            "visited=$visitedNodes, truncated=$truncated)"
}

/**
 * Walks the visible part of a tree once, within a node budget, and produces a [ScreenSummary].
 * Invisible subtrees (e.g. pages prefetched off-screen) are skipped.
 */
class TreeScanner(
    private val maxNodes: Int = DetectionConfig.MAX_NODES,
    private val maxDepth: Int = DetectionConfig.MAX_DEPTH,
) {
    fun scan(tree: UiTree, request: ScanRequest = ScanRequest()): ScreenSummary {
        val ownPrefix = "${tree.packageName}:id/"
        val ids = HashMap<String, RelBounds>()
        val selectedIds = HashSet<String>()
        val selectedLabels = HashSet<String>()
        val scrollables = ArrayList<ScrollableInfo>()
        val captured = ArrayList<String>()
        var visited = 0
        var truncated = false

        val stack = ArrayDeque<Pair<UiNode, Int>>()
        tree.root?.let { stack.addLast(it to 0) }

        while (stack.isNotEmpty()) {
            if (visited >= maxNodes) {
                truncated = true
                break
            }
            val (node, depth) = stack.removeLast()
            if (!node.isVisibleToUser) continue
            visited++

            val shortId = node.viewId?.let { if (it.startsWith(ownPrefix)) it.removePrefix(ownPrefix) else it }
            if (shortId != null) {
                val previous = ids[shortId]
                if (previous == null || node.bounds.height > previous.height) ids[shortId] = node.bounds
                if (node.isSelected) selectedIds += shortId
                if (shortId in request.captureTextOfIds) node.text?.toString()?.let { captured += it }
            }
            if (node.isSelected) labelOf(node)?.let { selectedLabels += it }
            if (request.captureTopEditTexts && isTopEditText(node)) node.text?.toString()?.let { captured += it }
            if (node.isScrollable) scrollables += describeScrollable(node, shortId)

            if (depth < maxDepth) {
                // Push in reverse so children are visited in screen order.
                for (i in node.childCount - 1 downTo 0) {
                    node.child(i)?.let { stack.addLast(it to depth + 1) }
                }
            } else {
                truncated = true
            }
        }

        return ScreenSummary(
            packageName = tree.packageName,
            ids = ids,
            selectedIds = selectedIds,
            selectedLabels = selectedLabels,
            scrollables = scrollables,
            capturedTexts = captured,
            visitedNodes = visited,
            truncated = truncated,
        )
    }

    private fun labelOf(node: UiNode): String? {
        val raw = (node.contentDescription ?: node.text)?.toString()?.trim() ?: return null
        if (raw.isEmpty() || raw.length > DetectionConfig.MAX_LABEL_LENGTH) return null
        return raw.lowercase()
    }

    private fun isTopEditText(node: UiNode): Boolean =
        node.className?.endsWith("EditText") == true && node.bounds.top <= DetectionConfig.ADDRESS_BAR_MAX_TOP

    private fun describeScrollable(node: UiNode, shortId: String?): ScrollableInfo {
        val childBounds = ArrayList<RelBounds>()
        var dominantKey: Int? = null
        var dominantHeight = 0f
        val count = minOf(node.childCount, DetectionConfig.MAX_CHILDREN_PER_SCROLLABLE)
        for (i in 0 until count) {
            val child = node.child(i) ?: continue
            if (!child.isVisibleToUser || child.bounds.isEmpty) continue
            childBounds += child.bounds
            if (child.bounds.height > dominantHeight) {
                dominantHeight = child.bounds.height
                dominantKey = child.identityKey
            }
        }
        return ScrollableInfo(
            viewId = shortId,
            className = node.className,
            bounds = node.bounds,
            rows = node.collectionRowCount,
            columns = node.collectionColumnCount,
            childBounds = childBounds,
            dominantChildKey = dominantKey,
            dominantChildHeight = dominantHeight,
        )
    }
}
