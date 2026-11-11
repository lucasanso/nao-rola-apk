package com.lucasanso.naorola.monitoring

import com.lucasanso.naorola.core.AppLogger
import com.lucasanso.naorola.core.Clock
import com.lucasanso.naorola.detection.classifier.Classification
import com.lucasanso.naorola.detection.diagnostics.StructureDump
import com.lucasanso.naorola.detection.rules.BrowserRules
import com.lucasanso.naorola.detection.rules.InstagramRules
import com.lucasanso.naorola.detection.rules.YouTubeRules
import com.lucasanso.naorola.detection.signal.DetectionResult
import com.lucasanso.naorola.detection.tree.UiTree
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Device/app facts written in the header of a dump (no personal data). */
data class DiagnosticContext(
    val appVersion: String,
    val androidRelease: String,
    val sdkInt: Int,
    val device: String,
    val versionOf: (String) -> String?,
)

/** A saved capture and the label the user gave it ("should block" / "should not block"). */
data class CaptureInfo(val file: File, val expectation: StructureDump.Expectation?)

/**
 * Diagnostic mode: when armed, the next screen of a supported app is saved as a structure-only
 * dump (see [StructureDump]) together with what the detector concluded. Files stay in the app's
 * private storage until the user chooses to share or delete them.
 */
class DiagnosticRecorder(
    private val directory: File,
    private val clock: Clock,
    private val logger: AppLogger,
    private val context: DiagnosticContext,
) {
    private val _armed = MutableStateFlow(false)
    val armed: StateFlow<Boolean> = _armed.asStateFlow()

    private val _files = MutableStateFlow(listCaptures())
    val files: StateFlow<List<CaptureInfo>> = _files.asStateFlow()

    fun arm() {
        _armed.value = true
    }

    fun disarm() {
        _armed.value = false
    }

    /** Called for every evaluation; writes at most one file per arming. */
    fun captureIfArmed(tree: UiTree, detection: DetectionResult, classification: Classification) {
        if (!_armed.compareAndSet(expect = true, update = false)) return
        try {
            val notes = linkedMapOf(
                "capturado_em" to timestamp("yyyy-MM-dd HH:mm:ss"),
                "versao_app_observado" to (context.versionOf(tree.packageName) ?: "desconhecida"),
                "versao_nao_rola" to context.appVersion,
                "android" to "${context.androidRelease} (API ${context.sdkInt})",
                "aparelho" to context.device,
                "regras" to RULE_SETS,
                "detector" to detection.detectorId,
                "sinais" to detection.signals.joinToString { "${it.id}:${it.polarity.name.first()}${it.weight}" }.ifEmpty { "nenhum" },
                "contexto" to detection.context.name,
                "classificacao" to describe(classification),
            )
            directory.mkdirs()
            val file = File(directory, "estrutura-${tree.packageName}-${timestamp("yyyyMMdd-HHmmss")}.txt")
            file.writeText(StructureDump.write(tree, notes))
            logger.info(TAG, "Captura salva: ${file.name}")
            refresh()
        } catch (e: Exception) {
            logger.error(TAG, "Falha ao salvar captura", e)
        }
    }

    /**
     * Records what should have happened on the captured screen. Labelled captures placed in
     * docs/capturas become automated tests of the detectors.
     */
    fun setExpectation(file: File, expectation: StructureDump.Expectation?) {
        if (file.parentFile != directory || !file.exists()) return
        try {
            file.writeText(StructureDump.withExpectation(file.readText(), expectation))
        } catch (e: Exception) {
            logger.error(TAG, "Falha ao rotular captura", e)
        }
        refresh()
    }

    fun delete(file: File) {
        if (file.parentFile == directory) file.delete()
        refresh()
    }

    fun deleteAll() {
        captureFiles().forEach { it.delete() }
        refresh()
    }

    fun refresh() {
        _files.value = listCaptures()
    }

    private fun captureFiles(): List<File> =
        directory.listFiles { f -> f.isFile && f.name.endsWith(".txt") }?.toList().orEmpty()

    /** Newest first, by the time in the file name (relabelling must not reorder the list). */
    private fun listCaptures(): List<CaptureInfo> = captureFiles()
        .sortedByDescending { STAMP.find(it.name)?.value ?: it.lastModified().toString() }
        .map { file ->
            val header = runCatching { file.useLines { lines -> lines.takeWhile { it.startsWith("#") }.joinToString("\n") } }
            CaptureInfo(file, header.getOrNull()?.let(StructureDump::expectationOf))
        }

    private fun timestamp(pattern: String) = SimpleDateFormat(pattern, Locale.ROOT).format(Date(clock.nowMillis()))

    private fun describe(c: Classification) = when (c) {
        is Classification.ShortVideo -> "VIDEO_CURTO ${c.surface} confianca=${c.confidence}"
        is Classification.NotShortVideo -> "NAO pontuacao=${c.score}" + (c.vetoedBy?.let { " veto=$it" } ?: "")
    }

    private companion object {
        const val TAG = "Diagnostico"
        val STAMP = Regex("""\d{8}-\d{6}(?=\.txt$)""")
        val RULE_SETS = listOf(YouTubeRules.INFO, InstagramRules.INFO, BrowserRules.INFO)
            .joinToString { "${it.name} ${it.version}" }
    }
}
