package com.lucasanso.naorola.presentation.ads

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lucasanso.naorola.R
import com.lucasanso.naorola.adblock.BrowserFilters
import com.lucasanso.naorola.core.Edition
import com.lucasanso.naorola.core.SystemSettings
import com.lucasanso.naorola.di.AppContainer
import com.lucasanso.naorola.presentation.common.containerViewModel
import com.lucasanso.naorola.presentation.components.NoticeCard
import com.lucasanso.naorola.presentation.components.ScreenScaffold
import com.lucasanso.naorola.presentation.components.SectionCard
import com.lucasanso.naorola.presentation.components.SwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AdsState(val enabled: Boolean = true, val locked: Boolean = false, val loaded: Boolean = false)

class AdsViewModel(private val container: AppContainer) : ViewModel() {
    private val filters = container.browserFilters

    val state: StateFlow<AdsState> = container.settingsRepository.settings
        .map { AdsState(it.adBlockEnabled, locked = it.discipline.isActive(container.clock.nowMillis()), loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AdsState())

    val filterStatus: StateFlow<BrowserFilters.Status> = filters.status

    private val _importResult = MutableStateFlow<BrowserFilters.ImportResult?>(null)
    val importResult: StateFlow<BrowserFilters.ImportResult?> = _importResult.asStateFlow()

    fun refresh() = viewModelScope.launch(Dispatchers.IO) { filters.refreshStatus() }

    fun setEnabled(enabled: Boolean) = viewModelScope.launch { container.settingsRepository.edit { it.copy(adBlockEnabled = enabled) } }

    fun import(uri: Uri) = viewModelScope.launch(Dispatchers.IO) { _importResult.value = filters.import(uri) }

    fun removeImported() = viewModelScope.launch(Dispatchers.IO) {
        filters.removeImported()
        _importResult.value = null
    }

    fun launchIntent(browser: String) = filters.launchIntent(browser)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

@Composable
fun AdsScreen(onBack: () -> Unit) {
    val viewModel = containerViewModel { AdsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status by viewModel.filterStatus.collectAsStateWithLifecycle()
    val importResult by viewModel.importResult.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::import) }
    LaunchedEffect(Unit) { viewModel.refresh() }

    ScreenScaffold(title = stringResource(R.string.ads_title), onBack = onBack) {
        if (state.locked) NoticeCard(stringResource(R.string.locked_by_discipline))

        SectionCard(title = stringResource(R.string.ads_apps_title)) {
            if (Edition.inAppAdBlock) {
                SwitchRow(
                    title = stringResource(R.string.ads_toggle),
                    body = stringResource(R.string.ads_toggle_body),
                    checked = state.enabled,
                    enabled = state.loaded && !state.locked,
                    onCheckedChange = { viewModel.setEnabled(it) },
                )
                Text(
                    stringResource(R.string.ads_apps_limits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(stringResource(R.string.ads_play_edition), style = MaterialTheme.typography.bodyMedium)
            }
        }

        SectionCard(title = stringResource(R.string.ads_browsers_title)) {
            Text(stringResource(R.string.ads_samsung_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.ads_samsung_body), style = MaterialTheme.typography.bodyMedium)
            val browser = status.browsers.firstOrNull()
            if (browser != null) {
                Button(onClick = { viewModel.launchIntent(browser)?.let { context.startActivity(it) } }) {
                    Text(stringResource(R.string.ads_samsung_open))
                }
            } else {
                Text(
                    stringResource(R.string.ads_samsung_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                pluralStringResource(R.plurals.ads_list_count, status.bundledRules, status.bundledRules),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (status.importedRules > 0) {
                Text(
                    pluralStringResource(R.plurals.ads_imported_count, status.importedRules, status.importedRules),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            Text(stringResource(R.string.ads_import_body), style = MaterialTheme.typography.bodyMedium)
            importResult?.let { ImportMessage(it) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) }) {
                    Text(stringResource(R.string.ads_import))
                }
                if (status.importedRules > 0) {
                    TextButton(onClick = { viewModel.removeImported() }) { Text(stringResource(R.string.ads_import_remove)) }
                }
            }
            HorizontalDivider()
            Text(stringResource(R.string.ads_chrome_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.ads_chrome_body), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { SystemSettings.openNetworkSettings(context) }) {
                Text(stringResource(R.string.ads_chrome_open))
            }
        }
    }
}

@Composable
private fun ImportMessage(result: BrowserFilters.ImportResult) {
    val (text, error) = when (result) {
        is BrowserFilters.ImportResult.Imported -> {
            val ok = pluralStringResource(R.plurals.ads_import_ok, result.rules, result.rules)
            (if (result.truncated) "$ok ${stringResource(R.string.ads_import_truncated)}" else ok) to false
        }
        BrowserFilters.ImportResult.Empty -> stringResource(R.string.ads_import_empty) to true
        BrowserFilters.ImportResult.TooLarge -> stringResource(R.string.ads_import_too_large) to true
        BrowserFilters.ImportResult.Unreadable -> stringResource(R.string.ads_import_unreadable) to true
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}
