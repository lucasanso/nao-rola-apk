package com.lucasanso.naorola.blocking

import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.lucasanso.naorola.core.AppLogger
import com.lucasanso.naorola.domain.model.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class BlockingOverlayManagerTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val content = BlockScreenContent(Surface.SHORTS_PLAYER, true, 10, false, false)

    private fun windowCount() = Shadow.extract<ShadowWindowManagerImpl>(windowManager).views.size

    @Test fun showAddsOneWindowAndHideRemovesIt() {
        val overlay = BlockingOverlayManager(context, AppLogger.None, onAction = {})
        val before = windowCount()
        overlay.show(content)
        overlay.show(content) // already visible: only updates the content
        ShadowLooper.idleMainLooper()
        assertTrue(overlay.isShowing)
        assertEquals(before + 1, windowCount())
        overlay.hide()
        assertFalse(overlay.isShowing)
        assertEquals(before, windowCount())
        overlay.hide() // idempotent
    }
}
