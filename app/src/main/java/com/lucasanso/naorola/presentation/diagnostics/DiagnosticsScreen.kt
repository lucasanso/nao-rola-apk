package com.lucasanso.naorola.presentation.diagnostics

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lucasanso.naorola.R
import com.lucasanso.naorola.core.SystemSettings
import com.lucasanso.naorola.detection.diagnostics.StructureDump
import com.lucasanso.naorola.di.AppContainer
import com.lucasanso.naorola.monitoring.CaptureInfo
import com.lucasanso.naorola.presentation.common.containerViewModel
import com.lucasanso.naorola.presentation.components.ScreenScaffold
import com.lucasanso.naorola.presentation.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DiagnosticsViewModel(private val container: AppContainer) : ViewModel() {
    val armed: StateFlow<Boolean> = container.diagnostics.armed
    val files: StateFlow<List<CaptureInfo>> = container.diagnostics.files
    val serviceConnected: StateFlow<Boolean> = container.serviceConnected

    private val _log = MutableStateFlow("")
    val log: StateFlow<String> = _log.asStateFlow()

    fun toggleCapture() = if (armed.value) container.diagnostics.disarm() else container.diagnostics.arm()

    fun delete(file: File) = viewModelScope.launch(Dispatchers.IO) { container.diagnostics.delete(file) }

    fun label(file: File, expectation: StructureDump.Expectation?) =
        viewModelScope.launch(Dispatchers.IO) { container.diagnostics.setExpectation(file, expectation) }

    fun deleteAll() = viewModelScope.launch(Dispatchers.IO) { container.diagnostics.deleteAll() }

    fun refresh() = viewModelScope.launch(Dispatchers.IO) {
        container.diagnostics.refresh()
        _log.value = container.logger.readAll().lines().takeLast(LOG_LINES_SHOWN).joinToString("\n")
    }

    fun clearLog() {
        container.logger.clear()
        _log.value = ""
    }

    /** Joins the rotating log files into one file that can be shared. */
    suspend fun exportLog(directory: File): File = withContext(Dispatchers.IO) {
        File(directory, "naorola-log-exportado.txt").apply { writeText(container.logger.readAll()) }
    }

    private companion object {
        const val LOG_LINES_SHOWN = 200
    }
}

@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val viewModel = containerViewModel { DiagnosticsViewModel(it) }
    val armed by viewModel.armed.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val connected by viewModel.serviceConnected.collectAsStateWithLifecycle()
    val log by viewModel.log.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooser = stringResource(R.string.share_chooser)
    LaunchedEffect(Unit) { viewModel.refresh() }

    ScreenScaffold(title = stringResource(R.string.diagnostics_title), onBack = onBack) {
        SectionCard {
            Text(stringResource(R.string.diagnostics_body), style = MaterialTheme.typography.bodyMedium)
            if (connected) {
                Button(onClick = viewModel::toggleCapture, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (armed) R.string.diagnostics_waiting else R.string.diagnostics_capture))
                }
            } else {
                Text(
                    stringResource(R.string.diagnostics_needs_service),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        SectionCard(title = stringResource(R.string.diagnostics_captures)) {
            if (files.isEmpty()) {
                Text(stringResource(R.string.diagnostics_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    stringResource(R.string.diagnostics_label_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            files.forEach { capture ->
                val file = capture.file
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(file.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            Formatter.formatShortFileSize(context, file.length()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { SystemSettings.shareFile(context, file, chooser) }) {
                        Icon(painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.action_share))
                    }
                    IconButton(onClick = { viewModel.delete(file) }) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.action_delete))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        StructureDump.Expectation.BLOCK to R.string.diagnostics_expect_block,
                        StructureDump.Expectation.ALLOW to R.string.diagnostics_expect_allow,
                    ).forEach { (expectation, label) ->
                        val selected = capture.expectation == expectation
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.label(file, if (selected) null else expectation) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }
            if (files.isNotEmpty()) {
                TextButton(onClick = { viewModel.deleteAll() }) { Text(stringResource(R.string.diagnostics_delete_all)) }
            }
        }

        SectionCard(title = stringResource(R.string.diagnostics_log)) {
            Text(
                stringResource(R.string.diagnostics_log_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                log.ifEmpty { stringResource(R.string.diagnostics_log_empty) },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
            )
            Row {
                val logDirectory = File(context.filesDir, "logs")
                val exportScope = rememberCoroutineScope()
                OutlinedButton(onClick = {
                    exportScope.launch { SystemSettings.shareFile(context, viewModel.exportLog(logDirectory), chooser) }
                }) { Text(stringResource(R.string.action_share)) }
                TextButton(onClick = viewModel::clearLog) { Text(stringResource(R.string.action_clear)) }
            }
        }
    }
}
