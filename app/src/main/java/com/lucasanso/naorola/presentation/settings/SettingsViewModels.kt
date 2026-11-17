package com.lucasanso.naorola.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasanso.naorola.data.settings.UiPreferences
import com.lucasanso.naorola.di.AppContainer
import com.lucasanso.naorola.domain.model.ProtectionSettings
import android.net.Uri
import com.lucasanso.naorola.data.settings.RestoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BlockingSettingsState(
    val settings: ProtectionSettings = ProtectionSettings(),
    /** Discipline mode is active: everything is read-only. */
    val locked: Boolean = false,
    val loaded: Boolean = false,
)

/** All edits go through the repository, which refuses them while Discipline is active. */
class BlockingSettingsViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<BlockingSettingsState> = container.settingsRepository.settings
        .map { BlockingSettingsState(it, locked = it.discipline.isActive(container.clock.nowMillis()), loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BlockingSettingsState())

    fun update(transform: (ProtectionSettings) -> ProtectionSettings) {
        viewModelScope.launch { container.settingsRepository.edit(transform) }
    }
}

enum class BackupMessage { SAVED, RESTORED, LOCKED, INVALID, FAILED }

data class SettingsHubState(val settings: ProtectionSettings = ProtectionSettings(), val ui: UiPreferences = UiPreferences())

class SettingsHubViewModel(private val container: AppContainer) : ViewModel() {
    private val _backupMessage = MutableStateFlow<BackupMessage?>(null)
    val backupMessage: StateFlow<BackupMessage?> = _backupMessage.asStateFlow()

    /** Writes the settings to a file the user picked (e.g. before reinstalling from the Play Store). */
    fun exportTo(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        _backupMessage.value = runCatching {
            val json = container.settingsRepository.exportBackup()
            container.context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
        }.fold(onSuccess = { BackupMessage.SAVED }, onFailure = {
            container.logger.error("Settings", "Falha ao salvar configurações", it)
            BackupMessage.FAILED
        })
    }

    fun restoreFrom(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val text = runCatching {
            container.context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
        _backupMessage.value = when (text?.let { container.settingsRepository.restoreBackup(it) }) {
            RestoreResult.RESTORED -> BackupMessage.RESTORED
            RestoreResult.LOCKED_BY_DISCIPLINE -> BackupMessage.LOCKED
            else -> BackupMessage.INVALID
        }
    }

    val state: StateFlow<SettingsHubState> = combine(
        container.settingsRepository.settings,
        container.settingsRepository.uiPreferences,
    ) { settings, ui -> SettingsHubState(settings, ui) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsHubState())

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setDynamicColor(enabled) }
    }
}

private const val STOP_TIMEOUT_MILLIS = 5_000L
