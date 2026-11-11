package com.lucasanso.naorola.detection.diagnostics

import com.lucasanso.naorola.detection.DetectionConfig
import com.lucasanso.naorola.detection.tree.RelBounds
import com.lucasanso.naorola.detection.tree.UiNode
import com.lucasanso.naorola.detection.tree.UiTree
import java.util.Locale

/**
 * Text format for the diagnostic mode: one line per node, with STRUCTURE ONLY.
 *
 *     depth \t class \t viewId \t left,top,right,bottom \t flags \t rows,cols \t label
 *
 * Flags: V visible, S scrollable, s selected, T has text, D has description.
 * Texts and descriptions are never written. The label column holds a token only when the
 * description/text is exactly a known navigation label (e.g. "shorts", "reels"), else "-".
 *
 * The same files are parsed back as test fixtures, so real screens become regression tests.
 */
object StructureDump {
    const val HEADER = "# naorola-structure-dump v1"
    private const val NONE = "-"
    private const val EXPECTATION_KEY = "esperado"

    /** What should have happened on the captured screen, set by the user in the app. */
    enum class Expectation(val label: String) {
        BLOCK("bloquear"),
        ALLOW("liberar"),
        ;

        companion object {
            fun fromLabel(value: String?): Expectation? = entries.firstOrNull { it.label == value?.trim() }
        }
    }

    /** Header notes ("# key=value" lines before the first node). */
    fun notesOf(text: String): Map<String, String> = text.lineSequence()
        .map { it.trimEnd() }
        .takeWhile { it.isEmpty() || it.startsWith("#") }
        .filter { it.startsWith("# ") && '=' in it }
        .associate { it.removePrefix("# ").substringBefore('=').trim() to it.substringAfter('=').trim() }

    fun expectationOf(text: String): Expectation? = Expectation.fromLabel(notesOf(text)[EXPECTATION_KEY])

    /** Returns [text] with the expectation line set (or removed when [expectation] is null). */
    fun withExpectation(text: String, expectation: Expectation?): String {
        val lines = text.lines().filterNot { it.startsWith("# $EXPECTATION_KEY=") }.toMutableList()
        if (expectation != null) {
            val at = if (lines.firstOrNull() == HEADER) 1 else 0
            lines.add(at, "# $EXPECTATION_KEY=${expectation.label}")
        }
        return lines.joinToString("\n")
    }

    /** Navigation labels that may be written (they are app UI, not user content). */
    val LABEL_ALLOWLIST: Set<String> = setOf(
        "shorts", "reels", "home", "início", "inicio", "subscriptions", "inscrições", "you", "você",
        "search", "pesquisar", "explore", "explorar", "profile", "perfil", "messages", "mensagens",
        "direct", "feed", "stories",
    )

    fun write(tree: UiTree, notes: Map<String, String> = emptyMap(), maxNodes: Int = DetectionConfig.MAX_NODES): String {
        val out = StringBuilder()
        out.append(HEADER).append('\n')
        out.append("# package=").append(tree.packageName).append('\n')
        notes.forEach { (k, v) -> out.append("# ").append(k).append('=').append(sanitize(v)).append('\n') }
        var written = 0
        val stack = ArrayDeque<Pair<UiNode, Int>>()
        tree.root?.let { stack.addLast(it to 0) }
        while (stack.isNotEmpty() && written < maxNodes) {
            val (node, depth) = stack.removeLast()
            out.append(line(node, depth)).append('\n')
            written++
            if (!node.isVisibleToUser || depth >= DetectionConfig.MAX_DEPTH) continue
            for (i in node.childCount - 1 downTo 0) node.child(i)?.let { stack.addLast(it to depth + 1) }
        }
        if (stack.isNotEmpty()) out.append("# truncated=true\n")
        return out.toString()
    }

    fun parse(text: String): DumpTree {
        var packageName = ""
        val roots = ArrayList<DumpNode>()
        val path = ArrayList<DumpNode>()
        text.lineSequence().forEachIndexed { index, raw ->
            val lineText = raw.trimEnd()
            if (lineText.isEmpty()) return@forEachIndexed
            if (lineText.startsWith("#")) {
                if (lineText.startsWith("# package=")) packageName = lineText.removePrefix("# package=").trim()
                return@forEachIndexed
            }
            val cols = lineText.split('\t')
            require(cols.size >= 7) { "Linha ${index + 1}: esperadas 7 colunas, encontradas ${cols.size}" }
            val depth = cols[0].toInt()
            val b = cols[3].split(',').map { it.toFloat() }
            val (rows, columns) = if (cols[5] == NONE) null to null else cols[5].split(',').let { it[0].toInt() to it[1].toInt() }
            val node = DumpNode(
                className = cols[1].takeUnless { it == NONE },
                viewId = cols[2].takeUnless { it == NONE },
                bounds = RelBounds(b[0], b[1], b[2], b[3]),
                isVisibleToUser = 'V' in cols[4],
                isScrollable = 'S' in cols[4],
                isSelected = 's' in cols[4],
                contentDescription = cols[6].takeUnless { it == NONE },
                collectionRowCount = rows,
                collectionColumnCount = columns,
                identityKey = index,
            )
            require(depth <= path.size) { "Linha ${index + 1}: profundidade $depth sem pai" }
            while (path.size > depth) path.removeAt(path.lastIndex)
            if (depth == 0) roots += node else path.last().children += node
            path += node
        }
        require(roots.size <= 1) { "Mais de uma raiz no arquivo" }
        return DumpTree(packageName, roots.firstOrNull())
    }

    private fun line(node: UiNode, depth: Int): String {
        val flags = buildString {
            if (node.isVisibleToUser) append('V')
            if (node.isScrollable) append('S')
            if (node.isSelected) append('s')
            if (!node.text.isNullOrEmpty()) append('T')
            if (!node.contentDescription.isNullOrEmpty()) append('D')
        }.ifEmpty { NONE }
        val b = node.bounds
        val rows = node.collectionRowCount
        val cols = node.collectionColumnCount
        return listOf(
            depth.toString(),
            sanitize(node.className ?: NONE),
            sanitize(node.viewId ?: NONE),
            String.format(Locale.ROOT, "%.3f,%.3f,%.3f,%.3f", b.left, b.top, b.right, b.bottom),
            flags,
            if (rows != null && cols != null) "$rows,$cols" else NONE,
            allowedLabel(node) ?: NONE,
        ).joinToString("\t")
    }

    private fun allowedLabel(node: UiNode): String? {
        val raw = (node.contentDescription ?: node.text)?.toString()?.trim()?.lowercase() ?: return null
        return raw.takeIf { it in LABEL_ALLOWLIST }
    }

    private fun sanitize(value: String) = value.replace('\t', ' ').replace('\n', ' ')
}

class DumpTree(override val packageName: String, override val root: DumpNode?) : UiTree

class DumpNode(
    override val className: String?,
    override val viewId: String?,
    override val bounds: RelBounds,
    override val isVisibleToUser: Boolean,
    override val isScrollable: Boolean,
    override val isSelected: Boolean,
    override val contentDescription: CharSequence?,
    override val collectionRowCount: Int?,
    override val collectionColumnCount: Int?,
    override val identityKey: Int,
) : UiNode {
    val children = ArrayList<DumpNode>()
    override val text: CharSequence? get() = null
    override val childCount: Int get() = children.size
    override fun child(index: Int): UiNode? = children.getOrNull(index)
}
