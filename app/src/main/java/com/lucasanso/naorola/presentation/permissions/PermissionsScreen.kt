package com.lucasanso.naorola.presentation.permissions

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lucasanso.naorola.R
import com.lucasanso.naorola.core.SystemSettings
import com.lucasanso.naorola.di.AppContainer
import com.lucasanso.naorola.monitoring.HealthSnapshot
import com.lucasanso.naorola.presentation.common.containerViewModel
import com.lucasanso.naorola.presentation.components.PillTone
import com.lucasanso.naorola.presentation.components.ScreenScaffold
import com.lucasanso.naorola.presentation.components.SectionCard
import com.lucasanso.naorola.presentation.components.StatusPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionsState(val health: HealthSnapshot? = null, val disclosureAccepted: Boolean = false)

class PermissionsViewModel(private val container: AppContainer) : ViewModel() {
    private val health = MutableStateFlow<HealthSnapshot?>(null)

    val state: StateFlow<PermissionsState> = combine(health, container.settingsRepository.uiPreferences) { h, ui ->
        PermissionsState(h, ui.disclosureAccepted)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PermissionsState())

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { health.value = container.health.snapshot(emptySet()) }
    }

    fun acceptDisclosure() {
        viewModelScope.launch { container.settingsRepository.setDisclosureAccepted(true) }
    }
}

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val viewModel = containerViewModel { PermissionsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDisclosure by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val health = state.health

    ScreenScaffold(title = stringResource(R.string.permissions_title), onBack = onBack) {
        if (health?.advancedProtectionOn == true) {
            PermissionCard(
                title = stringResource(R.string.perm_advanced_title),
                tag = null,
                granted = false,
                body = stringResource(R.string.perm_advanced_body),
                action = stringResource(R.string.perm_advanced_action),
            ) { SystemSettings.openSecurity(context) }
        }

        PermissionCard(
            title = stringResource(R.string.perm_a11y_title),
            tag = stringResource(R.string.perm_required),
            granted = health?.accessibilityEnabled,
            body = stringResource(R.string.perm_a11y_body) + "\n\n" + stringResource(R.string.perm_a11y_steps),
            action = stringResource(R.string.perm_a11y_action),
        ) {
            if (state.disclosureAccepted) SystemSettings.openAccessibility(context) else showDisclosure = true
        }
        if (health?.accessibilityEnabled == false) {
            PermissionCard(
                title = stringResource(R.string.perm_restricted_title),
                tag = null,
                granted = null,
                body = stringResource(R.string.perm_restricted_body),
                action = stringResource(R.string.perm_restricted_action),
                primary = false,
            ) { SystemSettings.openAppDetails(context) }
        }

        PermissionCard(
            title = stringResource(R.string.perm_usage_title),
            tag = stringResource(R.string.perm_optional),
            granted = health?.usageAccessGranted,
            body = stringResource(R.string.perm_usage_body),
            action = stringResource(R.string.perm_usage_action),
            primary = false,
        ) { SystemSettings.openUsageAccess(context) }

        PermissionCard(
            title = stringResource(R.string.perm_battery_title),
            tag = stringResource(R.string.perm_recommended),
            granted = health?.ignoringBatteryOptimizations,
            body = if (health?.ignoringBatteryOptimizations == true) {
                stringResource(R.string.perm_battery_ok)
            } else {
                stringResource(R.string.perm_battery_body)
            },
            action = stringResource(R.string.perm_battery_action),
            primary = false,
        ) { SystemSettings.openBatteryOptimization(context) }
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(stringResource(R.string.disclosure_title)) },
            text = { Text(stringResource(R.string.disclosure_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    viewModel.acceptDisclosure()
                    SystemSettings.openAccessibility(context)
                }) { Text(stringResource(R.string.disclosure_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) { Text(stringResource(R.string.disclosure_decline)) }
            },
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    tag: String?,
    granted: Boolean?,
    body: String,
    action: String,
    primary: Boolean = true,
    onAction: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            when (granted) {
                true -> StatusPill(stringResource(R.string.perm_granted), PillTone.POSITIVE)
                false -> StatusPill(tag ?: stringResource(R.string.perm_missing), if (tag == null) PillTone.NEGATIVE else PillTone.NEUTRAL)
                null -> Unit
            }
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (granted != true) {
            if (primary) {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(action) }
            } else {
                OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(action) }
            }
        }
    }
}
