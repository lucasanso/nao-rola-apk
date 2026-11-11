package com.lucasanso.naorola.domain.policy

import com.lucasanso.naorola.domain.model.DisciplineRules
import com.lucasanso.naorola.domain.model.DisciplineState
import com.lucasanso.naorola.domain.model.ProtectionSettings

/**
 * Discipline mode transitions. Leaving is only possible from inside the app, after the delay
 * chosen at activation; protection stays on while the delay runs.
 *
 * It does not (and cannot) stop the owner of the phone from turning the accessibility service
 * off in Android settings; that case is recorded and shown instead.
 */
object Discipline {

    fun activate(
        settings: ProtectionSettings,
        now: Long,
        endsAt: Long?,
        exitDelayMinutes: Int,
        rules: DisciplineRules,
    ): ProtectionSettings {
        require(endsAt == null || endsAt > now) { "O fim da Disciplina precisa estar no futuro" }
        require(exitDelayMinutes > 0) { "A espera para sair precisa ser positiva" }
        return settings.copy(
            snoozedUntil = 0L,
            discipline = DisciplineState(
                active = true,
                startedAt = now,
                endsAt = endsAt,
                exitDelayMinutes = exitDelayMinutes,
                exitRequestedAt = null,
                rules = rules,
            ),
        )
    }

    fun requestExit(settings: ProtectionSettings, now: Long): ProtectionSettings {
        val d = settings.discipline
        if (!d.isActive(now) || d.exitRequestedAt != null) return settings
        return settings.copy(discipline = d.copy(exitRequestedAt = now))
    }

    fun cancelExitRequest(settings: ProtectionSettings): ProtectionSettings =
        settings.copy(discipline = settings.discipline.copy(exitRequestedAt = null))

    /** Returns the settings without Discipline, or null while the exit delay is still running. */
    fun confirmExit(settings: ProtectionSettings, now: Long): ProtectionSettings? {
        val d = settings.discipline
        if (!d.isActive(now)) return normalize(settings, now)
        return if (d.canConfirmExit(now)) settings.copy(discipline = DisciplineState()) else null
    }

    /** Clears a Discipline whose automatic end has passed. */
    fun normalize(settings: ProtectionSettings, now: Long): ProtectionSettings =
        if (settings.discipline.active && !settings.discipline.isActive(now)) {
            settings.copy(discipline = DisciplineState())
        } else {
            settings
        }
}

sealed interface EditResult {
    data class Applied(val settings: ProtectionSettings) : EditResult
    data object LockedByDiscipline : EditResult
}

/** Every settings change from the UI goes through here, so Discipline cannot be bypassed. */
object SettingsEditGuard {
    fun apply(current: ProtectionSettings, now: Long, edit: (ProtectionSettings) -> ProtectionSettings): EditResult {
        if (current.discipline.isActive(now)) return EditResult.LockedByDiscipline
        // Discipline state only changes through [Discipline]; a regular edit cannot touch it.
        return EditResult.Applied(edit(current).copy(discipline = current.discipline))
    }
}
