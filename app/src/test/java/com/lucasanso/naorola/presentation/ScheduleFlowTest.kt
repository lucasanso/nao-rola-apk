package com.lucasanso.naorola.presentation

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.lucasanso.naorola.MainActivity
import com.lucasanso.naorola.NaoRolaApp
import com.lucasanso.naorola.domain.model.ProtectionSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.DayOfWeek

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScheduleFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val container get() = ApplicationProvider.getApplicationContext<NaoRolaApp>().container

    private fun waitText(text: String) = rule.waitUntil(10_000) {
        ShadowLooper.idleMainLooper()
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitSettings(predicate: (ProtectionSettings) -> Boolean) = rule.waitUntil(10_000) {
        ShadowLooper.idleMainLooper()
        runBlocking { predicate(container.settingsRepository.settings.first()) }
    }

    @Test fun addsAWeekdayWindowAndAnException() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("Proteção desligada")
            rule.onNodeWithText("Ajustes").performClick()
            waitText("Horários e exceções")
            rule.onNodeWithText("Horários e exceções").performClick()
            waitText("Só nos horários abaixo")
            rule.onNodeWithText("Só nos horários abaixo").performClick()
            waitText("Adicionar horário")
            rule.onNodeWithText("Adicionar horário").performClick()
            waitText("Salvar")
            rule.onNodeWithText("Salvar").performClick() // default: Mon–Fri 09:00–18:00
            waitSettings { it.activeWindows.size == 1 }
            waitText("Seg a sex · 09:00–18:00")

            rule.onNodeWithText("Adicionar exceção").performClick()
            waitText("Salvar")
            rule.onNodeWithText("sáb").performClick()
            rule.onNodeWithText("Salvar").performClick()
            waitSettings { it.exceptionWindows.size == 1 }

            val saved = runBlocking { container.settingsRepository.settings.first() }
            assertEquals(DayOfWeek.entries.take(5).toSet(), saved.activeWindows.single().days)
            assertEquals(DayOfWeek.entries.take(6).toSet(), saved.exceptionWindows.single().days)
        }
    }
}
