package com.lucasanso.naorola.presentation

import android.content.Intent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.lucasanso.naorola.MainActivity
import com.lucasanso.naorola.NaoRolaApp
import com.lucasanso.naorola.adblock.BrowserFilters
import com.lucasanso.naorola.presentation.components.TAG_SCREEN_CONTENT
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AdsScreenTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<NaoRolaApp>()

    private fun waitText(text: String) = rule.waitUntil(10_000) {
        ShadowLooper.idleMainLooper()
        rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun adBlockingCanBeTurnedOffFromSettings() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("Ajustes")
            rule.onAllNodesWithText("Ajustes")[0].performClick()
            waitText("Ligado · YouTube, Instagram e Samsung Internet")
            rule.onNodeWithText("Anúncios").performClick()
            waitText("Bloquear anúncios")
            rule.onNodeWithText("Bloquear anúncios").performClick()
            rule.waitUntil(10_000) {
                ShadowLooper.idleMainLooper()
                runBlocking { !app.container.settingsRepository.settings.first().adBlockEnabled }
            }
            assertFalse(runBlocking { app.container.settingsRepository.settings.first().adBlockEnabled })
        }
    }

    @Test fun samsungInternetOpensTheBlockerSettingsDirectly() {
        val intent = Intent(app, MainActivity::class.java).setAction(BrowserFilters.ACTION_SETTING)
        ActivityScenario.launch<MainActivity>(intent).use {
            waitText("Bloquear anúncios")
            rule.onNodeWithTag(TAG_SCREEN_CONTENT).performScrollToNode(hasText("Chrome"))
            waitText("O Samsung Internet não está instalado")
            waitText("Lista do Não-Rola:")
        }
    }
}
