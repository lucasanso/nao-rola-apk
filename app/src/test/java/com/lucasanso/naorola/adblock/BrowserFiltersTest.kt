package com.lucasanso.naorola.adblock

import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.lucasanso.naorola.NaoRolaApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ContentProviderController

@RunWith(RobolectricTestRunner::class)
class BrowserFiltersTest {
    @get:Rule val folder = TemporaryFolder()
    private val app = ApplicationProvider.getApplicationContext<NaoRolaApp>()
    private val filters get() = app.container.browserFilters

    private fun installSamsungInternet() {
        shadowOf(app.packageManager).installPackage(PackageInfo().apply { packageName = "com.sec.android.app.sbrowser" })
    }

    private fun providerText(): String {
        val provider = ContentProviderController.of(ContentBlockerProvider())
            .create("${app.packageName}.contentBlocker.contentProvider").get()
        val descriptor = provider.openFile(Uri.parse("content://${app.packageName}.contentBlocker.contentProvider/filters"), "r")!!
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    @Test fun providerServesTheBundledList() {
        val text = providerText()
        assertTrue(text.startsWith("! "))
        assertTrue(text.contains("||doubleclick.net^\n"))
        filters.refreshStatus()
        assertTrue(filters.status.value.bundledRules > 50)
    }

    @Test fun importedHostsFileIsMergedAndBrowserIsAskedToReload() {
        installSamsungInternet()
        val file = folder.newFile("hosts.txt").apply { writeText("# minha lista\n0.0.0.0 ads.exemplo.com.br\n0.0.0.0 doubleclick.net\n") }
        val result = filters.import(Uri.fromFile(file))
        assertEquals(BrowserFilters.ImportResult.Imported(rules = 2, ignoredLines = 0, truncated = false), result)
        val text = providerText()
        assertTrue(text.contains("||ads.exemplo.com.br^\n"))
        assertEquals(1, Regex("\\|\\|doubleclick\\.net\\^").findAll(text).count())
        val broadcast = shadowOf(app).broadcastIntents.last()
        assertEquals(BrowserFilters.ACTION_UPDATE, broadcast.action)
        assertEquals("com.sec.android.app.sbrowser", broadcast.`package`)
        assertEquals("package:${app.packageName}", broadcast.dataString)
        assertEquals(2, filters.status.value.importedRules)

        filters.removeImported()
        assertTrue(!providerText().contains("exemplo.com.br"))
        assertEquals(0, filters.status.value.importedRules)
    }

    @Test fun adultFilterAddsItsSitesToSamsungInternetAndRemovesThem() {
        installSamsungInternet()
        val before = shadowOf(app).broadcastIntents.size
        filters.setAdultRules(listOf("||pornhub.com^"))
        assertTrue(providerText().contains("||pornhub.com^\n"))
        assertEquals(before + 1, shadowOf(app).broadcastIntents.size)
        filters.setAdultRules(listOf("||pornhub.com^")) // same list: no reload request
        assertEquals(before + 1, shadowOf(app).broadcastIntents.size)
        filters.setAdultRules(emptyList())
        assertTrue(!providerText().contains("pornhub"))
    }

    @Test fun fileWithoutRulesIsRejected() {
        val file = folder.newFile("vazio.txt").apply { writeText("! só comentários\n\n") }
        assertEquals(BrowserFilters.ImportResult.Empty, filters.import(Uri.fromFile(file)))
    }

    @Test fun samsungInternetCanOpenTheBlockerSettings() {
        val intent = Intent(BrowserFilters.ACTION_SETTING).setPackage(app.packageName)
        val activities = app.packageManager.queryIntentActivities(intent, 0)
        assertEquals("com.lucasanso.naorola.MainActivity", activities.single().activityInfo.name)
    }
}
