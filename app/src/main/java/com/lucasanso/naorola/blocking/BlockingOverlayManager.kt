package com.lucasanso.naorola.blocking

import android.content.Context
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.lucasanso.naorola.core.AppLogger
import com.lucasanso.naorola.domain.session.UserAction
import com.lucasanso.naorola.presentation.theme.NaoRolaTheme

/**
 * The only class that talks to WindowManager. Shows the full-screen block screen as an
 * accessibility overlay (no "draw over other apps" permission needed). The window consumes
 * touches but is not focusable, so system gestures and the back gesture keep working.
 * Must be called on the main thread.
 */
class BlockingOverlayManager(
    private val context: Context,
    private val logger: AppLogger,
    private val onAction: (UserAction) -> Unit,
    private val windowType: Int = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val content = mutableStateOf<BlockScreenContent?>(null)
    private var view: View? = null
    private var owner: OverlayLifecycleOwner? = null

    val isShowing: Boolean get() = view != null

    fun show(screen: BlockScreenContent) {
        content.value = screen
        if (view != null) return
        val lifecycleOwner = OverlayLifecycleOwner().also { it.start() }
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                content.value?.let { current ->
                    NaoRolaTheme(dynamicColor = current.dynamicColor) {
                        BlockScreen(current) { action -> onAction(action) }
                    }
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { title = WINDOW_TITLE }
        try {
            windowManager.addView(composeView, params)
            view = composeView
            owner = lifecycleOwner
        } catch (e: RuntimeException) {
            logger.error(TAG, "Não foi possível mostrar a tela de bloqueio", e)
            lifecycleOwner.destroy()
        }
    }

    fun hide() {
        val current = view ?: return
        try {
            windowManager.removeViewImmediate(current)
        } catch (e: RuntimeException) {
            logger.warn(TAG, "Falha ao remover a tela de bloqueio", e)
        }
        owner?.destroy()
        view = null
        owner = null
        content.value = null
    }

    private companion object {
        const val TAG = "Overlay"
        const val WINDOW_TITLE = "NaoRolaBlockScreen"
    }
}

/** Minimal lifecycle so Compose can run inside a window that is not an Activity. */
private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun start() {
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
