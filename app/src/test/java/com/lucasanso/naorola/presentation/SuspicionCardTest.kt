package com.lucasanso.naorola.presentation

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.lucasanso.naorola.MainActivity
import com.lucasanso.naorola.NaoRolaApp
import com.lucasanso.naorola.domain.model.Platform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** A suspected detection failure is shown on the dashboard, leads to Diagnostics and can be dismissed. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SuspicionCardTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val container get() = ApplicationProvider.getApplicationContext<NaoRolaApp>().container

    private fun waitText(text: String) = rule.waitUntil(10_000) {
        ShadowLooper.idleMainLooper()
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun showOpenAndDismiss() {
        runBlocking { container.settingsRepository.setDetectionSuspicion(Platform.YOUTUBE, 1_000L) }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("Possível falha de detecção")
            rule.onNodeWithText("Abrir Diagnóstico").performClick()
            waitText("Capturas")
            rule.onNodeWithContentDescription("Voltar").performClick()
            waitText("Possível falha de detecção")
            rule.onNodeWithText("Dispensar").performClick()
            rule.waitUntil(10_000) {
                ShadowLooper.idleMainLooper()
                runBlocking { container.settingsRepository.detectionSuspicions.first().isEmpty() } &&
                    rule.onAllNodesWithText("Possível falha de detecção").fetchSemanticsNodes().isEmpty()
            }
        }
    }
}
