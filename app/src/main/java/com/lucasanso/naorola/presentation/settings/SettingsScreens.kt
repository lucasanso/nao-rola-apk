package com.lucasanso.naorola.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucasanso.naorola.R
import com.lucasanso.naorola.core.Edition
import com.lucasanso.naorola.domain.model.ProtectionMode
import com.lucasanso.naorola.domain.model.ProtectionSettings
import com.lucasanso.naorola.presentation.common.containerViewModel
import com.lucasanso.naorola.presentation.components.ChoiceChips
import com.lucasanso.naorola.presentation.components.NavRow
import com.lucasanso.naorola.presentation.components.NoticeCard
import com.lucasanso.naorola.presentation.components.ScreenScaffold
import com.lucasanso.naorola.presentation.components.SectionCard
import com.lucasanso.naorola.presentation.components.SwitchRow
import com.lucasanso.naorola.presentation.navigation.Routes

@Composable
fun SettingsHubScreen(onNavigate: (String) -> Unit) {
    val viewModel = containerViewModel { SettingsHubViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScreenScaffold(title = stringResource(R.string.settings_title)) {
        SectionCard {
            NavRow(
                title = stringResource(R.string.settings_blocking),
                summary = stringResource(
                    if (state.settings.mode == ProtectionMode.TOTAL) {
                        R.string.settings_blocking_summary_total
                    } else {
                        R.string.settings_blocking_summary_personalized
                    },
                ),
                onClick = { onNavigate(Routes.BLOCKING) },
            )
            NavRow(
                title = stringResource(R.string.settings_ads),
                summary = stringResource(
                    when {
                        !Edition.inAppAdBlock -> R.string.settings_ads_play
                        state.settings.adBlockEnabled -> R.string.settings_ads_on
                        else -> R.string.settings_ads_off
                    },
                ),
                onClick = { onNavigate(Routes.ADS) },
            )
            NavRow(
                title = stringResource(R.string.adult_title),
                summary = stringResource(if (state.settings.adultBlockEnabled) R.string.adult_summary_on else R.string.adult_summary_off),
                onClick = { onNavigate(Routes.ADULT) },
            )
            NavRow(
                title = stringResource(R.string.schedule_title),
                summary = with(state.settings) {
                    if (activeWindows.isEmpty() && exceptionWindows.isEmpty() && exceptionPeriods.isEmpty()) {
                        stringResource(R.string.schedule_summary_always)
                    } else {
                        listOf(
                            pluralStringResource(R.plurals.count_windows, activeWindows.size, activeWindows.size),
                            pluralStringResource(R.plurals.count_exceptions, exceptionWindows.size, exceptionWindows.size),
                            pluralStringResource(R.plurals.count_pauses, exceptionPeriods.size, exceptionPeriods.size),
                        ).joinToString(" · ")
                    }
                },
                onClick = { onNavigate(Routes.SCHEDULE) },
            )
            NavRow(
                title = stringResource(R.string.discipline_title),
                summary = stringResource(
                    if (state.settings.discipline.active) R.string.settings_discipline_on else R.string.settings_discipline_off,
                ),
                onClick = { onNavigate(Routes.DISCIPLINE) },
            )
            NavRow(stringResource(R.string.settings_permissions), stringResource(R.string.settings_permissions_summary)) {
                onNavigate(Routes.PERMISSIONS)
            }
        }
        SectionCard {
            SwitchRow(
                title = stringResource(R.string.settings_dynamic_color),
                body = stringResource(R.string.settings_dynamic_color_summary),
                checked = state.ui.dynamicColor,
                onCheckedChange = viewModel::setDynamicColor,
            )
        }
        SectionCard(title = stringResource(R.string.backup_title)) {
            val message by viewModel.backupMessage.collectAsStateWithLifecycle()
            val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                uri?.let(viewModel::exportTo)
            }
            val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::restoreFrom) }
            Text(
                stringResource(R.string.backup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { saver.launch("naorola-configuracoes.json") }) { Text(stringResource(R.string.backup_save)) }
                TextButton(onClick = { opener.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                    Text(stringResource(R.string.backup_restore))
                }
            }
            message?.let {
                Text(
                    stringResource(
                        when (it) {
                            BackupMessage.SAVED -> R.string.backup_saved
                            BackupMessage.RESTORED -> R.string.backup_restored
                            BackupMessage.LOCKED -> R.string.locked_by_discipline
                            BackupMessage.INVALID -> R.string.backup_invalid
                            BackupMessage.FAILED -> R.string.backup_failed
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it == BackupMessage.SAVED || it == BackupMessage.RESTORED) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
        SectionCard {
            NavRow(stringResource(R.string.settings_diagnostics), stringResource(R.string.settings_diagnostics_summary)) {
                onNavigate(Routes.DIAGNOSTICS)
            }
            NavRow(stringResource(R.string.settings_about), null) { onNavigate(Routes.ABOUT) }
        }
    }
}

@Composable
fun BlockingSettingsScreen(onBack: () -> Unit) {
    val viewModel = containerViewModel { BlockingSettingsViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state.settings
    val editable = state.loaded && !state.locked
    val personalized = s.mode == ProtectionMode.PERSONALIZED
    val update = viewModel::update

    ScreenScaffold(title = stringResource(R.string.blocking_title), onBack = onBack) {
        if (state.locked) NoticeCard(stringResource(R.string.locked_by_discipline))

        SectionCard(title = stringResource(R.string.blocking_mode)) {
            ModeSelector(s.mode, enabled = editable) { mode -> update { it.copy(mode = mode) } }
            Text(
                stringResource(if (personalized) R.string.mode_personalized_body else R.string.mode_total_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!personalized) NoticeCard(stringResource(R.string.blocking_total_overrides))
        val optionsEnabled = editable && personalized

        SectionCard(title = stringResource(R.string.blocking_platforms)) {
            SwitchRow(stringResource(R.string.toggle_youtube), stringResource(R.string.toggle_youtube_body), s.youtubeEnabled, optionsEnabled) { v ->
                update { it.copy(youtubeEnabled = v) }
            }
            HorizontalDivider()
            SwitchRow(stringResource(R.string.toggle_instagram), stringResource(R.string.toggle_instagram_body), s.instagramEnabled, optionsEnabled) { v ->
                update { it.copy(instagramEnabled = v) }
            }
            HorizontalDivider()
            SwitchRow(stringResource(R.string.hide_feeds_title), stringResource(R.string.hide_feeds_body), s.hideInFeeds, optionsEnabled) { v ->
                update { it.copy(hideInFeeds = v) }
            }
            HorizontalDivider()
            SwitchRow(stringResource(R.string.hide_home_title), stringResource(R.string.hide_home_body), s.hideYouTubeHome, editable) { v ->
                update { it.copy(hideYouTubeHome = v) }
            }
            if (s.hideYouTubeHome) {
                SwitchRow(
                    stringResource(R.string.hide_home_schedule_title),
                    stringResource(R.string.hide_home_schedule_body),
                    s.homeFollowsSchedule,
                    editable,
                ) { v -> update { it.copy(homeFollowsSchedule = v) } }
            }
            HorizontalDivider()
            SwitchRow(stringResource(R.string.toggle_browser), stringResource(R.string.toggle_browser_body), s.browserEnabled, optionsEnabled) { v ->
                update { it.copy(browserEnabled = v) }
            }
            HorizontalDivider()
            SwitchRow(stringResource(R.string.toggle_other_apps), stringResource(R.string.toggle_other_apps_body), s.otherAppsEnabled, optionsEnabled) { v ->
                update { it.copy(otherAppsEnabled = v) }
            }
        }

        SectionCard(title = stringResource(R.string.blocking_exceptions)) {
            Column {
                Text(stringResource(R.string.tolerance_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.tolerance_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ChoiceChips(
                options = ProtectionSettings.TOLERANCE_CHOICES_SECONDS,
                selected = s.toleranceSeconds,
                label = {
                    when (it) {
                        0 -> stringResource(R.string.tolerance_none)
                        ProtectionSettings.UNLIMITED_TOLERANCE -> stringResource(R.string.tolerance_unlimited)
                        else -> stringResource(R.string.seconds_short, it)
                    }
                },
                enabled = optionsEnabled,
            ) { v -> update { it.copy(toleranceSeconds = v) } }
            if (s.toleranceSeconds == ProtectionSettings.UNLIMITED_TOLERANCE && s.dailyLimitMinutes <= 0) {
                Text(
                    stringResource(R.string.tolerance_unlimited_needs_daily),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            HorizontalDivider()
            Column {
                Text(stringResource(R.string.daily_limit_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.daily_limit_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ChoiceChips(
                options = ProtectionSettings.DAILY_LIMIT_CHOICES_MINUTES,
                selected = s.dailyLimitMinutes,
                label = { if (it == 0) stringResource(R.string.daily_limit_off) else stringResource(R.string.minutes_short, it) },
                enabled = optionsEnabled,
            ) { v -> update { it.copy(dailyLimitMinutes = v) } }
            HorizontalDivider()
            SwitchRow(stringResource(R.string.snooze_title), stringResource(R.string.snooze_body), s.snoozeAllowed, optionsEnabled) { v ->
                update { it.copy(snoozeAllowed = v) }
            }
            if (s.snoozeAllowed) {
                Text(stringResource(R.string.snooze_duration), style = MaterialTheme.typography.bodyMedium)
                ChoiceChips(
                    options = ProtectionSettings.SNOOZE_CHOICES_MINUTES,
                    selected = s.snoozeMinutes,
                    label = { stringResource(R.string.minutes_short, it) },
                    enabled = optionsEnabled,
                ) { v -> update { it.copy(snoozeMinutes = v) } }
            }
            HorizontalDivider()
            SwitchRow(stringResource(R.string.direct_title), stringResource(R.string.direct_body), s.allowReelFromDirect, optionsEnabled) { v ->
                update { it.copy(allowReelFromDirect = v) }
            }
        }
    }
}

@Composable
private fun ModeSelector(mode: ProtectionMode, enabled: Boolean, onSelect: (ProtectionMode) -> Unit) {
    val options = listOf(ProtectionMode.PERSONALIZED to R.string.mode_personalized, ProtectionMode.TOTAL to R.string.mode_total)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onSelect(value) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                modifier = Modifier.testTag("mode_${value.name}"),
            ) { Text(stringResource(label)) }
        }
    }
}
