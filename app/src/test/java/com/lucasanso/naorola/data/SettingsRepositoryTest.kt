package com.lucasanso.naorola.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.lucasanso.naorola.core.AppLogger
import com.lucasanso.naorola.core.Clock
import com.lucasanso.naorola.data.settings.SettingsRepository
import com.lucasanso.naorola.domain.model.DisciplineRules
import com.lucasanso.naorola.domain.model.Platform
import com.lucasanso.naorola.domain.model.ProtectionMode
import com.lucasanso.naorola.domain.model.ScheduleWindow
import com.lucasanso.naorola.domain.policy.Discipline
import com.lucasanso.naorola.domain.policy.EditResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.DayOfWeek

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private var now = 1_000_000L

    private fun repository(scope: CoroutineScope, file: File = File(folder.root, "settings.preferences_pb")) = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = scope) { file },
        Clock { now },
        AppLogger.None,
    )

    @Test fun defaultsWhenNothingSaved() = runTest(UnconfinedTestDispatcher()) {
        val s = repository(backgroundScope).settings.first()
        assertTrue(s.youtubeEnabled && s.instagramEnabled)
        assertFalse(s.browserEnabled)
        assertTrue(s.hideInFeeds && s.adBlockEnabled)
        assertFalse(s.adultBlockEnabled)
        assertEquals(ProtectionMode.PERSONALIZED, s.mode)
    }

    @Test fun editsArePersistedAcrossInstances() = runTest(UnconfinedTestDispatcher()) {
        val file = File(folder.root, "persist.preferences_pb")
        val window = ScheduleWindow(setOf(DayOfWeek.MONDAY), 60, 120)
        val firstScope = CoroutineScope(backgroundScope.coroutineContext + Job())
        repository(firstScope, file).edit {
            it.copy(
                mode = ProtectionMode.TOTAL, toleranceSeconds = 15, browserEnabled = true, activeWindows = listOf(window),
                hideInFeeds = false, adBlockEnabled = false, adultBlockEnabled = true, adultSearchBlock = false,
            )
        }
        firstScope.cancel() // closes the first DataStore, like an app restart
        val reloaded = repository(backgroundScope, file).settings.first()
        assertEquals(ProtectionMode.TOTAL, reloaded.mode)
        assertEquals(15, reloaded.toleranceSeconds)
        assertTrue(reloaded.browserEnabled)
        assertEquals(listOf(window), reloaded.activeWindows)
        assertFalse(reloaded.hideInFeeds)
        assertFalse(reloaded.adBlockEnabled)
        assertTrue(reloaded.adultBlockEnabled)
        assertFalse(reloaded.adultSearchBlock)
    }

    @Test fun disciplineLocksEditsUntilExitIsConfirmed() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(backgroundScope)
        repo.changeDiscipline { s, t -> Discipline.activate(s, t, null, exitDelayMinutes = 5, rules = DisciplineRules()) }
        assertEquals(EditResult.LockedByDiscipline, repo.edit { it.copy(youtubeEnabled = false) })
        assertTrue(repo.settings.first().youtubeEnabled)

        repo.changeDiscipline { s, t -> Discipline.requestExit(s, t) }
        now += 4 * 60_000
        assertFalse(repo.changeDiscipline { s, t -> Discipline.confirmExit(s, t) })
        now += 2 * 60_000
        assertTrue(repo.changeDiscipline { s, t -> Discipline.confirmExit(s, t) })
        assertTrue(repo.edit { it.copy(youtubeEnabled = false) } is EditResult.Applied)
        assertFalse(repo.settings.first().youtubeEnabled)
    }

    @Test fun detectionSuspicionsAreStoredAndCleared() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(backgroundScope)
        repo.setDetectionSuspicion(Platform.YOUTUBE, 123L)
        repo.setDetectionSuspicion(Platform.OTHER, 5L) // not tracked
        assertEquals(mapOf(Platform.YOUTUBE to 123L), repo.detectionSuspicions.first())
        repo.setDetectionSuspicion(Platform.YOUTUBE, null)
        assertTrue(repo.detectionSuspicions.first().isEmpty())
    }

    @Test fun snoozeIsStored() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(backgroundScope)
        repo.setSnoozedUntil(42L)
        assertEquals(42L, repo.settings.first().snoozedUntil)
    }

    @Test fun backupMovesSettingsToAnotherInstallation() = runTest(UnconfinedTestDispatcher()) {
        val window = ScheduleWindow(setOf(DayOfWeek.FRIDAY), 600, 900)
        val source = repository(backgroundScope, File(folder.root, "origem.preferences_pb"))
        source.edit { it.copy(toleranceSeconds = 30, adultBlockEnabled = true, hideYouTubeHome = true, activeWindows = listOf(window)) }
        source.setSnoozedUntil(now + 60_000) // transient: does not travel
        val json = source.exportBackup()

        val target = repository(backgroundScope, File(folder.root, "destino.preferences_pb"))
        assertEquals(com.lucasanso.naorola.data.settings.RestoreResult.RESTORED, target.restoreBackup(json))
        val restored = target.settings.first()
        assertEquals(30, restored.toleranceSeconds)
        assertTrue(restored.adultBlockEnabled && restored.hideYouTubeHome)
        assertEquals(listOf(window), restored.activeWindows)
        assertEquals(0L, restored.snoozedUntil)
    }

    @Test fun backupIsRefusedDuringDisciplineAndWhenNotOurs() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(backgroundScope)
        assertEquals(com.lucasanso.naorola.data.settings.RestoreResult.INVALID, repo.restoreBackup("{\"x\":1}"))
        assertEquals(com.lucasanso.naorola.data.settings.RestoreResult.INVALID, repo.restoreBackup("não é json"))
        val json = repo.exportBackup()
        repo.changeDiscipline { s, t -> Discipline.activate(s, t, null, 5, DisciplineRules()) }
        assertEquals(com.lucasanso.naorola.data.settings.RestoreResult.LOCKED_BY_DISCIPLINE, repo.restoreBackup(json))
    }
}
