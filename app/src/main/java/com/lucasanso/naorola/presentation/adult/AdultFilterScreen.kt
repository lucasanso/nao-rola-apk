package com.lucasanso.naorola.presentation.adult

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lucasanso.naorola.R
import com.lucasanso.naorola.adblock.BrowserFilters
import com.lucasanso.naorola.core.PrivateDns
import com.lucasanso.naorola.core.SystemSettings
import com.lucasanso.naorola.di.AppContainer
import com.lucasanso.naorola.presentation.common.containerViewModel
import com.lucasanso.naorola.presentation.components.ChoiceChips
import com.lucasanso.naorola.presentation.components.NoticeCard
import com.lucasanso.naorola.presentation.components.ScreenScaffold
import com.lucasanso.naorola.presentation.components.SectionCard
import com.lucasanso.naorola.presentation.components.SwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AdultState(
    val enabled: Boolean = false,
    val searchBlock: Boolean = true,
    val locked: Boolean = false,
    val loaded: Boolean = false,
)

data class DnsState(
    val canManage: Boolean = false,
    val state: PrivateDns.State = PrivateDns.State.Unknown,
    val selected: PrivateDns.Provider = PrivateDns.Provider.ADGUARD_FAMILY,
)

class AdultFilterViewModel(private val container: AppContainer) : ViewModel() {
    private val store = container.adultSites

    val state: StateFlow<AdultState> = container.settingsRepository.settings
        .map { AdultState(it.adultBlockEnabled, it.adultSearchBlock, it.discipline.isActive(container.clock.nowMillis()), loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AdultState())

    val importedCount: StateFlow<Int> = store.importedCount
    val bundledCount: Int get() = store.bundledCount

    private val _importResult = MutableStateFlow<BrowserFilters.ImportResult?>(null)
    val importResult: StateFlow<BrowserFilters.ImportResult?> = _importResult.asStateFlow()

    private val _dns = MutableStateFlow(DnsState())
    val dns: StateFlow<DnsState> = _dns.asStateFlow()

    fun refreshDns() {
        val context = container.context
        val current = PrivateDns.state(context)
        _dns.value = _dns.value.copy(
            canManage = PrivateDns.canManage(context),
            state = current,
            selected = (current as? PrivateDns.State.Family)?.provider ?: _dns.value.selected,
        )
    }

    fun setEnabled(enabled: Boolean) = viewModelScope.launch { container.settingsRepository.edit { it.copy(adultBlockEnabled = enabled) } }

    fun setSearchBlock(enabled: Boolean) = viewModelScope.launch { container.settingsRepository.edit { it.copy(adultSearchBlock = enabled) } }

    fun import(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        _importResult.value = store.import(uri)
        syncBrowserList()
    }

    fun removeImported() = viewModelScope.launch(Dispatchers.IO) {
        store.removeImported()
        _importResult.value = null
        syncBrowserList()
    }

    fun select(provider: PrivateDns.Provider) {
        _dns.value = _dns.value.copy(selected = provider)
    }

    fun enableDns() {
        if (PrivateDns.enable(container.context, _dns.value.selected)) {
            container.logger.info(TAG, "DNS familiar ativado")
        }
        refreshDns()
    }

    fun disableDns() {
        if (PrivateDns.disable(container.context)) container.logger.info(TAG, "DNS familiar desativado")
        refreshDns()
    }

    private suspend fun syncBrowserList() {
        if (container.settingsRepository.settings.first().adultBlockEnabled) {
            container.browserFilters.setAdultRules(store.browserRules())
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TAG = "FiltroAdulto"
    }
}

@Composable
fun AdultFilterScreen(onBack: () -> Unit) {
    val viewModel = containerViewModel { AdultFilterViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val imported by viewModel.importedCount.collectAsStateWithLifecycle()
    val importResult by viewModel.importResult.collectAsStateWithLifecycle()
    val dns by viewModel.dns.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::import) }
    LaunchedEffect(Unit) { viewModel.refreshDns() }
    val editable = state.loaded && !state.locked

    ScreenScaffold(title = stringResource(R.string.adult_title), onBack = onBack) {
        if (state.locked) NoticeCard(stringResource(R.string.locked_by_discipline))

        SectionCard(title = stringResource(R.string.adult_browsers_title)) {
            SwitchRow(stringResource(R.string.adult_toggle), stringResource(R.string.adult_toggle_body), state.enabled, editable) {
                viewModel.setEnabled(it)
            }
            SwitchRow(stringResource(R.string.adult_search_toggle), stringResource(R.string.adult_search_body), state.searchBlock, editable && state.enabled) {
                viewModel.setSearchBlock(it)
            }
            Text(
                stringResource(R.string.adult_how_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Text(
                pluralStringResource(R.plurals.adult_list_count, viewModel.bundledCount, viewModel.bundledCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (imported > 0) {
                Text(
                    pluralStringResource(R.plurals.adult_imported_count, imported, imported),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(stringResource(R.string.adult_import_body), style = MaterialTheme.typography.bodyMedium)
            importResult?.let { ImportMessage(it) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) }, enabled = editable) {
                    Text(stringResource(R.string.ads_import))
                }
                if (imported > 0) {
                    TextButton(onClick = { viewModel.removeImported() }, enabled = editable) {
                        Text(stringResource(R.string.ads_import_remove))
                    }
                }
            }
        }

        SectionCard(title = stringResource(R.string.dns_title)) {
            Text(stringResource(R.string.dns_body), style = MaterialTheme.typography.bodyMedium)
            ChoiceChips(
                options = PrivateDns.Provider.entries,
                selected = dns.selected,
                label = { stringResource(it.label) },
                enabled = editable,
                onSelect = viewModel::select,
            )
            val current = dns.state
            Text(
                when (current) {
                    is PrivateDns.State.Family -> stringResource(R.string.dns_status_on, stringResource(current.provider.label))
                    PrivateDns.State.Other -> stringResource(R.string.dns_status_off)
                    PrivateDns.State.Unknown -> stringResource(R.string.dns_status_unknown)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (dns.canManage) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::enableDns, enabled = editable) { Text(stringResource(R.string.dns_enable)) }
                    if (current is PrivateDns.State.Family) {
                        TextButton(onClick = viewModel::disableDns, enabled = editable) { Text(stringResource(R.string.dns_disable)) }
                    }
                }
            } else {
                Text(stringResource(R.string.dns_needs_grant), style = MaterialTheme.typography.bodyMedium)
                SelectionContainer {
                    Text(
                        PrivateDns.grantCommand(context),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
                Text(
                    stringResource(R.string.dns_manual, dns.selected.host),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { SystemSettings.openNetworkSettings(context) }) {
                    Text(stringResource(R.string.ads_chrome_open))
                }
            }
        }

        SectionCard(title = stringResource(R.string.adult_limits_title)) {
            Text(stringResource(R.string.adult_limits_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ImportMessage(result: BrowserFilters.ImportResult) {
    val (text, error) = when (result) {
        is BrowserFilters.ImportResult.Imported -> pluralStringResource(R.plurals.adult_import_ok, result.rules, result.rules) to false
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
