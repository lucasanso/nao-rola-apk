package com.lucasanso.naorola.blocking

import android.graphics.Rect
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.lucasanso.naorola.core.AppLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl

@RunWith(RobolectricTestRunner::class)
class RegionCoverOverlayTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val shadowWindows get() = Shadow.extract<ShadowWindowManagerImpl>(windowManager)
    private val feed = Rect(0, 200, 1080, 2200)

    private fun item(top: Int, scrolls: Boolean = true) =
        CoverItem(Rect(0, top, 1080, top + 600), Rect(feed), "Shorts oculto", "Oculto", scrolls)

    /** Covers currently drawn, read back from the window's view. */
    @Suppress("UNCHECKED_CAST")
    private fun drawn(): List<CoverItem> {
        val view = shadowWindows.views.last { it.javaClass.simpleName == "CoverView" }
        val field = view.javaClass.getDeclaredField("items").apply { isAccessible = true }
        return field.get(view) as List<CoverItem>
    }

    private fun coverWindows() = shadowWindows.views.count { it.javaClass.simpleName == "CoverView" }

    @Test fun oneWindowForAllCoversThatTakesNoTouches() {
        val overlay = RegionCoverOverlay(context, AppLogger.None)
        overlay.show(listOf(item(300), item(1200)), scrollAtRead = 0)
        overlay.show(listOf(item(400)), scrollAtRead = 0)
        assertEquals(1, coverWindows())
        val params = (shadowWindows.views.last().layoutParams as WindowManager.LayoutParams)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertEquals(1, drawn().size)
    }

    @Test fun coversFollowScrollingUntilTheNextEvaluation() {
        val overlay = RegionCoverOverlay(context, AppLogger.None)
        overlay.show(listOf(item(800), item(2100, scrolls = false)), scrollAtRead = overlay.scrollTotal)
        overlay.onScrolled(150) // content moved up 150 px
        assertEquals(650, drawn()[0].rect.top)
        assertEquals(2100, drawn()[1].rect.top) // fixed covers (tab buttons) stay
    }

    @Test fun lateResultsAreRealignedWithScrollingThatHappenedMeanwhile() {
        val overlay = RegionCoverOverlay(context, AppLogger.None)
        overlay.show(listOf(item(800)), scrollAtRead = overlay.scrollTotal)
        val mark = overlay.scrollTotal
        overlay.onScrolled(100) // the screen was read before this scroll
        overlay.show(listOf(item(800)), scrollAtRead = mark)
        assertEquals(700, drawn().single().rect.top)
    }

    @Test fun emptyListOrClearRemovesTheWindow() {
        val overlay = RegionCoverOverlay(context, AppLogger.None)
        overlay.show(listOf(item(300)), 0)
        assertTrue(overlay.isShowing)
        overlay.show(emptyList(), 0)
        assertFalse(overlay.isShowing)
        assertEquals(0, coverWindows())
        overlay.show(listOf(item(300)), 0)
        overlay.clear()
        overlay.clear() // idempotent
        assertEquals(0, coverWindows())
    }

    @Test fun drawingDoesNotCrash() {
        val overlay = RegionCoverOverlay(context, AppLogger.None)
        overlay.show(listOf(item(300), CoverItem(Rect(0, 2250, 270, 2340), Rect(0, 0, 1080, 2340), "Shorts oculto", "Oculto", false)), 0)
        val view = shadowWindows.views.last()
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1080, 2340)
        view.draw(android.graphics.Canvas(android.graphics.Bitmap.createBitmap(1080, 2340, android.graphics.Bitmap.Config.ARGB_8888)))
    }
}
