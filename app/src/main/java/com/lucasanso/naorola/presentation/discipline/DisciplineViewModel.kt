package com.lucasanso.naorola.presentation.discipline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasanso.naorola.data.stats.ProtectionEventType
import com.lucasanso.naorola.di.AppContainer
import com.lucasanso.naorola.domain.model.DisciplineRules
import com.lucasanso.naorola.domain.model.DisciplineState
import com.lucasanso.naorola.domain.model.ProtectionSettings
import com.lucasanso.naorola.domain.policy.Discipline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Choices for a new Discipline session, before activation. */
data class DisciplineDraft(
    /** null = until the user leaves through the app. */
    val durationHours: Int? = null,
    val exitDelayMinutes: Int = DisciplineState.DEFAULT_EXIT_DELAY_MINUTES,
    val rules: DisciplineRules = DisciplineRules(),
)

data class DisciplineUiState(
    val settings: ProtectionSettings = ProtectionSettings(),
    val now: Long = 0L,
    val draft: DisciplineDraft = DisciplineDraft(),
    val serviceStopsDuringDiscipline: Int = 0,
    val loaded: Boolean = false,
) {
    val discipline: DisciplineState get() = settings.discipline
    val active: Boolean get() = discipline.isActive(now)

    /** Millis left before the exit can be confirmed; null when no exit was requested. */
    val exitWaitMillis: Long? get() = discipline.exitReadyAt()?.let { (it - now).coerceAtLeast(0L) }
}

class DisciplineViewModel(private val container: AppContainer) : ViewModel() {
    private val draft = MutableStateFlow(DisciplineDraft())
    private val serviceStops = MutableStateFlow(0)

    /** One tick per second while the screen is visible (exit countdown). */
    private val clock = flow {
        while (true) {
            emit(container.clock.nowMillis())
            delay(TICK_MILLIS)
        }
    }

    val state: StateFlow<DisciplineUiState> = combine(
        container.settingsRepository.settings,
        clock,
        draft,
        serviceStops,
    ) { settings, now, draft, stops ->
        DisciplineUiState(settings, now, draft, stops, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DisciplineUiState())

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val d = state.value.discipline
            serviceStops.value = if (d.active) {
                container.statsRepository.protectionEventsSince(d.startedAt)
                    .count { it.type == ProtectionEventType.SERVICE_DISCONNECTED }
            } else {
                0
            }
        }
    }

    fun updateDraft(transform: (DisciplineDraft) -> DisciplineDraft) = draft.update(transform)

    fun activate() {
        val choice = draft.value
        viewModelScope.launch {
            val changed = container.settingsRepository.changeDiscipline { s, now ->
                if (s.discipline.isActive(now)) {
                    null
                } else {
                    Discipline.activate(
                        settings = s,
                        now = now,
                        endsAt = choice.durationHours?.let { now + it * HOUR_MILLIS },
                        exitDelayMinutes = choice.exitDelayMinutes,
                        rules = choice.rules,
                    )
                }
            }
            if (changed) record(ProtectionEventType.DISCIPLINE_ON)
            serviceStops.value = 0
        }
    }

    fun requestExit() = change { s, now -> Discipline.requestExit(s, now) }

    fun cancelExitRequest() = change { s, _ -> Discipline.cancelExitRequest(s) }

    fun confirmExit() {
        viewModelScope.launch {
            val changed = container.settingsRepository.changeDiscipline { s, now -> Discipline.confirmExit(s, now) }
            if (changed) record(ProtectionEventType.DISCIPLINE_OFF)
        }
    }

    private fun change(transform: (ProtectionSettings, Long) -> ProtectionSettings?) {
        viewModelScope.launch { container.settingsRepository.changeDiscipline(transform) }
    }

    private suspend fun record(type: ProtectionEventType) {
        runCatching { container.statsRepository.recordProtection(type) }
    }

    companion object {
        val DURATION_CHOICES_HOURS: List<Int?> = listOf(1, 4, 12, 24, null)
        private const val HOUR_MILLIS = 3_600_000L
        private const val TICK_MILLIS = 1_000L
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
