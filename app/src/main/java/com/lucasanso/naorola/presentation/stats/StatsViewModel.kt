package com.lucasanso.naorola.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasanso.naorola.detection.rules.InstagramRules
import com.lucasanso.naorola.detection.rules.YouTubeRules
import com.lucasanso.naorola.di.AppContainer
import com.lucasanso.naorola.domain.model.Platform
import com.lucasanso.naorola.domain.stats.AvoidedExposureEstimator
import com.lucasanso.naorola.domain.stats.DayStats
import com.lucasanso.naorola.domain.stats.StatsCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class StatsState(
    val loading: Boolean = true,
    val days: List<DayStats> = emptyList(),
    val usageGranted: Boolean = false,
    val estimate: AvoidedExposureEstimator.Estimate? = null,
    val today: LocalDate? = null,
    val selected: LocalDate? = null,
) {
    val totalBlocked: Int get() = days.sumOf { it.blockedTotal }
    fun blockedOn(platform: Platform): Int = days.sumOf { it.blocked[platform] ?: 0 }
}

class StatsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(StatsState())
    val state: StateFlow<StatsState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val zone = ZoneId.systemDefault()
            val now = container.clock.nowMillis()
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val days = StatsCalculator.lastDays(today, DAYS)
            val from = days.first().atStartOfDay(zone).toInstant().toEpochMilli()
            val stats = container.statsRepository

            val granted = container.health.hasUsageAccess()
            val usage = if (granted) {
                container.dailyUsageHistory.forDays(days, today, setOf(YouTubeRules.PACKAGE, InstagramRules.PACKAGE))
                    .mapValues { (_, byPackage) ->
                        mapOf(
                            Platform.YOUTUBE to (byPackage[YouTubeRules.PACKAGE] ?: 0L),
                            Platform.INSTAGRAM to (byPackage[InstagramRules.PACKAGE] ?: 0L),
                        )
                    }
            } else {
                null
            }
            val series = StatsCalculator.dailySeries(days, stats.eventsSince(from), stats.exposuresSince(from), usage, zone)
            val sessions = stats.exposuresSince(now - ESTIMATE_WINDOW_DAYS * DAY_MILLIS).map { it.durationMillis }
            val estimate = AvoidedExposureEstimator.estimate(series.sumOf { it.blockedTotal }, sessions)
            _state.update {
                StatsState(
                    loading = false,
                    days = series,
                    usageGranted = granted,
                    estimate = estimate,
                    today = today,
                    selected = it.selected ?: today,
                )
            }
        }
    }

    fun select(day: LocalDate) = _state.update { it.copy(selected = day) }

    private companion object {
        const val DAYS = 7
        const val ESTIMATE_WINDOW_DAYS = 30L
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
