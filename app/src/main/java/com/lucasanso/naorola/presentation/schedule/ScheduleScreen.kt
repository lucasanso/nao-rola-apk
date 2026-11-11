package com.lucasanso.naorola.presentation.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lucasanso.naorola.R
import com.lucasanso.naorola.di.AppContainer
import com.lucasanso.naorola.domain.model.ExceptionPeriod
import com.lucasanso.naorola.domain.model.ProtectionMode
import com.lucasanso.naorola.domain.model.ProtectionSettings
import com.lucasanso.naorola.domain.model.ScheduleWindow
import com.lucasanso.naorola.presentation.common.containerViewModel
import com.lucasanso.naorola.presentation.components.ChoiceChips
import com.lucasanso.naorola.presentation.components.NoticeCard
import com.lucasanso.naorola.presentation.components.ScreenScaffold
import com.lucasanso.naorola.presentation.components.SectionCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

data class ScheduleUiState(val settings: ProtectionSettings = ProtectionSettings(), val locked: Boolean = false, val loaded: Boolean = false)

class ScheduleViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<ScheduleUiState> = container.settingsRepository.settings
        .map { ScheduleUiState(it, locked = it.discipline.isActive(container.clock.nowMillis()), loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState())

    private fun edit(transform: (ProtectionSettings) -> ProtectionSettings) {
        viewModelScope.launch { container.settingsRepository.edit(transform) }
    }

    fun addActive(window: ScheduleWindow) = edit { it.copy(activeWindows = it.activeWindows + window) }
    fun removeActive(index: Int) = edit { it.copy(activeWindows = it.activeWindows.filterIndexed { i, _ -> i != index }) }
    fun clearActive() = edit { it.copy(activeWindows = emptyList()) }
    fun addException(window: ScheduleWindow) = edit { it.copy(exceptionWindows = it.exceptionWindows + window) }
    fun removeException(index: Int) = edit { it.copy(exceptionWindows = it.exceptionWindows.filterIndexed { i, _ -> i != index }) }

    /** Adding a pause also drops the ones already over. */
    fun addPause(period: ExceptionPeriod) {
        val now = container.clock.nowMillis()
        edit { s -> s.copy(exceptionPeriods = s.exceptionPeriods.filter { it.endMillis > now } + period) }
    }

    fun removePause(period: ExceptionPeriod) = edit { s -> s.copy(exceptionPeriods = s.exceptionPeriods - period) }

    fun now(): Long = container.clock.nowMillis()
}

@Composable
fun ScheduleScreen(onBack: () -> Unit) {
    val viewModel = containerViewModel { ScheduleViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var windowEditor by remember { mutableStateOf<((ScheduleWindow) -> Unit)?>(null) }
    var pauseEditor by remember { mutableStateOf(false) }
    var onlyWindows by remember(state.settings.activeWindows.isNotEmpty()) { mutableStateOf(state.settings.activeWindows.isNotEmpty()) }
    val s = state.settings
    val editable = state.loaded && !state.locked

    ScreenScaffold(title = stringResource(R.string.schedule_title), onBack = onBack) {
        if (state.locked) NoticeCard(stringResource(R.string.locked_by_discipline))
        if (s.mode == ProtectionMode.TOTAL) NoticeCard(stringResource(R.string.schedule_total_note))

        SectionCard(title = stringResource(R.string.schedule_when)) {
            val options = listOf(false to R.string.schedule_always, true to R.string.schedule_only_windows)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = onlyWindows == value,
                        onClick = {
                            onlyWindows = value
                            if (!value) viewModel.clearActive()
                        },
                        enabled = editable,
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) { Text(stringResource(label)) }
                }
            }
            if (onlyWindows) {
                if (s.activeWindows.isEmpty()) Text(stringResource(R.string.schedule_windows_empty), style = MaterialTheme.typography.bodyMedium)
                s.activeWindows.forEachIndexed { i, w -> WindowRow(w, editable) { viewModel.removeActive(i) } }
                OutlinedButton(onClick = { windowEditor = viewModel::addActive }, enabled = editable) {
                    Text(stringResource(R.string.schedule_add_window))
                }
            }
        }

        SectionCard(title = stringResource(R.string.schedule_exceptions)) {
            Text(stringResource(R.string.schedule_exceptions_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.exceptionWindows.isEmpty()) Text(stringResource(R.string.schedule_exceptions_empty), style = MaterialTheme.typography.bodyMedium)
            s.exceptionWindows.forEachIndexed { i, w -> WindowRow(w, editable) { viewModel.removeException(i) } }
            OutlinedButton(onClick = { windowEditor = viewModel::addException }, enabled = editable) {
                Text(stringResource(R.string.schedule_add_exception))
            }
        }

        SectionCard(title = stringResource(R.string.schedule_pauses)) {
            Text(stringResource(R.string.schedule_pauses_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val upcoming = s.exceptionPeriods.filter { it.endMillis > viewModel.now() }.sortedBy { it.startMillis }
            if (upcoming.isEmpty()) Text(stringResource(R.string.schedule_pauses_empty), style = MaterialTheme.typography.bodyMedium)
            upcoming.forEach { p ->
                ItemRow("${dateTime(p.startMillis)} → ${dateTime(p.endMillis)}", editable) { viewModel.removePause(p) }
            }
            OutlinedButton(onClick = { pauseEditor = true }, enabled = editable) { Text(stringResource(R.string.schedule_add_pause)) }
        }
    }

    windowEditor?.let { save ->
        WindowEditorDialog(onDismiss = { windowEditor = null }) { window ->
            save(window)
            windowEditor = null
        }
    }
    if (pauseEditor) {
        PauseEditorDialog(now = viewModel.now(), onDismiss = { pauseEditor = false }) { period ->
            viewModel.addPause(period)
            pauseEditor = false
        }
    }
}

@Composable
private fun WindowRow(window: ScheduleWindow, editable: Boolean, onDelete: () -> Unit) {
    val days = when (ScheduleFormat.preset(window.days)) {
        ScheduleFormat.DaysPreset.EVERY_DAY -> stringResource(R.string.schedule_every_day)
        ScheduleFormat.DaysPreset.WEEKDAYS -> stringResource(R.string.schedule_weekdays)
        ScheduleFormat.DaysPreset.WEEKEND -> stringResource(R.string.schedule_weekend)
        ScheduleFormat.DaysPreset.CUSTOM -> ScheduleFormat.customDays(window.days)
    }
    val nextDay = if (ScheduleFormat.crossesMidnight(window)) " " + stringResource(R.string.schedule_next_day) else ""
    ItemRow("$days · ${ScheduleFormat.time(window.startMinute)}–${ScheduleFormat.time(window.endMinute)}$nextDay", editable, onDelete)
}

@Composable
private fun ItemRow(text: String, editable: Boolean, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete, enabled = editable) {
            Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.action_delete))
        }
    }
}

