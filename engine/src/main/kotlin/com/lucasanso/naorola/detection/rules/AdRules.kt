package com.lucasanso.naorola.detection.rules

/**
 * Labels that mark ads and "skip ad" buttons. Labels are UI texts in lower case; they are matched
 * in memory and never stored. Only exact UI words count, so a video titled "Anúncio do produto"
 * is not mistaken for an ad.
 */
object AdRules {
    /** Keys used by detectors to remember nodes with these labels (see ScanRequest.labelKey). */
    const val KEY_AD = "ad"
    const val KEY_SKIP = "skip"
    const val KEY_SKIP_WEAK = "skip_weak"

    /** What diagnostic dumps write instead of the real ad label (which may name the advertiser). */
    const val CANONICAL_AD_LABEL = "patrocinado"

    private val AD_EXACT = setOf(
        "patrocinado", "patrocinada", "sponsored", "anúncio", "anuncio", "ad", "publicidade",
        "promovido", "promovida", "promoted", "conteúdo patrocinado", "conteudo patrocinado",
    )

    /** "Patrocinado · Loja X", "Sponsored · Brand". */
    private val AD_FIRST_WORD = setOf("patrocinado", "patrocinada", "sponsored")

    /** "Anúncio · 0:15", "Ad · 1 of 2": only with the separator YouTube uses. */
    private val AD_FIRST_WORD_WITH_SEPARATOR = setOf("anúncio", "anuncio", "ad")
    private val SEPARATORS = charArrayOf('·', '•')

    val SKIP_LABELS = setOf(
        "pular anúncio", "pular anúncios", "pular anuncio", "pular anuncios", "skip ad", "skip ads",
    )

    /** Short labels of the newer skip button; only trusted when an ad is evidently playing. */
    val SKIP_WEAK_LABELS = setOf("pular", "skip")

    fun isAdLabel(label: String): Boolean {
        if (label in AD_EXACT) return true
        val firstWord = label.split(' ', '·', '•', ',').firstOrNull { it.isNotEmpty() } ?: return false
        if (firstWord in AD_FIRST_WORD) return true
        return firstWord in AD_FIRST_WORD_WITH_SEPARATOR && label.indexOfAny(SEPARATORS) > 0
    }

    /** Key for [label] in ad-aware scans, or null. */
    fun keyOf(label: String): String? = when {
        isAdLabel(label) -> KEY_AD
        label in SKIP_LABELS -> KEY_SKIP
        label in SKIP_WEAK_LABELS -> KEY_SKIP_WEAK
        else -> null
    }
}
