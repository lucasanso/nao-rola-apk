package com.lucasanso.naorola.adult

import android.content.Context
import android.net.Uri
import com.lucasanso.naorola.adblock.BrowserFilters.ImportResult
import com.lucasanso.naorola.adblock.ListFileReader
import com.lucasanso.naorola.core.AppLogger
import com.lucasanso.naorola.detection.rules.AdultRules
import com.lucasanso.naorola.detection.rules.AdultSiteMatcher
import com.lucasanso.naorola.domain.adblock.FilterList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The adult filter's site list: the bundled domains (in the engine) plus a list the user may
 * import (hosts, AdBlock or one domain per line). Only domain names are kept, in private storage.
 */
class AdultSiteStore(context: Context, private val logger: AppLogger) {
    private val appContext = context.applicationContext
    private val file = File(context.filesDir, "adult/sites-importados.txt")

    val matcher = AdultSiteMatcher()

    private val _importedCount = MutableStateFlow(0)
    val importedCount: StateFlow<Int> = _importedCount.asStateFlow()

    val bundledCount: Int get() = AdultRules.BUNDLED_DOMAINS.size

    /** Loads the imported list; call off the main thread. */
    fun load() {
        val domains = if (file.exists()) file.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet() else emptySet()
        matcher.extraDomains = domains
        _importedCount.value = domains.size
    }

    fun import(uri: Uri): ImportResult {
        val text = when (val read = ListFileReader.read(appContext, uri, logger)) {
            is ListFileReader.Read.Text -> read.value
            ListFileReader.Read.TooLarge -> return ImportResult.TooLarge
            ListFileReader.Read.Unreadable -> return ImportResult.Unreadable
        }
        val parsed = FilterList.parse(text)
        val domains = AdultSiteMatcher.domainsOf(parsed.rules)
        if (domains.isEmpty()) return ImportResult.Empty
        file.parentFile?.mkdirs()
        file.writeText(domains.joinToString("\n", postfix = "\n"))
        matcher.extraDomains = domains
        _importedCount.value = domains.size
        logger.info(TAG, "Lista de sites importada: ${domains.size} domínios")
        return ImportResult.Imported(domains.size, parsed.ignoredLines + (parsed.rules.size - domains.size), parsed.truncated)
    }

    fun removeImported() {
        file.delete()
        matcher.extraDomains = emptySet()
        _importedCount.value = 0
    }

    /** Rules for Samsung Internet's blocker. */
    fun browserRules(): List<String> = (AdultRules.BUNDLED_DOMAINS + matcher.extraDomains).map { "||$it^" }

    private companion object {
        const val TAG = "FiltroAdulto"
    }
}
