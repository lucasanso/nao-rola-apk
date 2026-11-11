package com.lucasanso.naorola.presentation

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.lucasanso.naorola.MainActivity
import com.lucasanso.naorola.NaoRolaApp
import com.lucasanso.naorola.domain.model.ProtectionSettings
import com.lucasanso.naorola.presentation.components.TAG_SCREEN_CONTENT
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** Activating Discipline from the UI locks the settings and starts the exit countdown on request. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class DisciplineFlowTest {
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

    @Test fun activateLockAndRequestExit() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("Proteção desligada")
            rule.onNodeWithText("Ajustes").performClick()
            waitText("Modo Disciplina")
            rule.onNodeWithText("Modo Disciplina").performClick()
            waitText("Ativar Modo Disciplina")
            rule.onNodeWithText("5 min").performClick()
            rule.onNodeWithTag(TAG_SCREEN_CONTENT).performScrollToNode(hasText("Ativar Modo Disciplina"))
            rule.onNodeWithText("Ativar Modo Disciplina").performClick()
            waitText("Ativar agora?")
            rule.onNodeWithText("Ativar").performClick()
            waitSettings { it.discipline.active && it.discipline.exitDelayMinutes == 5 }
            waitText("Modo Disciplina ativo")

            rule.onNodeWithContentDescription("Voltar").performClick()
            waitText("Bloqueio")
            rule.onNodeWithText("Bloqueio").performClick()
            waitText("O Modo Disciplina está ativo: as configurações só podem ser alteradas depois de sair dele.")
            rule.onNodeWithText("YouTube Shorts").assertIsNotEnabled()
            rule.onNodeWithContentDescription("Voltar").performClick()

            rule.onNodeWithText("Modo Disciplina").performClick()
            waitText("Quero sair")
            rule.onNodeWithText("Quero sair").performClick()
            waitSettings { it.discipline.exitRequestedAt != null }
            rule.waitUntil(10_000) {
                ShadowLooper.idleMainLooper()
                rule.onAllNodes(hasText("Você poderá sair em", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(runBlocking { container.settingsRepository.settings.first().discipline.active })
        }
    }
}
