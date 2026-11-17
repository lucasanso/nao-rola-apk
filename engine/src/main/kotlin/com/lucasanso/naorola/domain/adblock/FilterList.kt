package com.lucasanso.naorola.domain.adblock

/**
 * Normalizes ad-block lists for the Samsung Internet content blocker, which reads AdBlock
 * syntax ("||ads.example.com^"). Accepts AdBlock lists, hosts files ("0.0.0.0 ads.example.com")
 * and plain domain lists; drops comments, headers and duplicates.
 */
object FilterList {
    /** Upper bound of rules handed to the browser: huge lists slow page loads down. */
    const val MAX_RULES = 150_000

    data class Parsed(val rules: List<String>, val ignoredLines: Int, val truncated: Boolean)

    private val HOSTS_ADDRESSES = setOf("0.0.0.0", "127.0.0.1", "::", "::1")
    private val LOCAL_NAMES = setOf("localhost", "localhost.localdomain", "local", "broadcasthost", "ip6-localhost", "ip6-loopback", "0.0.0.0")
    private val DOMAIN = Regex("^(?=.{1,253}$)([a-z0-9_]([a-z0-9_-]{0,61}[a-z0-9])?\\.)+[a-z][a-z0-9-]{0,62}$")
    private val COSMETIC_MARKERS = listOf("##", "#@#", "#?#", "#$#")
    private val ADBLOCK_MARKERS = listOf("||", "@@", "^", "$", "/", "|", "*")

    fun parse(text: String, maxRules: Int = MAX_RULES): Parsed {
        val rules = LinkedHashSet<String>()
        var ignored = 0
        var truncated = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) continue
            if (line.startsWith("#") && COSMETIC_MARKERS.none { line.startsWith(it) }) continue // hosts comment
            val rule = ruleOf(line)
            if (rule == null) {
                ignored++
                continue
            }
            if (rules.size >= maxRules) {
                truncated = true
                break
            }
            rules += rule
        }
        return Parsed(rules.toList(), ignored, truncated)
    }

    /** One line in AdBlock syntax, or null when the line is not a rule. */
    fun ruleOf(line: String): String? {
        // Element-hiding rules may contain spaces inside their CSS selector.
        if (COSMETIC_MARKERS.any { it in line }) return line
        if (line.startsWith("#")) return null
        val parts = line.substringBefore(" #").substringBefore("\t#").split(' ', '\t').filter { it.isNotEmpty() }
        if (parts.size >= 2 && parts[0] in HOSTS_ADDRESSES) {
            val host = parts[1].lowercase()
            return if (host in LOCAL_NAMES || !DOMAIN.matches(host)) null else "||$host^"
        }
        if (parts.size == 1 && DOMAIN.matches(line.lowercase())) return "||${line.lowercase()}^"
        return line.takeIf { parts.size == 1 && ADBLOCK_MARKERS.any { marker -> marker in line } }
    }

    /** Joins lists into the file served to the browser. */
    fun render(header: String, vararg lists: List<String>): String = buildString {
        header.lines().forEach { append("! ").append(it).append('\n') }
        lists.asSequence().flatten().distinct().forEach { append(it).append('\n') }
    }
}
