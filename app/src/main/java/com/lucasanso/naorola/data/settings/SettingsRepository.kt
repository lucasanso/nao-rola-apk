package com.lucasanso.naorola.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lucasanso.naorola.core.AppLogger
import com.lucasanso.naorola.core.Clock
import com.lucasanso.naorola.domain.model.DisciplineRules
import com.lucasanso.naorola.domain.model.DisciplineState
import com.lucasanso.naorola.domain.model.Platform
import com.lucasanso.naorola.domain.model.ProtectionMode
import com.lucasanso.naorola.domain.model.ProtectionSettings
import com.lucasanso.naorola.domain.policy.Discipline
import com.lucasanso.naorola.domain.policy.EditResult
import com.lucasanso.naorola.domain.policy.SettingsCodec
import com.lucasanso.naorola.domain.policy.SettingsEditGuard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** App-only preferences that are not protection rules. */
data class UiPreferences(val disclosureAccepted: Boolean = false, val dynamicColor: Boolean = false)

/**
 * Single source of truth for configuration (DataStore). Every user edit goes through
 * [SettingsEditGuard], so Discipline mode cannot be bypassed from any screen.
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
    private val logger: AppLogger,
) {
    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) {
            logger.error(TAG, "Falha ao ler configurações; usando padrões", error)
            emit(emptyPreferences())
        } else {
            throw error
        }
    }

    val settings: Flow<ProtectionSettings> = preferences.map(::read).distinctUntilChanged()

    /** Platforms whose detector looks outdated (see DetectionHealthMonitor), with the start time. */
    val detectionSuspicions: Flow<Map<Platform, Long>> = preferences.map { p ->
        buildMap {
            p[Keys.SUSPECT_YOUTUBE]?.takeIf { it > 0 }?.let { put(Platform.YOUTUBE, it) }
            p[Keys.SUSPECT_INSTAGRAM]?.takeIf { it > 0 }?.let { put(Platform.INSTAGRAM, it) }
        }
    }.distinctUntilChanged()

    /** [sinceMillis] null clears the suspicion. Only YouTube and Instagram are tracked. */
    suspend fun setDetectionSuspicion(platform: Platform, sinceMillis: Long?) {
        val key = when (platform) {
            Platform.YOUTUBE -> Keys.SUSPECT_YOUTUBE
            Platform.INSTAGRAM -> Keys.SUSPECT_INSTAGRAM
            Platform.OTHER -> return
        }
        dataStore.edit { it[key] = sinceMillis ?: 0L }
    }

    val uiPreferences: Flow<UiPreferences> = preferences.map {
        UiPreferences(it[Keys.DISCLOSURE_ACCEPTED] ?: false, it[Keys.DYNAMIC_COLOR] ?: false)
    }.distinctUntilChanged()

    suspend fun edit(transform: (ProtectionSettings) -> ProtectionSettings): EditResult {
        var result: EditResult = EditResult.LockedByDiscipline
        dataStore.edit { prefs ->
            result = SettingsEditGuard.apply(read(prefs), clock.nowMillis(), transform)
            (result as? EditResult.Applied)?.let { write(prefs, it.settings) }
        }
        return result
    }

    /** Written by the blocking session (only possible when the policy allows snoozing). */
    suspend fun setSnoozedUntil(untilMillis: Long) {
        dataStore.edit { it[Keys.SNOOZED_UNTIL] = untilMillis }
    }

    /**
     * Discipline transitions (activate, request/cancel/confirm exit). [transform] returns null to
     * refuse the change; returns whether something was written.
     */
    suspend fun changeDiscipline(transform: (ProtectionSettings, Long) -> ProtectionSettings?): Boolean {
        var changed = false
        dataStore.edit { prefs ->
            val now = clock.nowMillis()
            val current = Discipline.normalize(read(prefs), now)
            transform(current, now)?.let {
                write(prefs, it)
                changed = true
            }
        }
        return changed
    }

    suspend fun setDisclosureAccepted(accepted: Boolean) {
        dataStore.edit { it[Keys.DISCLOSURE_ACCEPTED] = accepted }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    private fun read(p: Preferences): ProtectionSettings {
        val defaults = ProtectionSettings()
        val disciplineDefaults = DisciplineRules()
        return ProtectionSettings(
            mode = p[Keys.MODE]?.let { runCatching { ProtectionMode.valueOf(it) }.getOrNull() } ?: defaults.mode,
            youtubeEnabled = p[Keys.YOUTUBE] ?: defaults.youtubeEnabled,
            instagramEnabled = p[Keys.INSTAGRAM] ?: defaults.instagramEnabled,
            browserEnabled = p[Keys.BROWSER] ?: defaults.browserEnabled,
            feedReelsEnabled = p[Keys.FEED_REELS] ?: defaults.feedReelsEnabled,
            otherAppsEnabled = p[Keys.OTHER_APPS] ?: defaults.otherAppsEnabled,
            toleranceSeconds = p[Keys.TOLERANCE_SECONDS] ?: defaults.toleranceSeconds,
            dailyLimitMinutes = p[Keys.DAILY_LIMIT_MINUTES] ?: defaults.dailyLimitMinutes,
            snoozeAllowed = p[Keys.SNOOZE_ALLOWED] ?: defaults.snoozeAllowed,
            snoozeMinutes = p[Keys.SNOOZE_MINUTES] ?: defaults.snoozeMinutes,
            snoozedUntil = p[Keys.SNOOZED_UNTIL] ?: defaults.snoozedUntil,
            allowReelFromDirect = p[Keys.ALLOW_DIRECT] ?: defaults.allowReelFromDirect,
            activeWindows = SettingsCodec.decodeWindows(p[Keys.ACTIVE_WINDOWS]),
            exceptionWindows = SettingsCodec.decodeWindows(p[Keys.EXCEPTION_WINDOWS]),
            exceptionPeriods = SettingsCodec.decodePeriods(p[Keys.EXCEPTION_PERIODS]),
            discipline = DisciplineState(
                active = p[Keys.DISCIPLINE_ACTIVE] ?: false,
                startedAt = p[Keys.DISCIPLINE_STARTED] ?: 0L,
                endsAt = p[Keys.DISCIPLINE_ENDS]?.takeIf { it > 0 },
                exitDelayMinutes = p[Keys.DISCIPLINE_EXIT_DELAY] ?: DisciplineState.DEFAULT_EXIT_DELAY_MINUTES,
                exitRequestedAt = p[Keys.DISCIPLINE_EXIT_REQUESTED]?.takeIf { it > 0 },
                rules = DisciplineRules(
                    zeroTolerance = p[Keys.DR_ZERO_TOLERANCE] ?: disciplineDefaults.zeroTolerance,
                    suspendExceptions = p[Keys.DR_SUSPEND_EXCEPTIONS] ?: disciplineDefaults.suspendExceptions,
                    forceAllPlatforms = p[Keys.DR_FORCE_ALL] ?: disciplineDefaults.forceAllPlatforms,
                    keepReelFromDirect = p[Keys.DR_KEEP_DIRECT] ?: disciplineDefaults.keepReelFromDirect,
                    forceTotalMode = p[Keys.DR_FORCE_TOTAL] ?: disciplineDefaults.forceTotalMode,
                ),
            ),
        )
    }

    private fun write(p: MutablePreferences, s: ProtectionSettings) {
        p[Keys.MODE] = s.mode.name
        p[Keys.YOUTUBE] = s.youtubeEnabled
        p[Keys.INSTAGRAM] = s.instagramEnabled
        p[Keys.BROWSER] = s.browserEnabled
        p[Keys.FEED_REELS] = s.feedReelsEnabled
        p[Keys.OTHER_APPS] = s.otherAppsEnabled
        p[Keys.TOLERANCE_SECONDS] = s.toleranceSeconds
        p[Keys.DAILY_LIMIT_MINUTES] = s.dailyLimitMinutes
        p[Keys.SNOOZE_ALLOWED] = s.snoozeAllowed
        p[Keys.SNOOZE_MINUTES] = s.snoozeMinutes
        p[Keys.SNOOZED_UNTIL] = s.snoozedUntil
        p[Keys.ALLOW_DIRECT] = s.allowReelFromDirect
        p[Keys.ACTIVE_WINDOWS] = SettingsCodec.encodeWindows(s.activeWindows)
        p[Keys.EXCEPTION_WINDOWS] = SettingsCodec.encodeWindows(s.exceptionWindows)
        p[Keys.EXCEPTION_PERIODS] = SettingsCodec.encodePeriods(s.exceptionPeriods)
        val d = s.discipline
        p[Keys.DISCIPLINE_ACTIVE] = d.active
        p[Keys.DISCIPLINE_STARTED] = d.startedAt
        p[Keys.DISCIPLINE_ENDS] = d.endsAt ?: 0L
        p[Keys.DISCIPLINE_EXIT_DELAY] = d.exitDelayMinutes
        p[Keys.DISCIPLINE_EXIT_REQUESTED] = d.exitRequestedAt ?: 0L
        p[Keys.DR_ZERO_TOLERANCE] = d.rules.zeroTolerance
        p[Keys.DR_SUSPEND_EXCEPTIONS] = d.rules.suspendExceptions
        p[Keys.DR_FORCE_ALL] = d.rules.forceAllPlatforms
        p[Keys.DR_KEEP_DIRECT] = d.rules.keepReelFromDirect
        p[Keys.DR_FORCE_TOTAL] = d.rules.forceTotalMode
    }

    private object Keys {
        val MODE = stringPreferencesKey("mode")
        val YOUTUBE = booleanPreferencesKey("youtube_enabled")
        val INSTAGRAM = booleanPreferencesKey("instagram_enabled")
        val BROWSER = booleanPreferencesKey("browser_enabled")
        val FEED_REELS = booleanPreferencesKey("feed_reels_enabled")
        val OTHER_APPS = booleanPreferencesKey("other_apps_enabled")
        val TOLERANCE_SECONDS = intPreferencesKey("tolerance_seconds")
        val DAILY_LIMIT_MINUTES = intPreferencesKey("daily_limit_minutes")
        val SNOOZE_ALLOWED = booleanPreferencesKey("snooze_allowed")
        val SNOOZE_MINUTES = intPreferencesKey("snooze_minutes")
        val SNOOZED_UNTIL = longPreferencesKey("snoozed_until")
        val ALLOW_DIRECT = booleanPreferencesKey("allow_reel_from_direct")
        val ACTIVE_WINDOWS = stringPreferencesKey("active_windows_v1")
        val EXCEPTION_WINDOWS = stringPreferencesKey("exception_windows_v1")
        val EXCEPTION_PERIODS = stringPreferencesKey("exception_periods_v1")
        val DISCIPLINE_ACTIVE = booleanPreferencesKey("discipline_active")
        val DISCIPLINE_STARTED = longPreferencesKey("discipline_started_at")
        val DISCIPLINE_ENDS = longPreferencesKey("discipline_ends_at")
        val DISCIPLINE_EXIT_DELAY = intPreferencesKey("discipline_exit_delay_minutes")
        val DISCIPLINE_EXIT_REQUESTED = longPreferencesKey("discipline_exit_requested_at")
        val DR_ZERO_TOLERANCE = booleanPreferencesKey("discipline_zero_tolerance")
        val DR_SUSPEND_EXCEPTIONS = booleanPreferencesKey("discipline_suspend_exceptions")
        val DR_FORCE_ALL = booleanPreferencesKey("discipline_force_all")
        val DR_KEEP_DIRECT = booleanPreferencesKey("discipline_keep_direct")
        val DR_FORCE_TOTAL = booleanPreferencesKey("discipline_force_total")
        val DISCLOSURE_ACCEPTED = booleanPreferencesKey("disclosure_accepted")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SUSPECT_YOUTUBE = longPreferencesKey("detection_suspect_youtube_since")
        val SUSPECT_INSTAGRAM = longPreferencesKey("detection_suspect_instagram_since")
    }

    private companion object {
        const val TAG = "Settings"
    }
}