@Composable
private fun WindowEditorDialog(onDismiss: () -> Unit, onSave: (ScheduleWindow) -> Unit) {
    var days by remember { mutableStateOf(ScheduleFormat.WEEK.take(5).toSet()) }
    var start by remember { mutableIntStateOf(9 * 60) }
    var end by remember { mutableIntStateOf(18 * 60) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    val window = ScheduleFormat.toWindow(days, start, end)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_editor_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.schedule_days), style = MaterialTheme.typography.labelLarge)
                ScheduleFormat.WEEK.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { day ->
                            FilterChip(
                                selected = day in days,
                                onClick = { days = if (day in days) days - day else days + day },
                                label = { Text(ScheduleFormat.shortName(day)) },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picking = true }) { Text(stringResource(R.string.schedule_start, ScheduleFormat.time(start))) }
                    OutlinedButton(onClick = { picking = false }) { Text(stringResource(R.string.schedule_end, ScheduleFormat.time(end))) }
                }
                if (window != null && ScheduleFormat.crossesMidnight(window)) {
                    Text(stringResource(R.string.schedule_next_day), style = MaterialTheme.typography.bodySmall)
                }
                if (window == null) {
                    Text(stringResource(R.string.schedule_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { window?.let(onSave) }, enabled = window != null) { Text(stringResource(R.string.schedule_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.discipline_cancel)) } },
    )
    picking?.let { isStart ->
        TimePickerDialog(initialMinute = if (isStart) start else end, onDismiss = { picking = null }) { minute ->
            if (isStart) start = minute else end = minute
            picking = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initialMinute: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = (initialMinute / 60) % 24, initialMinute = initialMinute % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.schedule_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.discipline_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PauseEditorDialog(now: Long, onDismiss: () -> Unit, onSave: (ExceptionPeriod) -> Unit) {
    val zone = ZoneId.systemDefault()
    var date by remember { mutableStateOf(Instant.ofEpochMilli(now).atZone(zone).toLocalDate()) }
    var startMinute by remember { mutableIntStateOf(((Instant.ofEpochMilli(now).atZone(zone).hour + 1) % 24) * 60) }
    var hours by remember { mutableIntStateOf(PAUSE_HOURS.first()) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    val start = date.atStartOfDay(zone).plusMinutes(startMinute.toLong()).toInstant().toEpochMilli()
    val period = ExceptionPeriod(start, start + hours * 3_600_000L).takeIf { it.endMillis > now }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_pause_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { pickingDate = true }) { Text(stringResource(R.string.schedule_pause_date, date.format(DATE))) }
                OutlinedButton(onClick = { pickingTime = true }) { Text(stringResource(R.string.schedule_pause_time, ScheduleFormat.time(startMinute))) }
                Text(stringResource(R.string.schedule_pause_duration), style = MaterialTheme.typography.labelLarge)
                ChoiceChips(PAUSE_HOURS, hours, label = { stringResource(R.string.hours_short, it) }) { hours = it }
                if (period == null) {
                    Text(stringResource(R.string.schedule_pause_in_past), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { period?.let(onSave) }, enabled = period != null) { Text(stringResource(R.string.schedule_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.discipline_cancel)) } },
    )
    if (pickingDate) {
        // The date picker works in UTC midnights.
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickingDate = false
                }) { Text(stringResource(R.string.schedule_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.discipline_cancel)) } },
        ) { DatePicker(state = state, modifier = Modifier.padding(top = 8.dp)) }
    }
    if (pickingTime) {
        TimePickerDialog(startMinute, onDismiss = { pickingTime = false }) {
            startMinute = it
            pickingTime = false
        }
    }
}

private val PAUSE_HOURS = listOf(1, 2, 4, 8, 24)
private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun dateTime(millis: Long): String = SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(millis))
