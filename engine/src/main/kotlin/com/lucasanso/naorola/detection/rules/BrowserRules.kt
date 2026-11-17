package com.lucasanso.naorola.detection.rules

import com.lucasanso.naorola.domain.model.Surface

/** Host, path and query of an address typed or shown in a browser. Never stored or logged. */
class ParsedUrl(val host: String, val path: String, val query: String) {
    override fun toString() = "ParsedUrl(…)"
}

/**
 * Browser rules: only the address bar is read, the URL is interpreted in memory and only the
 * resulting yes/no leaves this object. URLs are never stored or logged.
 */
object BrowserRules {
    const val CHROME = "com.android.chrome"
    const val SAMSUNG_INTERNET = "com.sec.android.app.sbrowser"

    val INFO = RuleSetInfo(
        name = "Navegadores",
        version = "2026.11.1",
        status = "id do Chrome conhecido; demais navegadores candidatos (com reserva pela posição)",
    )

    /**
     * Address bar ids per browser (Chromium-based browsers share "url_bar"). Editable fields at
     * the top of the screen are a fallback when an id changes.
     */
    val ADDRESS_BAR_IDS: Map<String, Set<String>> = mapOf(
        CHROME to setOf("url_bar"),
        SAMSUNG_INTERNET to setOf("location_bar_edit_text"),
        "com.sec.android.app.sbrowser.beta" to setOf("location_bar_edit_text"),
        "org.mozilla.firefox" to setOf("mozac_browser_toolbar_url_view", "mozac_browser_toolbar_edit_url_view"),
        "com.microsoft.emmx" to setOf("url_bar"),
        "com.brave.browser" to setOf("url_bar"),
        "com.opera.browser" to setOf("url_field"),
        "com.duckduckgo.mobile.android" to setOf("omnibarTextInput"),
    )

    val PACKAGES: Set<String> get() = ADDRESS_BAR_IDS.keys

    private val YOUTUBE_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com")
    private val INSTAGRAM_HOSTS = setOf("instagram.com", "www.instagram.com", "m.instagram.com")
    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    /** Parses an address with or without scheme; null when the text is not an address. */
    fun parse(raw: String): ParsedUrl? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val withoutScheme = trimmed.replaceFirst(SCHEME, "")
        val hostEnd = withoutScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val hostPart = if (hostEnd < 0) withoutScheme else withoutScheme.substring(0, hostEnd)
        val rest = if (hostEnd < 0) "" else withoutScheme.substring(hostEnd).substringBefore('#')
        val host = hostPart.substringAfterLast('@').substringBefore(':').trimEnd('.').lowercase()
        if ('.' !in host || host.startsWith('.')) return null
        return ParsedUrl(host, rest.substringBefore('?'), rest.substringAfter('?', ""))
    }

    /** Returns the short-video surface a URL points to, or null. Accepts URLs without scheme. */
    fun surfaceFor(raw: String): Surface? {
        val url = parse(raw) ?: return null
        val lowerPath = url.path.lowercase()
        return when {
            url.host in YOUTUBE_HOSTS && (lowerPath == "/shorts" || lowerPath.startsWith("/shorts/")) -> Surface.WEB_SHORTS
            url.host in INSTAGRAM_HOSTS &&
                (lowerPath.startsWith("/reel/") || lowerPath == "/reels" || lowerPath.startsWith("/reels/")) -> Surface.WEB_REELS
            else -> null
        }
    }
}
