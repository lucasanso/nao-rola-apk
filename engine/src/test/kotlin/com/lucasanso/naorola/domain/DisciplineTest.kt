package com.lucasanso.naorola.domain

import com.lucasanso.naorola.domain.model.DisciplineRules
import com.lucasanso.naorola.domain.model.ProtectionMode
import com.lucasanso.naorola.domain.model.ProtectionSettings
import com.lucasanso.naorola.domain.policy.Discipline
import com.lucasanso.naorola.domain.policy.EditResult
import com.lucasanso.naorola.domain.policy.SettingsEditGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisciplineTest {
    private val t0 = 1_000_000L
    private val minute = 60_000L
    private val active = Discipline.activate(ProtectionSettings(), t0, endsAt = null, exitDelayMinutes = 15, rules = DisciplineRules())

    @Test fun settingsAreLockedWhileActive() {
        val result = SettingsEditGuard.apply(active, t0 + minute) { it.copy(youtubeEnabled = false) }
        assertEquals(EditResult.LockedByDiscipline, result)
    }

    @Test fun regularEditsCannotTouchDisciplineState() {
        val result = SettingsEditGuard.apply(ProtectionSettings(), t0) {
            it.copy(mode = ProtectionMode.TOTAL, discipline = active.discipline)
        } as EditResult.Applied
        assertEquals(ProtectionMode.TOTAL, result.settings.mode)
        assertFalse(result.settings.discipline.active)
    }

    @Test fun exitRequiresTheChosenDelayAndConfirmation() {
        val requested = Discipline.requestExit(active, t0 + minute)
        assertTrue(requested.discipline.isActive(t0 + 2 * minute)) // still protecting while waiting
        assertNull(Discipline.confirmExit(requested, t0 + 15 * minute))
        val left = Discipline.confirmExit(requested, t0 + 16 * minute)
        assertNotNull(left)
        assertFalse(left!!.discipline.active)
    }

    @Test fun cannotConfirmWithoutRequesting() {
        assertNull(Discipline.confirmExit(active, t0 + 999 * minute))
    }

    @Test fun cancellingTheRequestRestartsTheWait() {
        val requested = Discipline.requestExit(active, t0)
        val cancelled = Discipline.cancelExitRequest(requested)
        assertNull(cancelled.discipline.exitRequestedAt)
        assertNull(Discipline.confirmExit(cancelled, t0 + 60 * minute))
    }

    @Test fun endsAutomaticallyAtTheChosenTime() {
        val timed = Discipline.activate(ProtectionSettings(), t0, endsAt = t0 + 30 * minute, exitDelayMinutes = 60, rules = DisciplineRules())
        assertTrue(timed.discipline.isActive(t0 + 29 * minute))
        assertFalse(timed.discipline.isActive(t0 + 30 * minute))
        assertFalse(Discipline.normalize(timed, t0 + 31 * minute).discipline.active)
        assertTrue(SettingsEditGuard.apply(timed, t0 + 31 * minute) { it } is EditResult.Applied)
    }

    @Test(expected = IllegalArgumentException::class)
    fun endTimeMustBeInTheFuture() {
        Discipline.activate(ProtectionSettings(), t0, endsAt = t0, exitDelayMinutes = 5, rules = DisciplineRules())
    }
}
