package com.lucasanso.naorola.detection.rules

import com.lucasanso.naorola.domain.model.Surface

/**
 * Browser rules: only the address bar is read, the URL is interpreted in memory and only the
 * resulting yes/no leaves this object. URLs are never stored or logged.
 */
object BrowserRules {
    const val CHROME = "com.android.chrome"
    const val SAMSUNG_INTERNET = "com.sec.android.app.sbrowser"

    val INFO = RuleSetInfo(name = "Navegadores", version = "2026.11.0", status = "id do Chrome conhecido; Samsung Internet candidato")

    /** Address bar ids per browser. Editable fields at the top of the screen are a fallback. */
    val ADDRESS_BAR_IDS: Map<String, Set<String>> = mapOf(
        CHROME to setOf("url_bar"),
        SAMSUNG_INTERNET to setOf("location_bar_edit_text"),
    )

    val PACKAGES: Set<String> get() = ADDRESS_BAR_IDS.keys

    private val YOUTUBE_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com")
    private val INSTAGRAM_HOSTS = setOf("instagram.com", "www.instagram.com", "m.instagram.com")

    /** Returns the short-video surface a URL points to, or null. Accepts URLs without scheme. */
    fun surfaceFor(raw: String): Surface? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val withoutScheme = trimmed.replaceFirst(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://"), "")
        val hostEnd = withoutScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val hostPart = if (hostEnd < 0) withoutScheme else withoutScheme.substring(0, hostEnd)
        val path = if (hostEnd < 0) "" else withoutScheme.substring(hostEnd).substringBefore('?').substringBefore('#')
        val host = hostPart.substringAfterLast('@').substringBefore(':').trimEnd('.').lowercase()
        val lowerPath = path.lowercase()
        return when {
            host in YOUTUBE_HOSTS && (lowerPath == "/shorts" || lowerPath.startsWith("/shorts/")) -> Surface.WEB_SHORTS
            host in INSTAGRAM_HOSTS &&
                (lowerPath.startsWith("/reel/") || lowerPath == "/reels" || lowerPath.startsWith("/reels/")) -> Surface.WEB_REELS
            else -> null
        }
    }
}
