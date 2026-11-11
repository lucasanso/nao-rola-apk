package com.lucasanso.naorola.presentation

import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.lucasanso.naorola.MainActivity
import com.lucasanso.naorola.NaoRolaApp
import com.lucasanso.naorola.accessibility.NaoRolaAccessibilityService
import com.lucasanso.naorola.blocking.BlockScreen
import com.lucasanso.naorola.blocking.BlockScreenContent
import com.lucasanso.naorola.detection.rules.InstagramRules
import com.lucasanso.naorola.detection.rules.YouTubeRules
import com.lucasanso.naorola.domain.model.BlockEvent
import com.lucasanso.naorola.domain.model.BlockOutcome
import com.lucasanso.naorola.domain.model.ScheduleWindow
import com.lucasanso.naorola.domain.model.ShortVideoExposure
import com.lucasanso.naorola.domain.model.Surface
import com.lucasanso.naorola.presentation.theme.NaoRolaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.DayOfWeek

/**
 * Renders screenshots of the real app (Robolectric native graphics).
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*ScreenshotTest*" -Pscreenshots
 * Output: app/build/screenshots/
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val out = File("build/screenshots").apply { mkdirs() }
    private val app get() = ApplicationProvider.getApplicationContext<NaoRolaApp>()

    @Before fun enabled() = assumeTrue(System.getProperty("naorola.screenshots") == "true")

    @Test fun protectionOff() = launch { waitText("Proteção desligada"); shot(it, "01-painel-desligado") }

    @Test fun protectionActive() {
        simulateActiveProtection()
        launch {
            waitText("Proteção ativa")
            waitText("7")
            shot(it, "02-painel-ativo")
            rule.onNodeWithText("Estatísticas").performClick(); waitText("Bloqueios por dia"); shot(it, "09-estatisticas")
            rule.onNodeWithText("Ajustes").performClick(); waitText("Bloqueio"); shot(it, "03-ajustes")
            rule.onNodeWithText("Bloqueio").performClick(); waitText("Tolerância e exceções"); shot(it, "04-bloqueio")
            rule.onNodeWithContentDescription("Voltar").performClick(); waitText("Permissões")
            rule.onNodeWithText("Permissões").performClick(); waitText("Bateria sem restrições"); shot(it, "05-permissoes")
        }
    }

    @Test @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun protectionActiveDark() {
        simulateActiveProtection()
        launch { waitText("Proteção ativa"); waitText("7"); shot(it, "06-painel-escuro") }
    }

    @Test fun scheduleAndDiscipline() {
        runBlocking {
            app.container.settingsRepository.edit {
                it.copy(
                    activeWindows = listOf(ScheduleWindow(DayOfWeek.entries.take(5).toSet(), 8 * 60, 22 * 60)),
                    exceptionWindows = listOf(ScheduleWindow(setOf(DayOfWeek.SATURDAY), 20 * 60, 23 * 60)),
                    toleranceSeconds = 10,
                    dailyLimitMinutes = 15,
                )
            }
        }
        launch {
            waitText("Proteção desligada")
            rule.onNodeWithText("Ajustes").performClick(); waitText("Horários e exceções")
            rule.onNodeWithText("Horários e exceções").performClick(); waitText("Seg a sex · 08:00–22:00"); shot(it, "10-horarios")
            rule.onNodeWithContentDescription("Voltar").performClick(); waitText("Modo Disciplina")
            rule.onNodeWithText("Modo Disciplina").performClick(); waitText("Espera para sair"); shot(it, "11-disciplina")
        }
    }

    @Test fun blockScreen() = blockScreenShot("07-tela-de-bloqueio")

    @Test @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun blockScreenDark() = blockScreenShot("08-tela-de-bloqueio-escuro")

    private fun blockScreenShot(name: String) {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    NaoRolaTheme { BlockScreen(BlockScreenContent(Surface.SHORTS_PLAYER, true, 10, false, false)) { } }
                }
            }
            waitText("Você realmente queria assistir a isso?")
            Thread.sleep(400) // let the entrance animation finish
            shot(scenario, name)
        }
    }

    private fun simulateActiveProtection() {
        val component = ComponentName(app, NaoRolaAccessibilityService::class.java).flattenToString()
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, component)
        listOf(YouTubeRules.PACKAGE, InstagramRules.PACKAGE).forEach { pkg ->
            shadowOf(app.packageManager).installPackage(PackageInfo().apply { packageName = pkg })
        }
        shadowOf(app.getSystemService(AppOpsManager::class.java))
            .setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName, AppOpsManager.MODE_ALLOWED)
        val now = System.currentTimeMillis()
        val usage = shadowOf(app.getSystemService(UsageStatsManager::class.java))
        usage.addEvent(YouTubeRules.PACKAGE, now - 47 * 60_000L, UsageEvents.Event.ACTIVITY_RESUMED)
        usage.addEvent(YouTubeRules.PACKAGE, now - 9 * 60_000L, UsageEvents.Event.ACTIVITY_PAUSED)
        usage.addEvent(InstagramRules.PACKAGE, now - 8 * 60_000L, UsageEvents.Event.ACTIVITY_RESUMED)
        usage.addEvent(InstagramRules.PACKAGE, now - 60_000L, UsageEvents.Event.ACTIVITY_PAUSED)
        app.container.onServiceConnected()
        runBlocking {
            val stats = app.container.statsRepository
            repeat(4) { stats.record(BlockEvent(now - it * 600_000L, Surface.SHORTS_PLAYER, BlockOutcome.BLOCKED)) }
            repeat(3) { stats.record(BlockEvent(now - it * 500_000L, Surface.REELS_VIEWER, BlockOutcome.BLOCKED)) }
            stats.recordExposure(ShortVideoExposure(Surface.SHORTS_PLAYER, now - 300_000L, now - 120_000L))
            val day = 24 * 3_600_000L
            listOf(9, 6, 11, 4, 8, 5).forEachIndexed { i, count ->
                repeat(count) { stats.record(BlockEvent(now - (6 - i) * day + it * 60_000L, Surface.SHORTS_PLAYER, BlockOutcome.BLOCKED)) }
            }
        }
    }

    private fun launch(block: (ActivityScenario<out Activity>) -> Unit) =
        ActivityScenario.launch(MainActivity::class.java).use(block)

    private fun waitText(text: String) = rule.waitUntil(10_000) {
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun shot(scenario: ActivityScenario<out Activity>, name: String) {
        rule.waitForIdle()
        scenario.onActivity { a ->
            val v = a.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
