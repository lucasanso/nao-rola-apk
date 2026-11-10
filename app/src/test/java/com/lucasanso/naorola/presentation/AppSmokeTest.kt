package com.lucasanso.naorola.presentation

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.lucasanso.naorola.MainActivity
import com.lucasanso.naorola.NaoRolaApp
import com.lucasanso.naorola.domain.model.ProtectionMode
import com.lucasanso.naorola.presentation.components.TAG_SCREEN_CONTENT
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** Starts the real app and walks through every screen, checking that edits are persisted. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val container get() = ApplicationProvider.getApplicationContext<NaoRolaApp>().container

    private fun waitText(text: String) = rule.waitUntil(10_000) {
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    /**
     * DataStore runs the edit on the caller's dispatcher (main), so the main looper must be
     * drained while waiting — under Robolectric nothing else does it inside waitUntil.
     */
    private fun waitSettings(predicate: (com.lucasanso.naorola.domain.model.ProtectionSettings) -> Boolean) =
        rule.waitUntil(10_000) {
            ShadowLooper.idleMainLooper()
            runBlocking { predicate(container.settingsRepository.settings.first()) }
        }

    /** Settings load asynchronously; controls stay disabled until then. */
    private fun clickWhenEnabled(matcher: SemanticsMatcher) {
        rule.waitUntil(10_000) { runCatching { rule.onNode(matcher).assertIsEnabled() }.isSuccess }
        rule.onNodeWithTag(TAG_SCREEN_CONTENT).performScrollToNode(matcher)
        rule.onNode(matcher).performClick()
    }

    private fun back() = rule.onNodeWithContentDescription("Voltar").performClick()

    @Test fun walksThroughTheAppAndPersistsSettings() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("Proteção desligada")
            rule.onNodeWithText("Resolver").performClick()
            waitText("Serviço de acessibilidade")
            back()

            rule.onNodeWithText("Ajustes").performClick()
            waitText("Bloqueio")
            rule.onNodeWithText("Bloqueio").performClick()
            waitText("Shorts e Reels no navegador")
            clickWhenEnabled(hasText("Shorts e Reels no navegador"))
            waitSettings { it.browserEnabled }
            clickWhenEnabled(hasText("15 s"))
            waitSettings { it.toleranceSeconds == 15 }
            clickWhenEnabled(hasTestTag("mode_TOTAL"))
            waitSettings { it.mode == ProtectionMode.TOTAL }
            waitText("O Bloqueio total ignora as opções abaixo.")
            back()

            rule.onNodeWithText("Diagnóstico").performClick()
            waitText("Ative o serviço de acessibilidade para capturar.")
            back()
            rule.onNodeWithText("Sobre").performClick()
            waitText("Regras de detecção")
            back()

            rule.onNodeWithText("Painel").performClick()
            waitText("Bloqueio total")
        }
    }
}
