package com.lucasanso.naorola.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasanso.naorola.detection.rules.InstagramRules
import com.lucasanso.naorola.detection.rules.YouTubeRules
import com.lucasanso.naorola.di.AppContainer
import com.lucasanso.naorola.monitoring.HealthSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

class DashboardViewModel(private val container: AppContainer) : ViewModel() {
    private val health = MutableStateFlow(
        HealthSnapshot(
            accessibilityEnabled = false,
            usageAccessGranted = false,
            ignoringBatteryOptimizations = false,
            advancedProtectionOn = null,
            installedApps = emptySet(),
        ),
    )
    private val usage = MutableStateFlow<Map<String, Long>?>(null)

    val state: StateFlow<DashboardState> = combine(
        container.settingsRepository.settings,
        health,
        container.serviceConnected,
        container.statsRepository.today(),
        usage,
    ) { settings, health, connected, today, usage ->
        DashboardStateFactory.build(settings, health, connected, today, usage, container.clock.nowMillis(), ZoneId.systemDefault())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DashboardState())

    /** System facts can change while the app is in the background: re-read them on resume. */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = container.health.snapshot(DashboardStateFactory.PACKAGES_OF_INTEREST)
            health.value = snapshot
            usage.value = if (snapshot.usageAccessGranted) {
                runCatching {
                    container.usageStats.todayForegroundMillis(setOf(YouTubeRules.PACKAGE, InstagramRules.PACKAGE))
                }.getOrNull()
            } else {
                null
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
