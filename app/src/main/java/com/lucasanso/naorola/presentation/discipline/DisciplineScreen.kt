package com.lucasanso.naorola.presentation.discipline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucasanso.naorola.R
import com.lucasanso.naorola.domain.model.DisciplineRules
import com.lucasanso.naorola.domain.model.DisciplineState
import com.lucasanso.naorola.presentation.common.containerViewModel
import com.lucasanso.naorola.presentation.components.ChoiceChips
import com.lucasanso.naorola.presentation.components.NoticeCard
import com.lucasanso.naorola.presentation.components.ScreenScaffold
import com.lucasanso.naorola.presentation.components.SectionCard
import com.lucasanso.naorola.presentation.components.SwitchRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DisciplineScreen(onBack: () -> Unit) {
    val viewModel = containerViewModel { DisciplineViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(state.discipline.startedAt) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    ScreenScaffold(title = stringResource(R.string.discipline_title), onBack = onBack) {
        if (!state.loaded) return@ScreenScaffold
        if (state.active) ActiveDiscipline(state, viewModel) else SetupDiscipline(state.draft, viewModel)
    }
}

@Composable
private fun SetupDiscipline(draft: DisciplineDraft, viewModel: DisciplineViewModel) {
    var confirm by remember { mutableStateOf(false) }
    SectionCard {
        Text(stringResource(R.string.discipline_intro), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.discipline_honesty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SectionCard(title = stringResource(R.string.discipline_duration)) {
        ChoiceChips(
            options = DisciplineViewModel.DURATION_CHOICES_HOURS,
            selected = draft.durationHours,
            label = { hours -> if (hours == null) stringResource(R.string.discipline_until_exit) else stringResource(R.string.hours_short, hours) },
        ) { hours -> viewModel.updateDraft { it.copy(durationHours = hours) } }
    }
    SectionCard(title = stringResource(R.string.discipline_exit_delay)) {
        Text(
            stringResource(R.string.discipline_exit_delay_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChoiceChips(
            options = DisciplineState.EXIT_DELAY_CHOICES_MINUTES,
            selected = draft.exitDelayMinutes,
            label = { stringResource(R.string.minutes_short, it) },
        ) { minutes -> viewModel.updateDraft { it.copy(exitDelayMinutes = minutes) } }
    }
    SectionCard(title = stringResource(R.string.discipline_rules)) {
        RuleSwitches(draft.rules) { rules -> viewModel.updateDraft { it.copy(rules = rules) } }
    }
    Button(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.discipline_activate))
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.discipline_confirm_title)) },
            text = { Text(stringResource(R.string.discipline_confirm_body, draft.exitDelayMinutes)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    viewModel.activate()
                }) { Text(stringResource(R.string.discipline_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.discipline_cancel)) } },
        )
    }
}

@Composable
private fun RuleSwitches(rules: DisciplineRules, onChange: (DisciplineRules) -> Unit) {
    SwitchRow(stringResource(R.string.rule_zero_tolerance), stringResource(R.string.rule_zero_tolerance_body), rules.zeroTolerance) {
        onChange(rules.copy(zeroTolerance = it))
    }
    HorizontalDivider()
    SwitchRow(stringResource(R.string.rule_suspend_exceptions), stringResource(R.string.rule_suspend_exceptions_body), rules.suspendExceptions) {
        onChange(rules.copy(suspendExceptions = it))
    }
    HorizontalDivider()
    SwitchRow(stringResource(R.string.rule_force_all), stringResource(R.string.rule_force_all_body), rules.forceAllPlatforms) {
        onChange(rules.copy(forceAllPlatforms = it))
    }
    HorizontalDivider()
    SwitchRow(stringResource(R.string.rule_keep_direct), stringResource(R.string.rule_keep_direct_body), rules.keepReelFromDirect) {
        onChange(rules.copy(keepReelFromDirect = it))
    }
    HorizontalDivider()
    SwitchRow(stringResource(R.string.rule_force_total), stringResource(R.string.rule_force_total_body), rules.forceTotalMode) {
        onChange(rules.copy(forceTotalMode = it))
    }
}

@Composable
private fun ActiveDiscipline(state: DisciplineUiState, viewModel: DisciplineViewModel) {
    val d = state.discipline
    SectionCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            stringResource(R.string.discipline_active),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            stringResource(R.string.discipline_since, dateTime(d.startedAt)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            d.endsAt?.let { stringResource(R.string.discipline_ends_at, dateTime(it)) } ?: stringResource(R.string.discipline_ends_on_exit),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
    if (state.serviceStopsDuringDiscipline > 0) {
        NoticeCard(
            pluralStringResource(R.plurals.discipline_service_stops, state.serviceStopsDuringDiscipline, state.serviceStopsDuringDiscipline),
        )
    }
    SectionCard(title = stringResource(R.string.discipline_active_rules)) {
        val active = buildList {
            if (d.rules.zeroTolerance) add(R.string.rule_zero_tolerance)
            if (d.rules.suspendExceptions) add(R.string.rule_suspend_exceptions)
            if (d.rules.forceAllPlatforms) add(R.string.rule_force_all)
            if (d.rules.keepReelFromDirect) add(R.string.rule_keep_direct)
            if (d.rules.forceTotalMode) add(R.string.rule_force_total)
        }
        if (active.isEmpty()) {
            Text(stringResource(R.string.discipline_no_extra_rules), style = MaterialTheme.typography.bodyMedium)
        }
        Column {
            active.forEach { Text("• " + stringResource(it), style = MaterialTheme.typography.bodyMedium) }
        }
    }
    SectionCard {
        val wait = state.exitWaitMillis
        when {
            wait == null -> {
                Text(
                    stringResource(R.string.discipline_request_exit_body, d.exitDelayMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = viewModel::requestExit, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.discipline_request_exit))
                }
            }
            wait > 0 -> {
                Text(stringResource(R.string.discipline_wait, countdown(wait)), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = viewModel::cancelExitRequest) { Text(stringResource(R.string.discipline_cancel_request)) }
            }
            else -> {
                Text(stringResource(R.string.discipline_ready), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = viewModel::confirmExit, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.discipline_confirm_exit))
                }
                TextButton(onClick = viewModel::cancelExitRequest) { Text(stringResource(R.string.discipline_cancel_request)) }
            }
        }
    }
}

private fun dateTime(millis: Long): String = SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(millis))

private fun countdown(millis: Long): String {
    val totalSeconds = (millis + 999) / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
