package com.lucasanso.naorola.detection.rules

import com.lucasanso.naorola.domain.model.Surface
import java.net.URLDecoder
import java.text.Normalizer

/**
 * Adult filter rules. Everything happens in memory on the address shown in the browser; only
 * "adult site", "explicit search" or nothing leaves this file.
 *
 * Sites are recognised by a bundled list of major adult domains (plus any list the user
 * imports), adult-only top-level domains and unambiguous words in the host name. Searches are
 * recognised by explicit terms only: "sexo" alone is NOT blocked (health and education).
 */
object AdultRules {
    val INFO = RuleSetInfo(name = "Filtro adulto", version = "2026.11.0", status = "lista própria + palavras; importação opcional")

    private const val BUNDLED_RESOURCE = "/naorola/adult_domains.txt"

    /** Bundled domains (one per line, "#" comments). */
    val BUNDLED_DOMAINS: Set<String> by lazy {
        AdultRules::class.java.getResourceAsStream(BUNDLED_RESOURCE)?.bufferedReader()?.use { reader ->
            reader.lineSequence().map { it.substringBefore('#').trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        }.orEmpty()
    }

    val ADULT_TLDS = setOf("xxx", "porn", "adult", "sex")

    /** Words that only appear in adult host names (checked inside each label). */
    val HOST_WORDS = listOf("porn", "xxx", "hentai", "xvideo", "xnxx", "xhamster", "nsfw")

    /** Single words that make a search explicit (accents removed). */
    val SEARCH_WORDS = setOf(
        "porno", "porn", "porns", "pornografia", "pornografico", "pornograficos", "xvideos", "xnxx", "pornhub",
        "xhamster", "redtube", "youporn", "hentai", "rule34", "nudes", "putaria", "xxx", "nsfw", "camgirl",
        "camgirls", "onlyfans", "brazzers", "xvideo", "nudez",
    )

    /** Phrases that make a search explicit; their words alone do not. */
    val SEARCH_PHRASES: List<List<String>> = listOf(
        "video de sexo", "videos de sexo", "video sexo", "videos sexo", "filme de sexo", "sexo explicito",
        "sexo amador", "sexo ao vivo", "mulher nua", "mulheres nuas", "mulher pelada", "mulheres peladas",
        "fotos nuas", "foto nua", "garota nua", "garotas nuas", "sex video", "sex videos", "naked women",
    ).map { it.split(' ') }

    private val SEARCH_HOSTS = setOf(
        "bing.com", "duckduckgo.com", "search.yahoo.com", "search.brave.com", "ecosia.org", "startpage.com",
    )
    private val SEARCH_PARAMS = setOf("q", "p", "query", "text")

    fun isSearchEngine(host: String): Boolean {
        val h = host.removePrefix("www.").removePrefix("m.")
        return h.startsWith("google.") || h.startsWith("yandex.") || h in SEARCH_HOSTS || h.endsWith(".search.yahoo.com")
    }

    /** Lower case, no accents, only letters and digits as words. */
    fun words(text: String): List<String> =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }

    fun isExplicitSearch(text: String): Boolean {
        val tokens = words(text)
        if (tokens.any { it in SEARCH_WORDS }) return true
        return SEARCH_PHRASES.any { phrase -> tokens.windowed(phrase.size).any { it == phrase } }
    }

    /** Search terms of a search-engine address, or null. */
    fun searchTermsOf(url: ParsedUrl): String? {
        if (!isSearchEngine(url.host)) return null
        return url.query.split('&').firstNotNullOfOrNull { pair ->
            val name = pair.substringBefore('=')
            if (name !in SEARCH_PARAMS) return@firstNotNullOfOrNull null
            runCatching { URLDecoder.decode(pair.substringAfter('=', ""), Charsets.UTF_8.name()) }.getOrNull()
        }
    }
}

/**
 * Decides whether an address-bar text is an adult site or an explicit search. [extraDomains]
 * holds a list imported by the user and can be replaced at any time.
 */
class AdultSiteMatcher(
    private val bundled: Set<String> = AdultRules.BUNDLED_DOMAINS,
    @Volatile var extraDomains: Set<String> = emptySet(),
) {
    fun surfaceFor(raw: String): Surface? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val url = BrowserRules.parse(text)
            // Not an address: search terms shown in (or typed into) the address bar.
            ?: return if (AdultRules.isExplicitSearch(text)) Surface.ADULT_SEARCH else null
        if (isAdultHost(url.host)) return Surface.ADULT_SITE
        val terms = AdultRules.searchTermsOf(url) ?: return null
        return if (AdultRules.isExplicitSearch(terms)) Surface.ADULT_SEARCH else null
    }

    fun isAdultHost(rawHost: String): Boolean {
        val host = rawHost.lowercase().removePrefix("www.")
        val labels = host.split('.')
        if (labels.last() in AdultRules.ADULT_TLDS) return true
        // The host itself or any parent domain listed ("br.pornhub.com" → "pornhub.com").
        for (i in 0 until labels.size - 1) {
            val candidate = labels.subList(i, labels.size).joinToString(".")
            if (candidate in bundled || candidate in extraDomains) return true
        }
        return labels.dropLast(1).any { label -> AdultRules.HOST_WORDS.any { it in label } }
    }

    companion object {
        /** Domains from AdBlock-style rules ("||example.com^") produced by FilterList. */
        fun domainsOf(rules: List<String>): Set<String> = rules.mapNotNull { rule ->
            rule.takeIf { it.startsWith("||") && it.endsWith("^") }?.removePrefix("||")?.removeSuffix("^")
                ?.takeIf { '/' !in it && '*' !in it }
        }.toSet()
    }
}
