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
    /** Positions in [ScreenSummary.items] of this container's visible children. */
    val itemIndices: List<Int> = emptyList(),
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

/**
 * One visible child of a scrollable list (a feed post, a shelf…) with what was found inside it.
 * Labels are short texts/descriptions in lower case, kept in memory only.
 */
class FeedItem(
    val scrollable: Int,
    val bounds: RelBounds,
    val ids: Set<String>,
    val labels: Set<String>,
    /** Distinct "portrait cards" inside: tall thumbnails, the shape of Shorts/Reels shelves. */
    val portraitCards: List<RelBounds>,
    /** The item itself is a portrait card (a cell of a Shorts grid). */
    val isPortrait: Boolean = false,
) {
    /** Two portrait cards side by side: a Shorts shelf or a Shorts grid row. */
    fun hasPortraitPair(): Boolean = portraitCards.indices.any { i ->
        portraitCards.indices.any { j ->
            val a = portraitCards[i]
            val b = portraitCards[j]
            i != j && kotlin.math.abs(a.centerX - b.centerX) >= DetectionConfig.PAIR_MIN_CENTER_DISTANCE &&
                a.verticalOverlap(b) >= DetectionConfig.PAIR_MIN_VERTICAL_OVERLAP * minOf(a.height, b.height)
        }
    }

    override fun toString() = "FeedItem(bounds=$bounds, ids=${ids.size}, labels=${labels.size}, cards=${portraitCards.size})"
}

/** A node remembered because a rule may need its position or may press it. */
class KeyedNode(val bounds: RelBounds, val node: UiNode)

/** What to capture beyond structure. */
data class ScanRequest(
    /** Text of these short ids is captured (browsers: address bar only). */
    val captureTextOfIds: Set<String> = emptySet(),
    val captureTopEditTexts: Boolean = false,
    /** Maps a lower-case label to a key worth remembering with its node (e.g. "ad", "skip"), or null. */
    val labelKey: (String) -> String? = { null },
    /** Short ids whose nodes are remembered (e.g. buttons the app may press). */
    val nodeIds: Set<String> = emptySet(),
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
    val items: List<FeedItem> = emptyList(),
    val keyedLabels: Map<String, List<KeyedNode>> = emptyMap(),
    val keyedIds: Map<String, KeyedNode> = emptyMap(),
    val aspectRatio: Float = UiTree.DEFAULT_ASPECT_RATIO,
) {
    /**
     * The main vertical list of the screen (home feed, search results, Instagram feed…), if any.
     * Pagers (every visible child fills the container, e.g. the tab pager or the Shorts player)
     * are not feeds: treating a whole page as one "item" would cover everything.
     */
    fun mainFeed(): ScrollableInfo? = scrollables
        .filter { s -> !s.isHorizontal && s.itemIndices.isNotEmpty() && !isPager(s) }
        .maxByOrNull { it.bounds.width * it.bounds.height }

    private fun isPager(s: ScrollableInfo): Boolean =
        s.itemIndices.all { items[it].bounds.height >= DetectionConfig.PAGER_PAGE_FRACTION * s.bounds.height }

    fun itemsOf(scrollable: ScrollableInfo): List<FeedItem> = scrollable.itemIndices.map { items[it] }

    override fun toString(): String =
        "ScreenSummary(package=$packageName, ids=${ids.size}, scrollables=${scrollables.size}, items=${items.size}, " +
            "visited=$visitedNodes, truncated=$truncated)"
}

/**
 * Walks the visible part of a tree once, within a node budget, and produces a [ScreenSummary].
 * Invisible subtrees (e.g. pages prefetched off-screen) are skipped. Each node's children are
 * read once (every read may be a call into the observed app).
 */
