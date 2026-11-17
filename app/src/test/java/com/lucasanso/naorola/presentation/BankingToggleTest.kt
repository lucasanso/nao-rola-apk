package com.lucasanso.naorola.presentation

import android.Manifest
import android.content.ComponentName
import android.provider.Settings
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.lucasanso.naorola.MainActivity
import com.lucasanso.naorola.NaoRolaApp
import com.lucasanso.naorola.accessibility.NaoRolaAccessibilityService
import com.lucasanso.naorola.accessibility.ServiceComponent
import com.lucasanso.naorola.tile.QuickToggleTile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Banking apps refuse to run while an accessibility service from outside the Play Store is on.
 * Não-Rola offers a real, quick off switch (no hiding) and a quick way back on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BankingToggleTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<NaoRolaApp>()
    private var disableCalls = 0
    private val component get() = ComponentName(app, NaoRolaAccessibilityService::class.java).flattenToString()

    /** Simulates a running service whose disableSelf() really turns it off. */
    private fun runningService() {
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, component)
        app.container.onServiceConnected(disableSelf = {
            disableCalls++
            Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
            app.container.onServiceDisconnected()
        })
    }

    private fun waitText(text: String) = rule.waitUntil(10_000) {
        ShadowLooper.idleMainLooper()
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun disableNowOnlyWorksWhileRunning() {
        assertFalse(app.container.disableServiceNow())
        runningService()
        assertTrue(app.container.disableServiceNow())
        assertEquals(1, disableCalls)
        assertFalse(app.container.disableServiceNow()) // already off
    }

    private fun enabledList() = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()

    @Test fun dashboardBankModeRemovesTheServiceAndComesBack() {
        runBlocking { app.container.settingsRepository.setDisclosureAccepted(true) }
        runningService()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitText("Proteção ativa")
            rule.onNodeWithText("Modo banco (Nubank e outros bancos)").performClick()
            waitText("Entrar no modo banco?")
            rule.onNodeWithText("Entrar no modo banco").performClick()
            waitText("Modo banco ativo")
            assertEquals(1, disableCalls)
            assertTrue(ServiceComponent.isDisabled(app))
            assertFalse(enabledList().contains("NaoRolaAccessibilityService"))

            rule.onNodeWithText("Sair do modo banco").performClick()
            assertFalse(ServiceComponent.isDisabled(app))
            // Without the adb grant, the user turns the service on in Android's settings.
            scenario.onActivity { activity ->
                val next = shadowOf(activity).nextStartedActivity ?: shadowOf(app).nextStartedActivity
                assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, next?.action)
            }
        }
    }

    @Test fun bankModeIsOfferedEvenWithTheServiceAlreadyOff() {
        runBlocking { app.container.settingsRepository.setDisclosureAccepted(true) }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("Proteção desligada")
            rule.onNodeWithText("Modo banco (Nubank e outros bancos)").performClick()
            rule.onNodeWithText("Entrar no modo banco").performClick()
            waitText("Modo banco ativo")
            assertTrue(ServiceComponent.isDisabled(app))
        }
    }

    @Test fun withTheAdbGrantLeavingBankModeTurnsTheServiceBackOn() {
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        val other = "com.example.leitor/.Servico"
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "$other:$component")
        app.container.enterBankMode()
        assertEquals(other, enabledList()) // ours removed, the other one kept
        assertTrue(app.container.exitBankMode())
        assertEquals("$other:$component", enabledList())
        assertFalse(ServiceComponent.isDisabled(app))
    }

    @Test fun quickSettingsTileEntersAndLeavesBankMode() {
        runningService()
        val tile = Robolectric.buildService(QuickToggleTile::class.java).create().get()
        tile.onClick()
        assertEquals(1, disableCalls)
        assertTrue(app.container.bankMode.value)
        assertTrue(ServiceComponent.isDisabled(app))
        tile.onClick()
        assertFalse(app.container.bankMode.value)
        assertFalse(ServiceComponent.isDisabled(app))
    }
}
