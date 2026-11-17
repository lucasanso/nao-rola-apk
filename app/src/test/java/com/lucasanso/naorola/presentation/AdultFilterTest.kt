package com.lucasanso.naorola.presentation

import android.Manifest
import android.net.Uri
import android.provider.Settings
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
import com.lucasanso.naorola.core.PrivateDns
import com.lucasanso.naorola.domain.model.Surface
import com.lucasanso.naorola.presentation.components.TAG_SCREEN_CONTENT
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AdultFilterTest {
    @get:Rule val rule = createEmptyComposeRule()
    @get:Rule val folder = TemporaryFolder()
    private val app get() = ApplicationProvider.getApplicationContext<NaoRolaApp>()

    private fun waitText(text: String) = rule.waitUntil(10_000) {
        ShadowLooper.idleMainLooper()
        rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun openScreen() {
        waitText("Ajustes")
        rule.onAllNodesWithText("Ajustes")[0].performClick()
        waitText("Filtro adulto")
        rule.onNodeWithText("Filtro adulto").performClick()
        waitText("Bloquear sites pornográficos")
    }

    @Test fun turningOnTheFilterSavesItAndShowsHowToGrantDns() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openScreen()
            rule.onNodeWithText("Bloquear sites pornográficos").performClick()
            rule.waitUntil(10_000) {
                ShadowLooper.idleMainLooper()
                runBlocking { app.container.settingsRepository.settings.first().adultBlockEnabled }
            }
            rule.onNodeWithTag(TAG_SCREEN_CONTENT).performScrollToNode(hasText(PrivateDns.grantCommand(app)))
            waitText("adb shell pm grant")
        }
    }

    @Test fun withThePermissionTheAppSwitchesPrivateDns() {
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        ActivityScenario.launch(MainActivity::class.java).use {
            openScreen()
            rule.onNodeWithTag(TAG_SCREEN_CONTENT).performScrollToNode(hasText("Usar este DNS"))
            rule.onNodeWithText("Usar este DNS").performClick()
            waitText("Em uso: AdGuard Família")
            assertEquals("hostname", Settings.Global.getString(app.contentResolver, "private_dns_mode"))
            assertEquals("family.adguard-dns.com", Settings.Global.getString(app.contentResolver, "private_dns_specifier"))
            rule.onNodeWithText("Voltar ao automático").performClick()
            waitText("DNS familiar não está em uso")
            assertEquals("opportunistic", Settings.Global.getString(app.contentResolver, "private_dns_mode"))
        }
    }

    @Test fun importedSitesAreRecognised() {
        val store = app.container.adultSites
        val file = folder.newFile("lista.txt").apply { writeText("0.0.0.0 siteadulto-exemplo.com.br\n0.0.0.0 outro-exemplo.net\n") }
        assertEquals(BrowserFilters.ImportResult.Imported(2, 0, false), store.import(Uri.fromFile(file)))
        assertEquals(Surface.ADULT_SITE, store.matcher.surfaceFor("https://siteadulto-exemplo.com.br/x"))
        store.removeImported()
        assertEquals(null, store.matcher.surfaceFor("https://siteadulto-exemplo.com.br/x"))
        assertTrue(store.browserRules().contains("||pornhub.com^"))
    }
}