class TreeScanner(
    private val maxNodes: Int = DetectionConfig.MAX_NODES,
    private val maxDepth: Int = DetectionConfig.MAX_DEPTH,
) {
    private class ItemAgg(val scrollable: Int, val bounds: RelBounds, val isPortrait: Boolean) {
        val ids = HashSet<String>()
        val labels = HashSet<String>()
        val cards = ArrayList<RelBounds>()

        fun addCard(b: RelBounds) {
            // Card container and its thumbnail have nearly the same bounds: keep one.
            if (cards.none { iou(it, b) >= DetectionConfig.CARD_DUPLICATE_IOU }) cards += b
        }
    }

    private class Frame(val node: UiNode, val depth: Int, val tags: IntArray)

    fun scan(tree: UiTree, request: ScanRequest = ScanRequest()): ScreenSummary {
        val ownPrefix = "${tree.packageName}:id/"
        val aspect = tree.aspectRatio
        val ids = HashMap<String, RelBounds>()
        val selectedIds = HashSet<String>()
        val selectedLabels = HashSet<String>()
        val scrollables = ArrayList<ScrollableInfo>()
        val captured = ArrayList<String>()
        val items = ArrayList<ItemAgg>()
        val keyedLabels = HashMap<String, MutableList<KeyedNode>>()
        val keyedIds = HashMap<String, KeyedNode>()
        var visited = 0
        var truncated = false

        val stack = ArrayDeque<Frame>()
        tree.root?.let { stack.addLast(Frame(it, 0, IntArray(0))) }

        while (stack.isNotEmpty()) {
            if (visited >= maxNodes) {
                truncated = true
                break
            }
            val frame = stack.removeLast()
            val node = frame.node
            if (!node.isVisibleToUser) continue
            visited++
            val bounds = node.bounds

            val shortId = node.viewId?.let { if (it.startsWith(ownPrefix)) it.removePrefix(ownPrefix) else it }
            if (shortId != null) {
                val previous = ids[shortId]
                if (previous == null || bounds.height > previous.height) ids[shortId] = bounds
                if (node.isSelected) selectedIds += shortId
                if (shortId in request.captureTextOfIds) node.text?.toString()?.let { captured += it }
                if (shortId in request.nodeIds && shortId !in keyedIds) keyedIds[shortId] = KeyedNode(bounds, node)
                for (t in frame.tags) items[t].ids += shortId
            }
            val label = labelOf(node)
            if (label != null) {
                if (node.isSelected) selectedLabels += label
                for (t in frame.tags) items[t].labels += label
                request.labelKey(label)?.let { key -> keyedLabels.getOrPut(key) { ArrayList() } += KeyedNode(bounds, node) }
            }
            if (request.captureTopEditTexts && isTopEditText(node)) node.text?.toString()?.let { captured += it }
            if (frame.tags.isNotEmpty() && isPortraitCard(bounds, aspect)) for (t in frame.tags) items[t].addCard(bounds)

            if (frame.depth >= maxDepth) {
                truncated = true
                continue
            }
            val children = (0 until minOf(node.childCount, DetectionConfig.MAX_CHILDREN_PER_NODE)).mapNotNull { node.child(it) }
            val childTags = arrayOfNulls<IntArray>(children.size)
            if (node.isScrollable) {
                val scrollableIndex = scrollables.size
                val itemIndices = ArrayList<Int>()
                children.forEachIndexed { i, child ->
                    if (itemIndices.size < DetectionConfig.MAX_ITEMS_PER_SCROLLABLE && child.isVisibleToUser && !child.bounds.isEmpty) {
                        itemIndices += items.size
                        childTags[i] = frame.tags + items.size
                        items += ItemAgg(scrollableIndex, child.bounds, isPortraitCard(child.bounds, aspect))
                    }
                }
                scrollables += describeScrollable(node, shortId, children, itemIndices)
            }
            // Push in reverse so children are visited in screen order.
            for (i in children.indices.reversed()) {
                stack.addLast(Frame(children[i], frame.depth + 1, childTags[i] ?: frame.tags))
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
            items = items.map { FeedItem(it.scrollable, it.bounds, it.ids, it.labels, it.cards, it.isPortrait) },
            keyedLabels = keyedLabels,
            keyedIds = keyedIds,
            aspectRatio = aspect,
        )
    }

    private fun labelOf(node: UiNode): String? {
        val raw = (node.contentDescription ?: node.text)?.toString()?.trim() ?: return null
        if (raw.isEmpty() || raw.length > DetectionConfig.MAX_LABEL_LENGTH) return null
        return raw.lowercase()
    }

    private fun isTopEditText(node: UiNode): Boolean =
        node.className?.endsWith("EditText") == true && node.bounds.top <= DetectionConfig.ADDRESS_BAR_MAX_TOP

    /** Tall thumbnail of a reasonable size: the shape of Shorts/Reels cards in feeds. */
    private fun isPortraitCard(b: RelBounds, aspect: Float): Boolean {
        if (b.width < DetectionConfig.CARD_MIN_WIDTH || b.width > DetectionConfig.CARD_MAX_WIDTH) return false
        if (b.height < DetectionConfig.CARD_MIN_HEIGHT) return false
        val realAspect = b.height / b.width * aspect
        return realAspect >= DetectionConfig.CARD_MIN_ASPECT && realAspect <= DetectionConfig.CARD_MAX_ASPECT
    }

    private fun describeScrollable(node: UiNode, shortId: String?, children: List<UiNode>, itemIndices: List<Int>): ScrollableInfo {
        val childBounds = ArrayList<RelBounds>()
        var dominantKey: Int? = null
        var dominantHeight = 0f
        for (child in children.take(DetectionConfig.MAX_CHILDREN_PER_SCROLLABLE)) {
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
            itemIndices = itemIndices,
        )
    }

    private companion object {
        fun iou(a: RelBounds, b: RelBounds): Float {
            val inter = a.intersect(b)
            val interArea = inter.width * inter.height
            val union = a.width * a.height + b.width * b.height - interArea
            return if (union <= 0f) 0f else interArea / union
        }
    }
}
