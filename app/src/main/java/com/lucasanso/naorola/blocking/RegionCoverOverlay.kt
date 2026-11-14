package com.lucasanso.naorola.blocking

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.withTranslation
import com.lucasanso.naorola.core.AppLogger

/**
 * One piece of the screen to hide, in screen pixels.
 * @param clip area the cover may occupy (the list it belongs to, cut above the keyboard).
 * @param scrolls whether the cover follows the list's scrolling until the next evaluation.
 */
data class CoverItem(val rect: Rect, val clip: Rect, val label: String, val shortLabel: String, val scrolls: Boolean)

/**
 * Opaque covers drawn over parts of another app (Shorts shelves, Reels posts, ads), in ONE
 * full-screen accessibility overlay window that does not take touches: the app underneath keeps
 * working normally, and tapping a covered Short still opens it — and then the regular block
 * screen takes over.
 *
 * Covers are recomputed after each evaluation; between evaluations, scroll events move them by
 * the distance the list reported, so they stay on their content while the user scrolls.
 * Must be called on the main thread.
 */
class RegionCoverOverlay(
    private val context: Context,
    private val logger: AppLogger,
    private val windowType: Int = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: CoverView? = null

    /** Sum of vertical scroll deltas seen so far; evaluations note it to realign late results. */
    @Volatile var scrollTotal: Int = 0
        private set

    val isShowing: Boolean get() = view != null

    /** Replaces the covers. [scrollAtRead] is [scrollTotal] when the screen was read. */
    fun show(items: List<CoverItem>, scrollAtRead: Int) {
        if (items.isEmpty()) {
            clear()
            return
        }
        val pending = scrollTotal - scrollAtRead
        val current = view ?: attach() ?: return
        current.items = items.map { it.copy(rect = Rect(it.rect)) }.onEach { if (it.scrolls && pending != 0) it.rect.offset(0, -pending) }
        current.invalidate()
    }

    /** A monitored app's list moved by [deltaY] pixels (positive = content moved up). */
    fun onScrolled(deltaY: Int) {
        if (deltaY == 0) return
        scrollTotal += deltaY
        val current = view ?: return
        current.items.forEach { if (it.scrolls) it.rect.offset(0, -deltaY) }
        current.invalidate()
    }

    fun clear() {
        val current = view ?: return
        view = null
        try {
            windowManager.removeViewImmediate(current)
        } catch (e: RuntimeException) {
            logger.warn(TAG, "Falha ao remover as capas", e)
        }
    }

    private fun attach(): CoverView? {
        val coverView = CoverView(context)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            title = WINDOW_TITLE
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            setFitInsetsTypes(0)
        }
        return try {
            windowManager.addView(coverView, params)
            view = coverView
            coverView
        } catch (e: RuntimeException) {
            logger.error(TAG, "Não foi possível mostrar as capas", e)
            null
        }
    }

    private companion object {
        const val TAG = "Capas"
        const val WINDOW_TITLE = "NaoRolaCovers"
    }
}

/** Draws the covers. Positions are in screen pixels; the window's own offset is subtracted. */
private class CoverView(context: Context) : View(context) {
    var items: List<CoverItem> = emptyList()

    private val density = resources.displayMetrics.density
    private val radius = CORNER_DP * density
    private val margin = (MARGIN_DP * density).toInt()
    private val padding = PADDING_DP * density
    private val location = IntArray(2)
    private val drawRect = Rect()
    private val roundRect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TEXT_SP * resources.displayMetrics.scaledDensity
    }

    override fun onDraw(canvas: Canvas) {
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        fill.color = if (dark) DARK_FILL else LIGHT_FILL
        text.color = if (dark) DARK_TEXT else LIGHT_TEXT
        getLocationOnScreen(location)
        canvas.withTranslation(-location[0].toFloat(), -location[1].toFloat()) {
            for (item in items) {
                drawRect.set(item.rect)
                drawRect.inset(-margin, -margin)
                if (!drawRect.intersect(item.clip)) continue
                roundRect.set(drawRect)
                drawRoundRect(roundRect, radius, radius, fill)
                drawLabel(this, item)
            }
        }
    }

    private fun drawLabel(canvas: Canvas, item: CoverItem) {
        val room = drawRect.width() - 2 * padding
        if (drawRect.height() < text.textSize * LINE_HEIGHT) return
        val label = listOf(item.label, item.shortLabel).firstOrNull { text.measureText(it) <= room } ?: return
        val baseline = drawRect.exactCenterY() - (text.descent() + text.ascent()) / 2
        canvas.drawText(label, drawRect.exactCenterX(), baseline, text)
    }

    private companion object {
        const val CORNER_DP = 12f
        const val MARGIN_DP = 2f
        const val PADDING_DP = 8f
        const val TEXT_SP = 14f
        const val LINE_HEIGHT = 1.6f
        const val LIGHT_FILL = 0xFFECEAE8.toInt()
        const val LIGHT_TEXT = 0xFF5F5B57.toInt()
        const val DARK_FILL = 0xFF2A2827.toInt()
        const val DARK_TEXT = 0xFFB7B2AD.toInt()
    }
}
