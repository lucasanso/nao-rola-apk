package com.lucasanso.naorola.adblock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.lucasanso.naorola.R
import com.lucasanso.naorola.core.AppLogger
import com.lucasanso.naorola.domain.adblock.FilterList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.net.toUri
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The ad-block list that Samsung Internet reads through [ContentBlockerProvider]: Não-Rola's own
 * list plus, optionally, a list the user imports from a file. Works offline — the app has no
 * internet permission, so lists are never downloaded by it.
 *
 * Chrome offers no API for other apps to block its ads; that is explained in the app.
 */
class BrowserFilters(private val context: Context, private val logger: AppLogger) {

    data class Status(
        val bundledRules: Int = 0,
        val importedRules: Int = 0,
        val importedTruncated: Boolean = false,
        /** Browsers installed that understand content blockers. */
        val browsers: List<String> = emptyList(),
    )

    sealed interface ImportResult {
        data class Imported(val rules: Int, val ignoredLines: Int, val truncated: Boolean) : ImportResult
        data object Empty : ImportResult
        data object TooLarge : ImportResult
        data object Unreadable : ImportResult
    }

    private val directory = File(context.filesDir, DIRECTORY)
    private val served = File(directory, SERVED_FILE)
    private val imported = File(directory, IMPORTED_FILE)
    private val lock = Any()

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    /** The file to hand to the browser, built on first use. */
    fun servedFile(): File = synchronized(lock) {
        if (!served.exists()) rebuild()
        served
    }

    fun refreshStatus() {
        val importedRules = if (imported.exists()) imported.useLines { lines -> lines.count { it.isNotBlank() } } else 0
        _status.value = Status(
            bundledRules = bundled().size,
            importedRules = importedRules,
            importedTruncated = _status.value.importedTruncated,
            browsers = installedBrowsers(),
        )
    }

    /** Reads a list chosen by the user (AdBlock, hosts or one domain per line). */
    fun import(uri: Uri): ImportResult {
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > MAX_IMPORT_BYTES) return ImportResult.TooLarge
                }
                out.toString(Charsets.UTF_8.name())
            } ?: return ImportResult.Unreadable
        } catch (e: Exception) {
            logger.error(TAG, "Falha ao ler a lista importada", e)
            return ImportResult.Unreadable
        }
        val parsed = FilterList.parse(text)
        if (parsed.rules.isEmpty()) return ImportResult.Empty
        synchronized(lock) {
            directory.mkdirs()
            imported.writeText(parsed.rules.joinToString("\n", postfix = "\n"))
            rebuild()
        }
        _status.value = _status.value.copy(importedTruncated = parsed.truncated)
        logger.info(TAG, "Lista importada: ${parsed.rules.size} regras")
        notifyBrowsers()
        refreshStatus()
        return ImportResult.Imported(parsed.rules.size, parsed.ignoredLines, parsed.truncated)
    }

    fun removeImported() {
        synchronized(lock) {
            imported.delete()
            rebuild()
        }
        _status.value = _status.value.copy(importedTruncated = false)
        notifyBrowsers()
        refreshStatus()
    }

    /** Asks Samsung Internet to read the list again. */
    fun notifyBrowsers() {
        for (browser in installedBrowsers()) {
            runCatching {
                context.sendBroadcast(
                    Intent(ACTION_UPDATE).setData("package:${context.packageName}".toUri()).setPackage(browser),
                )
            }.onFailure { logger.warn(TAG, "Falha ao avisar $browser", it) }
        }
    }

    fun installedBrowsers(): List<String> = SAMSUNG_BROWSERS.filter { pkg ->
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun launchIntent(browser: String): Intent? = context.packageManager.getLaunchIntentForPackage(browser)

    private fun bundled(): List<String> =
        context.resources.openRawResource(R.raw.filtros_anuncios).bufferedReader().use { FilterList.parse(it.readText()).rules }

    /** Writes the served file atomically, so the browser never reads half a list. */
    private fun rebuild() {
        directory.mkdirs()
        val user = if (imported.exists()) imported.readLines().filter { it.isNotBlank() } else emptyList()
        val temp = File(directory, "$SERVED_FILE.tmp")
        temp.writeText(FilterList.render(HEADER, bundled(), user))
        if (!temp.renameTo(served)) {
            served.delete()
            temp.renameTo(served)
        }
    }

    companion object {
        private const val TAG = "Anuncios"
        private const val DIRECTORY = "adblock"
        private const val SERVED_FILE = "filtros.txt"
        private const val IMPORTED_FILE = "lista-importada.txt"
        private const val MAX_IMPORT_BYTES = 8 * 1024 * 1024
        private const val BUFFER_BYTES = 64 * 1024
        private const val HEADER = "Não-Rola · bloqueio de anúncios para o Samsung Internet"

        /** Samsung Internet's content-blocker contract (public interface names only). */
        const val ACTION_UPDATE = "com.samsung.android.sbrowser.contentBlocker.ACTION_UPDATE"
        const val ACTION_SETTING = "com.samsung.android.sbrowser.contentBlocker.ACTION_SETTING"
        val SAMSUNG_BROWSERS = listOf("com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta")
    }
}
